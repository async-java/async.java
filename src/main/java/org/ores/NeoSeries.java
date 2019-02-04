package org.ores;

import java.util.*;

class NeoSeries {
  
   static <T, E> void Series(
    Map<String, Asyncc.AsyncTask<T, E>> tasks,
    Asyncc.IAsyncCallback<Map<String, T>, E> f) {
    
    Map<String, T> results = new HashMap<>();
    ShortCircuit s = new ShortCircuit();
    Counter c = new Counter();
    
    Iterator<Map.Entry<String, Asyncc.AsyncTask<T, E>>> entries = tasks.entrySet().iterator();
    Limit lim = new Limit(1);
    
    Util.<T, E>RunMapLimit(entries, tasks, results, c, s, lim, f);
    
  }
  
   static <T, E> void Series(
    List<Asyncc.AsyncTask<T, E>> tasks,
    Asyncc.IAsyncCallback<List<T>, E> f) {
    
    List<T> results = new ArrayList<T>(Collections.nCopies(tasks.size(), null));
    Counter c = new Counter();
    ShortCircuit s = new ShortCircuit();
    
    if (tasks.size() < 1) {
      f.done(null, Collections.emptyList());
      return;
    }
    
    Util.<T, E>RunTasksSerially(tasks, results, s, c, f);
    
  }
}
