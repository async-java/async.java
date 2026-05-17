package org.ores.async;

/**
 * Internal flag-bag tracking the lifecycle of a single control-flow combinator invocation.
 *
 * <p>Originally only {@link #isFinalCallbackFired()} was {@code synchronized}; the other
 * accessors mutated {@code volatile}-less fields concurrently with the reads inside the
 * {@code synchronized} block, which means the JMM didn't establish a happens-before edge to
 * the threads that subsequently re-read these flags from another worker. Made every accessor
 * synchronized on {@code this} so all reads and writes share a monitor, which is the cheapest
 * way to get a consistent memory model here without rewriting callers.
 */
class ShortCircuit {

  private boolean isShortCircuited = false;
  private boolean isFinalCallbackFired = false;
  private boolean sameTick = true;

  public synchronized boolean isFinalCallbackFired() {
    return this.isFinalCallbackFired;
  }

  public synchronized void setFinalCallbackFired(boolean finalCallbackFired) {
    this.isFinalCallbackFired = finalCallbackFired;
  }

  public synchronized boolean isShortCircuited() {
    return this.isShortCircuited;
  }

  public synchronized boolean setShortCircuited(boolean v) {
    return this.isShortCircuited = v;
  }

  public synchronized boolean isSameTick() {
    return this.sameTick;
  }

  public synchronized void setSameTick(boolean sameTick) {
    this.sameTick = sameTick;
  }
}
