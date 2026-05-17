package org.ores.async;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * An async (non-blocking) mutex.
 *
 * <p>Production-readiness changes relative to the original implementation:
 * <ul>
 *   <li>The {@code Unlock} returned by {@link #makeUnlock(boolean)} previously mutated
 *       {@code callable} under the <em>inner Unlock instance's</em> monitor while also mutating
 *       {@code lck.locked} (a field on the enclosing {@code NeoLock}) outside that lock. Under
 *       contention a thread could observe {@code locked==false} and acquire the mutex before the
 *       releasing thread had dequeued the next waiter, producing two concurrent holders of the
 *       same lock. All state transitions are now performed under the enclosing {@code NeoLock}'s
 *       monitor.</li>
 *   <li>The waiter queue was a plain {@link java.util.ArrayList} mutated from multiple threads
 *       without synchronisation; switched to a properly-synchronised {@link ArrayDeque} guarded
 *       by the same monitor.</li>
 *   <li>{@code throw new Error(...)} replaced with {@code IllegalStateException} on duplicate
 *       release.</li>
 * </ul>
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
