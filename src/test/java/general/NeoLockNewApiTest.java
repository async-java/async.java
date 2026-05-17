package general;

import org.junit.Test;
import org.ores.async.NeoLock;
import org.ores.async.Unlock;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the v0.2.5 additions to {@link NeoLock}:
 *
 * <ul>
 *   <li>{@link NeoLock#isLocked()} and {@link NeoLock#queueDepth()} introspection.</li>
 *   <li>{@link NeoLock#tryAcquire()} non-blocking attempt.</li>
 *   <li>{@link NeoLock#acquire(long, org.ores.async.Asyncc.IAsyncCallback)} bounded wait.</li>
 *   <li>{@link NeoLock#withLock(Runnable)} sync critical section with leak-safe release.</li>
 * </ul>
 */
public class NeoLockNewApiTest {

  private static final int TIMEOUT_MS = 15_000;

  @Test(timeout = TIMEOUT_MS)
  public void isLocked_false_initially_true_while_held() throws Exception {
    final NeoLock lock = new NeoLock("introspect-test");
    assertFalse("brand-new lock should not be held", lock.isLocked());

    final Optional<Unlock> u = lock.tryAcquire();
    assertTrue("tryAcquire should succeed on a free lock", u.isPresent());
    assertTrue("lock should be held after tryAcquire", lock.isLocked());

    u.get().releaseLock();
    assertFalse("lock should be free after release", lock.isLocked());
  }

  @Test(timeout = TIMEOUT_MS)
  public void queueDepth_reflects_waiters() throws Exception {
    final NeoLock lock = new NeoLock("queuedepth-test");

    final AtomicReference<Unlock> firstUnlock = new AtomicReference<>();
    final CountDownLatch firstAcquired = new CountDownLatch(1);
    lock.acquire((err, unlock) -> {
      firstUnlock.set(unlock);
      firstAcquired.countDown();
    });
    assertTrue(firstAcquired.await(2, TimeUnit.SECONDS));
    assertEquals("no waiters yet", 0, lock.queueDepth());

    for (int i = 0; i < 5; i++) {
      lock.acquire((err, unlock) -> {
        if (unlock != null) unlock.releaseLock();
      });
    }
    assertEquals("5 waiters now", 5, lock.queueDepth());

    firstUnlock.get().releaseLock();
    // wait for queue to drain
    final long deadline = System.currentTimeMillis() + 5_000;
    while (lock.queueDepth() > 0 && System.currentTimeMillis() < deadline) {
      Thread.sleep(5);
    }
    assertEquals("queue drained", 0, lock.queueDepth());
    assertFalse("lock free after queue drained", lock.isLocked());
  }

  @Test(timeout = TIMEOUT_MS)
  public void tryAcquire_returns_empty_when_held() throws Exception {
    final NeoLock lock = new NeoLock("try-test");
    final Optional<Unlock> first = lock.tryAcquire();
    assertTrue(first.isPresent());

    final Optional<Unlock> second = lock.tryAcquire();
    assertFalse("second tryAcquire on held lock should fail", second.isPresent());

    first.get().releaseLock();
    final Optional<Unlock> third = lock.tryAcquire();
    assertTrue("tryAcquire after release should succeed", third.isPresent());
    third.get().releaseLock();
  }

  @Test(timeout = TIMEOUT_MS)
  public void acquire_with_zero_timeout_is_tryAcquire() throws Exception {
    final NeoLock lock = new NeoLock("zero-timeout-test");

    final AtomicReference<Unlock> got = new AtomicReference<>();
    lock.acquire(0L, (err, unlock) -> got.set(unlock));
    assertNotNull("zero-timeout on free lock should succeed", got.get());

    // Now the lock is held; zero-timeout should fail immediately.
    final AtomicReference<Object> err2 = new AtomicReference<>();
    final AtomicReference<Unlock> got2 = new AtomicReference<>();
    lock.acquire(0L, (err, unlock) -> {
      err2.set(err);
      got2.set(unlock);
    });
    assertNull("no unlock when zero-timeout on held lock", got2.get());
    assertTrue("err should be TimeoutException", err2.get() instanceof TimeoutException);

    got.get().releaseLock();
  }

  @Test(timeout = TIMEOUT_MS)
  public void acquire_with_timeout_succeeds_when_available_in_time() throws Exception {
    final NeoLock lock = new NeoLock("timeout-success-test");

    final Optional<Unlock> holder = lock.tryAcquire();
    assertTrue(holder.isPresent());

    final CountDownLatch acquired = new CountDownLatch(1);
    final AtomicReference<Object> errSink = new AtomicReference<>();
    final AtomicReference<Unlock> unlockSink = new AtomicReference<>();

    lock.acquire(500L, (err, unlock) -> {
      errSink.set(err);
      unlockSink.set(unlock);
      acquired.countDown();
    });

    // Release within the timeout window.
    Thread.sleep(50);
    holder.get().releaseLock();

    assertTrue("acquire should complete within timeout", acquired.await(2, TimeUnit.SECONDS));
    assertNull("no timeout error", errSink.get());
    assertNotNull("got the unlock", unlockSink.get());
    unlockSink.get().releaseLock();
  }

  @Test(timeout = TIMEOUT_MS)
  public void acquire_with_timeout_fails_when_lock_never_released() throws Exception {
    final NeoLock lock = new NeoLock("timeout-fail-test");
    final Optional<Unlock> holder = lock.tryAcquire();
    assertTrue(holder.isPresent());

    final CountDownLatch done = new CountDownLatch(1);
    final AtomicReference<Object> errSink = new AtomicReference<>();
    final AtomicReference<Unlock> unlockSink = new AtomicReference<>();

    final long t0 = System.currentTimeMillis();
    lock.acquire(150L, (err, unlock) -> {
      errSink.set(err);
      unlockSink.set(unlock);
      done.countDown();
    });

    assertTrue("timeout should fire", done.await(3, TimeUnit.SECONDS));
    final long elapsed = System.currentTimeMillis() - t0;
    assertTrue("elapsed (" + elapsed + ") should be >= 150ms", elapsed >= 140);
    assertNull("no unlock on timeout", unlockSink.get());
    assertTrue("err should be TimeoutException", errSink.get() instanceof TimeoutException);

    // After timeout, the waiter should NOT be in the queue anymore — releasing the holder
    // should put the lock back into the free state.
    holder.get().releaseLock();
    assertFalse("lock should be free after holder releases (no stale waiter)", lock.isLocked());
    assertEquals("queue should be empty", 0, lock.queueDepth());
  }

  @Test(timeout = TIMEOUT_MS)
  public void withLock_runs_critical_section_and_releases() throws Exception {
    final NeoLock lock = new NeoLock("withLock-test");
    final AtomicInteger ran = new AtomicInteger();

    lock.withLock(ran::incrementAndGet);

    // Give the async callback a moment to complete (acquire is synchronous when free)
    Thread.sleep(20);

    assertEquals("critical section ran once", 1, ran.get());
    assertFalse("lock released after withLock", lock.isLocked());
  }

  @Test(timeout = TIMEOUT_MS)
  public void withLock_releases_lock_even_when_body_throws() throws Exception {
    final NeoLock lock = new NeoLock("withLock-throw-test");
    final AtomicInteger ran = new AtomicInteger();

    try {
      lock.withLock(() -> {
        ran.incrementAndGet();
        throw new RuntimeException("boom");
      });
      fail("expected the RuntimeException to propagate");
    } catch (RuntimeException re) {
      assertEquals("boom", re.getMessage());
    }

    Thread.sleep(20);
    assertEquals("critical section ran once even though it threw", 1, ran.get());
    assertFalse("lock released even after exception", lock.isLocked());
    assertEquals("no leaked waiters", 0, lock.queueDepth());

    // Sanity: we can still acquire afterwards.
    final Optional<Unlock> u = lock.tryAcquire();
    assertTrue("can re-acquire after throw", u.isPresent());
    u.get().releaseLock();
  }

  @Test(timeout = TIMEOUT_MS)
  public void timeout_loser_does_not_leak_lock_when_winner_arrives_after_timeout() throws Exception {
    // The "winner-arrives-after-timeout" race: timeout fires and removes the waiter, but the
    // holder happens to release at the exact same moment, granting to whatever was at the head
    // of the queue. The dedup-guard in acquire(timeoutMs) must release the late-arrival lock
    // so it's not stranded.
    final NeoLock lock = new NeoLock("race-test");
    final Optional<Unlock> holder = lock.tryAcquire();
    assertTrue(holder.isPresent());

    final CountDownLatch done = new CountDownLatch(1);
    final AtomicReference<Object> errSink = new AtomicReference<>();
    lock.acquire(50L, (err, unlock) -> {
      errSink.set(err);
      done.countDown();
    });

    assertTrue(done.await(3, TimeUnit.SECONDS));
    assertTrue(errSink.get() instanceof TimeoutException);

    // Now release the holder. The waiter (already-removed) must NOT receive the lock.
    holder.get().releaseLock();
    Thread.sleep(30);
    assertFalse("lock must be free; nobody to grant to", lock.isLocked());
    assertEquals("no waiters", 0, lock.queueDepth());
  }
}
