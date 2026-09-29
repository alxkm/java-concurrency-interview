# Fork/join and parallel streams

One method call turns a stream parallel, which is why this topic is full of code that is slower than
the sequential version it replaced. Interviewers ask it to see whether you can say no.

Runnable: [fork/join](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/forkjoinpool).

```
   fork/join, one worker's deque

   push/pop this end (LIFO, cache-warm)      steal from this end (FIFO, biggest task)
            |                                              |
            v                                              v
        [ task4 ][ task3 ][ task2 ][ task1 ]  <---- idle worker steals here

   compute() { if (small enough) solve directly;
               else split, fork one half, compute the other, join }
```

## How does `ForkJoinPool` differ from a fixed thread pool?
- id: forkjoin-vs-fixed
- level: mid
- tags: forkjoin, executors

* [ ] It is a fixed pool with a nicer API
* [x] Per-worker deques with work stealing, and a `join` that runs pending tasks instead of blocking
* [ ] It creates a thread per task
* [ ] It is only for parallel streams

A fixed pool has one shared queue, and every worker contends on it. A fork/join pool gives each worker
a deque it owns, pushing and popping its own end with no contention, and stealing from the other end of
someone else's when it runs dry.

The other half is `join`. In a plain pool, waiting for a subtask blocks a worker and can starve the
pool. In fork/join, `join` first tries to execute other pending tasks on the current thread, so the
thread stays useful and recursive decomposition does not deadlock.

That design targets CPU-bound divide-and-conquer. For independent blocking tasks it brings nothing
over a plain pool, and its assumptions actively hurt.

## What is the correct shape of a `RecursiveTask`?
- id: recursivetask-shape
- level: mid
- tags: forkjoin, api

* [ ] Fork both halves, then join both
* [x] Fork one half, compute the other on the current thread, then join: `left.fork(); right.compute(); left.join();`
* [ ] Call `invoke()` on both halves
* [ ] Submit both halves to the common pool

```java
protected Long compute() {
    if (hi - lo <= THRESHOLD) {
        return computeDirectly();
    }
    int mid = (lo + hi) >>> 1;
    SumTask left = new SumTask(array, lo, mid);
    left.fork();                       // hand one half to the pool
    long rightResult = new SumTask(array, mid, hi).compute();   // do the other here
    return rightResult + left.join();  // then collect
}
```

Forking both halves and joining both wastes the current thread, which sits idle while two other threads
work. The order matters too: fork, then compute, then join. Joining before computing the second half
serialises the whole thing.

The threshold is the other half of the answer. Too small and the splitting overhead dominates; the
usual starting point is a few thousand elements of simple work, tuned by measurement.

## What is the common pool, and why does it cause trouble?
- id: common-pool
- level: senior
- tags: forkjoin, parallel-streams

* [ ] It is a pool of shared objects
* [x] A JVM-wide pool of cores minus one, shared by every parallel stream and every `*Async` call
* [ ] It is created per parallel stream
* [ ] It is unbounded

`ForkJoinPool.commonPool()` is a static, shared, lazily created pool sized at
`availableProcessors() - 1`. Every parallel stream in the JVM uses it, including ones inside libraries,
and `CompletableFuture`'s async methods use it when you do not pass an executor.

So a single parallel stream doing blocking I/O occupies a scarce global resource, and every other
parallel operation in the process slows down or stalls. On a machine reporting one CPU, the common pool
has zero threads and everything runs on the caller.

You can raise its size with `-Djava.util.concurrent.ForkJoinPool.common.parallelism=N`, which is a
blunt global setting, or run the stream inside your own `ForkJoinPool` by submitting it there, which
works because the stream uses the pool of the thread it runs on.

## When is a parallel stream faster, and when is it slower?
- id: parallel-stream-when
- level: mid
- tags: parallel-streams, performance

* [ ] Always faster with more than one core
* [x] Faster for large, CPU-bound, splittable, independent work; slower for small collections, blocking work, or sources that split badly
* [ ] Faster only for `IntStream`
* [ ] Slower only when the collection is sorted

The rough rule of thumb is N times Q: the number of elements times the cost per element. If that
product is not in the hundreds of thousands of cycles, the splitting, task submission and merging cost
more than the parallelism saves.

Four things must hold: enough data, real CPU work per element, a source that splits evenly, and
independent elements. `ArrayList`, arrays and `IntStream.range` split perfectly. `LinkedList`,
`Stream.iterate` and a `BufferedReader`'s lines do not: they must be traversed to be split, so one
thread does most of the work.

The rest is usually a mistake: blocking I/O in a parallel stream ties up the common pool, `limit` and
`findFirst` on an ordered stream force coordination, and a stateful lambda is a race waiting to happen.
Measure both, on production-shaped data.

## Why is `forEach` on a parallel stream dangerous, and what should you use?
- id: parallel-foreach
- level: mid
- tags: parallel-streams, api

* [ ] It is single threaded
* [x] It runs unordered from many threads, so shared accumulation is a race; use `collect`
* [ ] It throws on a parallel stream
* [ ] It silently drops elements

`forEach` makes no ordering promise and calls your lambda from every worker thread at once. Adding to
an `ArrayList` from it corrupts the list; adding to a `Collectors.toList` result via a side effect is
the same bug with more steps.

```java
List<String> out = new ArrayList<>();
stream.parallel().forEach(out::add);          // race, lost elements, occasional exception

List<String> out = stream.parallel().collect(toList());   // correct and faster
```

`collect` is designed for this: each thread accumulates into its own container and the containers are
merged. `forEachOrdered` restores encounter order at the cost of the parallelism you asked for. And
even a synchronized list is the wrong fix, since every element then serialises on one lock.

## What does `Collectors.toConcurrentMap` change, and when is `groupingByConcurrent` actually used?
- id: concurrent-collectors
- level: senior
- tags: parallel-streams, collectors

* [ ] They are the only collectors that work in parallel
* [x] They let all threads accumulate into one concurrent container instead of merging per-thread maps, which only helps when the collector is unordered and the stream is parallel
* [ ] They make the stream parallel automatically
* [ ] They preserve encounter order

An ordinary `groupingBy` in a parallel stream gives each thread its own map and merges them at the end,
which allocates and copies. The concurrent versions declare the `CONCURRENT` characteristic, so the
framework lets every thread write into a single `ConcurrentHashMap` instead.

That is faster only when merging was actually expensive, and it requires the collector to be
`UNORDERED` too, otherwise the framework falls back to merging to preserve encounter order. Since the
result of a concurrent collector is in unspecified order anyway, using one on a stream where order
matters is a correctness bug rather than an optimisation.

In practice: profile first. The sequential collector plus merge wins more often than people expect.

## What is a `Spliterator` and why does it matter for parallelism?
- id: spliterator
- level: senior
- tags: parallel-streams, internals

* [ ] An iterator that can be reset
* [x] The stream source: it traverses and splits, so how well it splits decides whether parallelism helps
* [ ] A collector for splitting results
* [ ] A JIT optimisation

Every stream is backed by a `Spliterator`. Sequentially it behaves like an iterator via
`tryAdvance`/`forEachRemaining`; in parallel the framework calls `trySplit` recursively to build the
task tree.

Its characteristics (`SIZED`, `SUBSIZED`, `ORDERED`, `DISTINCT`, `SORTED`, `IMMUTABLE`, `NONNULL`) are
what the pipeline optimises against. An array spliterator is `SIZED` and `SUBSIZED`, so splits are
exact and free. A linked list's is neither: `trySplit` has to walk the nodes to buffer a prefix, and the
split is uneven.

If you write a custom data source and want parallel streams over it to be worth anything, implementing
`trySplit` and reporting accurate characteristics is the work. The default from
`Spliterators.spliteratorUnknownSize` splits poorly on purpose.

## Can you run a parallel stream in your own pool, and should you?
- id: stream-custom-pool
- level: senior
- tags: parallel-streams, forkjoin

* [ ] No, parallel streams always use the common pool
* [x] Yes, by submitting the whole pipeline to your own `ForkJoinPool`, which is a documented-enough trick but not an officially supported API
* [ ] Yes, via `stream.parallel(executor)`
* [ ] Yes, with a system property per stream

There is no API for it. The trick works because a parallel stream executes in the pool of the thread
that runs it:

```java
ForkJoinPool pool = new ForkJoinPool(8);
try {
    List<Result> out = pool.submit(() -> items.parallelStream().map(this::work).toList()).get();
} finally {
    pool.shutdown();
}
```

It is widely used to isolate one workload from the common pool, and it works on every current JVM, but
it relies on an implementation detail rather than a specification. Say that in an interview: it is the
kind of nuance the question is testing for. If you need genuine isolation and control, an explicit
executor with explicit tasks is clearer than a stream.

## Why is `Stream.iterate` a poor source for a parallel stream?
- id: stream-iterate-parallel
- level: mid
- tags: parallel-streams, performance

* [ ] It is infinite
* [x] Each element depends on the last, so the source cannot be split
* [ ] It is not thread safe
* [ ] It boxes every element

`Stream.iterate(0, i -> i + 1)` is inherently sequential: element N cannot be produced without
producing N-1. Its spliterator cannot split usefully, so the framework buffers chunks and you get the
cost of task management with almost none of the parallelism.

`IntStream.range(0, n)` is the fix where it applies: the range is known, so splitting is exact and
free. The general lesson transfers to any source whose next value depends on its last, including
reading a file line by line and iterating a `LinkedList`. If the source cannot be split, parallelism
cannot help, no matter how expensive the per-element work is.

## What happens if a fork/join task blocks on I/O?
- id: forkjoin-blocking
- level: senior
- tags: forkjoin, performance

* [ ] The pool creates a replacement thread automatically
* [x] The worker is stuck, its deque is unavailable except to stealers, and with enough blocked workers the pool stops making progress; `ManagedBlocker` is the escape hatch
* [ ] The task is rescheduled
* [ ] Nothing, fork/join is designed for I/O

Fork/join is sized for CPU work, with roughly one thread per core, and its whole model assumes tasks
finish quickly and often spawn subtasks. A worker blocked on a socket does none of that: it holds a
thread, and the work it would have stolen goes undone. Block all of them and the pool is dead while the
CPU sits idle.

`ForkJoinPool.ManagedBlocker` is the official escape. It tells the pool "I am about to block", and the
pool may compensate by starting an extra thread. `ConcurrentHashMap.computeIfAbsent` and the
`CompletableFuture` join paths use it internally.

The better answer in 2026 is that blocking work belongs on virtual threads or a dedicated pool, and
fork/join should keep to what it was built for.

## What does `parallelStream()` change compared with `stream()`?
- id: parallel-stream-basics
- level: junior
- tags: parallel-streams, common-pool

* [ ] Every element is processed on a thread of its own
* [x] The source is split into chunks that run on the common `ForkJoinPool`, the caller helping
* [ ] The pipeline runs in order, just on one background thread
* [ ] Nothing, unless the source is a concurrent collection

The pipeline is the same; the execution is not. The source is split by its `Spliterator`, the
chunks run as fork/join tasks in `ForkJoinPool.commonPool()`, and the calling thread joins in rather
than waiting idle. The common pool has one thread fewer than there are cores, so with the caller the
work uses every core.

Three consequences are what the interviewer is after:

- **Order is not guaranteed** for `forEach`. Use `forEachOrdered`, or better, a collector.
- **Side effects break.** Adding to a shared `ArrayList` from `forEach` loses elements or throws.
  `collect(toList())` is correct in parallel because each chunk builds its own list and they are merged.
- **It is not automatically faster.** Splitting and merging cost something, so a few thousand cheap
  operations usually run slower in parallel. Measure first.

And the one that bites in production: every parallel stream in the JVM shares that one common pool,
so a slow or blocking operation in one of them slows down all the others.
