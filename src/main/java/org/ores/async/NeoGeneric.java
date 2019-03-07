package org.ores.async;

import java.util.Map;

public abstract class NeoGeneric<T, V, E> implements
  NeoEachI.IEacher<V, E>, NeoReduceI.IReducer<T, V, E>, Asyncc.IMapper<T, V, E>, Asyncc.AsyncValueTask<V, E>, NeoWaterfallI.AsyncValueTask<V, E> {
  
  abstract void handle(final Object v, final Asyncc.IAsyncCallback cb);
  
  @Override
  public void run(Object v, Asyncc.IAsyncCallback<V,E> cb) {
    this.handle(v, cb);
  }
  
  @Override
  public void run(Object v, NeoWaterfallI.AsyncCallback<V, E> cb) {
    this.handle(v, cb);
  }
  
  @Override
  public void map(T v, Asyncc.IAsyncCallback<V, E> cb) {
    this.handle(v, cb);
  }
  
  @Override
  public void reduce(V prev, T curr, Asyncc.IAsyncCallback<V, E> cb) {
    this.handle(new NeoReduceI.ReduceArg<T, V>(prev, curr), cb);
  }
  
  @Override
  public void each(V v, NeoEachI.IEachCallback<E> cb) {
    this.handle(v, (err, results) -> {
      // TODO: results are discarded by user, warn them?
      cb.done((E) err);
    });
  }
}
