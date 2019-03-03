package org.ores.async;

class NeoParallelI {
  
  public interface AsyncValueTask<T, E> {
     void run(Object v, Asyncc.IAsyncCallback<T, E> cb);
  }
  
}
