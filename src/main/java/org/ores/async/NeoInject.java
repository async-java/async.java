package org.ores.async;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NeoInject {
  
  public static interface IInjectable<T, E> {
    public void run(AsyncCallbackSet<T, E> cb);
  }
  
  public static class Task<T, E> {
    
    Set<String> s;
    IInjectable<T, E> i;
    
    public Task(Set<String> s, IInjectable<T, E> i) {
      this.s = s;
      this.i = i;
    }
    
    public Task(IInjectable<T, E> i) {
      this.s = Set.of();
      this.i = i;
    }
    
    public Task(String a, IInjectable<T, E> i) {
      this.s = Set.of(a);
      this.i = i;
    }
    
    public Task(String a, String b, IInjectable<T, E> i) {
      this.s = Set.of(a, b);
      this.i = i;
    }
    
    public Task(String a, String b, String c, IInjectable<T, E> i) {
      this.s = Set.of(a, b, c);
      this.i = i;
    }
    
    public Task(String a, String b, String c, String d, IInjectable<T, E> i) {
      this.s = Set.of(a, b, c, d);
      this.i = i;
    }
    
    public Task(String a, String b, String c, String d, String e, IInjectable<T, E> i) {
      this.s = Set.of(a, b, c, d, e);
      this.i = i;
    }
    
    public Task(String a, String b, String c, String d, String e, String f, IInjectable<T, E> i) {
      this.s = Set.of(a, b, c, d, e, f);
      this.i = i;
    }
    
    public Task(String a, String b, String c, String d, String e, String f, String g, IInjectable<T, E> i) {
      this.s = Set.of(a, b, c, d, e, f, g);
      this.i = i;
    }
    
    Set<String> getSet() {
      return this.s;
    }
    
    IInjectable getInjectable() {
      return this.i;
    }
  }
  
  static <T, E> void checkForCircularDep(String key, Set<String> h, Map<String, Task<T, E>> tasks) {
    
    if (h.contains(key)) {
      throw new Error("The following key has a circular dep: " + key);
    }
    
    for (String s : h) {
      Task t = tasks.get(s);
      if (t == null) {
        throw new Error("No injectable task for string '" + s + "'");
      }
      checkForCircularDep(key, t.getSet(), tasks);
    }
  }
  
  
  static <T, E> void Inject(
    Map<String, Task<T, E>> tasks,
    Asyncc.IAsyncCallback<Map<String, Object>, E> f) {
    ShortCircuit s = new ShortCircuit();
    Map<String, Object> results = new HashMap<>();
    
    if (tasks.size() < 1) {
      f.done(null, Map.of());
      return;
    }
    
    for (Map.Entry<String, Task<T, E>> entry : tasks.entrySet()) {
      
      final String key = entry.getKey();
      final Set<String> set = entry.getValue().getSet();
      
      checkForCircularDep(key, set, tasks);
    }
    
    HashSet<String> started = new HashSet<>();
    HashSet<String> completed = new HashSet<>();
    
    RunInject(started, completed, tasks, results, s, f);
  }
  
  public static abstract class AsyncCallbackSet<T, E> implements Asyncc.IAsyncCallback<T, E>, Asyncc.ICallbacks<T, E> {
    private ShortCircuit s;
    private Map<String, Object> values;
    private boolean isFinished = false;
    
    public AsyncCallbackSet(ShortCircuit s, Map<String, Object> vals) {
      this.s = s;
      this.values = vals;
    }
    
    public boolean isShortCircuited() {
      return this.s.isShortCircuited();
    }
    
    public <V> V get(String s) {
      return (V)this.values.get(s);
    }
  
    boolean isFinished(){
      return this.isFinished;
    }
  
    boolean setFinished(boolean b){
      return this.isFinished = b;
    }
    
  }
  
  private static <T, E> void RunInject(
    HashSet<String> started,
    HashSet<String> completed,
    Map<String, Task<T, E>> m,
    Map<String, Object> results,
    ShortCircuit s,
    Asyncc.IAsyncCallback<Map<String, Object>, E> f) {
    
    
    for (Map.Entry<String, Task<T, E>> entry : m.entrySet()) {
      
      final String key = entry.getKey();
      final Set<String> set = entry.getValue().getSet();
      
      if (started.contains(key)) {
        continue;
      }
      
      if (!completed.containsAll(set)) {
        continue;
      }
      
      final IInjectable<T, E> v = entry.getValue().getInjectable();
      started.add(key);
      
      v.run(new AsyncCallbackSet<T, E>(s, results) {
        
        @Override
        public void resolve(T v) {
          this.done(null, v);
        }
        
        @Override
        public void reject(E e) {
          this.done(e, null);
        }
        
        @Override
        public void done(E err, T v) {
  
          if(this.isFinished()){
            new Error("Callback fired more than once.").printStackTrace();
            return;
          }
  
          this.setFinished(true);
          
          if (s.isShortCircuited()) {
            return;
          }
          
          if (err != null) {
            s.setShortCircuited(true);
            return;
          }
          
          completed.add(key);
          results.put(key, v);
          
          if (completed.size() == m.size()) {
            f.done(null, results);
            return;
          }
          
          RunInject(started, completed, m, results, s, f);
        }
      });
      
    }
  }
  
}
