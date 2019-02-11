package org.ores.async;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class NeoConcat {
  
  
  private static <T> List<T> flatten(int desiredDepth, int currentDepth, Collection<T> results) {
    
    if (currentDepth >= desiredDepth) {
      return (List) results;
    }
    
    ArrayList<T> concatenated = new ArrayList<>();
    
    for (T l : results) {
      
      if(l instanceof Collection){
        concatenated.addAll(flatten(desiredDepth, ++currentDepth, (Collection) l));
      }
      else{
        concatenated.add(l);
      }
    }
    
    return concatenated;
  }
  
  private static <T> List<T> concatenate(List<T> results) {
    
    ArrayList<T> concatenated = new ArrayList<>();
    
    for (T l : results) {
      if(l instanceof Collection){
        concatenated.addAll((Collection)l);
      } else{
        concatenated.add(l);
      }
    }
    
    return concatenated;
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void Concat(List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.Parallel(tasks, (err, results) -> {
      f.done(err, concatenate(results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void ConcatSeries(List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoSeries.Series(tasks, (err, results) -> {
      f.done(err, concatenate(results));
    });
  }
  
  
  @SuppressWarnings("Duplicates")
  static <T, E> void ConcatLimit(int lim, List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.ParallelLimit(lim, tasks, (err, results) -> {
      f.done(err, concatenate(results));
    });
  }
  
  
  @SuppressWarnings("Duplicates")
  static <T, E> void Concat(int depth, List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.Parallel(tasks, (err, results) -> {
      f.done(err, flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void ConcatSeries(int depth, List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoSeries.Series(tasks, (err, results) -> {
      f.done(err, flatten(depth, 0, results));
    });
  }
  
  
  @SuppressWarnings("Duplicates")
  static <T, E> void ConcatLimit(int depth, int lim, List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.ParallelLimit(lim, tasks, (err, results) -> {
      f.done(err, flatten(depth, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void ConcatDeep(List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.Parallel(tasks, (err, results) -> {
      f.done(err, flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void ConcatDeepSeries(List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoSeries.Series(tasks, (err, results) -> {
      f.done(err, flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void ConcatDeepLimit(int lim, List<Asyncc.AsyncTask<T, E>> tasks, Asyncc.IAsyncCallback<List<T>, E> f) {
    NeoParallel.ParallelLimit(lim, tasks, (err, results) -> {
      f.done(err, flatten(Integer.MAX_VALUE, 0, results));
    });
  }
  
  
}
