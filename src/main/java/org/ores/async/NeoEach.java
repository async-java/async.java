package org.ores.async;

import java.util.*;

import static org.ores.async.NeoEachI.IEacher;
import static org.ores.async.NeoEachI.IEach;
import static org.ores.async.NeoEachI.IEachCallback;
import static org.ores.async.NeoEachI.EachCallback;
import static org.ores.async.NeoUtils.handleSameTickCall;
import static org.ores.async.NeoEachI.IEacherWithTypedIndex;

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
class NeoEach {
  
  static <T, E> void Each(final int limit, final Iterable<T> i, final IEacher<T, E> m, final IEachCallback<E> f) {
    
    final CounterLimit c = new CounterLimit(limit);
    final ShortCircuit s = new ShortCircuit();
    final var iterator = i.iterator();
    RunEach(iterator, i, false, c, s, m, f);
    handleSameTickCall(s);
  }
  
  static <T, V, E> void EachOf(final int limit, final Iterable<T> i, final IEacherWithTypedIndex<T, V, E> m, final IEachCallback<E> f) {
    
    final CounterLimit c = new CounterLimit(limit);
    final ShortCircuit s = new ShortCircuit();
    final var iterator = i.iterator();
    RunEach(iterator, i, true, c, s, m, f);
    handleSameTickCall(s);
  }
  
  @SuppressWarnings("Duplicates")
  private static <T, E> void RunEach(
    final Iterator<T> iterator,
    final Iterable<T> i,
    final boolean isEachOf,
    final CounterLimit c,
    final ShortCircuit s,
    final IEach m,
    final IEachCallback<E> f) {
    
    final T v;
    
    synchronized (iterator) {
      if (!iterator.hasNext()) {
        return;
      }
      
      v = iterator.next();
    }
    
    final var val = c.getStartedCount();
    
    final var taskRunner = new EachCallback<E>(s) {
      
      @Override
      public void done(E e, Object v) {
        new RuntimeException("Warning: async.each does not accept an mapped argument.").printStackTrace(System.err);
        this.done(e);
      }
      
      @Override
      public void done(E e) {
        
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
          NeoUtils.fireFinalCallback(s, e, f);
          return;
        }
        
        final boolean isDone, isBelowCapacity;
        
        synchronized (c) {
          isDone = !iterator.hasNext() && (c.getFinishedCount() == c.getStartedCount());
          isBelowCapacity = c.isBelowCapacity();
        }
        
        if (isDone) {
          NeoUtils.fireFinalCallback(s, null, f);
          return;
        }
        
        if (isBelowCapacity) {
          RunEach(iterator, i, isEachOf, c, s, m, f);
        }
        
      }
      
    };
    
    c.incrementStarted();
    
    try {
      //TODO this logical mess is unfortunate, but not sure how to improve it
      if (isEachOf) {
        if (i instanceof Set) {
          if (v instanceof Map.Entry) {
            ((IEacherWithTypedIndex) m).each(((Map.Entry) v).getValue(), ((Map.Entry) v).getKey(), taskRunner);
          } else {
            ((IEacherWithTypedIndex) m).each(v, v, taskRunner);
          }
        } else if (i instanceof List) {
          ((IEacherWithTypedIndex) m).each(v, val, taskRunner);
        } else if (i instanceof Queue) {
          ((IEacherWithTypedIndex) m).each(v, v, taskRunner);
        } else {
          ((IEacherWithTypedIndex) m).each(v, val, taskRunner);
        }
      } else {
        // only passing value, not passing key
        ((IEacher) m).each(v, taskRunner);
      }
      
    } catch (Exception err) {
      s.setShortCircuited(true);
      NeoUtils.fireFinalCallback(s, err, f);
      return;
    }
    
    final boolean isBelowCapacity;
    
    synchronized (c) {
      isBelowCapacity = c.isBelowCapacity();
    }
    
    if (isBelowCapacity) {
      RunEach(iterator, i, isEachOf, c, s, m, f);
    }
    
  }
  
}
