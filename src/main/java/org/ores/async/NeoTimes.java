package org.ores.async;

import java.util.ArrayList;
import java.util.List;

import static org.ores.async.NeoUtils.handleSameTickCall;
import static org.ores.async.NeoTimesI.ITimesCallback;
import static org.ores.async.NeoTimesI.ITimesr;
import static org.ores.async.NeoTimesI.TimesCallback;

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
class NeoTimes {
  
  static <T, E> void Times(int limit, int count, ITimesr<T, E> m, ITimesCallback<List<T>, E> f) {
    
    var results = new ArrayList<T>();
    
    if (count < 1) {
      f.done(null, results);
      return;
    }
    
    final CounterLimit c = new CounterLimit(limit, count);
    final ShortCircuit s = new ShortCircuit();
    RunTimes(c, s, results, m, f);
    handleSameTickCall(s);
  }
  
  @SuppressWarnings("Duplicates")
  private static <T, E> void RunTimes(
    final CounterLimit c,
    final ShortCircuit s,
    final ArrayList<T> results,
    final ITimesr<T, E> m,
    final ITimesCallback<List<T>, E> f) {
    
    final int val;
    
    synchronized (c){
      val = c.getStartedCount();
      if (val >= c.getTimesTotal()) {
        new RuntimeException("Warning: hit max, but this should not be reached.").printStackTrace(System.err);
        return;
      }
    }
    
    c.incrementStarted();
    results.add(null);
    
    final var taskRunner = new TimesCallback<T, E>(s) {
      
      @Override
      public void done(final E e, final T v) {
        
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
          results.set(val, v);
  
          if (e != null) {
            s.setShortCircuited(true);
            NeoUtils.fireFinalCallback(s, e, results, f);
            return;
          }
          
        }
        
        final boolean isDone, isBelowCapacity, maxxedOut;
        
        synchronized (c) {
          isDone = c.getFinishedCount() == c.getTimesTotal();
          isBelowCapacity = c.isBelowCapacity();
          maxxedOut = c.getStartedCount() >= c.getTimesTotal();
        }
        
        if (isDone) {
          NeoUtils.fireFinalCallback(s, null, results, f);
          return;
        }
        
        if (!maxxedOut && isBelowCapacity) {
          RunTimes(c, s, results, m, f);
        }
        
      }
      
    };
    
    try {
      m.run(val, taskRunner);
    } catch (Exception err) {
      s.setShortCircuited(true);
      NeoUtils.fireFinalCallback(s, err, results, f);
      return;
    }
    
    final boolean isBelowCapacity, isDone, maxxedOut;
    
    synchronized (c) {
      isDone = c.getFinishedCount() == c.getTimesTotal();
      isBelowCapacity = c.isBelowCapacity();
      maxxedOut = c.getStartedCount() >= c.getTimesTotal();
    }
    
    if (!isDone && !maxxedOut && isBelowCapacity) {
      RunTimes(c, s, results, m, f);
    }
    
  }
  
}
