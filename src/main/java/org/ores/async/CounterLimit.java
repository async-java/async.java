package org.ores.async;

/*
 * <script src="https://cdn.rawgit.com/google/code-prettify/master/loader/run_prettify.js"></script>
 **/
class CounterLimit {
  
  private Integer limit;
  private Integer started = 0;
  private Integer finished = 0;
  private Integer timesTotal = null;
  
  public CounterLimit(Integer limit) {
    this.limit = limit;
  }
  
  public CounterLimit(Integer limit, Integer max) {
    this.limit = limit;
    this.timesTotal = max;
  }
  
  Integer getConcurrency() {
    return this.limit;
  }
  
  Integer setConcurrency(Integer val) {
    return this.limit = val;
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
  
  boolean isIdle() {
    return this.finished >= this.started;
  }
  
  public Integer getTimesTotal() {
    return this.timesTotal;
  }
  
  public Integer setTimesTotal(Integer max) {
    return this.timesTotal = max;
  }
}
