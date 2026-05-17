package org.ores.async;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An async (non-blocking) reader/writer lock. Sibling to {@link NeoLock}: same callback-shape
 * acquire pattern (acquire returns an {@link Unlock} token via callback; release happens via
 * that token), but supports two distinct holder modes:
 *
 * <ul>
 *   <li><strong>Read</strong> — multiple concurrent holders allowed when no writer is held.</li>
 *   <li><strong>Write</strong> — exclusive. While a writer is held, no readers and no other
 *       writers are admitted.</li>
 * </ul>
 *
 * <h3>Fairness policy: FIFO with reader-burst</h3>
 *
 * <p>Waiters are dispatched in arrival order. The one twist: when the lock is released and the
 * head of the queue is a reader, <strong>all adjacent queued readers wake up concurrently</strong>.
 * If the queue is {@code [R1, R2, R3, W1, R4]} and the lock becomes free, R1, R2, and R3 are all
 * granted simultaneously. When all three release, W1 runs alone. When W1 releases, R4 proceeds.
 *
 * <p>This is the natural compromise:
 * <ul>
 *   <li>No <strong>reader-preference</strong> (new readers don't jump ahead of queued writers
 *       — that would starve writers under heavy read load).</li>
 *   <li>No <strong>writer-preference</strong> (queued writers don't block new readers when no
 *       writer is currently held — that would starve readers under bursty write workloads).</li>
 *   <li>Adjacent queued readers run together to capture the spirit of "many readers
 *       concurrent" when they happen to be queued together.</li>
 * </ul>
 *
 * <p>One consequence: a single writer behind 1 000 queued readers waits until all 1 000 release.
 * If you can't accept that, use {@link #tryAcquireWrite()} or
 * {@link #acquireWrite(long, Asyncc.IAsyncCallback)} with a bounded timeout.
 *
 * <h3>Usage</h3>
 *
 * <pre>
 *   NeoRwLock cacheLock = new NeoRwLock("config-cache");
 *
 *   // Reader — multiple readers can hold the lock concurrently.
 *   cacheLock.acquireRead((err, unlock) -&gt; {
 *       try {
 *           return cache.get(key);
 *       } finally {
 *           unlock.releaseLock();
 *       }
 *   });
 *
 *   // Writer — exclusive.
 *   cacheLock.acquireWrite((err, unlock) -&gt; {
 *       try {
 *           cache.put(key, value);
 *       } finally {
 *           unlock.releaseLock();
 *       }
 *   });
 *
 *   // Sync helper for read-only critical sections — auto-releases even if the body throws.
 *   cacheLock.withRead(() -&gt; renderTemplate(cache));
 *
 *   // Sync helper for exclusive critical sections.
 *   cacheLock.withWrite(() -&gt; cache.refresh());
 * </pre>
 *
 * <h3>What's <em>not</em> supported</h3>
 *
 * <ul>
 *   <li><strong>Reader-to-writer upgrade</strong> — a holder of a read lock that wants to upgrade
 *       to a write lock must release the read first, then re-acquire as a writer. Two concurrent
 *       readers both trying to upgrade is a classic deadlock; Java's
 *       {@link java.util.concurrent.locks.ReentrantReadWriteLock} doesn't support upgrade either.</li>
 *   <li><strong>Writer-to-reader downgrade</strong> — similarly, release the write first. (The
 *       semantics are easier than upgrade but the API surface isn't worth it for v0.2.6.)</li>
 *   <li><strong>Reentrance</strong> — calling any {@code acquire*} from inside an acquire
 *       callback that has not yet been released will queue the new request behind the current
 *       holder. There is no general way to detect this in an async/callback world (release may
 *       fire on a different thread). Use {@link #tryAcquireRead()} / {@link #tryAcquireWrite()}
 *       if you need a defensive check.</li>
 * </ul>
 *
 * <h3>Concurrency contract</h3>
 *
 * <ul>
 *   <li><strong>Mutual exclusion</strong>: no read holder while a write is held; no write holder
 *       while any read is held.</li>
 *   <li><strong>FIFO across modes</strong>: writes never starve readers and vice versa; once a
 *       writer is at the head of the queue, no new readers are admitted until it gets a turn.</li>
 *   <li><strong>Reader-burst</strong>: adjacent queued readers wake up concurrently.</li>
 *   <li><strong>Timeouts cleanly remove the waiter</strong> from the queue without disturbing
 *       any other waiter or in-flight grant.</li>
 *   <li><strong>Double-release detection</strong>: releasing the same {@link Unlock} twice logs
 *       an {@link IllegalStateException} to {@code System.err} but does not throw past the
 *       release call site (which is typically a {@code finally{}} block).</li>
 * </ul>
 *
 * @see NeoLock
 * @see Unlock
 * @since 0.2.6
 */
public class NeoRwLock {

  /** Shared daemon-threaded scheduler for timeout dispatch. */
  private static final ScheduledExecutorService TIMEOUT_SCHEDULER;
  static {
    final AtomicInteger ids = new AtomicInteger();
    TIMEOUT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
      final Thread t = new Thread(r, "neorwlock-timeout-" + ids.incrementAndGet());
      t.setDaemon(true);
      return t;
    });
  }

  private final String namespace;

  /** All fields below guarded by {@code this} (NeoRwLock instance monitor). */
  private int readers = 0;
  private boolean writerHeld = false;
  private final Deque<Waiter> queue = new ArrayDeque<>();

  /** Internal waiter record. {@code fired} guards against the timeout/grant race. */
  private static final class Waiter {
    final Asyncc.IAsyncCallback<Unlock, Object> cb;
    final boolean isRead;
    final AtomicBoolean fired = new AtomicBoolean();
    Waiter(final Asyncc.IAsyncCallback<Unlock, Object> cb, final boolean isRead) {
      this.cb = cb;
      this.isRead = isRead;
    }
  }

  public NeoRwLock() {
    this(null);
  }

  public NeoRwLock(final String namespace) {
    this.namespace = namespace;
  }

  public String getNamespace() {
    return namespace;
  }

  // ---------------- introspection ---------------------------------------

  /** Snapshot of the number of currently-held read locks. */
  public synchronized int readerCount() {
    return readers;
  }

  /** Snapshot of whether a write lock is currently held. */
  public synchronized boolean isWriteHeld() {
    return writerHeld;
  }

  /** Snapshot of the number of waiters queued behind the current holder(s). */
  public synchronized int queueDepth() {
    return queue.size();
  }

  // ---------------- tryAcquire ------------------------------------------

  /**
   * Non-blocking attempt to acquire a read lock. Returns an {@link Unlock} if the lock was
   * available <em>and there are no queued waiters</em> (to preserve FIFO fairness), or an empty
   * {@link Optional} otherwise.
   */
  public Optional<Unlock> tryAcquireRead() {
    synchronized (this) {
      if (writerHeld || !queue.isEmpty()) {
        return Optional.empty();
      }
      readers++;
    }
    return Optional.of(makeReadUnlock());
  }

  /**
   * Non-blocking attempt to acquire a write lock. Returns an {@link Unlock} if the lock was
   * fully free (no readers, no writer, no queued waiters), or an empty {@link Optional}
   * otherwise.
   */
  public Optional<Unlock> tryAcquireWrite() {
    synchronized (this) {
      if (writerHeld || readers > 0 || !queue.isEmpty()) {
        return Optional.empty();
      }
      writerHeld = true;
    }
    return Optional.of(makeWriteUnlock());
  }

  // ---------------- acquire (no timeout) --------------------------------

  /**
   * Acquire a read lock. If the lock is currently free of writers and no waiter is queued, the
   * callback fires immediately with the {@link Unlock} token. Otherwise the request is queued
   * in arrival order.
   */
  public void acquireRead(final Asyncc.IAsyncCallback<Unlock, Object> cb) {
    final boolean grant;
    synchronized (this) {
      if (!writerHeld && queue.isEmpty()) {
        readers++;
        grant = true;
      } else {
        queue.addLast(new Waiter(cb, true));
        grant = false;
      }
    }
    if (grant) {
      cb.success(makeReadUnlock());
    }
  }

  /**
   * Acquire a write lock. If no readers are held, no writer is held, and no waiter is queued,
   * the callback fires immediately. Otherwise the request is queued.
   */
  public void acquireWrite(final Asyncc.IAsyncCallback<Unlock, Object> cb) {
    final boolean grant;
    synchronized (this) {
      if (!writerHeld && readers == 0 && queue.isEmpty()) {
        writerHeld = true;
        grant = true;
      } else {
        queue.addLast(new Waiter(cb, false));
        grant = false;
      }
    }
    if (grant) {
      cb.success(makeWriteUnlock());
    }
  }

  // ---------------- acquire with timeout --------------------------------

  /**
   * Acquire a read lock with a bounded wait. If not granted within {@code timeoutMs}, fires the
   * callback with a {@link TimeoutException} and cleanly removes the pending waiter from the
   * queue.
   *
   * <p>If {@code timeoutMs <= 0}, equivalent to {@link #tryAcquireRead()}: succeeds immediately
   * if available, otherwise fails with {@link TimeoutException}.
   */
  public void acquireRead(final long timeoutMs, final Asyncc.IAsyncCallback<Unlock, Object> cb) {
    acquireWithTimeout(true, timeoutMs, cb);
  }

  /**
   * Acquire a write lock with a bounded wait. See {@link #acquireRead(long, Asyncc.IAsyncCallback)}.
   */
  public void acquireWrite(final long timeoutMs, final Asyncc.IAsyncCallback<Unlock, Object> cb) {
    acquireWithTimeout(false, timeoutMs, cb);
  }

  private void acquireWithTimeout(
      final boolean isRead,
      final long timeoutMs,
      final Asyncc.IAsyncCallback<Unlock, Object> cb) {

    if (timeoutMs <= 0) {
      final Optional<Unlock> u = isRead ? tryAcquireRead() : tryAcquireWrite();
      if (u.isPresent()) {
        cb.success(u.get());
      } else {
        cb.fail(new TimeoutException(
            "NeoRwLock[" + namespace + "].acquire" + (isRead ? "Read" : "Write")
                + "(0) failed: lock is contended"));
      }
      return;
    }

    final AtomicBoolean fired = new AtomicBoolean();
    final Asyncc.IAsyncCallback<Unlock, Object> guarded = (err, unlock) -> {
      if (fired.compareAndSet(false, true)) {
        cb.done(err, unlock);
      } else if (unlock != null) {
        // Timeout already fired. The user already received TimeoutException; release the
        // late-arrival lock so it isn't stranded.
        unlock.releaseLock();
      }
    };

    final Waiter w = new Waiter(guarded, isRead);
    final boolean grant;
    synchronized (this) {
      if (isRead) {
        if (!writerHeld && queue.isEmpty()) {
          readers++;
          grant = true;
        } else {
          queue.addLast(w);
          grant = false;
        }
      } else {
        if (!writerHeld && readers == 0 && queue.isEmpty()) {
          writerHeld = true;
          grant = true;
        } else {
          queue.addLast(w);
          grant = false;
        }
      }
    }

    if (grant) {
      guarded.success(isRead ? makeReadUnlock() : makeWriteUnlock());
      return;
    }

    final NeoRwLock self = this;
    TIMEOUT_SCHEDULER.schedule(() -> {
      if (fired.compareAndSet(false, true)) {
        synchronized (self) {
          self.queue.remove(w);
        }
        cb.fail(new TimeoutException(
            "NeoRwLock[" + namespace + "].acquire" + (isRead ? "Read" : "Write")
                + " timed out after " + timeoutMs + "ms"));
      }
    }, timeoutMs, TimeUnit.MILLISECONDS);
  }

  // ---------------- withRead / withWrite -------------------------------

  /**
   * Acquire a read lock, run the (synchronous) {@code criticalSection}, release. The lock is
   * released even if the critical section throws.
   *
   * <p>For async critical sections, use {@link #acquireRead(Asyncc.IAsyncCallback)} directly so
   * you can call {@link Unlock#releaseLock()} at the right point in your callback chain.
   */
  public void withRead(final Runnable criticalSection) {
    acquireRead((err, unlock) -> {
      if (err != null) return;
      try {
        criticalSection.run();
      } finally {
        unlock.releaseLock();
      }
    });
  }

  /**
   * Acquire a write lock, run the (synchronous) {@code criticalSection}, release. The lock is
   * released even if the critical section throws.
   */
  public void withWrite(final Runnable criticalSection) {
    acquireWrite((err, unlock) -> {
      if (err != null) return;
      try {
        criticalSection.run();
      } finally {
        unlock.releaseLock();
      }
    });
  }

  // ---------------- Unlock token factories ------------------------------

  private Unlock makeReadUnlock() {
    final NeoRwLock self = this;
    return new Unlock(false) {
      @Override
      public void releaseLock() {
        synchronized (self) {
          if (!this.callable) {
            new IllegalStateException(
                "NeoRwLock[" + namespace + "]: read Unlock.releaseLock() called more than once")
                .printStackTrace(System.err);
            return;
          }
          this.callable = false;
          self.readers--;
        }
        self.dispatchFromQueue();
      }
    };
  }

  private Unlock makeWriteUnlock() {
    final NeoRwLock self = this;
    return new Unlock(false) {
      @Override
      public void releaseLock() {
        synchronized (self) {
          if (!this.callable) {
            new IllegalStateException(
                "NeoRwLock[" + namespace + "]: write Unlock.releaseLock() called more than once")
                .printStackTrace(System.err);
            return;
          }
          this.callable = false;
          self.writerHeld = false;
        }
        self.dispatchFromQueue();
      }
    };
  }

  /**
   * Dispatch the next batch of waiters from the queue head. Called after each release.
   *
   * <p>The dispatch policy:
   * <ul>
   *   <li>If a writer is still held (shouldn't happen post-release, but defensive), nothing.</li>
   *   <li>If readers are still held (post-{@code releaseRead} when more readers remain), only
   *       a head-of-queue reader-burst can proceed (writers must wait until all readers
   *       release). Actually: in this case we don't even dispatch — the next dispatch will be
   *       triggered by whichever reader is the last to release. (Optimization: we could
   *       dispatch a queued reader burst here too, but that complicates the contract; see the
   *       trickle-reader scenario in the tests.)</li>
   *   <li>If fully free: if the head is a reader, dispatch the burst; if the head is a writer,
   *       dispatch just it.</li>
   * </ul>
   */
  private void dispatchFromQueue() {
    final List<Granted> toGrant = new ArrayList<>();

    synchronized (this) {
      if (writerHeld || readers > 0) {
        return;
      }
      while (true) {
        final Waiter head = queue.peekFirst();
        if (head == null) break;
        if (head.isRead) {
          queue.pollFirst();
          readers++;
          toGrant.add(new Granted(head, true));
          // continue draining adjacent readers
        } else {
          if (toGrant.isEmpty()) {
            queue.pollFirst();
            writerHeld = true;
            toGrant.add(new Granted(head, false));
          }
          break;
        }
      }
    }

    for (final Granted g : toGrant) {
      try {
        g.waiter.cb.success(g.isRead ? makeReadUnlock() : makeWriteUnlock());
      } catch (Throwable t) {
        // A misbehaving user callback should not strand other granted waiters.
        new RuntimeException(
            "NeoRwLock[" + namespace + "]: granted-callback threw; other waiters proceed", t)
            .printStackTrace(System.err);
      }
    }
  }

  private static final class Granted {
    final Waiter waiter;
    final boolean isRead;
    Granted(final Waiter w, final boolean isRead) {
      this.waiter = w;
      this.isRead = isRead;
    }
  }
}
