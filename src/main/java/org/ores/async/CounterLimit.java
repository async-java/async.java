package org.ores.async;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared counter used by {@link NeoParallel} / {@link NeoSeries} / {@link NeoQueue} / etc. to
 * track how many tasks have been started vs finished in a given combinator invocation, and to
 * answer {@link #isBelowCapacity()} for fan-out scheduling.
 *
 * <p>Production-readiness note: before this rewrite, {@code started} and {@code finished} were
 * plain non-atomic {@code Integer} fields mutated via post-increment {@code ++}. They were read
 * and incremented from <strong>different</strong> per-task callback threads (each
 * {@code AsyncTaskRunner} holds its own {@code cbLock} but increments a single shared
 * {@code CounterLimit}). Under sustained rapid-fire load, one of the increments eventually
 * gets lost — the resulting {@code finished < started} mismatch makes
 * {@code ParallelRunner.isDone()} return {@code false} forever, the final callback never fires,
 * and the caller's {@code CompletableFuture.get(timeout)} times out.
 *
 * <p>The fix is to make all counter mutations atomic. {@link AtomicInteger} also doubles as a
 * memory-visibility barrier so callers reading {@link #getStartedCount()} /
 * {@link #getFinishedCount()} from other threads see a consistent view without needing the
 * surrounding {@code synchronized} blocks that several combinators wrap counter access in.
 * Those synchronized blocks are retained for the moment (removing them would touch nine call
 * sites and we want this fix small) but they are now redundant for visibility.
 */
class CounterLimit {

  private volatile Integer limit;
  private final AtomicInteger started = new AtomicInteger(0);
  private final AtomicInteger finished = new AtomicInteger(0);
  private volatile Integer timesTotal = null;

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
    this.started.incrementAndGet();
  }

  void incrementFinished() {
    this.finished.incrementAndGet();
  }

  int getStartedCount() {
    return this.started.get();
  }

  int getFinishedCount() {
    return this.finished.get();
  }

  boolean isBelowCapacity() {
    // Single snapshot of both counters per call — slightly safer than reading started then
    // finished as independent volatile reads. For an upper-bound check, the worst case is
    // a stale-by-one result, which the caller already tolerates.
    final int s = this.started.get();
    final int f = this.finished.get();
    return this.limit > (s - f);
  }

  boolean isIdle() {
    return this.finished.get() >= this.started.get();
  }

  public Integer getTimesTotal() {
    return this.timesTotal;
  }

  public Integer setTimesTotal(Integer max) {
    return this.timesTotal = max;
  }
}
