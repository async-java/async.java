package org.ores.async;

public class NeoGroupByI {
  
  public static abstract class AsyncCallback<T, E> extends Asyncc.AsyncCallback<T, E> {
    
    public AsyncCallback(final ShortCircuit s) {
      super(s);
    }
    
  }
  
  public interface IMapper<T, E> {
    void map(T v, Asyncc.IAsyncCallback<String, E> cb);
  }
}
