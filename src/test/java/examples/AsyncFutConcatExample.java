package examples;

import org.ores.async.AsyncFut;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Compile-checked examples for promise-returning concat helpers.
 */
public final class AsyncFutConcatExample {

  private AsyncFutConcatExample() {}

  public static CompletableFuture<List<String>> fanOutThenFlatten(final OrdersClient client) {
    return AsyncFut.ConcatLimit(
        4,
        List.of("alice", "bob", "carol"),
        client::ordersForUser);
  }

  public static CompletableFuture<List<String>> sequentialFlatten(final OrdersClient client) {
    return AsyncFut.ConcatSeries(
        List.of("validate", "persist"),
        client::stepOutputs);
  }

  public interface OrdersClient {
    CompletionStage<List<String>> ordersForUser(String userId);

    CompletionStage<List<String>> stepOutputs(String step);
  }
}
