package org.ores.async;

import java.util.List;
import java.util.Map;

public class NeoTimesI {
  
  public interface AsyncTimesTask<T,E> {
    void run(ITimesCallback<T,E> cb);
  }
  
  public static interface AsyncValueTask<T, E> {
    public void run(Object v, ITimesCallback<T,E> cb);
  }
  
  public static interface AsyncValueMapTask<T, E> {
    public void run(Object v, Map<Object,Object> x, ITimesCallback<T,E> cb);
  }
  
  public interface ITimesr<T, E> {
    void run(Integer v, ITimesCallback<T,E> cb);
  }
  
  public interface ITimesCallback<T,E> extends Asyncc.IAsyncCallback<T,E> {
    void done(E e, T v);
  }
  
  public static interface ITimesCallbacks<T,E> {
    void resolve(T v);
    
    void reject(E e);
  }
  
  static abstract class TimesCallback<T,E>
    extends Asyncc.AsyncCallback<T,E> implements ITimesCallback<T,E>, ITimesCallbacks<T,E> {
    
     TimesCallback(ShortCircuit s) {
      super(s);
    }
    
  }
}
