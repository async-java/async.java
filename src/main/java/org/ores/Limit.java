package org.ores;


class Limit {
  private int val;
  private int current = 0;
  
  Limit(int val) {
    this.val = val;
  }
  
  public Limit() {
    this.val = 1;
  }
  
  public int getVal() {
    return this.val;
  }
  
  public int getCurrent() {
    return this.current;
  }
  
  void increment() {
    this.current++;
  }
  
  void decrement() {
    this.current--;
  }
  
  boolean isBelowCapacity() {
    return this.current < this.val;
  }
  
}