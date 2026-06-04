package examples;

import org.ores.async.AsyncFut;
import org.ores.async.AsyncLoom;
import org.ores.async.Asyncc;
import org.ores.async.WrapFuture;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Compile-checked examples for adapting plain JDK {@link Future} values.
 */
public final class FutureInteropExample {

  private FutureInteropExample() {}

  /**
   * Use a virtual-thread wait executor on JDK 21+, with a cached platform-thread fallback for
   * older JDKs. Callers own the returned executor and should shut it down.
   */
  public static ExecutorService defaultWaitExecutor() {
    if (AsyncLoom.isSupported()) {
      return AsyncLoom.newVirtualThreadPerTaskExecutor();
    }
    return Executors.newCachedThreadPool();
  }

  public static CompletableFuture<List<String>> parallelFutures(
      final ExecutorService legacyPool,
      final ExecutorService waiters) {

    final List<Future<String>> futures = List.of(
        legacyPool.submit(() -> lookup("primary")),
        legacyPool.submit(() -> lookup("secondary")));

    return AsyncFut.ParallelFutures(waiters, futures);
  }

  public static CompletableFuture<List<String>> callbackBridge(
      final ExecutorService legacyPool,
      final ExecutorService waiters) {

    return WrapFuture.toFuture(c ->
        Asyncc.Parallel(List.of(
            WrapFuture.fromFuture(waiters, legacyPool.submit(() -> lookup("cache"))),
            WrapFuture.fromFuture(waiters, legacyPool.submit(() -> lookup("database")))),
            c));
  }

  private static String lookup(final String source) {
    return "value-from-" + source;
  }
}
