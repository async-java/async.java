package org.ores.async;

import java.util.*;

import static org.ores.async.NeoUtils.fireFinalCallback;

class NeoParallel {
  
  public static class ParallelRunner<T, E> extends Asyncc.AsyncCallback<T, E> {
    
    final Iterator<Asyncc.AsyncTask<T, E>> iterator;
    final CounterLimit c;
    final int val;
    final Asyncc.IAsyncCallback<List<T>, E> f;
    final List<T> results;
    
     ParallelRunner(
      Iterator<Asyncc.AsyncTask<T, E>> iterator,
      CounterLimit c,
      ShortCircuit s,
      int val,
      List<T> results,
      Asyncc.IAsyncCallback<List<T>, E> f) {
      
      super(s);
      this.iterator = iterator;
      this.c = c;
      this.val = val;
      this.f = f;
      this.results = results;
    }
    
    @Override
    public void done(E e, T v) {
      
      synchronized (this.cbLock) {
        
        if (this.isFinished()) {
          new Error("Warning: Callback fired more than once.").printStackTrace();
          return;
        }
        
        this.setFinished(true);
        
        if (this.s.isShortCircuited()) {
          return;
        }
        
        if (e != null) {
          s.setShortCircuited(true);
          NeoUtils.fireFinalCallback(s, e, results, f);
          return;
        }
        
        results.set(val, v);
        c.incrementFinished();
        
      }
      
      final boolean isDone, isBelowCapacity, isCountEqual;
      
      synchronized (c) {
        System.out.println("Checking for below capacity.");
        isBelowCapacity = c.isBelowCapacity();
        System.out.println("below capacity: "+ isBelowCapacity);
        isCountEqual = c.getFinishedCount() == c.getStartedCount();
      }
      
      synchronized (iterator) {
        isDone = !iterator.hasNext() && isCountEqual;
      }
      
      if (isDone) {
        NeoUtils.fireFinalCallback(s, null, results, f);
        return;
      }
      
      if (isBelowCapacity) {
        RunTasksLimit(iterator, results, c, s, f);
      }
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void RunMapLimit(
    final Iterator<Map.Entry<Object, Asyncc.AsyncTask<T, E>>> entries,
    final int size,
    final Map<Object, T> results,
    final CounterLimit c,
    final ShortCircuit s,
    final Asyncc.IAsyncCallback<Map<Object, T>, E> f) {
    
    final Map.Entry<Object, Asyncc.AsyncTask<T, E>> entry;
    
    synchronized (entries) {
      if (!entries.hasNext()) {
        return;
      }
      entry = entries.next();
    }
    
    final Object key = entry.getKey();
    final Asyncc.AsyncTask<T, E> t = entry.getValue();
    
    c.incrementStarted();
    
    var taskRunner = new Asyncc.AsyncCallback<T, E>(s) {
      
      @Override
      public void done(E e, T v) {
  
        synchronized (this.cbLock) {
    
          if (this.isFinished()) {
            new Error("Warning: Callback fired more than once.").printStackTrace(System.err);
            return;
          }
    
          this.setFinished(true);
    
          results.put(key, v);
          c.incrementFinished();
    
          if (s.isShortCircuited()) {
            return;
          }
    
          if (e != null) {
            s.setShortCircuited(true);
            fireFinalCallback(s, e, results, f);
            return;
          }
    
        }
  
        if (c.getFinishedCount() == size) {
          fireFinalCallback(s, null, results, f);
          return;
        }
  
        if (c.getFinishedCount() >= size) {
          throw new RuntimeException("Finished count was greater than number of tasks. This is a bug.");
        }
  
        if (c.isBelowCapacity()) {
          RunMapLimit(entries, size, results, c, s, f);
        }
      }
    };
    
    try {
      t.run(taskRunner);
    } catch (Exception e) {
      fireFinalCallback(s, e, results, f);
      return;
    }
    
    if (c.getStartedCount() >= size) {
      return;
    }
    
    if (c.isBelowCapacity()) {
      RunMapLimit(entries, size, results, c, s, f);
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void ParallelLimit(
    final int limit,
    final Map<Object, Asyncc.AsyncTask<T, E>> tasks,
    final Asyncc.IAsyncCallback<Map<Object, T>, E> f) {
    
    final int size = tasks.size();
    final Map<Object, T> results = new HashMap<>();
    final CounterLimit c = new CounterLimit(limit);
    final ShortCircuit s = new ShortCircuit();
    
    final Iterator<Map.Entry<Object, Asyncc.AsyncTask<T, E>>> entries = tasks.entrySet().iterator();
    
    RunMapLimit(entries, size, results, c, s, f);
    NeoUtils.handleSameTickCall(s);
    
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void ParallelLimit(
    final int limit,
    final List<Asyncc.AsyncTask<T, E>> tasks,
    final Asyncc.IAsyncCallback<List<T>, E> f) {
  
    final List<T> results = new ArrayList<T>();
    
    if (tasks.size() < 1) {
      f.done(null, results);
      return;
    }
    
    final ShortCircuit s = new ShortCircuit();
    final CounterLimit c = new CounterLimit(limit);
    final Iterator<Asyncc.AsyncTask<T, E>> iterator = tasks.iterator();
    
    RunTasksLimit(iterator, results, c, s, f);
    NeoUtils.handleSameTickCall(s);
    
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void RunTasksLimit(
    final Iterator<Asyncc.AsyncTask<T, E>> iterator,
    final List<T> results,
    final CounterLimit c,
    final ShortCircuit s,
    final Asyncc.IAsyncCallback<List<T>, E> f) {
    
    final int val;
    final Asyncc.AsyncTask<T, E> t;
    
    synchronized (iterator) {
      
      if (!iterator.hasNext()) {
        return;
      }
      
      val = c.getStartedCount();
      c.incrementStarted();
      t = iterator.next();
    }
    
    final var taskRunner = new ParallelRunner<T, E>(iterator, c, s, val, results, f);
    
    try {
      t.run(taskRunner);
    } catch (Exception e) {
      NeoUtils.fireFinalCallback(s, e, results, f);
      return;
    }
    
    if (!iterator.hasNext()) {
      return;
    }
    
    if (c.isBelowCapacity()) {
      RunTasksLimit(iterator, results, c, s, f);
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void Parallel(Map<String, Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<Map<String, T>, E> f) {
    
    final Map<String, T> results = new HashMap<>();
    final CounterLimit c = new CounterLimit(Integer.MAX_VALUE);
    final ShortCircuit s = new ShortCircuit();
    
    for (Map.Entry<String, Asyncc.AsyncTask<T, E>> entry : tasks.entrySet()) {
      
      final String key = entry.getKey();
      final var taskRunner = new Asyncc.AsyncCallback<T, E>(s) {
        
        @Override
        public void done(E e, T v) {
          
          synchronized (this.cbLock) {
            
            if (this.isFinished()) {
              new Error("Warning: Callback fired more than once.").printStackTrace();
              return;
            }
            
            this.setFinished(true);
            
            if (s.isShortCircuited()) {
              return;
            }
            
          }
          
          c.incrementFinished();
          results.put(key, v);
          
          if (e != null) {
            s.setShortCircuited(true);
            f.done(e, results);
            return;
          }
          
          if (c.getFinishedCount() == tasks.size()) {
            f.done(null, results);
          }
        }
      };
      
      try {
        entry.getValue().run(taskRunner);
      } catch (Exception e) {
        s.setShortCircuited(true);
        f.done((E) e, results);
        return;
      }
      
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void Parallel(List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    
    List<T> results = new ArrayList<T>();
    
    if (tasks.size() < 1) {
      f.done(null, results);
      return;
    }
    
    CounterLimit c = new CounterLimit(Integer.MAX_VALUE);
    ShortCircuit s = new ShortCircuit();
    
    for (int i = 0; i < tasks.size(); i++) {
      
      results.add(null);
      final int index = i;
      
      tasks.get(i).run(new Asyncc.AsyncCallback<T, E>(s) {
        
        @Override
        public void done(E e, T v) {
          
          synchronized (this.cbLock) {
            
            if (this.isFinished()) {
              new Error("Warning: Callback fired more than once.").printStackTrace(System.err);
              return;
            }
            
            this.setFinished(true);
            
            if (s.isShortCircuited()) {
              return;
            }
            
            c.incrementFinished();
            results.set(index, v);
          }
          
          if (e != null) {
            s.setShortCircuited(true);
            f.done(e, results);
            return;
          }
          
          if (c.getFinishedCount() == tasks.size()) {
            f.done(null, results);
          }
        }
      });
      
    }
    
    if (s.isFinalCallbackFired()) {
      s.setSameTick(false);
    }
    
  }
  
}
