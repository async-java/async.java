package org.ores.async;

import java.util.List;
import java.util.Map;

public class NeoEachI {
  
  public interface AsyncEachTask<E> {
    void run(IEachCallback<E> cb);
  }
  
  public static interface AsyncValueTask<T, E> {
    public void run(Object v, List<T> x, IEachCallback<E> cb);
  }
  
  public static interface AsyncValueMapTask<T, E> {
    public void run(Object v, Map<Object,Object> x, IEachCallback<E> cb);
  }
  
  public interface IEacher<T, E> {
    void each(T v, NeoEachI.IEachCallback<E> cb);
  }
  
  public interface IEachCallback<E> {
    void done(E e);
  }
  
  public static interface IEachCallbacks<E> {
    void resolve();
    
    void reject(E e);
  }
  
  public static abstract class EachCallback<E> extends Asyncc.AsyncCallback<Object,E> implements IEachCallback<E>, IEachCallbacks<E> {
    
    private ShortCircuit s;
    private boolean isFinished = false;
    final Object cbLock = new Object();
    
    public EachCallback(ShortCircuit s) {
      super(s);
      this.s = s;
    }
    
    public boolean isShortCircuited() {
      return this.s.isShortCircuited();
    }
    
    boolean isFinished() {
      return this.isFinished;
    }
    
    boolean setFinished(boolean b) {
      return this.isFinished = b;
    }
    
    @Override
    public void resolve() {
      this.done(null);
    }



//    @Override
//    public void reject(E e) {
//      this.done(e);
//    }
  
  }
}
