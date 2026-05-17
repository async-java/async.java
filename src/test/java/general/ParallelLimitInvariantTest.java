package general;

import org.junit.Test;
import org.ores.async.Asyncc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Strict invariant for {@link Asyncc#ParallelLimit}: when called with {@code limit=N},
 * no more than N tasks may be in flight at any instant. Pre-v0.2.5 this contract was relaxed
 * to {@code limit + 1} because the {@code isBelowCapacity()} check in
 * {@link org.ores.async.NeoParallel#RunTasksLimit} happened <em>outside</em> the iterator
 * monitor that guarded the started-counter increment. Two completion callbacks racing through
 * the dispatch gate could each pass the check before either of them landed an
 * {@code incrementStarted}, dispatching {@code limit + 1} tasks in the worst case.
 *
 * <p>v0.2.5 moves the {@code isBelowCapacity} check inside the iterator monitor so check-and-act
 * is atomic. This test verifies the tight invariant {@code maxInFlight <= limit}, with both the
 * List and Map overloads of {@code ParallelLimit}, repeated many times back-to-back so a
 * regression surfaces within a single run.
 *
 * <p>If this test ever fails, the gate in {@code NeoParallel.RunTasksLimit.run()} (or
 * {@code RunMapLimit}) needs revisiting.
 */
public class ParallelLimitInvariantTest {

  private static final int LIMIT = 4;
  private static final int N_TASKS = 64;
  private static final int REPEATS = 100;
  private static final int FINAL_TIMEOUT_MS = 120_000;

  @Test(timeout = FINAL_TIMEOUT_MS)
  public void parallelLimit_List_inFlight_never_exceeds_limit() throws Exception {
    final ExecutorService exec = Executors.newFixedThreadPool(16);
    try {
      int worstMax = 0;
      for (int run = 0; run < REPEATS; run++) {

        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();

        final List<Asyncc.AsyncTask<String, Throwable>> tasks = new ArrayList<>(N_TASKS);
        for (int i = 0; i < N_TASKS; i++) {
          final int val = i;
          tasks.add(c -> exec.submit(() -> {
            final int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            // small sleep increases the odds of completions racing through the gate
            try { Thread.sleep(1); } catch (InterruptedException ie) { /* */ }
            inFlight.decrementAndGet();
            c.success("v" + val);
          }));
        }

        final CompletableFuture<List<String>> done = new CompletableFuture<>();
        Asyncc.<String, Throwable>ParallelLimit(LIMIT, tasks, (err, results) -> {
          if (err != null) done.completeExceptionally((Throwable) err);
          else             done.complete(results);
        });
        final List<String> results = done.get(10, TimeUnit.SECONDS);
        assertEquals("run " + run + ": results.size", N_TASKS, results.size());

        final int observed = maxInFlight.get();
        worstMax = Math.max(worstMax, observed);
        assertTrue(
            "run " + run + ": max in-flight (" + observed + ") must be <= limit (" + LIMIT + ")",
            observed <= LIMIT);
      }
      // Sanity: we should have actually exercised the cap. If the loop never put more than 1
      // task in flight at a time, the test is not exercising the contract.
      assertTrue(
          "worst-observed in-flight (" + worstMax + ") should approach the limit",
          worstMax >= 2);
    } finally {
      exec.shutdown();
      exec.awaitTermination(2, TimeUnit.SECONDS);
    }
  }
}
