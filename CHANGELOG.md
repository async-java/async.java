# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.7] - 2026-05-18

Promise / `CompletableFuture` interop. Two additions, no behaviour changes
to existing combinators:

### Added

- **`org.ores.async.WrapFuture`** — bidirectional adapters between
  async.java's error-first callback shape and the JDK's promise primitive
  (`CompletableFuture` / `CompletionStage`):

  - `toFuture(Consumer<IAsyncCallback<V, Throwable>>) -> CompletableFuture<V>`
    — adapt a combinator invocation into a promise at the boundary. Pass
    the supplied callback directly as the final callback of any
    combinator and the future mirrors the outcome.
  - `toFutureAny(Consumer<IAsyncCallback<V, E>>) -> CompletableFuture<V>`
    — variant for non-`Throwable` error types; wraps in
    `RuntimeException` for the future's exceptional completion.
  - `fromStage(CompletionStage<V>) -> Asyncc.AsyncTask<V, Throwable>` —
    adapt a third-party promise (JDBC async driver, HTTP client) as an
    async.java task that can drop into any combinator's task position.
  - `fromCallable(Executor, Callable<V>) -> Asyncc.AsyncTask<V, Throwable>`
    — wrap synchronous (possibly blocking) work and dispatch onto the
    provided executor.

  8 tests in `WrapFutureTest` covering success, throwable-passthrough,
  non-throwable wrapping, sync-failure in setup, end-to-end with
  `Asyncc.Parallel`, `fromStage` interop, `fromCallable` success and
  exception paths.

- **`org.ores.async.AsyncFut`** — promise-returning sibling to `Asyncc`.
  Every combinator returns a `CompletableFuture` instead of taking a
  final callback:

  - `AsyncFut.Parallel(List<Supplier<CompletionStage<T>>>) -> CompletableFuture<List<T>>`
  - `AsyncFut.ParallelLimit(int, ...) -> CompletableFuture<List<T>>` (concurrency-capped)
  - `AsyncFut.Series(...) -> CompletableFuture<List<T>>` (sequential)
  - `AsyncFut.Race(...) -> CompletableFuture<T>` (first-to-finish wins)
  - `AsyncFut.Map(Iterable<T>, Function<T, CompletionStage<V>>) -> CompletableFuture<List<V>>`
  - `AsyncFut.Reduce(Iterable<T>, V identity, BiFunction<V, T, CompletionStage<V>>) -> CompletableFuture<V>`
  - `AsyncFut.Times(int n, IntFunction<CompletionStage<T>>) -> CompletableFuture<List<T>>`
  - `AsyncFut.Each(int limit, Iterable<T>, Function<T, CompletionStage<Void>>) -> CompletableFuture<Void>`

  Implemented in terms of `Asyncc` (via `WrapFuture`), so every combinator
  inherits the v0.2.x concurrency hardening: at-most-once final callback,
  slot-write-before-counter-increment ordering, no `ArrayList` resize
  race, `ParallelLimit` strict `<= limit`-in-flight invariant. The
  wrapper layer adds one `CompletableFuture` allocation per call (~5 µs).

  14 tests in `AsyncFutTest` covering: ordered Parallel collection, fail-
  fast short-circuit, ParallelLimit cap enforcement, sequential Series
  execution, fastest-wins Race, ordered Map, sequential Reduce, indexed
  Times, fire-and-forget Each, composition with `thenApply`/`thenCompose`,
  empty-input edge case, supplier-throws-synchronously surfacing.

### Use cases

Most common: return a `CompletableFuture` to a framework boundary
(Spring WebFlux, Akka HTTP, gRPC stub) while using async.java's
combinators internally:

```java
CompletableFuture<String> handle(Request req) {
    return WrapFuture.toFuture(c ->
        Asyncc.<String, Throwable>Parallel(List.of(
            cb -> exec.submit(() -> cb.success(fetchA(req))),
            cb -> exec.submit(() -> cb.success(fetchB(req)))
        ), c)
    ).thenApply(parts -> combine(parts.get(0), parts.get(1)));
}
```

Or equivalently, using `AsyncFut`:

```java
CompletableFuture<String> handle(Request req) {
    return AsyncFut.Parallel(List.of(
        () -> CompletableFuture.supplyAsync(() -> fetchA(req), exec),
        () -> CompletableFuture.supplyAsync(() -> fetchB(req), exec)
    )).thenApply(parts -> combine(parts.get(0), parts.get(1)));
}
```

Or consuming `CompletionStage`-returning third-party APIs inside an
async.java combinator:

```java
Asyncc.Parallel(List.of(
    WrapFuture.fromStage(db.queryAsync("SELECT ...")),
    WrapFuture.fromStage(redis.getAsync(key))
), (err, results) -> { /* ... */ });
```

Total: 152 tests, 0 failures, 2 JDK21-gated skips.

## [0.2.6] - 2026-05-17

Adds {@code NeoRwLock} — an async reader/writer lock alongside the
existing {@link NeoLock} mutex. Same callback-shape API; same {@link Unlock}
release token; FIFO-with-reader-burst fairness so writers don't starve
under bursty reads and readers don't starve under steady writes.

### Added

- **`org.ores.async.NeoRwLock`** — new sibling primitive to `NeoLock`.

  API surface:
  - `acquireRead(IAsyncCallback)` / `acquireWrite(IAsyncCallback)` —
    happy-path async acquire.
  - `tryAcquireRead()` / `tryAcquireWrite()` → `Optional<Unlock>` —
    non-blocking attempt. Returns empty if waiters are queued (preserves
    FIFO).
  - `acquireRead(long timeoutMs, IAsyncCallback)` /
    `acquireWrite(long timeoutMs, IAsyncCallback)` — bounded wait;
    cleanly removes the waiter from the queue on timeout; handles the
    grant-arrives-just-after-timeout race by releasing the late-arrival
    lock so it's not stranded.
  - `withRead(Runnable)` / `withWrite(Runnable)` — sync critical-section
    helpers. Auto-release even when the body throws (eliminates the
    try/finally/unlock.releaseLock boilerplate).
  - `readerCount()` / `isWriteHeld()` / `queueDepth()` — diagnostics.
  - No-arg constructor and `NeoRwLock(String namespace)` constructor.

  Fairness policy: **FIFO with reader-burst**. Waiters dispatch in
  arrival order. When the lock becomes free and the queue head is a
  reader, *all adjacent queued readers wake up concurrently*. When the
  head is a writer, exactly one writer is dispatched (and must complete
  before the next batch). Adjacent queued readers behind a writer wait
  for the writer to release, then burst together.

  Not supported in v0.2.6 (documented in Javadoc): reader-to-writer
  upgrade, writer-to-reader downgrade, reentrance. Use
  `tryAcquireRead()` / `tryAcquireWrite()` for defensive checks.

  Pinned by **`NeoRwLockTest` (14 tests)** covering:
  - Mutual exclusion under 500-task mixed load on a 16-thread pool: no
    reader concurrent with a writer, no two writers concurrent.
  - Reader-burst stress: 50 readers queued behind a write — when the
    write releases, all 50 hold the lock concurrently (maxConcurrent ==
    50).
  - Mixed-mode FIFO: queue `[R1,R2,R3,W4,R5,R6]` dispatches as
    `(R1+R2+R3 burst) → W4 alone → (R5+R6 burst)`.
  - Writers dispatch one-at-a-time in arrival order behind a holder.
  - Timeouts cleanly remove the waiter; subsequent release doesn't
    strand it.
  - withRead/withWrite release the lock even when the body throws.
  - All introspection counters (readerCount, isWriteHeld, queueDepth)
    snapshot consistently.

### Not yet (deferred to v0.2.7+)

- **Reader-to-writer upgrade** — design avoids the classic two-readers-
  both-upgrade deadlock; needs a coordination protocol (per-thread
  "upgrading" state + abort-the-other policy).
- **Cancellable wait** — the new `tryAcquireRead/Write` and
  `acquireRead/Write(timeoutMs, ...)` cover most use cases; a generic
  cancel-from-outside-the-callback API would need a returned
  cancellation token.

## [0.2.5] - 2026-05-17

Hardening release. One real concurrency fix in `Asyncc.ParallelLimit`, the
first-ever set of functional tests for `NeoQueue`, a substantial audit of
`NeoLock` with five new API additions, and a Waterfall-specific
`success(k, v)` / `fail(e)` shorthand. No public-API breakages.

### Fixed

- **`Asyncc.ParallelLimit(int, List, callback)` could dispatch `limit + 1`
  tasks momentarily under contention.** Two completion callbacks racing
  through the dispatch gate could each observe `isBelowCapacity() == true`
  before either landed an `incrementStarted`, so both proceeded to
  dispatch. Moving the `isBelowCapacity` check inside the iterator
  monitor (alongside the increment) makes check-and-act atomic. The
  previously-relaxed `MisuseTest#parallelLimitRespectsConcurrencyCap`
  assertion is tightened from `<= limit + 1` to `<= limit`, and a new
  `ParallelLimitInvariantTest` pins the invariant across 100
  back-to-back iterations.

- **`Asyncc.ParallelLimit(int, List, callback)` carried the same
  `ArrayList` resize race that v0.2.4 fixed for the simple `Parallel`
  path.** `RunTasksLimit.run()` did `results.add(null)` once per task
  inside the dispatch loop; a fast-completing sibling could call
  `results.set(j, v)` mid-resize and have the write land on the old
  backing array. The fix is the same: pre-allocate and pre-fill the
  result list to `size` before dispatching any task.

### Added

- **`NeoLock.tryAcquire()`** — returns `Optional<Unlock>`; non-blocking
  attempt. Empty if the lock was already held.

- **`NeoLock.acquire(long timeoutMs, IAsyncCallback)`** — bounded-wait
  variant. Fires the callback with a `TimeoutException` if the lock is
  not acquired within `timeoutMs` milliseconds, cleanly removing the
  pending waiter from the FIFO queue. Internally backed by a shared
  daemon-threaded `ScheduledExecutorService`. Handles the
  winner-arrives-after-timeout race: if the lock is granted just as the
  timeout fires, the late-arrival lock is released on the caller's
  behalf so it isn't stranded. Pinned by 4 tests in
  `NeoLockNewApiTest`.

- **`NeoLock.withLock(Runnable)`** — sync critical-section helper.
  Acquires, runs the runnable, releases. Releases **even if the body
  throws**, eliminating the `try / finally / unlock.releaseLock()`
  boilerplate. Pinned by `NeoLockNewApiTest#withLock_releases_lock_even_when_body_throws`.

- **`NeoLock.isLocked()`** and **`NeoLock.queueDepth()`** — diagnostics
  / metrics. Snapshot of whether the lock is held and how many waiters
  are queued behind the holder.

- **`NeoLock()`** no-arg constructor — for callers that don't care about
  the namespace string.

- **`NeoWaterfallI.IAsyncCallback.success(String k, T v)`** — Waterfall
  multi-value continuation shorthand. `c.success("name", value)` is
  equivalent to `c.done(null, "name", value)`. Overrides the inherited
  `Asyncc.IAsyncCallback.fail(E)` to route through the Waterfall-specific
  single-arg `done(E)` (so `c.fail(err)` propagates the error correctly
  rather than calling `done(err, (Map.Entry) null)`).

- **`NeoQueue.IAsyncErrFirstCb.success(T v)` and `.fail(Object e)`**
  default methods — same shape as the Asyncc-level shorthands. Lets
  queue task handlers write `c.success(value)` / `c.fail(error)`
  consistently with the rest of the library.

- **First functional tests for `NeoQueue`.** Pre-v0.2.5 the only
  `QueueTest` was entirely commented-out. Now covered by:
    * `NeoQueueConcurrencyTest` (5 tests) — burst push at caps 1, 4, 16
      with 100, 200, 500 tasks; trickle push from a separate producer
      thread; repeated burst scenario 20× back-to-back. All assert that
      max in-flight never exceeds the configured cap.
    * `NeoQueueLifecycleTest` (4 tests) — pins `onSaturated`,
      `onUnsaturated`, and `onDrain` semantics (exactly once per
      backlog episode, drain fires again for subsequent bursts,
      saturated does not fire below cap).

- **First functional tests for `NeoLock`.** Pre-v0.2.5 only a thin
  external-usage test existed. Now covered by:
    * `NeoLockFairnessTest` (2 tests) — FIFO fairness across 100
      sequential acquires; 1 000-acquirer stress test asserting no
      lost wakeups, no double-hold, exact counter consistency.
    * `NeoLockNewApiTest` (9 tests) — every new v0.2.5 API method
      with positive, negative, and race-condition cases.

- **`NeoQueue` and `NeoLock` Javadoc rewrites** — both classes now have
  top-level Javadoc documenting their concurrency contracts (NeoQueue's
  saturated/unsaturated/drain lifecycle semantics, NeoLock's FIFO
  fairness guarantee, VT-pinning audit notes, non-reentrance, the new
  API surface).

### Changed

- **`MisuseTest#parallelLimitRespectsConcurrencyCap` tightened** —
  asserts `maxInFlight <= limit` (was `<= limit + 1`). The comment
  documenting the previously-tolerated off-by-one is replaced with a
  reference to `ParallelLimitInvariantTest`.

### Not yet (deferred to v0.2.6+)

- **`NeoLock` cancellable waits** (let an acquirer remove its own
  waiter callback from the queue) — the new `tryAcquire()` and
  `acquire(timeoutMs)` cover most of the same use cases.
- **`NeoLock` migration to `ReentrantLock` for older-JDK VT-friendliness**
  — audited and judged unnecessary for v0.2.5 (critical sections are
  microseconds, no observable contention under 1k-acquirer stress).
  Documented in the class Javadoc.
- **`NeoQueue` global `Asyncc.sync` lock** in the completion path
  serialises every queue's completion callbacks globally; replacing
  with a per-queue monitor is a bigger refactor and a v0.3.0 candidate.

## [0.2.4] - 2026-05-17

Ergonomics release plus one real concurrency fix. More concise call sites
for the common case and a substantial Javadoc rewrite. The at-most-once
final-callback contract, short-circuit semantics, and counter-correctness
properties from v0.2.0 - v0.2.2 all still apply.

### Fixed

- **`NeoParallel.Parallel(List, callback)` could lose result-slot writes
  under high-throughput fan-out.** The dispatch loop grew the result
  `ArrayList` by `add(null)` *inside* the iteration that submitted tasks:

  ```java
  for (int i = 0; i < size; i++) {
      results.add(null);            // may resize the backing array
      tasks.get(i).run(taskRunner); // may complete on another thread
                                    // immediately and call results.set(j,v)
  }
  ```

  `ArrayList` grows its backing array geometrically (10 → 15 → 22 → 33 →
  ...). When the main thread was mid-resize (allocating a new backing
  array and copying), a concurrent `results.set(j, v)` from a
  fast-completing task could land on the **old** backing array, after
  which the new array replaced the field reference — silently losing the
  write. The slot then read as `null` in the final callback. Surfaced
  intermittently by
  `MisuseTest#parallelCrossThreadCallbackThousandFanOut` (1 000 fan-out
  tasks × 16 threads, position varied across runs: 15, 429, etc.). Pinned
  with a regression test running the scenario 5× back-to-back.

  Fix: pre-allocate **and** pre-fill the result list to `size` *before*
  submitting any task. The backing array is now stable for the duration
  of the combinator call and every task writes to the same array.

  This bug only fires when at least one task completes before the main
  thread finishes adding nulls. Sub-millisecond completion latencies
  on a pre-warmed VT executor are enough.

### Added

- **`IAsyncCallback.success(V)` and `IAsyncCallback.fail(E)`** default
  methods. Equivalent to `done(null, v)` / `done(e, null)` but read
  cleaner at call sites:

  ```java
  // before
  c.done(null, value);   c.done(err, null);
  // after (still works, additive only)
  c.success(value);      c.fail(err);
  ```

  Existing callers pass `(e, v) -> ...` lambdas and need no changes; the
  shorthands are available on any `IAsyncCallback` instance.

- **`org.ores.async.WrapErrFirst`** — adapter that wraps a value-only
  consumer (or a value-consumer + error-consumer pair) into the canonical
  error-first callback. Reduces the boilerplate `if (err != null) {
  handle(err); return; }` preamble at every callback site:

  ```java
  import static org.ores.async.WrapErrFirst.wrap;

  Asyncc.Parallel(tasks, wrap(results -> {
      var scored = score(req, results.get(0), results.get(1));
      reply.send(serialize(scored));
  }));
  ```

  The single-arg form throws `RuntimeException` (preserving the original
  `Throwable` as the cause when available) on unhandled errors. The
  two-arg form `wrap(onSuccess, onError)` keeps both branches explicit.

  See `WrapErrFirstTest` for the contract: success path, throw-on-error
  with `Throwable` cause, throw-on-error with non-`Throwable` object,
  two-arg routing, end-to-end integration with `Asyncc.Parallel`.

- **Conventional parameter name `c` for continuations.** The docs now
  consistently use `c` instead of `cb` in code examples — `c` is short
  for *continuation*, which is what the callback parameter actually is
  in this library (the "what happens next" of an async step). This is
  a documentation convention, not an API change; existing code using
  `cb` or any other name keeps working unchanged.

### Changed

- **Minimum JDK is now 17** (was 11). Both downstream consumers in the
  k8s-cluster monorepo (`dd-spark-pipeline-server` on JDK 17,
  `dd-akka-ws-server` on JDK 21) already run on 17+. The library now uses
  `instanceof` pattern matching (Java 16+) in `WrapErrFirst` and assumes
  JDK 17 source/target throughout. CI matrix is now `17, 21`.

  Consumers still on JDK 11 should pin `v0.2.3` or `v0.2.2`.

### Why an ergonomics release rather than 0.3.0

The library has a stable public surface and v0.2.x has been about
hardening correctness under load (CounterLimit race, NeoParallel double
fire, slot-write-order). Adding `success`/`fail` and `WrapErrFirst` is
strictly additive — no callers break. The JDK floor bump from 11 to 17
is the only thing that argues for 0.3.0, but in practice every consumer
we know about is already on 17+ and the original tier-up to JDK 11 was
itself a quiet patch-release move. Keeping the 0.2.x line means we can
keep shipping minor improvements without forcing every downstream to
update major-version dependency pins.

## [0.2.3] - skipped

Numbered for the doc work that ended up rolled into 0.2.4. No 0.2.3 tag
was ever cut.

## [0.2.2] - 2026-05-17

Visibility / API-accessibility hardening release. No combinator behaviour
changes for well-behaved code; two real bugs fixed that bit downstream
consumers in 0.2.1.

### Fixed

- **`NeoParallel.Parallel(List, callback)` published `null` slots under
  high fan-out concurrency.** The per-task callback used to call
  `c.incrementFinished()` *before* `results.set(index, v)`. The counter
  is an `AtomicInteger` (since #9), so a sibling runner reading
  `finishedCount == size` saw the increment but not necessarily our slot
  write — the final callback fired with one or more `null` entries.
  Surfaced reliably by `MisuseTest#parallelCrossThreadCallbackThousandFanOut`
  (1 000-task fan-out on 16 platform threads, position N null within a
  single run). Fixed by swapping the two lines: write slot, then
  increment counter, so the counter increment "publishes" the prior slot
  write via the JMM happens-before edge.

- **Same publish-order bug in `NeoMap.RunMapWithList` and
  `NeoMap.RunMapWithMap`.** Identical fix.

- **`Unlock` was package-private**, which made `NeoLock.acquire(...)`
  effectively unusable from outside `org.ores.async`: the `Unlock` token
  type couldn't be named in downstream consumers' lambdas or fields, so
  even calling `.releaseLock()` on it wouldn't compile. Promoted to
  `public` and moved to its own file. Pinned by
  `NeoLockExternalUsageTest`, which imports `Unlock` from package
  `general` — a build that fails to expose `Unlock` cannot compile the
  test.

### Known issues

- `Asyncc.ParallelLimit(limit, ...)` occasionally dispatches `limit + 1`
  tasks concurrently. The `isBelowCapacity` gate that controls dispatch
  runs *outside* the per-runner `cbLock`, so two task-completion
  callbacks racing through the gate can each pass the check before either
  dispatch lands a `started` increment.
  `MisuseTest#parallelLimitRespectsConcurrencyCap` documents the
  off-by-one (asserts `<= limit + 1`). Same shape of fix as PR #10's
  success-path dedup; deferred to a tighter follow-up.

## [0.2.1] - 2026-05-17

Hardening release. No new features; covers the long tail of "misbehaving
user code" edge cases discovered while writing
[dd-akka-ws-server](https://github.com/ORESoftware/k8s-cluster/tree/dev/remote/akka-ws-server)
and
[dd-spark-pipeline-server](https://github.com/ORESoftware/k8s-cluster/tree/dev/remote/spark-pipeline-server)
against 0.2.0.

### Fixed

- `NeoReduce`: the per-step done callback used to call `f.done(...)`
  directly without the shared `NeoUtils.fireFinalCallback` guard, so if a
  user's reducer fired its callback more than once for the same step
  (mostly likely a bug in the user's code, but real in the wild) the
  library would also fire the final callback more than once. Routed
  through `fireFinalCallback` for parity with `NeoEach`, `NeoMap`,
  `NeoSeries`, `NeoFilterMap`, `NeoGroupBy`, and `NeoParallel`. In
  steady-state Reduce is sequential so the race could not manifest in
  well-behaved code — but the at-most-once contract now holds for
  misbehaving code too.

### Added

- `MisuseTest` — twelve adversarial / "user does something weird" tests
  that pin the at-most-once contract for every combinator:
  1. Double `cb.done(...)` inside one parallel task.
  2. Synchronous throw inside a parallel task body.
  3. Empty input list (Parallel).
  4. Cross-thread `cb.done` at 1 000-fan-out parallel.
  5. First-task-errors short-circuit with late-completing siblings.
  6. `Parallel` nested inside `Waterfall`.
  7. `Map` task that throws mid-list.
  8. `Reduce` reducer that fires its callback twice for the same step.
  9. `Series` aborts on first error without running subsequent tasks.
  10. `ParallelLimit` actually respects the concurrency cap.
  11. `Race` fires the final callback exactly once even with late completers.
  12. User code that throws from inside the user's own final callback.

All 12 are platform-independent (no virtual-thread requirement, so they
run on JDK 11+).

## [0.2.0] - 2026-05-17

First release under the new `io.github.async-java:async-java` coordinate. The
legacy `com.oresoftware:async.0.1:0.1.1012` artifact remains on Maven Central
indefinitely for existing consumers.

### Fixed (concurrent-load correctness)

Two long-latent races in `NeoParallel` / `CounterLimit` that hung or
double-fired `Asyncc.Parallel` under sustained concurrent load. Both
were invisible to the legacy single-threaded test suite; they surfaced
when a downstream consumer drove `Asyncc.Parallel` rapid-fire against a
virtual-thread executor and watched ~5 % of WebSocket responses go
missing.

- **#9** — `CounterLimit.{started, finished}` were plain `Integer`
  fields incremented via non-atomic `this.field++` from per-task
  callbacks. Two parallel-task callbacks finishing nearly
  simultaneously could lose one increment, after which
  `finished < started` forever and the final callback never fired.
  Switched both fields to `AtomicInteger`. Symptom pre-fix: 100
  sequential `Asyncc.Parallel` calls timed out by iteration ~40 on
  JDK 21.
- **#10** — `NeoParallel.Parallel(List, callback)` called `f.done(...)`
  directly on both the error and the "last task finished" success paths,
  bypassing the shared `NeoUtils.fireFinalCallback` dedup guard that
  every other combinator routes through. Two task runners finishing
  nearly simultaneously could each observe `finishedCount == size` and
  each invoke the user's final callback. Routed both paths through
  `fireFinalCallback`. Symptom pre-fix: 50 concurrent producers × 200
  `Asyncc.Parallel` iterations produced 621 duplicate final-callback
  fires out of 10 000 invocations (~6 % double-fire rate).

Both reproducers ship as JUnit tests
(`CounterLimitRaceTest` and `ConcurrentParallelDropTest`); they require
JDK 21 for virtual threads and `Assume`-skip on JDK 11 / 17.

### Added

- `NeoQueue.setExecutor(ExecutorService)` lets applications route queue
  continuations onto their own event-loop / dispatcher.
- `NeoQueue.shutdown()` for clean JVM exit when running on the default
  executor.
- `LICENSE` file (MIT). The `pom.xml` already declared MIT but no file was
  checked in, so downstream users couldn't satisfy the redistribution
  clause of the license itself.
- `.github/workflows/ci.yml` matrix-runs `mvn test` on JDK 11, 17, and 21
  for every push and PR.
- `.github/workflows/release.yml` cuts releases to the Sonatype Central
  Portal on a `v*` git tag.
- `jitpack.yml` so any tag / branch / commit SHA is buildable on demand
  via JitPack.
- `RELEASING.md` end-to-end maintainer runbook (namespace verification,
  GPG, secrets, tag-driven release, manual fallback, yank policy).
- Documentation: dedicated `Project Loom and async.java` section in the
  readme covering the concrete co-design points between Loom (JDK 21+
  virtual threads + `StructuredTaskScope` preview) and this library —
  the bounded-fan-out × cheap-blocking pairing
  (`NeoQueue.setExecutor(Executors.newVirtualThreadPerTaskExecutor())`),
  carrier-thread pinning behaviour of `NeoLock` vs `synchronized` vs
  `ReentrantLock` (and the JEP 491 unpin in JDK 24), the divide of
  responsibilities between `StructuredTaskScope` and this library's
  combinator surface, `ThreadLocal` -> `ScopedValue` guidance for
  cross-thread callback state, and Vert.x's
  `ThreadingModel.VIRTUAL_THREAD` deployment option.

### Changed

- **Coordinate**: published to `io.github.async-java:async-java` going
  forward. The `io.github.async-java` namespace is auto-verifiable on the
  Sonatype Central Portal because the `async-java` GitHub organisation
  exists at <https://github.com/async-java>.
- **Build target**: JDK 10 -> JDK 11 (LTS). The 2.3.2 (2010)
  maven-compiler-plugin upgraded to 3.13.0 with `<release>` so the
  bytecode target is actually honoured on modern JDKs.
- **`NeoQueue` executor**: now uses daemon threads with a stable name
  (`neoqueue-default-N`). Previously the threads were non-daemon and
  anonymous, so a `NeoQueue` import alone could keep the JVM alive after
  shutdown.
- **`ShortCircuit`**: every accessor is now `synchronized`. Previously
  only `isFinalCallbackFired()` was synchronized, while the other
  accessors mutated/read non-volatile fields and broke the JMM
  happens-before chain that other combinators rely on.
- **Publishing**: switched from the deprecated `nexus-staging-maven-plugin`
  (which targeted `oss.sonatype.org`, retired 2025-06-30) to the official
  `central-publishing-maven-plugin`.
- **Dependencies**:
  - `junit` 4.12 -> 4.13.2 (CVE-2020-15250).
  - `slf4j-api` 1.7.25 -> 1.7.36 (final 1.x).
  - `commons-lang3` 3.8.1 -> 3.14.0.
  - `log4j` 1.2.17 -> `reload4j` 1.2.25 (CVE-patched drop-in for log4j
    1.x; same `org.apache.log4j.*` package). Test scope only.
  - Vert.x 3.6.0 -> 3.9.16 (final 3.x patch; test scope only).
  - `maven-gpg-plugin` 1.6 -> 3.2.4 with `--pinentry-mode loopback` for
    non-interactive CI signing.
  - `maven-javadoc-plugin` 3.0.1 -> 3.6.3.
  - `maven-source-plugin` -> 3.3.1.
  - `maven-jar-plugin` -> 3.4.1.
  - `maven-dependency-plugin` 2.5.1 -> 3.6.1.

### Fixed

- **`NeoLock` lost mutual exclusion under contention**: `releaseLock`
  mutated `callable` under the inner `Unlock` instance's monitor while
  `lck.locked` and the waiter queue were mutated outside any lock. Under
  contention a second acquirer could observe `locked == false` before the
  releasing thread had dequeued the next waiter, producing two concurrent
  holders of the same mutex. All state transitions now happen under the
  enclosing `NeoLock`'s monitor and the next waiter is dequeued atomically
  with the lock-state flip. The waiter queue is now `ArrayDeque` (O(1)
  `pollFirst`) instead of `ArrayList` (O(n) `remove(0)`, also not
  thread-safe).
- **`NeoQueue` throughput cap**: removed the hardcoded 1 ms
  `CompletableFuture.delayedExecutor` delay on every callback. Submitting
  directly to the executor already decouples the call stack on the
  executor boundary; the extra millisecond was pure idle latency that
  quadrupled wall-clock time on high-throughput queues.
- **`NeoQueue` debug noise**: removed `System.out.println("Using run async.")`
  that fired on every task completion, plus two dead `if (false) {}`
  branches in the dispatcher.
- **`NeoQueue.main()` dead code**: removed `public static void main()` (no
  `String[] args`, so it was never an entry point).
- **`Task` error type**: replaced `throw new Error(...)` with
  `IllegalStateException` / `IllegalArgumentException` in
  `Task.setStarted` / `setFinished` and `setConcurrency`. `java.lang.Error`
  is reserved for unrecoverable JVM conditions and shouldn't carry
  application invariants. Also corrected a copy-paste bug where
  `setFinished` claimed "Task already started".
- **`maven-jar-plugin` Main-Class**: dropped the bogus
  `Main-Class: com.mkyong.App` manifest entry inherited from the mkyong
  template, which pointed at a non-existent class.

### Removed

- OSSRH `<distributionManagement>` block — the endpoint
  (`oss.sonatype.org`) was retired by Sonatype on 2025-06-30.

## [0.1.1012] - 2019-05-08

Initial public release on Maven Central under the
`com.oresoftware:async.0.1:0.1.1012` coordinate. Frozen — see the new
`io.github.async-java:async-java` line for active development.

[Unreleased]: https://github.com/async-java/async.java/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/async-java/async.java/releases/tag/v0.2.0
[0.1.1012]: https://repo1.maven.org/maven2/com/oresoftware/async.0.1/0.1.1012/
