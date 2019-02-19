package org.ores.async;

class ShortCircuit {
  
  private boolean isShortCircuited = false;
  private boolean isFinalCallbackFired = false;
  private boolean sameTick = true;
  
  public synchronized boolean  isFinalCallbackFired() {
    return this.isFinalCallbackFired;
  }
  
  public void setFinalCallbackFired(boolean finalCallbackFired) {
    this.isFinalCallbackFired = finalCallbackFired;
  }
  
  public boolean isShortCircuited(){
    return this.isShortCircuited;
  }
  
  public boolean setShortCircuited(boolean v){
    return this.isShortCircuited = v;
  }
  
  public boolean isSameTick() {
    return this.sameTick;
  }
  
  public void setSameTick(boolean sameTick) {
    this.sameTick = sameTick;
  }
}
