package org.ores;

class Counter {
  
  private int started = 0;
  private int finished = 0;
  
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
}