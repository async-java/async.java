package general;

import org.junit.Test;
import org.ores.async.NeoQueue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the {@link NeoQueue} concurrency-cap invariant:
 *
 * <p>If the queue is constructed with {@code concurrency = N}, no more than N task handlers
 * may be in flight at any instant, regardless of how many tasks are pushed.
 *
 * <p>Pre-v0.2.5 there was no test for this contract — the existing {@code QueueTest} was
 * entirely commented out. This test verifies the contract holds under three conditions:
 *
 * <ol>
 *   <li>Burst push: 200 tasks pushed before any has completed.</li>
 *   <li>Trickle push: tasks pushed one at a time from a separate producer thread.</li>
 *   <li>Repeated runs: scenario 1 repeated 20 times back-to-back.</li>
 * </ol>
 *
 * <p>The handler increments an {@link AtomicInteger} at entry, decrements at exit, and the
 * test records the running maximum. If any run observes more than the cap, the test fails.
 */
public class NeoQueueConcurrencyTest {

  private static final int TIMEOUT_MS = 60_000;

  @Test(timeout = TIMEOUT_MS)
  public void burstPush_neverExceedsCap() throws Exception {
    runBurstScenario(4, 200);
  }

  @Test(timeout = TIMEOUT_MS)
  public void burstPush_concurrency_1_neverExceedsCap() throws Exception {
    runBurstScenario(1, 100);
  }

  @Test(timeout = TIMEOUT_MS)
  public void burstPush_concurrency_16_neverExceedsCap() throws Exception {
    runBurstScenario(16, 500);
  }

  @Test(timeout = TIMEOUT_MS)
  public void burstPush_repeated_20x() throws Exception {
    for (int i = 0; i < 20; i++) {
      runBurstScenario(8, 100);
    }
  }

  @Test(timeout = TIMEOUT_MS)
  public void tricklePush_neverExceedsCap() throws Exception {
    final int cap = 4;
    final int n = 100;
    final ExecutorService userExec = Executors.newFixedThreadPool(16);
    try {
      final AtomicInteger inFlight = new AtomicInteger();
      final AtomicInteger maxInFlight = new AtomicInteger();
      final AtomicInteger completed = new AtomicInteger();
      final CountDownLatch allDone = new CountDownLatch(n);

      final NeoQueue<Integer, Integer> q = new NeoQueue<>(cap, (task, c) -> {
        userExec.submit(() -> {
          final int now = inFlight.incrementAndGet();
          maxInFlight.accumulateAndGet(now, Math::max);
          try { Thread.sleep(2); } catch (InterruptedException ie) { /* */ }
          inFlight.decrementAndGet();
          completed.incrementAndGet();
          allDone.countDown();
          c.success(task.getValue());
        });
      });

      final Thread producer = new Thread(() -> {
        for (int i = 0; i < n; i++) {
          q.push(new NeoQueue.Task<>(i));
          try { Thread.sleep(1); } catch (InterruptedException ie) { return; }
        }
      });
      producer.start();
      producer.join(TIMEOUT_MS);

      assertTrue("queue drained within " + TIMEOUT_MS + "ms", allDone.await(20, TimeUnit.SECONDS));
      assertEquals("all tasks completed", n, completed.get());
      assertTrue(
          "max in-flight (" + maxInFlight.get() + ") must not exceed cap (" + cap + ")",
          maxInFlight.get() <= cap);
    } finally {
      userExec.shutdown();
      userExec.awaitTermination(2, TimeUnit.SECONDS);
    }
  }

  private void runBurstScenario(final int cap, final int n) throws Exception {
    final ExecutorService userExec = Executors.newFixedThreadPool(16);
    try {
      final AtomicInteger inFlight = new AtomicInteger();
      final AtomicInteger maxInFlight = new AtomicInteger();
      final AtomicInteger completed = new AtomicInteger();
      final CountDownLatch allDone = new CountDownLatch(n);

      final NeoQueue<Integer, Integer> q = new NeoQueue<>(cap, (task, c) -> {
        userExec.submit(() -> {
          final int now = inFlight.incrementAndGet();
          maxInFlight.accumulateAndGet(now, Math::max);
          try { Thread.sleep(2); } catch (InterruptedException ie) { /* */ }
          inFlight.decrementAndGet();
          completed.incrementAndGet();
          allDone.countDown();
          c.success(task.getValue());
        });
      });

      // Burst push: all N tasks pushed at once before any handler has had a chance to complete.
      for (int i = 0; i < n; i++) {
        q.push(new NeoQueue.Task<>(i));
      }

      assertTrue("queue drained within 20s", allDone.await(20, TimeUnit.SECONDS));
      assertEquals("cap=" + cap + " n=" + n + ": all tasks completed", n, completed.get());
      assertTrue(
          "cap=" + cap + " n=" + n + ": max in-flight (" + maxInFlight.get()
              + ") must not exceed cap (" + cap + ")",
          maxInFlight.get() <= cap);
      // Sanity: at high N with cap > 1 we should actually exercise multiple in-flight tasks
      // (otherwise the test isn't really testing the cap)
      if (cap > 1 && n >= 50) {
        assertTrue(
            "cap=" + cap + " n=" + n + ": max in-flight should approach cap "
                + "(observed " + maxInFlight.get() + ")",
            maxInFlight.get() >= 2);
      }
    } finally {
      userExec.shutdown();
      userExec.awaitTermination(2, TimeUnit.SECONDS);
    }
  }
}
