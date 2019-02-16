package org.ores.async;

import java.util.ArrayList;

abstract class Unlock {
  boolean isImmediate = false;
  boolean callable = true;
  
  public abstract void releaseLock();
  
  public Unlock(boolean isImmediate) {
    this.isImmediate = isImmediate;
  }
}

public class NeoLock {
  
  private boolean locked = false;
  private String namespace;
  private ArrayList<Asyncc.IAsyncCallback> queue = new ArrayList<Asyncc.IAsyncCallback>();
  
  public NeoLock(String name) {
    this.namespace = name;
  }
  
  public Unlock makeUnlock(boolean isImmediate) {
    var lck = this;
    return new Unlock(isImmediate) {
      @Override
      public void releaseLock() {
        
        synchronized (this){
          if(!this.callable){
            return;
          }
  
          callable = false;
          lck.locked = false;
        }
        
        if (queue.size() > 0) {
          queue.remove(0).done(null, lck.makeUnlock(false));
        }
      }
    };
  }
  
  public void acquire(Asyncc.IAsyncCallback<Unlock, Object> cb) {
    
    boolean addToQueue = false;
    
    synchronized (this) {
      if (this.locked) {
        addToQueue = true;
      }
      else{
        this.locked = true;
      }
    }
    
    if(addToQueue){
      this.queue.add(cb);
      return;
    }
    
    cb.done(null, this.makeUnlock(true));
    
  }
  
  
}
