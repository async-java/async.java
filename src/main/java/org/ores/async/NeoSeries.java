package org.ores.async;

import java.util.*;

import static org.ores.async.NeoUtils.fireFinalCallback;

class NeoSeries {
  
  static <T, E> void Series(
    final Map<Object, Asyncc.AsyncTask<T, E>> tasks,
    final Asyncc.IAsyncCallback<Map<Object, T>, E> f) {
    
    final int size = tasks.size();
    final Map<Object, T> results = new HashMap<>();
    
    if (size < 1) {
      f.done(null, results);
      return;
    }
   
    final ShortCircuit s = new ShortCircuit();
    final CounterLimit c = new CounterLimit(1);
    final Iterator<Map.Entry<Object, Asyncc.AsyncTask<T, E>>> entries = tasks.entrySet().iterator();
    
    RunMapLimit(entries, size, results, c, s, f);
    
  }
  
  static <T, E> void Series(
    final List<Asyncc.AsyncTask<T, E>> tasks,
    final Asyncc.IAsyncCallback<List<T>, E> f) {
    

    final int size = tasks.size();
    
    if (size < 1) {
      f.done(null, Collections.emptyList());
      return;
    }
  
    final List<T> results = new ArrayList<T>();
    final CounterLimit c = new CounterLimit(1);
    final ShortCircuit s = new ShortCircuit();
    final Iterator<Asyncc.AsyncTask<T, E>> iterator = tasks.iterator();
    
    RunTasksSerially(iterator, size, results, s, c, f);
    
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
      public void resolve(T v) {
        this.done(null, v);
      }
      
      @Override
      public void reject(E e) {
        this.done(e, null);
      }
      
      @Override
      public void done(E e, T v) {
        
        synchronized (this.cbLock) {
          
          if (this.isFinished()) {
            new Error("Warning: Callback fired more than once.").printStackTrace();
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
  static <T, E> void RunTasksSerially(
    final Iterator<Asyncc.AsyncTask<T, E>> iterator,
    final int size,
    final List<T> results,
    final ShortCircuit s,
    final CounterLimit c,
    final Asyncc.IAsyncCallback<List<T>, E> f) {
    
    final Asyncc.AsyncTask<T, E> t;
    final int startedCount;
    
    synchronized (iterator) {
      
      if (!iterator.hasNext()) {
        new RuntimeException("Warning: iterator was empty but should have an item.").printStackTrace(System.err);
        return;
      }
      
      startedCount = c.getStartedCount();
      c.incrementStarted();
      t = iterator.next();
    }
    
    results.add(null);
    
    var taskRunner = new Asyncc.AsyncCallback<T, E>(s) {
      
      @Override
      public void resolve(T v) {
        this.done(null, v);
      }
      
      @Override
      public void reject(E e) {
        this.done(e, null);
      }
      
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
          
          if (e != null) {
            s.setShortCircuited(true);
            fireFinalCallback(s, e, results, f);
            return;
          }
          
        }
        
        c.incrementFinished();
        results.set(startedCount, v);
        
        if (c.getFinishedCount() == size) {
          fireFinalCallback(s, null, results, f);
          return;
        }
        
        if (c.getFinishedCount() >= size) {
          throw new RuntimeException("Finished count was greater than number of tasks. This is a bug.");
        }
        
        RunTasksSerially(iterator, size, results, s, c, f);
      }
      
    };
    
    try {
      t.run(taskRunner);
    } catch (Exception e) {
      fireFinalCallback(s, e, results, f);
    }
    
  }
}
