package org.ores.async;

import java.util.*;

import static org.ores.async.NeoUtils.fireFinalCallback;
import static org.ores.async.NeoParallelI.AsyncCallback;

class NeoParallel {
  
  private static class AsyncTaskRunner<T, E> extends AsyncCallback<T, E> {
    
    final private ParallelRunner p;
    final private Runnable r;
    final private Integer index;
    
    private AsyncTaskRunner(ParallelRunner p, Integer index, Runnable r) {
      super(p.s);
      this.p = p;
      this.r = r;
      this.index = index;
    }
    
    @Override
    public void done(final E e, final T v) {
      
      synchronized (this.cbLock) {
        
        if (this.isFinished()) {
          new Error("Warning: Callback fired more than once.").printStackTrace(System.err);
          return;
        }
        
        this.setFinished(true);
        
        if (this.s.isShortCircuited()) {
          return;
        }

//        p.results.set(index, v);
        this.p.handleValue(index, v);
        p.c.incrementFinished();
        
        if (e != null) {
          s.setShortCircuited(true);
          NeoUtils.fireFinalCallback(s, e, p.results, p.f);
          return;
        }
      }
      
//      final boolean isDone, isBelowCapacity, isCountEqual;
//
//      synchronized (p.c) {
//        isBelowCapacity = p.c.isBelowCapacity();
//        isCountEqual = p.c.getFinishedCount() == p.c.getStartedCount();
//      }
//
//      synchronized (p.iterator) {
//        isDone = !p.iterator.hasNext() && isCountEqual;
//      }
      
      if (this.p.isDone()) {
        NeoUtils.fireFinalCallback(s, null, p.results, p.f);
        return;
      }
      
      if (this.r != null && this.p.isBelowCapacity()) {
        this.r.run();
      }
    }
  }
  
  private abstract static class AbstractParallelRunner<T, E> {
    protected abstract void handleValue(Object key, Object val);
    
    protected abstract boolean isBelowCapacity();
    
    protected abstract boolean isDone();
  }
  
  private static class ParallelRunner<T, E> extends AbstractParallelRunner<T, E> {
    
    protected final Iterator<Asyncc.AsyncTask<T, E>> iterator;
    protected final CounterLimit c;
    protected final Asyncc.IAsyncCallback<List<T>, E> f;
    protected final List<T> results;
    protected final Integer size;
    protected final ShortCircuit s;
    
    private ParallelRunner(
      final Iterator<Asyncc.AsyncTask<T, E>> iterator,
      final CounterLimit c,
      final ShortCircuit s,
      final Integer size,
      final List<T> results,
      final Asyncc.IAsyncCallback<List<T>, E> f) {
      
      this.s = s;
      this.iterator = iterator;
      this.size = size;
      this.c = c;
      this.f = f;
      this.results = results;
    }
    
    @Override
    protected void handleValue(Object key, Object val) {
      this.results.set((Integer) key, (T) val);
    }
    
    @Override
    protected boolean isBelowCapacity() {
      synchronized (this.c) {
        return this.c.isBelowCapacity();
      }
    }
    
    @Override
    protected boolean isDone() {
      synchronized (this.iterator) {
        synchronized (this.c) {
          return !this.iterator.hasNext() && this.c.getFinishedCount() == this.c.getStartedCount();
        }
      }
    }
  }
  
  @SuppressWarnings("Duplicates")
  private static class RunTasksLimit<T, E> extends ParallelRunner<T, E> implements Runnable {
    
    private RunTasksLimit(
      final Iterator<Asyncc.AsyncTask<T, E>> iterator,
      final CounterLimit c,
      final ShortCircuit s,
      final Integer size,
      final List<T> results,
      final Asyncc.IAsyncCallback<List<T>, E> f) {
      //////
      super(iterator, c, s, size, results, f);
    }
    
    public void run() {
      
      final int val;
      final Asyncc.AsyncTask<T, E> t;
      
      synchronized (iterator) {
        
        if (!iterator.hasNext()) {
          return;
        }
        
        val = c.getStartedCount();
        c.incrementStarted();
        t = iterator.next();
      }
      
      this.results.add(null);
      
      final var taskRunner = new AsyncTaskRunner<T, E>(this, val, this);
      
      try {
        t.run(taskRunner);
      } catch (Exception e) {
        NeoUtils.fireFinalCallback(s, e, results, f);
        return;
      }
      
      if (!iterator.hasNext()) {
        return;
      }
      
      if (c.isBelowCapacity()) {
        this.run();
      }
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  private static <T, E> void RunMapLimit(
    final Iterator<Map.Entry<Object, Asyncc.AsyncTask<T, E>>> entries,
    final int size,
    final Map<Object, T> results,
    final CounterLimit c,
    final ShortCircuit s,
    final Asyncc.IAsyncCallback<Map<Object, T>, E> f) {
    
    final Map.Entry<Object, Asyncc.AsyncTask<T, E>> entry;
    
    synchronized (entries) {
      if (!entries.hasNext()) {
        return;
      }
      entry = entries.next();
    }
    
    final Object key = entry.getKey();
    final Asyncc.AsyncTask<T, E> t = entry.getValue();
    
    c.incrementStarted();
    
    var taskRunner = new Asyncc.AsyncCallback<T, E>(s) {
      
      @Override
      public void done(E e, T v) {
        
        synchronized (this.cbLock) {
          
          if (this.isFinished()) {
            new Error("Warning: Callback fired more than once.").printStackTrace(System.err);
            return;
          }
          
          this.setFinished(true);
          
          results.put(key, v);
          c.incrementFinished();
          
          if (s.isShortCircuited()) {
            return;
          }
          
          if (e != null) {
            s.setShortCircuited(true);
            fireFinalCallback(s, e, results, f);
            return;
          }
          
        }
        
        if (c.getFinishedCount() == size) {
          fireFinalCallback(s, null, results, f);
          return;
        }
        
        if (c.getFinishedCount() >= size) {
          throw new RuntimeException("Finished count was greater than number of tasks. This is a bug.");
        }
        
        if (c.isBelowCapacity()) {
          RunMapLimit(entries, size, results, c, s, f);
        }
      }
    };
    
    try {
      t.run(taskRunner);
    } catch (Exception e) {
      fireFinalCallback(s, e, results, f);
      return;
    }
    
    if (c.getStartedCount() >= size) {
      return;
    }
    
    if (c.isBelowCapacity()) {
      RunMapLimit(entries, size, results, c, s, f);
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void ParallelLimit(
    final int limit,
    final Map<Object, Asyncc.AsyncTask<T, E>> tasks,
    final Asyncc.IAsyncCallback<Map<Object, T>, E> f) {
    
    final int size = tasks.size();
    final Map<Object, T> results = new HashMap<>();
    final CounterLimit c = new CounterLimit(limit);
    final ShortCircuit s = new ShortCircuit();
    
    final Iterator<Map.Entry<Object, Asyncc.AsyncTask<T, E>>> entries = tasks.entrySet().iterator();
    
    RunMapLimit(entries, size, results, c, s, f);
    NeoUtils.handleSameTickCall(s);
    
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void ParallelLimit(
    final int limit,
    final List<Asyncc.AsyncTask<T, E>> tasks,
    final Asyncc.IAsyncCallback<List<T>, E> f) {
    
    final List<T> results = new ArrayList<T>();
    
    if (tasks.size() < 1) {
      f.done(null, results);
      return;
    }
    
    final ShortCircuit s = new ShortCircuit();
    final CounterLimit c = new CounterLimit(limit);
    final Iterator<Asyncc.AsyncTask<T, E>> iterator = tasks.iterator();

//    RunTasksLimit(iterator, results, c, s, f);
    new RunTasksLimit<T, E>(iterator, c, s, null, results, f).run();
    NeoUtils.handleSameTickCall(s);
    
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void Parallel(
    final Map<Object, Asyncc.AsyncTask<T, E>> tasks,
    final Asyncc.IAsyncCallback<Map<Object, T>, E> f) {
    
    final Map<Object, T> results = new HashMap<>();
    final CounterLimit c = new CounterLimit(Integer.MAX_VALUE);
    final ShortCircuit s = new ShortCircuit();
    final Set<Map.Entry<Object, Asyncc.AsyncTask<T, E>>> entrySet = tasks.entrySet();
    final int size = entrySet.size();
    
    for (final Map.Entry<Object, Asyncc.AsyncTask<T, E>> entry : entrySet) {
      
      final Object key = entry.getKey();
      final var taskRunner = new AsyncCallback<T, E>(s) {
        
        @Override
        public void done(final E e, final T v) {
          
          synchronized (this.cbLock) {
            
            if (this.isFinished()) {
              if (e != null) {
                System.err.println(e.toString());
              }
              new Error("Warning: Callback fired more than once.").printStackTrace();
              return;
            }
            
            this.setFinished(true);
            
            c.incrementFinished();
            results.put(key, v);
            
            if (s.isShortCircuited()) {
              if (e != null) {
                System.err.println(e.toString());
              }
              return;
            }
            
          }
          
          if (e != null) {
            s.setShortCircuited(true);
            fireFinalCallback(s, e, results, f);
            return;
          }
          
          if (c.getFinishedCount() == size) {
            fireFinalCallback(s, null, results, f);
            return;
          }
          
          if (c.getFinishedCount() >= size) {
            throw new RuntimeException("Finished count was greater than number of tasks. This is a bug.");
          }
        }
      };
      
      try {
        entry.getValue().run(taskRunner);
      } catch (Exception e) {
        s.setShortCircuited(true);
        fireFinalCallback(s, e, results, f);
        break; // breaks us out of loop
      }
      
    }
    
    if (s.isFinalCallbackFired()) {
      s.setSameTick(false);
    }
    
  }
  
  @SuppressWarnings("Duplicates")
  static <T, E> void Parallel(
    final List<Asyncc.AsyncTask<T, E>> tasks,
    final Asyncc.IAsyncCallback<List<T>, E> f) {
    
    final List<T> results = new ArrayList<T>();
    final int size = tasks.size();
    
    if (size < 1) {
      f.done(null, results);
      return;
    }
    
    final CounterLimit c = new CounterLimit(Integer.MAX_VALUE);
    final ShortCircuit s = new ShortCircuit();
    
    for (int i = 0; i < size; i++) {
      
      results.add(null);
      c.incrementStarted();
      
      final int index = i;
      final var taskRunner = new AsyncCallback<T, E>(s) {
        
        @Override
        public void done(final E e, final T v) {
          
          synchronized (this.cbLock) {
            
            if (this.isFinished()) {
              new Error("Warning: Callback fired more than once.").printStackTrace(System.err);
              return;
            }
            
            this.setFinished(true);
            
            c.incrementFinished();
            results.set(index, v);
            
            if (s.isShortCircuited()) {
              return;
            }
          }
          
          if (e != null) {
            s.setShortCircuited(true);
            f.done(e, results);
            return;
          }
          
          if (c.getFinishedCount() == size) {
            f.done(null, results);
          }
        }
      };
      
      try {
        tasks.get(i).run(taskRunner);
      } catch (Exception e) {
        s.setShortCircuited(true);
        fireFinalCallback(s, e, results, f);
        break;
      }
      
    }
    
    if (s.isFinalCallbackFired()) {
      s.setSameTick(false);
    }
    
  }
  
}
