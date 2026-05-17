package general;

import org.junit.Test;
import org.ores.async.NeoLock;
import org.ores.async.Unlock;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * Pins that {@link NeoLock} is usable from <em>outside</em> the {@code org.ores.async}
 * package — i.e. that {@link Unlock} is {@code public} so consumers can name the token type
 * in their lambdas, fields, and method signatures.
 *
 * <p>Before 0.2.2 this would fail to compile: the test class is in package {@code general},
 * not {@code org.ores.async}, and the package-private {@code Unlock} could not be imported.
 * That made {@code NeoLock.acquire(...)} effectively unreachable from any downstream
 * Java consumer.
 */
public class NeoLockExternalUsageTest {

  @Test(timeout = 10_000)
  public void neoLockAcquireUnlockTokenIsAccessibleFromOutsidePackage() throws Exception {

    final NeoLock lock = new NeoLock("external-usage-test");
    final AtomicInteger holdersAtOnce = new AtomicInteger();
    final AtomicInteger peak = new AtomicInteger();

    final int contenders = 50;
    final CompletableFuture<?>[] dones = new CompletableFuture<?>[contenders];

    for (int i = 0; i < contenders; i++) {
      final CompletableFuture<Void> done = new CompletableFuture<>();
      dones[i] = done;
      lock.acquire((err, unlock) -> {
        // The lambda parameter `unlock` is of type Unlock — must be importable / nameable
        // from outside `org.ores.async`. Pre-0.2.2 this compiled because Java's generics
        // erasure tolerates references to package-private types in some contexts, but the
        // .releaseLock() call below failed to resolve.
        final Unlock heldToken = unlock;
        final int now = holdersAtOnce.incrementAndGet();
        peak.accumulateAndGet(now, Math::max);
        try {
          Thread.sleep(1);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        } finally {
          holdersAtOnce.decrementAndGet();
          heldToken.releaseLock();
        }
        done.complete(null);
      });
    }

    CompletableFuture.allOf(dones).get(5, TimeUnit.SECONDS);
    assertEquals("NeoLock must hold mutual exclusion across contenders", 1, peak.get());
  }
}
