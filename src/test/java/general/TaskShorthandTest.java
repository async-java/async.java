package general;

import org.junit.Test;
import org.ores.async.Asyncc;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * Pins the v0.2.8 ergonomic shorthand: {@link Asyncc.Task} and {@link Asyncc.Callback} are
 * Throwable-fixed sub-interfaces of {@link Asyncc.AsyncTask} and {@link Asyncc.IAsyncCallback}
 * respectively, and a {@code List<Asyncc.Task<T>>} flows into the widened
 * {@code Parallel/ParallelLimit/Series(List<? extends AsyncTask<T, E>>, ...)} combinator
 * signatures without an explicit cast.
 *
 * <p>This is the user-visible ergonomic the issue was filed against: dropping
 * {@code , Throwable} from explicitly-typed stream pipelines like:
 *
 * <pre>
 *   final var classifyTasks = items.stream()
 *       .&lt;Asyncc.Task&lt;Result&gt;&gt;map(it -&gt; c -&gt; { ... })
 *       .toList();
 *   Asyncc.&lt;Result, Throwable&gt;ParallelLimit(8, classifyTasks, ...);
 * </pre>
 */
public class TaskShorthandTest {

  @Test(timeout = 5_000)
  public void task_shorthand_flows_into_Parallel() throws Exception {
    final ExecutorService exec = Executors.newFixedThreadPool(4);
    try {
      final List<Integer> items = List.of(1, 2, 3, 4);

      // The shorthand: no `, Throwable` anywhere.
      final List<Asyncc.Task<Integer>> tasks = items.stream()
          .<Asyncc.Task<Integer>>map(n ->
              c -> exec.submit(() -> c.success(n * n)))
          .toList();

      final CompletableFuture<List<Integer>> done = new CompletableFuture<>();
      Asyncc.<Integer, Throwable>Parallel(tasks, (err, results) -> {
        if (err != null) done.completeExceptionally(err);
        else done.complete(results);
      });

      assertEquals(List.of(1, 4, 9, 16), done.get(2, TimeUnit.SECONDS));
    } finally {
      exec.shutdown();
    }
  }

  @Test(timeout = 5_000)
  public void task_shorthand_flows_into_ParallelLimit() throws Exception {
    final ExecutorService exec = Executors.newFixedThreadPool(4);
    try {
      final List<Integer> items = List.of(1, 2, 3, 4, 5, 6, 7, 8);

      final List<Asyncc.Task<Integer>> tasks = items.stream()
          .<Asyncc.Task<Integer>>map(n ->
              c -> exec.submit(() -> {
                try { Thread.sleep(2); } catch (InterruptedException ie) { /* */ }
                c.success(n + 100);
              }))
          .toList();

      final CompletableFuture<List<Integer>> done = new CompletableFuture<>();
      Asyncc.<Integer, Throwable>ParallelLimit(3, tasks, (err, results) -> {
        if (err != null) done.completeExceptionally(err);
        else done.complete(results);
      });

      assertEquals(List.of(101, 102, 103, 104, 105, 106, 107, 108), done.get(3, TimeUnit.SECONDS));
    } finally {
      exec.shutdown();
    }
  }

  @Test(timeout = 5_000)
  public void task_shorthand_flows_into_Series() throws Exception {
    final List<String> names = List.of("alpha", "beta", "gamma");

    final List<Asyncc.Task<String>> tasks = names.stream()
        .<Asyncc.Task<String>>map(name ->
            c -> c.success(name.toUpperCase()))
        .toList();

    final CompletableFuture<List<String>> done = new CompletableFuture<>();
    Asyncc.<String, Throwable>Series(tasks, (err, results) -> {
      if (err != null) done.completeExceptionally(err);
      else done.complete(results);
    });

    assertEquals(List.of("ALPHA", "BETA", "GAMMA"), done.get(2, TimeUnit.SECONDS));
  }

  @Test(timeout = 5_000)
  public void task_shorthand_matches_AsyncTask_in_same_call() throws Exception {
    // Mixing styles in the same call should still work: Task<T> and AsyncTask<T, Throwable>
    // are the same erasure and Task<T> IS-A AsyncTask<T, Throwable>.
    final Asyncc.Task<String> a = c -> c.success("a");
    final Asyncc.AsyncTask<String, Throwable> b = c -> c.success("b");

    final CompletableFuture<List<String>> done = new CompletableFuture<>();
    Asyncc.<String, Throwable>Parallel(List.of(a, b), (err, results) -> {
      if (err != null) done.completeExceptionally(err);
      else done.complete(results);
    });

    assertEquals(List.of("a", "b"), done.get(2, TimeUnit.SECONDS));
  }

  @Test(timeout = 5_000)
  public void task_shorthand_supports_c_success_and_c_fail_defaults() throws Exception {
    // Confirms that the inherited success / fail default methods are visible on
    // Asyncc.Task<T>'s callback (which is IAsyncCallback<T, Throwable>).
    final Asyncc.Task<String> ok    = c -> c.success("ok");
    final Asyncc.Task<String> oops  = c -> c.fail(new IllegalStateException("oops"));

    final CompletableFuture<List<String>> done = new CompletableFuture<>();
    Asyncc.<String, Throwable>Parallel(List.of(ok, oops), (err, results) -> {
      if (err != null) done.completeExceptionally(err);
      else done.complete(results);
    });

    try {
      done.get(2, TimeUnit.SECONDS);
      org.junit.Assert.fail("expected ExecutionException");
    } catch (ExecutionException ee) {
      org.junit.Assert.assertTrue(ee.getCause().getMessage().contains("oops"));
    }
  }
}
