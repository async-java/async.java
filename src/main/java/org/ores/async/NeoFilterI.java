package org.ores.async;

public class NeoFilterI {
  
  public static abstract class AsyncCallback<T, E> extends Asyncc.AsyncCallback<T, E> {
    
    boolean included = true;
    
    AsyncCallback(ShortCircuit s) {
      super(s);
    }
    
    public void include() {
      this.included = true;
    }
    
    public boolean keep(boolean keep) {
      return this.included = keep;
    }
    
    public void discard() {
      this.included = false;
    }
    
    public boolean isIncluded() {
      return this.included;
    }
    
    
  }
  
  public interface IMapper<T, V, E> {
    void map(T v, AsyncCallback<V, E> cb);
  }
  
}
