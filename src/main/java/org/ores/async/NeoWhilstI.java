package org.ores.async;

public class NeoWhilstI {
  
  interface SyncTruthTest {
    boolean test();
  }
  
  interface AsyncTruthTest {
    void test(Asyncc.AsyncCallback<Boolean,Object> v);
  }
  
  public static abstract class AsyncCallback<T, E> extends Asyncc.AsyncCallback<T, E> {
    
    public AsyncCallback(final ShortCircuit s) {
      super(s);
    }
    
  }
}
