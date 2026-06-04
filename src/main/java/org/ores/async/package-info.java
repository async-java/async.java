/**
 * <h2>async.java &mdash; callback-based async control flow for the JVM</h2>
 *
 * <p>{@code async.java} is a Java port of the Node.js
 * <a href="https://github.com/caolan/async">async</a> library. It provides a small set of
 * <em>combinators</em> &mdash; {@code Parallel}, {@code Series}, {@code Waterfall}, {@code Race},
 * {@code Map}, {@code Reduce}, {@code Each}, {@code Times}, {@code FilterMap}, {@code GroupBy},
 * {@code Concat}, {@code Inject}, {@code Whilst}, {@code DoWhilst} &mdash; plus a coordination
 * pair, {@link org.ores.async.NeoQueue} (bounded async work queue) and
 * {@link org.ores.async.NeoLock} (async mutex).
 *
 * <p>All combinators share a single shape:
 *
 * <pre>
 *   Asyncc.&lt;combinator&gt;(tasks, finalCallback);
 * </pre>
 *
 * where {@code finalCallback} is an error-first callback {@code (err, value) -&gt; ...}. The
 * library guarantees that the final callback fires <strong>at most once</strong>, regardless of
 * how many tasks succeed, fail, double-fire, or throw synchronously.
 *
 * <h3>The {@code c} convention</h3>
 *
 * <p>Code examples in this library consistently name the continuation parameter {@code c}, short
 * for <em>continuation</em>. The continuation receives the result of an async step and is what
 * happens next &mdash; either the final callback of the combinator, or the next inner step.
 * Existing code using {@code cb} or any other name works unchanged; this is purely a
 * documentation convention.
 *
 * <p>Each continuation can be fired three ways:
 *
 * <pre>
 *   c.done(null, value);   // canonical error-first form
 *   c.success(value);      // shorthand for done(null, value)     — v0.2.4+
 *   c.fail(err);           // shorthand for done(err, null)       — v0.2.4+
 * </pre>
 *
 * <h3>30-second example</h3>
 *
 * <pre>
 *   var exec = Executors.newVirtualThreadPerTaskExecutor();
 *
 *   var tasks = List.&lt;Asyncc.AsyncTask&lt;String, Throwable&gt;&gt;of(
 *       c -&gt; exec.submit(() -&gt; c.success(fetchA())),
 *       c -&gt; exec.submit(() -&gt; c.success(fetchB())),
 *       c -&gt; exec.submit(() -&gt; c.success(fetchC()))
 *   );
 *
 *   Asyncc.Parallel(tasks, (err, results) -&gt; {
 *       if (err != null) { log.error("at least one failed", err); return; }
 *       reply.send(combine(results.get(0), results.get(1), results.get(2)));
 *   });
 * </pre>
 *
 * <p>{@code results} preserves the order of the input list. The callback fires once when the
 * last task completes &mdash; or once with {@code err} set the first time any task fails (other
 * tasks are short-circuited via {@link org.ores.async.ShortCircuit}).
 *
 * <h3>Skipping the error-check boilerplate</h3>
 *
 * <p>For call sites that do not want to handle the error inline, wrap a value-only consumer with
 * {@link org.ores.async.WrapErrFirst#wrap(java.util.function.Consumer)}:
 *
 * <pre>
 *   import static org.ores.async.WrapErrFirst.wrap;
 *
 *   Asyncc.Parallel(tasks, wrap(results -&gt; {
 *       reply.send(combine(results.get(0), results.get(1), results.get(2)));
 *   }));
 * </pre>
 *
 * <p>The single-arg form throws on any unhandled error (carrying the original {@code Throwable}
 * as the cause when available). The two-arg form {@code wrap(onSuccess, onError)} keeps both
 * branches explicit.
 *
 * <h3>Composition</h3>
 *
 * <p>Combinators nest because they all use the same callback contract. A Waterfall wrapping a
 * Map wrapping a Parallel is a perfectly normal pipeline:
 *
 * <pre>
 *   Asyncc.Waterfall(List.of(
 *       c -&gt; fetchPage(url, c),
 *       (html, c) -&gt; c.success(extractLinks(html)),
 *       (links, c) -&gt; Asyncc.Map(links, (link, inner) -&gt; {
 *           Asyncc.Parallel(List.of(
 *               c2 -&gt; exec.submit(() -&gt; c2.success(headOk(link))),
 *               c2 -&gt; exec.submit(() -&gt; c2.success(classify(link)))
 *           ), (err, pair) -&gt; inner.done(err, pair));
 *       }, c)
 *   ), (err, perLinkData) -&gt; {
 *       // ...consume the per-link classification results...
 *   });
 * </pre>
 *
 * <p>See <a href="https://async-java.github.io/examples/">async-java.github.io/examples</a> for
 * larger composition examples.
 *
 * <h3>Project Loom</h3>
 *
 * <p>async.java's default callback combinators do not own a thread pool: tasks run on whichever
 * thread invokes the continuation. For blocking work on Java 21+, use {@link org.ores.async.AsyncLoom}
 * to run {@link java.util.concurrent.Callable} tasks on virtual threads while preserving the
 * same orchestration semantics:
 *
 * <pre>
 *   CompletableFuture&lt;List&lt;User&gt;&gt; users = AsyncLoom.ParallelBlocking(List.of(
 *       () -&gt; jdbc.fetchUser("a"),
 *       () -&gt; jdbc.fetchUser("b")
 *   ));
 *
 *   Asyncc.SeriesBlocking(List.of(
 *       () -&gt; migrateStep1(),
 *       () -&gt; migrateStep2()
 *   ), (err, results) -&gt; {
 *       if (err != null) { rollback(err); return; }
 *       commit(results);
 *   });
 * </pre>
 *
 * <p>For legacy APIs that return plain {@link java.util.concurrent.Future}, use
 * {@link org.ores.async.WrapFuture#fromFuture(java.util.concurrent.Executor, java.util.concurrent.Future)}
 * or {@link org.ores.async.AsyncFut#ParallelFutures(java.util.concurrent.Executor, java.util.List)}.
 * async.java calls {@code Future.get()} inside the adapter on the executor you provide.
 * For promise-returning APIs, use {@link org.ores.async.WrapFuture#fromStage(java.util.concurrent.CompletionStage)}
 * for already-started work and {@link org.ores.async.WrapFuture#fromStage(java.util.function.Supplier)}
 * when {@code Series}, {@code ParallelLimit}, or another bounded combinator should decide when
 * the stage is created. Promise-returning collection helpers such as
 * {@link org.ores.async.AsyncFut#ConcatLimit(int, java.lang.Iterable, java.util.function.Function)}
 * preserve async.java's bounded fan-out while returning {@link java.util.concurrent.CompletableFuture}.
 *
 * <h3>Concurrency contract</h3>
 *
 * <p>Under concurrent task completion, the library guarantees:
 * <ul>
 *   <li><strong>at-most-once</strong> final callback &mdash; dedup-guarded via
 *       {@link org.ores.async.NeoUtils#fireFinalCallback};</li>
 *   <li><strong>result-slot visibility</strong> &mdash; per-index slot writes happen-before the
 *       atomic counter increment that releases the final callback (fixed in v0.2.2);</li>
 *   <li><strong>at-most-once short-circuit</strong> &mdash; the first error wins and other tasks'
 *       results are discarded;</li>
 *   <li><strong>volatile / atomic</strong> internal counters &mdash; lost-update races have
 *       reproducer tests pinning the fix ({@code CounterLimitRaceTest}).</li>
 * </ul>
 *
 * <p>If you can break any of these in production, it is a bug. File at
 * <a href="https://github.com/async-java/async.java/issues">github.com/async-java/async.java/issues</a>.
 *
 * @see org.ores.async.Asyncc
 * @see org.ores.async.AsyncFut
 * @see org.ores.async.AsyncLoom
 * @see org.ores.async.WrapFuture
 * @see org.ores.async.NeoQueue
 * @see org.ores.async.NeoLock
 */
package org.ores.async;
