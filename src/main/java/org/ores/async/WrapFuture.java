package org.ores.async;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Bridge between async.java's error-first callback shape and the JDK's promise primitive
 * ({@link CompletableFuture} / {@link CompletionStage}).
 *
 * <p>Three adapters cover the common interop directions:
 *
 * <ul>
 *   <li>{@link #toFuture(Consumer)} — async.java callback → {@code CompletableFuture}.
 *       Wrap a combinator invocation in a future at the boundary where you need to return a
 *       promise to a framework (Spring WebFlux, Akka HTTP, gRPC stub).</li>
 *   <li>{@link #fromStage(CompletionStage)} — {@code CompletionStage} → async.java
 *       {@link Asyncc.AsyncTask}. Useful when consuming a third-party promise-returning API
 *       (a JDBC async driver, an HTTP client) inside an async.java combinator.</li>
 *   <li>{@link #fromStage(Supplier)} — lazy {@code CompletionStage} supplier → async.java
 *       task. Use this with {@code Series} / {@code ParallelLimit} when the stage should not be
 *       created until the combinator actually starts that task.</li>
 *   <li>{@link #fromCallable(Executor, Callable)} — wrap a sync, possibly-blocking
 *       {@link Callable} as an async.java task, dispatching it onto the provided executor.</li>
 *   <li>{@link #fromFuture(Executor, Future)} — wrap a plain JDK {@link Future} as an async.java
 *       task, waiting on the provided executor so the caller thread is never blocked.</li>
 * </ul>
 *
 * <h3>The {@code toFuture} idiom</h3>
 *
 * <p>The setup consumer receives an {@link Asyncc.IAsyncCallback} that is wired to complete
 * the future. You can pass it directly as the final callback of any combinator:
 *
 * <pre>
 *   CompletableFuture&lt;List&lt;String&gt;&gt; handle(Request req) {
 *       return WrapFuture.toFuture(c -&gt;
 *           Asyncc.Parallel(List.of(
 *               cb -&gt; exec.submit(() -&gt; cb.success(fetchA(req))),
 *               cb -&gt; exec.submit(() -&gt; cb.success(fetchB(req)))
 *           ), c)
 *       );
 *   }
 * </pre>
 *
 * <p>The callback {@code c} is fired exactly once by the combinator. If {@code err != null}, the
 * future completes exceptionally (wrapping non-{@link Throwable} errors in a
 * {@link RuntimeException}); otherwise it completes with the value.
 *
 * <h3>The {@code fromStage} idiom</h3>
 *
 * <p>Wrap a third-party {@code CompletionStage} as an async.java task you can drop into any
 * combinator's task position:
 *
 * <pre>
 *   Asyncc.Parallel(List.of(
 *       WrapFuture.fromStage(db.queryAsync("SELECT ...")),
 *       WrapFuture.fromStage(redis.getAsync(key)),
 *       WrapFuture.fromStage(httpClient.sendAsync(req).thenApply(HttpResponse::body))
 *   ), (err, results) -&gt; { /* ... *&#47; });
 * </pre>
 *
 * <h3>Static-import convention</h3>
 *
 * <p>Pair with {@link WrapErrFirst} as a static import:
 *
 * <pre>
 *   import static org.ores.async.WrapErrFirst.wrap;
 *   import static org.ores.async.WrapFuture.toFuture;
 *   import static org.ores.async.WrapFuture.fromStage;
 * </pre>
 *
 * @since 0.2.7
 */
public final class WrapFuture {

  private WrapFuture() {}

  /**
   * Adapt an async.java combinator invocation into a {@link CompletableFuture}.
   *
   * <p>The {@code setup} consumer is invoked synchronously with a callback that, when fired,
   * completes the returned future. Pass this callback directly to any combinator as its final
   * callback &mdash; the future then mirrors the combinator's outcome.
   *
   * <p>Error type is fixed to {@link Throwable} (the natural match for
   * {@code CompletableFuture}'s {@code completeExceptionally(Throwable)}). For async.java
   * combinators that use a non-{@code Throwable} error type, see {@link #toFutureAny(Consumer)}.
   *
   * <p>Synchronous failure: if {@code setup.accept(...)} itself throws, the future completes
   * exceptionally with that throwable.
   *
   * <h4>Usage</h4>
   *
   * <pre>
   *   CompletableFuture&lt;List&lt;String&gt;&gt; result = WrapFuture.toFuture(c -&gt;
   *       Asyncc.&lt;String, Throwable&gt;Parallel(tasks, c)
   *   );
   * </pre>
   *
   * @param <V> value type produced by the combinator
   * @param setup a consumer that wires the supplied callback into a combinator
   * @return a {@code CompletableFuture} that completes when the combinator's final callback fires
   */
  public static <V> CompletableFuture<V> toFuture(
      final Consumer<Asyncc.IAsyncCallback<V, Throwable>> setup) {

    final CompletableFuture<V> cf = new CompletableFuture<>();
    final Asyncc.IAsyncCallback<V, Throwable> sink = (err, value) -> {
      if (err != null) {
        cf.completeExceptionally(err);
      } else {
        cf.complete(value);
      }
    };

    try {
      setup.accept(sink);
    } catch (Throwable t) {
      cf.completeExceptionally(t);
    }

    return cf;
  }

  /**
   * Generic-error-type variant of {@link #toFuture(Consumer)}. Use this when the underlying
   * async.java combinator's error type is something other than {@link Throwable} (e.g.
   * {@link Object} or a domain-specific error tag). Non-{@code Throwable} errors are wrapped
   * in a {@link RuntimeException} for the future's exceptional completion.
   *
   * <p>Most callers don't need this; prefer {@link #toFuture(Consumer)}.
   *
   * @param <V> value type
   * @param <E> error type
   * @param setup a consumer that wires the supplied callback into a combinator
   */
  public static <V, E> CompletableFuture<V> toFutureAny(
      final Consumer<Asyncc.IAsyncCallback<V, E>> setup) {

    final CompletableFuture<V> cf = new CompletableFuture<>();
    final Asyncc.IAsyncCallback<V, E> sink = (err, value) -> {
      if (err != null) {
        cf.completeExceptionally(toThrowable(err));
      } else {
        cf.complete(value);
      }
    };

    try {
      setup.accept(sink);
    } catch (Throwable t) {
      cf.completeExceptionally(t);
    }

    return cf;
  }

  /**
   * Adapt a {@link CompletionStage} to an async.java {@link Asyncc.AsyncTask}.
   *
   * <p>The returned task, when invoked by a combinator, attaches a {@code whenComplete} handler
   * to the stage that fires the combinator's per-task callback. Successful completion calls
   * {@code callback.success(value)}; exceptional completion calls {@code callback.fail(err)}.
   *
   * <p>The same stage instance can only be consumed by one task (a stage is a one-shot
   * primitive). If you need to consume the same logical work from multiple combinator
   * task positions, wrap each in its own {@link java.util.function.Supplier}.
   *
   * @param <V> value type produced by the stage
   * @param stage a one-shot promise-shaped value
   * @return an async.java task suitable for any combinator that accepts {@code AsyncTask<V, Throwable>}
   */
  public static <V> Asyncc.AsyncTask<V, Throwable> fromStage(
      final CompletionStage<V> stage) {

    Objects.requireNonNull(stage, "stage");

    return c -> stage.whenComplete((value, err) -> {
      if (err != null) {
        c.fail(err);
      } else {
        c.success(value);
      }
    });
  }

  /**
   * Adapt a lazy {@link CompletionStage} supplier to an async.java {@link Asyncc.AsyncTask}.
   *
   * <p>This is the supplier-shaped sibling of {@link #fromStage(CompletionStage)}. The supplier
   * is invoked only when the combinator starts this task, which preserves async.java scheduling
   * semantics for {@code Series}, {@code ParallelLimit}, {@code RaceLimit}, and other bounded or
   * sequential combinators.
   *
   * <pre>
   *   Asyncc.Series(List.of(
   *       WrapFuture.fromStage(() -&gt; client.validateAsync(req)),
   *       WrapFuture.fromStage(() -&gt; client.persistAsync(req))
   *   ), finalCallback);
   * </pre>
   *
   * <p>If the supplier throws before returning a stage, the task fails the callback with that
   * throwable. If it returns {@code null}, the task fails with {@link NullPointerException}.
   *
   * @param <V> value type produced by the stage
   * @param stageSupplier supplier invoked when the async.java task starts
   * @return an async.java task suitable for callback combinators
   * @since 0.2.10
   */
  public static <V> Asyncc.AsyncTask<V, Throwable> fromStage(
      final Supplier<? extends CompletionStage<V>> stageSupplier) {

    Objects.requireNonNull(stageSupplier, "stageSupplier");

    return c -> {
      final CompletionStage<V> stage;
      try {
        stage = Objects.requireNonNull(stageSupplier.get(), "stageSupplier.get()");
      } catch (Throwable t) {
        c.fail(t);
        return;
      }

      stage.whenComplete((value, err) -> {
        if (err != null) {
          c.fail(err);
        } else {
          c.success(value);
        }
      });
    };
  }

  /**
   * Adapt a plain JDK {@link Future} to a {@link CompletableFuture}.
   *
   * <p>{@code Future#get()} is blocking, so this method waits on the supplied {@code executor}
   * instead of blocking the caller. For large numbers of blocking futures, a virtual-thread
   * executor is a natural fit:
   *
   * <pre>
   *   try (var vt = AsyncLoom.newVirtualThreadPerTaskExecutor()) {
   *       CompletableFuture&lt;Response&gt; cf = WrapFuture.toCompletableFuture(vt, legacyFuture);
   *   }
   * </pre>
   *
   * <p>If the returned {@code CompletableFuture} is cancelled, cancellation is propagated to the
   * underlying {@code Future}. If {@code future.get()} throws {@link ExecutionException}, the
   * original cause is used for exceptional completion.
   *
   * @param <V> value type produced by the future
   * @param exec executor used for the blocking {@code Future#get()} wait
   * @param future legacy/plain JDK future to adapt
   * @return a {@code CompletableFuture} mirroring the supplied future
   * @since 0.2.10
   */
  public static <V> CompletableFuture<V> toCompletableFuture(
      final Executor exec,
      final Future<? extends V> future) {

    Objects.requireNonNull(exec, "exec");
    Objects.requireNonNull(future, "future");

    final CompletableFuture<V> cf = new CompletableFuture<>() {
      @Override
      public boolean cancel(final boolean mayInterruptIfRunning) {
        future.cancel(mayInterruptIfRunning);
        return super.cancel(mayInterruptIfRunning);
      }
    };

    try {
      exec.execute(() -> {
        try {
          cf.complete(future.get());
        } catch (CancellationException e) {
          cf.cancel(false);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          cf.completeExceptionally(e);
        } catch (ExecutionException e) {
          cf.completeExceptionally(e.getCause() == null ? e : e.getCause());
        } catch (Throwable t) {
          cf.completeExceptionally(t);
        }
      });
    } catch (Throwable t) {
      cf.completeExceptionally(t);
    }

    return cf;
  }

  /**
   * Dispatch a checked {@link Callable} onto an executor and expose its result as a
   * {@link CompletableFuture}. Unlike {@link CompletableFuture#supplyAsync}, checked exceptions
   * from {@code callable.call()} are preserved directly as the exceptional completion cause.
   *
   * @param <V> value type produced by the callable
   * @param exec executor to run the callable on
   * @param callable synchronous work to perform
   * @return a {@code CompletableFuture} for the callable result
   * @since 0.2.10
   */
  public static <V> CompletableFuture<V> toCompletableFuture(
      final Executor exec,
      final Callable<V> callable) {

    Objects.requireNonNull(exec, "exec");
    Objects.requireNonNull(callable, "callable");

    final CompletableFuture<V> cf = new CompletableFuture<>();

    try {
      exec.execute(() -> {
        try {
          cf.complete(callable.call());
        } catch (Throwable t) {
          cf.completeExceptionally(t);
        }
      });
    } catch (Throwable t) {
      cf.completeExceptionally(t);
    }

    return cf;
  }

  /**
   * Adapt a plain JDK {@link Future} to an async.java {@link Asyncc.AsyncTask}.
   *
   * <p>The blocking wait happens on {@code exec}. This makes the adapter safe to use at an
   * async.java boundary without pinning the caller thread; for highly concurrent blocking waits,
   * prefer a virtual-thread executor from {@link AsyncLoom#newVirtualThreadPerTaskExecutor()}.
   *
   * @param <V> value type produced by the future
   * @param exec executor used for the blocking {@code Future#get()} wait
   * @param future legacy/plain JDK future to adapt
   * @return an async.java task suitable for callback combinators
   * @since 0.2.10
   */
  public static <V> Asyncc.AsyncTask<V, Throwable> fromFuture(
      final Executor exec,
      final Future<? extends V> future) {

    return c -> toCompletableFuture(exec, future).whenComplete((value, err) -> {
      if (err != null) {
        c.fail(err);
      } else {
        c.success(value);
      }
    });
  }

  /**
   * Adapt a synchronous (possibly blocking) {@link Callable} to an async.java task by
   * dispatching it onto the provided executor.
   *
   * <p>Equivalent to {@code fromStage(CompletableFuture.supplyAsync(callable::call, exec))} but
   * preserves checked exceptions from the callable's {@code call()} method as the failure.
   *
   * @param <V> value type produced by the callable
   * @param exec executor to run the callable on
   * @param callable the synchronous work to perform
   * @return an async.java task
   */
  public static <V> Asyncc.AsyncTask<V, Throwable> fromCallable(
      final Executor exec,
      final Callable<V> callable) {

    return c -> toCompletableFuture(exec, callable).whenComplete((value, err) -> {
      if (err != null) {
        c.fail(err);
      } else {
        c.success(value);
      }
    });
  }

  /** Coerce an arbitrary error reference into a {@link Throwable}. Public for {@link AsyncFut}. */
  static Throwable toThrowable(final Object err) {
    if (err instanceof Throwable t) {
      return t;
    }
    return new RuntimeException("async.java error: " + err);
  }
}
