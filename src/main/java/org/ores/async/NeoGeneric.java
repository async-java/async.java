package org.ores.async;

public abstract class NeoGeneric<T,V,E> implements
  Asyncc.IEacher<V,E>, Asyncc.IReducer<V,E>, Asyncc.IMapper<T,V,E>, Asyncc.AsyncValueTask<V,E>, NeoWaterfall.AsyncValueTask<V,E> {
  
  abstract void handle(Object v, Asyncc.IAsyncCallback cb);
  
  @Override
  public void run(Object v, Asyncc.IAsyncCallback cb){
    this.handle(v, cb);
  }
  
  @Override
  public void run(Object v, NeoWaterfall.AsyncCallback<V, E> cb) {
    this.handle(v, (Asyncc.IAsyncCallback)cb);
  }
  
  @Override
  public void map(T v, Asyncc.AsyncCallback<V,E> cb) {
     this.handle(v, cb);
  }
  
  @Override
  public void reduce(Asyncc.ReduceArg v, Asyncc.AsyncCallback<V, E> cb) {
    this.handle(v, cb);
  }
  
  @Override
  public void each(V v, NeoEachI.EachCallback<E> cb) {
    this.handle(v, cb);
  }
}
