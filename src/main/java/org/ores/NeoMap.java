package org.ores;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class NeoMap {
  
  @SuppressWarnings("Duplicates")
  static <V, T, E> void Map(List<?> items, Asyncc.Mapper<V, T, E> m, Asyncc.IAsyncCallback<List<T>, E> f) {
    
    List<T> results = new ArrayList<T>(Collections.<T>nCopies(items.size(), null));
    Counter c = new Counter();
    ShortCircuit s = new ShortCircuit();
    
    for (int i = 0; i < items.size(); i++) {
      
      final int val = c.getStartedCount();
      c.incrementStarted();
      Asyncc.KeyValue<V> kv = new Asyncc.KeyValue<V>(null, (V) items.get(i));
      
      m.map(kv, new Asyncc.AsyncCallback<T, E>(s) {
        
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
            f.done(e, Collections.emptyList());  // List.of()?
            return;
          }
          
          c.incrementFinished();
          results.set(val, v);
          
          if (c.getFinishedCount() == items.size()) {
            f.done(null, results);
          }
        }
        
      });
      
    }
    
  }
}
