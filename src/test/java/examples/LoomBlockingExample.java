package examples;

import org.ores.async.AsyncLoom;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Compile-checked examples for Java 21+ virtual-thread blocking helpers.
 */
public final class LoomBlockingExample {

  private LoomBlockingExample() {}

  public static CompletableFuture<List<String>> parallelBlocking() {
    return AsyncLoom.ParallelBlocking(List.of(
        () -> blockingRead("profile"),
        () -> blockingRead("permissions")));
  }

  public static CompletableFuture<List<String>> seriesBlocking() {
    return AsyncLoom.SeriesBlocking(List.of(
        () -> blockingRead("validate"),
        () -> blockingRead("persist"),
        () -> blockingRead("notify")));
  }

  public static CompletableFuture<String> raceBlocking() {
    return AsyncLoom.RaceBlocking(List.of(
        () -> {
          Thread.sleep(25);
          return blockingRead("primary");
        },
        () -> blockingRead("replica")));
  }

  public static CompletableFuture<Integer> reduceBlocking() {
    return AsyncLoom.ReduceBlocking(
        List.of("a", "bb", "ccc"),
        0,
        (acc, value) -> acc + value.length());
  }

  public static CompletableFuture<List<Path>> concatBlocking() {
    return AsyncLoom.ConcatBlocking(
        List.of("tenant-a", "tenant-b"),
        tenant -> List.of(
            Path.of("/data", tenant, "events.log"),
            Path.of("/data", tenant, "metrics.log")));
  }

  private static String blockingRead(final String key) throws InterruptedException {
    Thread.sleep(10);
    return "read-" + key;
  }
}
