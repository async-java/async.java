package org.ores.async;

import java.util.*;

import static org.ores.async.NeoWhilstI.AsyncCallback;
import static org.ores.async.NeoWhilstI.AsyncTruthTest;
import static org.ores.async.NeoWhilstI.SyncTruthTest;
import static org.ores.async.NeoWhilstI.AsyncTask;

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
