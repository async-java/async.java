package org.ores.async;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An async (non-blocking) mutex.
 *
 * <p>Unlike {@code synchronized} blocks and {@link java.util.concurrent.locks.ReentrantLock},
 * {@code NeoLock} <strong>does not park a thread</strong> while waiting. Waiters enqueue an
 * error-first callback; when the lock becomes free, the next waiter's callback fires with a
 * fresh {@link Unlock} token. This makes it suitable for callback chains where the acquire and
 * the release happen on different threads (or different virtual threads), which is the common
 * case with async I/O.
 *
 * <h3>Why not just use {@code synchronized}?</h3>
 *
 * <ol>
 *   <li><strong>Cross-thread release.</strong> A {@code synchronized} block must be released by
 *       the same thread that acquired it. Inside a callback chain, the release usually happens
 *       inside an async completion handler running on a different worker. {@code NeoLock}
 *       models this directly: acquire returns a token via callback, release happens via that
 *       token.</li>
 *   <li><strong>VT-friendly under all JDKs.</strong> On JDK 21 with VTs, {@code synchronized}
 *       <em>used to</em> pin the carrier thread; JEP 491 (JDK 24) removes that constraint, but
 *       {@code NeoLock} never had it.</li>
 * </ol>
 *
 * <p>The continuation that {@link #acquire} fires is conventionally named {@code c} in code
 * examples (short for <em>continuation</em>). It receives the {@link Unlock} token instead of a
 * value.
 *
 * <h3>Usage</h3>
 *
 * <pre>
 *   NeoLock lock = new NeoLock("inventory");
 *
 *   lock.acquire((err, unlock) -&gt; {
 *       try {
 *           // critical section. Safe to await async work inside here; the lock is held until
 *           // unlock.releaseLock() is called.
 *           mutateInventory(itemId);
 *       } finally {
 *           unlock.releaseLock();
 *       }
 *   });
 * </pre>
 *
 * <p>For synchronous critical sections, use {@link #withLock(Runnable)} which auto-releases on
 * normal return <em>and on exception</em>, eliminating the {@code finally{}} boilerplate:
 *
 * <pre>
 *   lock.withLock(() -&gt; mutateInventory(itemId));
 *   // even if mutateInventory throws, the lock is released.
 * </pre>
 *
 * <p>For a critical section that awaits async work:
 *
 * <pre>
 *   lock.acquire((err, unlock) -&gt; {
 *       fetchCurrentBalance(accountId, (e, balance) -&gt; {
 *           updateBalance(accountId, balance.subtract(amount), (e2, ok) -&gt; {
 *               unlock.releaseLock(); // released after the async update completes
 *               reply.send(ok);
 *           });
 *       });
 *   });
 * </pre>
 *
 * <h3>Non-reentrance</h3>
 *
 * <p>{@code NeoLock} is <strong>not reentrant</strong>. Calling {@link #acquire} from inside an
 * acquire callback that has not yet been released will enqueue the new request behind the
 * current holder &mdash; if the current holder is the same logical "owner" waiting on its own
 * release, the callback chain stalls indefinitely. There is no general way to detect this in
 * an async/callback world (release may be on a different thread or scheduled later); if you
 * need a defensive check, use {@link #tryAcquire()} which returns immediately when the lock is
 * held.
 *
 * <h3>VT-pinning audit (JDK 21 - 23)</h3>
 *
 * <p>Internally {@code NeoLock} uses {@code synchronized (this)} blocks to guard the
 * {@code locked} flag and the waiter queue. On JDK 21 - 23, {@code synchronized} pins the
 * carrier thread while the monitor is held; on JDK 24+ ([JEP 491]) it does not. The critical
 * sections are microseconds (one flag check, one deque push or pop) so the pinning impact is
 * negligible in practice. Audited at v0.2.5 with a 1 000-acquirer stress test
 * ({@code NeoLockFairnessTest#stress_1000_acquirers_no_lost_wakeups}) on JDK 17; no observable
 * latency floor or thread-starvation. A future major release may migrate the internal monitor
 * to a {@link java.util.concurrent.locks.ReentrantLock} for explicit VT-friendliness on older
 * JDKs.
 *
 * <h3>Concurrency contract</h3>
 *
 * <ul>
 *   <li><strong>FIFO fairness</strong>: acquirers receive the lock in the order they called
 *       {@code acquire(...)}. Pinned by {@code NeoLockFairnessTest} (100 sequential acquirers
 *       in order). Internally backed by an {@link ArrayDeque} popped from head, pushed to
 *       tail.</li>
 *   <li>The waiter queue and the {@code locked} flag are both guarded by the {@code NeoLock}
 *       instance's monitor. (Earlier versions split those across two locks, allowing two
 *       concurrent acquirers in a tight race &mdash; fixed in v0.2.0.)</li>
 *   <li>{@link Unlock} is a public sibling class (since v0.2.2) so {@code NeoLock} is usable
 *       from any package.</li>
 *   <li>v0.2.5 adds {@link #tryAcquire()}, {@link #acquire(long, Asyncc.IAsyncCallback)} with
 *       timeout, {@link #withLock(Runnable)}, {@link #isLocked()}, {@link #queueDepth()}.</li>
 * </ul>
 *
 * @see Unlock
 * @see NeoQueue
 */
public class NeoLock {

  /**
   * Shared daemon-threaded scheduler used to fire timeout failures for
   * {@link #acquire(long, Asyncc.IAsyncCallback)}. One thread is plenty; the scheduler only
   * dispatches Runnables that flip an {@link AtomicBoolean} and call back into user code, so
   * the work is light and not on any hot path.
   */
  private static final ScheduledExecutorService TIMEOUT_SCHEDULER;
  static {
    final AtomicInteger ids = new AtomicInteger();
    TIMEOUT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
      final Thread t = new Thread(r, "neolock-timeout-" + ids.incrementAndGet());
      t.setDaemon(true);
      return t;
    });
  }

  private final String namespace;
  // All three of these fields are guarded by `this` (the NeoLock instance's monitor).
  private boolean locked = false;
  private final Deque<Asyncc.IAsyncCallback<Unlock, Object>> queue = new ArrayDeque<>();

  public NeoLock() {
    this(null);
  }

  public NeoLock(final String name) {
    this.namespace = name;
  }

  public String getNamespace() {
    return namespace;
  }

  /**
   * Snapshot of whether the lock is currently held. Useful for diagnostics / metrics; not a
   * substitute for {@link #tryAcquire()} in a check-then-act sequence (the lock state may
   * change between the {@code isLocked()} read and a subsequent {@code acquire(...)} call).
   *
   * @since 0.2.5
   */
  public synchronized boolean isLocked() {
    return this.locked;
  }

  /**
   * Snapshot of the number of waiters currently queued behind the holder. Useful for
   * diagnostics and metrics. The count excludes the holder itself.
   *
   * @since 0.2.5
   */
  public synchronized int queueDepth() {
    return this.queue.size();
  }

  /**
   * Non-blocking attempt to acquire the lock. Returns an {@link Optional} containing an
   * {@link Unlock} token if the lock was free and is now held by this caller, or an empty
   * {@code Optional} if the lock was already held.
   *
   * <p>Callers must {@link Unlock#releaseLock()} the token when done. The lock is held until
   * release; subsequent {@code tryAcquire()} calls (and queued {@code acquire(...)} calls)
   * will be denied/wait respectively until then.
   *
   * @since 0.2.5
   */
  public Optional<Unlock> tryAcquire() {
    synchronized (this) {
      if (this.locked) {
        return Optional.empty();
      }
      this.locked = true;
    }
    return Optional.of(this.makeUnlock(true));
  }

  /**
   * Acquire the lock with a bounded wait. If the lock is not acquired within
   * {@code timeoutMs} milliseconds, fires the callback with a {@link TimeoutException} as the
   * error. A pending acquire is cancelled cleanly (removed from the waiter queue) on timeout.
   *
   * <p>If {@code timeoutMs <= 0}, this is equivalent to {@link #tryAcquire()}: succeeds
   * immediately if available, fails immediately with {@link TimeoutException} otherwise.
   *
   * <p>If the lock is granted just as the timeout fires (a race), the lock is released on the
   * caller's behalf and the callback fires with the {@code TimeoutException} only once.
   *
   * @since 0.2.5
   */
  public void acquire(final long timeoutMs, final Asyncc.IAsyncCallback<Unlock, Object> cb) {

    if (timeoutMs <= 0) {
      final Optional<Unlock> u = this.tryAcquire();
      if (u.isPresent()) {
        cb.success(u.get());
      } else {
        cb.fail(new TimeoutException(
            "NeoLock[" + this.namespace + "].acquire(0) failed: lock is already held"));
      }
      return;
    }

    // Wrap the user's callback so we can guarantee at-most-once delivery between the
    // acquire-success path and the timeout-fail path.
    final AtomicBoolean fired = new AtomicBoolean(false);
    final Asyncc.IAsyncCallback<Unlock, Object> guarded =
        (err, unlock) -> {
          if (fired.compareAndSet(false, true)) {
            cb.done(err, unlock);
          } else if (unlock != null) {
            // Timeout already fired but we beat it on the wire and got the lock anyway.
            // We've committed to handing the caller a TimeoutException, so release the lock.
            unlock.releaseLock();
          }
        };

    final boolean acquiredNow;
    synchronized (this) {
      if (this.locked) {
        this.queue.addLast(guarded);
        acquiredNow = false;
      } else {
        this.locked = true;
        acquiredNow = true;
      }
    }

    if (acquiredNow) {
      guarded.success(this.makeUnlock(true));
      return;
    }

    final NeoLock lck = this;
    TIMEOUT_SCHEDULER.schedule(() -> {
      if (fired.compareAndSet(false, true)) {
        synchronized (lck) {
          lck.queue.remove(guarded);
        }
        cb.fail(new TimeoutException(
            "NeoLock[" + lck.namespace + "].acquire timed out after " + timeoutMs + "ms"));
      }
    }, timeoutMs, TimeUnit.MILLISECONDS);
  }

  /**
   * Acquire the lock, run the {@code criticalSection} synchronously, release. The lock is
   * released even if the critical section throws &mdash; eliminating the standard
   * {@code try / finally / unlock.releaseLock()} boilerplate.
   *
   * <p>The critical section is <strong>synchronous</strong>: it runs to completion on the
   * acquire-callback thread, and the lock is released as soon as it returns or throws. If you
   * need to await async work inside a critical section, use
   * {@link #acquire(Asyncc.IAsyncCallback)} directly so you can decide when to call
   * {@link Unlock#releaseLock()}.
   *
   * <p>Any exception thrown by {@code criticalSection} propagates to the caller of the
   * acquire-callback thread <em>after</em> the lock is released. The lock will never leak due
   * to a sync throw.
   *
   * @since 0.2.5
   */
  public void withLock(final Runnable criticalSection) {
    this.acquire((err, unlock) -> {
      if (err != null) {
        // pre-acquire errors are not currently produced by NeoLock; surface defensively
        return;
      }
      try {
        criticalSection.run();
      } finally {
        unlock.releaseLock();
      }
    });
  }

  public Unlock makeUnlock(final boolean isImmediate) {
    final NeoLock lck = this;
    return new Unlock(isImmediate) {
      @Override
      public void releaseLock() {

        final Asyncc.IAsyncCallback<Unlock, Object> next;

        synchronized (lck) {
          if (!this.callable) {
            // Releasing the same Unlock twice is a programming bug — log it loudly but don't
            // throw past the user's release call site, which is typically in a finally{} block.
            new IllegalStateException(
                "NeoLock[" + lck.namespace + "]: Unlock.releaseLock() called more than once")
                .printStackTrace(System.err);
            return;
          }
          this.callable = false;
          // Dequeue the next waiter, if any, while we still hold the monitor. If we pop a waiter
          // we keep `locked == true` and hand them a fresh Unlock; otherwise we mark the lock as
          // free. This is the critical change: previously `lck.locked = false` happened *before*
          // the queue check, so a second acquirer could observe an unlocked lock and race past
          // the still-pending waiter.
          next = lck.queue.pollFirst();
          if (next == null) {
            lck.locked = false;
          }
        }

        if (next != null) {
          next.done(null, lck.makeUnlock(false));
        }
      }
    };
  }

  public void acquire(final Asyncc.IAsyncCallback<Unlock, Object> cb) {

    final boolean acquireImmediately;

    synchronized (this) {
      if (this.locked) {
        this.queue.addLast(cb);
        return;
      }
      this.locked = true;
      acquireImmediately = true;
    }

    if (acquireImmediately) {
      cb.done(null, this.makeUnlock(true));
    }
  }
}
