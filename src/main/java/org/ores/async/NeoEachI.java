package org.ores.async;

import java.util.List;
import java.util.Map;

public class NeoEachI {

  public interface AsyncEachTask<E> {
    void run(IEachCallback<E> cb);
  }

  public static interface AsyncValueTask<T, E> {
     void run(Object v, List<T> x, IEachCallback<E> cb);
  }

  public static interface AsyncValueMapTask<T, E> {
    void run(Object v, Map<Object, Object> x, IEachCallback<E> cb);
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

  public static abstract class EachCallback<E> extends Asyncc.AsyncCallback<Object, E> implements IEachCallback<E>, IEachCallbacks<E> {


    EachCallback(final ShortCircuit s) {
      super(s);
    }

    @Override
    public void resolve() {
      this.done(null);
    }


  }
}
