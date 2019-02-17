package org.ores.async;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

class NeoMap {
  
  @SuppressWarnings("Duplicates")
  static <V, T, E> void Map(int limit, Iterable<T> items, Asyncc.Mapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    
    List<V> results = new ArrayList<V>();
    Iterator<T> iterator = items.iterator();
    
    if (!iterator.hasNext()) {
      f.done(null, results);
      return;
    }
    
    CounterLimit c = new CounterLimit(limit);
    ShortCircuit s = new ShortCircuit();
    
    RunMap(iterator, m, results, c, s, f);
    
  }
  
  @SuppressWarnings("Duplicates")
  private static <T, V, E> void RunMap(
    Iterator<T> items,
    Asyncc.Mapper<T, V, E> m,
    List<V> results,
    CounterLimit c,
    ShortCircuit s,
    Asyncc.IAsyncCallback<List<V>, E> f) {
    
    if (!items.hasNext()) {
      return;
    }
    
    results.add(null);
    T item = (T) items.next();
    final int val = c.getStartedCount();
    c.incrementStarted();
  
    
    m.map(item, new Asyncc.AsyncCallback<V, E>(s) {
      
      @Override
      public void resolve(V v) {
        this.done(null, v);
      }
      
      @Override
      public void reject(E e) {
        this.done(e, null);
      }
      
      @Override
      public void done(E e, V v) {
        
        synchronized (this.cbLock) {
          
          if (this.isFinished()) {
            new Error("Callback fired more than once.").printStackTrace();
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
          f.done(e, Collections.emptyList());  // List.of()?
          return;
        }
  
  
        final boolean isDone, isBelowCapacity;
        
        synchronized(c) {
          isDone = !items.hasNext() && (c.getFinishedCount() == c.getStartedCount());
          isBelowCapacity = c.isBelowCapacity();
        }
        
        if (isDone) {
          f.done(null, results);
          return;
        }
        
        if (isBelowCapacity) {
          RunMap(items, m, results, c, s, f);
        }
      }
      
    });
  
    final boolean isBelowCapacity;
  
    synchronized(c) {
      isBelowCapacity = c.isBelowCapacity();
    }
    
    if (isBelowCapacity) {
      RunMap(items, m, results, c, s, f);
    }
    
  }
  

}
