package examples;

import org.ores.async.Asyncc;
import org.ores.async.WrapFuture;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Compile-checked examples for bridging promise-returning APIs into callback combinators.
 */
public final class CompletionStageInteropExample {

  private CompletionStageInteropExample() {}

  public static CompletableFuture<List<String>> lazySeriesStages(final StageClient client) {
    return WrapFuture.toFuture(c ->
        Asyncc.<String, Throwable>Series(List.of(
            WrapFuture.fromStage(client::validateAsync),
            WrapFuture.fromStage(client::persistAsync)
        ), c));
  }

  public interface StageClient {
    CompletionStage<String> validateAsync();

    CompletionStage<String> persistAsync();
  }
}
