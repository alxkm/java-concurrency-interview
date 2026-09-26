# Executors and thread pools

The topic where interview answers most often come from a tutorial rather than from an incident. Every
question below has cost somebody a production outage at some point.

Runnable: [executors](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/executors), [scheduler](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/scheduler), [threads instead of tasks](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/antipatterns/threadsinsteadoftasks).

```
   submit(task)
        |
        v
   [ core threads busy? ] --no--> start a new core thread
        | yes
        v
   [ queue accepts it? ] --yes--> wait in queue      <-- unbounded queue: max pool
        | no                                             size is never reached
        v
   [ below maximumPoolSize? ] --yes--> start a new thread
        | no
        v
   RejectedExecutionHandler   (Abort | CallerRuns | Discard | DiscardOldest)
```

## Walk through what `ThreadPoolExecutor` does with a submitted task.
- id: pool-sizing-algorithm
- level: mid
- tags: executors, internals

* [ ] It always queues the task and a thread picks it up
* [x] Below core size it starts a thread; otherwise it queues; only if the queue rejects does it grow to maximum; then it rejects
* [ ] It starts a thread up to maximum, then queues
* [ ] It runs the task on the calling thread

The order is the part people get backwards, and it explains most misconfigured pools:

1. Fewer than `corePoolSize` threads: start a new thread, even if others are idle.
2. Otherwise offer to the queue. If it accepts, done.
3. Only if the queue refuses (a bounded queue that is full) start threads up to `maximumPoolSize`.
4. If that fails too, hand the task to the `RejectedExecutionHandler`.

The consequence: with an unbounded queue, step 3 never happens, so `maximumPoolSize` is dead
configuration and the pool never grows past core. People set a maximum of 200, watch the pool stay at
10, and conclude the setting is broken.

## Why is `Executors.newFixedThreadPool` considered dangerous in production?
- id: fixed-pool-unbounded-queue
- level: mid
- tags: executors, backpressure

* [ ] It creates too many threads
* [x] Its queue is unbounded, so a backlog grows until the heap is gone instead of pushing back
* [ ] It never shuts down
* [ ] It uses daemon threads

`newFixedThreadPool` and `newSingleThreadExecutor` both use an unbounded queue. When producers outrun
consumers, nothing pushes back: the queue grows, old GC pressure rises, and the process dies with an
`OutOfMemoryError` whose stack trace points at the queue rather than at the real cause.

`newCachedThreadPool` has the opposite failure. Its `SynchronousQueue` has no capacity and its maximum
is `Integer.MAX_VALUE`, so a burst creates a thread per task until the OS refuses.

The production shape is to build the executor yourself:

```java
new ThreadPoolExecutor(8, 8, 60, SECONDS,
        new ArrayBlockingQueue<>(1000),           // bounded: backpressure
        new NamedThreadFactory("api-"),           // named: readable thread dumps
        new ThreadPoolExecutor.CallerRunsPolicy() // slow the producer down
);
```

## How do you size a thread pool?
- id: pool-sizing-formula
- level: mid
- tags: executors, performance

* [ ] Always the number of CPU cores
* [x] For CPU-bound work, about the core count; for blocking work, cores * (1 + wait time / service time), then measure
* [ ] As many threads as concurrent requests
* [ ] Twice the number of cores, always

For CPU-bound tasks, more threads than cores only adds context switches: `availableProcessors()`, or
one less if a thread is needed elsewhere. For blocking tasks the threads are mostly asleep, so the
useful count scales with how much of the time is spent waiting: `N = cores * utilisation * (1 + W/S)`.
A task that waits 90 ms per 10 ms of compute wants roughly ten threads per core.

Say the rest of it, because that is what distinguishes an engineer from a formula: the number is a
starting point to be measured, separate pools for separate workloads so a slow dependency cannot
starve fast ones, and `availableProcessors()` respects cgroup limits in a container only on a
reasonably modern JVM, so check what the pod actually reports.

Virtual threads change this: for blocking work the answer becomes "do not pool at all".

## What is the difference between `shutdown()` and `shutdownNow()`, and how do you shut down properly?
- id: pool-shutdown
- level: mid
- tags: executors, lifecycle

* [ ] `shutdown()` kills threads immediately
* [x] `shutdown()` stops accepting work and drains the queue; `shutdownNow()` also interrupts running tasks and returns the queued ones
* [ ] They are identical
* [ ] `shutdownNow()` waits for tasks to finish

`shutdown()` is graceful: no new submissions, everything already queued still runs. `shutdownNow()`
tries to stop immediately: it drains the queue, returns the tasks that never ran, and interrupts the
workers. Interrupt is cooperative, so a task that ignores interruption keeps going regardless.

Neither method waits. The complete idiom:

```java
pool.shutdown();
if (!pool.awaitTermination(30, SECONDS)) {
    pool.shutdownNow();
    if (!pool.awaitTermination(30, SECONDS)) {
        log.error("pool did not terminate");
    }
}
```

A non-daemon pool that is never shut down keeps the JVM alive after main returns, which is a common
cause of a process that will not exit.

## What happens to an exception thrown inside a pooled task?
- id: pool-exceptions
- level: mid
- tags: executors, error-handling

* [ ] It kills the pool
* [x] `execute` reaches the uncaught handler; `submit` hides it in the `Future` until someone calls `get()`
* [ ] It is always printed to stderr
* [ ] It is rethrown on the submitting thread

This asymmetry swallows more bugs than any other part of the API. `execute(Runnable)` lets the
exception propagate: the thread's `UncaughtExceptionHandler` runs (by default printing to stderr) and
`ThreadPoolExecutor` quietly replaces the dead worker.

`submit` wraps the task in a `FutureTask`, which catches everything and stores it. No log line, no
handler, nothing, until someone calls `get()` and receives an `ExecutionException`. A fire-and-forget
`submit` whose `Future` is discarded loses failures completely.

Defences: wrap task bodies in try/catch, override `afterExecute` to inspect both paths, or use
`CompletableFuture` with `whenComplete`. For scheduled tasks it is worse, see the next question.

## What happens when a `ScheduledExecutorService` task throws?
- id: scheduled-task-throws
- level: senior
- tags: executors, scheduling

* [ ] The next execution happens as normal
* [x] The repeating schedule is cancelled silently, and nothing runs again until the process restarts
* [ ] The pool shuts down
* [ ] The exception is logged and the task retried

`scheduleAtFixedRate` and `scheduleWithFixedDelay` stop rescheduling if the task throws. The
`ScheduledFuture` completes exceptionally, and because nobody holds it, the failure is invisible. The
symptom is a heartbeat, a cache refresh or a cleanup job that "stopped working last Tuesday".

The fix is to make the task incapable of throwing:

```java
scheduler.scheduleWithFixedDelay(() -> {
    try {
        refresh();
    } catch (Throwable t) {          // Throwable: an Error kills the schedule just as dead
        log.error("refresh failed", t);
    }
}, 0, 1, MINUTES);
```

While you are there: `scheduleAtFixedRate` measures from start to start, so if a run overruns the
period the next one begins immediately and they can pile up. `scheduleWithFixedDelay` measures from
end to start and cannot.

## Which rejection policy would you choose and why?
- id: rejection-policies
- level: senior
- tags: executors, backpressure

* [ ] `DiscardPolicy`, it never fails
* [x] `CallerRunsPolicy` when you want backpressure, `AbortPolicy` when the caller must know, and the discard policies almost never
* [ ] `AbortPolicy` always
* [ ] It makes no difference

`AbortPolicy` (the default) throws `RejectedExecutionException`, which is honest: the caller learns
the system is saturated and can shed load, return 503 or retry.

`CallerRunsPolicy` runs the task on the submitting thread. That is a throttle with feedback: while the
web thread is executing a task it is not accepting new requests, so the pressure propagates back to
the client. It is the right default for an ingest pipeline, and the wrong one where the caller is an
event loop that must not block.

`DiscardPolicy` and `DiscardOldestPolicy` drop work silently. That is acceptable only for data whose
loss is genuinely fine, such as sampled metrics, and it should be counted so the loss is visible.
Whatever you pick, a rejection counter on a dashboard is what turns this from a guess into an
operational signal.

## What is `invokeAll` versus `invokeAny`, and where does `CompletionService` fit?
- id: invokeall-invokeany
- level: mid
- tags: executors, api

* [ ] `invokeAll` runs tasks one by one
* [x] `invokeAll` blocks until all finish and returns futures in submission order; `invokeAny` returns the first success and cancels the rest; `CompletionService` yields results as they complete
* [ ] `invokeAny` returns the fastest task's future without running the others
* [ ] They all require a `ScheduledExecutorService`

`invokeAll` submits a batch and blocks until every task is done (or the timeout expires), returning
futures in the order of the input, all of them already complete. `invokeAny` is the racing variant: the
first task to return normally wins, its value is returned, and the others are cancelled.

Neither helps when you want to process results in completion order rather than submission order, and
writing that with `Future.get()` in a loop wastes time on a slow first element.
`ExecutorCompletionService` solves it: it pushes each finished task onto a queue you `take()` from, so
you always handle whatever is ready.

In modern code `CompletableFuture.allOf` / `anyOf` covers the same ground non-blockingly, and
`StructuredTaskScope` covers it with cancellation built in.

## Why name your threads, and how?
- id: thread-factory
- level: junior
- tags: executors, diagnostics

* [ ] Naming has no runtime effect
* [x] `pool-3-thread-7` tells you nothing in a dump, and a factory also sets daemon status and handlers
* [ ] To make threads faster
* [ ] Because the JVM requires unique names

Thread names are the primary label in thread dumps, profilers, APM traces and log MDCs. When
production is on fire and the dump shows 200 threads called `pool-2-thread-N`, you cannot tell which
subsystem is stuck. `payment-callback-7` answers the question instantly.

A `ThreadFactory` is also where you centralise the rest: daemon status, an `UncaughtExceptionHandler`
that logs rather than prints, a context classloader, and a per-pool counter. Any small library or a
handful of lines will do, and it costs nothing at runtime.

## Why must a `ThreadLocal` be removed in a pooled thread?
- id: threadlocal-in-pools
- level: senior
- tags: executors, threadlocal, memory-leak

* [ ] It does not have to be, the GC handles it
* [x] Because the thread outlives the task, so the value stays reachable and leaks, and the next unrelated task on that thread can read it
* [ ] Because `ThreadLocal` is deprecated
* [ ] Because it is not thread safe

A `ThreadLocal` entry lives in a map owned by the thread. In a pool the thread is reused indefinitely,
so the value is never collected, which in an application server means a retained classloader and the
familiar "PermGen/Metaspace leak after redeploy". Worse than the leak: task B sees the tenant id, user
principal or transaction that task A left behind.

The keys in `ThreadLocalMap` are weak references but the values are not, so dropping the
`ThreadLocal` object does not free the value until that slot happens to be cleaned. The discipline is
a `finally`:

```java
try {
    context.set(requestContext);
    handle(request);
} finally {
    context.remove();
}
```

In Java 21, `ScopedValue` is the intended replacement: immutable, bounded to a scope, and inherited by
structured concurrency forks without any of this.

## What is work stealing, and which executors use it?
- id: work-stealing
- level: senior
- tags: executors, forkjoin

* [ ] Threads take tasks from each other's stacks at random
* [x] Each worker has its own deque, pushes and pops at its own end, and steals from the other end of a busy worker's deque when idle
* [ ] The OS scheduler moves tasks between threads
* [ ] Only virtual threads use it

In a classic pool, all workers contend on one shared queue, and that queue is the bottleneck. In a
work-stealing pool each worker owns a double-ended queue. It pushes and pops from its own end (LIFO,
which keeps the freshest and most cache-warm task), and an idle worker steals from the opposite end
(FIFO, taking the oldest and typically largest piece of work).

`ForkJoinPool`, the common pool behind parallel streams, `Executors.newWorkStealingPool` and the
virtual thread scheduler all work this way. It shines for recursive divide-and-conquer where tasks
spawn subtasks; it buys little for uniform, independent, blocking tasks, where a plain pool is simpler
and just as fast.

## What breaks when a pooled task submits another task to the same pool and waits for it?
- id: pool-self-deadlock
- level: senior
- tags: executors, deadlock

* [ ] Nothing, the pool grows as needed
* [x] Thread starvation deadlock: the waiting tasks occupy every thread, and the tasks they wait for can never be scheduled
* [ ] The task is rejected
* [ ] The pool creates a new thread automatically

With a pool of N threads, if N tasks each block on the result of a subtask they submitted to the same
pool, there is no thread left to run any subtask. Nothing is deadlocked in the lock sense, so a dump
shows no cycle, only every worker parked in `Future.get`. It is invisible under light load and appears
the moment concurrency reaches the pool size.

Three ways out: separate pools for the two layers, so the dependency crosses a boundary; a non-blocking
composition with `CompletableFuture` so nothing waits; or `ForkJoinPool`, whose `join` runs pending
tasks on the current thread (the reason `invokeAll` inside fork/join is safe).

The same hazard appears with nested parallel streams, which all share the common pool by default.
