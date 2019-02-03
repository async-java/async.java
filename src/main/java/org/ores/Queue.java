package org.ores;

import java.util.ArrayList;
import java.util.ListIterator;

interface IAsyncCallback<T> {
  void done(Object e, T v);
}

interface ITaskHandler<T,V> {
  void run(Task<T,V> t, IAsyncCallback<V> v);
}

interface ICallbacks<T> {
  void resolve(T v);
  void reject(Object e);
//		 void done(E e, T... v);
}

class Task<T, V> {
  
  private T value;
  private ArrayList<IAsyncCallback<V>> cbs = new ArrayList<>();
  private boolean isStarted = false;
  private boolean isFinished = false;
  
  public Task(T value) {
    this.value = value;
  }
  
  public Task(T value, IAsyncCallback<V> cb) {
    this.value = value;
    this.cbs.add(cb);
  }
  
  public ArrayList<IAsyncCallback<V>> getCallbacks() {
    return this.cbs;
  }
  
  public void addCallback(IAsyncCallback<V> cb) {
    this.cbs.add(cb);
  }
  
  public T getValue() {
    return this.value;
  }
  
  public void _setStarted() {
    if (this.isStarted) {
      throw new Error("Task already started.");
    }
    this.isStarted = true;
  }
  
  public boolean isStarted() {
    return this.isStarted;
  }
  
  public void _setFinished() {
    if (this.isFinished) {
      throw new Error("Task already started.");
    }
    this.isFinished = true;
  }
  
  public boolean isFinished() {
    return this.isFinished;
  }
}

abstract class AsyncCallback<T> implements IAsyncCallback<T>, ICallbacks<T> {
  
  private ShortCircuit s;
  
//  public AsyncCallback(ShortCircuit s){
//    this.s = s;
//  }
  
  public AsyncCallback(){
  
  }
  
  public boolean isShortCircuited(){
    return this.s.isShortCircuited();
  }
  
}


public class Queue<T,V> {
  
  private ArrayList<Task<T, V>> tasks = new ArrayList<>();
  private ITaskHandler<T,V> h;
  private boolean paused;
  private CounterLimit c;
  
  public static void main() {
    
    Queue q = new Queue<Integer,Integer>((task, v) -> {
      v.done(null, null);
    });
    
    q.push(new Task<Integer, Integer>(3, (err, v) -> {
    
    
    }));
    
  }
  
  
  public Queue(Integer concurrency, ITaskHandler<T,V> h) {
    this.h = h;
    this.c = new CounterLimit(concurrency);
  }
  
  public Queue(ITaskHandler<T,V> h) {
    this.c = new CounterLimit(1);
    this.h = h;
  }
  
  public Integer getConcurrency() {
    return this.c.getConcurrency();
  }
  
  public Integer setConcurrency(Integer v) {
    if (v < 1) {
      throw new Error("Concurrency value must be an integer greater than 0");
    }
    return this.c.setConcurrency(v);
  }
  
  public void nudge() {
    // poke, prod, nudge, etc
    this.processTasks();
  }
  
  public void push(Task<T, V> t) {
    this.tasks.add(t);
    if (this.paused) {
      return;
    }
    this.processTasks();
  }
  
  public void push(Task<T, V> t, IAsyncCallback<V> cb) {
    t.addCallback(cb);
    this.tasks.add(t);
    if (this.paused) {
      return;
    }
    this.processTasks();
  }
  
  public void unshift(Task<T, V> t) {
    this.tasks.add(0, t);
    if (this.paused) {
      return;
    }
    this.processTasks();
  }
  
  public void pause() {
    this.paused = true;
  }
  
  public void resume() {
    
    if (!this.paused) {
      return;
    }
    
    this.paused = false;
    this.processTasks();
  }
  
  public boolean isIdle() {
    return this.c.isIdle();
  }
  
  private synchronized void processTasks() {
    
    if (this.paused) {
      return;
    }
    
    if (!this.c.isBelowCapacity()) {
      return;
    }
    
    if (tasks.size() < 1) {
      return;
    }
    
    Task<T, V> t = this.tasks.remove(0);
    t._setStarted();  // signify that the task has started so it can't be removed anymore by the user
    
    this.c.incrementStarted();
    Queue<T,V> q = this;
    
    this.h.run(t, new AsyncCallback<V>() {
  
      @Override
      public void resolve(V v) {
        this.done(null,v);
      }
  
      @Override
      public void reject(Object e) {
        this.done(e,null);
      }
      
      @Override
      public void done(Object e, V v) {
        
        if (t.isFinished()) {
          // callback was fired more than once
          return;
        }
  
        q.c.incrementFinished();
        t._setFinished();
  
  
        ListIterator<IAsyncCallback<V>> iter = t.getCallbacks().listIterator();
  
        while (iter.hasNext()) {
          IAsyncCallback<V> cb = iter.next();
          iter.remove();
          cb.done(e, v);
        }
  
        //  for (IAsyncCallback cb : t.getCallbacks()) {
        //    cb.done(e, v);
        //  }
  
        if (q.paused) {
          return;
        }
  
        q.processTasks();
      }
  
  
    });
    
    this.processTasks();
    
  }
  
}
