package org.ores;


class CounterLimit {
  
  private int limit;
  private int started = 0;
  private int finished = 0;
  
  public CounterLimit(Integer limit){
    this.limit = limit;
  }
  
  public CounterLimit(){
    this.limit = 1;
  }
  
  void incrementStarted() {
    this.started++;
  }
  
  void incrementFinished() {
    this.finished++;
  }
  
  int getStartedCount() {
    return this.started;
  }
  
  int getFinishedCount() {
    return this.finished;
  }
  
  boolean isBelowCapacity() {
    return this.limit > (this.started - this.finished);
  }
}
