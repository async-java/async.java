package org.ores.async;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Project Loom helpers for async.java.
 *
 * <p>This class is compiled for Java 17 but uses reflection to call the Java 21 virtual-thread
 * APIs when they are present. That keeps the library usable on Java 17 while letting Java 21+
 * consumers run blocking work on virtual threads with no extra adapter code.
 *
 * <h3>Run blocking tasks on virtual threads</h3>
 *
 * <pre>
 *   CompletableFuture&lt;List&lt;Payload&gt;&gt; payloads = AsyncLoom.ParallelBlocking(List.of(
 *       () -&gt; jdbcCall(id1),
 *       () -&gt; jdbcCall(id2),
 *       () -&gt; blockingHttpCall(id3)
 *   ));
 * </pre>
 *
 * <h3>Bridge legacy Futures without calling get()</h3>
 *
 * <pre>
 *   ExecutorService vt = AsyncLoom.newVirtualThreadPerTaskExecutor();
 *   try {
 *       CompletableFuture&lt;List&lt;Payload&gt;&gt; payloads =
 *           AsyncFut.ParallelFutures(vt, legacyFutures);
 *   } finally {
 *       vt.shutdown();
 *   }
 * </pre>
 *
 * <p>{@code AsyncLoom} is intentionally small: it does not replace {@link Asyncc} or
 * {@link AsyncFut}. It supplies a cheap blocking executor so those combinators can orchestrate
 * work that still uses blocking APIs.
 *
 * @see AsyncFut
 * @see WrapFuture
 * @since 0.2.10
 */
public final class AsyncLoom {

  private static final Method NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR =
      findMethod(java.util.concurrent.Executors.class, "newVirtualThreadPerTaskExecutor");

  private static final Method THREAD_IS_VIRTUAL =
      findMethod(Thread.class, "isVirtual");

  private AsyncLoom() {}

  /**
   * Return {@code true} when this JVM exposes the Java 21 virtual-thread executor API.
   *
   * <p>On Java 17 this returns {@code false}. Calling methods that require virtual threads on
   * such a JVM throws {@link UnsupportedOperationException}.
   */
  public static boolean isSupported() {
    return NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR != null;
  }

  /**
   * Return whether {@link Thread#currentThread()} is virtual. On Java 17 this returns
   * {@code false}.
   */
  public static boolean isVirtualThread() {
    return isVirtualThread(Thread.currentThread());
  }

  /**
   * Return whether {@code thread} is virtual. On Java 17 this returns {@code false}.
   *
   * @param thread thread to inspect
   */
  public static boolean isVirtualThread(final Thread thread) {
    if (THREAD_IS_VIRTUAL == null) {
      return false;
    }
    try {
      return Boolean.TRUE.equals(THREAD_IS_VIRTUAL.invoke(thread));
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw asRuntime(e);
    }
  }

  /**
   * Create a Java 21+ {@code Executors.newVirtualThreadPerTaskExecutor()} reflectively.
   *
   * <p>The caller owns the returned executor and should call {@link ExecutorService#shutdown()}
   * when done. On Java 17 this throws {@link UnsupportedOperationException}.
   *
   * @return a new virtual-thread-per-task executor
   */
  public static ExecutorService newVirtualThreadPerTaskExecutor() {
    if (NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR == null) {
      throw new UnsupportedOperationException(
          "Virtual threads require JDK 21+; this runtime does not expose "
              + "Executors.newVirtualThreadPerTaskExecutor().");
    }
    try {
      return (ExecutorService) NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR.invoke(null);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw asRuntime(e);
    }
  }

  /**
   * Install a virtual-thread executor for {@link NeoQueue} continuations.
   *
   * <p>The returned executor is now owned by {@code NeoQueue}; keep a reference if your process
   * needs to shut it down explicitly. A later call to {@link NeoQueue#setExecutor(ExecutorService)}
   * replaces it.
   *
   * @return the executor installed into {@code NeoQueue}
   */
  public static ExecutorService installNeoQueueExecutor() {
    final ExecutorService exec = newVirtualThreadPerTaskExecutor();
    NeoQueue.setExecutor(exec);
    return exec;
  }

  /**
   * Run one blocking {@link Callable} on a virtual thread and return its result as a
   * {@link CompletableFuture}.
   *
   * <p>The temporary virtual-thread executor is shut down when the callable completes.
   *
   * @param <V> callable result type
   * @param callable blocking work to run on a virtual thread
   * @return a future completed with the callable result
   */
  public static <V> CompletableFuture<V> supply(final Callable<V> callable) {
    final ExecutorService exec = newVirtualThreadPerTaskExecutor();
    return WrapFuture.toCompletableFuture(exec, callable)
        .whenComplete((value, err) -> exec.shutdown());
  }

  /**
   * Run one blocking {@link ThrowingRunnable} on a virtual thread.
   *
   * @param runnable blocking work to run on a virtual thread
   * @return a future completed when the runnable finishes
   */
  public static CompletableFuture<Void> run(final ThrowingRunnable runnable) {
    return supply(() -> {
      runnable.run();
      return null;
    });
  }

  /**
   * Wrap a blocking callable as an async.java task. Each task invocation uses one virtual
   * thread and shuts down its temporary executor after completion.
   *
   * @param <V> callable result type
   * @param callable blocking work to run
   * @return async.java task
   */
  public static <V> Asyncc.AsyncTask<V, Throwable> task(final Callable<V> callable) {
    return c -> supply(callable).whenComplete((value, err) -> {
      if (err != null) {
        c.fail(err);
      } else {
        c.success(value);
      }
    });
  }

  /**
   * Run blocking callables concurrently on virtual threads and collect results in input order.
   *
   * @param <V> callable result type
   * @param tasks blocking tasks to run
   * @return a future of all task results
   */
  public static <V> CompletableFuture<List<V>> ParallelBlocking(
      final List<? extends Callable<V>> tasks) {

    final ExecutorService exec = newVirtualThreadPerTaskExecutor();
    return AsyncFut.Parallel(toStageSuppliers(exec, tasks))
        .whenComplete((value, err) -> exec.shutdown());
  }

  /**
   * Run blocking callables on virtual threads with at most {@code limit} tasks in flight.
   *
   * @param <V> callable result type
   * @param limit max in-flight tasks
   * @param tasks blocking tasks to run
   * @return a future of all task results in input order
   */
  public static <V> CompletableFuture<List<V>> ParallelBlocking(
      final int limit,
      final List<? extends Callable<V>> tasks) {

    final ExecutorService exec = newVirtualThreadPerTaskExecutor();
    return AsyncFut.ParallelLimit(limit, toStageSuppliers(exec, tasks))
        .whenComplete((value, err) -> exec.shutdown());
  }

  /**
   * Named alias for {@link #ParallelBlocking(int, List)}.
   *
   * @param <V> callable result type
   * @param limit max in-flight tasks
   * @param tasks blocking tasks to run
   * @return a future of all task results in input order
   */
  public static <V> CompletableFuture<List<V>> ParallelLimitBlocking(
      final int limit,
      final List<? extends Callable<V>> tasks) {

    return ParallelBlocking(limit, tasks);
  }

  /**
   * Run blocking callables sequentially on virtual threads and collect each result.
   *
   * @param <V> callable result type
   * @param tasks blocking tasks to run
   * @return a future of all task results in input order
   */
  public static <V> CompletableFuture<List<V>> SeriesBlocking(
      final List<? extends Callable<V>> tasks) {

    final ExecutorService exec = newVirtualThreadPerTaskExecutor();
    return AsyncFut.Series(toStageSuppliers(exec, tasks))
        .whenComplete((value, err) -> exec.shutdown());
  }

  /**
   * Run blocking callables concurrently on virtual threads and complete with the first result.
   *
   * @param <V> callable result type
   * @param tasks blocking tasks to race
   * @return a future completed with the first task result
   */
  public static <V> CompletableFuture<V> RaceBlocking(
      final List<? extends Callable<V>> tasks) {

    final ExecutorService exec = newVirtualThreadPerTaskExecutor();
    return AsyncFut.Race(toStageSuppliers(exec, tasks))
        .whenComplete((value, err) -> exec.shutdown());
  }

  /**
   * Map each input value through a blocking function on virtual threads. Results preserve input
   * order.
   *
   * @param <T> input value type
   * @param <V> mapped value type
   * @param input values to map
   * @param mapper blocking mapper to run on virtual threads
   * @return a future of mapped results
   */
  public static <T, V> CompletableFuture<List<V>> MapBlocking(
      final Iterable<T> input,
      final BlockingFunction<? super T, V> mapper) {

    return MapLimitBlocking(Integer.MAX_VALUE, input, mapper);
  }

  /**
   * Map each input value through a blocking function on virtual threads with at most
   * {@code limit} mapper calls in flight.
   *
   * @param <T> input value type
   * @param <V> mapped value type
   * @param limit max in-flight mapper calls
   * @param input values to map
   * @param mapper blocking mapper to run on virtual threads
   * @return a future of mapped results
   */
  public static <T, V> CompletableFuture<List<V>> MapLimitBlocking(
      final int limit,
      final Iterable<T> input,
      final BlockingFunction<? super T, V> mapper) {

    final ExecutorService exec = newVirtualThreadPerTaskExecutor();
    final List<Supplier<? extends CompletionStage<V>>> suppliers = new ArrayList<>();
    final Iterator<T> it = input.iterator();
    while (it.hasNext()) {
      final T item = it.next();
      suppliers.add(() -> WrapFuture.toCompletableFuture(exec, () -> mapper.apply(item)));
    }

    return AsyncFut.ParallelLimit(limit, suppliers)
        .whenComplete((value, err) -> exec.shutdown());
  }

  /**
   * Run a blocking async-style each function on virtual threads. The result future completes
   * with {@code null} when all items have been processed.
   *
   * @param <T> input value type
   * @param input values to process
   * @param consumer blocking per-item function
   * @return completion future
   */
  public static <T> CompletableFuture<Void> EachBlocking(
      final Iterable<T> input,
      final BlockingConsumer<? super T> consumer) {

    return EachLimitBlocking(Integer.MAX_VALUE, input, consumer);
  }

  /**
   * Run a blocking async-style each function on virtual threads with at most {@code limit}
   * calls in flight.
   *
   * @param <T> input value type
   * @param limit max in-flight calls
   * @param input values to process
   * @param consumer blocking per-item function
   * @return completion future
   */
  public static <T> CompletableFuture<Void> EachLimitBlocking(
      final int limit,
      final Iterable<T> input,
      final BlockingConsumer<? super T> consumer) {

    return MapLimitBlocking(limit, input, item -> {
      consumer.accept(item);
      return null;
    }).thenApply(ignored -> null);
  }

  /**
   * Sequentially reduce input values with a blocking reducer. Each reducer call runs on a
   * virtual thread, and the next reducer call starts only after the prior one completes.
   *
   * @param <T> input value type
   * @param <V> accumulator/result type
   * @param input values to reduce
   * @param identity initial accumulator value
   * @param reducer blocking reducer
   * @return final accumulator future
   */
  public static <T, V> CompletableFuture<V> ReduceBlocking(
      final Iterable<T> input,
      final V identity,
      final BlockingReducer<V, ? super T> reducer) {

    final ExecutorService exec = newVirtualThreadPerTaskExecutor();
    return AsyncFut.Reduce(input, identity,
        (acc, item) -> WrapFuture.toCompletableFuture(exec, () -> reducer.reduce(acc, item)))
        .whenComplete((value, err) -> exec.shutdown());
  }

  /**
   * Run a blocking function {@code n} times on virtual threads and collect the results in index
   * order.
   *
   * @param <V> result type
   * @param n number of calls
   * @param task blocking index-aware function
   * @return ordered result future
   */
  public static <V> CompletableFuture<List<V>> TimesBlocking(
      final int n,
      final BlockingIntFunction<V> task) {

    final List<Callable<V>> tasks = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      final int index = i;
      tasks.add(() -> task.apply(index));
    }
    return ParallelBlocking(tasks);
  }

  /**
   * Map each item to a collection on virtual threads, then concatenate one level while
   * preserving input order.
   *
   * @param <T> input value type
   * @param <V> flattened output value type
   * @param input values to map
   * @param mapper blocking mapper producing zero or more output values
   * @return flattened result future
   */
  public static <T, V> CompletableFuture<List<V>> ConcatBlocking(
      final Iterable<T> input,
      final BlockingFunction<? super T, ? extends Collection<? extends V>> mapper) {

    return ConcatLimitBlocking(Integer.MAX_VALUE, input, mapper);
  }

  /**
   * Sequential version of {@link #ConcatBlocking(Iterable, BlockingFunction)}.
   *
   * @param <T> input value type
   * @param <V> flattened output value type
   * @param input values to map
   * @param mapper blocking mapper producing zero or more output values
   * @return flattened result future
   */
  public static <T, V> CompletableFuture<List<V>> ConcatSeriesBlocking(
      final Iterable<T> input,
      final BlockingFunction<? super T, ? extends Collection<? extends V>> mapper) {

    return ConcatLimitBlocking(1, input, mapper);
  }

  /**
   * Bounded-concurrency version of {@link #ConcatBlocking(Iterable, BlockingFunction)}.
   *
   * @param <T> input value type
   * @param <V> flattened output value type
   * @param limit max in-flight mapper calls
   * @param input values to map
   * @param mapper blocking mapper producing zero or more output values
   * @return flattened result future
   */
  public static <T, V> CompletableFuture<List<V>> ConcatLimitBlocking(
      final int limit,
      final Iterable<T> input,
      final BlockingFunction<? super T, ? extends Collection<? extends V>> mapper) {

    final ExecutorService exec = newVirtualThreadPerTaskExecutor();
    final List<Supplier<? extends CompletionStage<Collection<? extends V>>>> suppliers =
        new ArrayList<>();
    final Iterator<T> it = input.iterator();
    while (it.hasNext()) {
      final T item = it.next();
      suppliers.add(() -> WrapFuture.toCompletableFuture(exec, () -> mapper.apply(item)));
    }

    return AsyncFut.ParallelLimit(limit, suppliers)
        .thenApply(AsyncLoom::flattenOne)
        .whenComplete((value, err) -> exec.shutdown());
  }

  /**
   * Functional interface for {@link #run(ThrowingRunnable)}.
   */
  @FunctionalInterface
  public interface ThrowingRunnable {
    void run() throws Exception;
  }

  /**
   * Blocking function used by virtual-thread collection helpers.
   */
  @FunctionalInterface
  public interface BlockingFunction<T, V> {
    V apply(T value) throws Exception;
  }

  /**
   * Blocking reducer used by {@link #ReduceBlocking(Iterable, Object, BlockingReducer)}.
   */
  @FunctionalInterface
  public interface BlockingReducer<V, T> {
    V reduce(V accumulator, T value) throws Exception;
  }

  /**
   * Blocking consumer used by {@link #EachBlocking(Iterable, BlockingConsumer)}.
   */
  @FunctionalInterface
  public interface BlockingConsumer<T> {
    void accept(T value) throws Exception;
  }

  /**
   * Blocking index-aware function used by {@link #TimesBlocking(int, BlockingIntFunction)}.
   */
  @FunctionalInterface
  public interface BlockingIntFunction<V> {
    V apply(int index) throws Exception;
  }

  private static <V> List<Supplier<? extends CompletionStage<V>>> toStageSuppliers(
      final ExecutorService exec,
      final List<? extends Callable<V>> tasks) {

    final List<Supplier<? extends CompletionStage<V>>> suppliers = new ArrayList<>(tasks.size());
    for (final Callable<V> task : tasks) {
      suppliers.add(() -> WrapFuture.toCompletableFuture(exec, task));
    }
    return suppliers;
  }

  private static <V> List<V> flattenOne(
      final List<? extends Collection<? extends V>> chunks) {

    final List<V> out = new ArrayList<>();
    for (final Collection<? extends V> chunk : chunks) {
      if (chunk != null) {
        out.addAll(chunk);
      }
    }
    return out;
  }

  private static Method findMethod(final Class<?> type, final String name) {
    try {
      return type.getMethod(name);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private static RuntimeException asRuntime(final ReflectiveOperationException e) {
    if (e instanceof InvocationTargetException ite && ite.getCause() != null) {
      final Throwable cause = ite.getCause();
      if (cause instanceof RuntimeException re) {
        return re;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      return new RuntimeException(cause);
    }
    return new RuntimeException(e);
  }
}
