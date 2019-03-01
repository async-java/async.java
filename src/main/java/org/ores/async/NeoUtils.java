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
      } else if (e != null) {
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
    
    f.done((E) e);
//    CompletableFuture
//      .delayedExecutor(3, TimeUnit.MILLISECONDS)
//      .execute(() -> f.done((E) e));
    
  }

//  static <E> boolean validateLimit(Integer limit, Asyncc.IEachCallback f){
//    if (limit < 1) {
//      if(f == null){
//        throw new RuntimeException("No callback passed, limit argument needs to be a *positive* integer.");
//      }
//      f.done((E) new RuntimeException("Limit value must be a positive integer."));
//      return true;
//    }
//    return false;
//  }
  
  static <E> void validateLimit(Integer limit) {
    if (limit < 1) {
      throw new RuntimeException("No callback passed, limit argument must be a *positive* integer.");
    }
  }
  
  static <E> boolean validateLimit(Integer limit, Asyncc.IAsyncCallback f) {
    if (limit < 1) {
      if (f == null) {
        throw new RuntimeException("No callback passed, limit argument needs to be a *positive* integer.");
      }
      
      if (f instanceof Asyncc.IEachCallback) {
        ((Asyncc.IEachCallback) f).done((E) new RuntimeException("Limit value must be a positive integer."));
      } else {
        f.done((E) new RuntimeException("Limit value must be a *positive* integer."), null);
      }
      
      return true;
    }
    return false;
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
      } else if (e != null) {
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
        ret.put(entry.getKey(), (V) entry.getValue());
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
