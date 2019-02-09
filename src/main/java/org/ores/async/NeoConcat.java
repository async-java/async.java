package org.ores.async;

import java.util.ArrayList;
import java.util.List;

public class NeoConcat {
  
   @SuppressWarnings("Duplicates")
   static <T, E> void Concat(List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.Parallel(tasks, (err, results) -> {
      
      ArrayList<T> concatenated = new ArrayList<>();
      for (T l : results) {
        concatenated.add(l);
      }
      
      f.done(err, concatenated);
      
    });
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void ConcatSeries(List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    
    NeoSeries.Series(tasks, (err, results) -> {
    
      ArrayList<T> concatenated = new ArrayList<>();
      for (T l : results) {
        concatenated.add(l);
      }
    
      f.done(err, concatenated);
    
    });
  }
  
  
  @SuppressWarnings("Duplicates")
  static <T, E> void ConcatLimit(int lim, List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.ParallelLimit(lim, tasks, (err, results) -> {
      
      ArrayList<T> concatenated = new ArrayList<>();
      
      for (T l : results) {
        concatenated.add(l);
      }
      
      f.done(err, concatenated);
      
    });
  }
  
}
