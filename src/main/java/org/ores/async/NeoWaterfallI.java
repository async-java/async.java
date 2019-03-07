package org.ores.async;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

public class NeoWaterfallI {
  
  enum Marker {
    DONE
  }
  
  private static class UserMap extends HashMap<String, Object> {
  
  }
  
  public interface IAsyncCallback<T, E> extends Asyncc.IAsyncCallback<Map.Entry<String,T>,E>  {  //
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
    final Object cbLock = new Object();
    
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

    
    public <V> void set(String s, V v) {
      this.map.put(s, v);
    }
    
    protected abstract void doneInternal(Marker done, E e, Map.Entry<String, T> m);
    
    
    @Override
    public void done(E e, Map.Entry<String, T> m) {
      this.doneInternal(Marker.DONE, e, m);
    }
    
    @Override
    public void done(E e, String k, T v) {
      this.doneInternal(Marker.DONE, e, new AbstractMap.SimpleEntry(k, v));
    }
    
    @Override
    public void resolve(Map.Entry<String, T> m) {
      this.doneInternal(Marker.DONE, null, m);
    }
    
    @Override
    public void resolve(String k, T v) {
      this.doneInternal(Marker.DONE, null, new AbstractMap.SimpleEntry(k, v));
    }
    
    @Override
    public void reject(E e) {
      this.doneInternal(Marker.DONE, e, null);
    }
    
    @Override
    public void done(E e) {
      this.doneInternal(Marker.DONE, e, null);
    }
    
  }
  
//  public static interface AsyncValueTask<T, E> {
//    public void run(Object v, IAsyncCallback<T, E> cb);
//  }
//
//  public static interface AsyncTask<T, E> {
//    public void run(IAsyncCallback<T, E> cb);
//  }
  
  public static interface AsyncValueTask<T, E> {
    public void run(Object v, AsyncCallback<T, E> cb);
  }
  
  public static interface AsyncTask<T, E> {
    public void run(AsyncCallback<T, E> cb);
  }
  
}
