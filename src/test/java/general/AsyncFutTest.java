package general;

import org.junit.Test;
import org.ores.async.AsyncFut;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the {@link AsyncFut} contract — the promise-shaped sibling of {@code Asyncc}.
 */
public class AsyncFutTest {

  private static final int TIMEOUT = 10_000;

  // ---------------- Parallel ---------------------------------------------

  @Test(timeout = TIMEOUT)
  public void parallel_collects_results_in_input_order() throws Exception {
    final ExecutorService exec = Executors.newFixedThreadPool(4);
    try {
      final CompletableFuture<List<String>> fut = AsyncFut.Parallel(List.of(
          () -> CompletableFuture.supplyAsync(() -> "a", exec),
          () -> CompletableFuture.supplyAsync(() -> "b", exec),
          () -> CompletableFuture.supplyAsync(() -> "c", exec)
      ));
      assertEquals(List.of("a", "b", "c"), fut.get(2, TimeUnit.SECONDS));
    } finally {
      exec.shutdown();
    }
  }

  @Test(timeout = TIMEOUT)
  public void parallel_short_circuits_on_first_failure() throws Exception {
    final IllegalStateException boom = new IllegalStateException("boom");
    final CompletableFuture<List<String>> fut = AsyncFut.Parallel(List.of(
        () -> CompletableFuture.completedFuture("a"),
        () -> CompletableFuture.failedFuture(boom)
    ));
    try {
      fut.get(2, TimeUnit.SECONDS);
      fail("expected ExecutionException");
    } catch (ExecutionException ee) {
      assertTrue(
          "cause should mention boom: " + ee.getCause(),
          ee.getCause().getMessage() == null
              ? ee.getCause().toString().contains("boom")
              : ee.getCause().getMessage().contains("boom"));
    }
  }

  @Test(timeout = TIMEOUT)
  public void parallel_two_arg_convenience() throws Exception {
    final CompletableFuture<List<Integer>> fut = AsyncFut.Parallel(
        () -> CompletableFuture.completedFuture(1),
        () -> CompletableFuture.completedFuture(2)
    );
    assertEquals(List.of(1, 2), fut.get(2, TimeUnit.SECONDS));
  }

  // ---------------- ParallelLimit ----------------------------------------

  @Test(timeout = TIMEOUT)
  public void parallelLimit_enforces_concurrency_cap() throws Exception {
    final ExecutorService exec = Executors.newFixedThreadPool(16);
    try {
      final AtomicInteger inFlight = new AtomicInteger();
      final AtomicInteger maxInFlight = new AtomicInteger();
      final int limit = 4;
      final int n = 32;

      final List<java.util.function.Supplier<? extends java.util.concurrent.CompletionStage<Integer>>> tasks =
          new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        final int val = i;
        tasks.add(() -> CompletableFuture.supplyAsync(() -> {
          final int now = inFlight.incrementAndGet();
          maxInFlight.accumulateAndGet(now, Math::max);
          try { Thread.sleep(2); } catch (InterruptedException ie) { /* */ }
          inFlight.decrementAndGet();
          return val;
        }, exec));
      }

      final List<Integer> results = AsyncFut.ParallelLimit(limit, tasks)
          .get(5, TimeUnit.SECONDS);

      assertEquals(n, results.size());
      assertTrue(
          "max in-flight (" + maxInFlight.get() + ") must not exceed limit (" + limit + ")",
          maxInFlight.get() <= limit);
    } finally {
      exec.shutdown();
      exec.awaitTermination(2, TimeUnit.SECONDS);
    }
  }

  // ---------------- Series -----------------------------------------------

  @Test(timeout = TIMEOUT)
  public void series_runs_sequentially_in_order() throws Exception {
    final List<String> order = new ArrayList<>();
    final CompletableFuture<List<Integer>> fut = AsyncFut.Series(List.of(
        () -> { order.add("start-a"); return CompletableFuture.supplyAsync(() -> {
          try { Thread.sleep(10); } catch (InterruptedException ie) { /* */ }
          order.add("done-a"); return 1;
        }); },
        () -> { order.add("start-b"); return CompletableFuture.completedFuture(2); },
        () -> { order.add("start-c"); return CompletableFuture.completedFuture(3); }
    ));
    assertEquals(List.of(1, 2, 3), fut.get(2, TimeUnit.SECONDS));
    assertEquals(List.of("start-a", "done-a", "start-b", "start-c"), order);
  }

  // ---------------- Race -------------------------------------------------

  @Test(timeout = TIMEOUT)
  public void race_returns_the_fastest_result() throws Exception {
    final ExecutorService exec = Executors.newFixedThreadPool(2);
    try {
      final CompletableFuture<String> fut = AsyncFut.Race(List.of(
          () -> CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(50); } catch (InterruptedException ie) { /* */ }
            return "slow";
          }, exec),
          () -> CompletableFuture.completedFuture("fast")
      ));
      assertEquals("fast", fut.get(2, TimeUnit.SECONDS));
    } finally {
      exec.shutdown();
    }
  }

  // ---------------- Map --------------------------------------------------

  @Test(timeout = TIMEOUT)
  public void map_transforms_each_element_preserving_input_order() throws Exception {
    final ExecutorService exec = Executors.newFixedThreadPool(4);
    try {
      final CompletableFuture<List<Integer>> fut = AsyncFut.Map(List.of(1, 2, 3, 4),
          x -> CompletableFuture.supplyAsync(() -> x * x, exec));
      assertEquals(List.of(1, 4, 9, 16), fut.get(2, TimeUnit.SECONDS));
    } finally {
      exec.shutdown();
    }
  }

  // ---------------- Reduce -----------------------------------------------

  @Test(timeout = TIMEOUT)
  public void reduce_accumulates_in_order() throws Exception {
    final CompletableFuture<Integer> fut = AsyncFut.Reduce(
        List.of(1, 2, 3, 4),
        0,
        (acc, x) -> CompletableFuture.completedFuture(acc + x));
    assertEquals(Integer.valueOf(10), fut.get(2, TimeUnit.SECONDS));
  }

  // ---------------- Times ------------------------------------------------

  @Test(timeout = TIMEOUT)
  public void times_runs_N_times_collecting_results_in_index_order() throws Exception {
    final CompletableFuture<List<String>> fut = AsyncFut.Times(5,
        i -> CompletableFuture.completedFuture("idx-" + i));
    final List<String> results = fut.get(2, TimeUnit.SECONDS);
    assertEquals(5, results.size());
    for (int i = 0; i < 5; i++) {
      assertEquals("idx-" + i, results.get(i));
    }
  }

  // ---------------- Each -------------------------------------------------

  @Test(timeout = TIMEOUT)
  public void each_fires_per_element_completes_with_void() throws Exception {
    final AtomicInteger fires = new AtomicInteger();
    final CompletableFuture<Void> fut = AsyncFut.Each(
        List.of("a", "b", "c"),
        item -> { fires.incrementAndGet(); return CompletableFuture.completedFuture(null); });
    fut.get(2, TimeUnit.SECONDS);
    assertEquals(3, fires.get());
  }

  // ---------------- Composition: AsyncFut + CompletableFuture chaining --

  @Test(timeout = TIMEOUT)
  public void composes_with_thenApply_and_thenCompose() throws Exception {
    // Combine two async lookups, then transform the result via plain CompletableFuture.thenApply.
    final ExecutorService exec = Executors.newFixedThreadPool(2);
    try {
      final CompletableFuture<String> fut = AsyncFut.Parallel(List.of(
              () -> CompletableFuture.supplyAsync(() -> "hello", exec),
              () -> CompletableFuture.supplyAsync(() -> "world", exec)
          ))
          .thenApply(parts -> parts.get(0) + " " + parts.get(1));
      assertEquals("hello world", fut.get(2, TimeUnit.SECONDS));
    } finally {
      exec.shutdown();
    }
  }

  @Test(timeout = TIMEOUT)
  public void boundary_signature_matches_the_user_request_example() throws Exception {
    // Mirrors the user's preferred boundary:
    //   CompletableFuture<String> handle(Request req) {
    //     return WrapFuture.toFuture(x ->
    //       Asyncc.Parallel(tasks, x)
    //     ).thenApply(this::combine);
    //   }
    // The point of this test: the AsyncFut.Parallel form should compile with no glue.
    final CompletableFuture<String> fut = AsyncFut.Parallel(List.of(
            () -> CompletableFuture.completedFuture("alpha"),
            () -> CompletableFuture.completedFuture("beta")
        ))
        .thenApply(results -> results.get(0) + "+" + results.get(1));
    assertEquals("alpha+beta", fut.get(2, TimeUnit.SECONDS));
  }

  // ---------------- Edge cases -------------------------------------------

  @Test(timeout = TIMEOUT)
  public void parallel_empty_list_completes_immediately() throws Exception {
    final CompletableFuture<List<String>> fut = AsyncFut.Parallel(List.of());
    final List<String> result = fut.get(2, TimeUnit.SECONDS);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void parallel_supplier_throwing_synchronously_surfaces_as_failed_future() throws Exception {
    final CompletableFuture<List<String>> fut = AsyncFut.Parallel(List.of(
        () -> CompletableFuture.completedFuture("ok"),
        () -> { throw new IllegalStateException("supplier blew up"); }
    ));
    try {
      fut.get(2, TimeUnit.SECONDS);
      fail("expected ExecutionException");
    } catch (ExecutionException ee) {
      assertTrue(ee.getCause().getMessage().contains("supplier blew up"));
    }
  }
}
