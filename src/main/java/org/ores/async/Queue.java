package org.ores.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class Queue<T, V> {
  
  private static ExecutorService executor = Executors.newFixedThreadPool(1);//creating a pool of 5 threads
  private boolean isSaturated = false;
  private List<Task<T, V>> tasks = Collections.synchronizedList(new ArrayList<>());
  private ITaskHandler<T, V> h;
  private boolean isPaused;
  private CounterLimit c;
  private List<IAsyncCb> drainCBs = Collections.synchronizedList(new ArrayList<>());
  private List<IAsyncCb> saturatedCBs = Collections.synchronizedList(new ArrayList<>());
  private List<IAsyncCb> unsaturatedCBs = Collections.synchronizedList(new ArrayList<>());
  private boolean isDrained = false;
  
  final static Logger log = LoggerFactory.getLogger(Queue.class);
  
  
  public interface IAsyncErrFirstCb<T> {
    void done(Object e, T v);
  }
  
  public interface ITaskHandler<T, V> {
    void run(Task<T, V> t, IAsyncErrFirstCb<V> v);
  }
  
  public interface ICallbacks<T> {
    void resolve(T v);
    
    void reject(Object e);
//		 void run(E e, T... v);
  }
  
  public interface IAsyncCb {
    void run(Queue q);
  }
  
  
  public abstract static class AsyncCallback<T> implements IAsyncErrFirstCb<T>, ICallbacks<T> {
    
    private ShortCircuit s;

//  public AsyncCallback(ShortCircuit s){
//    this.s = s;
//  }
    
    public AsyncCallback() {
    
    }
    
    public boolean isShortCircuited() {
      return this.s.isShortCircuited();
    }
    
  }
  
  public static class Task<T, V> {
    
    private T value;
    private ArrayList<IAsyncErrFirstCb<V>> cbs = new ArrayList<>();
    private boolean isStarted = false;
    private boolean isFinished = false;
    
    public Task(T value) {
      this.value = value;
    }
    
    public Task(T value, IAsyncErrFirstCb<V> cb) {
      this.value = value;
      this.cbs.add(cb);
    }
    
    ArrayList<IAsyncErrFirstCb<V>> getCallbacks() {
      return this.cbs;
    }
    
    void addCallback(IAsyncErrFirstCb<V> cb) {
      this.cbs.add(cb);
    }
    
    public T getValue() {
      return this.value;
    }
    
    void setStarted() {
      if (this.isStarted) {
        throw new Error("Task already started.");
      }
      this.isStarted = true;
    }
    
    public boolean isStarted() {
      return this.isStarted;
    }
    
    void setFinished() {
      if (this.isFinished) {
        throw new Error("Task already started.");
      }
      this.isFinished = true;
    }
    
    boolean isFinished() {
      return this.isFinished;
    }
  }
  
  public static void main() {
    
    var q = new Queue<Integer, Integer>((task, v) -> {
      v.done(null, null);
    });
    
    q.push(new Task<Integer, Integer>(3, (err, v) -> {
    
    }));
    
  }
  
  
  public Queue(Integer concurrency, ITaskHandler<T, V> h) {
    this.h = h;
    this.c = new CounterLimit(concurrency);
  }
  
  public Queue(ITaskHandler<T, V> h) {
    this.c = new CounterLimit(1);
    this.h = h;
  }
  
  public Integer getConcurrency() {
    return this.c.getConcurrency();
  }
  
  
  public boolean isDrained() {
    return this.isDrained;
  }
  
  public synchronized List<IAsyncCb> getOnDrainCbs() {
    return this.drainCBs;
  }
  
  public synchronized void setDrained(boolean drained) {
    this.isDrained = drained;
  }
  
  public synchronized List<IAsyncCb> getOnSaturatedCbs() {
    return this.saturatedCBs;
  }
  
  public synchronized List<IAsyncCb> getOnUnsaturatedCbs() {
    return this.unsaturatedCBs;
  }
  
  public Integer setConcurrency(Integer v) {
    if (v < 1) {
      throw new Error("Concurrency value must be an integer greater than 0");
    }
    return this.c.setConcurrency(v);
  }
  
  public void nudge() {
    // poke, prod, nudge, etc
    // useful if the concurrency was just increased
    this.processTasks();
  }
  
  public void push(Task<T, V> t) {
    this.tasks.add(t);
    if (this.isPaused) {
      return;
    }
    this.processTasks();
  }
  
  public void push(Task<T, V> t, IAsyncErrFirstCb<V> cb) {
    t.addCallback(cb);
    this.tasks.add(t);
    if (this.isPaused) {
      return;
    }
    this.processTasks();
  }
  
  public void onDrain(IAsyncCb cb) {
    this.getOnDrainCbs().add(cb);
  }
  
  public void onSaturated(IAsyncCb cb) {
    this.getOnSaturatedCbs().add(cb);
  }
  
  public void onUnsaturated(IAsyncCb cb) {
    this.getOnUnsaturatedCbs().add(cb);
  }
  
  
  public void unshift(Task<T, V> t) {
    this.tasks.add(0, t);
    if (this.isPaused) {
      return;
    }
    this.processTasks();
  }
  
  public void pause() {
    this.isPaused = true;
  }
  
  public void resume() {
    
    if (!this.isPaused) {
      return;
    }
    
    this.isPaused = false;
    this.processTasks();
  }
  
  public boolean isIdle() {
    return this.c.isIdle();
  }
  
  private static void executeRunnable(Runnable r) {
    if (false && Asyncc.nextTick != null) {
      Asyncc.nextTick.accept(r);
    } else if (false) {
      Queue.executor.execute(r);
    } else {
      System.out.println("Using run async.");
      CompletableFuture.runAsync(r, executor);
    }
  }
  
  private synchronized void processTasks() {
    
    if (this.isPaused) {
      return;
    }
    
    if (!this.c.isBelowCapacity()) {
      return;
    }
    
    if (tasks.size() < 1) {
      return;
    }
    
    Task<T, V> t = this.tasks.remove(0);
    
    t.setStarted();  // signify that the task has started so it can't be removed anymore by the user
    
    this.c.incrementStarted();
    
    if (!this.c.isBelowCapacity() && !this.isSaturated) {
      this.isSaturated = true;
      synchronized (this) {
        for (IAsyncCb cb : this.getOnSaturatedCbs()) {
          cb.run(this);
        }
      }
    }
    
    final var q = this;
    
    this.setDrained(false);
    
    this.h.run(t, new AsyncCallback<V>() {
      
      @Override
      public void resolve(V v) {
        this.done(null, v);
      }
      
      @Override
      public void reject(Object e) {
        this.done(e, null);
      }
      
      @Override
      public void done(Object e, V v) {
        
        if (t.isFinished()) {
          // callback was fired more than once
          new Error("Callback was fired more than once.").printStackTrace();
          return;
        }
        
        t.setFinished();
        
//        executeRunnable(() -> {
          
          // Queue.executor.execute(() -> {
          
          CompletableFuture.delayedExecutor(1, TimeUnit.MILLISECONDS, executor).execute(() -> {
            // Your code here executes after 5 seconds!
            
            synchronized (Asyncc.sync) {
              
              q.c.incrementFinished();
              
              ListIterator<IAsyncErrFirstCb<V>> iter = t.getCallbacks().listIterator();
              
              while (iter.hasNext()) {
                IAsyncErrFirstCb<V> cb = iter.next();
                iter.remove();
                cb.done(e, v);
              }
              
              
              if (q.tasks.size() < 1 && q.isSaturated) {
                q.isSaturated = false;
                synchronized (q) {
                  for (IAsyncCb cb : q.getOnUnsaturatedCbs()) {
                    cb.run(q);
                  }
                }
              }
              
              if (!q.isDrained() && q.isIdle() && q.tasks.size() < 1) {
                q.setDrained(true);
                synchronized (q) {
                  for (IAsyncCb cb : q.getOnDrainCbs()) {
                    cb.run(q);
                  }
                }
              }
              
              if (q.isPaused) {
                return;
              }
              
              q.processTasks();
              
            }
            
          });
          
          
//        });
        
      }
      
    });
    
    this.processTasks();
    
  }
  
}
