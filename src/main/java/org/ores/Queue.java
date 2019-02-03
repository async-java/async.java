package org.ores;

import java.util.ArrayList;
import java.util.List;

interface IAsyncCallback<T> {
  void done(Error e, T v);
}

interface ITaskHandler<T> {
  void run(T t, IAsyncCallback v);
}

class Task<T, V> {
  
  private T value;
  private IAsyncCallback<V> cb;
  private boolean isHasCallback;
  private boolean isStarted = false;
  private boolean isFinished = false;
  
  public Task(T value) {
    this.value = value;
  }
  
  public Task(T value, IAsyncCallback<V> cb) {
    this.value = value;
    this.cb = cb;
    this.isHasCallback = true;
  }
  
  public boolean hasCallback() {
    return this.isHasCallback;
  }
  
  public IAsyncCallback<V> getCallback() {
    return this.cb;
  }
  
  public T getValue() {
    return this.value;
  }
  
  public void setStarted(){
    if(this.isStarted){
      throw new Error("Task already started.");
    }
    this.isStarted = true;
  }
  
  public boolean isStarted(){
    return this.isStarted;
  }
  
  public void setFinished(){
    if(this.isFinished){
      throw new Error("Task already started.");
    }
    this.isFinished = true;
  }
  
  public boolean isFinished(){
    return this.isFinished;
  }
}


public class Queue<T> {
  
  private ArrayList<Task<T, Object>> tasks = new ArrayList<>();
  private ITaskHandler h;
  private boolean paused;
  private CounterLimit c;
  
  public static void main() {
    new Queue<String>((task, v) -> {
      v.done(null, null);
    });
  }
  
  private Integer concurrency;
  
  public Queue(Integer concurrency, ITaskHandler<T> h) {
    this.concurrency = concurrency;
    this.h = h;
    this.c = new CounterLimit(concurrency);
  }
  
  public Queue(ITaskHandler<T> h) {
    this.concurrency = 1;
    this.c = new CounterLimit(1);
    this.h = h;
  }
  
  public <V> void push(Task<T, Object> t) {
    this.tasks.add(t);
    if (this.paused) {
      return;
    }
    this.processTasks();
  }
  
  public void unshift(Task<T, Object> t) {
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
    return true;
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
    
    Task<T, Object> t = this.tasks.remove(0);
    t.setStarted();  // signify that the task has started so it can't be removed anymore by the user
    
    this.c.incrementStarted();
    
    this.h.run(t, (e, v) -> {
      
      this.c.incrementFinished();
      
      if (t.hasCallback()) {
        t.getCallback().done(e, v);
      }
      
      if (this.paused) {
        return;
      }
      
      this.processTasks();
      
    });
    
    this.processTasks();
    
  }
  
  
}
