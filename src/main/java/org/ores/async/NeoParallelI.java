package org.ores.async;

class NeoParallelI {
  
  public static abstract class AsyncCallback<T, E> extends Asyncc.AsyncCallback<T, E> {
    public AsyncCallback(ShortCircuit s) {
      super(s);
    }
  }
  
  public interface AsyncValueTask<T, E> {
     void run(Object v, Asyncc.IAsyncCallback<T, E> cb);
  }
  
}
