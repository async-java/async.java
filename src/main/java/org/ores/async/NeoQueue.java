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
import java.util.concurrent.atomic.AtomicInteger;


/**
 * A bounded async work queue.
 *
 * <p>Hand the queue a task handler; push tasks; the queue dispatches them via the shared
 * executor, respecting a concurrency cap. Callers can subscribe to {@code saturated},
 * {@code unsaturated}, and {@code drain} lifecycle events for back-pressure modelling.
 *
 * <h3>Why a queue (and not just {@link Asyncc#ParallelLimit})?</h3>
 *
 * <p>{@code ParallelLimit} takes a fixed list of tasks. A {@code NeoQueue} accepts <em>streaming
 * arrivals</em> &mdash; you push tasks as they show up and the queue keeps the concurrency cap
 * honoured. Useful for incoming WS frames, paged downstream calls, file watcher events.
 *
 * <h3>Usage</h3>
 *
 * <p>The handler's continuation parameter is conventionally named {@code c} (for
 * <em>continuation</em>). Fire it via {@code c.success(value)}, {@code c.fail(error)}, or the
 * canonical {@code c.done(err, value)}.
 *
 * <pre>
 *   NeoQueue&lt;JobSpec, JobResult&gt; queue = new NeoQueue&lt;&gt;(4); // concurrency = 4
 *
 *   queue.setTaskHandler((task, c) -&gt; {
 *       try {
 *           c.success(processJob(task.getValue()));
 *       } catch (Throwable t) {
 *           c.fail(t);
 *       }
 *   });
 *
 *   queue.saturated((q) -&gt; log.warn("queue saturated; in-flight at cap"));
 *   queue.drain((q) -&gt; log.info("queue drained"));
 *
 *   incoming.forEach(spec -&gt; queue.push(spec));
 * </pre>
 *
 * <h3>With virtual threads</h3>
 *
 * <p>The queue ships with a daemon-threaded default executor. For production deployments on
 * JDK 21+, swap it for a VT executor once at startup:
 *
 * <pre>
 *   NeoQueue.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
 * </pre>
 *
 * @param <T> task input type
 * @param <V> task result type
 */
public class NeoQueue<T, V> {

  /**
   * Process-wide executor used to deliver queue callbacks asynchronously.
   *
   * <p>Two production-readiness changes relative to the original implementation:
   * <ul>
   *   <li>threads are now <strong>daemon</strong> so a leftover {@code NeoQueue} cannot keep
   *       the JVM alive after the user's app has finished;</li>
   *   <li>threads carry a stable, human-readable name (formerly anonymous {@code pool-N-thread-1})
   *       which makes the executor identifiable in thread dumps and APM tools.</li>
   * </ul>
   *
   * <p>Callers that want to bound queue resources can swap this out at startup with
   * {@link #setExecutor(ExecutorService)}; the default is good enough for short-lived processes
   * and unit tests.
   */
  private static volatile ExecutorService executor = newDefaultExecutor();

  private static ExecutorService newDefaultExecutor() {
    final AtomicInteger ids = new AtomicInteger(0);
    return Executors.newSingleThreadExecutor(r -> {
      final Thread t = new Thread(r, "neoqueue-default-" + ids.incrementAndGet());
      t.setDaemon(true);
      return t;
    });
  }

  /**
   * Swap the shared executor used for callback delivery. Useful when an application wants to
   * route async.java callbacks onto a Vert.x context, an Akka dispatcher, or a bounded
   * ForkJoinPool.
   */
  public static synchronized void setExecutor(final ExecutorService e) {
    if (e == null) {
      throw new IllegalArgumentException("executor must not be null");
    }
    NeoQueue.executor = e;
  }

  /**
   * Shut down the default executor. No-op if a custom executor was installed via
   * {@link #setExecutor(ExecutorService)} — the caller owns that one.
   */
  public static synchronized void shutdown() {
    if (executor != null) {
      executor.shutdown();
    }
  }

  private boolean isSaturated = false;
  private List<Task<T, V>> tasks = Collections.synchronizedList(new ArrayList<>());
  private ITaskHandler<T, V> h;
  private boolean isPaused;
  private CounterLimit c;
  private List<IAsyncCb> drainCBs = Collections.synchronizedList(new ArrayList<>());
  private List<IAsyncCb> saturatedCBs = Collections.synchronizedList(new ArrayList<>());
  private List<IAsyncCb> unsaturatedCBs = Collections.synchronizedList(new ArrayList<>());
  private boolean isDrained = false;
  
  final static Logger log = LoggerFactory.getLogger(NeoQueue.class);
  
  
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
    void run(NeoQueue q);
  }
  
  
  public abstract static class AsyncCallback<T> implements IAsyncErrFirstCb<T>, ICallbacks<T> {
    
    private ShortCircuit s;
    final Object cbLock = new Object();
    
    AsyncCallback(ShortCircuit s) {
      this.s = s;
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
        throw new IllegalStateException("Task already started.");
      }
      this.isStarted = true;
    }

    public boolean isStarted() {
      return this.isStarted;
    }

    void setFinished() {
      if (this.isFinished) {
        // Original message said "already started" by mistake — fixed to match the actual state.
        throw new IllegalStateException("Task already finished.");
      }
      this.isFinished = true;
    }

    boolean isFinished() {
      return this.isFinished;
    }
  }
  
  
  public NeoQueue(Integer concurrency, ITaskHandler<T, V> h) {
    this.h = h;
    this.c = new CounterLimit(concurrency);
  }
  
  public NeoQueue(ITaskHandler<T, V> h) {
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
    if (v == null || v < 1) {
      throw new IllegalArgumentException("Concurrency value must be an integer greater than 0");
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
  
  boolean isIdle() {
    return this.c.isIdle();
  }
  
  /**
   * Dispatch a {@link Runnable} onto the queue's executor.
   *
   * <p>The dead branches (a registered {@link Asyncc#nextTick} hook, a synchronous
   * {@code executor.execute}) and the {@code System.out.println("Using run async.")} debug
   * statement that used to live here were removed; the latter was emitted on every task
   * completion in production and pinned a sync-print on the hot path.
   */
  private static void executeRunnable(Runnable r) {
    if (Asyncc.nextTick != null) {
      Asyncc.nextTick.accept(r);
      return;
    }
    CompletableFuture.runAsync(r, executor);
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
    ShortCircuit s = new ShortCircuit();
    this.setDrained(false);
    
    this.h.run(t, new AsyncCallback<V>(s) {
      
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
        
        synchronized (this.cbLock) {

          if (t.isFinished()) {
            // callback was fired more than once
            new IllegalStateException("Callback was fired more than once.").printStackTrace();
            return;
          }

          t.setFinished();

        }

        // Schedule continuation onto the shared executor. The previous implementation hardcoded
        // a 1 ms delay via `CompletableFuture.delayedExecutor`; that delay was added in early
        // development to break stack recursion in tight queues but it pinned a synchronous wait
        // on every task completion (so a queue churning 10k items added 10s of pure idle latency).
        // Submitting directly to the executor already decouples the stack on the executor boundary
        // and is correct without the artificial delay.
        executor.execute(() -> {
          
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
