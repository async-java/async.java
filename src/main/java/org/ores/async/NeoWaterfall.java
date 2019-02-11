package org.ores.async;

import java.util.*;

public class NeoWaterfall {
  
  static class UserMap extends HashMap<String, Object> {
  
  }
  
  public interface IAsyncCallback<T, E> {
    void done(E e);
    
    void done(E e, UserMap.Entry<String, T> m);
    
    void done(E e, String k, T v);
  }
  
  public static interface ICallbacks<T, E> {
    void resolve(UserMap.Entry<String, T> m);
    
    void resolve(String k, T v);
    
    void reject(E e);
  }
  
  public static abstract class AsyncCallback<T, E> implements IAsyncCallback<T, E>, ICallbacks<T, E> {
    private final ShortCircuit s;
    public final HashMap<String, Object> map;
    private boolean isFinished = false;
    
    public AsyncCallback(ShortCircuit s, HashMap<String, Object> m) {
      this.s = s;
      this.map = m;
    }
    
    public boolean isShortCircuited() {
      return this.s.isShortCircuited();
    }
    
    public <V> V get(String s) {
      return (V) this.map.get(s);
    }
    
    boolean isFinished() {
      return this.isFinished;
    }
    
    boolean setFinished(boolean b) {
      return this.isFinished = b;
    }

//    public Object get(String s) {
//      return this.map.get(s);
//    }
    
    public <V> void set(String s, V v) {
      this.map.put(s, v);
    }
    
  }
  
  public static interface AsyncTask<T, E> {
    public void run(AsyncCallback<T, E> cb);
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void Waterfall(
    List<AsyncTask<T, E>> tasks,
    Asyncc.IAsyncCallback<HashMap<String, Object>, E> f) {
    
    HashMap<String, Object> results = new HashMap<>();
    CounterLimit c = new CounterLimit(1);
    ShortCircuit s = new ShortCircuit();
    
    if (tasks.size() < 1) {
      f.done(null, results);
      return;
    }
    
    WaterfallInternal(tasks, results, s, c, f);
    
  }
  
  
  @SuppressWarnings("Duplicates")
  private static <T, E> void WaterfallInternal(
    List<AsyncTask<T, E>> tasks,
    HashMap<String, Object> results,
    ShortCircuit s,
    CounterLimit c,
    Asyncc.IAsyncCallback<HashMap<String, Object>, E> f) {
    
    final int startedCount = c.getStartedCount();
    
    if (startedCount >= tasks.size()) {
//      f.done(null, results);
      return;
    }
    
    AsyncTask<T, E> t = tasks.get(startedCount);
    c.incrementStarted();
    
    t.run(new AsyncCallback<T, E>(s, results) {
      
      private void doneInternal(Asyncc.Marker done, E e, Map.Entry<String, T> m) {
        
        synchronized (this) {
          
          if (this.isFinished()) {
            new Error("Callback fired more than once.").printStackTrace();
            return;
          }
          
          this.setFinished(true);
          
          if (s.isShortCircuited()) {
            return;
          }
          
          if (e != null) {
            s.setShortCircuited(true);
          }
          
          c.incrementFinished();
          
          if (m != null) {
            results.put(m.getKey(), m.getValue());
          }
        }
  
        if (e != null) {
          f.done(e, results);
          return;
        }
        
        if (c.getFinishedCount() == tasks.size()) {
          f.done(null, results);
          return;
        }
        
        WaterfallInternal(tasks, results, s, c, f);
        
      }
      
      
      @Override
      public void done(E e, Map.Entry<String, T> m) {
        this.doneInternal(Asyncc.Marker.DONE, e, m);
      }
      
      @Override
      public void done(E e, String k, T v) {
        this.doneInternal(Asyncc.Marker.DONE, e, new AbstractMap.SimpleEntry(k, v));
      }
      
      @Override
      public void resolve(Map.Entry<String, T> m) {
        this.doneInternal(Asyncc.Marker.DONE, null, m);
      }
      
      @Override
      public void resolve(String k, T v) {
        this.doneInternal(Asyncc.Marker.DONE, null, new AbstractMap.SimpleEntry(k, v));
      }
      
      @Override
      public void reject(E e) {
        this.doneInternal(Asyncc.Marker.DONE, e, null);
      }
      
      @Override
      public void done(E e) {
        this.doneInternal(Asyncc.Marker.DONE, e, null);
      }
      
    });
    
  }
}
