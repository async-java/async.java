package general;

import org.junit.Test;
import org.ores.async.NeoRwLock;
import org.ores.async.Unlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the contract of {@link NeoRwLock}:
 *
 * <ul>
 *   <li>Mutual exclusion: never a reader concurrent with a writer; never two writers.</li>
 *   <li>FIFO across modes: writers don't starve readers and vice versa.</li>
 *   <li>Reader-burst: adjacent queued readers wake up concurrently.</li>
 *   <li>tryAcquireRead/Write semantics.</li>
 *   <li>Timeout cleanly removes the waiter.</li>
 *   <li>withRead/withWrite release the lock even on throw.</li>
 *   <li>Diagnostics: readerCount, isWriteHeld, queueDepth.</li>
 * </ul>
 */
public class NeoRwLockTest {

  private static final int TIMEOUT_MS = 30_000;

  // ------------------------------------------------------------------------
  // Basic happy path
  // ------------------------------------------------------------------------

  @Test(timeout = TIMEOUT_MS)
  public void tryAcquireRead_succeeds_on_free_lock() {
    final NeoRwLock lock = new NeoRwLock("t1");
    final Optional<Unlock> u = lock.tryAcquireRead();
    assertTrue(u.isPresent());
    assertEquals(1, lock.readerCount());
    assertFalse(lock.isWriteHeld());
    u.get().releaseLock();
    assertEquals(0, lock.readerCount());
  }

  @Test(timeout = TIMEOUT_MS)
  public void tryAcquireWrite_succeeds_on_free_lock() {
    final NeoRwLock lock = new NeoRwLock("t2");
    final Optional<Unlock> u = lock.tryAcquireWrite();
    assertTrue(u.isPresent());
    assertTrue(lock.isWriteHeld());
    assertEquals(0, lock.readerCount());
    u.get().releaseLock();
    assertFalse(lock.isWriteHeld());
  }

  @Test(timeout = TIMEOUT_MS)
  public void multiple_readers_concurrent() {
    final NeoRwLock lock = new NeoRwLock("t3");
    final Optional<Unlock> r1 = lock.tryAcquireRead();
    final Optional<Unlock> r2 = lock.tryAcquireRead();
    final Optional<Unlock> r3 = lock.tryAcquireRead();
    assertTrue(r1.isPresent());
    assertTrue(r2.isPresent());
    assertTrue(r3.isPresent());
    assertEquals(3, lock.readerCount());
    r1.get().releaseLock();
    r2.get().releaseLock();
    r3.get().releaseLock();
    assertEquals(0, lock.readerCount());
  }

  @Test(timeout = TIMEOUT_MS)
  public void writer_blocks_subsequent_readers() {
    final NeoRwLock lock = new NeoRwLock("t4");
    final Optional<Unlock> w = lock.tryAcquireWrite();
    assertTrue(w.isPresent());
    final Optional<Unlock> r = lock.tryAcquireRead();
    assertFalse("readers cannot acquire while writer held", r.isPresent());
    w.get().releaseLock();
  }

  @Test(timeout = TIMEOUT_MS)
  public void reader_blocks_writer() {
    final NeoRwLock lock = new NeoRwLock("t5");
    final Optional<Unlock> r = lock.tryAcquireRead();
    assertTrue(r.isPresent());
    final Optional<Unlock> w = lock.tryAcquireWrite();
    assertFalse("writer cannot acquire while reader held", w.isPresent());
    r.get().releaseLock();
  }

  // ------------------------------------------------------------------------
  // FIFO across modes + reader-burst
  // ------------------------------------------------------------------------

  @Test(timeout = TIMEOUT_MS)
  public void readers_in_burst_acquire_simultaneously() throws Exception {
    final NeoRwLock lock = new NeoRwLock("burst-test");

    // Hold a write lock so all subsequent readers queue up.
    final Optional<Unlock> w = lock.tryAcquireWrite();
    assertTrue(w.isPresent());

    final int n = 50;
    final CountDownLatch allAcquired = new CountDownLatch(n);
    final AtomicInteger maxConcurrent = new AtomicInteger();
    final AtomicInteger nowConcurrent = new AtomicInteger();
    final List<Unlock> unlocks = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < n; i++) {
      lock.acquireRead((err, unlock) -> {
        final int now = nowConcurrent.incrementAndGet();
        maxConcurrent.accumulateAndGet(now, Math::max);
        unlocks.add(unlock);
        allAcquired.countDown();
      });
    }

    assertEquals("all readers queued behind the writer", n, lock.queueDepth());

    w.get().releaseLock();

    assertTrue("all readers should grant", allAcquired.await(10, TimeUnit.SECONDS));
    assertEquals(
        "reader-burst: all " + n + " readers should hold concurrently after the writer releases",
        n, maxConcurrent.get());

    for (final Unlock u : unlocks) {
      nowConcurrent.decrementAndGet();
      u.releaseLock();
    }
    assertEquals(0, lock.readerCount());
  }

  @Test(timeout = TIMEOUT_MS)
  public void writers_dispatched_one_at_a_time_in_FIFO_order() throws Exception {
    final NeoRwLock lock = new NeoRwLock("write-fifo");

    // Hold a write lock so subsequent writers queue.
    final Optional<Unlock> w0 = lock.tryAcquireWrite();
    assertTrue(w0.isPresent());

    final int n = 20;
    final List<Integer> acquireOrder = Collections.synchronizedList(new ArrayList<>());
    final CountDownLatch done = new CountDownLatch(n);

    for (int i = 0; i < n; i++) {
      final int idx = i;
      lock.acquireWrite((err, unlock) -> {
        acquireOrder.add(idx);
        unlock.releaseLock();
        done.countDown();
      });
    }

    w0.get().releaseLock();
    assertTrue(done.await(10, TimeUnit.SECONDS));
    for (int i = 0; i < n; i++) {
      assertEquals("writer #" + i + " acquired in FIFO order", Integer.valueOf(i), acquireOrder.get(i));
    }
  }

  @Test(timeout = TIMEOUT_MS)
  public void writer_in_middle_of_queue_blocks_later_readers() throws Exception {
    // queue layout: [R1, R2, R3, W4, R5, R6]
    // expected: writer releases the lock; R1+R2+R3 burst; when they release, W4 alone; when
    // W4 releases, R5+R6 burst.
    final NeoRwLock lock = new NeoRwLock("middle-writer");

    final Optional<Unlock> holder = lock.tryAcquireWrite();
    assertTrue(holder.isPresent());

    final List<String> order = Collections.synchronizedList(new ArrayList<>());
    final CountDownLatch done = new CountDownLatch(6);
    final AtomicReference<Unlock> w4Unlock = new AtomicReference<>();

    final Runnable readEnter = () -> {};
    final List<Unlock> firstBurstUnlocks = Collections.synchronizedList(new ArrayList<>());
    final List<Unlock> secondBurstUnlocks = Collections.synchronizedList(new ArrayList<>());

    lock.acquireRead((err, unlock) -> { order.add("R1"); firstBurstUnlocks.add(unlock); done.countDown(); });
    lock.acquireRead((err, unlock) -> { order.add("R2"); firstBurstUnlocks.add(unlock); done.countDown(); });
    lock.acquireRead((err, unlock) -> { order.add("R3"); firstBurstUnlocks.add(unlock); done.countDown(); });
    lock.acquireWrite((err, unlock) -> { order.add("W4"); w4Unlock.set(unlock); done.countDown(); });
    lock.acquireRead((err, unlock) -> { order.add("R5"); secondBurstUnlocks.add(unlock); done.countDown(); });
    lock.acquireRead((err, unlock) -> { order.add("R6"); secondBurstUnlocks.add(unlock); done.countDown(); });

    holder.get().releaseLock();

    // Wait for R1/R2/R3 to all be granted; W4 must still be queued behind them.
    final long deadline = System.currentTimeMillis() + 5_000;
    while (lock.readerCount() < 3 && System.currentTimeMillis() < deadline) {
      Thread.sleep(2);
    }
    assertEquals("first burst: 3 readers concurrent", 3, lock.readerCount());
    assertFalse("W4 not yet granted", lock.isWriteHeld());
    assertEquals("W4 + R5 + R6 still queued", 3, lock.queueDepth());

    // Release the first burst; W4 should be granted next.
    for (final Unlock u : firstBurstUnlocks) u.releaseLock();
    while (!lock.isWriteHeld() && System.currentTimeMillis() < deadline) Thread.sleep(2);
    assertTrue("W4 granted after R1/R2/R3 release", lock.isWriteHeld());
    assertEquals(0, lock.readerCount());
    assertEquals("R5 + R6 still queued", 2, lock.queueDepth());

    // Release W4; R5 + R6 should burst.
    w4Unlock.get().releaseLock();
    while (lock.readerCount() < 2 && System.currentTimeMillis() < deadline) Thread.sleep(2);
    assertEquals("second burst: 2 readers concurrent", 2, lock.readerCount());

    for (final Unlock u : secondBurstUnlocks) u.releaseLock();

    assertTrue("all 6 callbacks fired", done.await(2, TimeUnit.SECONDS));
    assertEquals(List.of("R1", "R2", "R3", "W4", "R5", "R6"), order);
    assertEquals(0, lock.readerCount());
    assertFalse(lock.isWriteHeld());
    assertEquals(0, lock.queueDepth());
  }

  // ------------------------------------------------------------------------
  // Mutual exclusion stress
  // ------------------------------------------------------------------------

  @Test(timeout = TIMEOUT_MS)
  public void mutex_invariants_under_mixed_load() throws Exception {
    // Dispatch handlers onto an executor so the "work" portion (Thread.sleep) doesn't block
    // the main thread, allowing readers to genuinely overlap.
    final java.util.concurrent.ExecutorService exec =
        java.util.concurrent.Executors.newFixedThreadPool(16);
    try {
      final NeoRwLock lock = new NeoRwLock("mutex-stress");
      final int n = 500;
      final AtomicInteger readers = new AtomicInteger();
      final AtomicInteger writers = new AtomicInteger();
      final AtomicInteger maxReaders = new AtomicInteger();
      final AtomicReference<String> violation = new AtomicReference<>();
      final CountDownLatch done = new CountDownLatch(n);

      for (int i = 0; i < n; i++) {
        final boolean isRead = (i % 3 != 0); // 2/3 reads, 1/3 writes
        if (isRead) {
          lock.acquireRead((err, unlock) -> exec.submit(() -> {
            final int r = readers.incrementAndGet();
            maxReaders.accumulateAndGet(r, Math::max);
            if (writers.get() > 0) {
              violation.compareAndSet(null, "reader entered while writer held");
            }
            try { Thread.sleep(1); } catch (InterruptedException ie) { /* */ }
            readers.decrementAndGet();
            unlock.releaseLock();
            done.countDown();
          }));
        } else {
          lock.acquireWrite((err, unlock) -> exec.submit(() -> {
            final int w = writers.incrementAndGet();
            if (w != 1) violation.compareAndSet(null, "two writers concurrent");
            if (readers.get() > 0) {
              violation.compareAndSet(null, "writer entered while reader held");
            }
            try { Thread.sleep(1); } catch (InterruptedException ie) { /* */ }
            writers.decrementAndGet();
            unlock.releaseLock();
            done.countDown();
          }));
        }
      }

      assertTrue("all " + n + " complete", done.await(20, TimeUnit.SECONDS));
      assertNull("mutual exclusion violation: " + violation.get(), violation.get());
      assertEquals(0, lock.readerCount());
      assertFalse(lock.isWriteHeld());
      assertEquals(0, lock.queueDepth());
      // Sanity: under mixed load with cap-free read concurrency, multiple readers should overlap.
      assertTrue("max-readers observed: " + maxReaders.get(), maxReaders.get() >= 2);
    } finally {
      exec.shutdown();
      exec.awaitTermination(2, TimeUnit.SECONDS);
    }
  }

  // ------------------------------------------------------------------------
  // Timeouts
  // ------------------------------------------------------------------------

  @Test(timeout = TIMEOUT_MS)
  public void acquireRead_with_timeout_fails_when_writer_held() throws Exception {
    final NeoRwLock lock = new NeoRwLock("timeout-read");
    final Optional<Unlock> w = lock.tryAcquireWrite();
    assertTrue(w.isPresent());

    final CountDownLatch done = new CountDownLatch(1);
    final AtomicReference<Object> err = new AtomicReference<>();
    final AtomicReference<Unlock> got = new AtomicReference<>();
    lock.acquireRead(100L, (e, u) -> {
      err.set(e);
      got.set(u);
      done.countDown();
    });

    assertTrue(done.await(3, TimeUnit.SECONDS));
    assertTrue("err should be TimeoutException", err.get() instanceof TimeoutException);
    assertNull(got.get());

    // Releasing the writer must not strand the timed-out waiter.
    w.get().releaseLock();
    Thread.sleep(50);
    assertEquals("queue empty after timeout-and-release", 0, lock.queueDepth());
    assertEquals(0, lock.readerCount());
  }

  @Test(timeout = TIMEOUT_MS)
  public void acquireWrite_with_timeout_succeeds_when_reader_releases_in_time() throws Exception {
    final NeoRwLock lock = new NeoRwLock("timeout-write");
    final Optional<Unlock> r = lock.tryAcquireRead();
    assertTrue(r.isPresent());

    final CountDownLatch acquired = new CountDownLatch(1);
    final AtomicReference<Unlock> got = new AtomicReference<>();
    final AtomicReference<Object> err = new AtomicReference<>();
    lock.acquireWrite(500L, (e, u) -> {
      err.set(e);
      got.set(u);
      acquired.countDown();
    });

    Thread.sleep(50);
    r.get().releaseLock();

    assertTrue(acquired.await(2, TimeUnit.SECONDS));
    assertNull(err.get());
    assertNotNull(got.get());
    got.get().releaseLock();
  }

  // ------------------------------------------------------------------------
  // withRead / withWrite
  // ------------------------------------------------------------------------

  @Test(timeout = TIMEOUT_MS)
  public void withRead_releases_lock_even_when_body_throws() throws Exception {
    final NeoRwLock lock = new NeoRwLock("withRead-throw");
    try {
      lock.withRead(() -> { throw new RuntimeException("boom-read"); });
      fail("expected RuntimeException");
    } catch (RuntimeException re) {
      assertEquals("boom-read", re.getMessage());
    }
    Thread.sleep(20);
    assertEquals(0, lock.readerCount());
    assertFalse(lock.isWriteHeld());
  }

  @Test(timeout = TIMEOUT_MS)
  public void withWrite_releases_lock_even_when_body_throws() throws Exception {
    final NeoRwLock lock = new NeoRwLock("withWrite-throw");
    try {
      lock.withWrite(() -> { throw new RuntimeException("boom-write"); });
      fail("expected RuntimeException");
    } catch (RuntimeException re) {
      assertEquals("boom-write", re.getMessage());
    }
    Thread.sleep(20);
    assertEquals(0, lock.readerCount());
    assertFalse(lock.isWriteHeld());
  }

  @Test(timeout = TIMEOUT_MS)
  public void withRead_then_withWrite_run_serially() throws Exception {
    final NeoRwLock lock = new NeoRwLock("withSerial");
    final AtomicInteger order = new AtomicInteger();
    final AtomicInteger readOrder = new AtomicInteger();
    final AtomicInteger writeOrder = new AtomicInteger();

    lock.withRead(() -> readOrder.set(order.incrementAndGet()));
    lock.withWrite(() -> writeOrder.set(order.incrementAndGet()));

    // Allow callback chain to settle
    Thread.sleep(30);
    assertEquals(1, readOrder.get());
    assertEquals(2, writeOrder.get());
    assertEquals(0, lock.readerCount());
    assertFalse(lock.isWriteHeld());
  }
}
