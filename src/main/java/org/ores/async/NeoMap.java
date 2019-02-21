package org.ores.async;

import java.util.*;

/*
 * <script src="https://cdn.rawgit.com/google/code-prettify/master/loader/run_prettify.js"></script>
 **/
class NeoMap {
  
  @SuppressWarnings("Duplicates")
  static <V, T, E> void Map(int limit, Iterable<T> items, Asyncc.Mapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    
    final List<V> results = new ArrayList<V>();
    final Iterator<T> iterator = items.iterator();
    
    if (!iterator.hasNext()) {
      f.done(null, results);
      return;
    }
    
    final CounterLimit c = new CounterLimit(limit);
    final ShortCircuit s = new ShortCircuit();
    RunMap(iterator, m, results, c, s, f);
    
    if (s.isFinalCallbackFired()) {
      s.setSameTick(false);
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  static <V, T, E> void Map(int limit, Map<Object, T> map, Asyncc.Mapper<T, V, E> m, Asyncc.IAsyncCallback<Map<Object, V>, E> f) {
    
    final HashMap<Object, V> results = new HashMap<>();
    final Iterator<Map.Entry<Object, T>> iterator = map.entrySet().iterator();
    
    if (!iterator.hasNext()) {
      f.done(null, results);
      return;
    }
    
    final CounterLimit c = new CounterLimit(limit);
    final ShortCircuit s = new ShortCircuit();
    RunMapWithMap(iterator, m, results, c, s, f);
    
    if (s.isFinalCallbackFired()) {
      s.setSameTick(false);
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  private static <T, V, E> void RunMapWithMap(
    final Iterator<Map.Entry<Object, T>> entries,
    final Asyncc.Mapper<T, V, E> m,
    final Map<Object, V> results,
    final CounterLimit c,
    final ShortCircuit s,
    final Asyncc.IAsyncCallback<Map<Object, V>, E> f) {
    
    final Map.Entry<Object, T> entry;
    
    synchronized (entries) {
      if (!entries.hasNext()) {
        return;
      }
      
      entry = entries.next();
      c.incrementStarted();
    }
    
    final Object key = entry.getKey();
    final T value = entry.getValue();
    
    final var taskRunner = new Asyncc.AsyncCallback<V, E>(s) {
      
      @Override
      public void done(E e, V v) {
        
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
          results.put(key, v);
        }
        
        if (e != null) {
          s.setShortCircuited(true);
          NeoUtils.fireFinalCallback(s, e, results, f);
          return;
        }
        
        final boolean isDone, isBelowCapacity;
        
        synchronized (c) {
          isDone = !entries.hasNext() && (c.getFinishedCount() == c.getStartedCount());
          isBelowCapacity = c.isBelowCapacity();
        }
        
        if (isDone) {
          NeoUtils.fireFinalCallback(s, null, results, f);
          return;
        }
        
        if (isBelowCapacity) {
          RunMapWithMap(entries, m, results, c, s, f);
        }
      }
      
    };
    
    try {
      m.map(value, taskRunner);
    } catch (Exception e) {
      s.setShortCircuited(true);
      NeoUtils.fireFinalCallback(s, e, results, f);
      return;
    }
    
    final boolean isBelowCapacity;
    
    synchronized (c) {
      isBelowCapacity = c.isBelowCapacity();
    }
    
    if (isBelowCapacity) {
      RunMapWithMap(entries, m, results, c, s, f);
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  private static <T, V, E> void RunMap(
    final Iterator<T> items,
    final Asyncc.Mapper<T, V, E> m,
    final List<V> results,
    final CounterLimit c,
    final ShortCircuit s,
    final Asyncc.IAsyncCallback<List<V>, E> f) {
    
    if (!items.hasNext()) {
      return;
    }
    
    final T item = (T) items.next();
    final int val = c.getStartedCount();
    final var taskRunner = new Asyncc.AsyncCallback<V, E>(s) {
      
      @Override
      public void done(E e, V v) {
        
        synchronized (this.cbLock) {
          
          if (this.isFinished()) {
            new Error("Warning: Callback fired more than once.").printStackTrace();
            return;
          }
          
          this.setFinished(true);
          
          if (s.isShortCircuited()) {
            return;
          }
          
          c.incrementFinished();
          results.set(val, v);
        }
        
        if (e != null) {
          s.setShortCircuited(true);
          NeoUtils.fireFinalCallback(s, e, results, f);
          return;
        }
        
        final boolean isDone, isBelowCapacity;
        
        synchronized (c) {
          isDone = !items.hasNext() && (c.getFinishedCount() == c.getStartedCount());
          isBelowCapacity = c.isBelowCapacity();
        }
        
        if (isDone) {
          NeoUtils.fireFinalCallback(s, null, results, f);
          return;
        }
        
        if (isBelowCapacity) {
          RunMap(items, m, results, c, s, f);
        }
      }
      
    };
    
    results.add(null);
    c.incrementStarted();
    
    try {
      m.map(item, taskRunner);
    } catch (Exception e) {
      s.setShortCircuited(true);
      NeoUtils.fireFinalCallback(s, e, results, f);
      return;
    }
    
    final boolean isBelowCapacity;
    
    synchronized (c) {
      isBelowCapacity = c.isBelowCapacity();
    }
    
    if (isBelowCapacity) {
      RunMap(items, m, results, c, s, f);
    }
    
  }
  
}
