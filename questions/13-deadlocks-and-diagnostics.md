# Deadlock, livelock and diagnostics

The half of concurrency that only exists in production. Interviewers like this topic because it cannot
be answered from a tutorial: either you have read a thread dump at 3am or you have not.

Runnable: [deadlock](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/antipatterns/deadlock), [diagnostics](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/diagnostics), [dining philosophers](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/philosopher).

```
   thread A holds L1, wants L2        +------+        +------+
   thread B holds L2, wants L1        |  L1  |<--wait--|  B   |
                                      +------+        +------+
   Coffman conditions, all four:          ^ owns          ^ owns
     mutual exclusion                     |               |
     hold and wait                     +------+        +------+
     no preemption                     |  A   |--wait-->|  L2  |
     circular wait                     +------+        +------+
```

## What four conditions must hold for a deadlock, and which is easiest to break?
- id: coffman-conditions
- level: mid
- tags: deadlock, theory

* [ ] Two threads and two locks, nothing more
* [x] Mutual exclusion, hold and wait, no preemption, and circular wait; circular wait is the one you break in practice by imposing a lock ordering
* [ ] Only circular wait
* [ ] Deadlock requires at least three threads

All four Coffman conditions must hold at once, so removing any one prevents deadlock. In Java you
cannot remove mutual exclusion (that is the point of a lock) and you cannot preempt a monitor.

That leaves two. Break hold-and-wait with `tryLock` and a timeout: acquire what you can, release
everything and retry if you cannot. Break circular wait with a global lock ordering: if every thread
takes locks in the same order, no cycle can form. For dynamic objects the order can be derived, for
instance from `System.identityHashCode`, with a tie-breaker lock for the rare collision.

The cheapest defence of all is not holding two locks, which is usually achievable by narrowing the
critical sections.

## Show the classic transfer deadlock and fix it.
- id: transfer-deadlock
- level: mid
- tags: deadlock, lock-ordering

* [ ] Synchronize the whole transfer method
* [x] Opposite lock order on the two accounts; fix with a consistent global order or `tryLock`
* [ ] Use a `ConcurrentHashMap` of accounts
* [ ] Make the balances volatile

```java
void transfer(Account from, Account to, long amount) {
    synchronized (from) {
        synchronized (to) { ... }     // transfer(a, b) and transfer(b, a) deadlock
    }
}
```

The ordered fix picks a total order over accounts and always locks in that order:

```java
Account first  = from.id() < to.id() ? from : to;
Account second = from.id() < to.id() ? to : from;
synchronized (first) {
    synchronized (second) { ... }
}
```

If there is no natural id, use `System.identityHashCode` plus a tie-break lock for the collision case.
The alternative is `tryLock` on the second account with a timeout, releasing the first and retrying
after a random backoff, which also handles locks you do not control.

## How do you diagnose a deadlock in a running JVM?
- id: diagnosing-deadlock
- level: mid
- tags: diagnostics, deadlock

* [ ] Read the logs
* [x] Take a thread dump: the JVM prints "Found one Java-level deadlock" with both stacks
* [ ] Attach a debugger and step through
* [ ] Restart and hope

`jcmd <pid> Thread.print` or `jstack <pid>` gives you the dump, and HotSpot does the analysis for you:
it walks the monitor ownership graph and prints a "Found one Java-level deadlock" section naming both
threads, the locks each holds and the lock each wants.

Two caveats worth raising, because they are what the follow-up question is about. Deadlock detection
covers monitors and, since Java 6, `java.util.concurrent` locks that implement `AbstractOwnableSynchronizer`,
but not a `Semaphore` misuse or a latch nobody counts down, which is a hang without a cycle. And a
thread waiting on a `ReentrantLock` shows as WAITING at `LockSupport.park`, not BLOCKED, so the
ownership line (`- parking to wait for <0x...> owned by "worker-3"`) is what you look for.

Take three dumps a few seconds apart. One dump shows where threads are; three show whether they are
moving.

## What is a livelock, and how does it differ from a deadlock?
- id: livelock
- level: mid
- tags: livelock, theory

* [ ] A deadlock that resolves itself
* [x] Threads keep running and keep responding to each other, but no one makes progress; CPU is busy, unlike a deadlock where everyone is parked
* [ ] A thread that never gets CPU time
* [ ] A deadlock involving more than two locks

The corridor analogy: two people step aside for each other, repeatedly, in the same direction. Nobody
is blocked, everybody is polite, nobody gets through.

In code it usually comes from naive deadlock avoidance: two threads both detect a conflict, both
release their locks, both retry immediately, and both collide again. It shows up as high CPU with no
throughput, which is the opposite signature to a deadlock, where CPU is at zero and threads are
BLOCKED.

The standard fix is randomised exponential backoff, so the retries desynchronise. Ethernet's collision
handling is the same idea. Message-driven systems produce a variant where two actors bounce a message
back and forth forever.

## What is starvation, and what causes it in a JVM?
- id: starvation
- level: mid
- tags: starvation, fairness

* [ ] A thread with too little heap
* [x] A thread that never gets the resource: barging locks, hogging tasks, endless readers
* [ ] A pool with no idle threads
* [ ] A GC pause

Starvation is progress denied indefinitely to one thread while others proceed. The usual causes: an
unfair lock where arriving threads barge ahead of the queue; long-running tasks in a small pool leaving
no slot for others; a write lock behind a continuous stream of readers; and thread priorities, which
are a hint the OS may ignore entirely and should never be used as a correctness mechanism.

Thread starvation deadlock is the variant worth naming separately: every thread in a pool waits for a
task that can only run on that same pool. Nothing is BLOCKED, no cycle exists, and the pool is simply
dead.

Fair locks and bounded task durations are the fixes; fairness costs throughput, so it is a trade you
make deliberately.

## How do you read a thread dump? What do you look for first?
- id: reading-thread-dumps
- level: senior
- tags: diagnostics

* [ ] The first stack trace
* [x] The deadlock section, then shared top frames, then lock owners, then a second dump later
* [ ] Only threads in RUNNABLE state
* [ ] The heap summary

A method, in order:

1. The deadlock section at the bottom, if HotSpot found one.
2. Cluster the threads by their top frames. Forty threads in the same `socketRead0` is the answer;
   forty in forty places is not a hang.
3. Follow ownership: `- waiting to lock <0x000000076b2c3f10>` plus
   `- locked <0x000000076b2c3f10>` in another thread names the culprit directly.
4. Take three dumps twenty seconds apart. Threads that are stuck stay identical; threads that are busy
   move.
5. Check the pool threads' names, which is why naming them matters.

Also worth knowing: RUNNABLE in a dump means "not blocked by the JVM", and a thread blocked in native
socket I/O still reports RUNNABLE. That misleads people constantly.

## Your service stops responding but CPU is at zero. What is your first hypothesis?
- id: hang-zero-cpu
- level: senior
- tags: diagnostics, scenario

* [ ] An infinite loop
* [x] Everyone is blocked: a deadlock, an exhausted pool, or a lock held across an I/O call
* [ ] A garbage collection storm with long pauses
* [ ] A CPU throttle

Zero CPU with no progress means nobody is running, so look for where they are all parked. The three
usual shapes: a genuine deadlock cycle; a thread pool whose every worker is waiting on a slow or
unresponsive dependency, with the queue growing behind it; or one lock held across an I/O call, with
everyone else BLOCKED behind it.

The dump tells you which within a minute. A missing timeout is the root cause an embarrassing amount of
the time: a socket read with no `SO_TIMEOUT`, a `Future.get()` with no bound, an HTTP client whose
default is infinite.

High CPU with no progress is the opposite diagnosis: a livelock, a spin loop, or a GC death spiral,
which you separate with `jcmd GC.heap_info` or a GC log rather than a thread dump.

## Which JDK tools do you use for concurrency problems?
- id: concurrency-tooling
- level: mid
- tags: diagnostics, tooling

* [ ] Print statements
* [x] jcmd and jstack for dumps, JFR in production, async-profiler for lock contention
* [ ] A debugger, stepping through each thread
* [ ] VisualVM only

The toolbox worth naming:

- **jcmd / jstack**: thread dumps; `jcmd Thread.dump_to_file -format=json` for virtual threads.
- **JFR**: `jcmd JFR.start settings=profile`. Events for monitor contention (`jdk.JavaMonitorEnter`),
  park duration, thread starts and virtual thread pinning, with low enough overhead for production.
- **async-profiler**: `-e lock` for lock contention and wall-clock mode, which is the one that shows
  where threads wait rather than where they burn CPU.
- **jcstress**: for memory model questions, where an ordinary test cannot reproduce the effect at all.
- **Thread MXBean**: `findDeadlockedThreads()` programmatically, for a health check endpoint.

A debugger is the tool that least often helps, because stopping at a breakpoint changes the
interleaving you are trying to observe.

## Why does a debugger so often make a concurrency bug disappear?
- id: heisenbug
- level: mid
- tags: diagnostics, testing

* [ ] Debuggers fix race conditions
* [x] A breakpoint, and even a log line, adds synchronisation and delay that changes the interleaving, so the race no longer occurs
* [ ] The JIT is disabled under a debugger
* [ ] Debuggers run code single threaded

A concurrency bug is a property of one interleaving out of many. Anything that changes timing changes
the distribution of interleavings: a breakpoint suspends threads, a `println` takes a lock on the
stream and performs I/O, and a logging call flushes buffers. Any of them can make the bad interleaving
effectively impossible.

There is a memory model dimension too. `System.out.println` is synchronized, so it inserts a happens-
before edge that may be the only reason the values look consistent. Removing a log line and watching a
bug appear is a genuine and maddening experience.

What works instead: stress the code with jcstress, run with `-XX:+StressLCM -XX:+StressGCM`, test on
weakly ordered hardware (ARM), use assertions and invariant checks rather than prints, and reason about
happens-before rather than about observed behaviour.

## A colleague reports a bug that happens once a week in production and never locally. How do you approach it?
- id: rare-production-race
- level: senior
- tags: diagnostics, scenario

* [ ] Add retries until it stops
* [x] Treat rarity as evidence of a race: review for unguarded state, add JFR, reproduce with stress
* [ ] Increase the thread pool size
* [ ] Ask the team to restart nightly

Once a week means an interleaving that needs an unlikely coincidence, which is the signature of a data
race rather than a logic error. The productive order:

1. Review the code path for shared mutable state, and for every field ask which lock or happens-before
   edge covers it. Most races are found this way, not by observation.
2. Add cheap invariant checks that fail loudly and capture context when the state is impossible.
3. Turn on JFR continuously so the next occurrence comes with evidence instead of a bug report.
4. Reproduce deliberately: jcstress for a memory model hypothesis, a load test with more threads than
   cores, and ARM hardware, where weak ordering exposes what x86 hides.
5. Fix the race, not the symptom. A retry or a sleep that makes it rarer converts a weekly bug into a
   quarterly one that is far harder to find.

Saying "a retry makes it rarer, not absent" is usually what the interviewer is listening for.
