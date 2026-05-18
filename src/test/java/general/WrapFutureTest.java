package general;

import org.junit.Test;
import org.ores.async.Asyncc;
import org.ores.async.WrapFuture;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins the {@link WrapFuture} contract:
 *
 * <ul>
 *   <li>{@code toFuture(setup)} hands the setup a callback wired to the returned future.</li>
 *   <li>Calling {@code c.success(v)} completes the future with the value.</li>
 *   <li>Calling {@code c.fail(t)} completes the future exceptionally; {@code Throwable} errors
 *       are preserved as-is; non-{@code Throwable} errors are wrapped.</li>
 *   <li>If {@code setup} itself throws synchronously, the future completes exceptionally.</li>
 *   <li>{@code fromStage} adapts a {@code CompletionStage} into an async.java task.</li>
 *   <li>{@code fromCallable} dispatches sync work onto the executor and surfaces both the
 *       value and any exception.</li>
 *   <li>End-to-end: {@code toFuture(c -> Asyncc.Parallel(tasks, c))} completes with the
 *       collected results.</li>
 * </ul>
 */
public class WrapFutureTest {

  @Test(timeout = 10_000)
  public void toFuture_completes_on_success() throws Exception {
    final CompletableFuture<String> fut = WrapFuture.toFuture(c -> c.success("ok"));
    assertEquals("ok", fut.get(2, TimeUnit.SECONDS));
  }

  @Test(timeout = 10_000)
  public void toFuture_completes_exceptionally_with_throwable_passthrough() throws Exception {
    final IllegalStateException cause = new IllegalStateException("boom");
    final CompletableFuture<String> fut = WrapFuture.toFuture(c -> c.fail(cause));
    try {
      fut.get(2, TimeUnit.SECONDS);
      fail("expected ExecutionException");
    } catch (java.util.concurrent.ExecutionException ee) {
      assertSame("Throwable err is preserved as-is", cause, ee.getCause());
    }
  }

  @Test(timeout = 10_000)
  public void toFutureAny_wraps_non_throwable_err_in_RuntimeException() throws Exception {
    final CompletableFuture<String> fut = WrapFuture.<String, Object>toFutureAny(c -> c.fail("string-as-error"));
    try {
      fut.get(2, TimeUnit.SECONDS);
      fail("expected ExecutionException");
    } catch (java.util.concurrent.ExecutionException ee) {
      assertTrue(
          "wrapped message should include the err",
          ee.getCause().getMessage().contains("string-as-error"));
    }
  }

  @Test(timeout = 10_000)
  public void toFuture_completes_exceptionally_when_setup_throws_synchronously() {
    final CompletableFuture<String> fut = WrapFuture.toFuture((java.util.function.Consumer<Asyncc.IAsyncCallback<String, Throwable>>) c -> {
      throw new IllegalStateException("setup blew up");
    });
    try {
      fut.get(2, TimeUnit.SECONDS);
      fail("expected ExecutionException");
    } catch (Exception e) {
      assertTrue(e.getCause().getMessage().contains("setup blew up"));
    }
  }

  @Test(timeout = 10_000)
  public void toFuture_endtoend_with_Asyncc_Parallel() throws Exception {
    final ExecutorService exec = Executors.newFixedThreadPool(4);
    try {
      final CompletableFuture<List<String>> fut = WrapFuture.toFuture(c ->
          Asyncc.<String, Throwable>Parallel(List.of(
              cb -> exec.submit(() -> cb.success("alpha")),
              cb -> exec.submit(() -> cb.success("beta")),
              cb -> exec.submit(() -> cb.success("gamma"))
          ), c)
      );
      final List<String> results = fut.get(5, TimeUnit.SECONDS);
      assertEquals(List.of("alpha", "beta", "gamma"), results);
    } finally {
      exec.shutdown();
      exec.awaitTermination(2, TimeUnit.SECONDS);
    }
  }

  @Test(timeout = 10_000)
  public void fromStage_adapts_CompletionStage_as_AsyncTask() throws Exception {
    final CompletableFuture<String> upstream = CompletableFuture.completedFuture("hello");

    final CompletableFuture<List<String>> fut = WrapFuture.toFuture(c ->
        Asyncc.<String, Throwable>Parallel(List.of(
            WrapFuture.fromStage(upstream),
            WrapFuture.fromStage(CompletableFuture.completedFuture("world"))
        ), c)
    );

    assertEquals(List.of("hello", "world"), fut.get(2, TimeUnit.SECONDS));
  }

  @Test(timeout = 10_000)
  public void fromCallable_dispatches_sync_work_and_collects_result() throws Exception {
    final ExecutorService exec = Executors.newSingleThreadExecutor();
    try {
      final AtomicInteger ran = new AtomicInteger();
      final CompletableFuture<Integer> fut = WrapFuture.toFuture(c ->
          Asyncc.<Integer, Throwable>Parallel(List.of(
              WrapFuture.fromCallable(exec, () -> { ran.incrementAndGet(); return 42; })
          ), (err, results) -> {
            if (err != null) c.fail(err);
            else c.success(results.get(0));
          })
      );
      assertEquals(Integer.valueOf(42), fut.get(2, TimeUnit.SECONDS));
      assertEquals(1, ran.get());
    } finally {
      exec.shutdown();
      exec.awaitTermination(2, TimeUnit.SECONDS);
    }
  }

  @Test(timeout = 10_000)
  public void fromCallable_surfaces_callable_exception() throws Exception {
    final ExecutorService exec = Executors.newSingleThreadExecutor();
    try {
      final CompletableFuture<List<Integer>> fut = WrapFuture.toFuture(c ->
          Asyncc.<Integer, Throwable>Parallel(List.of(
              WrapFuture.fromCallable(exec, () -> { throw new IllegalStateException("callable failed"); })
          ), c)
      );
      try {
        fut.get(2, TimeUnit.SECONDS);
        fail("expected ExecutionException");
      } catch (java.util.concurrent.ExecutionException ee) {
        assertTrue(ee.getCause().getMessage().contains("callable failed"));
      }
    } finally {
      exec.shutdown();
      exec.awaitTermination(2, TimeUnit.SECONDS);
    }
  }
}
