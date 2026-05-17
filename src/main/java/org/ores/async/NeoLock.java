package org.ores.async;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * An async (non-blocking) mutex.
 *
 * <p>Unlike {@code synchronized} blocks and {@link java.util.concurrent.locks.ReentrantLock},
 * {@code NeoLock} <strong>does not park a thread</strong> while waiting. Waiters enqueue an
 * error-first callback; when the lock becomes free, the next waiter's callback fires with a fresh
 * {@link Unlock} token. This makes it suitable for callback chains where the acquire and the
 * release happen on different threads (or different virtual threads), which is the common case
 * with async I/O.
 *
 * <h3>Why not just use {@code synchronized}?</h3>
 *
 * <p>Two reasons:
 * <ol>
 *   <li><strong>Cross-thread release.</strong> A {@code synchronized} block must be released by
 *       the same thread that acquired it. Inside a callback chain, the release usually happens
 *       inside an async completion handler running on a different worker. {@code NeoLock} models
 *       this directly: acquire returns a token via callback, release happens via that token.</li>
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
 * <p>Calling {@code unlock.releaseLock()} a second time is a programming bug and logs a stack
 * trace to {@code System.err} but does not throw past the call site (which is usually a
 * {@code finally} block where re-throwing would mask the real error).
 *
 * <h3>Concurrency contract (v0.2.x)</h3>
 *
 * <ul>
 *   <li>The waiter queue and the {@code locked} flag are <em>both</em> guarded by the
 *       {@code NeoLock} instance's monitor. (Earlier versions split those across two locks,
 *       allowing two concurrent acquirers in a tight race &mdash; fixed in v0.2.0.)</li>
 *   <li>Waiter queue is an {@link ArrayDeque}, popped from the head, pushed to the tail &mdash;
 *       FIFO ordering of pending acquirers.</li>
 *   <li>{@link Unlock} is a public sibling class (in v0.2.2+) so {@code NeoLock} is usable from
 *       any package.</li>
 * </ul>
 *
 * @see Unlock
 * @see NeoQueue
 */
public class NeoLock {

  private final String namespace;
  // All three of these fields are guarded by `this` (the NeoLock instance's monitor).
  private boolean locked = false;
  private final Deque<Asyncc.IAsyncCallback<Unlock, Object>> queue = new ArrayDeque<>();

  public NeoLock(final String name) {
    this.namespace = name;
  }

  public String getNamespace() {
    return namespace;
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
