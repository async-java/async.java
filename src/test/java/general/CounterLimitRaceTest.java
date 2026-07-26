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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reproducer for a {@code CounterLimit} data race surfaced by running {@code Asyncc.Parallel}
 * rapid-fire against a virtual-thread executor on JDK 21+.
 *
 * <p><strong>Symptom:</strong> {@code Asyncc.Parallel(2 tasks, callback)} eventually fails to
 * fire its final callback after some number of healthy iterations (observed: ~30-130 in
 * downstream benchmark harnesses). The caller's {@code CompletableFuture.get(timeout)} hits
 * its deadline; no exception is thrown by the library itself; the work appears to be lost.
 *
 * <p><strong>Cause:</strong> {@code CounterLimit.finished} (and {@code started}) used to be
 * plain {@code Integer} fields mutated via {@code this.finished++} from per-task callbacks.
 * Each {@code NeoParallel.AsyncTaskRunner} holds its own {@code cbLock}, so two parallel
 * tasks could both be inside their {@code synchronized(this.cbLock)} blocks at the same
 * time, both calling {@code p.c.incrementFinished()} against the <em>shared</em>
 * {@code CounterLimit}. The post-increment is a read-modify-write on a non-atomic field;
 * under contention one of the increments gets lost. After the lost increment,
 * {@code finished &lt; started} forever, so {@code ParallelRunner.isDone()} returns
 * {@code false} forever, the final callback never fires, and the future hangs.
 *
 * <p>The investigation was originally labelled "VT pinning" because virtual threads are
 * what surfaced it under sustained load. The actual bug is a textbook lost-update data race
 * — virtual threads just amplify concurrency enough to make it reliably reproducible.
 *
 * <p>The fix is in {@code CounterLimit.java} — switch {@code started}/{@code finished} to
 * {@link java.util.concurrent.atomic.AtomicInteger}.
 */
public class CounterLimitRaceTest {

  /**
   * 500 sequential {@code Asyncc.Parallel} calls, two tasks each, each task sleeps ~2ms. On
   * the fixed (post-AtomicInteger) implementation this completes in ~400ms; the pre-fix branch
   * times out by iteration 40-130 with the lost-update symptom described in the class
   * javadoc.
   */
  @Test(timeout = 60_000)
  public void parallelDoesNotLoseFinalCallbackUnderRapidFire() throws Exception {

    // The library targets JDK 17; only run this test when a JDK 21+ runtime gives us
    // virtual threads. Reflection because `Executors.newVirtualThreadPerTaskExecutor()` is
    // not available in the JDK 17 baseline `release` the maven-compiler-plugin enforces.
    final ExecutorService vt = newVirtualThreadPerTaskExecutorOrSkip();
    final int iterations = 500;
    try {
      for (int i = 0; i < iterations; i++) {

        final CompletableFuture<Integer> result = new CompletableFuture<>();
        final int iter = i;

        final AtomicInteger counter = new AtomicInteger();
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

        Asyncc.Parallel(tasks, (err, results) -> {
          if (err != null) {
            result.completeExceptionally(unwrap(err));
            return;
          }
          counter.incrementAndGet();
          result.complete(results.size());
        });

        try {
          final Integer got = result.get(5, TimeUnit.SECONDS);
          if (got == null || got != 2) {
            throw new AssertionError("iter=" + iter + " got=" + got);
          }
        } catch (java.util.concurrent.TimeoutException te) {
          throw new AssertionError("Asyncc.Parallel timed out at iteration " + iter
              + ". Cause: CounterLimit.{started,finished} non-atomic increment lost an"
              + " update; isDone() now returns false forever and the final callback"
              + " never fires. Make those fields AtomicInteger.", te);
        }
      }
    } finally {
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
