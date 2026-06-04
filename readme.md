# async.java

> A Java port of [`async`](https://github.com/caolan/async) for callback-style
> JVM code. Type-safe, error-first callbacks. Designed for use inside Vert.x
> verticles, Akka actors, plain Spring services, or any callback-passing-style
> codebase that wants structured control flow without dragging in
> RxJava/Reactor.

[![ci](https://github.com/async-java/async.java/actions/workflows/ci.yml/badge.svg)](https://github.com/async-java/async.java/actions/workflows/ci.yml)
[![maven central](https://img.shields.io/maven-central/v/io.github.oresoftware/async-java)](https://central.sonatype.com/artifact/io.github.oresoftware/async-java)
[![jdk](https://img.shields.io/badge/JDK-17%2B-blue)](#requirements)
[![license: MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)

---

## Contents

- [Why async.java](#why-asyncjava)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quickstart](#quickstart)
- [Future and blocking-task interop](#future-and-blocking-task-interop)
- [Control flow](#control-flow)
  - [Series](#series) · [Parallel](#parallel) · [Waterfall](#waterfall) ·
    [Race](#race) · [Inject](#inject)
- [Collections](#collections)
  - [Map](#map) · [FilterMap](#filtermap) · [Reduce](#reduce) · [Each](#each) ·
    [GroupBy](#groupby) · [Concat](#concat) · [Times](#times)
- [Looping](#looping)
  - [Whilst / DoWhilst](#whilst--dowhilst)
- [Queue](#queue): bounded-concurrency worker pool
- [Lock](#lock): async mutex
- [Threading model](#threading-model)
- [Project Loom and async.java](#project-loom-and-asyncjava)
- [Using with Vert.x](#using-with-vertx)
- [Error handling: short-circuit semantics](#error-handling-short-circuit-semantics)
- [Migrating from `com.oresoftware:async.0.1`](#migrating-from-comoresoftwareasync01)
- [Contributing](#contributing)
- [Releasing](#releasing)
- [License & credits](#license--credits)

---

## Why async.java

The Java port of `async` is useful when:

- **You have callback-style code already.** JDBC drivers handed off to Vert.x's
  `executeBlocking`, Akka `tell`, Netty futures, SDK callbacks — they all
  share the same shape. async.java lets you compose them without inventing
  ad-hoc `CompletableFuture` plumbing per call site.
- **You want explicit concurrency limits.** `ParallelLimit`, `MapLimit`,
  `EachLimit`, and `NeoQueue` give you fan-out with a hard cap, which is hard
  to express cleanly in `CompletableFuture.allOf(...)`.
- **You don't want a reactive-streams runtime.** No Mono, no Flux, no
  `Publisher`. The whole library compiles against the JDK's standard library
  plus SLF4J.
- **You like error-first callbacks.** Every callback is
  `(error, value) -> ...`, the Node convention; on the first non-null `error`
  the combinator short-circuits and the final callback fires once.

### Pairs well with

- [Vert.x](https://vertx.io/) — see [Using with Vert.x](#using-with-vertx).
- [Akka](https://akka.io/) — the same callback discipline plugs into actor
  `tell` / `ask` flows.
- Plain `Future` / `CompletableFuture` APIs — bridge callback combinators
  with `WrapFuture`, `AsyncFut`, and the virtual-thread helpers in
  `AsyncLoom`.
- Plain `ExecutorService` — install your own executor with
  `NeoQueue.setExecutor(...)` and async.java's continuations run on it.

### See also

See the dedicated [Project Loom and async.java](#project-loom-and-asyncjava)
section below for how this library composes with virtual threads — the
short version is "they're complementary: Loom solves blocking, this
library solves orchestration."

## Requirements

- **JDK 17+** (tested on 17 and 21).
- SLF4J on the classpath (binding optional — pick `logback-classic`,
  `slf4j-simple`, etc.).

## Installation

### Maven Central

```xml
<dependency>
  <groupId>io.github.oresoftware</groupId>
  <artifactId>async-java</artifactId>
  <version>0.2.10</version>
</dependency>
```

### Gradle

```kotlin
implementation("io.github.oresoftware:async-java:0.2.10")
```

### Snapshots

```xml
<repositories>
  <repository>
    <id>central-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <snapshots><enabled>true</enabled></snapshots>
    <releases><enabled>false</enabled></releases>
  </repository>
</repositories>
```

### JitPack (arbitrary git refs)

Need to try a branch, tag, or commit SHA before it lands on Central?

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.async-java</groupId>
  <artifactId>async.java</artifactId>
  <version>v0.2.10</version>  <!-- or a branch name, or a 10-char commit SHA -->
</dependency>
```

## Quickstart

```java
import org.ores.async.Asyncc;

public class Quickstart {
  public static void main(String[] args) {

    // Run three asynchronous tasks in parallel; fire one callback when they all finish.
    Asyncc.<String, Throwable>Parallel(
        cb -> cb.done(null, "alpha"),
        cb -> cb.done(null, "beta"),
        cb -> cb.done(null, "gamma"),
        (err, results) -> {
          if (err != null) {
            err.printStackTrace();
            return;
          }
          System.out.println(results);   // -> [alpha, beta, gamma]
        });
  }
}
```

Every task receives an `IAsyncCallback<T, E>` and signals completion exactly
once by calling `cb.done(error, value)`. The final callback runs after the
last task settles — or immediately when any task surfaces an error.

---

## Future and blocking-task interop

### Plain `Future`: async.java calls `get()` for you

Legacy APIs often return plain JDK `Future<T>`, where the only way to retrieve
the value is the blocking `future.get()`. Use `WrapFuture` or `AsyncFut` to
move that blocking wait inside the library:

```java
ExecutorService legacyPool = Executors.newFixedThreadPool(8);
ExecutorService waiters = AsyncLoom.isSupported()
    ? AsyncLoom.newVirtualThreadPerTaskExecutor()
    : Executors.newCachedThreadPool();

try {
  List<Future<Row>> futures = List.of(
      legacyPool.submit(() -> fetchRow("a")),
      legacyPool.submit(() -> fetchRow("b"))
  );

  CompletableFuture<List<Row>> rows = AsyncFut.ParallelFutures(waiters, futures);
  rows.thenAccept(this::reply);
} finally {
  waiters.shutdown();
  legacyPool.shutdown();
}
```

For callback-style code, use `WrapFuture.fromFuture(...)`:

```java
Asyncc.Parallel(List.of(
    WrapFuture.fromFuture(waiters, legacyPool.submit(() -> fetchA())),
    WrapFuture.fromFuture(waiters, legacyPool.submit(() -> fetchB()))
), (err, results) -> {
    if (err != null) { reply.fail(err); return; }
    reply.ok(results);
});
```

### Blocking work on virtual threads

On JDK 21+, `AsyncLoom` runs blocking `Callable` work on virtual threads and
returns `CompletableFuture` results:

```java
CompletableFuture<List<Row>> rows = AsyncLoom.ParallelBlocking(List.of(
    () -> jdbc.fetch("a"),
    () -> jdbc.fetch("b")
));

CompletableFuture<List<Row>> ordered = AsyncLoom.SeriesBlocking(List.of(
    () -> migrateStep1(),
    () -> migrateStep2()
));

CompletableFuture<Integer> total = AsyncLoom.ReduceBlocking(
    List.of("a", "b", "c"),
    0,
    (acc, id) -> acc + scoreBlocking(id));

CompletableFuture<List<Path>> paths = AsyncLoom.ConcatBlocking(
    tenants,
    tenant -> listTenantFilesBlocking(tenant));
```

The same feature is available in callback form:

```java
Asyncc.SeriesBlocking(List.of(
    () -> migrateStep1(),
    () -> migrateStep2()
), (err, results) -> {
    if (err != null) { rollback(err); return; }
    commit(results);
});

Asyncc.RaceBlocking(List.of(
    () -> readPrimaryBlocking(key),
    () -> readReplicaBlocking(key)
), (err, value) -> {
    if (err != null) { reply.fail(err); return; }
    reply.ok(value);
});
```

Existing `Parallel`, `Series`, `Reduce`, and `Concat` methods keep their
current threading contract: tasks run on whichever thread invokes the callback.
Use the `*Blocking` family when you want async.java to put blocking work on
virtual threads for you.

The repo also includes compile-checked examples under
[`src/test/java/examples`](src/test/java/examples):

- `FutureInteropExample` adapts plain JDK `Future` values without user-side
  `get()` calls.
- `LoomBlockingExample` shows `ParallelBlocking`, `SeriesBlocking`,
  `RaceBlocking`, `ReduceBlocking`, and `ConcatBlocking`.
- `CallbackBlockingExample` shows the callback-style `Asyncc.*Blocking`
  wrappers.

---

## Control flow

### Series

Run tasks sequentially; collect their results into an ordered list.

```java
Asyncc.<Integer, Throwable>Series(
    cb -> cb.done(null, 1),
    cb -> cb.done(null, 2),
    cb -> cb.done(null, 3),
    (err, results) -> {
      // results = [1, 2, 3]
    });
```

You can also pass a `List<AsyncTask<T,E>>` if the task set is dynamic.

### Parallel

Same shape as `Series` but tasks run concurrently. Results are returned in the
order the tasks were declared, not the order they finished:

```java
List<Asyncc.AsyncTask<String, Throwable>> tasks = List.of(
    cb -> slowFetch("a", cb),
    cb -> slowFetch("b", cb),
    cb -> slowFetch("c", cb));

Asyncc.Parallel(tasks, (err, results) -> {
  // results is List<String> in the declared order: [resultOfA, resultOfB, resultOfC]
});
```

### ParallelLimit

Bound the in-flight task count. Useful for fan-outs against a rate-limited
upstream (HTTP, JDBC, S3 PutObject, etc.):

```java
Asyncc.ParallelLimit(8, tasks, (err, results) -> { /* ... */ });
```

### Waterfall

Sequential pipeline where each stage publishes a `(key, value)` and downstream
stages can read prior values:

```java
import org.ores.async.NeoWaterfallI;

List<NeoWaterfallI.AsyncTask<String, Throwable>> stages = List.of(
    cb -> cb.done(null, "user", "alice"),
    cb -> {
      String user = cb.get("user");
      cb.done(null, "permissions", "ro,rw,admin:" + user);
    },
    cb -> {
      String perms = cb.get("permissions");
      cb.done(null, "report", "issued for " + perms);
    });

Asyncc.<String, Throwable>Waterfall(stages, (err, all) -> {
  // all = { user=alice, permissions=ro,rw,admin:alice, report=issued for ro,rw,admin:alice }
});
```

The keyed-map design (rather than the Node `(prev) -> next` chain) means a
stage can read *any* previous output by name, not just the immediately
preceding one.

### Race

Settle as soon as the first task completes (success or failure):

```java
Asyncc.<String, String, Throwable>Race(
    List.of(
        cb -> tryPrimary(cb),
        cb -> tryFallback(cb)),
    (err, winner) -> { /* the first task to call cb.done(...) wins */ });
```

### Inject

Named-task DAG. Each task declares which other named tasks it depends on by
listing their names in the `Task` constructor; the combinator computes a
valid order and runs independent branches in parallel. Dependencies' results
are read inside a task via `cb.get("name")`:

```java
import org.ores.async.NeoInject;

Map<String, NeoInject.Task<String, Throwable>> tasks = new LinkedHashMap<>();

tasks.put("loadUser",
    new NeoInject.Task<>(cb -> cb.done(null, "alice")));

tasks.put("loadPrefs",
    new NeoInject.Task<>("loadUser", cb -> {
      String user = cb.get("loadUser");
      cb.done(null, "dark-mode for " + user);
    }));

tasks.put("render",
    new NeoInject.Task<>("loadUser", "loadPrefs", cb -> {
      String user  = cb.get("loadUser");
      String prefs = cb.get("loadPrefs");
      cb.done(null, "<html>" + user + " / " + prefs + "</html>");
    }));

Asyncc.Inject(tasks, (err, all) -> {
  // all.get("render") => "<html>alice / dark-mode for alice</html>"
});
```

`NeoInject.Task` has constructors for 0 through 7 named dependencies; for
larger fan-ins pass a `Set<String>` directly.

---

## Collections

### Map

Async transform over an iterable. `MapLimit` caps the in-flight count:

```java
List<Long> userIds = List.of(101L, 102L, 103L);

Asyncc.<User, Long, Throwable>Map(userIds,
    (id, cb) -> fetchUserById(id, cb),     // (T, IAsyncCallback<V, E>) -> void
    (err, users) -> {
      // users is List<User> in input order
    });

// concurrency cap of 4:
Asyncc.MapLimit(4, userIds, (id, cb) -> fetchUserById(id, cb), (err, users) -> {});
```

`MapSeries` is the sequential variant (concurrency = 1).

### FilterMap

Map and filter in one pass — return `null` (or any sentinel) from the mapper
to drop the element:

```java
Asyncc.<User, Long, Throwable>FilterMap(userIds,
    (id, cb) -> fetchUserById(id, cb, /* allowNull */ true),
    (err, present) -> {
      // null mapper results are filtered out; present is List<User>
    });
```

### Reduce

Async fold; the reducer receives `(accumulator, next, cb)`:

```java
Asyncc.<Integer, Long, Throwable>Reduce(
    0L,                                       // initial value
    List.of(1, 2, 3, 4, 5),
    (acc, next, cb) -> cb.done(null, acc + next),
    (err, total) -> {
      // total = 15
    });
```

`ReduceRight` reduces from the tail.

### Each

Same shape as `Map`, but the per-item callback takes no value (use it for
side effects: writes, log lines, fan-out fire-and-forget):

```java
Asyncc.<Long, Throwable>EachLimit(4, userIds,
    (id, cb) -> sendWelcomeEmail(id, cb),
    err -> { /* fires once when every email has settled */ });
```

### GroupBy

Bucket items into a `Map<String, List<V>>` (or `Set<V>` via `GroupToSets`)
keyed by an async classifier:

```java
Asyncc.<User, User, Throwable>GroupBy(allUsers,
    (user, cb) -> cb.done(null, user.region()),
    (err, byRegion) -> {
      // byRegion = { "us-east": [...], "eu-west": [...], ... }
    });
```

### Concat

`Map` then flatten the list-of-lists into a single list. Useful for
"fetch+flatten" pipelines:

```java
Asyncc.<Long, Order, Throwable>Concat(userIds,
    (id, cb) -> listOrdersFor(id, cb),       // returns List<Order> per user
    (err, allOrders) -> {
      // allOrders = flattened List<Order>
    });
```

`ConcatDeep` flattens arbitrarily nested lists.

### Times

Run the same task N times, collect the results:

```java
Asyncc.<UUID, Throwable>Times(5,
    (i, cb) -> cb.done(null, UUID.randomUUID()),
    (err, ids) -> {
      // ids = List<UUID>, 5 entries
    });
```

`TimesLimit(lim, count, ...)` caps in-flight runs.

---

## Looping

### Whilst / DoWhilst

Run a task repeatedly while a predicate holds:

```java
AtomicInteger n = new AtomicInteger();

Asyncc.<Integer, Throwable>Whilst(
    () -> n.get() < 10,                                  // sync truth test
    cb -> cb.done(null, n.incrementAndGet()),            // task
    (err, results) -> {
      // results = [1, 2, ..., 10]
    });
```

There's also an async-truth-test overload
(`NeoWhilstI.AsyncTruthTest`) for predicates that need IO. `DoWhilst` runs the
task once before testing the predicate (Node's `whilst` vs `doWhilst`
distinction).

---

## Queue

`NeoQueue<T, V>` is a non-blocking worker pool. Push tasks in; the queue runs
at most `concurrency` of them at any moment, and fires lifecycle callbacks
as it saturates / drains:

```java
import org.ores.async.NeoQueue;

NeoQueue<String, Integer> queue = new NeoQueue<>(/* concurrency */ 4,
    (task, cb) -> {
      // task.getValue() is the payload pushed in below
      processAsync(task.getValue(), cb);   // call cb.done(err, result) when ready
    });

queue.onSaturated(q -> log.info("queue saturated"));
queue.onUnsaturated(q -> log.info("queue has slack"));
queue.onDrain(q -> log.info("queue drained"));

for (String url : urls) {
  queue.push(new NeoQueue.Task<>(url, (err, byteCount) -> {
    // per-task completion callback
  }));
}
```

Process-wide knobs:

```java
NeoQueue.setExecutor(myAppExecutor);  // route continuations onto an existing executor
NeoQueue.shutdown();                  // tear down the built-in executor at JVM exit
```

By default the built-in executor is a single daemon thread named
`neoqueue-default-N` — picked so a forgotten `NeoQueue` import alone can
never prevent the JVM from exiting.

---

## Lock

`NeoLock` is an async mutex: `acquire` queues your callback, and your
callback receives an `Unlock` token that you call `.releaseLock()` on when
done:

```java
import org.ores.async.NeoLock;

NeoLock lock = new NeoLock("billing-invariant");

lock.acquire((err, unlock) -> {
  try {
    rebalanceLedger();
  } finally {
    unlock.releaseLock();
  }
});
```

Multiple concurrent `acquire` calls are queued FIFO; only one holder at a
time. Unlike `synchronized`, the calling thread is never blocked.

---

## Threading model

- **Combinator continuations** run on whatever thread invoked
  `cb.done(...)`. If you call `cb.done` synchronously inside a task, the
  next task in the chain runs on that same thread.
- **`NeoQueue` callbacks** are dispatched onto the executor returned by
  `NeoQueue.setExecutor(...)` (default: a single daemon-thread executor) so
  the queue can never recurse into itself on a single stack.
- **`NeoLock` continuations** run on the thread that called
  `unlock.releaseLock()` for the next waiter — typically your own worker
  thread.

### Choosing an executor

Three sensible defaults, depending on your runtime:

```java
// (a) Built-in: one daemon thread, single-threaded continuation. The default.
// Good for short-lived processes and unit tests; pinpoints accidental long-running
// callbacks because they queue up behind each other.

// (b) Virtual threads (JDK 21+). Best when you don't care about callback ordering
// and want unbounded continuation parallelism without the overhead of platform threads.
NeoQueue.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

// (c) A bounded platform-thread pool. Best when callbacks do CPU-bound work and you
// want a hard ceiling on concurrent CPU consumption.
NeoQueue.setExecutor(Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors(),
    r -> {
      Thread t = new Thread(r, "neoqueue-cpu");
      t.setDaemon(true);
      return t;
    }));
```

For Vert.x apps, leave the default in place and let your combinator callbacks
run on whichever Vert.x context invoked them (event loop / worker / virtual
thread, depending on how you deployed the verticle) — there's no benefit to
routing them off-context.

---

## Project Loom and async.java

Loom and this library address two different halves of the same problem
and pair cleanly. The TL;DR is at the end of
[Why async.java](#why-asyncjava); this section is the detail for anyone
designing concurrent code on JDK 21+ and wondering whether to reach for
this library.

### One-line framing

> **Loom makes blocking cheap. async.java makes orchestration explicit.
> Loom doesn't replace orchestration; it just means each orchestrated
> task can block freely.**

The two share a heritage: both are continuation-style execution models.
Loom's virtual threads are continuations the JVM saves and restores
under your blocking `read()`; async.java's combinators are continuations
*you* write as `(err, value) -> ...` lambdas. Mixing them is natural —
the JDK runs each callback on a VT, and you stop caring whether the
callback "blocks" on its way to calling `cb.done(...)`.

### The killer combo: bounded fan-out × cheap blocking

The biggest concrete win is pairing `NeoQueue` with a virtual-thread
executor:

```java
// Once, at app startup, on JDK 21+
NeoQueue.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
```

Why this matters: Loom removes the *cost* ceiling on concurrency, which
shifts the real bottleneck onto downstream resources — connection pools,
HTTP rate limits, S3 per-prefix quotas, database row locks. Cheap VTs
*encourage* you to spawn more concurrent work, so a hard cap on how many
tasks are simultaneously in flight matters more than ever, not less.

`NeoQueue` is exactly that hard cap, plus the lifecycle hooks
(`onSaturated` / `onUnsaturated` / `onDrain`) you need to react to it.
With a VT executor installed, each in-flight slot is free to block on
JDBC, HTTP, file I/O, `Thread.sleep` — Loom keeps the carrier thread
moving while your task parks.

### Carrier-thread pinning: `NeoLock` vs `synchronized` vs `ReentrantLock`

If a virtual thread holds a `synchronized` block while it blocks, it
**pins the carrier thread** on JDK 21 through 23. JEP 491 fixed that
in JDK 24 (March 2025), but a lot of production deployment is still on
21 LTS and will be for a while. `ReentrantLock` never pinned, but it's
*thread-affine*: the thread that called `lock()` is the only one that
can call `unlock()`, which means in callback-style code you have to
ferry the lock back to the original VT.

`NeoLock` has neither problem:

- **No carrier pinning, ever** — it's a callback queue, not a real
  mutex. A waiter's continuation is invoked when the holder releases;
  the waiting VT itself isn't blocked-on-a-lock, it's just not
  scheduled until its callback fires.
- **Not thread-affine** — `acquire` can happen on VT A and
  `unlock.releaseLock()` can happen on VT B. This is the common case in
  Vert.x event-loop code or any pipeline where ownership of an in-flight
  operation hops across contexts.

```java
NeoLock lock = new NeoLock("inventory");

// VT A: acquire, then hand the Unlock to whoever finishes the work.
lock.acquire((err, unlock) -> {
    sendToWorkerPool(item, () -> unlock.releaseLock());   // VT B releases later
});
```

### Relationship to `StructuredTaskScope`

`StructuredTaskScope` is the JDK's official structured-concurrency
primitive — still in preview in JDK 26 (JEP 525, the sixth preview).
A scope owns a set of subtasks, and when the scope's
`try-with-resources` closes, all subtasks have settled.

The JDK 25 redesign reshaped the API: scopes are now opened via static
factory methods (`StructuredTaskScope.open(...)`) parameterised by a
`Joiner` policy. The stock joiners cover two shapes —
`Joiner.allSuccessfulOrThrow()` (parallel-all, fail-fast) and
`Joiner.anySuccessfulResultOrThrow()` (race-first). The JDK 21–24
preview shipped these as the `ShutdownOnFailure` / `ShutdownOnSuccess`
subclasses; those subclasses were removed in JDK 25.

Where this library fits relative to it:

| You need...                                       | JDK answer                                            | This library                                              |
| ------------------------------------------------- | ----------------------------------------------------- | --------------------------------------------------------- |
| Parallel-all                                      | `Joiner.allSuccessfulOrThrow()`                       | `Asyncc.Parallel(...)`                                    |
| Race-first                                        | `Joiner.anySuccessfulResultOrThrow()`                 | `Asyncc.Race(...)`                                        |
| Sequential pipeline with named intermediate values | `CompletableFuture.thenCompose` chains               | `Asyncc.Waterfall(...)` (publishes a `Map<String, Object>`) |
| Bounded fan-out with backpressure hooks           | `Semaphore` + your own glue                           | `Asyncc.ParallelLimit` / `NeoQueue`                       |
| Group N items into buckets by async classifier    | hand-rolled                                           | `Asyncc.GroupBy(...)`                                     |
| Async fold                                        | hand-rolled                                           | `Asyncc.Reduce(...)` / `ReduceRight`                      |
| Async mutex with cross-thread release             | hand-rolled                                           | `NeoLock`                                                 |
| Named-task DAG with declared dependencies         | hand-rolled                                           | `Asyncc.Inject(...)`                                      |

The two compose well. A common pattern: use `NeoQueue` for the
*outer* concurrency cap (your service-wide policy), and use
`StructuredTaskScope` inside each task handler for *inner* short-lived
parallel work. The outer queue protects downstream resources; the inner
scope gives you structured-concurrency guarantees on the fan-out shape.

```java
// JDK 25 preview API (StructuredTaskScope.open + Joiner)
NeoQueue<Order, OrderResult> queue = new NeoQueue<>(/* slots */ 8, (task, done) -> {

    try (var scope = StructuredTaskScope.open()) {                      // default = parallel-all, fail-fast
        var customer  = scope.fork(() -> fetchCustomer(task.getValue()));
        var inventory = scope.fork(() -> fetchInventory(task.getValue()));
        var pricing   = scope.fork(() -> fetchPricing(task.getValue()));
        scope.join();                                                   // throws on any subtask failure

        done.done(null, assemble(customer.get(), inventory.get(), pricing.get()));
    } catch (Exception e) {
        done.done(e, null);
    }
});
```

On JDK 21–24 the inner block reads
`try (var scope = new StructuredTaskScope.ShutdownOnFailure()) { ...
scope.join(); scope.throwIfFailed(); ... }` — same idea, older spelling.

### `ThreadLocal` and `ScopedValue`

VTs are abundant, so the JDK is steering you off `ThreadLocal` and
toward `ScopedValue` (preview). For async.java specifically: a
combinator callback may run on a different thread than the one that
*scheduled* it, especially with a VT executor installed in `NeoQueue`.
That means **any state you propagate via `ThreadLocal` will not be
visible inside a combinator callback** unless you copy it explicitly.

The fix is either:

1. Pass state through your own closure (the lambda's captured
   variables), which is the cleanest option in callback-style code; or
2. Use `ScopedValue.where(...).run(() -> ...)` around the call site
   that invokes the combinator, so the scoped binding is in effect for
   the whole continuation chain.

If you're using SLF4J `MDC` for per-request log context, you'll need a
similar copy / restore at every async hop. This is a general
async-callback caveat, not specific to async.java — it just becomes
more visible once VTs encourage more frequent context hops.

### Vert.x + Loom + async.java

Vert.x 4.5+ already supports deploying verticles on virtual threads:

```java
vertx.deployVerticle(MyVerticle::new, new DeploymentOptions()
    .setThreadingModel(ThreadingModel.VIRTUAL_THREAD));
```

In a VT-backed verticle you can `Future.toCompletionStage().toCompletableFuture().get()`
to await results inline (Vert.x's official escape hatch from callback
hell). async.java's combinators work the same way in either threading
model — event-loop or virtual-thread — because they don't care about
the carrier thread. The recipe in
[Using with Vert.x](#using-with-vertx) below holds verbatim with
`ThreadingModel.VIRTUAL_THREAD`; you just gain the option of writing
`.get()` instead of `.onComplete(...)` inside individual stages.

### When you should not reach for this library on JDK 21+

The honest exception: a **single straight-line sequence of blocking
calls**. Don't write

```java
Asyncc.Waterfall(List.of(
    cb -> cb.done(null, "user",  fetchUserBlocking()),
    cb -> cb.done(null, "perms", fetchPermsBlocking(cb.get("user")))),
  ...);
```

when on JDK 21+ you can just write

```java
String user  = fetchUserBlocking();
String perms = fetchPermsBlocking(user);
```

inside a virtual thread. async.java is for **multi-task orchestration**
(parallel with caps, race, group, reduce, queue, lock, DAG) and for
**bridging callback-shaped APIs** (Vert.x `Handler<AsyncResult<T>>`,
Netty `ChannelFuture`, AWS SDK v2 `xxxAsync(...)`, Akka
`Patterns.ask`) that hand you `(err, result)` and won't go away just
because you have virtual threads.

---

## Using with Vert.x

A real-world pipeline (excerpted from
[dd-spark-pipeline-server](https://github.com/ORESoftware/k8s-cluster/tree/dev/remote/spark-pipeline-server)):

```java
public final class JobService {

  private final Vertx vertx;
  private final NeoQueue<JobRecord, JobRecord> queue;

  public JobService(Vertx vertx) {
    this.vertx = vertx;
    // 4 jobs in flight at most across the whole process.
    this.queue = new NeoQueue<>(4, this::runJob);
  }

  public void submit(JobRecord rec) {
    queue.push(new NeoQueue.Task<>(rec));
  }

  private void runJob(NeoQueue.Task<JobRecord, JobRecord> task,
                      NeoQueue.IAsyncErrFirstCb<JobRecord> done) {
    final JobRecord rec = task.getValue();

    // Hop onto a Vert.x worker so any blocking JDBC inside a stage doesn't pin
    // the NeoQueue executor thread.
    vertx.<JobRecord>executeBlocking(p -> {

      // Three independent prechecks in parallel, then submit.
      Asyncc.<String, Throwable>Parallel(
          cb -> precheckCluster(rec, cb),
          cb -> precheckJar(rec, cb),
          cb -> precheckConfig(rec, cb),
          (err, prechecks) -> {
            if (err != null) { p.fail(err); return; }
            sparkSubmit(rec, prechecks, p);
          });

    }, false).onComplete(ar -> done.done(ar.cause(), ar.result()));
  }
}
```

The pattern — **`NeoQueue` for backpressure, `Asyncc.Parallel` / `Series` /
`Waterfall` per job** — composes cleanly with `vertx.executeBlocking` and
`Future` chains and avoids the Reactor / RxJava learning curve.

---

## Error handling: short-circuit semantics

Every combinator follows the same contract:

- Tasks signal completion by calling `cb.done(error, value)` **exactly once**.
- The **first** task to pass a non-null `error` short-circuits the whole
  combinator: the final callback fires immediately with `(error, partialOrNullResults)`,
  and `cb.isShortCircuited()` starts returning `true` for any still-in-flight
  tasks so they can bail out without producing a value.
- Subsequent `cb.done(...)` calls after the combinator has settled are
  logged-and-dropped (rather than throwing) — important because in-flight
  tasks may complete after a sibling has already failed.
- Throwing from inside a task is caught and converted to the same
  short-circuit path, so a buggy lambda doesn't lose the rest of the
  pipeline.

---

## Migrating from `com.oresoftware:async.0.1`

The legacy 2019 release stays on Maven Central indefinitely so existing
consumers don't break:

```xml
<!-- old, frozen at 0.1.1012 -->
<dependency>
  <groupId>com.oresoftware</groupId>
  <artifactId>async.0.1</artifactId>
  <version>0.1.1012</version>
</dependency>

<!-- new -->
<dependency>
  <groupId>io.github.oresoftware</groupId>
  <artifactId>async-java</artifactId>
  <version>0.2.10</version>
</dependency>
```

The Java package (`org.ores.async`) and every public API signature are
unchanged, so swapping the coordinate is enough for almost all consumers —
no source edits required. The one behaviour change to be aware of: methods
that previously threw `java.lang.Error` on application invariants
(`NeoQueue.Task.setStarted`, `setFinished`, `NeoQueue.setConcurrency`,
`NeoLock` duplicate release) now throw `IllegalStateException` /
`IllegalArgumentException`. If you catch `Error` anywhere, audit those
sites.

What's new since 0.1.1012:

- `NeoQueue` executor is daemon-threaded and named (`neoqueue-default-N`);
  added `NeoQueue.setExecutor(...)` and `NeoQueue.shutdown()`.
- Removed the hardcoded 1 ms delay on every queue callback (was capping
  throughput at ~1k tasks/sec).
- `NeoLock`: fixed a race where two acquirers could observe an unlocked
  mutex during concurrent release; replaced `ArrayList` waiter queue with
  `ArrayDeque`.
- `ShortCircuit` now uniformly synchronizes its flag accessors for a
  consistent memory model.
- Replaced `throw new Error(...)` with `IllegalStateException` /
  `IllegalArgumentException` on application invariants.
- Dropped the old JDK 10/11-era baseline; JDK 17+ is now required.

See [CHANGELOG.md](CHANGELOG.md) for the full list.

---

## Contributing

```bash
git clone https://github.com/async-java/async.java.git
cd async.java
mvn -B test         # 207 tests, ~15s on a warm cache
```

Pull requests welcome. CI runs `mvn test` on JDK 17 and 21 for every
push and PR — see [.github/workflows/ci.yml](.github/workflows/ci.yml).

Coding conventions:

- Public API lives under `org.ores.async`; internal helpers are
  package-private.
- Combinator entry points live in `Asyncc.java`; per-combinator runners
  live in `Neo*.java` (e.g. `NeoParallel`, `NeoWaterfall`).
- Tests use JUnit 4 + Vert.x Unit for the async ones; new tests in JUnit 4
  to match existing style.

## Releasing

Maintainers — see [RELEASING.md](RELEASING.md) for the namespace / GPG /
secret setup and the tag-driven release workflow that publishes to
Sonatype Central Portal.

## License & credits

[MIT License](LICENSE).

Originally written by [@ORESoftware](https://github.com/ORESoftware) (Alex
Mills) as a port of [Caolan McMahon](https://github.com/caolan)'s seminal
[`async`](https://github.com/caolan/async) library for Node.js. Inspired by
[Suguru Motegi](https://github.com/suguru03)'s
[`neo-async`](https://github.com/suguru03/neo-async).
