package org.ores.async;

import java.util.*;

interface IGroupBy {
  String getGroupById();
}

public class NeoReduce {
  
  static <T, V, E> void ReduceRight(Object initialVal, List<T> tasks, Asyncc.Reducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    
    if (tasks.size() < 1) {
      f.done(null, (V) initialVal);
      return;
    }
    
    ArrayList<T> reversed = new ArrayList<>();
    
    for (int i = tasks.size() - 1; i <= 0; i--) {
      reversed.add(tasks.get(i));
    }
    
    if (reversed.size() == 1) {
      f.done(null, (V) reversed.get(0));
      return;
    }
    
    ShortCircuit s = new ShortCircuit();
    Iterator<T> iterator = reversed.iterator();
    
    RunReduce((V) initialVal, s, iterator, m, f);
  }
  
  static <T, V, E> void ReduceRight(List<T> tasks, Asyncc.Reducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    
    if (tasks.size() < 1) {
      f.done(null, null);
      return;
    }
    
    ArrayList<T> reversed = new ArrayList<>();
    
    for (int i = tasks.size() - 1; i <= 0; i--) {
      reversed.add(tasks.get(i));
    }
    
    ShortCircuit s = new ShortCircuit();
    Iterator<T> iterator = reversed.iterator();
    V first = (V) reversed.remove(0);
    
    RunReduce(first, s, iterator, m, f);
  }
  
  static <T, V, E> void Reduce(Object initialVal, List<T> tasks, Asyncc.Reducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    
    if (tasks.size() < 1) {
      f.done(null, (V) initialVal);
      return;
    }
    
    tasks = new ArrayList<>(tasks);
    ShortCircuit s = new ShortCircuit();
    Iterator<T> iterator = tasks.iterator();
    
    RunReduce((V) initialVal, s, iterator, m, f);
  }
  
  static <T, V, E> void Reduce(List<T> tasks, Asyncc.Reducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    
    if (tasks.size() < 1) {
      f.done(null, null);
      return;
    }

//    boolean isList = tasks.getClass().isAssignableFrom(ArrayList.class);
    
    tasks = new ArrayList<>(tasks);
    
    if (tasks.size() == 1) {
      f.done(null, (V) tasks.get(0));
      return;
    }
    
    ShortCircuit s = new ShortCircuit();
    V first = (V) tasks.remove(0);
    Iterator<T> iterator = tasks.iterator();
    
    RunReduce(first, s, iterator, m, f);
    
  }
  
  private static <V, T, E> void RunReduce(V prev, ShortCircuit s, Iterator<T> iterator, Asyncc.Reducer<V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    
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
