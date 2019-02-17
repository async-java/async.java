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
  
  public static class KeyValue<K,V> {
    
    public K key;
    public V value;
    
    public KeyValue(K key, V value) {
      this.key = key;
      this.value = value;
    }
  
    public KeyValue(Map.Entry<K,V> m) {
      this.key = m.getKey();
      this.value = m.getValue();
    }
  }
  
  public interface Mapper<T, V, E> {
     void map(T v, AsyncCallback<V, E> cb);
  }
  
  public static class ReduceArg<T, V> {
    
    final public T prev;
    final public V curr;
    
    public ReduceArg(T prev, V curr) {
      this.prev = prev;
      this.curr = curr;
    }
  }
  
  public interface Reducer<V, E> {
    void reduce(ReduceArg v, AsyncCallback<V, E> cb);
  }
  
  public interface Eacher<T,E> {
    void each(T v, IEachCallback<E> cb);
  }
  
  public interface IAsyncCallback<T, E> {
    void done(E e, T v);
  }
  
  public interface IEachCallback<E> {
    void done(E e);
  }
  
  public static interface IEachCallbacks<E> {
    void resolve();
    void reject(E e);
  }
  
  public static interface ICallbacks<T, E> {
    void resolve(T v);
    
    void reject(E e);
  }
  
  
  public static abstract class EachCallback<E> implements IEachCallback<E>, IEachCallbacks<E> {
    
    private ShortCircuit s;
    private boolean isFinished = false;
    final Object cbLock = new Object();
    
    public EachCallback(ShortCircuit s) {
      this.s = s;
    }
    
    public boolean isShortCircuited() {
      return this.s.isShortCircuited();
    }
    
    boolean isFinished() {
      return this.isFinished;
    }
    
    boolean setFinished(boolean b) {
      return this.isFinished = b;
    }
    
  }
  
  public static abstract class AsyncCallback<T, E> implements IAsyncCallback<T, E>, ICallbacks<T, E> {
    
    private ShortCircuit s;
    private boolean isFinished = false;
    final Object cbLock = new Object();
    
    public AsyncCallback(ShortCircuit s) {
      this.s = s;
    }
    
    public boolean isShortCircuited() {
      return this.s.isShortCircuited();
    }
    
    boolean isFinished() {
      return this.isFinished;
    }
    
    boolean setFinished(boolean b) {
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
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<List<V>, E> Map(List<T> items, Mapper<T,V, E> m) {
    return cb -> NeoMap.<V, T, E>Map(Integer.MAX_VALUE, items, m, (IAsyncCallback<List<V>, E>) cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<List<V>, E> MapSeries(List<T> items, Mapper<T, V, E> m) {
    return cb -> NeoMap.<V, T, E>Map(1, items, m, (IAsyncCallback<List<V>, E>) cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<List<V>, E> Map(Integer lim, List<T> items, Mapper<T, V, E> m) {
    return cb -> NeoMap.<V, T, E>Map(lim, items, m, (IAsyncCallback<List<V>, E>) cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void Map(Iterable<T> items, Mapper<T, V, E> m, IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(Integer.MAX_VALUE, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void MapSeries(Iterable<T> items, Mapper<T, V, E> m, IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(1, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void MapLimit(Integer limit, Iterable<T> items, Mapper<T, V, E> m, IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(limit, items, m, f);
  }
  
  // end map
  
  // start each
  
  public static <T, V, E> void Each(Iterable<T> i, Asyncc.Eacher<T,E> m, Asyncc.IEachCallback<E> f) {
    NeoEach.Each(Integer.MAX_VALUE, i, m, f);
  }
  
  public static <T, V, E> void EachLimit(int limit, Iterable<T> i, Asyncc.Eacher<T,E> m, Asyncc.IEachCallback< E> f) {
    NeoEach.Each(limit, i, m, f);
  }
  
  public static <T, V, E> void EachSeries(Iterable<T> i, Asyncc.Eacher<T,E> m, Asyncc.IEachCallback<E> f) {
    NeoEach.Each(1, i, m, f);
  }
  
  // end each
  
  // start series
  
  public static <T, E> void Series(
    Map<String, AsyncTask<T, E>> tasks,
    IAsyncCallback<Map<String, T>, E> f) {
    NeoSeries.Series(tasks, f);
  }
  
  public static <T, E> void Series(
    List<AsyncTask<T, E>> tasks,
    IAsyncCallback<List<T>, E> cb) {
    NeoSeries.<T, E>Series(tasks, cb);
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    IAsyncCallback<List<T>, E> cb) {
    NeoSeries.<T, E>Series(List.of(a), cb);
  }
  
  public static <T, E> void Series(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    IAsyncCallback<List<T>, E> cb) {
    NeoSeries.<T, E>Series(List.of(a, b), cb);
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
    NeoParallel.<T, E>Parallel(List.of(a, b, c, d), cb);
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
    List<AsyncTask<T, E>> tasks,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.ParallelLimit(limit, tasks, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <I, T, V, E> void ReduceRight(I initialVal, List<T> tasks, Asyncc.Reducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoReduce.ReduceRight(initialVal, tasks, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ReduceRight(List<T> tasks, Asyncc.Reducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoReduce.ReduceRight(tasks, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <I, T, V, E> void Reduce(I initialVal, List<T> tasks, Asyncc.Reducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoReduce.Reduce(initialVal, tasks, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void Reduce(List<T> tasks, Asyncc.Reducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoReduce.Reduce(tasks, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void Concat(List<T> tasks, Asyncc.Mapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(Integer.MAX_VALUE, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatSeries(List<T> tasks, Asyncc.Mapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(1, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatLimit(int lim, List<T> tasks, Asyncc.Mapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(lim, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void Concat(int depth, List<T> tasks, Asyncc.Mapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(Integer.MAX_VALUE, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatSeries(int depth, List<T> tasks, Asyncc.Mapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(1, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatLimit(int depth, int lim, List<T> tasks, Asyncc.Mapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(lim, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatDeep(List<T> tasks, Asyncc.Mapper<T,V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(Integer.MAX_VALUE, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatDeepSeries(List<T> tasks, Asyncc.Mapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(1, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatDeepLimit(int lim, List<T> tasks, Asyncc.Mapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(lim, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void Concat(List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.Parallel(tasks, (err, results) -> {
      f.done(err, NeoConcat.concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ConcatSeries(List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoSeries.Series(tasks, (err, results) -> {
      f.done(err, NeoConcat.concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ConcatLimit(int lim, List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.ParallelLimit(lim, tasks, (err, results) -> {
      f.done(err, NeoConcat.concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void Concat(int depth, List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.Parallel(tasks, (err, results) -> {
      f.done(err, NeoConcat.flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ConcatSeries(int depth, List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoSeries.Series(tasks, (err, results) -> {
      f.done(err, NeoConcat.flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ConcatLimit(int depth, int lim, List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.ParallelLimit(lim, tasks, (err, results) -> {
      f.done(err, NeoConcat.flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ConcatDeep(List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.Parallel(tasks, (err, results) -> {
      f.done(err, NeoConcat.flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ConcatDeepSeries(List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoSeries.Series(tasks, (err, results) -> {
      f.done(err, NeoConcat.flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ConcatDeepLimit(int lim, List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.ParallelLimit(lim, tasks, (err, results) -> {
      f.done(err, NeoConcat.flatten(Integer.MAX_VALUE, 0, results));
    });
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
