package org.ores.async;

import java.util.*;

import static org.ores.async.NeoGroupByI.IMapper;
import static org.ores.async.NeoGroupByI.AsyncCallback;

public class NeoGroupBy {
  
  @SuppressWarnings("Duplicates")
  static <V, T, E> void Group(
    final int limit,
    final Iterable<T> items,
    final IMapper<T, E> m,
    final Asyncc.IAsyncCallback<Map<String,List<V>>, E> f) {
  
    final HashMap<String, List<V>> results = new HashMap<>();
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
    final IMapper<T, E> m,
    final Map<String,List<V>> results,
    final CounterLimit c,
    final ShortCircuit s,
    final Asyncc.IAsyncCallback<Map<String,List<V>>, E> f) {
    
    final T item;
    
    synchronized (iterator) {
      if (!iterator.hasNext()) {
        return;
      }
      
      item = (T) iterator.next();
    }
    
    final int val = c.getStartedCount();
    c.incrementStarted();
    
    final var taskRunner = new AsyncCallback<String , E>(s) {
      
      @Override
      public void done(final E e, final String v) {
        
        synchronized (this.cbLock) {
          
          if (this.isFinished()) {
            new Error("Warning: Callback fired more than once.").printStackTrace();
            return;
          }
          
          this.setFinished(true);
          
          if(!results.containsKey(v)){
            results.put(v, new ArrayList<>());
          }
          
          // TODO: allow user to map item to a different object
          results.get(v).add((V)item);
          
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
