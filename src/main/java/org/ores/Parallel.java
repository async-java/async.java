package org.ores;

import java.util.*;

class Parallel {
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ParallelLimit(
    int limit,
    Map<String, Asyncc.AsyncTask<T, E>> tasks,
    Asyncc.IAsyncCallback<Map<String, T>, E> f) {
    
    Map<String, T> results = new HashMap<>();
    Counter c = new Counter();
    ShortCircuit s = new ShortCircuit();
    
    Iterator<Map.Entry<String, Asyncc.AsyncTask<T, E>>> entries = tasks.entrySet().iterator();
    Limit lim = new Limit(limit);
    
    Util.<T, E>RunMapLimit(entries, tasks, results, c, s, lim, f);
    
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void Parallel(Map<String, Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<Map<String, T>, E> f) {
    
    Map<String, T> results = new HashMap<>();
    Counter c = new Counter();
    ShortCircuit s = new ShortCircuit();
    
    for (Map.Entry<String, Asyncc.AsyncTask<T, E>> entry : tasks.entrySet()) {
      
      final String key = entry.getKey();
      
      entry.getValue().run(new Asyncc.AsyncCallback<T, E>(s) {
        
        @Override
        public void resolve(T v) {
          this.done(null, v);
        }
        
        @Override
        public void reject(E e) {
          this.done(e, null);
        }
        
        @Override
        public void done(E e, T v) {
          
          if (s.isShortCircuited()) {
            return;
          }
          
          if (e != null) {
            s.setShortCircuited(true);
            f.done(e, Map.of());
            return;
          }
          
          c.incrementFinished();
          results.put(key, v);
          
          if (c.getFinishedCount() == tasks.size()) {
            f.done(null, results);
          }
          
        }
      });
      
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void Parallel(List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    
    List<T> results = new ArrayList<T>(Collections.<T>nCopies(tasks.size(), null));
    Counter c = new Counter();
    ShortCircuit s = new ShortCircuit();
    
    for (int i = 0; i < tasks.size(); i++) {
      
      final int index = i;
      
      tasks.get(i).run(new Asyncc.AsyncCallback<T, E>(s) {
        
        @Override
        public void resolve(T v) {
          this.done(null, v);
        }
        
        @Override
        public void reject(E e) {
          this.done(e, null);
        }
        
        @Override
        public void done(E e, T v) {
          
          if (s.isShortCircuited()) {
            return;
          }
          
          if (e != null) {
            s.setShortCircuited(true);
            f.done(e, Collections.emptyList());
            return;
          }
          
          c.incrementFinished();
          results.set(index, v);
          
          if (c.getFinishedCount() == tasks.size()) {
            f.done(null, results);
          }
        }
      });
      
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  public static <T, E> void ParallelLimit(
    int limit,
    List<Asyncc.AsyncTask> tasks,
    Asyncc.IAsyncCallback<List<T>, E> f) {
    
    Limit lim = new Limit(limit);
    ShortCircuit s = new ShortCircuit();
    List<T> results = new ArrayList<T>(Collections.<T>nCopies(tasks.size(), null));
    Counter c = new Counter();
    
    Util.RunTasksLimit(tasks, results, c, s, lim, f);
    
  }
  
  
}
