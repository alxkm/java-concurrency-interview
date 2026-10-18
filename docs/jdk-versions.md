# What changed in which JDK

Concurrency answers expire. "An uncontended monitor is free" was true until biased locking went away,
and "never block inside `synchronized` on a virtual thread" stopped being true in JDK 24. This page
collects the facts in the answers that depend on a version, so you can check which ones apply to the
JVM the team actually runs. Each row links to the question that explains it.

Preview and incubator APIs need `--enable-preview` or `--add-modules` and can change between releases;
the table says which state an API was in.

| JDK | What changed | Explained in |
|---|---|---|
| 5 | The modern Java memory model (JSR-133): happens-before, and `volatile` and `final` with real guarantees | [happens-before](https://alxkm.github.io/java-concurrency-interview/#happens-before-definition), [double-checked locking](https://alxkm.github.io/java-concurrency-interview/#dcl-volatile) |
| 5 | `java.util.concurrent`: executors, `ConcurrentHashMap`, latches, semaphores, atomics, `ReentrantLock` | [synchronized vs ReentrantLock](https://alxkm.github.io/java-concurrency-interview/#synchronized-vs-reentrantlock), [Timer vs ScheduledExecutorService](https://alxkm.github.io/java-concurrency-interview/#timer-vs-scheduled-executor) |
| 6 | Thread dumps detect deadlocks on `java.util.concurrent` locks, not only on monitors | [diagnosing a deadlock](https://alxkm.github.io/java-concurrency-interview/#diagnosing-deadlock) |
| 7 | Fork/join, `Phaser`, and a `FutureTask` that no longer uses AQS | [fork/join vs a fixed pool](https://alxkm.github.io/java-concurrency-interview/#forkjoin-vs-fixed), [Phaser](https://alxkm.github.io/java-concurrency-interview/#phaser), [AQS](https://alxkm.github.io/java-concurrency-interview/#aqs-basics) |
| 8 | `CompletableFuture`, `LongAdder`, `StampedLock`, the common pool and parallel streams | [CompletableFuture vs Future](https://alxkm.github.io/java-concurrency-interview/#cf-vs-future), [LongAdder](https://alxkm.github.io/java-concurrency-interview/#longadder-vs-atomiclong), [StampedLock](https://alxkm.github.io/java-concurrency-interview/#stampedlock-optimistic), [the common pool](https://alxkm.github.io/java-concurrency-interview/#common-pool) |
| 8 | `ConcurrentHashMap` rewritten: no segments, CAS on empty bins, a lock per bin, trees for long chains | [ConcurrentHashMap internals](https://alxkm.github.io/java-concurrency-interview/#chm-internals) |
| 9 | `VarHandle` replaces `Unsafe` for doing CAS yourself, and the atomics' `weakCompareAndSet` is deprecated | [CAS by hand](https://alxkm.github.io/java-concurrency-interview/#cas-by-hand), [weak CAS](https://alxkm.github.io/java-concurrency-interview/#weak-cas) |
| 9 | `CompletableFuture.orTimeout` and `completeOnTimeout` | [timeouts](https://alxkm.github.io/java-concurrency-interview/#cf-timeout) |
| 9 | A recursive `computeIfAbsent` throws `IllegalStateException` instead of spinning forever | [the computeIfAbsent trap](https://alxkm.github.io/java-concurrency-interview/#computeifabsent-trap) |
| 9 | `ConcurrentSkipListMap.size()` reads a counter instead of walking the list | [ConcurrentSkipListMap](https://alxkm.github.io/java-concurrency-interview/#skiplist-map) |
| 15 | Biased locking disabled by default (JEP 374), removed in JDK 18 | [the cost of synchronized](https://alxkm.github.io/java-concurrency-interview/#uncontended-synchronized-cost) |
| 19 | `ExecutorService` is `AutoCloseable`: `close()` waits for submitted tasks | [closing a virtual thread executor](https://alxkm.github.io/java-concurrency-interview/#virtual-executor-close) |
| 19 | Structured concurrency incubates (JEPs 428 and 437, in 19 and 20) | [why structured concurrency](https://alxkm.github.io/java-concurrency-interview/#structured-concurrency-why) |
| 20 | `Thread.stop()` throws `UnsupportedOperationException` | [Thread.stop](https://alxkm.github.io/java-concurrency-interview/#thread-stop-deprecated) |
| 21 | Virtual threads are final (JEP 444) | [what a virtual thread is](https://alxkm.github.io/java-concurrency-interview/#virtual-thread-basics) |
| 21 | Structured concurrency and `ScopedValue` become previews (JEPs 453 and 446) | [ScopedValue](https://alxkm.github.io/java-concurrency-interview/#scoped-values) |
| 21–23 | `synchronized` and `Object.wait()` pin a virtual thread to its carrier while it blocks | [pinning](https://alxkm.github.io/java-concurrency-interview/#virtual-thread-pinning), [locks in library code](https://alxkm.github.io/java-concurrency-interview/#virtual-threads-locks) |
| 24 | Monitors no longer pin virtual threads (JEP 491), and `-Djdk.tracePinnedThreads` is removed | [pinning](https://alxkm.github.io/java-concurrency-interview/#virtual-thread-pinning) |
| 25 | `ScopedValue` is final (JEP 506) | [ScopedValue](https://alxkm.github.io/java-concurrency-interview/#scoped-values), [context propagation](https://alxkm.github.io/java-concurrency-interview/#context-propagation) |
| 25 | Structured concurrency, still a preview (JEP 505), moves to `StructuredTaskScope.open` with joiners | [scope policies](https://alxkm.github.io/java-concurrency-interview/#structured-scope-policies) |

Something here out of date? [Open a correction](https://github.com/alxkm/java-concurrency-interview/issues/new?template=correction.yml)
with the JEP or release note that settles it.
