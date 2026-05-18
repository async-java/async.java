package general;

import org.junit.Test;
import org.ores.async.Asyncc;
import org.ores.async.AsyncFut;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * Showcases — and pins — the three composition patterns the library supports:
 *
 * <ol>
 *   <li><strong>Callback-style nesting</strong>: an outer combinator's per-task callback is
 *       passed directly as the inner combinator's final callback. Works on {@link Asyncc}
 *       today; v0.2.8's {@link Asyncc.Task} shorthand makes the type witness easier.</li>
 *   <li><strong>Promise-style with suppliers</strong>: {@code AsyncFut.Parallel(List.of(() -> inner1, () -> inner2))}
 *       &mdash; the {@code () ->} defers the inner combinator's start until the outer combinator
 *       requests it (necessary for {@code Series}).</li>
 *   <li><strong>Promise-style with already-started futures</strong>: {@code AsyncFut.ParallelF(List.of(inner1, inner2))}
 *       &mdash; drops the supplier wrappers when the inner combinators have already returned
 *       their futures (correct semantics for {@code Parallel} / {@code Race} where you want
 *       eager start anyway).</li>
 * </ol>
 */
public class CompositionPatternsTest {

  private static final int TIMEOUT = 5_000;

  // ====== 1. Callback-style nesting (Asyncc) =============================

  @Test(timeout = TIMEOUT)
  public void asyncc_series_of_parallel_callback_passthrough() throws Exception {
    final ExecutorService exec = Executors.newFixedThreadPool(4);
    try {
      // Outer Series, each step is itself a Parallel. The outer per-task callback `c` is
      // typed `IAsyncCallback<List<String>, Throwable>` — which is exactly what the inner
      // Parallel's final-callback parameter expects. So we pass `c` straight through.
      final CompletableFuture<List<List<String>>> done = new CompletableFuture<>();

      Asyncc.<List<String>, Throwable>Series(List.of(
          (Asyncc.Task<List<String>>) c -> Asyncc.<String, Throwable>Parallel(List.of(
              cb -> exec.submit(() -> cb.success("A1")),
              cb -> exec.submit(() -> cb.success("A2"))
          ), c),
          (Asyncc.Task<List<String>>) c -> Asyncc.<String, Throwable>Parallel(List.of(
              cb -> exec.submit(() -> cb.success("B1")),
              cb -> exec.submit(() -> cb.success("B2"))
          ), c)
      ), (err, results) -> {
        if (err != null) done.completeExceptionally(err);
        else done.complete(results);
      });

      assertEquals(
          List.of(List.of("A1", "A2"), List.of("B1", "B2")),
          done.get(2, TimeUnit.SECONDS));
    } finally {
      exec.shutdown();
    }
  }

  @Test(timeout = TIMEOUT)
  public void asyncc_parallel_of_series_callback_passthrough() throws Exception {
    final ExecutorService exec = Executors.newFixedThreadPool(4);
    try {
      // Outer Parallel of Series. Same idea, inverted: each outer task is a Series.
      final CompletableFuture<List<List<String>>> done = new CompletableFuture<>();

      Asyncc.<List<String>, Throwable>Parallel(List.of(
          (Asyncc.Task<List<String>>) c -> Asyncc.<String, Throwable>Series(List.of(
              cb -> cb.success("A1"),
              cb -> cb.success("A2")
          ), c),
          (Asyncc.Task<List<String>>) c -> Asyncc.<String, Throwable>Series(List.of(
              cb -> cb.success("B1"),
              cb -> cb.success("B2")
          ), c)
      ), (err, results) -> {
        if (err != null) done.completeExceptionally(err);
        else done.complete(results);
      });

      assertEquals(
          List.of(List.of("A1", "A2"), List.of("B1", "B2")),
          done.get(2, TimeUnit.SECONDS));
    } finally {
      exec.shutdown();
    }
  }

  // ====== 2. Promise-style with suppliers (AsyncFut + supplier form) =====

  @Test(timeout = TIMEOUT)
  public void asyncfut_parallel_of_series_via_suppliers() throws Exception {
    final CompletableFuture<List<List<String>>> fut = AsyncFut.Parallel(List.of(
        () -> AsyncFut.Series(List.of(
            () -> CompletableFuture.completedFuture("A1"),
            () -> CompletableFuture.completedFuture("A2")
        )),
        () -> AsyncFut.Parallel(List.of(
            () -> CompletableFuture.completedFuture("B1"),
            () -> CompletableFuture.completedFuture("B2")
        ))
    ));
    assertEquals(
        List.of(List.of("A1", "A2"), List.of("B1", "B2")),
        fut.get(2, TimeUnit.SECONDS));
  }

  // ====== 3. Promise-style with already-started futures (AsyncFut.ParallelF) ====

  @Test(timeout = TIMEOUT)
  public void asyncfut_parallelF_drops_supplier_wrappers() throws Exception {
    // The shape the user asked for: no `() ->` boilerplate around each task.
    final CompletableFuture<List<List<String>>> fut = AsyncFut.ParallelF(List.of(
        AsyncFut.Series(List.of(
            () -> CompletableFuture.completedFuture("A1"),
            () -> CompletableFuture.completedFuture("A2")
        )),
        AsyncFut.Parallel(List.of(
            () -> CompletableFuture.completedFuture("B1"),
            () -> CompletableFuture.completedFuture("B2")
        ))
    ));
    assertEquals(
        List.of(List.of("A1", "A2"), List.of("B1", "B2")),
        fut.get(2, TimeUnit.SECONDS));
  }

  @Test(timeout = TIMEOUT)
  public void asyncfut_raceF_returns_first_completing_future() throws Exception {
    final ExecutorService exec = Executors.newFixedThreadPool(2);
    try {
      final CompletableFuture<String> slow = CompletableFuture.supplyAsync(() -> {
        try { Thread.sleep(50); } catch (InterruptedException ie) { /* */ }
        return "slow";
      }, exec);
      final CompletableFuture<String> fast = CompletableFuture.completedFuture("fast");

      final CompletableFuture<String> winner = AsyncFut.RaceF(List.of(slow, fast));
      assertEquals("fast", winner.get(2, TimeUnit.SECONDS));
    } finally {
      exec.shutdown();
    }
  }

  @Test(timeout = TIMEOUT)
  public void asyncfut_parallelF_short_circuits_on_first_failure() {
    final IllegalStateException boom = new IllegalStateException("boom");
    final CompletableFuture<List<String>> fut = AsyncFut.ParallelF(List.of(
        CompletableFuture.completedFuture("a"),
        CompletableFuture.failedFuture(boom)
    ));
    try {
      fut.get(2, TimeUnit.SECONDS);
      org.junit.Assert.fail("expected ExecutionException");
    } catch (Exception e) {
      org.junit.Assert.assertTrue(e.getCause().getMessage().contains("boom"));
    }
  }

  // ====== Bonus: deeply nested ParallelF ================================

  @Test(timeout = TIMEOUT)
  public void deeply_nested_parallelF_of_series_of_parallel() throws Exception {
    // Three levels deep: ParallelF([Series([Parallel([F, F])]), ParallelF([Series([F]), F])])
    final CompletableFuture<List<List<List<String>>>> fut = AsyncFut.ParallelF(List.of(
        AsyncFut.Series(List.of(
            () -> AsyncFut.ParallelF(List.of(
                CompletableFuture.completedFuture("a"),
                CompletableFuture.completedFuture("b")
            ))
        )),
        AsyncFut.ParallelF(List.of(
            AsyncFut.Series(List.of(
                () -> CompletableFuture.completedFuture("c")
            )),
            CompletableFuture.completedFuture(List.of("d"))
        ))
    ));
    final List<List<List<String>>> result = fut.get(2, TimeUnit.SECONDS);
    assertEquals(2, result.size());
    assertEquals(List.of("a", "b"), result.get(0).get(0));
    assertEquals(List.of("c"), result.get(1).get(0));
    assertEquals(List.of("d"), result.get(1).get(1));
  }
}
