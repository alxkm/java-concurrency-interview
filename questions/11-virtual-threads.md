# Virtual threads and structured concurrency

Java 21 made threads cheap. That invalidates a decade of advice, and interviewers now use this topic to
find out whether a candidate has actually used them or only read the announcement.

Runnable: [virtual threads](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/virtualthreads), [structured concurrency](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/structured), [pinning](https://github.com/alxkm/java-concurrency-patterns/blob/master/docs/diagrams/05-virtual-thread-pinning.md).

```
   platform thread            virtual thread
   ---------------            --------------
   1:1 with an OS thread      many per carrier thread (a ForkJoinPool worker)
   ~1 MB stack, reserved      stack lives on the heap, grows as needed
   park = OS context switch   park = unmount, continuation stored, carrier freed

   virtual  |--run--[ blocking call ]......................[ resumes ]--run--|
   carrier  |--run--|            (free for other virtual threads)     |--run--|
```

## What is a virtual thread, and how is it scheduled?
- id: virtual-thread-basics
- level: mid
- tags: virtual-threads, loom

* [ ] A thread with a smaller stack
* [x] A JVM-managed thread whose stack lives on the heap, multiplexed by a work-stealing scheduler onto a small pool of platform carrier threads
* [ ] A green thread implemented by the OS
* [ ] Another name for a fiber in a library

A virtual thread is an ordinary `java.lang.Thread` whose execution is a continuation the JVM can park
and resume. When it blocks, the JVM saves its stack to the heap, unmounts it from its carrier, and the
carrier runs someone else. When the blocking call finishes, the continuation is mounted on any
available carrier and resumed.

The scheduler is a dedicated `ForkJoinPool` in FIFO mode, sized to `availableProcessors()` by default.
Creating one costs roughly a few hundred bytes rather than a megabyte of reserved stack, so millions
are practical.

Everything else stays the same: `Thread.currentThread()`, thread dumps, `try`/`catch`, debuggers and
stack traces all work, which is the entire point.

## What is pinning, and what causes it?
- id: virtual-thread-pinning
- level: senior
- tags: virtual-threads, pinning

* [ ] A thread being assigned to a specific CPU core
* [x] A virtual thread that cannot unmount while blocked, so it occupies its carrier: caused by blocking inside `synchronized` (before JDK 24) or inside a native frame
* [ ] The scheduler refusing to steal work
* [ ] A GC pause

If a virtual thread blocks while it holds a monitor, the JVM cannot unmount it, because the monitor is
tied to the carrier's stack frame. The carrier is occupied for the whole blocking call. With a default
of one carrier per core, a handful of pinned threads can stop the entire application while the CPU
sits idle.

Measured on Java 21, a `synchronized` block around a blocking call cost about 6x throughput against
the same code with a `ReentrantLock`. Native frames (JNI) pin as well and cannot be fixed by swapping a
lock.

Diagnosis: `-Djdk.tracePinnedThreads=full`, or the `jdk.VirtualThreadPinned` JFR event. The fix before
JDK 24 is to replace `synchronized` around blocking calls with `ReentrantLock`. JEP 491 in JDK 24
removed most monitor pinning, so on newer JVMs this is largely historical, but the question is still
asked and the JFR event is still the way to check.

## Should you pool virtual threads?
- id: pooling-virtual-threads
- level: mid
- tags: virtual-threads, executors

* [ ] Yes, pooling is always good practice
* [x] No: they are cheap to create and meant to be one per task, and pooling reintroduces exactly the limit they exist to remove
* [ ] Yes, with a pool size of 1000
* [ ] Only for CPU-bound work

A pool exists to amortise the cost of an expensive resource. A virtual thread is not expensive, so a
pool of them adds a queue, a lifetime that outlives the task and all the `ThreadLocal` leakage problems
that come with reuse, while capping concurrency at exactly the number the pool was sized to.

The intended shape is `Executors.newVirtualThreadPerTaskExecutor()`, which despite the name is not a
pool at all: it starts a fresh virtual thread per task.

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (var request : requests) {
        executor.submit(() -> handle(request));   // one thread each, thousands of them
    }
}   // close() waits for them all
```

If you need to limit concurrency, limit it directly with a `Semaphore`, which expresses the actual
constraint (the downstream service tolerates 50 in flight) rather than hiding it in a pool size.

## When do virtual threads not help?
- id: virtual-threads-limits
- level: senior
- tags: virtual-threads, performance

* [ ] They always help
* [x] For CPU-bound work, where the core count is still the limit, and for anything pinned or native; they solve blocking, not computation
* [ ] For any workload with more than 1000 tasks
* [ ] For database access

Virtual threads make blocking cheap. They do not create CPU capacity. A thousand virtual threads doing
matrix multiplication finish no sooner than a pool of N platform threads, and you have added scheduling
overhead. Keep CPU-bound work on a sized pool.

Other cases to name: work that is pinned (native frames, or monitors on an older JVM) gets no benefit;
`ThreadLocal` caching that assumed a small fixed number of threads can now allocate a copy per task,
which is the reverse of the intended optimisation; and downstream resources such as a connection pool
of 20 remain the real bottleneck, so a million virtual threads just move the queue somewhere less
visible.

Also worth saying: memory is not free either. A deep stack still costs heap, and a million idle
threads with deep stacks is a real number in your heap dump.

## What problem does structured concurrency solve?
- id: structured-concurrency-why
- level: mid
- tags: structured-concurrency, loom

* [ ] It makes tasks run in order
* [x] It gives concurrent subtasks a scope with a defined lifetime, so errors propagate, cancellation is automatic and nothing outlives the block that started it
* [ ] It replaces all executors
* [ ] It removes the need for exception handling

With an executor, a submitted task's lifetime is unrelated to the code that submitted it. If the caller
returns early or fails, the subtasks keep running, and cancelling them is manual bookkeeping everyone
gets wrong. Errors surface only when someone calls `get()`.

Structured concurrency binds the subtasks to a lexical scope, the same way a block binds local
variables. The scope does not exit until every fork has finished or been cancelled, a failure in one
fork cancels the siblings, and the subtask's stack trace is linked to the parent's, so thread dumps
show the tree.

```java
try (var scope = StructuredTaskScope.open()) {      // JDK 25 API shape
    var user  = scope.fork(() -> loadUser(id));
    var order = scope.fork(() -> loadOrders(id));
    scope.join();
    return new Page(user.get(), order.get());
}   // on any exit path, both forks are finished or cancelled
```

The API is still evolving (preview across JDK 19 to 24, and the factory-based shape from JDK 25), so
name the concept confidently and the exact signature with a caveat.

## What are `ShutdownOnFailure` and `ShutdownOnSuccess` for?
- id: structured-scope-policies
- level: senior
- tags: structured-concurrency

* [ ] Logging policies
* [x] Fail fast when any subtask fails, or take the first success and cancel the rest
* [ ] Shutdown hooks for the JVM
* [ ] Retry policies

`ShutdownOnFailure` is the "all must succeed" case, which is most fan-out: three service calls to
assemble one page, where any failure makes the whole page impossible. The first exception cancels the
other forks immediately rather than leaving them running for results nobody will use.

`ShutdownOnSuccess` is the racing case: query three replicas, or a cache and an origin, and take
whichever answers first, cancelling the losers.

In JDK 25 these became joiners passed to `StructuredTaskScope.open(...)`
(`Joiner.allSuccessfulOrThrow()`, `Joiner.anySuccessfulResultOrThrow()`), with custom joiners for
policies such as "wait for a quorum". The concepts are the same; only the spelling changed.

## What is a `ScopedValue`, and why is it preferred to `ThreadLocal` here?
- id: scoped-values
- level: senior
- tags: scoped-values, virtual-threads

* [ ] A `ThreadLocal` with a shorter name
* [x] An immutable value bound for the duration of a call, inherited by structured forks, with no set/remove lifecycle and therefore no leak
* [ ] A value scoped to a class
* [ ] A replacement for method parameters in general

`ThreadLocal` is mutable, unbounded in lifetime and must be removed by hand, which is unmanageable when
threads number in the millions and are created per task. `InheritableThreadLocal` copies the whole map
per child, which is worse.

`ScopedValue` inverts it: the value is bound for the dynamic extent of a call and automatically
unbound at the end, it cannot be reassigned by callees, and structured forks share the parent's binding
by reference rather than copying.

```java
private static final ScopedValue<Principal> USER = ScopedValue.newInstance();

ScopedValue.where(USER, principal).run(() -> handleRequest());   // USER.get() inside
```

Use it for context that is genuinely ambient (request id, principal, tenant) and pass everything else
as a parameter.

## How do you debug a system running a million virtual threads?
- id: virtual-thread-diagnostics
- level: senior
- tags: virtual-threads, diagnostics

* [ ] `jstack` as usual
* [x] `jcmd Thread.dump_to_file -format=json`, which dumps virtual threads grouped by their structured scope; plain `jstack` does not show them
* [ ] The JVM prints them on OOM
* [ ] You cannot, that is the trade-off

`jstack` and the classic dump show only platform threads, so your carriers appear and the million
virtual threads on top of them do not. The replacement is
`jcmd <pid> Thread.dump_to_file -format=json threads.json`, which walks the virtual threads too and,
for structured concurrency, nests them by scope so the output reads as a tree of who forked whom.

Also worth naming: JFR events `jdk.VirtualThreadStart`, `jdk.VirtualThreadEnd`,
`jdk.VirtualThreadPinned` and `jdk.VirtualThreadSubmitFailed`; and the fact that a million threads
produce a dump measured in hundreds of megabytes, so grep it rather than reading it.

## Do virtual threads change how you use `synchronized` in library code?
- id: virtual-threads-locks
- level: senior
- tags: virtual-threads, locks, pinning

* [ ] No, monitors work identically
* [x] Yes, on JDK 21 to 23 a monitor held across a blocking call pins the carrier, so library code that blocks should use `ReentrantLock`
* [ ] Yes, `synchronized` throws on a virtual thread
* [ ] Only for static methods

On JDK 21 through 23, blocking inside `synchronized` pins the carrier thread. A library that wraps its
I/O in a monitor (plenty of older JDBC drivers and HTTP clients did) will pin every virtual thread that
calls it, and the application's throughput collapses in a way that points at the wrong place.

The guidance for that era: audit your own blocking paths, replace monitors around them with
`ReentrantLock`, and check dependencies with `-Djdk.tracePinnedThreads=full`.

JDK 24's JEP 491 made monitors unmount properly, which retires most of this. Give both halves in an
interview: the mechanism, and the version where it stopped mattering, because the answer depends
entirely on which JVM the team is on.

## Is `Thread.sleep` still a bad idea on a virtual thread?
- id: virtual-thread-sleep
- level: mid
- tags: virtual-threads, basics

* [ ] Yes, it blocks a carrier thread
* [x] No: `sleep` on a virtual thread unmounts it, costing no OS thread, which is one of the clearest illustrations of what changed
* [ ] It throws `UnsupportedOperationException`
* [ ] It is converted into a busy wait

`Thread.sleep` is one of the JDK calls made virtual-thread aware. On a virtual thread it parks the
continuation and frees the carrier, so a million sleeping virtual threads consume essentially no OS
resources. That is the standard demonstration: start a million threads that each sleep a second, and
watch it finish in about a second.

The same retrofit covers `java.net` sockets, `NIO` channels, `java.util.concurrent` locks and queues,
`Object.wait` and `Process.waitFor`. What is not covered: `synchronized` on older JVMs, native calls,
and file I/O on some platforms, where the JVM falls back to a platform thread underneath.

Sleeping as a way to coordinate threads remains a bad idea, but for reasons of correctness rather than
cost.

## What does `Executors.newVirtualThreadPerTaskExecutor().close()` do?
- id: virtual-executor-close
- level: mid
- tags: virtual-threads, executors

* [ ] Nothing, it is a no-op
* [x] It blocks until every submitted task has finished, which is why it is used with try-with-resources as a join point
* [ ] It interrupts running tasks
* [ ] It rejects the tasks still queued

`ExecutorService` extends `AutoCloseable` since Java 19, and `close()` means shutdown plus
`awaitTermination`, retrying on interrupt. With try-with-resources that gives you a scope whose closing
brace is a guaranteed join:

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    results = urls.stream().map(url -> executor.submit(() -> fetch(url))).toList();
}   // every fetch has completed here
```

This is structured concurrency's shape without its error propagation: a failure in one task does not
cancel the others, and you still collect outcomes from the futures yourself. It is the pragmatic
halfway house available today in stable API.
