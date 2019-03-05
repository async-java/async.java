package org.ores.async;

public class NeoMapI {
  
  public static abstract class AsyncCallback<T, E> extends Asyncc.AsyncCallback<T, E> {
    
    public AsyncCallback(final ShortCircuit s) {
      super(s);
    }
    
  }
  
}
