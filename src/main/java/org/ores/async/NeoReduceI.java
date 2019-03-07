package org.ores.async;

public class NeoReduceI {
  
  public static class ReduceArg<T, V> {
    
    final public V prev;
    final public T curr;
    
    public ReduceArg(V prev, T curr) {
      this.prev = prev;
      this.curr = curr;
    }
  }
  
//  public interface IReducer<V, E> {
//    void reduce(ReduceArg v, Asyncc.AsyncCallback<V, E> cb);
//  }
  
  public interface IReducer<T, V, E> {
    void reduce(V v, T next, Asyncc.IAsyncCallback<V, E> cb);
  }
  
}
