# Synchronizers: latches, barriers, semaphores

The coordination primitives, and the topic with the single most predictable interview question in Java:
the difference between a latch and a barrier. Knowing the whole family is what separates a memorised
answer from a chosen one.

Runnable: [synchronizers](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/synchronizers), [semaphore](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/semaphore).

```
   CountDownLatch  one-shot, N counts down, everyone waits for zero
        start ---[3]---[2]---[1]---[0] -> all waiters released, forever open

   CyclicBarrier   N parties meet, all released together, then it RESETS
        round 1: |--wait--wait--GO--|  round 2: |--wait--wait--GO--|

   Semaphore       N permits, acquire blocks when none are left, release gives one back
        [****......]  4 of 10 in flight

   Phaser          a barrier whose party count changes between phases
   Exchanger       two threads swap objects at a rendezvous point
```

## What is the difference between `CountDownLatch` and `CyclicBarrier`?
- id: latch-vs-barrier
- level: mid
- tags: synchronizers, latch, barrier

* [ ] The barrier is thread safe and the latch is not
* [x] A latch is one-shot and counted down by anyone; a barrier waits for N parties and resets for reuse
* [ ] A latch waits for threads, a barrier waits for time
* [ ] They differ only in the method names

A `CountDownLatch` counts towards zero and stays there. Any thread may call `countDown()`, including
threads that never wait, and once it opens it can never close. That makes it the tool for "start
everyone at once" and "wait until all N things have happened".

A `CyclicBarrier` counts arrivals. Each party calls `await()`, which blocks until the Nth party
arrives, then all are released together and the barrier resets for the next round. The parties are the
waiters, and the count is fixed at construction.

Two more differences worth volunteering. The barrier can run a barrier action, a `Runnable` executed by
the last arriving thread before the others are released, which is where you merge results between
rounds. And a barrier is fragile: if one party is interrupted or times out, every other party gets a
`BrokenBarrierException`, because a barrier that cannot reach N is useless to everyone.

## When would you use a latch as a starting gate rather than as a finish line?
- id: latch-starting-gate
- level: mid
- tags: synchronizers, latch, testing

* [ ] A latch can only be used as a finish line
* [x] Two latches: one released to start every thread at once, one counted down as each finishes
* [ ] A starting gate needs a `CyclicBarrier`, a latch cannot do it
* [ ] `Thread.sleep` in each thread achieves the same

The two-latch idiom is the standard way to create real contention, in tests and in benchmarks:

```java
CountDownLatch start = new CountDownLatch(1);      // the gate
CountDownLatch done  = new CountDownLatch(threads); // the finish line

for (int i = 0; i < threads; i++) {
    new Thread(() -> {
        start.await();          // everyone piles up here (try/catch omitted)
        work();
        done.countDown();
    }).start();
}
start.countDown();              // released together
done.await(10, SECONDS);        // bounded, so a hang fails instead of hanging
```

Without the gate, thread 1 usually finishes before thread 8 has started, because starting a thread
costs more than a short task body. A `CyclicBarrier` would also work for the gate, but it makes the
workers wait for each other rather than for you, and it cannot express "one controller releases N
workers".

## What does a `Semaphore` actually protect?
- id: semaphore-purpose
- level: junior
- tags: synchronizers, semaphore

* [ ] A single critical section, like a lock
* [x] A count of concurrent users of a resource, which is a different question from mutual exclusion
* [ ] The order in which threads run
* [ ] Memory visibility only

A lock answers "one at a time". A semaphore answers "at most N at a time", which is what you want when
the constraint lives outside your process: a downstream service that tolerates 50 concurrent calls, a
pool of 20 connections, a licence for 4 decoders.

```java
Semaphore permits = new Semaphore(50);
permits.acquire();
try {
    callDownstream();
} finally {
    permits.release();
}
```

Two things interviewers probe. A binary semaphore (`new Semaphore(1)`) is not a lock: it has no owner,
so any thread can release it, and it is not reentrant, so the same thread acquiring twice deadlocks.
That ownerless property is occasionally exactly what you want, when one thread signals and another
proceeds. And permits are just a count: releasing more than you acquired silently inflates the limit,
which is a bug that surfaces as an overloaded dependency weeks later.

## Why is `Semaphore` the right way to limit concurrency with virtual threads?
- id: semaphore-with-virtual-threads
- level: senior
- tags: synchronizers, semaphore, virtual-threads

* [ ] It is not, a fixed thread pool is still correct
* [x] Because the limit belongs to the resource, not to the thread count, and a semaphore says so directly
* [ ] Because virtual threads cannot use locks
* [ ] Because semaphores are faster on virtual threads

With platform threads, a pool of 20 did two jobs at once: it reused expensive threads, and it capped
how many things happened at the same time. Virtual threads remove the first job, and people then
discover the second one was load-bearing.

A semaphore expresses the cap where it belongs. One thread per task, and the permit count is the real
constraint written down:

```java
Semaphore db = new Semaphore(20);   // the connection pool has 20 connections

try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (var request : requests) {
        executor.submit(() -> {
            db.acquire();
            try { return query(request); } finally { db.release(); }
        });
    }
}
```

Now a million tasks can be in flight while exactly 20 touch the database, and a reader can see which
number belongs to which resource, instead of one pool size standing in for three unrelated limits.

## What is a `Phaser` and when does it beat a `CyclicBarrier`?
- id: phaser
- level: senior
- tags: synchronizers, phaser

* [ ] It is the deprecated predecessor of `CyclicBarrier`
* [x] A reusable barrier whose number of parties can change between phases, with parties registering and deregistering dynamically
* [ ] A barrier that never resets
* [ ] A scheduler for periodic tasks

`CyclicBarrier` fixes the party count at construction. `Phaser` lets threads `register()` and
`arriveAndDeregister()` as the computation proceeds, which fits work that spawns or retires
participants between rounds: a simulation where entities appear and die, or a pipeline whose stages
drain one by one.

It also numbers its phases, so `arriveAndAwaitAdvance()` returns the phase you just completed and
`awaitAdvance(phase)` lets a thread wait for a specific round without being a party to it. Overriding
`onAdvance(phase, parties)` gives you the barrier action plus the ability to terminate the phaser by
returning true.

The honest caveat: most code that reaches for a `Phaser` does not need one, and the API is easy to get
wrong. Say that. Fixed parties and a fixed number of rounds are a `CyclicBarrier`, and one-shot is a
latch.

## What is an `Exchanger` for?
- id: exchanger
- level: senior
- tags: synchronizers, exchanger

* [ ] Swapping the contents of two collections atomically
* [x] A rendezvous where exactly two threads meet and swap objects, each blocking until the other arrives
* [ ] Exchanging a lock between two threads
* [ ] A queue with two ends

`Exchanger<T>` is a two-party meeting point: each thread calls `exchange(myObject)`, blocks, and
receives what the other one passed. It is a `SynchronousQueue` that goes both ways.

The canonical use is double buffering in a producer/consumer pair. The producer fills a buffer while
the consumer drains another, and when both are done they swap buffers at the exchanger, with no
allocation and no copying.

It is rare in application code, and that is fine to say. What it demonstrates in an interview is that
you know the `java.util.concurrent` catalogue rather than just the three classes everyone names.

## How does `CompletableFuture.allOf` compare to a `CountDownLatch` for "wait for N tasks"?
- id: allof-vs-latch
- level: mid
- tags: synchronizers, completablefuture, design

* [ ] They are equivalent, pick either
* [x] The latch blocks a thread and carries no results or failures; `allOf` composes without blocking and propagates both
* [ ] `allOf` is only for I/O
* [ ] A latch is faster and should be preferred

A latch is a counter. It tells you that N things finished and nothing else: no values, no exceptions,
no cancellation. You still need somewhere to put the results, usually a concurrent collection, and
somewhere to put the failures, usually an `AtomicReference` everyone forgets to check. And someone must
block on `await()`.

`allOf` gives a future that completes when all inputs do, carries each result in its own future, and
completes exceptionally if any input fails. Nothing blocks unless you ask it to.

A latch still wins in two places: coordinating threads you did not start as futures, such as a starting
gate in a test, and simple lifecycle signalling ("the server is up"). Structured concurrency covers
the fan-out case with cancellation on top, which is the answer to the follow-up about what you would
use on Java 21 and newer.

## Why must a `Semaphore` release live in a `finally`, and what goes wrong if it does not?
- id: semaphore-leak
- level: mid
- tags: synchronizers, semaphore, antipattern

* [ ] Nothing, the permit is returned when the thread dies
* [x] A permit lost on an exception path is lost forever, so the limit shrinks with every failure until everything blocks
* [ ] The semaphore throws on the next acquire
* [ ] The JVM reclaims permits during garbage collection

Permits are a plain count, not an ownership record. Nobody tracks that your thread holds one, so
nothing returns it if you throw, return early or get interrupted between `acquire` and `release`. A
`Semaphore(50)` in a code path that fails one call in a thousand becomes a `Semaphore(0)` after enough
traffic, and the symptom is a service that goes quiet over days with no error to point at.

```java
semaphore.acquire();
try {
    call();
} finally {
    semaphore.release();      // the only correct place
}
```

The mirror image is releasing without acquiring, in a retry path or a stray cleanup, which raises the
ceiling above the real limit and overloads the thing you were protecting. Both are silent. Exporting
`availablePermits()` as a metric is the cheapest way to see either one coming.

## Is `CountDownLatch.await()` a happens-before edge?
- id: latch-happens-before
- level: senior
- tags: synchronizers, memory-model

* [ ] No, latches only coordinate timing
* [x] Yes: everything before a `countDown()` happens-before anything after a returning `await()`
* [ ] Only if the shared state is volatile
* [ ] Only when the count reaches zero from a single thread

Every synchroniser in `java.util.concurrent` documents its memory effects, and this is the half people
forget. Actions in a thread before it calls `countDown()` happen-before actions following a successful
return from `await()` in another thread.

That is what makes the ordinary pattern correct: worker threads write their results into plain,
unsynchronised fields or array slots, count down, and the coordinator reads them all after `await()`
with no further synchronisation. No `volatile` is needed, because the latch provided the edge.

The same guarantee exists across the package: a `put` into a `BlockingQueue` happens-before the `take`
that removes it, submitting a task to an executor happens-before the task running, a task's completion
happens-before `Future.get()` returns, and `CyclicBarrier` and `Phaser` publish everything done before
the arrival to everything after the release.

## Which synchroniser would you choose, and how do you decide?
- id: choosing-a-synchronizer
- level: mid
- tags: synchronizers, design

* [ ] Always a `CountDownLatch`, it is the simplest
* [x] By what the waiting means: a one-shot event, a repeated meeting of N parties, a concurrency cap, or a handoff of data
* [ ] By how many threads there are
* [ ] By whether the code is CPU-bound

The question behind the question is whether you reach for a primitive or for a queue, so answer with
the decision rather than the list:

- **One-shot event, "wait until X has happened"**: `CountDownLatch`, or a `CompletableFuture` if a
  value or a failure needs to travel with it.
- **Repeated meeting of a fixed set of workers**: `CyclicBarrier`, with a barrier action for the
  between-rounds work.
- **The same, with a changing set of workers**: `Phaser`.
- **At most N at a time**: `Semaphore`, sized after the resource, not after the threads.
- **Handing data over**: a `BlockingQueue`, or an `Exchanger` for a symmetric two-party swap.
- **Fan-out and fan-in with results, failures and cancellation**: `CompletableFuture` or
  `StructuredTaskScope`, not a latch plus a concurrent collection.

And the meta-answer that earns the point: if you can express it as a queue between threads, do that
instead. A queue carries the data, provides the happens-before edge and gives you backpressure, which
none of these primitives do on their own.
