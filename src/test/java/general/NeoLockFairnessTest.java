package general;

import org.junit.Test;
import org.ores.async.NeoLock;
import org.ores.async.Unlock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins {@link NeoLock}'s FIFO fairness guarantee.
 *
 * <p>The internal waiter queue is an {@link java.util.ArrayDeque} popped from the head and
 * pushed to the tail. The fairness contract: <em>acquirers receive the lock in the order they
 * called {@code acquire(...)}</em>, regardless of the thread that the eventual unlock callback
 * fires on.
 *
 * <p>This test sequentially calls {@code acquire(...)} 100 times (one call per loop iteration,
 * single-threaded so the call order is deterministic) while the lock is held, then releases
 * the lock and verifies each acquirer's callback fired in submission order.
 *
 * @since 0.2.5
 */
public class NeoLockFairnessTest {

  private static final int TIMEOUT_MS = 30_000;

  @Test(timeout = TIMEOUT_MS)
  public void acquire_order_equals_request_order_for_100_waiters() throws Exception {
    final NeoLock lock = new NeoLock("fairness-test");
    final int n = 100;

    // Take the lock first so all subsequent acquires queue.
    final CountDownLatch firstAcquired = new CountDownLatch(1);
    final Unlock[] firstUnlockHolder = new Unlock[1];
    lock.acquire((err, unlock) -> {
      firstUnlockHolder[0] = unlock;
      firstAcquired.countDown();
    });
    assertTrue("first acquire should succeed immediately", firstAcquired.await(2, TimeUnit.SECONDS));

    // Now request the lock n times. Each request appends its index to acquireOrder
    // when the callback fires. The lock is still held by firstUnlockHolder so all queue up.
    final List<Integer> acquireOrder = new ArrayList<>();
    final CountDownLatch allDone = new CountDownLatch(n);

    for (int i = 0; i < n; i++) {
      final int idx = i;
      lock.acquire((err, unlock) -> {
        synchronized (acquireOrder) {
          acquireOrder.add(idx);
        }
        unlock.releaseLock();
        allDone.countDown();
      });
    }

    // Release the initial lock — the queue cascades.
    firstUnlockHolder[0].releaseLock();
    assertTrue("all waiters should complete", allDone.await(20, TimeUnit.SECONDS));

    assertEquals("FIFO: acquire count", n, acquireOrder.size());
    for (int i = 0; i < n; i++) {
      assertEquals("FIFO: position " + i, Integer.valueOf(i), acquireOrder.get(i));
    }
  }

  /**
   * 1 000-acquirer stress test: no lost wakeups, no double-hold, no leaked locks.
   *
   * <p>Each acquirer increments a shared counter inside its critical section. With FIFO
   * fairness and mutual exclusion, the final counter must equal n. If two acquirers held the
   * lock at once, an interleaving of {@code counter++} could lose increments; if any wakeup
   * was lost, the corresponding acquirer's callback would never fire and {@code allDone}
   * would never count down.
   */
  @Test(timeout = TIMEOUT_MS)
  public void stress_1000_acquirers_no_lost_wakeups() throws Exception {
    final NeoLock lock = new NeoLock("stress-test");
    final int n = 1_000;
    final AtomicInteger inCritical = new AtomicInteger();
    final AtomicInteger maxInCritical = new AtomicInteger();
    final AtomicInteger counter = new AtomicInteger();
    final CountDownLatch allDone = new CountDownLatch(n);

    for (int i = 0; i < n; i++) {
      lock.acquire((err, unlock) -> {
        // critical section: there should never be more than one of us in here at a time
        final int now = inCritical.incrementAndGet();
        maxInCritical.accumulateAndGet(now, Math::max);
        counter.incrementAndGet();
        inCritical.decrementAndGet();
        unlock.releaseLock();
        allDone.countDown();
      });
    }

    assertTrue("all 1000 acquirers should complete", allDone.await(30, TimeUnit.SECONDS));
    assertEquals("counter must equal n (no lost wakeups, no double-hold)", n, counter.get());
    assertEquals("never more than 1 holder at a time (mutual exclusion)", 1, maxInCritical.get());
  }
}
