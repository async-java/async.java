package general;

import org.junit.Assume;
import org.junit.Test;
import org.ores.async.AsyncFut;
import org.ores.async.AsyncLoom;
import org.ores.async.Asyncc;
import org.ores.async.WrapFuture;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pins interop with plain JDK Futures and Java 21 virtual threads.
 */
public class FutureAndLoomInteropTest {

  @Test(timeout = 10_000)
  public void toCompletableFuture_calls_future_get_inside_the_library() throws Exception {
    final AtomicInteger getCalls = new AtomicInteger();
    final Future<String> future = new Future<>() {
      @Override public boolean cancel(boolean mayInterruptIfRunning) { return false; }
      @Override public boolean isCancelled() { return false; }
      @Override public boolean isDone() { return true; }
      @Override public String get() { getCalls.incrementAndGet(); return "ok"; }
      @Override public String get(long timeout, TimeUnit unit) { getCalls.incrementAndGet(); return "ok"; }
    };

    final ExecutorService waitExec = Executors.newSingleThreadExecutor();
    try {
      final CompletableFuture<String> cf = WrapFuture.toCompletableFuture(waitExec, future);
      assertEquals("ok", cf.get(2, TimeUnit.SECONDS));
      assertEquals(1, getCalls.get());
    } finally {
      waitExec.shutdownNow();
    }
  }

  @Test(timeout = 10_000)
  public void toCompletableFuture_unwraps_execution_exception_cause() throws Exception {
    final IllegalStateException boom = new IllegalStateException("boom");
    final FutureTask<String> future = new FutureTask<>(() -> {
      throw boom;
    });
    new Thread(future, "future-interop-failing-work").start();

    final ExecutorService waitExec = Executors.newSingleThreadExecutor();
    try {
      final CompletableFuture<String> cf = WrapFuture.toCompletableFuture(waitExec, future);
      try {
        cf.get(2, TimeUnit.SECONDS);
        fail("expected ExecutionException");
      } catch (ExecutionException ee) {
        assertSame(boom, ee.getCause());
      }
    } finally {
      waitExec.shutdownNow();
    }
  }

  @Test(timeout = 10_000)
  public void cancelling_completable_future_cancels_underlying_future() throws Exception {
    final FutureTask<String> future = new FutureTask<>(() -> {
      Thread.sleep(5_000);
      return "late";
    });
    final Thread worker = new Thread(future, "future-interop-cancel-work");
    worker.start();

    final ExecutorService waitExec = Executors.newSingleThreadExecutor();
    try {
      final CompletableFuture<String> cf = WrapFuture.toCompletableFuture(waitExec, future);
      assertTrue(cf.cancel(true));
      assertTrue(future.isCancelled());
      worker.join(1_000);
    } finally {
      waitExec.shutdownNow();
    }
  }

  @Test(timeout = 10_000)
  public void fromFuture_drops_plain_future_into_callback_combinator() throws Exception {
    final ExecutorService workExec = Executors.newFixedThreadPool(2);
    final ExecutorService waitExec = Executors.newFixedThreadPool(2);
    try {
      final CompletableFuture<List<String>> cf = WrapFuture.toFuture(c ->
          Asyncc.<String, Throwable>Parallel(List.of(
              WrapFuture.fromFuture(waitExec, workExec.submit(() -> "alpha")),
              WrapFuture.fromFuture(waitExec, workExec.submit(() -> "beta"))
          ), c)
      );

      assertEquals(List.of("alpha", "beta"), cf.get(2, TimeUnit.SECONDS));
    } finally {
      waitExec.shutdownNow();
      workExec.shutdownNow();
    }
  }

  @Test(timeout = 10_000)
  public void parallelFutures_collects_plain_future_results_in_order() throws Exception {
    final ExecutorService workExec = Executors.newFixedThreadPool(3);
    final ExecutorService waitExec = Executors.newCachedThreadPool();
    try {
      final List<Future<Integer>> futures = List.of(
          workExec.submit(() -> {
            Thread.sleep(20);
            return 1;
          }),
          workExec.submit(() -> 2),
          workExec.submit(() -> 3)
      );

      assertEquals(
          List.of(1, 2, 3),
          AsyncFut.ParallelFutures(waitExec, futures).get(2, TimeUnit.SECONDS));
    } finally {
      waitExec.shutdownNow();
      workExec.shutdownNow();
    }
  }

  @Test(timeout = 10_000)
  public void raceFutures_returns_first_plain_future_result() throws Exception {
    final ExecutorService workExec = Executors.newFixedThreadPool(2);
    final ExecutorService waitExec = Executors.newCachedThreadPool();
    try {
      final List<Future<String>> futures = List.of(
          workExec.submit(() -> {
            Thread.sleep(200);
            return "slow";
          }),
          workExec.submit(() -> "fast")
      );

      assertEquals("fast", AsyncFut.RaceFutures(waitExec, futures).get(2, TimeUnit.SECONDS));
    } finally {
      waitExec.shutdownNow();
      workExec.shutdownNow();
    }
  }

  @Test(timeout = 10_000)
  public void asyncLoom_detection_is_safe_on_java17() {
    if (!AsyncLoom.isSupported()) {
      assertFalse(AsyncLoom.isVirtualThread());
      try {
        AsyncLoom.newVirtualThreadPerTaskExecutor();
        fail("expected UnsupportedOperationException");
      } catch (UnsupportedOperationException expected) {
        assertTrue(expected.getMessage().contains("JDK 21"));
      }
    }
  }

  @Test(timeout = 10_000)
  public void asyncLoom_supply_runs_callable_on_virtual_thread_when_available() throws Exception {
    Assume.assumeTrue("requires JDK 21+ virtual threads", AsyncLoom.isSupported());

    final CompletableFuture<Boolean> cf = AsyncLoom.supply(AsyncLoom::isVirtualThread);
    assertTrue(cf.get(2, TimeUnit.SECONDS));
  }

  @Test(timeout = 10_000)
  public void asyncLoom_parallelBlocking_runs_tasks_on_virtual_threads_when_available() throws Exception {
    Assume.assumeTrue("requires JDK 21+ virtual threads", AsyncLoom.isSupported());

    final List<Callable<Boolean>> tasks = List.of(
        AsyncLoom::isVirtualThread,
        AsyncLoom::isVirtualThread,
        AsyncLoom::isVirtualThread
    );

    assertEquals(
        List.of(true, true, true),
        AsyncLoom.ParallelBlocking(tasks).get(2, TimeUnit.SECONDS));
  }

  @Test(timeout = 10_000)
  public void asyncc_blocking_wrappers_report_unsupported_on_java17() throws Exception {
    Assume.assumeFalse("only meaningful on Java 17/unsupported runtimes", AsyncLoom.isSupported());

    final CompletableFuture<Throwable> err = new CompletableFuture<>();
    Asyncc.SeriesBlocking(List.of(() -> "x"), (e, results) -> err.complete(e));

    assertTrue(err.get(2, TimeUnit.SECONDS) instanceof UnsupportedOperationException);
  }

  @Test(timeout = 10_000)
  public void asyncLoom_seriesBlocking_runs_each_step_on_virtual_thread_when_available() throws Exception {
    Assume.assumeTrue("requires JDK 21+ virtual threads", AsyncLoom.isSupported());

    final AtomicInteger order = new AtomicInteger();
    final List<Callable<String>> tasks = List.of(
        () -> {
          assertTrue(AsyncLoom.isVirtualThread());
          assertEquals(0, order.getAndIncrement());
          return "a";
        },
        () -> {
          assertTrue(AsyncLoom.isVirtualThread());
          assertEquals(1, order.getAndIncrement());
          return "b";
        }
    );

    assertEquals(List.of("a", "b"), AsyncLoom.SeriesBlocking(tasks).get(2, TimeUnit.SECONDS));
  }

  @Test(timeout = 10_000)
  public void asyncLoom_reduceBlocking_runs_reducer_on_virtual_thread_when_available() throws Exception {
    Assume.assumeTrue("requires JDK 21+ virtual threads", AsyncLoom.isSupported());

    final Integer sum = AsyncLoom.ReduceBlocking(
        List.of(1, 2, 3),
        0,
        (acc, item) -> {
          assertTrue(AsyncLoom.isVirtualThread());
          return acc + item;
        }).get(2, TimeUnit.SECONDS);

    assertEquals(Integer.valueOf(6), sum);
  }

  @Test(timeout = 10_000)
  public void asyncLoom_concatBlocking_runs_mapper_on_virtual_threads_when_available() throws Exception {
    Assume.assumeTrue("requires JDK 21+ virtual threads", AsyncLoom.isSupported());

    final List<Integer> result = AsyncLoom.ConcatBlocking(
        List.of(1, 2, 3),
        item -> {
          assertTrue(AsyncLoom.isVirtualThread());
          return List.of(item, item * 10);
        }).get(2, TimeUnit.SECONDS);

    assertEquals(List.of(1, 10, 2, 20, 3, 30), result);
  }

  @Test(timeout = 10_000)
  public void asyncc_raceBlocking_races_callables_on_virtual_threads_when_available() throws Exception {
    Assume.assumeTrue("requires JDK 21+ virtual threads", AsyncLoom.isSupported());

    final CompletableFuture<String> result = new CompletableFuture<>();
    Asyncc.<String>RaceBlocking(List.<Callable<String>>of(
        () -> {
          assertTrue(AsyncLoom.isVirtualThread());
          Thread.sleep(200);
          return "slow";
        },
        () -> {
          assertTrue(AsyncLoom.isVirtualThread());
          return "fast";
        }
    ), (err, value) -> {
      if (err != null) {
        result.completeExceptionally(err);
      } else {
        result.complete(value);
      }
    });

    assertEquals("fast", result.get(2, TimeUnit.SECONDS));
  }

  @Test(timeout = 10_000)
  public void asyncc_concatBlocking_reports_flattened_callback_result_when_available() throws Exception {
    Assume.assumeTrue("requires JDK 21+ virtual threads", AsyncLoom.isSupported());

    final CompletableFuture<List<Integer>> result = new CompletableFuture<>();
    Asyncc.ConcatBlocking(List.of(1, 2), item -> {
      assertTrue(AsyncLoom.isVirtualThread());
      return List.of(item, item + 10);
    }, (err, value) -> {
      if (err != null) {
        result.completeExceptionally(err);
      } else {
        result.complete(value);
      }
    });

    assertEquals(List.of(1, 11, 2, 12), result.get(2, TimeUnit.SECONDS));
  }
}
