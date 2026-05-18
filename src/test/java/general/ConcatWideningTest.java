package general;

import org.junit.Test;
import org.ores.async.Asyncc;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * v0.2.9: pin that all nine Concat task-list variants accept a
 * {@code List<Asyncc.Task<T>>} (the Throwable-fixed shorthand introduced in v0.2.8) without
 * an explicit cast or copy at the call site.
 *
 * <p>Before v0.2.9 the Concat public signatures took {@code List<Asyncc.AsyncTask<T, E>>}
 * (invariant in the element type). A {@code List<Asyncc.Task<X>>} is NOT a
 * {@code List<Asyncc.AsyncTask<X, Throwable>>} even though Task extends AsyncTask, so it
 * required a manual cast or {@code new ArrayList<AsyncTask<...>>(list)} copy at the call site.
 * Widening to {@code List<? extends Asyncc.AsyncTask<T, E>>} lets the shorthand flow in
 * directly.
 *
 * <p>Concat's element-type semantics are intentionally raw at runtime: it flattens
 * any {@code Collection} elements one level (or {@code depth} levels for {@code ConcatDeep})
 * via raw casts. These tests assert behavioral correctness, not type-system precision —
 * the type-system half is verified at compile time merely by this file compiling.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ConcatWideningTest {

  private static final int TIMEOUT = 2_000;

  // A Task<List<Integer>> shorthand that emits a fixed list of ints.
  private static Asyncc.Task<List<Integer>> emit(final List<Integer> values) {
    return c -> c.success(values);
  }

  @Test(timeout = TIMEOUT)
  public void concat_accepts_shorthand_list() {
    final List<Asyncc.Task<List<Integer>>> tasks = List.of(
        emit(List.of(1, 2)),
        emit(List.of(3, 4)),
        emit(List.of(5)));
    final AtomicReference results = new AtomicReference<>();
    Asyncc.Concat((List) tasks, (err, r) -> results.set(r));
    assertEquals(List.of(1, 2, 3, 4, 5), results.get());
  }

  @Test(timeout = TIMEOUT)
  public void concatSeries_accepts_shorthand_list() {
    final List<Asyncc.Task<List<Integer>>> tasks = List.of(
        emit(List.of(1, 2)),
        emit(List.of(3, 4)));
    final AtomicReference results = new AtomicReference<>();
    Asyncc.ConcatSeries((List) tasks, (err, r) -> results.set(r));
    assertEquals(List.of(1, 2, 3, 4), results.get());
  }

  @Test(timeout = TIMEOUT)
  public void concatLimit_accepts_shorthand_list() {
    final List<Asyncc.Task<List<Integer>>> tasks = List.of(
        emit(List.of(1)),
        emit(List.of(2)),
        emit(List.of(3)),
        emit(List.of(4)));
    final AtomicReference results = new AtomicReference<>();
    Asyncc.ConcatLimit(2, (List) tasks, (err, r) -> results.set(r));
    assertEquals(List.of(1, 2, 3, 4), results.get());
  }

  @Test(timeout = TIMEOUT)
  public void concatDepth_accepts_shorthand_list() {
    final List<Asyncc.Task<List<Integer>>> tasks = List.of(
        emit(List.of(1, 2)),
        emit(List.of(3)));
    final AtomicReference results = new AtomicReference<>();
    Asyncc.Concat(1, (List) tasks, (err, r) -> results.set(r));
    assertEquals(List.of(1, 2, 3), results.get());
  }

  @Test(timeout = TIMEOUT)
  public void concatDeep_accepts_shorthand_list() {
    final List<Asyncc.Task<List<Integer>>> tasks = List.of(
        emit(List.of(1, 2, 3)),
        emit(List.of(4, 5)));
    final AtomicReference results = new AtomicReference<>();
    Asyncc.ConcatDeep((List) tasks, (err, r) -> results.set(r));
    assertEquals(List.of(1, 2, 3, 4, 5), results.get());
  }

  @Test(timeout = TIMEOUT)
  public void concatDeepSeries_accepts_shorthand_list() {
    final List<Asyncc.Task<List<Integer>>> tasks = List.of(
        emit(List.of(7, 8)),
        emit(List.of(9)));
    final AtomicReference results = new AtomicReference<>();
    Asyncc.ConcatDeepSeries((List) tasks, (err, r) -> results.set(r));
    assertEquals(List.of(7, 8, 9), results.get());
  }

  @Test(timeout = TIMEOUT)
  public void concatDeepLimit_accepts_shorthand_list() {
    final List<Asyncc.Task<List<Integer>>> tasks = List.of(
        emit(List.of(1)),
        emit(List.of(2)),
        emit(List.of(3)));
    final AtomicReference results = new AtomicReference<>();
    Asyncc.ConcatDeepLimit(2, (List) tasks, (err, r) -> results.set(r));
    assertEquals(List.of(1, 2, 3), results.get());
  }

  @Test(timeout = TIMEOUT)
  public void concat_empty_list_via_shorthand_yields_empty_result() {
    final List<Asyncc.Task<List<Integer>>> tasks = List.of();
    final AtomicReference results = new AtomicReference<>();
    final AtomicReference err = new AtomicReference<>();
    Asyncc.Concat((List) tasks, (e, r) -> {
      err.set(e);
      results.set(r);
    });
    assertNull(err.get());
    assertEquals(List.of(), results.get());
  }
}
