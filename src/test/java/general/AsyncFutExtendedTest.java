package general;

import org.junit.Test;
import org.ores.async.AsyncFut;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Thorough tests for the v0.2.8 additions to {@link AsyncFut}: {@code Waterfall},
 * {@code FilterMap}, {@code GroupBy}, {@code Whilst}, {@code DoWhilst}.
 */
public class AsyncFutExtendedTest {

  private static final int TIMEOUT = 10_000;

  // ====================== Waterfall ======================================

  @Test(timeout = TIMEOUT)
  public void waterfall_named_accumulator_builds_up_in_order() throws Exception {
    final CompletableFuture<Map<String, Object>> fut = AsyncFut.Waterfall(List.of(
        acc -> CompletableFuture.completedFuture(Map.entry("config", "{shards:8}")),
        acc -> CompletableFuture.completedFuture(Map.entry("shardCount", 8)),
        acc -> CompletableFuture.completedFuture(Map.entry("region", "us-east-1"))
    ));
    final Map<String, Object> result = fut.get(2, TimeUnit.SECONDS);
    assertEquals("{shards:8}", result.get("config"));
    assertEquals(Integer.valueOf(8), result.get("shardCount"));
    assertEquals("us-east-1", result.get("region"));
  }

  @Test(timeout = TIMEOUT)
  public void waterfall_step_sees_prior_accumulator() throws Exception {
    final CompletableFuture<Map<String, Object>> fut = AsyncFut.Waterfall(List.of(
        acc -> CompletableFuture.completedFuture(Map.entry("a", 10)),
        acc -> CompletableFuture.completedFuture(Map.entry("b", ((Integer) acc.get("a")) * 2)),
        acc -> CompletableFuture.completedFuture(Map.entry("c", ((Integer) acc.get("a")) + ((Integer) acc.get("b"))))
    ));
    final Map<String, Object> result = fut.get(2, TimeUnit.SECONDS);
    assertEquals(Integer.valueOf(10), result.get("a"));
    assertEquals(Integer.valueOf(20), result.get("b"));
    assertEquals(Integer.valueOf(30), result.get("c"));
  }

  @Test(timeout = TIMEOUT)
  public void waterfall_short_circuits_on_failure() {
    final CompletableFuture<Map<String, Object>> fut = AsyncFut.Waterfall(List.of(
        acc -> CompletableFuture.completedFuture(Map.entry("a", 1)),
        acc -> CompletableFuture.failedFuture(new IllegalStateException("boom-mid")),
        acc -> { fail("step 3 must not run"); return CompletableFuture.completedFuture(Map.entry("c", 3)); }
    ));
    try {
      fut.get(2, TimeUnit.SECONDS);
      fail("expected ExecutionException");
    } catch (Exception e) {
      assertTrue(e.getCause().getMessage().contains("boom-mid"));
    }
  }

  @Test(timeout = TIMEOUT)
  public void waterfall_null_entry_skips_step_but_continues() throws Exception {
    final CompletableFuture<Map<String, Object>> fut = AsyncFut.Waterfall(List.of(
        acc -> CompletableFuture.completedFuture(Map.entry("a", 1)),
        acc -> CompletableFuture.completedFuture(null), // skip
        acc -> CompletableFuture.completedFuture(Map.entry("c", 3))
    ));
    final Map<String, Object> result = fut.get(2, TimeUnit.SECONDS);
    assertEquals(Integer.valueOf(1), result.get("a"));
    assertFalse(result.containsKey("b"));
    assertEquals(Integer.valueOf(3), result.get("c"));
  }

  @Test(timeout = TIMEOUT)
  public void waterfall_empty_list_completes_with_empty_map() throws Exception {
    final CompletableFuture<Map<String, Object>> fut = AsyncFut.Waterfall(List.of());
    final Map<String, Object> result = fut.get(2, TimeUnit.SECONDS);
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void waterfall_step_throwing_synchronously_surfaces_as_failed_future() {
    final CompletableFuture<Map<String, Object>> fut = AsyncFut.Waterfall(List.of(
        acc -> CompletableFuture.completedFuture(Map.entry("a", 1)),
        acc -> { throw new IllegalStateException("sync-throw"); }
    ));
    try {
      fut.get(2, TimeUnit.SECONDS);
      fail("expected ExecutionException");
    } catch (Exception e) {
      assertTrue(e.getCause().getMessage().contains("sync-throw"));
    }
  }

  // ====================== FilterMap ======================================

  @Test(timeout = TIMEOUT)
  public void filterMap_drops_null_results_preserves_order() throws Exception {
    final CompletableFuture<List<Integer>> fut = AsyncFut.FilterMap(
        List.of(1, 2, 3, 4, 5, 6),
        n -> CompletableFuture.completedFuture(n % 2 == 0 ? n * 10 : null));
    final List<Integer> result = fut.get(2, TimeUnit.SECONDS);
    assertEquals(List.of(20, 40, 60), result);
  }

  @Test(timeout = TIMEOUT)
  public void filterMap_short_circuits_on_failure() {
    final CompletableFuture<List<Integer>> fut = AsyncFut.FilterMap(
        List.of(1, 2, 3),
        n -> {
          if (n == 2) return CompletableFuture.failedFuture(new IllegalStateException("filter-boom"));
          return CompletableFuture.completedFuture(n * 10);
        });
    try {
      fut.get(2, TimeUnit.SECONDS);
      fail("expected ExecutionException");
    } catch (Exception e) {
      assertTrue(e.getCause().getMessage().contains("filter-boom"));
    }
  }

  @Test(timeout = TIMEOUT)
  public void filterMap_empty_input_completes_with_empty_list() throws Exception {
    final List<Integer> empty = List.of();
    final CompletableFuture<List<Integer>> fut = AsyncFut.<Integer, Integer>FilterMap(
        empty,
        n -> CompletableFuture.completedFuture(n));
    assertTrue(fut.get(2, TimeUnit.SECONDS).isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void filterMap_concurrent_execution() throws Exception {
    final ExecutorService exec = Executors.newFixedThreadPool(4);
    try {
      final AtomicInteger inFlight = new AtomicInteger();
      final AtomicInteger maxInFlight = new AtomicInteger();

      final CompletableFuture<List<Integer>> fut = AsyncFut.FilterMap(
          List.of(1, 2, 3, 4, 5, 6, 7, 8),
          n -> CompletableFuture.supplyAsync(() -> {
            final int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            try { Thread.sleep(5); } catch (InterruptedException ie) { /* */ }
            inFlight.decrementAndGet();
            return n % 2 == 0 ? n : (Integer) null;
          }, exec));

      final List<Integer> result = fut.get(5, TimeUnit.SECONDS);
      assertEquals(List.of(2, 4, 6, 8), result);
      assertTrue("expected concurrent execution, maxInFlight=" + maxInFlight.get(),
          maxInFlight.get() >= 2);
    } finally {
      exec.shutdown();
    }
  }

  // ====================== GroupBy ========================================

  @Test(timeout = TIMEOUT)
  public void groupBy_buckets_elements_by_async_key() throws Exception {
    final CompletableFuture<Map<String, List<Integer>>> fut = AsyncFut.GroupBy(
        List.of(1, 2, 3, 4, 5, 6),
        n -> CompletableFuture.completedFuture(n % 2 == 0 ? "even" : "odd"));
    final Map<String, List<Integer>> result = fut.get(2, TimeUnit.SECONDS);
    assertEquals(List.of(1, 3, 5), result.get("odd"));
    assertEquals(List.of(2, 4, 6), result.get("even"));
  }

  @Test(timeout = TIMEOUT)
  public void groupBy_empty_input_completes_with_empty_map() throws Exception {
    final CompletableFuture<Map<String, List<Integer>>> fut = AsyncFut.GroupBy(
        List.of(),
        n -> CompletableFuture.completedFuture("key"));
    assertTrue(fut.get(2, TimeUnit.SECONDS).isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void groupBy_short_circuits_on_keyer_failure() {
    final CompletableFuture<Map<String, List<Integer>>> fut = AsyncFut.GroupBy(
        List.of(1, 2, 3),
        n -> {
          if (n == 2) return CompletableFuture.failedFuture(new IllegalStateException("key-boom"));
          return CompletableFuture.completedFuture("k");
        });
    try {
      fut.get(2, TimeUnit.SECONDS);
      fail("expected ExecutionException");
    } catch (Exception e) {
      assertTrue(e.getCause().getMessage().contains("key-boom"));
    }
  }

  // ====================== Whilst =========================================

  @Test(timeout = TIMEOUT)
  public void whilst_runs_while_test_returns_true_collects_results() throws Exception {
    final AtomicInteger counter = new AtomicInteger();
    final CompletableFuture<List<Integer>> fut = AsyncFut.Whilst(
        () -> counter.get() < 5,
        () -> CompletableFuture.completedFuture(counter.getAndIncrement()));
    final List<Integer> result = fut.get(2, TimeUnit.SECONDS);
    assertEquals(List.of(0, 1, 2, 3, 4), result);
    assertEquals(5, counter.get());
  }

  @Test(timeout = TIMEOUT)
  public void whilst_returns_empty_when_test_initially_false() throws Exception {
    final CompletableFuture<List<Integer>> fut = AsyncFut.Whilst(
        () -> false,
        () -> { fail("body must not run"); return CompletableFuture.completedFuture(0); });
    final List<Integer> result = fut.get(2, TimeUnit.SECONDS);
    assertTrue(result.isEmpty());
  }

  @Test(timeout = TIMEOUT)
  public void whilst_short_circuits_on_body_failure() {
    final AtomicInteger counter = new AtomicInteger();
    final CompletableFuture<List<Integer>> fut = AsyncFut.Whilst(
        () -> counter.get() < 10,
        () -> {
          final int now = counter.getAndIncrement();
          if (now == 3) return CompletableFuture.failedFuture(new IllegalStateException("whilst-boom"));
          return CompletableFuture.completedFuture(now);
        });
    try {
      fut.get(2, TimeUnit.SECONDS);
      fail("expected ExecutionException");
    } catch (Exception e) {
      assertTrue(e.getCause().getMessage().contains("whilst-boom"));
    }
    // NeoWhilst's recursion strategy can race one extra body call: when m.run() returns
    // before the body's async failure has propagated, NeoWhilst checks the test again and
    // may recurse once more. Counter ends at 4 or 5 depending on timing. The important
    // invariant is that the loop short-circuits well short of the 10-iteration ceiling.
    assertTrue("counter " + counter.get() + " should be 4 or 5 (short-circuit)",
        counter.get() == 4 || counter.get() == 5);
    assertTrue("counter " + counter.get() + " must be well short of 10",
        counter.get() < 10);
  }

  // ====================== DoWhilst =======================================

  @Test(timeout = TIMEOUT)
  public void doWhilst_runs_body_at_least_once_even_if_test_false() throws Exception {
    final AtomicInteger ran = new AtomicInteger();
    final CompletableFuture<List<Integer>> fut = AsyncFut.DoWhilst(
        () -> false,
        () -> CompletableFuture.completedFuture(ran.incrementAndGet()));
    final List<Integer> result = fut.get(2, TimeUnit.SECONDS);
    assertEquals("body ran once before test was consulted", 1, ran.get());
    assertEquals(List.of(1), result);
  }

  @Test(timeout = TIMEOUT)
  public void doWhilst_loops_while_test_true() throws Exception {
    final AtomicInteger counter = new AtomicInteger();
    final CompletableFuture<List<Integer>> fut = AsyncFut.DoWhilst(
        () -> counter.get() < 4,
        () -> CompletableFuture.completedFuture(counter.incrementAndGet()));
    final List<Integer> result = fut.get(2, TimeUnit.SECONDS);
    assertEquals(List.of(1, 2, 3, 4), result);
  }

  // ====================== Cross-cutting ==================================

  @Test(timeout = TIMEOUT)
  public void filterMap_then_groupBy_composition() throws Exception {
    final CompletableFuture<Map<String, List<Integer>>> fut = AsyncFut.FilterMap(
            List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
            n -> CompletableFuture.completedFuture(n > 3 ? n : null)) // keep > 3
        .thenCompose(filtered ->
            AsyncFut.GroupBy(filtered,
                n -> CompletableFuture.completedFuture(n % 2 == 0 ? "even" : "odd")));

    final Map<String, List<Integer>> result = fut.get(2, TimeUnit.SECONDS);
    assertEquals(List.of(5, 7, 9), result.get("odd"));
    assertEquals(List.of(4, 6, 8, 10), result.get("even"));
  }

  @Test(timeout = TIMEOUT)
  public void waterfall_chained_with_thenCompose() throws Exception {
    final CompletableFuture<String> fut = AsyncFut.Waterfall(List.of(
        (java.util.function.Function<Map<String, Object>, CompletableFuture<Map.Entry<String, Object>>>)
          acc -> CompletableFuture.completedFuture(Map.entry("greeting", "hello"))
    )).thenApply(map -> map.get("greeting") + " world");
    assertEquals("hello world", fut.get(2, TimeUnit.SECONDS));
  }
}
