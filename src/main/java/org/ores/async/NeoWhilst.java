package org.ores.async;

import java.util.*;
import static org.ores.async.NeoWhilstI.AsyncCallback;

public class NeoWhilst {
  
  
  @SuppressWarnings("Duplicates")
  static <V, T, E> void Map(
    final int limit,
    final Iterable<T> items,
    final Asyncc.IMapper<T, V, E> m,
    final Asyncc.IAsyncCallback<List<V>, E> f) {
    
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
  private static <T, V, E> void RunMap(
    final Iterator<T> iterator,
    final Asyncc.IMapper<T, V, E> m,
    final List<V> results,
    final CounterLimit c,
    final ShortCircuit s,
    final Asyncc.IAsyncCallback<List<V>, E> f) {
    
    final T item;
    
    synchronized (iterator) {
      if (!iterator.hasNext()) {
        return;
      }
      
      item = (T) iterator.next();
    }
    
    final int val = c.getStartedCount();
    results.add(null);
    c.incrementStarted();
    
    final var taskRunner = new AsyncCallback<V, E>(s) {
      
      @Override
      public void done(final E e, final V v) {
        
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
          isDone = !iterator.hasNext() && (c.getFinishedCount() == c.getStartedCount());
          isBelowCapacity = c.isBelowCapacity();
        }
        
        if (isDone) {
          NeoUtils.fireFinalCallback(s, null, results, f);
          return;
        }
        
        if (isBelowCapacity) {
          RunMap(iterator, m, results, c, s, f);
        }
      }
      
    };
    
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
      RunMap(iterator, m, results, c, s, f);
    }
    
  }
}
