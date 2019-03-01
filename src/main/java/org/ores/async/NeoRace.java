package org.ores.async;

import java.util.Iterator;

import static org.ores.async.NeoUtils.handleSameTickCall;
import static org.ores.async.NeoRaceIfc.IMapper;
import static org.ores.async.NeoRaceIfc.RaceCallback;
import static org.ores.async.NeoRaceIfc.RaceParam;
import static org.ores.async.NeoRaceIfc.AsyncTask;

/**
 * @see <a href="http://google.com">http://google.com</a>
 * <script src="https://cdn.rawgit.com/google/code-prettify/master/loader/run_prettify.js"></script>
 *
 * <pre class="prettyprint">
 * new BeanTranslator.Builder()
 *   .translate(
 *     new{@code Translator<String, Integer>}(String.class, Integer.class){
 *      {@literal @}Override
 *       public Integer translate(String instance) {
 *         return Integer.valueOf(instance);
 *       }})
 *   .build();
 * </pre>
 */
class NeoRace {
  
  static <T, V, E> void Race(int limit, Iterable<AsyncTask<T, E>> i, Asyncc.IAsyncCallback<V, E> f) {
    
    final CounterLimit c = new CounterLimit(limit);
    final ShortCircuit s = new ShortCircuit();
    final var iterator = i.iterator();
    RunRace(iterator, c, s, null, f);
    handleSameTickCall(s);
  }
  
  static <T, V, E> void Race(int limit, Iterable<T> i, IMapper<T, V, E> m, Asyncc.IAsyncCallback<V, E> f) {
    
    final CounterLimit c = new CounterLimit(limit);
    final ShortCircuit s = new ShortCircuit();
    final var iterator = i.iterator();
    RunRace(iterator, c, s, m, f);
    handleSameTickCall(s);
  }
  
  @SuppressWarnings("Duplicates")
  static <T, V, E> void RunRace(
    final Iterator<T> iterator,
    final CounterLimit c,
    final ShortCircuit s,
    final IMapper<T, V, E> m,
    final Asyncc.IAsyncCallback<V, E> f) {
    
    final T v;
    
    synchronized (iterator) {
      if (!iterator.hasNext()) {
        return;
      }
      
      if (s.isShortCircuited()) {
        // an error occurred or first one back (race) occurred
        return;
      }
      
      v = iterator.next();
    }
    
    final var taskRunner = new RaceCallback<V, E>(s) {
      
      @Override
      public void done(final E e) {
        this.done(e, null);
      }
      
      @Override
      public void done(final E e, final V v) {
        
        synchronized (this.cbLock) {
          
          if (this.isFinished()) {
            new Error("Warning: Callback fired more than once.").printStackTrace(System.err);
            return;
          }
          
          this.setFinished(true);
          
          if (s.isShortCircuited()) {
            return;
          }
          
          c.incrementFinished();
          
        }
        
        if (e != null) {
          s.setShortCircuited(true);
          NeoUtils.fireFinalCallback(s, e, null, f);
          return;
        }
        
        boolean isHaveAWinner = true;
        V value = v;
        
        if (v instanceof RaceParam) {
          isHaveAWinner = ((RaceParam) v).keep;
          value = ((RaceParam<V>) v).value;
        }
        
        if (isHaveAWinner) {
          s.setShortCircuited(true);
          NeoUtils.fireFinalCallback(s, null, value, f);
          return;
        }
        
        final boolean isDone, isBelowCapacity;
        
        synchronized (c) {
          isDone = !iterator.hasNext() && (c.getFinishedCount() == c.getStartedCount());
          isBelowCapacity = c.isBelowCapacity();
        }
        
        if (isDone) {
          NeoUtils.fireFinalCallback(s, null, null, f);
          return;
        }
        
        if (isBelowCapacity) {
          RunRace(iterator, c, s, m, f);
        }
        
      }
      
    };
    
    c.incrementStarted();
    
    if (m == null) {
      
      try {
        ((AsyncTask<V, E>) v).run(taskRunner);
      } catch (Exception err) {
        s.setShortCircuited(true);
        NeoUtils.fireFinalCallback(s, err, null, f);
        return;
      }
    } else {
      
      try {
        m.map(v, taskRunner);
      } catch (Exception err) {
        s.setShortCircuited(true);
        NeoUtils.fireFinalCallback(s, err, null, f);
        return;
      }
      
    }
    
    final boolean isBelowCapacity;
    
    synchronized (c) {
      isBelowCapacity = c.isBelowCapacity();
    }
    
    if (isBelowCapacity) {
      RunRace(iterator, c, s, m, f);
    }
    
  }
  
}
