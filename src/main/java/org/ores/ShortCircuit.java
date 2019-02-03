package org.ores;

class ShortCircuit {
  
  private boolean isShortCircuited = false;
  
  public boolean isShortCircuited(){
    return this.isShortCircuited;
  }
  
  public boolean setShortCircuited(boolean v){
    return this.isShortCircuited = v;
  }
  
}
