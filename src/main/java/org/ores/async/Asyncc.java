package org.ores.async;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Entry point for async.java's combinators.
 *
 * <p>All public combinator methods are static. They share a common shape: take a collection of
 * tasks (or values) and an error-first final callback. The final callback fires <strong>at most
 * once</strong>, with either {@code (err, null)} if any task failed or {@code (null, results)}
 * once all tasks have completed.
 *
 * <h3>The combinator catalogue</h3>
 *
 * <table>
 *   <caption>Combinators and their shape</caption>
 *   <tr><th>combinator</th><th>shape</th><th>typical use</th></tr>
 *   <tr><td>{@code Parallel}</td>
 *       <td>fan out N tasks, await all</td>
 *       <td>independent async lookups that combine into one response</td></tr>
 *   <tr><td>{@code ParallelLimit}</td>
 *       <td>parallel with concurrency cap</td>
 *       <td>many tasks, bounded in-flight (avoid downstream overload)</td></tr>
 *   <tr><td>{@code Series}</td>
 *       <td>sequential tasks, collect each result</td>
 *       <td>step-by-step workflows where each step is independent</td></tr>
 *   <tr><td>{@code Waterfall}</td>
 *       <td>each task receives the prior task's value</td>
 *       <td>typed pipelines (parse &rarr; validate &rarr; transform &rarr; persist)</td></tr>
 *   <tr><td>{@code Race}</td>
 *       <td>first task to finish wins</td>
 *       <td>primary + replica lookups, timeout-via-task patterns</td></tr>
 *   <tr><td>{@code Times}</td>
 *       <td>run the same task N times</td>
 *       <td>batch generators, repeat-with-different-id workflows</td></tr>
 *   <tr><td>{@code Each}</td>
 *       <td>parallel "for each" with concurrency cap; no collected result</td>
 *       <td>fire-and-forget batches (e.g. send email per user)</td></tr>
 *   <tr><td>Map / FilterMap / Reduce / GroupBy / Concat / Inject</td>
 *       <td>collection transforms with async element functions</td>
 *       <td>data pipelines where each element does I/O</td></tr>
 *   <tr><td>Whilst / DoWhilst</td>
 *       <td>async loop with sync test</td>
 *       <td>polling, retry-until-success</td></tr>
 * </table>
 *
 * <h3>The {@code c} convention</h3>
 *
 * <p>Examples in this Javadoc consistently name the continuation parameter {@code c}, short for
 * <em>continuation</em>. A continuation is fired with either a successful value or an error;
 * three equivalent ways to do that:
 *
 * <pre>
 *   c.done(null, value);   // canonical error-first form
 *   c.success(value);      // shorthand for done(null, value)     — v0.2.4+
 *   c.fail(err);           // shorthand for done(err, null)       — v0.2.4+
 * </pre>
 *
 * <h3>Minimal example</h3>
 *
 * <pre>
 *   List&lt;Asyncc.AsyncTask&lt;String, Throwable&gt;&gt; tasks = List.of(
 *       c -&gt; exec.submit(() -&gt; c.success(fetchA())),
 *       c -&gt; exec.submit(() -&gt; c.success(fetchB()))
 *   );
 *
 *   Asyncc.Parallel(tasks, (err, results) -&gt; {
 *       if (err != null) { handleError(err); return; }
 *       // results.get(0) and results.get(1) are in submission order
 *   });
 * </pre>
 *
 * <h3>Skipping the error-check boilerplate</h3>
 *
 * <p>For call sites that do not want to write the {@code if (err != null) ...} preamble, wrap a
 * value-only consumer with {@link WrapErrFirst#wrap(java.util.function.Consumer)}:
 *
 * <pre>
 *   import static org.ores.async.WrapErrFirst.wrap;
 *
 *   Asyncc.Parallel(tasks, wrap(results -&gt; {
 *       var scored = score(req, results.get(0), results.get(1));
 *       reply.send(serialize(scored));
 *   }));
 * </pre>
 *
 * <p>{@code wrap(onSuccess)} throws on any unhandled error (preserving the original
 * {@code Throwable} as the cause when available). For explicit error handling without the
 * if/else preamble, use the two-arg form {@code wrap(onSuccess, onError)}.
 *
 * <h3>Error handling</h3>
 *
 * <p>The first task to call {@code c.fail(err)} (or {@code c.done(err, null)}) short-circuits
 * the combinator: the final callback fires <em>immediately</em> with that error, and any later
 * task completions are discarded (the at-most-once guard absorbs duplicate fires). This makes
 * error handling local: you do not need to coordinate cancellation across siblings &mdash; just
 * report the error.
 *
 * <pre>
 *   Asyncc.Parallel(tasks, (err, results) -&gt; {
 *       if (err instanceof TimeoutException) { reply.timeout(); return; }
 *       if (err != null)                     { reply.error(err);  return; }
 *       reply.success(results);
 *   });
 * </pre>
 *
 * <h3>Composition (real-world example)</h3>
 *
 * <p>Combinators nest because they all honour the same callback shape. The pipeline below uses
 * {@code Waterfall}, {@code Map}, {@code Parallel}, and {@code Reduce} together:
 *
 * <pre>
 *   // For each input URL: fetch the page, extract links, classify each link in parallel,
 *   // then aggregate per-domain counts.
 *   Asyncc.Map(urls, (url, perUrl) -&gt; {
 *       Asyncc.Waterfall(List.of(
 *           c -&gt; fetchPage(url, c),
 *           (html, c) -&gt; c.success(extractLinks(html)),
 *           (links, c) -&gt; Asyncc.Map(links, (link, inner) -&gt; {
 *               Asyncc.Parallel(List.of(
 *                   c2 -&gt; exec.submit(() -&gt; c2.success(headOk(link))),
 *                   c2 -&gt; exec.submit(() -&gt; c2.success(classify(link)))
 *               ), inner);
 *           }, c)
 *       ), perUrl);
 *   }, (err, perUrlResults) -&gt; {
 *       Asyncc.Reduce(perUrlResults, new HashMap&lt;String, Integer&gt;(), (acc, list, c) -&gt; {
 *           list.forEach(pair -&gt; acc.merge(pair.get(1).toString(), 1, Integer::sum));
 *           c.success(acc);
 *       }, (err2, totals) -&gt; reply.send(totals));
 *   });
 * </pre>
 *
 * <h3>Concurrency contract (v0.2.x)</h3>
 *
 * <ul>
 *   <li><strong>At-most-once final callback</strong>, even under concurrent task completion.</li>
 *   <li><strong>Result-slot visibility</strong>: per-index writes happen-before the atomic counter
 *       increment that releases the final callback (v0.2.2 fix).</li>
 *   <li><strong>Lost-update-free counters</strong>: {@link CounterLimit} uses {@code AtomicInteger}
 *       (v0.2.0 fix; pinned by {@code CounterLimitRaceTest}).</li>
 *   <li><strong>No duplicate fires</strong>: routed through {@link NeoUtils#fireFinalCallback}.</li>
 * </ul>
 *
 * <p>See <a href="https://async-java.github.io/blog/">async-java.github.io/blog</a> for benchmark
 * results and design notes.
 *
 * @see NeoQueue
 * @see NeoLock
 */
public class Asyncc {
  
  public final static Object sync = new Object();


  public enum Overloader {
    GENERIC
  }

  /**
   * Throwable-fixed shorthand for {@link AsyncTask AsyncTask}{@code <T, Throwable>}.
   *
   * <p>Java doesn't support default generic parameters, but the error type in this library is
   * almost always {@code Throwable}. This shorthand lets you drop the redundant
   * {@code , Throwable} from explicitly-typed task references:
   *
   * <pre>
   *   // before:
   *   final var classifyTasks = items.stream()
   *       .&lt;Asyncc.AsyncTask&lt;Result, Throwable&gt;&gt;map(it -&gt; c -&gt; { ... })
   *       .toList();
   *
   *   // after (v0.2.8+):
   *   final var classifyTasks = items.stream()
   *       .&lt;Asyncc.Task&lt;Result&gt;&gt;map(it -&gt; c -&gt; { ... })
   *       .toList();
   * </pre>
   *
   * <p>{@code Task<T>} is a strict subtype of {@code AsyncTask<T, Throwable>}, so anywhere the
   * latter is expected, the former is accepted &mdash; including the {@code List}-taking
   * {@link #Parallel(List, IAsyncCallback) Parallel},
   * {@link #ParallelLimit(int, List, IAsyncCallback) ParallelLimit}, and
   * {@link #Series(List, IAsyncCallback) Series} combinators, whose List parameters were
   * widened to {@code List<? extends AsyncTask<T, E>>} in v0.2.8 to make
   * {@code List<Task<T>>} flow in cleanly.
   *
   * @since 0.2.8
   */
  public interface Task<T> extends AsyncTask<T, Throwable> {
  }

  /**
   * Throwable-fixed shorthand for {@link IAsyncCallback IAsyncCallback}{@code <T, Throwable>}.
   * Same rationale as {@link Task}: drop the redundant {@code , Throwable} from explicitly-typed
   * callback references.
   *
   * @since 0.2.8
   */
  public interface Callback<T> extends IAsyncCallback<T, Throwable> {
  }

  public interface IAcceptRunnable {
    void accept(Runnable r);
  }
  
  static IAcceptRunnable nextTick = null;
  
  public static IAcceptRunnable setOnNext(IAcceptRunnable r) {
    return Asyncc.nextTick = r;
  }
  
  public static class KeyValue<K, V> {
    
    public K key;
    public V value;
    
    public KeyValue(K key, V value) {
      this.key = key;
      this.value = value;
    }
    
    public KeyValue(Map.Entry<K, V> m) {
      this.key = m.getKey();
      this.value = m.getValue();
    }
  }
  
  public interface IMapper<T, V, E> {
    void map(T v, IAsyncCallback<V, E> cb);
  }
  

  
  interface IErrorOnlyDoneable<T, E> {
    void done(E e, T v);
  }
  
  interface IErrorValueDoneable<T, E> {
    void done(final E e, final T v);
  }
  
  /**
   * Error-first callback (Node.js-style). The combinators' final callback and per-task callback
   * both use this interface.
   *
   * <p>The conventional parameter name in user code is {@code c}, short for <em>continuation</em>.
   * The continuation receives the result of an async step and is responsible for "what happens
   * next" &mdash; either the final callback of the combinator, or the next inner step.
   *
   * <p>Three ways to fire the continuation:
   *
   * <pre>
   *   c.done(null, value);   // legacy / explicit error-first form (still works)
   *   c.success(value);      // shorthand for done(null, value) — added in v0.2.4
   *   c.fail(err);           // shorthand for done(err, null)   — added in v0.2.4
   * </pre>
   *
   * <p>For the common case where the caller does not want to handle the error inline, use
   * {@link WrapErrFirst#wrap(java.util.function.Consumer)} to wrap a value-only consumer into
   * an error-first callback that throws on any non-null error.
   *
   * @param <T> result value type
   * @param <E> error type (typically {@code Throwable}, but can be any reference type)
   */
  public interface IAsyncCallback<T, E> {
    boolean isDone = false;

    /**
     * Fire the continuation with either an error or a value (never both).
     *
     * @param e the error, or {@code null} for success
     * @param v the value, or {@code null} when {@code e} is non-null
     */
    void done(final E e, final T v);

    /**
     * Fire the continuation with a successful value. Equivalent to {@code done(null, v)}.
     *
     * <p>Added in v0.2.4.
     */
    default void success(final T v) {
      done(null, v);
    }

    /**
     * Fire the continuation with an error. Equivalent to {@code done(e, null)}.
     *
     * <p>Added in v0.2.4.
     */
    default void fail(final E e) {
      done(e, null);
    }

    default void setDone() {

    }
  }
  
  public static interface ICallbacks<T, E> {
    void resolve(final T v);
    
    void reject(final E e);
  }
  
  public static abstract class AsyncCallback<T, E> implements IAsyncCallback<T, E>, ICallbacks<T, E> {
    
    protected final ShortCircuit s;
    protected boolean isFinished = false;
    protected final Object cbLock = new Object();
    
    public AsyncCallback(ShortCircuit s) {
      this.s = s;
    }
    
    @Override
    public void resolve(final T v) {
      this.done(null, v);
    }
    
    @Override
    public void reject(final E e) {
      this.done(e, null);
    }
    
    public boolean isShortCircuited() {
      return this.s.isShortCircuited();
    }
    
    boolean isFinished() {
      return this.isFinished;
    }
    
    boolean setFinished(boolean b) {
      return this.isFinished = b;
    }
    
  }
  
  public interface AsyncValueTask<T, E> {
    //    public void run(AsyncCallback<T, E> cb);
    void run(final Object v, final IAsyncCallback<T, E> cb);
  }
  
  public interface AsyncTask<T, E> {
    //    public void run(AsyncCallback<T, E> cb);
    void run(IAsyncCallback<T, E> cb);
  }

  /**
   * Run blocking {@link Callable} tasks concurrently on Java 21+ virtual threads and report
   * the ordered results through the normal error-first callback.
   *
   * <p>This is a callback-style wrapper around {@link AsyncLoom#ParallelBlocking(List)}. On
   * Java 17, where virtual threads are unavailable, the callback receives an
   * {@link UnsupportedOperationException}.
   *
   * @param <T> result type
   * @param tasks blocking tasks to run
   * @param f final callback
   * @since 0.2.10
   */
  public static <T> void ParallelBlocking(
      final List<? extends Callable<T>> tasks,
      final IAsyncCallback<List<T>, Throwable> f) {

    pipeLoomFuture(() -> AsyncLoom.ParallelBlocking(tasks), f);
  }

  /**
   * Run blocking {@link Callable} tasks on Java 21+ virtual threads with at most {@code limit}
   * tasks in flight.
   *
   * @param <T> result type
   * @param limit max in-flight tasks
   * @param tasks blocking tasks to run
   * @param f final callback
   * @since 0.2.10
   */
  public static <T> void ParallelLimitBlocking(
      final int limit,
      final List<? extends Callable<T>> tasks,
      final IAsyncCallback<List<T>, Throwable> f) {

    pipeLoomFuture(() -> AsyncLoom.ParallelBlocking(limit, tasks), f);
  }

  /**
   * Run blocking {@link Callable} tasks sequentially on Java 21+ virtual threads.
   *
   * <p>Each step starts only after the previous step has completed, preserving the normal
   * {@code Series} contract. The blocking work itself runs on a virtual thread so callers do
   * not have to call {@code get()}, {@code join()}, or blocking I/O directly on their current
   * thread.
   *
   * @param <T> result type
   * @param tasks blocking tasks to run in order
   * @param f final callback
   * @since 0.2.10
   */
  public static <T> void SeriesBlocking(
      final List<? extends Callable<T>> tasks,
      final IAsyncCallback<List<T>, Throwable> f) {

    pipeLoomFuture(() -> AsyncLoom.SeriesBlocking(tasks), f);
  }

  /**
   * Race blocking {@link Callable} tasks on Java 21+ virtual threads and report the first
   * result.
   *
   * @param <T> result type
   * @param tasks blocking tasks to race
   * @param f final callback
   * @since 0.2.10
   */
  public static <T> void RaceBlocking(
      final List<? extends Callable<T>> tasks,
      final IAsyncCallback<T, Throwable> f) {

    pipeLoomFuture(() -> AsyncLoom.RaceBlocking(tasks), f);
  }

  /**
   * Map values with a blocking mapper on Java 21+ virtual threads.
   *
   * @param <T> input value type
   * @param <V> mapped value type
   * @param input values to map
   * @param mapper blocking mapper
   * @param f final callback
   * @since 0.2.10
   */
  public static <T, V> void MapBlocking(
      final Iterable<T> input,
      final AsyncLoom.BlockingFunction<? super T, V> mapper,
      final IAsyncCallback<List<V>, Throwable> f) {

    pipeLoomFuture(() -> AsyncLoom.MapBlocking(input, mapper), f);
  }

  /**
   * Map values with a blocking mapper on Java 21+ virtual threads with at most {@code limit}
   * mapper calls in flight.
   *
   * @param <T> input value type
   * @param <V> mapped value type
   * @param limit max in-flight mapper calls
   * @param input values to map
   * @param mapper blocking mapper
   * @param f final callback
   * @since 0.2.10
   */
  public static <T, V> void MapLimitBlocking(
      final int limit,
      final Iterable<T> input,
      final AsyncLoom.BlockingFunction<? super T, V> mapper,
      final IAsyncCallback<List<V>, Throwable> f) {

    pipeLoomFuture(() -> AsyncLoom.MapLimitBlocking(limit, input, mapper), f);
  }

  /**
   * Process each value with a blocking consumer on Java 21+ virtual threads.
   *
   * @param <T> input value type
   * @param input values to process
   * @param consumer blocking consumer
   * @param f final error-only callback
   * @since 0.2.10
   */
  public static <T> void EachBlocking(
      final Iterable<T> input,
      final AsyncLoom.BlockingConsumer<? super T> consumer,
      final NeoEachI.IEachCallback<Throwable> f) {

    pipeLoomEach(() -> AsyncLoom.EachBlocking(input, consumer), f);
  }

  /**
   * Process each value with a blocking consumer on Java 21+ virtual threads with at most
   * {@code limit} calls in flight.
   *
   * @param <T> input value type
   * @param limit max in-flight calls
   * @param input values to process
   * @param consumer blocking consumer
   * @param f final error-only callback
   * @since 0.2.10
   */
  public static <T> void EachLimitBlocking(
      final int limit,
      final Iterable<T> input,
      final AsyncLoom.BlockingConsumer<? super T> consumer,
      final NeoEachI.IEachCallback<Throwable> f) {

    pipeLoomEach(() -> AsyncLoom.EachLimitBlocking(limit, input, consumer), f);
  }

  /**
   * Reduce values sequentially with a blocking reducer on Java 21+ virtual threads.
   *
   * @param <T> input value type
   * @param <V> accumulator/result type
   * @param initialVal initial accumulator
   * @param input values to reduce
   * @param reducer blocking reducer
   * @param f final callback
   * @since 0.2.10
   */
  public static <T, V> void ReduceBlocking(
      final V initialVal,
      final Iterable<T> input,
      final AsyncLoom.BlockingReducer<V, ? super T> reducer,
      final IAsyncCallback<V, Throwable> f) {

    pipeLoomFuture(() -> AsyncLoom.ReduceBlocking(input, initialVal, reducer), f);
  }

  /**
   * Run a blocking index-aware function {@code count} times on Java 21+ virtual threads.
   *
   * @param <T> result type
   * @param count number of calls
   * @param task blocking index-aware task
   * @param f final callback
   * @since 0.2.10
   */
  public static <T> void TimesBlocking(
      final int count,
      final AsyncLoom.BlockingIntFunction<T> task,
      final IAsyncCallback<List<T>, Throwable> f) {

    pipeLoomFuture(() -> AsyncLoom.TimesBlocking(count, task), f);
  }

  /**
   * Map each value to a collection on Java 21+ virtual threads, then concatenate one level.
   *
   * @param <T> input value type
   * @param <V> flattened output value type
   * @param input values to map
   * @param mapper blocking mapper producing zero or more values
   * @param f final callback
   * @since 0.2.10
   */
  public static <T, V> void ConcatBlocking(
      final Iterable<T> input,
      final AsyncLoom.BlockingFunction<? super T, ? extends Collection<? extends V>> mapper,
      final IAsyncCallback<List<V>, Throwable> f) {

    pipeLoomFuture(() -> AsyncLoom.ConcatBlocking(input, mapper), f);
  }

  /**
   * Sequential version of {@link #ConcatBlocking(Iterable, AsyncLoom.BlockingFunction, IAsyncCallback)}.
   *
   * @param <T> input value type
   * @param <V> flattened output value type
   * @param input values to map
   * @param mapper blocking mapper producing zero or more values
   * @param f final callback
   * @since 0.2.10
   */
  public static <T, V> void ConcatSeriesBlocking(
      final Iterable<T> input,
      final AsyncLoom.BlockingFunction<? super T, ? extends Collection<? extends V>> mapper,
      final IAsyncCallback<List<V>, Throwable> f) {

    pipeLoomFuture(() -> AsyncLoom.ConcatSeriesBlocking(input, mapper), f);
  }

  /**
   * Bounded-concurrency version of
   * {@link #ConcatBlocking(Iterable, AsyncLoom.BlockingFunction, IAsyncCallback)}.
   *
   * @param <T> input value type
   * @param <V> flattened output value type
   * @param limit max in-flight mapper calls
   * @param input values to map
   * @param mapper blocking mapper producing zero or more values
   * @param f final callback
   * @since 0.2.10
   */
  public static <T, V> void ConcatLimitBlocking(
      final int limit,
      final Iterable<T> input,
      final AsyncLoom.BlockingFunction<? super T, ? extends Collection<? extends V>> mapper,
      final IAsyncCallback<List<V>, Throwable> f) {

    pipeLoomFuture(() -> AsyncLoom.ConcatLimitBlocking(limit, input, mapper), f);
  }

  private interface FutSupplier<T> {
    CompletableFuture<T> get();
  }

  private static <T> void pipeLoomFuture(
      final FutSupplier<T> supplier,
      final IAsyncCallback<T, Throwable> f) {

    try {
      supplier.get().whenComplete((value, err) -> {
        if (err != null) {
          f.fail(unwrapCompletion(err));
        } else {
          f.success(value);
        }
      });
    } catch (Throwable t) {
      f.fail(unwrapCompletion(t));
    }
  }

  private static void pipeLoomEach(
      final FutSupplier<Void> supplier,
      final NeoEachI.IEachCallback<Throwable> f) {

    try {
      supplier.get().whenComplete((value, err) -> {
        if (err != null) {
          f.done(unwrapCompletion(err));
        } else {
          f.done(null);
        }
      });
    } catch (Throwable t) {
      f.done(unwrapCompletion(t));
    }
  }

  private static Throwable unwrapCompletion(final Throwable err) {
    if (err instanceof CompletionException && err.getCause() != null) {
      return err.getCause();
    }
    return err;
  }
  
  // begin parallel
  
  public static <T, V, E> NeoGeneric<T, V, E> Parallel(NeoParallelI.AsyncValueTask<T, E> o, AsyncTask<T, E>... tasks) {
    return new NeoGeneric<>() {
      
      @Override
      void handle(Object v, IAsyncCallback cb) {
        
        final var task = new AsyncTask<>() {
          @Override
          public void run(IAsyncCallback<Object, Object> cb) {
            o.run(v, cb::done);
          }
        };
        
        final var x = new ArrayList<AsyncTask<T, E>>();
        x.add((AsyncTask) task);
        x.addAll(Arrays.asList(tasks));
        NeoParallel.Parallel(x, cb);
      }
    };
  }
  
  public static <T, E> AsyncTask<List<T>, E> Parallel(AsyncTask<T, E> t, AsyncTask<T, E>... tasks) {
    ////
    final var listOfTasks = new ArrayList<>(Arrays.asList(tasks));
    listOfTasks.add(0, t);
    return cb -> NeoParallel.Parallel(listOfTasks, cb);
  }
  
  public static <T, E> AsyncTask<List<T>, E> Series(AsyncTask<T, E> t, AsyncTask<T, E>... tasks) {
    ////
    final var listOfTasks = new ArrayList<AsyncTask<T, E>>();
    listOfTasks.add(t);
    listOfTasks.addAll(Arrays.asList(tasks));
    return cb -> NeoSeries.Series(listOfTasks, cb);
  }
  
  public static <T, E> AsyncTask<List<T>, E> Parallel(List<AsyncTask<T, E>> tasks) {
    return cb -> NeoParallel.Parallel(tasks, cb);
  }
  
  public static <T, E> AsyncTask<List<T>, E> Series(List<AsyncTask<T, E>> tasks) {
    return cb -> NeoSeries.Series(tasks, cb);
  }
  
  public static <T, E> AsyncTask<Map<Object, T>, E> Parallel(Map<Object, AsyncTask<T, E>> tasks) {
    return cb -> NeoParallel.Parallel(tasks, cb);
  }
  
  public static <T, E> AsyncTask<Map<Object, T>, E> Series(Map<Object, AsyncTask<T, E>> tasks) {
    return cb -> NeoSeries.Series(tasks, cb);
  }
  
  // begin race
  
  public static <T, V, E> void Race(Iterable<NeoRaceIfc.AsyncTask<T, E>> i, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(Integer.MAX_VALUE, i, f);
  }
  
  public static <T, V, E> void Race(Iterable<T> i, NeoRaceIfc.IMapper<T, V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(Integer.MAX_VALUE, i, m, f);
  }
  
  public static <T, V, E> void Race(Map<Object, NeoRaceIfc.AsyncTask<T, E>> i, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(Integer.MAX_VALUE, i.values(), f);
  }
  
  public static <T, V, E> void Race(Map<Object, T> i, NeoRaceIfc.IMapper<T, V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(Integer.MAX_VALUE, i.values(), m, f);
  }
  
  public static <T, V, E> void RaceLimit(Integer lim, Iterable<NeoRaceIfc.AsyncTask<T, E>> i, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(lim, i, f);
  }
  
  public static <T, V, E> void RaceLimit(Integer lim, Iterable<T> i, NeoRaceIfc.IMapper<T, V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(lim, i, m, f);
  }
  
  public static <T, V, E> void RaceLimit(Integer lim, Map<Object, NeoRaceIfc.AsyncTask<T, E>> i, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(lim, i.values(), f);
  }
  
  public static <T, V, E> void RaceLimit(Integer lim, Map<Object, T> i, NeoRaceIfc.IMapper<T, V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(lim, i.values(), m, f);
  }
  
  // end race
  
  // begin map
  
  public static <T, V, E> NeoGeneric<T, V, E> Map(NeoEachI.AsyncValueTask<T, E> o, Iterable<T> i, IMapper<T, V, E> m) {
    return new NeoGeneric<T, V, E>() {
      
      @Override
      void handle(Object v, IAsyncCallback cb) {
        
        List<T> copy = new ArrayList<>();
        i.forEach(copy::add); // add rest of iterable after v
        
        o.run(v, copy, e -> {
          
          if (e != null) {
            cb.done(e, null);
            return;
          }
          
          NeoMap.Map(Integer.MAX_VALUE, copy, m, cb);
          
        });
      }
    };
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<List<V>, E> Map(List<T> items, IMapper<T, V, E> m) {
    return cb -> NeoMap.<V, T, E>Map(Integer.MAX_VALUE, items, m, (IAsyncCallback<List<V>, E>) cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<List<V>, E> MapSeries(List<T> items, IMapper<T, V, E> m) {
    return cb -> NeoMap.<V, T, E>Map(1, items, m, (IAsyncCallback<List<V>, E>) cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<List<V>, E> Map(Integer lim, List<T> items, IMapper<T, V, E> m) {
    NeoUtils.validateLimit(lim);
    return cb -> NeoMap.<V, T, E>Map(lim, items, m, (IAsyncCallback<List<V>, E>) cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void Map(Iterable<T> items, IMapper<T, V, E> m, IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(Integer.MAX_VALUE, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void MapSeries(Iterable<T> items, IMapper<T, V, E> m, IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(1, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void MapLimit(Integer limit, Iterable<T> items, IMapper<T, V, E> m, IAsyncCallback<List<V>, E> f) {
    NeoUtils.validateLimit(limit);
    NeoMap.Map(limit, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void Map(Map<Object, T> items, IMapper<T, V, E> m, IAsyncCallback<Map<Object, V>, E> f) {
    NeoMap.Map(Integer.MAX_VALUE, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void MapSeries(Map<Object, T> items, IMapper<T, V, E> m, IAsyncCallback<Map<Object, V>, E> f) {
    NeoMap.Map(1, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void MapLimit(Integer limit, Map<Object, T> items, IMapper<T, V, E> m, IAsyncCallback<Map<Object, V>, E> f) {
    NeoUtils.validateLimit(limit);
    NeoMap.Map(limit, items, m, f);
  }
  
  // end map
  
  //start filter + map
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<List<V>, E> FilterMap(Iterable<T> items, NeoFilterMapI.IMapper<T, V, E> m) {
    return cb -> NeoFilterMap.Map(Integer.MAX_VALUE, items, m, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<List<V>, E> FilterMapSeries(Iterable<T> items, NeoFilterMapI.IMapper<T, V, E> m) {
    return cb -> NeoFilterMap.Map(1, items, m, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<List<V>, E> FilterMapLimit(Integer limit, Iterable<T> items, NeoFilterMapI.IMapper<T, V, E> m) {
    NeoUtils.validateLimit(limit);
    return cb -> NeoFilterMap.Map(limit, items, m, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<Map<Object, V>, E> FilterMap(Map<Object, T> items, NeoFilterMapI.IMapper<T, V, E> m) {
    return cb -> NeoFilterMap.Map(Integer.MAX_VALUE, items, m, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<Map<Object, V>, E> FilterMapSeries(Map<Object, T> items, NeoFilterMapI.IMapper<T, V, E> m) {
    return cb -> NeoFilterMap.Map(1, items, m, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<Map<Object, V>, E> FilterMapLimit(Integer limit, Map<Object, T> items, NeoFilterMapI.IMapper<T, V, E> m) {
    NeoUtils.validateLimit(limit);
    return cb -> NeoFilterMap.Map(limit, items, m, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void FilterMap(Iterable<T> items, NeoFilterMapI.IMapper<T, V, E> m, IAsyncCallback<List<V>, E> f) {
    NeoFilterMap.Map(Integer.MAX_VALUE, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void FilterMapSeries(Iterable<T> items, NeoFilterMapI.IMapper<T, V, E> m, IAsyncCallback<List<V>, E> f) {
    NeoFilterMap.Map(1, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void FilterMapLimit(Integer limit, Iterable<T> items, NeoFilterMapI.IMapper<T, V, E> m, IAsyncCallback<List<V>, E> f) {
    NeoFilterMap.Map(limit, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void FilterMap(Map<Object, T> items, NeoFilterMapI.IMapper<T, V, E> m, IAsyncCallback<Map<Object, V>, E> f) {
    NeoFilterMap.Map(Integer.MAX_VALUE, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void FilterMapSeries(Map<Object, T> items, NeoFilterMapI.IMapper<T, V, E> m, IAsyncCallback<Map<Object, V>, E> f) {
    NeoFilterMap.Map(1, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void FilterMapLimit(Integer limit, Map<Object, T> items, NeoFilterMapI.IMapper<T, V, E> m, IAsyncCallback<Map<Object, V>, E> f) {
    NeoFilterMap.Map(limit, items, m, f);
  }
  
  // end filter + map
  
  // start each
  
  public static <T, E> NeoGeneric<T, Object, E> Each(NeoEachI.AsyncValueTask<T, E> o, List<T> i, NeoEachI.IEacher<T, E> m) {
    return new NeoGeneric<>() {
      @Override
      void handle(Object v, IAsyncCallback cb) {
        
        final List<T> copy = new ArrayList<>(i);
        
        o.run(v, copy, e -> {  // TODO: not always a list..
          
          if (e != null) {
            cb.done((E) e, null);
            return;
          }
          
          NeoEach.Each(Integer.MAX_VALUE, copy, m, err -> {
            cb.done(err, null);
          });
          
        });
        
      }
    };
  }
  
  public static <T, E> NeoGeneric<T, Object, E> Each(
    final NeoEachI.AsyncValueMapTask<T, E> o,
    final Map<Object, Object> i,
    final NeoEachI.IEacher<T, E> m) {
    
    return new NeoGeneric<>() {
      @Override
      void handle(Object v, IAsyncCallback cb) {
        
        final Map<Object, Object> copy = new HashMap<>(i);
        
        o.run(v, copy, e -> {
          
          if (e != null) {
            cb.done((E) e, null);
            return;
          }
          
          NeoEach.Each(Integer.MAX_VALUE, (Iterable<T>) copy.entrySet(), m, err -> {
            cb.done(err, null);
          });
          
        });
        
      }
    };
  }
  
  public static <T, E> NeoEachI.AsyncEachTask<E> Each(Iterable<T> i, NeoEachI.IEacher<T, E> m) {
    return cb -> NeoEach.Each(Integer.MAX_VALUE, i, m, cb);
  }
  
  public static <T, E> void Each(Iterable<T> i, NeoEachI.IEacher<T, E> m, NeoEachI.IEachCallback<E> f) {
    NeoEach.Each(Integer.MAX_VALUE, i, m, f);
  }
  
  public static <T, E> void EachLimit(int limit, Iterable<T> i, NeoEachI.IEacher<T, E> m, NeoEachI.IEachCallback<E> f) {
    NeoUtils.validateLimit(limit);
    NeoEach.Each(limit, i, m, f);
  }
  
  public static <T, E> void EachSeries(Iterable<T> i, NeoEachI.IEacher<T, E> m, NeoEachI.IEachCallback<E> f) {
    NeoEach.Each(1, i, m, f);
  }
  
  public static <T, E> void Each(Map i, NeoEachI.IEacher<T, E> m, NeoEachI.IEachCallback<E> f) {
    NeoEach.Each(Integer.MAX_VALUE, (Set<T>) i.entrySet(), m, f);
  }
  
  public static <T, E> void EachLimit(int limit, Map i, NeoEachI.IEacher<T, E> m, NeoEachI.IEachCallback<E> f) {
    NeoUtils.validateLimit(limit);
    NeoEach.Each(limit, i.<T>entrySet(), m, f);
  }
  
  public static <T, E> void EachSeries(Map<Object, Object> i, NeoEachI.IEacher<T, E> m, NeoEachI.IEachCallback<E> f) {
    NeoEach.Each(1, (Set<T>) i.entrySet(), m, f);
  }
  
  // end each
  
  // start eachOf
  
  public static <T, V, E> NeoEachI.AsyncEachTask<E> EachOf(Iterable<T> i, NeoEachI.IEacherWithTypedIndex<T, V, E> m) {
    return cb -> NeoEach.EachOf(Integer.MAX_VALUE, i, m, cb);
  }
  
  public static <T, V, E> void EachOf(Iterable<T> i, NeoEachI.IEacherWithTypedIndex<T, V, E> m, NeoEachI.IEachCallback<E> f) {
    NeoEach.EachOf(Integer.MAX_VALUE, i, m, f);
  }
  
  public static <T, V, E> void EachOfLimit(
    int limit, Iterable<T> i,
    NeoEachI.IEacherWithTypedIndex<T, V, E> m,
    NeoEachI.IEachCallback<E> f) {
    ///
    NeoUtils.validateLimit(limit);
    NeoEach.EachOf(limit, i, m, f);
  }
  
  public static <T, V, E> void EachOfSeries(
    Iterable<T> i,
    NeoEachI.IEacherWithTypedIndex<T, V, E> m,
    NeoEachI.IEachCallback<E> f) {
    ///
    NeoEach.EachOf(1, i, m, f);
  }
  
  public static <T, V, E> void EachOf(
    final Map i,
    final NeoEachI.IEacherWithTypedIndex<T, V, E> m,
    final NeoEachI.IEachCallback<E> f) {
    ///
    NeoEach.EachOf(Integer.MAX_VALUE, (Set<T>) i.entrySet(), m, f);
  }
  
  public static <T, V, E> void EachOfLimit(
    final int limit,
    final Map i,
    final NeoEachI.IEacherWithTypedIndex<T, V, E> m,
    final NeoEachI.IEachCallback<E> f) {
    /////
    NeoUtils.validateLimit(limit);
    NeoEach.EachOf(limit, i.<T>entrySet(), m, f);
  }
  
  public static <T, V, E> void EachOfSeries(
    final Map<Object, Object> i,
    final NeoEachI.IEacherWithTypedIndex<T, V, E> m,
    final NeoEachI.IEachCallback<E> f) {
    //////
    NeoEach.EachOf(1, (Set<T>) i.entrySet(), m, f);
  }
  
  // end eachOf
  
  // start groupby / begin groupby GROUP_BY_TAG / GROUPBY_TAG
  
  // use Set
  public static <T, V, E> void GroupToSets(Iterable<T> i, NeoGroupByI.IMapper<T, E> m, IAsyncCallback<Map<String, Set<V>>, E> f) {
    NeoGroupBy.GroupToSets(Integer.MAX_VALUE, i, m, f);
  }
  
  public static <T, V, E> void GroupToSetsSeries(Iterable<T> i, NeoGroupByI.IMapper<T, E> m, IAsyncCallback<Map<String, Set<V>>, E> f) {
    NeoGroupBy.GroupToSets(1, i, m, f);
  }
  
  public static <T, V, E> void GroupToSetsLimit(int limit, Iterable<T> i, NeoGroupByI.IMapper<T, E> m, IAsyncCallback<Map<String, Set<V>>, E> f) {
    NeoUtils.validateLimit(limit);
    NeoGroupBy.GroupToSets(limit, i, m, f);
  }
  
  // use List
  public static <T, V, E> void GroupBy(Iterable<T> i, NeoGroupByI.IMapper<T, E> m, IAsyncCallback<Map<String, List<V>>, E> f) {
    NeoGroupBy.GroupToLists(Integer.MAX_VALUE, i, m, f);
  }
  
  public static <T, V, E> void GroupBySeries(Iterable<T> i, NeoGroupByI.IMapper<T, E> m, IAsyncCallback<Map<String, List<V>>, E> f) {
    NeoGroupBy.GroupToLists(1, i, m, f);
  }
  
  public static <T, V, E> void GroupByLimit(int limit, Iterable<T> i, NeoGroupByI.IMapper<T, E> m, IAsyncCallback<Map<String, List<V>>, E> f) {
    NeoUtils.validateLimit(limit);
    NeoGroupBy.GroupToLists(limit, i, m, f);
  }
  
  // end groupby
  
  // start inject / begin inject INJECT_TAG
  
  public static <T, E> void Inject(
    Map.Entry<String, NeoInject.Task<T, E>> a,
    Asyncc.IAsyncCallback<Map<String, Object>, E> f) {
    // one arg
    NeoInject.Inject(Map.ofEntries(a), f);
  }
  
  public static <T, E> void Inject(
    Map<String, NeoInject.Task<T, E>> tasks,
    Asyncc.IAsyncCallback<Map<String, Object>, E> f) {
    NeoInject.Inject(tasks, f);
  }
  
  public static <T, E> void Inject(
    Map<String, NeoInject.Task<T, E>> tasks,
    NeoInjectI.AsyncCallbackSet<Map, E> f) {
    NeoInject.Inject(tasks, (IAsyncCallback) f);
  }
  
  public static <T, E> AsyncTask<Map<String, Object>, E> Inject(
    Map<String, NeoInject.Task<T, E>> tasks) {
    return cb -> NeoInject.Inject(tasks, cb);
  }
  
  public static <T, E> NeoGeneric<T, Object, E> Inject(NeoInjectI.ValueTask<T, E> z, Map<String, NeoInject.Task<T, E>> tasks) {
    return new NeoGeneric<>() {
      @Override
      void handle(Object v, IAsyncCallback cb) {
        
        var task = z.run(v);
        var m = new HashMap<>(tasks);
        
        if (m.containsKey(task.getKey())) {
          throw new Error("Map already contains key: " + task.getKey());
        }
        
        NeoInject.Inject(tasks, cb);
        
      }
    };
  }
  
  // end inject
  
  // start series
  
  public static <T, E> void Series(
    Map<Object, AsyncTask<T, E>> tasks,
    IAsyncCallback<Map<Object, T>, E> f) {
    NeoSeries.Series(tasks, f);
  }
  
  /**
   * Run tasks sequentially. List parameter widened to {@code List<? extends AsyncTask<T, E>>}
   * in v0.2.8 so a {@code List<Task<T>>} (Throwable-fixed shorthand) flows in cleanly.
   */
  public static <T, E> void Series(
    List<? extends AsyncTask<T, E>> tasks,
    IAsyncCallback<List<T>, E> cb) {
    NeoSeries.<T, E>Series(tasks, cb);
  }
  
  public static <T, E> NeoGeneric<T, Void, E> Series(AsyncValueTask<T, E> z, AsyncTask<T, E>... args) {
    return new NeoGeneric<>() {
      @Override
      void handle(Object v, IAsyncCallback cb) {
        
        var t = new AsyncTask<T, E>() {
          @Override
          public void run(IAsyncCallback cb) {
            z.run(v, cb);
          }
        };
        
        var x = new ArrayList<>(Arrays.asList(args));
        x.add(0, t);
        NeoSeries.<T, E>Series(x, cb);
      }
    };
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    IAsyncCallback<List<T>, E> cb) {
    NeoSeries.<T, E>Series(List.of(a), cb);
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    IAsyncCallback<List<T>, E> cb) {
    NeoSeries.<T, E>Series(List.of(a, b), cb);
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    IAsyncCallback<List<T>, E> cb) {
    NeoSeries.Series(List.of(a, b, c), cb);
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    IAsyncCallback<List<T>, E> cb) {
    NeoSeries.Series(List.of(a, b, c, d), cb);
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    IAsyncCallback<List<T>, E> cb) {
    ////
    NeoSeries.Series(List.of(a, b, c, d, e), cb);
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    AsyncTask<T, E> f,
    IAsyncCallback<List<T>, E> cb) {
    ////
    NeoSeries.Series(List.of(a, b, c, d, e, f), cb);
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    AsyncTask<T, E> f,
    AsyncTask<T, E> g,
    IAsyncCallback<List<T>, E> cb) {
    /////
    NeoSeries.Series(List.of(a, b, c, d, e, f, g), cb);
  }
  
  // end series
  
  public static <T, E> void ParallelLimit(
    int limit,
    Map<Object, AsyncTask<T, E>> tasks,
    IAsyncCallback<Map<Object, T>, E> f) {
    ////
    NeoUtils.validateLimit(limit);
    NeoParallel.ParallelLimit(limit, tasks, f);
  }
  
  public static <T, E> void Parallel(
    Map.Entry<Object, AsyncTask<T, E>> a,
    IAsyncCallback<Map<Object, T>, E> f) {
    ////
    NeoParallel.Parallel(Map.ofEntries(a), f);
  }
  
  public static <T, E> void Parallel(
    Map.Entry<Object, AsyncTask<T, E>> a,
    Map.Entry<Object, AsyncTask<T, E>> b,
    IAsyncCallback<Map<Object, T>, E> f) {
    /////
    NeoParallel.Parallel(Map.ofEntries(a, b), f);
  }
  
  public static <T, E> void Parallel(
    Map.Entry<Object, AsyncTask<T, E>> a,
    Map.Entry<Object, AsyncTask<T, E>> b,
    Map.Entry<Object, AsyncTask<T, E>> c,
    IAsyncCallback<Map<Object, T>, E> f) {
    ////
    NeoParallel.Parallel(Map.ofEntries(a, b, c), f);
  }
  
  /**
   * @exclude
   */
  public static <T, E> void Parallel(
    Map.Entry<Object, AsyncTask<T, E>> a,
    Map.Entry<Object, AsyncTask<T, E>> b,
    Map.Entry<Object, AsyncTask<T, E>> c,
    Map.Entry<Object, AsyncTask<T, E>> d,
    IAsyncCallback<Map<Object, T>, E> f) {
    ////
    NeoParallel.Parallel(Map.ofEntries(a, b, c, d), f);
  }
  
  /**
   * @exclude
   */
  public static <T, E> void Parallel(
    Map.Entry<Object, AsyncTask<T, E>> a,
    Map.Entry<Object, AsyncTask<T, E>> b,
    Map.Entry<Object, AsyncTask<T, E>> c,
    Map.Entry<Object, AsyncTask<T, E>> d,
    Map.Entry<Object, AsyncTask<T, E>> e,
    IAsyncCallback<Map<Object, T>, E> f) {
    ////
    NeoParallel.Parallel(Map.ofEntries(a, b, c, d, e), f);
  }
  
  /**
   * @exclude
   */
  public static <T, E> void Parallel(
    Map.Entry<Object, AsyncTask<T, E>> a,
    Map.Entry<Object, AsyncTask<T, E>> b,
    Map.Entry<Object, AsyncTask<T, E>> c,
    Map.Entry<Object, AsyncTask<T, E>> d,
    Map.Entry<Object, AsyncTask<T, E>> e,
    Map.Entry<Object, AsyncTask<T, E>> f,
    IAsyncCallback<Map<Object, T>, E> cb) {
    /////
    NeoParallel.Parallel(Map.ofEntries(a, b, c, d, e, f), cb);
  }
  
  public static <T, E> void Parallel(
    Map.Entry<Object, AsyncTask<T, E>> a,
    Map.Entry<Object, AsyncTask<T, E>> b,
    Map.Entry<Object, AsyncTask<T, E>> c,
    Map.Entry<Object, AsyncTask<T, E>> d,
    Map.Entry<Object, AsyncTask<T, E>> e,
    Map.Entry<Object, AsyncTask<T, E>> f,
    Map.Entry<Object, AsyncTask<T, E>> g,
    IAsyncCallback<Map<Object, T>, E> cb) {
    /////
    NeoParallel.Parallel(Map.ofEntries(a, b, c, d, e, f, g), cb);
  }
  
  public static <T, E> void Parallel(Map<Object, AsyncTask<T, E>> tasks, IAsyncCallback<Map<Object, T>, E> f) {
    ////
    NeoParallel.Parallel(tasks, f);
  }
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    IAsyncCallback<List<T>, E> cb) {
    ////
    NeoParallel.Parallel(List.of(a), cb);
  }
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    IAsyncCallback<List<T>, E> cb) {
    ////
    NeoParallel.Parallel(List.of(a, b), cb);
  }
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.Parallel(List.of(a, b, c), cb);
  }
  
  /**
   * @exclude
   */
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.<T, E>Parallel(List.of(a, b, c, d), cb);
  }
  
  /**
   * @exclude
   */
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.Parallel(List.of(a, b, c, d, e), cb);
  }
  
  /**
   * @exclude
   */
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    AsyncTask<T, E> f,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.Parallel(List.of(a, b, c, d, e, f), cb);
  }
  
  /**
   * @exclude
   */
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    AsyncTask<T, E> f,
    AsyncTask<T, E> g,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.Parallel(List.of(a, b, c, d, e, f, g), cb);
  }
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    AsyncTask<T, E> f,
    AsyncTask<T, E> g,
    AsyncTask<T, E> h,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.Parallel(List.of(a, b, c, d, e, f, g, h), cb);
  }
  
  // begin waterfall / start waterfall  TAG_WATERFALL
  
  public static <T, E> void Waterfall(
    List<NeoWaterfallI.AsyncTask<T, E>> tasks,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(tasks, cb);
  }

  public static <T, E> void Waterfall2(
    List<NeoWaterfallI.AsyncTask<T, E>> tasks,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall2(tasks, cb);
  }
  
  public static <T, E> NeoGeneric<T, Object, E> Waterfall(NeoWaterfallI.AsyncValueTask<T, E> z, NeoWaterfallI.AsyncTask<T, E>... args) {
    return new NeoGeneric<>() {
      @Override
      void handle(Object v, IAsyncCallback cb) {
        
        var t = new NeoWaterfallI.AsyncTask<T, E>() {
          
          @Override
          public void run(NeoWaterfallI.AsyncCallback<T, E> cb) {
            z.run(v, cb);
          }
        };
        
        var c = new ArrayList<>(Arrays.asList(args));
        c.add(0, t);
        NeoWaterfall.Waterfall(c, cb);
      }
    };
  }
  
  public static <T, E> void Waterfall(
    NeoWaterfallI.AsyncTask<T, E> a,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(List.of(a), cb);
  }
  
  public static <T, E> void Waterfall(
    NeoWaterfallI.AsyncTask<T, E> a,
    NeoWaterfallI.AsyncTask<T, E> b,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(List.of(a, b), cb);
  }
  
  /**
   * @exclude
   * @Version 1.2.3
   * @Since 1.2.0
   */
  public static <T, E> void Waterfall(
    NeoWaterfallI.AsyncTask<T, E> a,
    NeoWaterfallI.AsyncTask<T, E> b,
    NeoWaterfallI.AsyncTask<T, E> c,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(List.of(a, b, c), cb);
  }
  
  /**
   * @exclude
   */
  public static <T, E> void Waterfall(
    NeoWaterfallI.AsyncTask<T, E> a,
    NeoWaterfallI.AsyncTask<T, E> b,
    NeoWaterfallI.AsyncTask<T, E> c,
    NeoWaterfallI.AsyncTask<T, E> d,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(List.of(a, b, c, d), cb);
  }
  
  /**
   * @exclude
   */
  public static <T, E> void Waterfall(
    NeoWaterfallI.AsyncTask<T, E> a,
    NeoWaterfallI.AsyncTask<T, E> b,
    NeoWaterfallI.AsyncTask<T, E> c,
    NeoWaterfallI.AsyncTask<T, E> d,
    NeoWaterfallI.AsyncTask<T, E> e,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(List.of(a, b, c, d, e), cb);
  }
  
  public static <T, E> void Waterfall(
    NeoWaterfallI.AsyncTask<T, E> a,
    NeoWaterfallI.AsyncTask<T, E> b,
    NeoWaterfallI.AsyncTask<T, E> c,
    NeoWaterfallI.AsyncTask<T, E> d,
    NeoWaterfallI.AsyncTask<T, E> e,
    NeoWaterfallI.AsyncTask<T, E> f,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(List.of(a, b, c, d, e, f), cb);
  }
  
  // end waterfall
  
  /**
   * Fan out tasks; final callback fires once with results in submission order (or with the
   * first error). List parameter widened to {@code List<? extends AsyncTask<T, E>>} in v0.2.8
   * so a {@code List<Task<T>>} (Throwable-fixed shorthand) flows in cleanly.
   */
  public static <T, E> void Parallel(List<? extends AsyncTask<T, E>> tasks, IAsyncCallback<List<T>, E> cb) {
    NeoParallel.Parallel(tasks, cb);
  }
  
  /**
   * <pre class="prettyprint">
   * public class JavadocTest {
   *   // indentation and line breaks are kept
   *
   *   @SuppressWarnings
   *   {@code public List<String> generics(){
   *     // '@', '<' and '>'  have to be escaped with HTML codes
   *     // when used in annotations or generics
   *   }}
   * }
   * </pre>
   */
  public static <T, E> void ParallelLimit(
    final int limit,
    final List<? extends AsyncTask<T, E>> tasks,
    final IAsyncCallback<List<T>, E> cb) {
    NeoUtils.validateLimit(limit);
    NeoParallel.ParallelLimit(limit, tasks, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <I, T, V, E> void ReduceRight(
    final I initialVal,
    final List<T> tasks,
    final NeoReduceI.IReducer<T, V, E> m,
    final Asyncc.IAsyncCallback<V, E> f) {
    ////
    NeoReduce.ReduceRight(initialVal, tasks, m, f);
  }
  
  /**
   * @Version 1.2.3
   * @Since 1.2.0
   * Returns an Image object that can then be painted on the screen.
   */
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ReduceRight(
    final List<T> tasks,
    final NeoReduceI.IReducer<T, V, E> m,
    final Asyncc.IAsyncCallback<V, E> f) {
    /////
    NeoReduce.ReduceRight(tasks, m, f);
  }
  
  /**
   * @Version 1.2.3
   * @Since 1.2.0
   * <pre class="prettyprint">
   * new BeanTranslator.Builder()
   *   .translate(
   *     new{@code Translator<String, Integer>}(String.class, Integer.class){
   *      {@literal @}Override
   *       public Integer translate(String instance) {
   *         return Integer.valueOf(instance);
   *       }})
   *   .build();
   * </pre>
   */
  @SuppressWarnings("Duplicates")
  public static <I, T, V, E> void Reduce(
    final I initialVal,
    final List<T> tasks,
    final NeoReduceI.IReducer<T, V, E> m,
    final Asyncc.IAsyncCallback<V, E> f) {
    ////
    NeoReduce.Reduce(initialVal, tasks, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void Reduce(List<T> tasks, NeoReduceI.IReducer<T, V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoReduce.Reduce(tasks, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void Concat(List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(Integer.MAX_VALUE, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatSeries(List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(1, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatLimit(int lim, List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoUtils.validateLimit(lim);
    NeoMap.Map(lim, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void Concat(int depth, List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(Integer.MAX_VALUE, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatSeries(int depth, List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(1, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatLimit(int depth, int lim, List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoUtils.validateLimit(lim);
    NeoMap.Map(lim, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatDeep(List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(Integer.MAX_VALUE, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatDeepSeries(List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(1, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatDeepLimit(int lim, List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    
    NeoUtils.validateLimit(lim);
    NeoMap.Map(lim, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void Concat(List<? extends Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.Parallel(tasks, (err, results) -> {
      f.done(err, NeoConcat.concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ConcatSeries(List<? extends Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoSeries.Series(tasks, (err, results) -> {
      f.done(err, NeoConcat.concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ConcatLimit(int lim, List<? extends Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    
    NeoUtils.validateLimit(lim);
    NeoParallel.ParallelLimit(lim, tasks, (err, results) -> {
      f.done(err, NeoConcat.concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void Concat(int depth, List<? extends Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.Parallel(tasks, (err, results) -> {
      f.done(err, NeoConcat.flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ConcatSeries(int depth, List<? extends Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoSeries.Series(tasks, (err, results) -> {
      f.done(err, NeoConcat.flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ConcatLimit(int depth, int lim, List<? extends Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    
    NeoUtils.validateLimit(lim);
    NeoParallel.ParallelLimit(lim, tasks, (err, results) -> {
      f.done(err, NeoConcat.flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ConcatDeep(List<? extends Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.Parallel(tasks, (err, results) -> {
      f.done(err, NeoConcat.flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ConcatDeepSeries(List<? extends Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoSeries.Series(tasks, (err, results) -> {
      f.done(err, NeoConcat.flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ConcatDeepLimit(int lim, List<? extends Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    
    NeoUtils.validateLimit(lim);
    NeoParallel.ParallelLimit(lim, tasks, (err, results) -> {
      f.done(err, NeoConcat.flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  // end concat
  
  // begin times / start times TIMES_TAG
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void Times(int count, NeoTimesI.ITimesr<T, E> m, NeoTimesI.ITimesCallback<List<T>, E> f) {
    NeoTimes.Times(Integer.MAX_VALUE, count, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void Series(int count, NeoTimesI.ITimesr<T, E> m, NeoTimesI.ITimesCallback<List<T>, E> f) {
    NeoTimes.Times(1, count, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void TimesLimit(int lim, int count, NeoTimesI.ITimesr<T, E> m, NeoTimesI.ITimesCallback<List<T>, E> f) {
    NeoUtils.validateLimit(lim);
    NeoTimes.Times(lim, count, m, f);
  }
  
  // end times
  
  // start whilst / begin whilst WHILST_TAG
  
  public static <T, E> void Whilst(NeoWhilstI.SyncTruthTest test, NeoWhilstI.AsyncTask<T, E> task, IAsyncCallback<List<T>, E> f) {
    NeoWhilst.Whilst(1, test, null, task, f);
  }
  
  public static <T, E> void WhilstLimit(int limit, NeoWhilstI.SyncTruthTest test, NeoWhilstI.AsyncTask<T, E> task, IAsyncCallback<List<T>, E> f) {
    NeoWhilst.Whilst(limit, test, null, task, f);
  }
  
  public static <T, E> void Whilst(NeoWhilstI.AsyncTruthTest test, NeoWhilstI.AsyncTask<T, E> task, IAsyncCallback<List<T>, E> f) {
    NeoWhilst.Whilst(1, null, test, task, f);
  }
  
  public static <T, E> void WhilstLimit(int limit, NeoWhilstI.AsyncTruthTest test, NeoWhilstI.AsyncTask<T, E> task, IAsyncCallback<List<T>, E> f) {
    NeoWhilst.Whilst(limit, null, test, task, f);
  }
  
  // end whilst
  
  // start DoWhilist / begin DoWhilist DO_WHILST_TAG DOWHILST_TAG
  
  // dowhilst has 2x as many variants as whilst, since dowhilst puts the truth test in 2 places, for convenience
  
  public static <T, E> void DoWhilst(NeoWhilstI.SyncTruthTest test, NeoWhilstI.AsyncTask<T, E> task, IAsyncCallback<List<T>, E> f) {
    NeoWhilst.DoWhilst(1, test, null, task, f);
  }

//  public static <T, E> void DoWhilst(NeoWhilstI.AsyncTask<T, E> task, NeoWhilstI.SyncTruthTest test, IAsyncCallback<List<T>, E> f) {
//    NeoWhilst.DoWhilst(1, test, null, task, f);
//  }
  
  public static <T, E> void DoWhilstLimit(int limit, NeoWhilstI.SyncTruthTest test, NeoWhilstI.AsyncTask<T, E> task, IAsyncCallback<List<T>, E> f) {
    NeoWhilst.DoWhilst(limit, test, null, task, f);
  }

//  public static <T, E> void DoWhilstLimit(int limit, NeoWhilstI.AsyncTask<T, E> task, NeoWhilstI.SyncTruthTest test, IAsyncCallback<List<T>, E> f) {
//    NeoWhilst.DoWhilst(limit, test, null, task, f);
//  }
  
  public static <T, E> void DoWhilst(NeoWhilstI.AsyncTruthTest test, NeoWhilstI.AsyncTask<T, E> task, IAsyncCallback<List<T>, E> f) {
    NeoWhilst.DoWhilst(1, null, test, task, f);
  }

//  public static <T, E> void DoWhilst(NeoWhilstI.AsyncTask<T, E> task, NeoWhilstI.AsyncTruthTest test, IAsyncCallback<List<T>, E> f) {
//    NeoWhilst.DoWhilst(1, null, test, task, f);
//  }
  
  public static <T, E> void DoWhilstLimit(int limit, NeoWhilstI.AsyncTruthTest test, NeoWhilstI.AsyncTask<T, E> task, IAsyncCallback<List<T>, E> f) {
    NeoWhilst.DoWhilst(limit, null, test, task, f);
  }

//  public static <T, E> void DoWhilstLimit(int limit, NeoWhilstI.AsyncTask<T, E> task, NeoWhilstI.AsyncTruthTest test, IAsyncCallback<List<T>, E> f) {
//    NeoWhilst.DoWhilst(limit, null, test, task, f);
//  }
  
  // end whilst
  
}
