package org.ores.async;

public class NeoParallelI {
  
  public static interface AsyncValueTask<T, E> {
    public void run(Object v, Asyncc.IAsyncCallback<T, E> cb);
  }
  
}
