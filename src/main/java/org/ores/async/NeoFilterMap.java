package org.ores.async;

import java.util.*;

public class NeoFilterMap {
  
  private final static Object DISCARD_KEY = new Object();
  
  public static abstract class AsyncCallback<T, E> extends Asyncc.AsyncCallback<T,E> {
    
    boolean included = true;
    
    AsyncCallback(ShortCircuit s) {
      super(s);
    }
    
    public void include(){
      this.included = true;
    }
    
    public boolean keep(boolean keep){
      return this.included = keep;
    }
    
    public void discard(){
      this.included = false;
    }
    
    public boolean isIncluded(){
      return this.included;
    }
    
    
  }
  
  public interface IMapper<T, V, E> {
    void map(T v, AsyncCallback<V, E> cb);
  }
  
  
  @SuppressWarnings("Duplicates")
  static <V, T, E> void Map(int limit, Iterable<T> items, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    
    final List<V> results = new ArrayList<V>();
    final Iterator<T> iterator = items.iterator();
    
    if (!iterator.hasNext()) {
      f.done(null, results);
      return;
    }
    
    final CounterLimit c = new CounterLimit(limit);
    final ShortCircuit s = new ShortCircuit();
    RunMap(iterator, m, results, c, s, (err,values) -> {
      f.done(err, NeoUtils.filterList(values, DISCARD_KEY));
    });
  
    if (s.isFinalCallbackFired()) {
      s.setSameTick(false);
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  static <V, T, E> void Map(int limit, Map<Object, T> map, IMapper<T, V, E> m, Asyncc.IAsyncCallback<Map<Object, V>, E> f) {
  
    final HashMap<Object, V> results = new HashMap<>();
    final Iterator<Map.Entry<Object, T>> iterator = map.entrySet().iterator();
  
    if (!iterator.hasNext()) {
      f.done(null, results);
      return;
    }
  
    final CounterLimit c = new CounterLimit(limit);
    final ShortCircuit s = new ShortCircuit();
    RunMapWithMap(iterator, m, results, c, s, (err, values) -> {
      f.done(err, NeoUtils.filterMap(values, DISCARD_KEY));
    });
  
    if (s.isFinalCallbackFired()) {
      s.setSameTick(false);
    }
  
  }
  
  @SuppressWarnings("Duplicates")
  private static <T, V, E> void RunMapWithMap(
    final Iterator<Map.Entry<Object, T>> entries,
    final IMapper<T, V, E> m,
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
    
    final var taskRunner = new AsyncCallback<V, E>(s) {
  
      @Override
      public void done(E e) {
        this.done(e,null);
      }
      
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
          
          if(this.isIncluded()){
            results.put(key, v);
          }
          else{
            results.put(key, (V) DISCARD_KEY);
          }
         
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
    final Iterator<T> iterator,
    final IMapper<T, V, E> m,
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
      public void done(E e) {
        this.done(e,null);
      }
      
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
          
          if(this.isIncluded()){
            results.set(val, v);
          }
          else{
            results.set(val, (V) DISCARD_KEY);
          }
          
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
