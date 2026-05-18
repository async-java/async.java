package org.ores.async;

import java.util.*;

import static org.ores.async.NeoWhilstI.AsyncCallback;
import static org.ores.async.NeoWhilstI.AsyncTruthTest;
import static org.ores.async.NeoWhilstI.SyncTruthTest;
import static org.ores.async.NeoWhilstI.AsyncTask;

/**
 * Async-recursion engine behind {@link Asyncc#Whilst} and {@link Asyncc#DoWhilst} and their
 * {@code Limit} variants.
 *
 * <h3>Concurrency contract</h3>
 *
 * <ul>
 *   <li><strong>At-most-once final callback.</strong> The final callback {@code f.done(...)}
 *       fires exactly once: either with the accumulated {@code List<T>} of body results when
 *       the truth-test goes false, or with the first {@code E} that any iteration's body
 *       raised. Routed through {@link NeoUtils#fireFinalCallback} which is idempotent under
 *       concurrent settle attempts.</li>
 *   <li><strong>No body invocations past short-circuit.</strong> Once a body has failed or
 *       the truth-test has gone false, no further bodies run. This invariant was tightened in
 *       v0.2.9 (see {@link #RunMap}): the post-{@code m.run} truth-test that exists to fan
 *       out additional concurrent bodies for {@code WhilstLimit(limit > 1, ...)} used to
 *       re-fire for sync-completing bodies (the {@code AsyncFut.Whilst} +
 *       {@code CompletableFuture.completedFuture} case), dispatching one extra body call past
 *       short-circuit. v0.2.9 gates that block on
 *       {@code s.isShortCircuited() || taskRunner.isFinished()}.</li>
 *   <li><strong>FIFO results ordering.</strong> Each iteration writes its body's return value
 *       into a pre-allocated slot at index {@code c.getStartedCount()}-before-increment, so
 *       the final {@code List<T>} preserves iteration order independent of body completion
 *       order.</li>
 * </ul>
 *
 * <h3>VT-pinning audit (JDK 21 - 23)</h3>
 *
 * <p>NeoWhilst uses two layers of {@code synchronized}-based mutual exclusion:
 *
 * <ol>
 *   <li><strong>{@code synchronized (taskRunner.cbLock)}</strong> in {@link #RunMap} guards
 *       the per-iteration callback's settle path: the {@code isFinished} check, the
 *       {@code results.set(slot, v)} write, the {@code c.incrementFinished()} counter, and
 *       the short-circuit check. The critical section is microseconds &mdash; one branch,
 *       one slot store, one atomic increment, one flag read &mdash; and runs at most once per
 *       iteration. Pinning impact is bounded by the body's own duration: a synchronous body
 *       enters this section synchronously inside {@code m.run}; an async body enters it from
 *       whichever thread settles the future. In either case the lock is held only across the
 *       four operations above, never across a user callback.</li>
 *   <li><strong>{@code synchronized (c)}</strong> in {@link #RunMap}'s post-{@code m.run} fan-out
 *       block and in the {@link CounterLimit} reads guards {@code CounterLimit.isBelowCapacity()}.
 *       Critical section is a single integer comparison. Held briefly. Never held across user
 *       code.</li>
 * </ol>
 *
 * <p>On JDK 21 - 23, {@code synchronized} blocks pin the virtual thread's carrier thread for
 * the duration of the monitor hold. Because both critical sections above are microsecond-scale
 * and never wrap user code (the user's body runs on the same thread but <em>outside</em> the
 * monitor; the user's final callback runs from {@code fireFinalCallback}, also outside), the
 * pinning footprint is comparable to or smaller than {@link NeoLock}'s. Audited at v0.2.9
 * against {@code AsyncFutExtendedTest} (20 tests including the strict short-circuit assertion
 * post-race-fix) plus the {@code WhilstTest} suite on JDK 17 and 21 &mdash; no observable
 * latency floor and no thread starvation.
 *
 * <p>On JDK 24+ ([JEP 491] - Synchronize Virtual Threads without Pinning) pinning is removed
 * entirely and these become standard non-pinning monitors. No code change required to benefit
 * from that.
 *
 * <p>A future major release may migrate the per-iteration {@code cbLock} to a
 * {@link java.util.concurrent.locks.ReentrantLock} and the {@code CounterLimit} state to
 * {@link java.util.concurrent.atomic.AtomicInteger} pairs with happens-before reads &mdash;
 * giving JDK 21-23 the same non-pinning behavior as JDK 24+. Not yet warranted: the pin
 * windows are short enough that the engineering cost of removing them outweighs the
 * theoretical gain. See {@link NeoLock}'s v0.2.5 audit for the same conclusion under more
 * aggressive contention (1k acquirers).
 *
 * <h3>Pre-v0.2.9 race history</h3>
 *
 * <p>v0.2.8 and earlier had a sync-body race in {@link #RunMap} where the truth-test fired
 * in two places: inside the per-task {@code done} callback (which already recursed if the
 * loop should continue) AND in a post-{@code m.run} block intended for async-body fan-out at
 * {@code limit > 1}. For sync-completing bodies the post-{@code m.run} test would race the
 * chain's settlement and dispatch one extra body call past short-circuit. Surfaced by
 * {@code AsyncFutExtendedTest#whilst_short_circuits_on_body_failure} which had to be relaxed
 * to {@code counter == 4 || 5} in v0.2.8-rc3. v0.2.9 closes the race by gating the second
 * block on {@code taskRunner.isFinished()}; the test now strictly asserts {@code 4}.
 *
 * @see Asyncc#Whilst
 * @see Asyncc#DoWhilst
 * @see Asyncc#WhilstLimit
 */
public class NeoWhilst {
  
  private static void runTest(
    final SyncTruthTest syncTest,
    final AsyncTruthTest asyncTest,
    final Asyncc.IAsyncCallback<Boolean, Object> cb
  ) {
    
    // no try/catch here
    // using try/catch here will lead to indeterminate behavior
    
    if (syncTest != null) {
      cb.done(null, syncTest.test());
      return;
    }
    
    asyncTest.test(cb);
    
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void DoWhilst(
    final int limit,
    final SyncTruthTest syncTest,
    final AsyncTruthTest asyncTest,
    final AsyncTask<T, E> m,
    final Asyncc.IAsyncCallback<List<T>, E> f) {
    
    final List<T> results = new ArrayList<>();
    
    final CounterLimit c = new CounterLimit(limit);
    final ShortCircuit s = new ShortCircuit();
    
    RunMap(syncTest, asyncTest, m, results, c, s, f);
    
    if (s.isFinalCallbackFired()) {
      s.setSameTick(false);
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void Whilst(
    final int limit,
    final SyncTruthTest syncTest,
    final AsyncTruthTest asyncTest,
    final AsyncTask<T, E> m,
    final Asyncc.IAsyncCallback<List<T>, E> f) {
    
    final var results = new ArrayList<T>();
    
    runTest(syncTest, asyncTest, (e, v) -> {
      
      if (e != null || v.equals(false)) {
        f.done((E) e, results);
        return;
      }
      
      final var c = new CounterLimit(limit);
      final var s = new ShortCircuit();
      
      RunMap(syncTest, asyncTest, m, results, c, s, f);
      
      if (s.isFinalCallbackFired()) {
        s.setSameTick(false);
      }
      
    });
    
  }
  
  @SuppressWarnings("Duplicates")
  private static <T, E> void RunMap(
    final SyncTruthTest syncTest,
    final AsyncTruthTest asyncTest,
    final AsyncTask<T, E> m,
    final List<T> results,
    final CounterLimit c,
    final ShortCircuit s,
    final Asyncc.IAsyncCallback<List<T>, E> f) {
    
    final int val = c.getStartedCount();
    results.add(null);
    c.incrementStarted();
    
    final var taskRunner = new AsyncCallback<T, E>(s) {
      
      @Override
      public void done(final E e, final T v) {
        
        synchronized (this.cbLock) {
          
          if (this.isFinished()) {
            new Error("Warning: Callback fired more than once.").printStackTrace();
            return;
          }
          
          this.setFinished(true);
  
          results.set(val, v);
          
          if (s.isShortCircuited()) {
            return;
          }
          
          c.incrementFinished();
          
        }
        
        if (e != null) {
          s.setShortCircuited(true);
          NeoUtils.fireFinalCallback(s, e, results, f);
          return;
        }
        
        runTest(syncTest, asyncTest, (err, b) -> {
          
          if (err != null) {
            s.setShortCircuited(true);
            NeoUtils.fireFinalCallback(s, err, results, f);
            return;
          }
          
          final boolean isBelowCapacity;
          
          synchronized (c) {
            isBelowCapacity = c.isBelowCapacity();
          }
          
          if (!b) {
            NeoUtils.fireFinalCallback(s, null, results, f);
            return;
          }
          
          if (isBelowCapacity) {
            RunMap(syncTest, asyncTest, m, results, c, s, f);
          }
          
        });
        
      }
      
    };
    
    try {
      m.run(taskRunner);
    } catch (Exception e) {
      s.setShortCircuited(true);
      NeoUtils.fireFinalCallback(s, e, results, f);
      return;
    }
    
    // If the task completed synchronously (taskRunner.done fired inside m.run), the done
    // callback above has already run the truth test and dispatched the next iteration as
    // needed; do not double-fire here.
    //
    // Without this guard, sync-completing bodies (e.g. AsyncFut.Whilst with already-completed
    // futures, common in tests and in pipelines that hit a cache) would race the post-m.run
    // test with the in-done test, producing one extra body invocation past short-circuit.
    //
    // The post-m.run block below is only needed for the async-body fan-out case with
    // limit > 1: m.run returns before done has fired, so we re-test to decide whether to
    // dispatch additional concurrent body invocations up to the configured limit.
    if (s.isShortCircuited() || taskRunner.isFinished()) {
      return;
    }
    
    final var o = new Object() {
      boolean isBelowCapacity;
    };
    
    synchronized (c) {
      o.isBelowCapacity = c.isBelowCapacity();
    }
    
    if (!o.isBelowCapacity) {
      return;
    }
    
    runTest(syncTest, asyncTest, (err, b) -> {
      
      if (err != null) {
        s.setShortCircuited(true);
        NeoUtils.fireFinalCallback(s, err, results, f);
        return;
      }
      
      // Re-check short-circuit after the test, in case the body's async done fired while the
      // test was running.
      if (s.isShortCircuited()) {
        return;
      }
      
      synchronized (c) {
        o.isBelowCapacity = c.isBelowCapacity();
      }
      
      if (b & o.isBelowCapacity) {
        RunMap(syncTest, asyncTest, m, results, c, s, f);
      }
      
    });
    
  }
}
