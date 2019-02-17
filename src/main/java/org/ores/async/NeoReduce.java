package org.ores.async;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class NeoReduce {
  
  static <T, V, E> void Reduce(Object initialVal, List<T> tasks, Asyncc.Reducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
  
    if (tasks.size() < 1) {
      f.done(null, (V)initialVal);
      return;
    }
    
    ShortCircuit s = new ShortCircuit();
    Iterator<T> iterator = tasks.iterator();
  
    RunReduce((V)initialVal, s, iterator, m, f);
  }
  
  static <T, V, E> void Reduce(List<T> tasks, Asyncc.Reducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    
    if (tasks.size() < 1) {
      f.done(null, null);
      return;
    }
    
    if (tasks.size() == 1) {
      f.done(null, (V) tasks.get(0));
      return;
    }
    
    ShortCircuit s = new ShortCircuit();
    Iterator<T> iterator = tasks.iterator();
    
    RunReduce((V) tasks.remove(0), s, iterator, m, f);
    
  }
  
  static <V, T, E> void RunReduce(V prev, ShortCircuit s, Iterator<T> iterator, Asyncc.Reducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    
    if (!iterator.hasNext()) {
      return;
    }
    
    T next = iterator.next();
    
    m.reduce(new Asyncc.ReduceArg(prev, next), new Asyncc.AsyncCallback<V, E>(s) {
      
      @Override
      public void resolve(V v) {
        this.done(null, v);
      }
      
      @Override
      public void reject(E e) {
        this.done(e, null);
      }
      
      @Override
      public void done(E e, V v) {
        
        synchronized (this.cbLock) {
          
          if (this.isFinished()) {
            new Error("Callback fired more than once.").printStackTrace();
            return;
          }
          
          this.setFinished(true);
          
          if (s.isShortCircuited()) {
            return;
          }
        }
        
        if (e != null) {
          s.setShortCircuited(true);
          f.done(e, null);
          return;
        }
        
        if (!iterator.hasNext()) {
          f.done(null, v);
          return;
        }
        
        RunReduce(v, s, iterator, m, f);
        
      }
      
    });
    
  }
  
}
