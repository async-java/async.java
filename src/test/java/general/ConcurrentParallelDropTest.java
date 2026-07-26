package general;

import org.junit.Assume;
import org.junit.Test;
import org.ores.async.Asyncc;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reproduces the "Asyncc.Parallel silently drops final callbacks under sustained concurrent
 * in-flight load" symptom observed when driving {@code dd-akka-ws-server}'s
 * {@code /ws/asyncjava} endpoint with 50 concurrent WS clients × 10 msg/sec for 20 seconds.
 * The WS load test sent 9 967 messages and received only 9 420 responses — a 5 % drop rate.
 *
 * <p>This test boils that environment down to pure async.java with zero networking:
 *
 * <ul>
 *   <li>{@code CONCURRENCY} producer threads run in parallel.</li>
 *   <li>Each producer drives {@code Asyncc.Parallel(twoTasks, finalCb)} {@code ITERATIONS}
 *       times back-to-back, awaiting each final callback before starting the next.</li>
 *   <li>Each of the two parallel tasks is dispatched onto a shared virtual-thread executor,
 *       sleeps a few ms, then calls {@code cb.done(null, result)}.</li>
 *   <li>If every single final callback fires within a generous deadline, the test passes.
 *       If any producer's final callback never fires, the test fails with a count of dropped
 *       invocations.</li>
 * </ul>
 *
 * <p>On the pre-PR-#9 main branch this test fails with the {@code CounterLimit} lost-update race
 * (already fixed in PR #9). On the post-PR-#9 main branch driven hard (high concurrency × many
 * iterations) it still drops invocations — that's the secondary bug this test exists to
 * pin down.
 */
public class ConcurrentParallelDropTest {

  // Tuned to reproduce the WS-driven drop in ~1-3 seconds without timing out the test.
  // Increase CONCURRENCY or ITERATIONS to make the failure faster / more reliable.
  private static final int CONCURRENCY = 50;
  private static final int ITERATIONS = 200;
  private static final int PER_CALL_TIMEOUT_MS = 5_000;
  private static final int OVERALL_TEST_TIMEOUT_MS = 180_000;

  @Test(timeout = OVERALL_TEST_TIMEOUT_MS)
  public void noFinalCallbackDroppedUnderConcurrentLoad() throws Exception {

    final ExecutorService vt = newVirtualThreadPerTaskExecutorOrSkip();
    final ExecutorService producers = Executors.newFixedThreadPool(CONCURRENCY);

    try {
      final AtomicInteger sent = new AtomicInteger();
      final AtomicInteger received = new AtomicInteger();
      final AtomicInteger droppedTimeouts = new AtomicInteger();
      final AtomicInteger duplicateFires = new AtomicInteger();
      final AtomicLong worstLatencyNs = new AtomicLong();

      final CompletableFuture<?>[] producerDone = new CompletableFuture<?>[CONCURRENCY];

      for (int p = 0; p < CONCURRENCY; p++) {
        final int producerId = p;
        producerDone[p] = CompletableFuture.runAsync(() -> {
          for (int i = 0; i < ITERATIONS; i++) {
            final int iter = i;
            final CompletableFuture<Integer> result = new CompletableFuture<>();

            final List<Asyncc.AsyncTask<String, Throwable>> tasks = List.of(
                cb -> vt.submit(() -> {
                  try {
                    Thread.sleep(2);
                    cb.done(null, "A");
                  } catch (Throwable t) {
                    cb.done(t, null);
                  }
                }),
                cb -> vt.submit(() -> {
                  try {
                    Thread.sleep(2);
                    cb.done(null, "B");
                  } catch (Throwable t) {
                    cb.done(t, null);
                  }
                }));

            final long sentAtNs = System.nanoTime();
            sent.incrementAndGet();

            final AtomicInteger thisCallFires = new AtomicInteger();
            Asyncc.Parallel(tasks, (err, results) -> {
              final int fireOrdinal = thisCallFires.incrementAndGet();
              if (fireOrdinal > 1) {
                duplicateFires.incrementAndGet();
                // Don't complete the future a second time; just record the duplicate.
                return;
              }
              if (err != null) {
                result.completeExceptionally(unwrap(err));
                return;
              }
              received.incrementAndGet();
              final long latencyNs = System.nanoTime() - sentAtNs;
              worstLatencyNs.accumulateAndGet(latencyNs, Math::max);
              result.complete(results.size());
            });

            try {
              result.get(PER_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
              droppedTimeouts.incrementAndGet();
              // Don't propagate — count and continue so the final assertion can show how
              // many invocations dropped across the whole concurrency × iteration matrix.
            } catch (Exception other) {
              throw new RuntimeException(
                  "producer=" + producerId + " iter=" + iter + " threw", other);
            }
          }
        }, producers);
      }

      CompletableFuture.allOf(producerDone).get(2, TimeUnit.MINUTES);

      final int expected = CONCURRENCY * ITERATIONS;
      final int actualReceived = received.get();
      final int actualDropped = droppedTimeouts.get();
      final long worstLatencyMs = worstLatencyNs.get() / 1_000_000;

      final int actualDuplicates = duplicateFires.get();
      System.out.printf(
          "ConcurrentParallelDropTest: concurrency=%d iters=%d sent=%d received=%d "
              + "dropped_timeouts=%d duplicate_fires=%d worst_latency_ms=%d%n",
          CONCURRENCY, ITERATIONS, sent.get(), actualReceived, actualDropped, actualDuplicates,
          worstLatencyMs);

      if (actualDropped > 0 || actualReceived != expected || actualDuplicates > 0) {
        throw new AssertionError(String.format(
            "Asyncc.Parallel produced %d duplicate final-callback fires and %d dropped"
                + " (timed-out) invocations across %d concurrent producers × %d iterations."
                + " received=%d sent=%d worst_latency_ms=%d. The two symptoms are sides of"
                + " the same coin: a race in NeoParallel.AsyncTaskRunner.done() / isDone()"
                + " where both per-task callbacks observe finished==started and each fires"
                + " the final callback. fireFinalCallback's isFinalCallbackFired guard"
                + " prevents the second f.done(...) from running, but the act of firing"
                + " twice means the parallel-pair's results may not be fully populated"
                + " when the first fire observes them.",
            actualDuplicates, actualDropped, CONCURRENCY, ITERATIONS, actualReceived, sent.get(),
            worstLatencyMs));
      }
    } finally {
      producers.shutdown();
      producers.awaitTermination(5, TimeUnit.SECONDS);
      vt.shutdown();
      vt.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private static Throwable unwrap(final Object err) {
    if (err instanceof Throwable) {
      return (Throwable) err;
    }
    return new RuntimeException(String.valueOf(err));
  }

  private static ExecutorService newVirtualThreadPerTaskExecutorOrSkip() {
    try {
      final Method m = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
      return (ExecutorService) m.invoke(null);
    } catch (NoSuchMethodException pre21) {
      Assume.assumeTrue("requires JDK 21+ for virtual threads", false);
      throw new AssertionError("unreachable");
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
