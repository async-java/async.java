package org.ores.async;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Promise-returning sibling to {@link Asyncc}. Each combinator returns a
 * {@link CompletableFuture} instead of taking an error-first final callback &mdash; the same
 * vocabulary, the JDK's promise shape.
 *
 * <p>{@code AsyncFut} is implemented in terms of {@link Asyncc} (via {@link WrapFuture}), so
 * every combinator inherits the v0.2.x concurrency hardening: at-most-once final callback,
 * lost-update-free counters, slot-write-before-counter-increment ordering, no
 * {@code ArrayList} resize race, the {@code ParallelLimit} {@code &lt;= limit}-in-flight
 * invariant, etc. The wrapper layer adds one {@code CompletableFuture} allocation per call;
 * use {@link Asyncc} directly if you need to shave that ~5 µs.
 *
 * <h3>Task shape</h3>
 *
 * <p>Most combinators take a list (or function) of {@link Supplier}s that produce a
 * {@link CompletionStage}. The supplier is invoked when the combinator chooses to start that
 * task &mdash; this is crucial for {@code Series}, {@code ParallelLimit}, and {@code Race},
 * where eagerly-started futures would defeat the point. If you already have an in-flight
 * future, wrap it as {@code () -&gt; theFuture}.
 *
 * <h3>Quick examples</h3>
 *
 * <pre>
 *   // Parallel: fan out N tasks, collect their results
 *   CompletableFuture&lt;List&lt;String&gt;&gt; both = AsyncFut.Parallel(List.of(
 *       () -&gt; CompletableFuture.supplyAsync(this::fetchA, exec),
 *       () -&gt; CompletableFuture.supplyAsync(this::fetchB, exec)
 *   ));
 *   both.thenAccept(results -&gt; reply.send(combine(results.get(0), results.get(1))));
 *
 *   // ParallelLimit: same but with a concurrency cap
 *   CompletableFuture&lt;List&lt;Path&gt;&gt; downloaded = AsyncFut.ParallelLimit(8, downloads);
 *
 *   // Series: sequential, collect each result
 *   CompletableFuture&lt;List&lt;Step&gt;&gt; chain = AsyncFut.Series(List.of(
 *       () -&gt; validate(req), () -&gt; persist(req), () -&gt; notify(req)
 *   ));
 *
 *   // Race: first completer wins
 *   CompletableFuture&lt;String&gt; winner = AsyncFut.Race(List.of(
 *       () -&gt; fromPrimary(), () -&gt; fromReplica()
 *   ));
 *
 *   // Map: async transform preserving input order
 *   CompletableFuture&lt;List&lt;Profile&gt;&gt; profiles =
 *       AsyncFut.Map(userIds, id -&gt; fetchProfileAsync(id));
 *
 *   // Concat: async map, then flatten one level
 *   CompletableFuture&lt;List&lt;Order&gt;&gt; orders =
 *       AsyncFut.Concat(userIds, id -&gt; fetchOrdersAsync(id));
 *
 *   // Reduce: sequential fold with an async reducer
 *   CompletableFuture&lt;BigDecimal&gt; total =
 *       AsyncFut.Reduce(transactions, BigDecimal.ZERO,
 *           (acc, txn) -&gt; computeAsync(acc, txn));
 *
 *   // Times: run the same task N times
 *   CompletableFuture&lt;List&lt;Sample&gt;&gt; samples =
 *       AsyncFut.Times(8, i -&gt; generateSampleAsync(i));
 *
 *   // Each: fire-and-forget per element, complete with Void when all done
 *   CompletableFuture&lt;Void&gt; sent =
 *       AsyncFut.Each(users, u -&gt; sendEmailAsync(u));
 * </pre>
 *
 * <h3>Error semantics</h3>
 *
 * <p>Each combinator's returned future completes exceptionally with the first error any task
 * produces (just like {@link Asyncc}'s short-circuit). Subsequent task completions are
 * absorbed by the at-most-once guard.
 *
 * <h3>Interop</h3>
 *
 * <p>You can mix {@link Asyncc} and {@code AsyncFut} freely via {@link WrapFuture}:
 *
 * <pre>
 *   // AsyncFut.Parallel inside an Asyncc.Waterfall step:
 *   Asyncc.Waterfall(List.of(
 *       c -&gt; c.success(parseRequest(raw)),
 *       (req, c) -&gt; WrapFuture.fromStage(
 *               AsyncFut.Parallel(List.of(
 *                   () -&gt; lookupA(req), () -&gt; lookupB(req)
 *               ))
 *           ).run(c)
 *   ), wrap(finalValue -&gt; reply.send(finalValue)));
 * </pre>
 *
 * <p>Plain JDK {@link Future Futures} are also supported. Because {@code Future#get()} blocks,
 * pass an executor that is dedicated to the wait. A virtual-thread executor from
 * {@link AsyncLoom#newVirtualThreadPerTaskExecutor()} is ideal for many blocking waits:
 *
 * <pre>
 *   ExecutorService vt = AsyncLoom.newVirtualThreadPerTaskExecutor();
 *   try {
 *       CompletableFuture&lt;List&lt;Payload&gt;&gt; all =
 *           AsyncFut.ParallelFutures(vt, legacyClient.submitAll(requests));
 *   } finally {
 *       vt.shutdown();
 *   }
 * </pre>
 *
 * @see Asyncc
 * @see WrapFuture
 * @since 0.2.7
 */
public final class AsyncFut {

  private AsyncFut() {}

  // WrapFuture.toFuture already fixes the error type to Throwable, which is what AsyncFut wants
  // throughout. We alias it locally just to keep the source readable.
  private static <V> CompletableFuture<V> futOf(
      final java.util.function.Consumer<Asyncc.IAsyncCallback<V, Throwable>> setup) {
    return WrapFuture.toFuture(setup);
  }

  // ---------------- Parallel ---------------------------------------------

  /**
   * Run all tasks concurrently; return a future of their results in the same order as the
   * input list. Short-circuits on the first failure.
   */
  public static <T> CompletableFuture<List<T>> Parallel(
      final List<Supplier<? extends CompletionStage<T>>> tasks) {
    return futOf(c ->
        Asyncc.<T, Throwable>Parallel(toAsyncTasks(tasks), c));
  }

  /** Two-task convenience. */
  public static <T> CompletableFuture<List<T>> Parallel(
      final Supplier<? extends CompletionStage<T>> a,
      final Supplier<? extends CompletionStage<T>> b) {
    return Parallel(List.of(a, b));
  }

  /** Three-task convenience. */
  public static <T> CompletableFuture<List<T>> Parallel(
      final Supplier<? extends CompletionStage<T>> a,
      final Supplier<? extends CompletionStage<T>> b,
      final Supplier<? extends CompletionStage<T>> c) {
    return Parallel(List.of(a, b, c));
  }

  // ---------------- ParallelLimit ----------------------------------------

  /** Like {@link #Parallel} but with at most {@code limit} tasks in flight at any time. */
  public static <T> CompletableFuture<List<T>> ParallelLimit(
      final int limit,
      final List<Supplier<? extends CompletionStage<T>>> tasks) {
    return futOf(c ->
        Asyncc.<T, Throwable>ParallelLimit(limit, toAsyncTasks(tasks), c));
  }

  // ---------------- Series -----------------------------------------------

  /**
   * Run tasks one after another. The returned future completes with a list of each task's
   * value in input order. Short-circuits on the first failure.
   */
  public static <T> CompletableFuture<List<T>> Series(
      final List<Supplier<? extends CompletionStage<T>>> tasks) {
    return futOf(c ->
        Asyncc.<T, Throwable>Series(toAsyncTasks(tasks), c));
  }

  // ---------------- ParallelF / RaceF — already-started futures ---------

  /**
   * Like {@link #Parallel(List) Parallel}, but accepts already-started {@link CompletionStage}s
   * directly instead of {@link Supplier}s. Use this for clean nested composition where the inner
   * combinators have already returned their futures:
   *
   * <pre>
   *   // before (one extra `() -&gt;` per task to defer the start):
   *   CompletableFuture&lt;List&lt;List&lt;X&gt;&gt;&gt; nested = AsyncFut.Parallel(List.of(
   *       () -&gt; AsyncFut.Series(seriesTasks),
   *       () -&gt; AsyncFut.Parallel(parallelTasks)
   *   ));
   *
   *   // after (drop the supplier wrappers):
   *   CompletableFuture&lt;List&lt;List&lt;X&gt;&gt;&gt; nested = AsyncFut.ParallelF(List.of(
   *       AsyncFut.Series(seriesTasks),
   *       AsyncFut.Parallel(parallelTasks)
   *   ));
   * </pre>
   *
   * <p>Semantic difference vs {@link #Parallel(List) Parallel}: this variant doesn't control
   * when the tasks start &mdash; they're already in flight by the time the list is built. For
   * top-level fan-out that's exactly what you want; for {@code Series}-style ordering it'd
   * defeat the purpose (use the {@link Supplier}-taking {@link #Series(List) Series} for that).
   *
   * @since 0.2.8
   */
  public static <T> CompletableFuture<List<T>> ParallelF(
      final List<? extends CompletionStage<T>> futures) {
    final List<Supplier<? extends CompletionStage<T>>> wrapped = new ArrayList<>(futures.size());
    for (final CompletionStage<T> stage : futures) {
      wrapped.add(() -> stage);
    }
    return Parallel(wrapped);
  }

  /**
   * Like {@link #Race(List) Race}, but accepts already-started {@link CompletionStage}s. See
   * {@link #ParallelF(List)} for the rationale.
   *
   * @since 0.2.8
   */
  public static <T> CompletableFuture<T> RaceF(
      final List<? extends CompletionStage<T>> futures) {
    final List<Supplier<? extends CompletionStage<T>>> wrapped = new ArrayList<>(futures.size());
    for (final CompletionStage<T> stage : futures) {
      wrapped.add(() -> stage);
    }
    return Race(wrapped);
  }

  // ---------------- Plain Future interop --------------------------------

  /**
   * Like {@link #ParallelF(List)}, but accepts plain JDK {@link Future Futures}.
   *
   * <p>The library calls {@link Future#get()} internally on {@code waitExecutor}, so callers do
   * not have to write their own blocking bridge. This is intended for already-started legacy
   * futures returned by APIs such as {@link java.util.concurrent.ExecutorService#submit}.
   *
   * <p>Important: {@code waitExecutor} is the executor used to wait on the futures, not
   * necessarily the executor that produced them. Avoid using the same saturated fixed-size pool
   * that still needs to run the underlying work. For many blocking waits, use
   * {@link AsyncLoom#newVirtualThreadPerTaskExecutor()} on JDK 21+.
   *
   * <p>Cancelling the returned future cancels the child bridge futures, which in turn propagate
   * cancellation to the underlying plain {@code Future}s.
   *
   * @param <T> value type produced by each future
   * @param waitExecutor executor used for blocking {@code Future#get()} waits
   * @param futures already-started plain JDK futures
   * @return a future of all results in input order
   * @since 0.2.10
   */
  public static <T> CompletableFuture<List<T>> ParallelFutures(
      final Executor waitExecutor,
      final List<? extends Future<? extends T>> futures) {

    final List<CompletableFuture<T>> stages = toCompletableFutures(waitExecutor, futures);
    return mirrorWithCancellation(ParallelF(stages), stages, false);
  }

  /**
   * Like {@link #RaceF(List)}, but accepts plain JDK {@link Future Futures}. The library calls
   * {@link Future#get()} internally on {@code waitExecutor}; the first completed wait wins.
   *
   * <p>When the race settles, incomplete child bridge futures are cancelled so losing plain
   * {@code Future}s are interrupted when their implementation supports cancellation.
   *
   * @param <T> value type produced by each future
   * @param waitExecutor executor used for blocking {@code Future#get()} waits
   * @param futures already-started plain JDK futures
   * @return a future completed with the first future result
   * @since 0.2.10
   */
  public static <T> CompletableFuture<T> RaceFutures(
      final Executor waitExecutor,
      final List<? extends Future<? extends T>> futures) {

    final List<CompletableFuture<T>> stages = toCompletableFutures(waitExecutor, futures);
    return mirrorWithCancellation(RaceF(stages), stages, true);
  }

  // ---------------- Race -------------------------------------------------

  /**
   * Run all tasks concurrently; complete with the value of whichever finishes first. Errors
   * from non-winning tasks are absorbed by the at-most-once guard.
   */
  public static <T> CompletableFuture<T> Race(
      final List<Supplier<? extends CompletionStage<T>>> tasks) {
    return AsyncFut.<T>futOf(c -> {
      // Race uses its own task interface (RaceCallback rather than IAsyncCallback). Bridge each
      // supplier into the Race-specific shape.
      final List<NeoRaceIfc.AsyncTask<T, Throwable>> raceTasks = new ArrayList<>(tasks.size());
      for (final Supplier<? extends CompletionStage<T>> s : tasks) {
        raceTasks.add(rcb -> {
          try {
            s.get().whenComplete((v, err) -> {
              if (err != null) rcb.done(err, null);
              else rcb.done(null, v);
            });
          } catch (Throwable t) {
            rcb.done(t, null);
          }
        });
      }
      Asyncc.<T, T, Throwable>Race(raceTasks, c);
    });
  }

  // ---------------- Map --------------------------------------------------

  /**
   * Run {@code mapper(element)} concurrently for each element of {@code input}; return a
   * future of the per-element values in input order.
   */
  public static <T, V> CompletableFuture<List<V>> Map(
      final Iterable<T> input,
      final Function<? super T, ? extends CompletionStage<V>> mapper) {

    return AsyncFut.<List<V>>futOf(c -> {
      // Materialise into a list to preserve order without rescanning the iterable.
      final List<T> items = toList(input);
      Asyncc.<V, T, Throwable>Map(items, (item, inner) -> {
        try {
          mapper.apply(item).whenComplete((v, err) -> {
            if (err != null) inner.fail(err);
            else inner.success(v);
          });
        } catch (Throwable t) {
          inner.fail(t);
        }
      }, c);
    });
  }

  // ---------------- Concat -----------------------------------------------

  /**
   * Async map + one-level flatten. {@code mapper(element)} runs for each element concurrently
   * and returns a collection of zero or more output values; the returned future completes with
   * all mapper results concatenated in input order.
   *
   * <pre>
   *   CompletableFuture&lt;List&lt;Order&gt;&gt; orders =
   *       AsyncFut.Concat(userIds, id -&gt; orderClient.ordersForUserAsync(id));
   * </pre>
   *
   * @param <T> input value type
   * @param <V> flattened output value type
   * @param input values to map
   * @param mapper async mapper producing zero or more output values
   * @return flattened result future
   * @since 0.2.10
   */
  public static <T, V> CompletableFuture<List<V>> Concat(
      final Iterable<T> input,
      final Function<? super T, ? extends CompletionStage<? extends Collection<? extends V>>> mapper) {

    return ConcatLimit(Integer.MAX_VALUE, input, mapper);
  }

  /**
   * Sequential version of {@link #Concat(Iterable, Function)}.
   *
   * @param <T> input value type
   * @param <V> flattened output value type
   * @param input values to map
   * @param mapper async mapper producing zero or more output values
   * @return flattened result future
   * @since 0.2.10
   */
  public static <T, V> CompletableFuture<List<V>> ConcatSeries(
      final Iterable<T> input,
      final Function<? super T, ? extends CompletionStage<? extends Collection<? extends V>>> mapper) {

    return ConcatLimit(1, input, mapper);
  }

  /**
   * Bounded-concurrency version of {@link #Concat(Iterable, Function)}.
   *
   * @param <T> input value type
   * @param <V> flattened output value type
   * @param limit max in-flight mapper calls
   * @param input values to map
   * @param mapper async mapper producing zero or more output values
   * @return flattened result future
   * @since 0.2.10
   */
  public static <T, V> CompletableFuture<List<V>> ConcatLimit(
      final int limit,
      final Iterable<T> input,
      final Function<? super T, ? extends CompletionStage<? extends Collection<? extends V>>> mapper) {

    final List<T> items = toList(input);
    final List<Supplier<? extends CompletionStage<Collection<? extends V>>>> suppliers =
        new ArrayList<>(items.size());

    for (final T item : items) {
      suppliers.add(() -> widenCollectionStage(mapper.apply(item)));
    }

    return AsyncFut.ParallelLimit(limit, suppliers).thenApply(AsyncFut::flattenOne);
  }

  // ---------------- Reduce -----------------------------------------------

  /**
   * Sequential fold with an async reducer. {@code reducer(acc, element)} is invoked once per
   * element, in input order, with the running accumulator. The returned future completes with
   * the final accumulator value.
   */
  public static <T, V> CompletableFuture<V> Reduce(
      final Iterable<T> input,
      final V identity,
      final BiFunction<V, ? super T, ? extends CompletionStage<V>> reducer) {

    return AsyncFut.<V>futOf(c -> {
      final List<T> items = toList(input);
      Asyncc.<V, T, V, Throwable>Reduce(identity, items, (acc, item, inner) -> {
        try {
          reducer.apply(acc, item).whenComplete((v, err) -> {
            if (err != null) inner.fail(err);
            else inner.success(v);
          });
        } catch (Throwable t) {
          inner.fail(t);
        }
      }, c);
    });
  }

  // ---------------- Times ------------------------------------------------

  /**
   * Run {@code task(i)} for {@code i} in {@code [0, n)} concurrently; return a future of the
   * per-iteration results in {@code i} order.
   */
  public static <T> CompletableFuture<List<T>> Times(
      final int n,
      final IntFunction<? extends CompletionStage<T>> task) {

    return AsyncFut.<List<T>>futOf(c -> {
      // The Times final-callback type is NeoTimesI.ITimesCallback<List<T>, E>, which extends
      // Asyncc.IAsyncCallback<List<T>, E> with the same single method. Wrap explicitly so
      // type inference is happy.
      final NeoTimesI.ITimesCallback<List<T>, Throwable> finalCb = c::done;

      Asyncc.<T, Throwable>Times(n,
          (NeoTimesI.ITimesr<T, Throwable>) (i, inner) -> {
            try {
              task.apply(i).whenComplete((v, err) -> {
                if (err != null) inner.done(err, null);
                else inner.done(null, v);
              });
            } catch (Throwable t) {
              inner.done(t, null);
            }
          },
          finalCb);
    });
  }

  // ---------------- Each -------------------------------------------------

  /**
   * Fire-and-forget per element with a concurrency cap. {@code task(element)} runs for each
   * element concurrently up to {@code limit} in-flight; the returned future completes with
   * {@code null} when all tasks finish, or exceptionally on the first failure. No per-element
   * value is collected.
   */
  public static <T> CompletableFuture<Void> Each(
      final int limit,
      final Iterable<T> input,
      final Function<? super T, ? extends CompletionStage<Void>> task) {

    return AsyncFut.<Void>futOf(c -> {
      // Use the package-private NeoEach.Each(int, ...) since Asyncc exposes only the
      // unlimited variant publicly.
      NeoEach.<T, Throwable>Each(limit, input,
          (NeoEachI.IEacher<T, Throwable>) (item, inner) -> {
            try {
              task.apply(item).whenComplete((v, err) -> {
                if (err != null) inner.done(err);
                else inner.done(null);
              });
            } catch (Throwable t) {
              inner.done(t);
            }
          },
          (NeoEachI.IEachCallback<Throwable>) err -> {
            if (err != null) c.fail(err);
            else c.success(null);
          });
    });
  }

  /** Unlimited-concurrency convenience for {@link #Each(int, Iterable, Function)}. */
  public static <T> CompletableFuture<Void> Each(
      final Iterable<T> input,
      final Function<? super T, ? extends CompletionStage<Void>> task) {
    return Each(Integer.MAX_VALUE, input, task);
  }

  // ---------------- Waterfall (named accumulator) ------------------------

  /**
   * Sequential pipeline with a named accumulator. Each step receives a snapshot of the
   * accumulated map so far and returns a {@link CompletionStage} producing a single
   * {@code (key, value)} entry to add to the accumulator. The returned future completes with
   * the final accumulator map.
   *
   * <p>This mirrors {@link Asyncc#Waterfall(List, Asyncc.IAsyncCallback)}'s named-accumulator
   * shape (the {@code HashMap<String, Object>} you get back from the callback form), adapted
   * for {@code CompletionStage}-returning steps.
   *
   * <p>If a step's stage emits {@code null} as the entry, that step is skipped (no entry is
   * added) but the pipeline continues. If a step's stage completes exceptionally, the
   * pipeline short-circuits and the returned future completes exceptionally.
   *
   * <pre>
   *   CompletableFuture&lt;Map&lt;String, Object&gt;&gt; fut = AsyncFut.Waterfall(List.of(
   *       acc -&gt; fetchConfigAsync().thenApply(cfg  -&gt; Map.entry("config",  cfg)),
   *       acc -&gt; fetchShardsAsync().thenApply(shs  -&gt; Map.entry("shards",  shs)),
   *       acc -&gt; enrichAsync(acc).thenApply(enr   -&gt; Map.entry("enriched", enr))
   *   ));
   *   fut.thenAccept(acc -&gt; reply.send(buildManifest(acc)));
   * </pre>
   */
  public static CompletableFuture<Map<String, Object>> Waterfall(
      final List<Function<Map<String, Object>, ? extends CompletionStage<Map.Entry<String, Object>>>> steps) {

    return AsyncFut.<Map<String, Object>>futOf(c -> {
      // Maintain our own accumulator outside Asyncc.Waterfall's internal one, so each step's
      // Function<Map, ...> can see prior values without Asyncc exposing its internal map to us.
      final Map<String, Object> accumulator = new ConcurrentHashMap<>();

      final List<NeoWaterfallI.AsyncTask<Object, Throwable>> bridge = new ArrayList<>(steps.size());
      for (final Function<Map<String, Object>, ? extends CompletionStage<Map.Entry<String, Object>>> step : steps) {
        bridge.add((NeoWaterfallI.AsyncTask<Object, Throwable>) taskCb -> {
          // Snapshot the accumulator to hand to the step (defensive copy).
          final Map<String, Object> snapshot = new HashMap<>(accumulator);
          try {
            step.apply(snapshot).whenComplete((entry, err) -> {
              if (err != null) {
                taskCb.done(err);
                return;
              }
              if (entry == null || entry.getKey() == null) {
                // skip — no entry to add
                taskCb.done(null);
                return;
              }
              accumulator.put(entry.getKey(), entry.getValue());
              taskCb.done(null, entry.getKey(), entry.getValue());
            });
          } catch (Throwable t) {
            taskCb.done(t);
          }
        });
      }

      // Asyncc.Waterfall's final callback gives us a HashMap. Wrap once for the future.
      Asyncc.<Object, Throwable>Waterfall(bridge, (err, finalMap) -> {
        if (err != null) c.fail(err);
        else c.success(finalMap == null ? Map.of() : finalMap);
      });
    });
  }

  // ---------------- FilterMap --------------------------------------------

  /**
   * Async map + filter. The {@code mapper} is applied to each element; if the returned stage
   * completes with {@code null}, the element is dropped from the result. Order is preserved
   * across the surviving elements.
   *
   * <pre>
   *   CompletableFuture&lt;List&lt;Profile&gt;&gt; active = AsyncFut.FilterMap(candidateIds,
   *       id -&gt; fetchProfileAsync(id)
   *           .thenApply(p -&gt; p.isActive() ? p : null));
   * </pre>
   */
  public static <T, V> CompletableFuture<List<V>> FilterMap(
      final Iterable<T> input,
      final Function<? super T, ? extends CompletionStage<V>> mapper) {

    return AsyncFut.<List<V>>futOf(c -> {
      final List<T> items = toList(input);
      Asyncc.<V, T, Throwable>FilterMap(items,
          (NeoFilterMapI.IMapper<T, V, Throwable>) (item, inner) -> {
            try {
              mapper.apply(item).whenComplete((v, err) -> {
                if (err != null) {
                  inner.done(err, (V) null);
                  return;
                }
                if (v == null) {
                  // Signal drop: must explicitly mark this slot for discard. Calling
                  // done(null, null) directly would store a null in the result list.
                  inner.discard();
                  inner.done(null, (V) null);
                } else {
                  inner.done(null, v);
                }
              });
            } catch (Throwable t) {
              inner.done(t, (V) null);
            }
          }, c);
    });
  }

  // ---------------- GroupBy ----------------------------------------------

  /**
   * Bucket each element by an async-computed string key. The returned future completes with a
   * {@code Map<String, List<T>>} where each entry is the list of elements that produced that
   * key, in input order within the bucket.
   *
   * <pre>
   *   CompletableFuture&lt;Map&lt;String, List&lt;User&gt;&gt;&gt; byRegion =
   *       AsyncFut.GroupBy(users, u -&gt; resolveRegionAsync(u));
   * </pre>
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public static <T> CompletableFuture<Map<String, List<T>>> GroupBy(
      final Iterable<T> input,
      final Function<? super T, ? extends CompletionStage<String>> keyer) {

    return AsyncFut.<Map<String, List<T>>>futOf(c -> {
      final List<T> items = toList(input);
      // Asyncc.GroupBy's final callback takes Map<String, List<V>>; here V == T.
      final Asyncc.IAsyncCallback<Map<String, List<T>>, Throwable> sink = c;
      Asyncc.<T, T, Throwable>GroupBy(items,
          (NeoGroupByI.IMapper<T, Throwable>) (item, inner) -> {
            try {
              keyer.apply(item).whenComplete((key, err) -> {
                if (err != null) inner.fail(err);
                else inner.success(key);
              });
            } catch (Throwable t) {
              inner.fail(t);
            }
          },
          (Asyncc.IAsyncCallback) sink);
    });
  }

  // ---------------- Whilst / DoWhilst ------------------------------------

  /**
   * Async while-loop. {@code test} is invoked synchronously before each iteration; if true,
   * {@code body} is invoked and its result added to the collected list. Loop terminates the
   * first time {@code test} returns false, or as soon as {@code body} fails. The returned
   * future completes with the list of all per-iteration values.
   *
   * <pre>
   *   AtomicInteger counter = new AtomicInteger();
   *   CompletableFuture&lt;List&lt;Page&gt;&gt; pages = AsyncFut.Whilst(
   *       () -&gt; counter.get() &lt; pageCount,
   *       () -&gt; fetchPageAsync(counter.getAndIncrement()));
   * </pre>
   */
  public static <T> CompletableFuture<List<T>> Whilst(
      final java.util.function.BooleanSupplier test,
      final Supplier<? extends CompletionStage<T>> body) {

    return AsyncFut.<List<T>>futOf(c -> {
      Asyncc.<T, Throwable>Whilst(
          (NeoWhilstI.SyncTruthTest) test::getAsBoolean,
          (NeoWhilstI.AsyncTask<T, Throwable>) taskCb -> {
            try {
              body.get().whenComplete((v, err) -> {
                if (err != null) taskCb.fail(err);
                else taskCb.success(v);
              });
            } catch (Throwable t) {
              taskCb.fail(t);
            }
          },
          c);
    });
  }

  /**
   * Like {@link #Whilst} but runs {@code body} at least once before consulting {@code test}.
   */
  public static <T> CompletableFuture<List<T>> DoWhilst(
      final java.util.function.BooleanSupplier test,
      final Supplier<? extends CompletionStage<T>> body) {

    return AsyncFut.<List<T>>futOf(c -> {
      Asyncc.<T, Throwable>DoWhilst(
          (NeoWhilstI.SyncTruthTest) test::getAsBoolean,
          (NeoWhilstI.AsyncTask<T, Throwable>) taskCb -> {
            try {
              body.get().whenComplete((v, err) -> {
                if (err != null) taskCb.fail(err);
                else taskCb.success(v);
              });
            } catch (Throwable t) {
              taskCb.fail(t);
            }
          },
          c);
    });
  }

  // ---------------- Note on Inject ---------------------------------------

  /*
   * Asyncc.Inject (DAG of named tasks with dependency resolution) intentionally does NOT have
   * an AsyncFut sibling in v0.2.8. The promise model doesn't gracefully express the
   * "task X depends on the result of tasks A and B" semantics that NeoInject.Task provides
   * via its constructor's dependency-name list. Callers who need Inject should use
   * Asyncc.Inject directly and bridge the boundary via WrapFuture.toFuture.
   */

  // ---------------- internals --------------------------------------------

  private static <T> List<Asyncc.AsyncTask<T, Throwable>> toAsyncTasks(
      final List<Supplier<? extends CompletionStage<T>>> tasks) {
    final List<Asyncc.AsyncTask<T, Throwable>> out = new ArrayList<>(tasks.size());
    for (final Supplier<? extends CompletionStage<T>> s : tasks) {
      out.add(c -> {
        try {
          s.get().whenComplete((v, err) -> {
            if (err != null) c.fail(err);
            else c.success(v);
          });
        } catch (Throwable t) {
          c.fail(t);
        }
      });
    }
    return out;
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

  private static <V> CompletionStage<Collection<? extends V>> widenCollectionStage(
      final CompletionStage<? extends Collection<? extends V>> stage) {

    return stage.thenApply((Collection<? extends V> chunk) -> chunk);
  }

  private static <T> List<CompletableFuture<T>> toCompletableFutures(
      final Executor waitExecutor,
      final List<? extends Future<? extends T>> futures) {

    final List<CompletableFuture<T>> stages = new ArrayList<>(futures.size());
    for (final Future<? extends T> future : futures) {
      stages.add(WrapFuture.toCompletableFuture(waitExecutor, future));
    }
    return stages;
  }

  private static <V> CompletableFuture<V> mirrorWithCancellation(
      final CompletableFuture<V> delegate,
      final List<? extends CompletableFuture<?>> children,
      final boolean cancelChildrenWhenDelegateCompletes) {

    final CompletableFuture<V> out = new CompletableFuture<>() {
      @Override
      public boolean cancel(final boolean mayInterruptIfRunning) {
        final boolean cancelled = super.cancel(mayInterruptIfRunning);
        if (cancelled) {
          cancelIncomplete(children, mayInterruptIfRunning);
          delegate.cancel(mayInterruptIfRunning);
        }
        return cancelled;
      }
    };

    delegate.whenComplete((value, err) -> {
      if (cancelChildrenWhenDelegateCompletes) {
        cancelIncomplete(children, true);
      }
      if (err != null) {
        if (err instanceof CancellationException) {
          out.cancel(false);
        } else {
          out.completeExceptionally(err);
        }
      } else {
        out.complete(value);
      }
    });

    return out;
  }

  private static void cancelIncomplete(
      final List<? extends CompletableFuture<?>> futures,
      final boolean mayInterruptIfRunning) {

    for (final CompletableFuture<?> future : futures) {
      if (!future.isDone()) {
        future.cancel(mayInterruptIfRunning);
      }
    }
  }

  private static <T> List<T> toList(final Iterable<T> input) {
    if (input instanceof List<T> list) {
      return list;
    }
    final List<T> out = new ArrayList<>();
    final Iterator<T> it = input.iterator();
    while (it.hasNext()) out.add(it.next());
    return out;
  }
}
