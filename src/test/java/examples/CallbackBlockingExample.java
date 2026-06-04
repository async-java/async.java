package examples;

import org.ores.async.Asyncc;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * Compile-checked examples for callback-style virtual-thread blocking helpers.
 */
public final class CallbackBlockingExample {

  private CallbackBlockingExample() {}

  public static CompletableFuture<List<String>> seriesBlockingCallback() {
    final CompletableFuture<List<String>> result = new CompletableFuture<>();
    Asyncc.SeriesBlocking(List.of(
        (Callable<String>) () -> blockingStep("validate"),
        () -> blockingStep("write"),
        () -> blockingStep("publish")),
        (err, values) -> {
          if (err != null) {
            result.completeExceptionally(err);
          } else {
            result.complete(values);
          }
        });
    return result;
  }

  public static CompletableFuture<String> raceBlockingCallback() {
    final CompletableFuture<String> result = new CompletableFuture<>();
    Asyncc.RaceBlocking(List.of(
        (Callable<String>) () -> {
          Thread.sleep(25);
          return blockingStep("primary");
        },
        () -> blockingStep("replica")),
        (err, value) -> {
          if (err != null) {
            result.completeExceptionally(err);
          } else {
            result.complete(value);
          }
        });
    return result;
  }

  private static String blockingStep(final String name) throws InterruptedException {
    Thread.sleep(10);
    return "done-" + name;
  }
}
