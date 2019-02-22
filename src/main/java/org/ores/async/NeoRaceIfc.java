package org.ores.async;

public class NeoRaceIfc {
  
  public static interface AsyncTask<T, E> {
    public void run(RaceCallback<T, E> cb);
  }
  
  public static class RaceParam<T> {
    
    public Boolean keep = true;
    public final T value;
    
    public RaceParam(T t){
      this.value = t;
    }
    
    public RaceParam(Boolean b, T t){
      this.keep = b;
      this.value = t;
    }
  }
  
  public static interface IMapper<T, V, E> {
    void map(T v, RaceCallback<V, E> cb);
  }
  
  public abstract static class RaceCallback<T,E> extends Asyncc.AsyncCallback<T,E> {
    
    public RaceCallback(ShortCircuit s){
      super(s);
    }
    
    public RaceParam setValue(T v){
      return new RaceParam<T>(v);
    }
    
    public RaceParam setValue(boolean b, T v){
      return new RaceParam<T>(b,v);
    }
    
  }
}
