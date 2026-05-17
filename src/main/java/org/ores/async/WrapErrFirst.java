package org.ores.async;

import java.util.function.Consumer;

/**
 * Helpers that adapt error-first callbacks into more concise call sites.
 *
 * <p>The library uses Node.js-style error-first callbacks throughout &mdash; every continuation
 * receives {@code (err, value)} and the caller checks {@code err} first. That convention is
 * stable, well-understood, and composes cleanly, but it does mean every callback site has the
 * same five-line error-checking preamble:
 *
 * <pre>
 *   Asyncc.Parallel(tasks, (err, results) -&gt; {
 *       if (err != null) { handleError(err); return; }
 *       // ...real work...
 *   });
 * </pre>
 *
 * <p>{@code WrapErrFirst.wrap(...)} lets you write just the value handler and either throw on any
 * unhandled error or pass a separate error consumer:
 *
 * <pre>
 *   import static org.ores.async.WrapErrFirst.wrap;
 *
 *   // (a) throw-on-error: short and loud. Wraps the error in a RuntimeException; useful when
 *   //     the surrounding context (Vert.x verticle, Akka actor, etc.) already has an
 *   //     uncaught-exception handler.
 *   Asyncc.Parallel(tasks, wrap(results -&gt; {
 *       var scored = score(req, results.get(0), results.get(1));
 *       reply.send(serialize(scored));
 *   }));
 *
 *   // (b) explicit error handler: cleaner than the if/else when the two paths are short.
 *   Asyncc.Parallel(tasks, wrap(
 *       results -&gt; reply.send(serialize(score(req, results.get(0), results.get(1)))),
 *       err     -&gt; reply.error(err)
 *   ));
 * </pre>
 *
 * <h3>Why not "error-back" / promise-style chaining instead?</h3>
 *
 * <p>async.java cannot easily be retrofitted to support promise-style "error-back" callbacks
 * (separate {@code onSuccess} / {@code onError} channels) because the library's at-most-once
 * contract, short-circuit semantics, and 17 combinators are all designed around a single
 * error-first signature. Re-typing every combinator to take two callbacks would double the
 * public surface, double the test matrix, and break every existing caller. {@code WrapErrFirst}
 * keeps the canonical error-first signature internally but lets call sites read like promise
 * chains where that ergonomics matters.
 *
 * <p>The convention used in this library's docs: the continuation parameter is named {@code c}
 * (for <em>continuation</em>). {@code wrap(...)} produces a continuation just like any other.
 *
 * @since 0.2.4
 */
public final class WrapErrFirst {

  private WrapErrFirst() {}

  /**
   * Wrap a value-only consumer into an error-first {@link Asyncc.IAsyncCallback}. If the
   * continuation is fired with a non-null error, this wrapper throws a {@link RuntimeException}
   * carrying the cause (the error itself, if it is a {@link Throwable}; otherwise its
   * {@code toString()}). If fired with a null error, calls {@code onSuccess.accept(value)}.
   *
   * <p>Use this when the caller does not need to handle the error inline and is happy for it to
   * propagate via the surrounding runtime's uncaught-exception handler. If you want the error
   * to be visible in your own logging or to be turned into an explicit response, use the
   * {@linkplain #wrap(Consumer, Consumer) two-argument form} instead.
   *
   * @param <V> value type
   * @param <E> error type
   * @param onSuccess consumer invoked with the successful value; never invoked when an error
   *                  is present
   * @return an {@code IAsyncCallback} suitable for any async.java combinator
   */
  public static <V, E> Asyncc.IAsyncCallback<V, E> wrap(final Consumer<V> onSuccess) {
    return (e, v) -> {
      if (e != null) {
        if (e instanceof Throwable t) {
          throw new RuntimeException("async.java task failed (unhandled error)", t);
        }
        throw new RuntimeException("async.java task failed (unhandled error): " + e);
      }
      onSuccess.accept(v);
    };
  }

  /**
   * Wrap two value-only consumers into an error-first {@link Asyncc.IAsyncCallback}. Exactly
   * one of {@code onError} or {@code onSuccess} is invoked per continuation fire.
   *
   * <p>This is the recommended form for production code: it never throws on its own (any throw
   * comes from the caller's consumer body) and keeps the two branches visually parallel.
   *
   * @param <V> value type
   * @param <E> error type
   * @param onSuccess consumer invoked when the continuation fires with {@code err == null}
   * @param onError   consumer invoked when the continuation fires with a non-null error
   * @return an {@code IAsyncCallback} suitable for any async.java combinator
   */
  public static <V, E> Asyncc.IAsyncCallback<V, E> wrap(
      final Consumer<V> onSuccess,
      final Consumer<E> onError) {
    return (e, v) -> {
      if (e != null) {
        onError.accept(e);
      } else {
        onSuccess.accept(v);
      }
    };
  }
}
