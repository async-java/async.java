package general;

import org.junit.Test;
import org.ores.async.NeoQueue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pins the lifecycle-hook semantics of {@link NeoQueue}: {@code saturated}, {@code unsaturated},
 * and {@code drain}.
 *
 * <p>The semantics as of v0.2.5:
 *
 * <ul>
 *   <li><strong>{@code saturated}</strong> &mdash; fires when an {@code incrementStarted}
 *       causes in-flight to reach the concurrency cap. Gated by an {@code isSaturated} flag so
 *       it fires once per saturation event, not on every dispatch while at cap.</li>
 *   <li><strong>{@code unsaturated}</strong> &mdash; fires at task-completion time when
 *       <em>the pending queue is empty</em> AND we were saturated. Pairs with {@code saturated}
 *       to bracket "the queue is backlogged" intervals: it does <em>not</em> fire every time
 *       in-flight drops below the cap if there is still work pending. Use this as a "no longer
 *       backlogged" signal, not as a "below cap right now" signal.</li>
 *   <li><strong>{@code drain}</strong> &mdash; fires at task-completion time when there is no
 *       pending work AND all started tasks have finished. Gated so it fires exactly once per
 *       drain transition. A subsequent {@code push} resets the gate.</li>
 * </ul>
 *
 * <p>These tests pin the current behaviour so v0.3+ refactors do not silently change the
 * lifecycle event semantics.
 */
public class NeoQueueLifecycleTest {

  private static final int TIMEOUT_MS = 30_000;

  @Test(timeout = TIMEOUT_MS)
  public void saturated_fires_once_per_backlog_episode() throws Exception {
    final int cap = 4;
    final int n = 20;
    final ExecutorService userExec = Executors.newFixedThreadPool(8);
    try {
      final AtomicInteger saturatedFires = new AtomicInteger();
      final AtomicInteger unsaturatedFires = new AtomicInteger();
      final CountDownLatch drained = new CountDownLatch(1);

      final NeoQueue<Integer, Integer> q = new NeoQueue<>(cap, (task, c) -> {
        userExec.submit(() -> {
          try { Thread.sleep(5); } catch (InterruptedException ie) { /* */ }
          c.success(task.getValue());
        });
      });

      q.onSaturated(queue -> saturatedFires.incrementAndGet());
      q.onUnsaturated(queue -> unsaturatedFires.incrementAndGet());
      q.onDrain(queue -> drained.countDown());

      // Burst push - we cross the cap once and then drain.
      for (int i = 0; i < n; i++) {
        q.push(new NeoQueue.Task<>(i));
      }

      assertTrue("drain should fire", drained.await(15, TimeUnit.SECONDS));
      assertEquals("saturated fires exactly once for this backlog episode", 1, saturatedFires.get());
      assertEquals("unsaturated fires exactly once for this backlog episode", 1, unsaturatedFires.get());
    } finally {
      userExec.shutdown();
      userExec.awaitTermination(2, TimeUnit.SECONDS);
    }
  }

  @Test(timeout = TIMEOUT_MS)
  public void drain_fires_exactly_once_for_one_burst() throws Exception {
    final int cap = 2;
    final int n = 8;
    final ExecutorService userExec = Executors.newFixedThreadPool(4);
    try {
      final AtomicInteger drainFires = new AtomicInteger();
      final CountDownLatch firstDrain = new CountDownLatch(1);

      final NeoQueue<Integer, Integer> q = new NeoQueue<>(cap, (task, c) -> {
        userExec.submit(() -> {
          try { Thread.sleep(5); } catch (InterruptedException ie) { /* */ }
          c.success(task.getValue());
        });
      });

      q.onDrain(queue -> {
        drainFires.incrementAndGet();
        firstDrain.countDown();
      });

      for (int i = 0; i < n; i++) {
        q.push(new NeoQueue.Task<>(i));
      }

      assertTrue("first drain should fire", firstDrain.await(15, TimeUnit.SECONDS));
      // Give the queue extra time to (incorrectly) fire drain again
      Thread.sleep(200);
      assertEquals("drain fires exactly once for one burst", 1, drainFires.get());
    } finally {
      userExec.shutdown();
      userExec.awaitTermination(2, TimeUnit.SECONDS);
    }
  }

  @Test(timeout = TIMEOUT_MS)
  public void drain_fires_again_for_subsequent_burst() throws Exception {
    final int cap = 2;
    final ExecutorService userExec = Executors.newFixedThreadPool(4);
    try {
      final AtomicInteger drainFires = new AtomicInteger();

      final NeoQueue<Integer, Integer> q = new NeoQueue<>(cap, (task, c) -> {
        userExec.submit(() -> {
          try { Thread.sleep(3); } catch (InterruptedException ie) { /* */ }
          c.success(task.getValue());
        });
      });

      q.onDrain(queue -> drainFires.incrementAndGet());

      // First burst
      for (int i = 0; i < 4; i++) {
        q.push(new NeoQueue.Task<>(i));
      }

      // Wait for first drain
      final long deadline = System.currentTimeMillis() + 10_000;
      while (drainFires.get() < 1 && System.currentTimeMillis() < deadline) {
        Thread.sleep(20);
      }
      assertEquals("first burst drains once", 1, drainFires.get());

      // Second burst
      for (int i = 0; i < 4; i++) {
        q.push(new NeoQueue.Task<>(100 + i));
      }

      while (drainFires.get() < 2 && System.currentTimeMillis() < deadline) {
        Thread.sleep(20);
      }
      assertEquals("second burst drains again", 2, drainFires.get());

      Thread.sleep(100);
      assertEquals("still exactly two drain fires", 2, drainFires.get());
    } finally {
      userExec.shutdown();
      userExec.awaitTermination(2, TimeUnit.SECONDS);
    }
  }

  @Test(timeout = TIMEOUT_MS)
  public void saturated_does_not_fire_when_below_cap() throws Exception {
    final int cap = 8;
    final ExecutorService userExec = Executors.newFixedThreadPool(4);
    try {
      final AtomicInteger saturatedFires = new AtomicInteger();
      final CountDownLatch drained = new CountDownLatch(1);

      final NeoQueue<Integer, Integer> q = new NeoQueue<>(cap, (task, c) -> {
        userExec.submit(() -> {
          try { Thread.sleep(2); } catch (InterruptedException ie) { /* */ }
          c.success(task.getValue());
        });
      });

      q.onSaturated(queue -> saturatedFires.incrementAndGet());
      q.onDrain(queue -> drained.countDown());

      // Push fewer tasks than the cap. The queue should never reach saturation.
      for (int i = 0; i < 3; i++) {
        q.push(new NeoQueue.Task<>(i));
      }

      assertTrue("drain should fire", drained.await(10, TimeUnit.SECONDS));
      assertEquals("saturated must not fire when below cap", 0, saturatedFires.get());
    } finally {
      userExec.shutdown();
      userExec.awaitTermination(2, TimeUnit.SECONDS);
    }
  }
}
