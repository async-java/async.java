package org.ores;

import java.util.*;

import static java.util.Arrays.asList;

public class Asyncc {
  
  public static interface IAcceptRunnable {
    public void accept(Runnable r);
  }
  
  
  public static IAcceptRunnable nextTick = null;

//	INextTick n;
//
//	public static interface INextTick {
//	    public void run();
//	}
  
  public static IAcceptRunnable setOnNext(IAcceptRunnable r) {
    return Asyncc.nextTick = r;
  }
  
  public static Class<Queue> Queue = Queue.class;
//	public static Class<Inject> Inject = Inject.class;
  
  
  public static <T, E> void Inject(
    Map.Entry<String, Inject.Task<T, E>> a,
    Asyncc.IAsyncCallback<Map<String, Object>, E> f) {
    // one arg
    Inject.Inject(Map.ofEntries(a), f);
  }
  
  public static <T, E> void Inject(
    Map<String, Inject.Task<T, E>> tasks,
    Asyncc.IAsyncCallback<Map<String, Object>, E> f) {
    Inject.Inject(tasks, f);
  }
  
  public static class KeyValue<V> {
    
    public String key;
    public V value;
    
    public KeyValue(String key, V value) {
      this.key = key;
      this.value = value;
    }
  }
  
  public static interface Mapper<V, T, E> {
    public void map(KeyValue<V> v, AsyncCallback<T, E> cb);
  }
  
  public static interface IAsyncCallback<T, E> {
    public void done(E e, T v);
  }
  
  public static interface ICallbacks<T, E> {
    void resolve(T v);
    
    void reject(E e);
//		 void run(E e, T... v);
  }
  
  public static abstract class AsyncCallback<T, E> implements IAsyncCallback<T, E>, ICallbacks<T, E> {
    private ShortCircuit s;
    
    public AsyncCallback(ShortCircuit s) {
      this.s = s;
    }
    
    public boolean isShortCircuited() {
      return this.s.isShortCircuited();
    }
    
  }
  
  public static interface AsyncTask<T, E> {
    public void run(AsyncCallback<T, E> cb);
//    public void run(AsyncCallback<T, E> cb, Integer v);
  }
  
  public static <T, E> AsyncTask<List<T>, E> Parallel(AsyncTask<T, E>... tasks) {
    return cb -> Asyncc.<T, E>Parallel(List.of(tasks), cb);
  }
  
  public static <T, E> AsyncTask<List<T>, E> Series(AsyncTask<T, E>... tasks) {
    return cb -> Asyncc.<T, E>Series(List.of(tasks), cb);
  }
  
  public static <T, E> AsyncTask<List<T>, E> Parallel(List<AsyncTask<T, E>> tasks) {
    return cb -> Asyncc.<T, E>Parallel(tasks, cb);
  }
  
  public static <T, E> AsyncTask<List<T>, E> Series(List<AsyncTask<T, E>> tasks) {
    return cb -> Asyncc.<T, E>Series(tasks, cb);
  }
  
  public static <T, E> AsyncTask<Map<String, T>, E> Parallel(Map<String, AsyncTask<T, E>> tasks) {
    return cb -> Asyncc.<T, E>Parallel(tasks, cb);
  }
  
  public static <T, E> AsyncTask<Map<String, T>, E> Series(Map<String, AsyncTask<T, E>> tasks) {
    return cb -> Asyncc.<T, E>Series(tasks, cb);
  }
  
  @SuppressWarnings("Duplicates")
  public static <V, T, E> void Map(List<?> items, Mapper<V, T, E> m, IAsyncCallback<List<T>, E> f) {
    NeoMap.Map(items, m, f);
  }
  
  public static <T, E> void Series(
    Map<String, AsyncTask<T, E>> tasks,
    IAsyncCallback<Map<String, T>, E> f) {
    
    NeoSeries.Series(tasks, f);
  }
  
  public static <T, E> void Series(
    List<AsyncTask<T, E>> tasks,
    IAsyncCallback<List<T>, E> f) {
    
    NeoSeries.Series(tasks, f);
  }
  
  public static <T, E> void ParallelLimit(
    int limit,
    Map<String, AsyncTask<T, E>> tasks,
    IAsyncCallback<Map<String, T>, E> f) {
    
    Parallel.ParallelLimit(limit, tasks, f);
    
  }
  
  public static <T, E> void Parallel(Map<String, AsyncTask<T, E>> tasks, IAsyncCallback<Map<String, T>, E> f) {
    Parallel.Parallel(tasks, f);
  }
  
  
  public static <T, E> void Parallel(AsyncTask<T, E> a, IAsyncCallback<List<T>, E> cb) {
    Parallel.Parallel(List.of(a), cb);
  }
  
  public static <T, E> void Parallel(AsyncTask<T, E> a, AsyncTask<T, E> b,IAsyncCallback<List<T>, E> cb) {
    Parallel.Parallel(List.of(a,b), cb);
  }
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    IAsyncCallback<List<T>, E> cb) {
    Parallel.Parallel(List.of(a,b,c), cb);
  }
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    IAsyncCallback<List<T>, E> cb) {
    Parallel.Parallel(List.of(a,b,c,d), cb);
  }
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    IAsyncCallback<List<T>, E> cb) {
    Parallel.Parallel(List.of(a,b,c,d,e), cb);
  }
  
  public static <T, E> void Parallel(
    AsyncTask<T, E> a,
    AsyncTask<T, E> b,
    AsyncTask<T, E> c,
    AsyncTask<T, E> d,
    AsyncTask<T, E> e,
    AsyncTask<T, E> f,
    IAsyncCallback<List<T>, E> cb) {
    Parallel.Parallel(List.of(a,b,c,d,e,f), cb);
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
    Parallel.Parallel(List.of(a,b,c,d,e,f,g), cb);
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
    Parallel.Parallel(List.of(a,b,c,d,e,f,g), cb);
  }
  
  public static <T, E> void Parallel(List<AsyncTask<T, E>> tasks, IAsyncCallback<List<T>, E> f) {
    Parallel.Parallel(tasks, f);
  }
  
  
  public static <T, E> void ParallelLimit(
    int limit,
    List<AsyncTask> tasks,
    IAsyncCallback<List<T>, E> f) {
    
    Parallel.ParallelLimit(limit, tasks, f);
  }
  
  
}
