package org.ores.async;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.ores.async.NeoInjectI.IInjectable;
import static org.ores.async.NeoInjectI.AsyncCallbackSet;

/**
 * @see <a href="http://google.com">http://google.com</a>
 * <script src="https://cdn.rawgit.com/google/code-prettify/master/loader/run_prettify.js"></script>
 **/
public class NeoInject {
  
  /**
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
    final Map<String, Task<T, E>> tasks,
    final Asyncc.IAsyncCallback<Map<String, Object>, E> f) {
    
    final ShortCircuit s = new ShortCircuit();
    final Map<String, Object> results = new HashMap<>();
    
    if (tasks.size() < 1) {
      f.done(null, Map.of());
      return;
    }
    
    for (Map.Entry<String, Task<T, E>> entry : tasks.entrySet()) {
      
      final String key = entry.getKey();
      final Set<String> set = entry.getValue().getSet();
      
      checkForCircularDep(key, set, tasks);
    }
    
    final HashSet<String> started = new HashSet<>();
    final HashSet<String> completed = new HashSet<>();
    
    RunInject(started, completed, tasks, results, s, f);
  }
  

  
  @SuppressWarnings("Duplicates")
  private static <T, E> void RunInject(
    final HashSet<String> started,
    final HashSet<String> completed,
    final Map<String, Task<T, E>> m,
    final Map<String, Object> results,
    final ShortCircuit s,
    final Asyncc.IAsyncCallback<Map<String, Object>, E> f) {
    
    for (Map.Entry<String, Task<T, E>> entry : m.entrySet()) {
      
      final String key = entry.getKey();
      final Set<String> set = entry.getValue().getSet();
      
      if (started.contains(key)) {
        continue;
      }
      
      if (!completed.containsAll(set)) {
        // if not all dependencies are already completed, we can't execute this task yet
        continue;
      }
      
      final IInjectable<T, E> v = entry.getValue().getInjectable();
      final var taskRunner = new AsyncCallbackSet<T, E>(s, results) {
        
        
        @Override
        public void done(E err, T v) {
          
          synchronized (this.cbLock) {
            
            if (this.isFinished()) {
              new Error("Warning: Callback fired more than once.").printStackTrace();
              return;
            }
            
            this.setFinished(true);
            
            if (s.isShortCircuited()) {
              return;
            }
            
            completed.add(key);
            results.put(key, v);
          }
          
          if (err != null) {
            s.setShortCircuited(true);
            NeoUtils.fireFinalCallback(s, err, results, f);
            return;
          }
          
          if (completed.size() == m.size()) {
            NeoUtils.fireFinalCallback(s, null, results, f);
            return;
          }
          
          RunInject(started, completed, m, results, s, f);
          
        }
      };
      
      started.add(key);
      
      try {
        v.run(taskRunner);
      } catch (Exception e) {
        s.setShortCircuited(true);
        NeoUtils.fireFinalCallback(s, e, results, f);
        return;
      }
      
    }
  }
  
}
