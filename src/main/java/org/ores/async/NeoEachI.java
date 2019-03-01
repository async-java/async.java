package org.ores.async;

public class NeoEachI {
  
  public interface AsyncEachTask<E> {
    void run(Asyncc.IEachCallback<E> cb);
  }
  
  public static abstract class EachCallback<E> extends Asyncc.AsyncCallback<Object,E> implements Asyncc.IEachCallback<E>, Asyncc.IEachCallbacks<E> {
    
    private ShortCircuit s;
    private boolean isFinished = false;
    final Object cbLock = new Object();
    
    public EachCallback(ShortCircuit s) {
      super(s);
      this.s = s;
    }
    
    public boolean isShortCircuited() {
      return this.s.isShortCircuited();
    }
    
    boolean isFinished() {
      return this.isFinished;
    }
    
    boolean setFinished(boolean b) {
      return this.isFinished = b;
    }
    
    @Override
    public void resolve() {
      this.done(null);
    }



//    @Override
//    public void reject(E e) {
//      this.done(e);
//    }
  
  }
}
