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

    /**
     * Shorthand for {@code done(null, k, v)} — name the value going into the next Waterfall
     * stage. The conventional way to fire a successful Waterfall continuation in v0.2.5+.
     *
     * @since 0.2.5
     */
    default void success(final String k, final T v) {
      done(null, k, v);
    }

    /**
     * Shorthand for {@code done(e)} — fail the Waterfall with the given error. Overrides the
     * inherited {@link Asyncc.IAsyncCallback#fail(Object)} so {@code c.fail(err)} routes through
     * the 1-arg Waterfall-specific {@code done(E)} path instead of {@code done(E, value)} with
     * a {@code null} value (which has different downstream semantics).
     *
     * @since 0.2.5
     */
    @Override
    default void fail(final E e) {
      done(e);
    }
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
  
  public interface AsyncValueTask<T, E> {
    public void run(Object v, AsyncCallback<T, E> cb);
  }
  
  public interface AsyncTask<T, E> {
    public void run(AsyncCallback<T, E> cb);
  }
  
}
