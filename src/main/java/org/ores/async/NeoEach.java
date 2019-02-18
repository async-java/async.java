package org.ores.async;

import java.util.Collections;
import java.util.Iterator;

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
  
  static <T, V, E> void Each(int limit, Iterable<T> i, Asyncc.Eacher<T, E> m, Asyncc.IEachCallback<E> f) {
    
    CounterLimit c = new CounterLimit(limit);
    ShortCircuit s = new ShortCircuit();
    
    var iterator = i.iterator();
    
    RunEach(iterator, c, s, m, f);
    
  }
  
  private static <T, V, E> void RunEach(
    Iterator<T> iterator,
    CounterLimit c,
    ShortCircuit s,
    Asyncc.Eacher<T, E> m,
    Asyncc.IEachCallback<E> f) {
    
    if (!iterator.hasNext()) {
      return;
    }
    
    var v = iterator.next();
    
    m.each(v, new Asyncc.EachCallback<E>(s) {
      
      @Override
      public void resolve() {
        this.done(null);
      }
      
      @Override
      public void reject(E e) {
        this.done(e);
      }
      
      @Override
      public void done(E e) {
        
        synchronized (this.cbLock) {
          
          if (this.isFinished()) {
            new Error("Callback fired more than once.").printStackTrace();
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
          f.done(e);  // List.of()?
          return;
        }
        
        final boolean isDone, isBelowCapacity;
        
        synchronized (c) {
          isDone = !iterator.hasNext() && (c.getFinishedCount() == c.getStartedCount());
          isBelowCapacity = c.isBelowCapacity();
        }
        
        if (isDone) {
          f.done(null);
          return;
        }
        
        if (isBelowCapacity) {
          RunEach(iterator, c, s, m, f);
        }
        
      }
      
    });
    
    final boolean isBelowCapacity;
    
    synchronized (c) {
      isBelowCapacity = c.isBelowCapacity();
    }
    
    if (isBelowCapacity) {
      RunEach(iterator, c, s, m, f);
    }
    
  }
  
}
