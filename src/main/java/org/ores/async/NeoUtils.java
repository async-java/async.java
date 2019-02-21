package org.ores.async;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class NeoUtils {
  
  public NeoUtils() {
    System.out.println("here is new UTIL class");
  }
  
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
    
    synchronized (s) {
      if (!s.isFinalCallbackFired()) {
        ok = true;
        s.setFinalCallbackFired(true);
      }
    }
    
    if (!ok) {
      if (e instanceof Exception) {
        ((Exception) e).printStackTrace(System.err);
      } else {
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
    
    CompletableFuture
      .delayedExecutor(3, TimeUnit.MILLISECONDS)
      .execute(() -> f.done((E) e));
    
  }
  
  @SuppressWarnings("Duplicates")
  static <V, E> void fireFinalCallback(ShortCircuit s, Object e, Object results, Asyncc.IAsyncCallback<V, E> f) {
    
    var ok = false;
    
    synchronized (s) {
      if (!s.isFinalCallbackFired()) {
        ok = true;
        s.setFinalCallbackFired(true);
      }
    }
    
    if (!ok) {
      if (e instanceof Exception) {
        ((Exception) e).printStackTrace(System.err);
      } else {
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
 
}
