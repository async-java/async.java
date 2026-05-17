package general;

import org.junit.Test;
import org.ores.async.Asyncc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Pins the v0.2.4 fix for the {@code ArrayList} resize race in
 * {@link Asyncc#Parallel(java.util.List, org.ores.async.Asyncc.IAsyncCallback)}.
 *
 * <p>The earlier dispatch loop did {@code results.add(null)} inside the same iteration that
 * submitted each task. A task that completed before the next iteration could call
 * {@code results.set(i, v)} concurrently with a backing-array resize, dropping its write.
 *
 * <p>The bug was intermittent; once-in-three on JDK 17 with a fixed thread pool of 16. This
 * test runs the same scenario five times back-to-back so a regression in the pre-fill
 * ordering is caught reliably within a single run.
 *
 * <p>If this test ever fails, the fix in {@link org.ores.async.NeoParallel}'s
 * {@code Parallel(List, callback)} branch needs revisiting.
 */
public class ParallelResizeRaceTest {

  @Test(timeout = 30_000)
  public void parallel_thousandFanOut_no_lost_writes_across_five_iterations() throws Exception {
    final int n = 1_000;
    final int repeats = 5;
    final ExecutorService exec = Executors.newFixedThreadPool(16);
    try {
      for (int run = 0; run < repeats; run++) {
        final List<Asyncc.AsyncTask<Integer, Throwable>> tasks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
          final int val = i;
          tasks.add(c -> exec.submit(() -> c.success(val)));
        }

        final CompletableFuture<List<Integer>> done = new CompletableFuture<>();
        Asyncc.Parallel(tasks, (err, results) -> {
          if (err != null) done.completeExceptionally((Throwable) err);
          else done.complete(results);
        });

        final List<Integer> results = done.get(10, TimeUnit.SECONDS);
        assertNotNull("run " + run + ": results", results);
        assertEquals("run " + run + ": size", n, results.size());
        for (int i = 0; i < n; i++) {
          assertEquals("run " + run + " position " + i, Integer.valueOf(i), results.get(i));
        }
      }
    } finally {
      exec.shutdown();
      exec.awaitTermination(2, TimeUnit.SECONDS);
    }
  }
}
