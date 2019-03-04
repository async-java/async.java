package org.ores.async;

import java.util.Map;

public class NeoInjectI {
  
  public interface IInjectable<T, E> {
    void run(final AsyncCallbackSet<T, E> cb);
  }

  public static interface ValueTask<T, E> {
    public Map.Entry<String, NeoInject.Task<T,E>> run(final Object v);
  }
  
  public static abstract class AsyncCallbackSet<T, E> extends Asyncc.AsyncCallback<T, E> {
    
    private Map<String, Object> values;
    
    public AsyncCallbackSet(final ShortCircuit s, final Map<String, Object> vals) {
      super(s);
      this.values = vals;
    }
    
    public <V> V get(final String s) {
      return (V) this.values.get(s);
    }
    
  }
}
