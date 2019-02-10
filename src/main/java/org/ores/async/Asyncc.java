package org.ores.async;

import java.util.*;

public class Asyncc {
  
  public final static Object sync = new Object();
  
  static enum Marker {
    DONE
  }
  
  public interface IAcceptRunnable {
    void accept(Runnable r);
  }
  
  static IAcceptRunnable nextTick = null;
  
  
  public static IAcceptRunnable setOnNext(IAcceptRunnable r) {
    return Asyncc.nextTick = r;
  }
  
  public static <T, E> void Inject(
    Map.Entry<String, NeoInject.Task<T, E>> a,
    Asyncc.IAsyncCallback<Map<String, Object>, E> f) {
    // one arg
    NeoInject.Inject(Map.ofEntries(a), f);
  }
  
  public static <T, E> void Inject(
    Map<String, NeoInject.Task<T, E>> tasks,
    Asyncc.IAsyncCallback<Map<String, Object>, E> f) {
    NeoInject.Inject(tasks, f);
  }
  
  public static class KeyValue<V> {
    
    public String key;
    public V value;
    
    public KeyValue(String key, V value) {
      this.key = key;
      this.value = value;
    }
  }
  
  public interface Mapper<V, T, E> {
    public void map(KeyValue<V> v, AsyncCallback<T, E> cb);
  }
  
  public interface IAsyncCallback<T, E> {
    void done(E e, T v);
  }
  
  public static interface ICallbacks<T, E> {
    void resolve(T v);
    void reject(E e);
  }
  
  public static abstract class AsyncCallback<T, E> implements IAsyncCallback<T, E>, ICallbacks<T, E> {
    private ShortCircuit s;
    private boolean isFinished = false;
    
    public AsyncCallback(ShortCircuit s) {
      this.s = s;
    }
    
    public boolean isShortCircuited() {
      return this.s.isShortCircuited();
    }
    
    boolean isFinished(){
      return this.isFinished;
    }
    
    boolean setFinished(boolean b){
      return this.isFinished = b;
    }
  }
  
  public static interface AsyncTask<T, E> {
    public void run(AsyncCallback<T, E> cb);
  }
  
  public static <T, E> AsyncTask<List<T>, E> Parallel(AsyncTask<T, E>... tasks) {
    return cb -> NeoParallel.Parallel(List.of(tasks), cb);
  }
  
  public static <T, E> AsyncTask<List<T>, E> Series(AsyncTask<T, E>... tasks) {
    return cb -> NeoSeries.Series(List.of(tasks), cb);
  }
  
  public static <T, E> AsyncTask<List<T>, E> Parallel(List<AsyncTask<T, E>> tasks) {
    return cb -> NeoParallel.Parallel(tasks, cb);
  }
  
  public static <T, E> AsyncTask<List<T>, E> Series(List<AsyncTask<T, E>> tasks) {
    return cb -> NeoSeries.Series(tasks, cb);
  }
  
  public static <T, E> AsyncTask<Map<String, T>, E> Parallel(Map<String, AsyncTask<T, E>> tasks) {
    return cb -> NeoParallel.Parallel(tasks, cb);
  }
  
  public static <T, E> AsyncTask<Map<String, T>, E> Series(Map<String, AsyncTask<T, E>> tasks) {
    return cb -> NeoSeries.Series(tasks, cb);
  }
  
  // begin map
  
//  @SuppressWarnings("Duplicates")
//  public static <V, T, E> IAsyncCallback<List<T>, E> Map(List<T> items, Mapper<V, T, E> m) {
//    return cb -> NeoMap.<V,T,E>Map(items, m, cb);
//  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void Map(List<?> items, Mapper<V, T, E> m, IAsyncCallback<List<T>, E> f) {
    NeoMap.Map(items, m, f);
  }
  
  // end map
  
  // start series
  
  public static <T, E> void Series(
    Map<String, AsyncTask<T, E>> tasks,
    IAsyncCallback<Map<String, T>, E> f) {
    NeoSeries.Series(tasks, f);
  }
  
  
  public static <T, E> void Series(
    List<AsyncTask<T, E>> tasks,
    IAsyncCallback<List<T>, E> cb) {
    NeoSeries.<T,E>Series(tasks, cb);
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    IAsyncCallback<List<T>, E> cb) {
    NeoSeries.<T,E>Series(List.of(a), cb);
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    IAsyncCallback<List<T>, E> cb) {
    NeoSeries.<T,E>Series(List.of(a, b), cb);
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    IAsyncCallback<List<T>, E> cb) {
    NeoSeries.Series(List.of(a, b, c), cb);
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    IAsyncCallback<List<T>, E> cb) {
    NeoSeries.Series(List.of(a, b, c, d), cb);
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    IAsyncCallback<List<T>, E> cb) {
    
    NeoSeries.Series(List.of(a, b, c, d, e), cb);
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    AsyncTask<T, E> f,
    IAsyncCallback<List<T>, E> cb) {
    
    NeoSeries.Series(List.of(a, b, c, d, e, f), cb);
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    AsyncTask<T, E> f,
    AsyncTask<T, E> g,
    IAsyncCallback<List<T>, E> cb) {
    
    NeoSeries.Series(List.of(a, b, c, d, e, f, g), cb);
  }
  
  // end series
  
  public static <T, E> void ParallelLimit(
    int limit,
    Map<String, AsyncTask<T, E>> tasks,
    IAsyncCallback<Map<String, T>, E> f) {
    NeoParallel.ParallelLimit(limit, tasks, f);
  }
  
  
  public static <T, E> void Parallel(
    Map.Entry<String, AsyncTask<T, E>> a,
    IAsyncCallback<Map<String, T>, E> f) {
    NeoParallel.Parallel(Map.ofEntries(a), f);
  }
  
  public static <T, E> void Parallel(
    Map.Entry<String, AsyncTask<T, E>> a,
    Map.Entry<String, AsyncTask<T, E>> b,
    IAsyncCallback<Map<String, T>, E> f) {
    NeoParallel.Parallel(Map.ofEntries(a, b), f);
  }
  
  public static <T, E> void Parallel(
    Map.Entry<String, AsyncTask<T, E>> a,
    Map.Entry<String, AsyncTask<T, E>> b,
    Map.Entry<String, AsyncTask<T, E>> c,
    IAsyncCallback<Map<String, T>, E> f) {
    NeoParallel.Parallel(Map.ofEntries(a, b, c), f);
  }
  
  public static <T, E> void Parallel(
    Map.Entry<String, AsyncTask<T, E>> a,
    Map.Entry<String, AsyncTask<T, E>> b,
    Map.Entry<String, AsyncTask<T, E>> c,
    Map.Entry<String, AsyncTask<T, E>> d,
    IAsyncCallback<Map<String, T>, E> f) {
    NeoParallel.Parallel(Map.ofEntries(a, b, c, d), f);
  }
  
  
  public static <T, E> void Parallel(Map<String, AsyncTask<T, E>> tasks, IAsyncCallback<Map<String, T>, E> f) {
    NeoParallel.Parallel(tasks, f);
  }
  
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.Parallel(List.of(a), cb);
  }
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.Parallel(List.of(a, b), cb);
  }
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.Parallel(List.of(a, b, c), cb);
  }
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.<T,E>Parallel(List.of(a, b, c, d), cb);
  }
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.Parallel(List.of(a, b, c, d, e), cb);
  }
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    AsyncTask<T, E> f,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.Parallel(List.of(a, b, c, d, e, f), cb);
  }
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    AsyncTask<T, E> f,
    AsyncTask<T, E> g,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.Parallel(List.of(a, b, c, d, e, f, g), cb);
  }
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    AsyncTask<T, E> f,
    AsyncTask<T, E> g,
    AsyncTask<T, E> h,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.Parallel(List.of(a, b, c, d, e, f, g, h), cb);
  }
  
  
  public static <T, E> void Waterfall(
    List<NeoWaterfall.AsyncTask<T, E>> tasks,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(tasks, cb);
  }
  
  public static <T, E> void Waterfall(
    NeoWaterfall.AsyncTask<T, E> a,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(List.of(a), cb);
  }
  
  
  public static <T, E> void Waterfall(
    NeoWaterfall.AsyncTask<T, E> a,
    NeoWaterfall.AsyncTask<T, E> b,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(List.of(a, b), cb);
  }
  
  public static <T, E> void Waterfall(
    NeoWaterfall.AsyncTask<T, E> a,
    NeoWaterfall.AsyncTask<T, E> b,
    NeoWaterfall.AsyncTask<T, E> c,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(List.of(a, b, c), cb);
  }
  
  public static <T, E> void Waterfall(
    NeoWaterfall.AsyncTask<T, E> a,
    NeoWaterfall.AsyncTask<T, E> b,
    NeoWaterfall.AsyncTask<T, E> c,
    NeoWaterfall.AsyncTask<T, E> d,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(List.of(a, b, c, d), cb);
  }
  
  public static <T, E> void Waterfall(
    NeoWaterfall.AsyncTask<T, E> a,
    NeoWaterfall.AsyncTask<T, E> b,
    NeoWaterfall.AsyncTask<T, E> c,
    NeoWaterfall.AsyncTask<T, E> d,
    NeoWaterfall.AsyncTask<T, E> e,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(List.of(a, b, c, d, e), cb);
  }
  
  public static <T, E> void Waterfall(
    NeoWaterfall.AsyncTask<T, E> a,
    NeoWaterfall.AsyncTask<T, E> b,
    NeoWaterfall.AsyncTask<T, E> c,
    NeoWaterfall.AsyncTask<T, E> d,
    NeoWaterfall.AsyncTask<T, E> e,
    NeoWaterfall.AsyncTask<T, E> f,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(List.of(a, b, c, d, e, f), cb);
  }
  
  
  public static <T, E> void Parallel(List<AsyncTask<T, E>> tasks, IAsyncCallback<List<T>, E> cb) {
    NeoParallel.Parallel(tasks, cb);
  }
  
  public static <T, E> void ParallelLimit(
    int limit,
    List<AsyncTask<T,E>> tasks,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.ParallelLimit(limit, tasks, cb);
  }
  
  static boolean isLocked;
  
  public static void getLockSync(Runnable r) {
    
    if (isLocked) {
      r.run();
      return;
    }
    
    synchronized (Asyncc.sync) {
      isLocked = true;
      r.run();
      isLocked = false;
    }
    
  }
  
}
