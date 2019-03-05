package org.ores.async;

public class NeoWhilstI {
  
  public interface SyncTruthTest {
    boolean test();
  }
  
  public interface AsyncTruthTest {
    void test(Asyncc.IAsyncCallback<Boolean,Object> v);
  }
  
  public interface AsyncTask<T, E> {
    //    public void run(AsyncCallback<T, E> cb);
    void run(AsyncCallback<T, E> cb);
  }
  
  public static abstract class AsyncCallback<T, E> extends Asyncc.AsyncCallback<T, E> {
    
    public AsyncCallback(final ShortCircuit s) {
      super(s);
    }
    
  }
}
