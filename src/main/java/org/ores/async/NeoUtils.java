package org.ores.async;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class NeoUtils {
  

  
  static void handleSameTickCall(ShortCircuit s) {
    synchronized (s) {
      if (!s.isFinalCallbackFired()) {
        s.setSameTick(false);
      }
    }
  }
  
  static boolean isNeverAsync = false;
  
  static {
    var env = System.getenv().get("asyncc_exclude_redundancy");
    isNeverAsync = env != null && env.equals("yes");
  }
  
  @SuppressWarnings("Duplicates")
  static <E> void fireFinalCallback(ShortCircuit s, Object e, Asyncc.IEachCallback<E> f) {
    
    var ok = false;
    
    s.setShortCircuited(true);
    
    synchronized (s) {
      if (!s.isFinalCallbackFired()) {
        ok = true;
        s.setFinalCallbackFired(true);
      }
    }
    
    if (!ok) {
      if (e instanceof Exception) {
        ((Exception) e).printStackTrace(System.err);
      } else if(e != null){
        System.err.println(e.toString());
      }
      return;
    }
    
    if (!s.isSameTick()) {
      f.done((E) e);
      return;
    }
    
    if (isNeverAsync) {
      f.done((E) e);
      return;
    }
    
    
    f.done((E)e);
//    CompletableFuture
//      .delayedExecutor(3, TimeUnit.MILLISECONDS)
//      .execute(() -> f.done((E) e));
    
  }
  
  @SuppressWarnings("Duplicates")
  static <V, E> void fireFinalCallback(ShortCircuit s, Object e, Object results, Asyncc.IAsyncCallback<V, E> f) {
    
    var ok = false;
    
    s.setShortCircuited(true);
    
    synchronized (s) {
      if (!s.isFinalCallbackFired()) {
        ok = true;
        s.setFinalCallbackFired(true);
      }
    }
    
    if (!ok) {
      if (e instanceof Exception) {
        ((Exception) e).printStackTrace(System.err);
      } else if(e != null){
        System.err.println(e.toString());
      }
      return;
    }
    
    if (!s.isSameTick()) {
      f.done((E) e, (V) results);
      return;
    }
    
    if (isNeverAsync) {
      f.done((E) e, (V) results);
      return;
    }
    
    CompletableFuture
      .delayedExecutor(3, TimeUnit.MILLISECONDS)
      .execute(() -> f.done((E) e, (V) results));
    
  }
  
  static <V> Map<Object, V> filterMap(Map<Object, V> m, Object uniqueValue) {
    Map<Object, V> ret = new HashMap<>();
    for (Map.Entry entry : m.entrySet()) {
      if (entry.getValue() != uniqueValue) {
        ret.put(entry.getKey(), (V)entry.getValue());
      }
    }
    return ret;
  }
  
  static <V> List<V> filterList(List<V> list, Object uniqueValue) {
    List<V> ret = new ArrayList<>();
    for (V o : list) {
      if (o != uniqueValue) {
        ret.add(o);
      }
    }
    return ret;
  }
  
}
