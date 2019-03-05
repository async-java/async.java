package org.ores.async;

import java.util.Optional;

public class NeoFilterMapI {
  
  final static Object DISCARD_KEY = new Object();
  
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
    
    public void done(E e, Optional<T> o) {
      if (this.isIncluded() && o != null && o.isPresent()) {
        this.done(e, o.get());
      } else {
        this.discard();
        this.done(e, (T) DISCARD_KEY);
      }
    }
  }
  
  public interface IMapper<T, V, E> {
    void map(T v, AsyncCallback<V, E> cb);
  }
  
}
