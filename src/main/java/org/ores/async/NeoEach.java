package org.ores.async;

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
  
  static <T, V, E> void Each(int limit, Iterable<T> i, Asyncc.IEacher<T, E> m, Asyncc.IEachCallback<E> f) {
    
    final CounterLimit c = new CounterLimit(limit);
    final ShortCircuit s = new ShortCircuit();
    final var iterator = i.iterator();
    RunEach(iterator, c, s, m, f);
    
  }
  

  @SuppressWarnings("Duplicates")
  private static <T, V, E> void RunEach(
    final Iterator<T> iterator,
    final CounterLimit c,
    final ShortCircuit s,
    final Asyncc.IEacher<T, E> m,
    final Asyncc.IEachCallback<E> f) {
    
    final T v;
    
    synchronized (iterator){
      if (!iterator.hasNext()) {
        return;
      }
  
      v = iterator.next();
    }
    
    final var taskRunner = new Asyncc.EachCallback<E>(s) {
      
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
            new Error("Warning: Callback fired more than once.").printStackTrace();
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
          RunEach(iterator, c, s, m, f);
        }
        
      }
      
    };
  
    c.incrementStarted();
    
    try {
      m.each(v, taskRunner);
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
      RunEach(iterator, c, s, m, f);
    }
    
  }
  
}
