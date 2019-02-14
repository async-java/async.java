package org.ores.async;

import java.util.ArrayList;

public class NeoLock {
  
  private boolean locked = false;
  private String namespace;
  private ArrayList<Asyncc.IAsyncCallback> queue = new ArrayList<Asyncc.IAsyncCallback>();
  
  public NeoLock(String name) {
    this.namespace = name;
  }
  
  public abstract class Unlock {
    boolean isImmediate = false;
    boolean callable = true;
    
    public abstract void unlock();
    
    public Unlock(boolean isImmediate) {
      this.isImmediate = isImmediate;
    }
  }
  
  public static void test(){
    
    var sync = new NeoLock("foo");
    sync.lock((err,v) -> {
      
      
      v.unlock();
      
    });
  }
  
  public Unlock makeUnlock(boolean isImmediate) {
    var lck = this;
    return new Unlock(isImmediate) {
      @Override
      public void unlock() {
        
        synchronized (this){
          if(!this.callable){
            return;
          }
  
          callable = false;
        }
        
        lck.locked = false;
        if (queue.size() > 0) {
          queue.remove(0).done(null, lck.makeUnlock(false));
        }
      }
    };
  }
  
  public void lock(Asyncc.IAsyncCallback<Unlock, Object> cb) {
    
    boolean add = false;
    synchronized (this) {
      if (this.locked) {
        add = true;
      }
      else{
        this.locked = true;
      }
    }
    
    if(add){
      this.queue.add(cb);
      return;
    }
    
    cb.done(null, this.makeUnlock(true));
    
  }
  
  
}
