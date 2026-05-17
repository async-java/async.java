# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
