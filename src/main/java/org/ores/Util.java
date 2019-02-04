package org.ores;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class Util {
  
  @SuppressWarnings("Duplicates")
  static <T, E> void RunMapLimit(
    Iterator<Map.Entry<String, Asyncc.AsyncTask<T, E>>> entries,
    Map<String, Asyncc.AsyncTask<T, E>> m,
    Map<String, T> results,
    Counter c,
    ShortCircuit s,
    Limit lim,
    Asyncc.IAsyncCallback<Map<String, T>, E> f) {
    
    if (c.getStartedCount() >= m.size()) {
      return;
    }
    
    if (!entries.hasNext()) {
      return;
    }
    
    Map.Entry<String, Asyncc.AsyncTask<T, E>> entry = entries.next();
    String key = entry.getKey();
    Asyncc.AsyncTask<T, E> t = entry.getValue();
    lim.increment();
    c.incrementStarted();
    
    t.run(new Asyncc.AsyncCallback<T, E>(s) {
      
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
        
        if (s.isShortCircuited()) {
          return;
        }
        
        if (e != null) {
          s.setShortCircuited(true);
          f.done(e, Map.of());
          return;
        }
        
        results.put(key, v);
        lim.decrement();
        c.incrementFinished();
        
        if (c.getFinishedCount() == m.size()) {
          f.done(null, results);
          return;
        }
        
        if (lim.isBelowCapacity()) {
          Util.RunMapLimit(entries, m, results, c, s, lim, f);
        }
      }
      
    });
    
    
    if (c.getStartedCount() >= m.size()) {
      return;
    }
    
    if (lim.isBelowCapacity()) {
      Util.RunMapLimit(entries, m, results, c, s, lim, f);
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void RunTasksLimit(
    List<Asyncc.AsyncTask> tasks,
    List<T> results,
    Counter c,
    ShortCircuit s,
    Limit lim,
    Asyncc.IAsyncCallback<List<T>, E> f) {
    
    if (c.getStartedCount() >= tasks.size()) {
//      f.run(null, results);
      return;
    }
    
    final int val = c.getStartedCount();
    Asyncc.AsyncTask<T, E> t = tasks.get(val);
    lim.increment();
    c.incrementStarted();
    
    t.run(new Asyncc.AsyncCallback<T, E>(s) {
      
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
        
        if (s.isShortCircuited()) {
          return;
        }
        
        if (e != null) {
          s.setShortCircuited(true);
          f.done(e, Collections.emptyList());
          return;
        }
        
        results.set(val, v);
        lim.decrement();
        c.incrementFinished();
        
        if (c.getFinishedCount() == tasks.size()) {
          f.done(null, results);
          return;
        }
        
        if (lim.isBelowCapacity()) {
          Util.RunTasksLimit(tasks, results, c, s, lim, f);
        }
        
      }
      
    });
    
    
    if (c.getStartedCount() >= tasks.size()) {
      return;
    }
    
    if (lim.isBelowCapacity()) {
      Util.RunTasksLimit(tasks, results, c, s, lim, f);
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void RunTasksSerially(
    List<Asyncc.AsyncTask<T, E>> tasks,
    List<T> results,
    ShortCircuit s,
    Counter c,
    Asyncc.IAsyncCallback<List<T>, E> f) {
    
    final int startedCount = c.getStartedCount();
    
    if (startedCount >= tasks.size()) {
//      f.run(null, results);
      return;
    }
    
    Asyncc.AsyncTask<T, E> t = tasks.get(startedCount);
    c.incrementStarted();
    
    t.run(new Asyncc.AsyncCallback<T, E>(s) {
      
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
        
        if (s.isShortCircuited()) {
          return;
        }
        
        if (e != null) {
          s.setShortCircuited(true);
          f.done(e, Collections.emptyList());
          return;
        }
        
        c.incrementFinished();
        results.set(startedCount, v);
        
        if (c.getFinishedCount() == tasks.size()) {
          f.done(null, results);
          return;
        }
        
        Util.RunTasksSerially(tasks, results, s, c, f);
      }
      
    });
    
  }
}
