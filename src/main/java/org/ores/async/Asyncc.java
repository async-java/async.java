package org.ores.async;

import java.util.*;

/**
 * <script src="https://cdn.rawgit.com/google/code-prettify/master/loader/run_prettify.js"></script>
 */
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
  
  public static class KeyValue<K, V> {
    
    public K key;
    public V value;
    
    public KeyValue(K key, V value) {
      this.key = key;
      this.value = value;
    }
    
    public KeyValue(Map.Entry<K, V> m) {
      this.key = m.getKey();
      this.value = m.getValue();
    }
  }
  
  public interface IMapper<T, V, E> {
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
  
  public interface IReducer<V, E> {
    void reduce(ReduceArg v, AsyncCallback<V, E> cb);
  }
  
  public interface IEacher<T, E> {
    void each(T v, IEachCallback<E> cb);
  }
  
  interface IErrorOnlyDoneable<T, E> {
    void done(E e, T v);
  }
  
  interface IErrorValueDoneable<T, E> {
    void done(E e, T v);
  }
  
  public interface IAsyncCallback<T, E> {
    boolean isDone = false;
    
    void done(E e, T v);
    
    default void setDone() {
    
    }
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
    
    @Override
    public void resolve() {
      this.done(null);
    }
    
    @Override
    public void reject(E e) {
      this.done(e);
    }
    
  }
  
  public static abstract class AsyncCallback<T, E> implements IAsyncCallback<T, E>, ICallbacks<T, E> {
    
    protected ShortCircuit s;
    protected boolean isFinished = false;
    protected final Object cbLock = new Object();
    
    public AsyncCallback(ShortCircuit s) {
      this.s = s;
    }
    
    @Override
    public void resolve(T v) {
      this.done(null, v);
    }
    
    @Override
    public void reject(E e) {
      this.done(e, null);
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
  
  public static <T, E> AsyncTask<Map<Object, T>, E> Parallel(Map<Object, AsyncTask<T, E>> tasks) {
    return cb -> NeoParallel.Parallel(tasks, cb);
  }
  
  public static <T, E> AsyncTask<Map<Object, T>, E> Series(Map<Object, AsyncTask<T, E>> tasks) {
    return cb -> NeoSeries.Series(tasks, cb);
  }
  
  // begin race
  
  public static <T, V, E> void Race(Iterable<NeoRaceIfc.AsyncTask<T, E>> i, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(Integer.MAX_VALUE, i, f);
  }
  
  public static <T, V, E> void Race(Iterable<T> i, NeoRaceIfc.IMapper<T, V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(Integer.MAX_VALUE, i, m, f);
  }
  
  public static <T, V, E> void Race(Map<Object, NeoRaceIfc.AsyncTask<T, E>> i, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(Integer.MAX_VALUE, i.values(), f);
  }
  
  public static <T, V, E> void Race(Map<Object, T> i, NeoRaceIfc.IMapper<T, V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(Integer.MAX_VALUE, i.values(), m, f);
  }
  
  public static <T, V, E> void RaceLimit(Integer lim, Iterable<NeoRaceIfc.AsyncTask<T, E>> i, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(lim, i, f);
  }
  
  public static <T, V, E> void RaceLimit(Integer lim, Iterable<T> i, NeoRaceIfc.IMapper<T, V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(lim, i, m, f);
  }
  
  public static <T, V, E> void RaceLimit(Integer lim, Map<Object, NeoRaceIfc.AsyncTask<T, E>> i, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(lim, i.values(), f);
  }
  
  public static <T, V, E> void RaceLimit(Integer lim, Map<Object, T> i, NeoRaceIfc.IMapper<T, V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoRace.Race(lim, i.values(), m, f);
  }
  
  // end race
  
  // begin map
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<List<V>, E> Map(List<T> items, IMapper<T, V, E> m) {
    return cb -> NeoMap.<V, T, E>Map(Integer.MAX_VALUE, items, m, (IAsyncCallback<List<V>, E>) cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<List<V>, E> MapSeries(List<T> items, IMapper<T, V, E> m) {
    return cb -> NeoMap.<V, T, E>Map(1, items, m, (IAsyncCallback<List<V>, E>) cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<List<V>, E> Map(Integer lim, List<T> items, IMapper<T, V, E> m) {
    return cb -> NeoMap.<V, T, E>Map(lim, items, m, (IAsyncCallback<List<V>, E>) cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void Map(Iterable<T> items, IMapper<T, V, E> m, IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(Integer.MAX_VALUE, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void MapSeries(Iterable<T> items, IMapper<T, V, E> m, IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(1, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void MapLimit(Integer limit, Iterable<T> items, IMapper<T, V, E> m, IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(limit, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void Map(Map<Object, T> items, IMapper<T, V, E> m, IAsyncCallback<Map<Object, V>, E> f) {
    NeoMap.Map(Integer.MAX_VALUE, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void MapSeries(Map<Object, T> items, IMapper<T, V, E> m, IAsyncCallback<Map<Object, V>, E> f) {
    NeoMap.Map(1, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void MapLimit(Integer limit, Map<Object, T> items, IMapper<T, V, E> m, IAsyncCallback<Map<Object, V>, E> f) {
    NeoMap.Map(limit, items, m, f);
  }
  
  // end map
  
  //start filter + map
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<List<V>, E>  FilterMap(Iterable<T> items, NeoFilterMap.IMapper<T, V, E> m) {
    return cb -> NeoFilterMap.Map(Integer.MAX_VALUE, items, m, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<List<V>, E> FilterMapSeries(Iterable<T> items, NeoFilterMap.IMapper<T, V, E> m) {
    return cb -> NeoFilterMap.Map(1, items, m, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<List<V>, E> FilterMapLimit(Integer limit, Iterable<T> items, NeoFilterMap.IMapper<T, V, E> m) {
    return cb -> NeoFilterMap.Map(limit, items, m, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<Map<Object,V>, E>  FilterMap(Map<Object, T> items, NeoFilterMap.IMapper<T, V, E> m) {
    return cb -> NeoFilterMap.Map(Integer.MAX_VALUE, items, m, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<Map<Object,V>, E> FilterMapSeries(Map<Object, T> items, NeoFilterMap.IMapper<T, V, E> m) {
    return cb -> NeoFilterMap.Map(1, items, m, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> AsyncTask<Map<Object,V>, E> FilterMapLimit(Integer limit, Map<Object, T> items, NeoFilterMap.IMapper<T, V, E> m) {
    return cb -> NeoFilterMap.Map(limit, items, m, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void FilterMap(Iterable<T> items, NeoFilterMap.IMapper<T, V, E> m, IAsyncCallback<List<V>, E> f) {
    NeoFilterMap.Map(Integer.MAX_VALUE, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void FilterMapSeries(Iterable<T> items, NeoFilterMap.IMapper<T, V, E> m, IAsyncCallback<List<V>, E> f) {
    NeoFilterMap.Map(1, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void FilterMapLimit(Integer limit, Iterable<T> items, NeoFilterMap.IMapper<T, V, E> m, IAsyncCallback<List<V>, E> f) {
    NeoFilterMap.Map(limit, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void FilterMap(Map<Object, T> items, NeoFilterMap.IMapper<T, V, E> m, IAsyncCallback<Map<Object, V>, E> f) {
    NeoFilterMap.Map(Integer.MAX_VALUE, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void FilterMapSeries(Map<Object, T> items, NeoFilterMap.IMapper<T, V, E> m, IAsyncCallback<Map<Object, V>, E> f) {
    NeoFilterMap.Map(1, items, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void FilterMapLimit(Integer limit, Map<Object, T> items, NeoFilterMap.IMapper<T, V, E> m, IAsyncCallback<Map<Object, V>, E> f) {
    NeoFilterMap.Map(limit, items, m, f);
  }
  
  // end filter + map
  
  // start each
  
  public static <T, E> void Each(Iterable<T> i, IEacher<T, E> m, Asyncc.IEachCallback<E> f) {
    NeoEach.Each(Integer.MAX_VALUE, i, m, f);
  }
  
  public static <T, E> void EachLimit(int limit, Iterable<T> i, IEacher<T, E> m, Asyncc.IEachCallback<E> f) {
  
    if(limit < 1){
      f.done((E)new RuntimeException("Limit value must be a positive integer."));
      return;
    }
    
    NeoEach.Each(limit, i, m, f);
  }
  
  public static <T, E> void EachSeries(Iterable<T> i, IEacher<T, E> m, Asyncc.IEachCallback<E> f) {
    NeoEach.Each(1, i, m, f);
  }
  
  public static <T, E> void Each(Map i, IEacher<T, E> m, Asyncc.IEachCallback<E> f) {
    NeoEach.Each(Integer.MAX_VALUE, (Set<T>)i.entrySet(), m, f);
  }
  
  public static <T, E> void EachLimit(int limit, Map i, IEacher<T, E> m, Asyncc.IEachCallback<E> f) {
    if(limit < 1){
      f.done((E)new RuntimeException("Limit value must be a positive integer."));
      return;
    }
    NeoEach.Each(limit, i.<T>entrySet(), m, f);
  }
  
  public static <T, E> void EachSeries(Map<Object,Object> i, IEacher<T, E> m, Asyncc.IEachCallback<E> f) {
    NeoEach.Each(1, (Set<T>)i.entrySet(), m, f);
  }
  
  // end each
  
  // start series
  
  public static <T, E> void Series(
    Map<Object, AsyncTask<T, E>> tasks,
    IAsyncCallback<Map<Object, T>, E> f) {
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
    Map<Object, AsyncTask<T, E>> tasks,
    IAsyncCallback<Map<Object, T>, E> f) {
  
    if(limit < 1){
      f.done((E)new RuntimeException("Limit value must be a positive integer."), null);
      return;
    }
    
    NeoParallel.ParallelLimit(limit, tasks, f);
  }
  
  public static <T, E> void Parallel(
    Map.Entry<Object, AsyncTask<T, E>> a,
    IAsyncCallback<Map<Object, T>, E> f) {
    NeoParallel.Parallel(Map.ofEntries(a), f);
  }
  
  public static <T, E> void Parallel(
    Map.Entry<Object, AsyncTask<T, E>> a,
    Map.Entry<Object, AsyncTask<T, E>> b,
    IAsyncCallback<Map<Object, T>, E> f) {
    NeoParallel.Parallel(Map.ofEntries(a, b), f);
  }
  
  public static <T, E> void Parallel(
    Map.Entry<Object, AsyncTask<T, E>> a,
    Map.Entry<Object, AsyncTask<T, E>> b,
    Map.Entry<Object, AsyncTask<T, E>> c,
    IAsyncCallback<Map<Object, T>, E> f) {
    NeoParallel.Parallel(Map.ofEntries(a, b, c), f);
  }
  
  /**
   * @exclude
   */
  public static <T, E> void Parallel(
    Map.Entry<Object, AsyncTask<T, E>> a,
    Map.Entry<Object, AsyncTask<T, E>> b,
    Map.Entry<Object, AsyncTask<T, E>> c,
    Map.Entry<Object, AsyncTask<T, E>> d,
    IAsyncCallback<Map<Object, T>, E> f) {
    NeoParallel.Parallel(Map.ofEntries(a, b, c, d), f);
  }
  
  /**
   * @exclude
   */
  public static <T, E> void Parallel(
    Map.Entry<Object, AsyncTask<T, E>> a,
    Map.Entry<Object, AsyncTask<T, E>> b,
    Map.Entry<Object, AsyncTask<T, E>> c,
    Map.Entry<Object, AsyncTask<T, E>> d,
    Map.Entry<Object, AsyncTask<T, E>> e,
    IAsyncCallback<Map<Object, T>, E> f) {
    NeoParallel.Parallel(Map.ofEntries(a, b, c, d, e), f);
  }
  
  /**
   * @exclude
   */
  public static <T, E> void Parallel(
    Map.Entry<Object, AsyncTask<T, E>> a,
    Map.Entry<Object, AsyncTask<T, E>> b,
    Map.Entry<Object, AsyncTask<T, E>> c,
    Map.Entry<Object, AsyncTask<T, E>> d,
    Map.Entry<Object, AsyncTask<T, E>> e,
    Map.Entry<Object, AsyncTask<T, E>> f,
    IAsyncCallback<Map<Object, T>, E> cb) {
    NeoParallel.Parallel(Map.ofEntries(a, b, c, d, e, f), cb);
  }
  
  public static <T, E> void Parallel(
    Map.Entry<Object, AsyncTask<T, E>> a,
    Map.Entry<Object, AsyncTask<T, E>> b,
    Map.Entry<Object, AsyncTask<T, E>> c,
    Map.Entry<Object, AsyncTask<T, E>> d,
    Map.Entry<Object, AsyncTask<T, E>> e,
    Map.Entry<Object, AsyncTask<T, E>> f,
    Map.Entry<Object, AsyncTask<T, E>> g,
    IAsyncCallback<Map<Object, T>, E> cb) {
    NeoParallel.Parallel(Map.ofEntries(a, b, c, d, e, f, g), cb);
  }
  
  public static <T, E> void Parallel(Map<Object, AsyncTask<T, E>> tasks, IAsyncCallback<Map<Object, T>, E> f) {
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
  
  /**
   * @exclude
   */
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.<T, E>Parallel(List.of(a, b, c, d), cb);
  }
  
  /**
   * @exclude
   */
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    IAsyncCallback<List<T>, E> cb) {
    NeoParallel.Parallel(List.of(a, b, c, d, e), cb);
  }
  
  /**
   * @exclude
   */
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
  
  /**
   * @exclude
   */
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
  
  /**
   * @exclude
   * @Version 1.2.3
   * @Since 1.2.0
   */
  public static <T, E> void Waterfall(
    NeoWaterfall.AsyncTask<T, E> a,
    NeoWaterfall.AsyncTask<T, E> b,
    NeoWaterfall.AsyncTask<T, E> c,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(List.of(a, b, c), cb);
  }
  
  /**
   * @exclude
   */
  public static <T, E> void Waterfall(
    NeoWaterfall.AsyncTask<T, E> a,
    NeoWaterfall.AsyncTask<T, E> b,
    NeoWaterfall.AsyncTask<T, E> c,
    NeoWaterfall.AsyncTask<T, E> d,
    IAsyncCallback<HashMap<String, Object>, E> cb) {
    NeoWaterfall.Waterfall(List.of(a, b, c, d), cb);
  }
  
  /**
   * @exclude
   */
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
  
  /**
   * <pre class="prettyprint">
   * public class JavadocTest {
   *   // indentation and line breaks are kept
   *
   *   @SuppressWarnings
   *   public List<String> generics(){
   *     // '@', '<' and '>'  have to be escaped with HTML codes
   *     // when used in annotations or generics
   *   }
   * }
   * </pre>
   */
  public static <T, E> void ParallelLimit(
    int limit,
    List<AsyncTask<T, E>> tasks,
    IAsyncCallback<List<T>, E> cb) {
    if(limit < 1){
      cb.done((E)new RuntimeException("Limit value must be a positive integer."), null);
      return;
    }
    NeoParallel.ParallelLimit(limit, tasks, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <I, T, V, E> void ReduceRight(I initialVal, List<T> tasks, IReducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoReduce.ReduceRight(initialVal, tasks, m, f);
  }
  
  /**
   * @Version 1.2.3
   * @Since 1.2.0
   * Returns an Image object that can then be painted on the screen.
   */
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ReduceRight(List<T> tasks, IReducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoReduce.ReduceRight(tasks, m, f);
  }
  
  /**
   * @Version 1.2.3
   * @Since 1.2.0
   * <pre class="prettyprint">
   * new BeanTranslator.Builder()
   *   .translate(
   *     new{@code Translator<String, Integer>}(String.class, Integer.class){
   *      {@literal @}Override
   *       public Integer translate(String instance) {
   *         return Integer.valueOf(instance);
   *       }})
   *   .build();
   * </pre>
   */
  @SuppressWarnings("Duplicates")
  public static <I, T, V, E> void Reduce(I initialVal, List<T> tasks, IReducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoReduce.Reduce(initialVal, tasks, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void Reduce(List<T> tasks, IReducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    NeoReduce.Reduce(tasks, m, f);
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void Concat(List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(Integer.MAX_VALUE, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatSeries(List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(1, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatLimit(int lim, List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
  
    if(lim < 1){
      f.done((E)new RuntimeException("Limit value must be a positive integer."), null);
      return;
    }
    
    NeoMap.Map(lim, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void Concat(int depth, List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(Integer.MAX_VALUE, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatSeries(int depth, List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(1, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatLimit(int depth, int lim, List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
  
    if(lim < 1){
      f.done((E)new RuntimeException("Limit value must be a positive integer."), null);
      return;
    }
    
    NeoMap.Map(lim, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatDeep(List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(Integer.MAX_VALUE, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatDeepSeries(List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
    NeoMap.Map(1, tasks, m, (err, results) -> {
      f.done(err, NeoConcat.flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, V, E> void ConcatDeepLimit(int lim, List<T> tasks, IMapper<T, V, E> m, Asyncc.IAsyncCallback<List<V>, E> f) {
  
    if(lim < 1){
      f.done((E)new RuntimeException("Limit value must be a positive integer."), null);
      return;
    }
    
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
  
    if(lim < 1){
      f.done((E)new RuntimeException("Limit value must be a positive integer."), null);
      return;
    }
    
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
  
    if(lim < 1){
      f.done((E)new RuntimeException("Limit value must be a positive integer."), null);
      return;
    }
    
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
  
    if(lim < 1){
      f.done((E)new RuntimeException("Limit value must be a positive integer."), null);
      return;
    }
    
    NeoParallel.ParallelLimit(lim, tasks, (err, results) -> {
      f.done(err, NeoConcat.flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  
}
