# Java Concurrency Interview

[![build](https://github.com/alxkm/java-concurrency-interview/actions/workflows/ci.yml/badge.svg)](https://github.com/alxkm/java-concurrency-interview/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![questions](https://img.shields.io/badge/questions-173-blue.svg)](#contents)

173 interview questions on Java concurrency, each answered the way you would have to answer it out
loud: the mechanism, the trade-off, and the follow-up the interviewer asks next.

It is also a quiz you can run. One file, no dependencies, ASCII only.

<!-- BEGIN SCREEN -->
```
  +----------------------------------------------------------------------------------------+
  |                                                                                        |
  |   J A V A   C O N C U R R E N C Y   I N T E R V I E W                                  |
  |                                                                                        |
  |   writer   --- write x = 1 --->[ release ]                                             |
  |                                     |                                                  |
  |                                     |  happens-before                                  |
  |                                     v                                                  |
  |   reader   ------------------->[ acquire ]--- reads x == 1 --->                        |
  |                                                                                        |
  |   173 questions across 16 topics, each answered with the reasoning,                    |
  |   not just the keyword an interviewer is listening for                                 |
  |                                                                                        |
  +----------------------------------------------------------------------------------------+

  main menu

    [1] quiz one topic
    [2] mock interview       12 mixed questions, hardest topics first
    [3] random ten
    [4] flashcards           no options, recall it yourself
    [5] read a topic         questions and answers, no marking
    [6] review my misses     the ones you got wrong before
    [7] progress
    [8] level filter         currently: all
    [q] quit

  choose >
```
<!-- END SCREEN -->

## Run it

Java 21 or newer, and nothing else. There is no dependency to download, so the fastest way needs no
build at all:

```bash
git clone https://github.com/alxkm/java-concurrency-interview.git
cd java-concurrency-interview

java src/main/java/org/alxkm/interview/Quiz.java     # straight into the quiz
```

Through the wrapper, if you prefer:

```bash
./gradlew quiz --console=plain -q     # --console=plain, or Gradle repaints over the questions
./gradlew stats                       # question counts by topic and level
./gradlew test                        # checks every question parses, and shows how to test threads
```

The quiz remembers what you got right in a `.progress` file next to the repository, so `[6] review my
misses` and `[2] mock interview` keep pointing you at your weak spots.

## What this is, and what it is not

Every answer here tries to be the answer a good candidate gives, not the one a search engine gives.
That means three things:

**The mechanism, not the keyword.** "Use `volatile`" is not an answer. "A volatile write
happens-before every subsequent read of that field, which is the edge that forbids the JIT from
hoisting the read out of the loop" is one, and it survives the follow-up question.

**The trade-off, out loud.** `LongAdder` beats `AtomicLong` at 1256 ops/us against 115 under
contention, and loses on every read because `sum()` walks the cells. Numbers quoted here were measured
in [java-concurrency-patterns](https://github.com/alxkm/java-concurrency-patterns), the sibling
repository with the runnable benchmarks.

**The version it stopped being true in.** Biased locking is gone since JDK 18, so "an uncontended
monitor is free" now dates you. Monitor pinning on virtual threads was mostly fixed in JDK 24, so the
right answer depends on which JVM the team runs.

What it is not: a list of definitions to memorise the night before. If a question here can be answered
by reciting a sentence, it is a bad question and a pull request would be welcome.

## Ten questions that predict the rest

If you can answer these without looking, skim the catalogue. If you cannot, these are the ten to start
with, in this order.

1. What is happens-before, and which edges do you get for free?
2. What does `volatile` guarantee, and what does it not?
3. Why does double-checked locking need `volatile`?
4. What does `ThreadPoolExecutor` do with a submitted task, step by step?
5. Why is `Executors.newFixedThreadPool` dangerous in production?
6. What happens to an exception thrown inside a pooled task?
7. What is the difference between `thenApply` and `thenCompose`, and which thread runs your callback?
8. What is pinning on a virtual thread, and what causes it?
9. Four conditions for a deadlock, and which one you break in practice?
10. Why can a unit test not reliably catch a visibility bug?

## Two weeks, if that is what you have

- **Days 1 to 2**: threads and lifecycle, the memory model. Everything else depends on these, and they
  are where a shaky answer is most obvious.
- **Days 3 to 5**: synchronized and locks, synchronizers, atomics, concurrent collections. The
  daily working set, and the latch against barrier question that comes up every time.
- **Days 6 to 8**: executors, `CompletableFuture`, fork/join. Where production incidents come from, so
  expect scenario questions rather than definitions.
- **Days 9 to 10**: virtual threads and structured concurrency. The topic most likely to separate you
  from other candidates in 2026, and the one most often answered from a headline. Then concurrency in
  real systems, which is where a backend interview spends its scenario questions.
- **Days 11 to 12**: deadlocks and diagnostics, patterns and antipatterns. Bring a story from your own
  experience for each.
- **Days 13 to 14**: the puzzles, on paper, with a timer. Then `[2] mock interview` until two runs in a
  row are clean.

Use `[4] flashcards` for recall and `[1] quiz one topic` for recognition. They are not the same skill,
and an interview asks for the first one.

## Contents

<!-- BEGIN QUESTIONS -->

| # | Topic | Questions | junior / mid / senior |
|---|---|---|---|
| 1 | [Threads and lifecycle](#threads-and-lifecycle) | 11 | 6 / 4 / 1 |
| 2 | [The Java memory model](#the-java-memory-model) | 10 | 2 / 3 / 5 |
| 3 | [synchronized, monitors, wait and notify](#synchronized-monitors-wait-and-notify) | 10 | 2 / 6 / 2 |
| 4 | [Locks, conditions and AQS](#locks-conditions-and-aqs) | 10 | 1 / 4 / 5 |
| 5 | [Synchronizers: latches, barriers, semaphores](#synchronizers-latches-barriers-semaphores) | 10 | 1 / 5 / 4 |
| 6 | [Atomics and compare-and-swap](#atomics-and-compare-and-swap) | 10 | 1 / 3 / 6 |
| 7 | [Concurrent collections](#concurrent-collections) | 12 | 2 / 7 / 3 |
| 8 | [Executors and thread pools](#executors-and-thread-pools) | 12 | 1 / 6 / 5 |
| 9 | [CompletableFuture and async composition](#completablefuture-and-async-composition) | 10 | 2 / 5 / 3 |
| 10 | [Fork/join and parallel streams](#forkjoin-and-parallel-streams) | 11 | 1 / 5 / 5 |
| 11 | [Virtual threads and structured concurrency](#virtual-threads-and-structured-concurrency) | 12 | 1 / 5 / 6 |
| 12 | [Concurrency in real systems](#concurrency-in-real-systems) | 12 | 1 / 5 / 6 |
| 13 | [Deadlock, livelock and diagnostics](#deadlock-livelock-and-diagnostics) | 11 | 1 / 7 / 3 |
| 14 | [Patterns and antipatterns](#patterns-and-antipatterns) | 11 | 2 / 7 / 2 |
| 15 | [Testing concurrent code](#testing-concurrent-code) | 11 | 1 / 5 / 5 |
| 16 | [Live coding puzzles](#live-coding-puzzles) | 10 | 1 / 5 / 4 |

### Threads and lifecycle

The warm-up round. Most interviews open here, and most candidates lose points not because they cannot
name the thread states but because they cannot say what the JVM actually does at each transition.

Runnable: [thread leakage](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/antipatterns/threadleakage), [ignoring InterruptedException](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/antipatterns/ignoringinterruptedexception), [thread states](https://github.com/alxkm/java-concurrency-patterns/blob/master/docs/diagrams/06-thread-states.md).

```
      new       start()      RUNNABLE  <---- yield/scheduler ----+
       |  --------------->  (ready or running on a core)         |
       |                       |     |      |                    |
       |            wait()/park|     |      |synchronized entry  |
       |                       v     v      v                    |
       |                  WAITING  TIMED_WAITING  BLOCKED --------+
       |                       |     |      |
       |    notify/unpark/timeout/lock acquired
       v
   TERMINATED  <--- run() returns or throws
```

<details>
<summary><b>1. What is the difference between a process and a thread, and what does that cost you in Java?</b>  <sub>junior</sub></summary>

A process owns an address space. Threads inside it share the heap, static fields and open file
descriptors, and each gets its own stack, program counter and thread-local storage. That sharing is
exactly why concurrency in Java is hard: two threads reach the same object with no coordination unless
you add some.

The cost side matters in interviews. A platform thread in HotSpot is a 1:1 mapping onto an OS thread,
with a reserved stack (1 MB by default on 64-bit Linux, `-Xss` to change it) and a context switch that
goes through the kernel. That is why a thread per request stops scaling in the thousands, and why
virtual threads exist.

</details>

<details>
<summary><b>2. Why is `Thread.stop()` deprecated, and what replaced it?</b>  <sub>junior</sub></summary>

`Thread.stop()` threw a `ThreadDeath` error into the target wherever it happened to be, and unwound
its stack. Every monitor it held was released, so any object it was halfway through mutating stayed
broken and was now visible to everyone else with no exception anywhere to explain it. There is no way
to write code that is safe against that, which is why it was degraded to throwing
`UnsupportedOperationException` in Java 20.

The replacement is cooperative: `interrupt()` sets a flag, blocking calls throw
`InterruptedException`, and the task decides where it is safe to stop. A task that never checks the
flag and never blocks cannot be cancelled, and that is a property of the task, not a missing JDK
feature.

</details>

<details>
<summary><b>3. What actually happens when you call `interrupt()` on a thread?</b>  <sub>mid</sub></summary>

`interrupt()` sets an internal flag. If the thread is parked in an interruptible call such as
`sleep`, `wait`, `join`, `BlockingQueue.take` or `Lock.lockInterruptibly`, the call throws
`InterruptedException` and the flag is cleared as part of throwing. If the thread is running, nothing
observable happens until it checks `Thread.interrupted()` or `isInterrupted()`, or reaches a blocking
call.

Some blocking is not interruptible: acquiring `synchronized`, `Lock.lock()`, and most plain socket or
file I/O. `InterruptibleChannel` is the exception, closing itself and throwing
`ClosedByInterruptException`.

</details>

<details>
<summary><b>4. Why is swallowing `InterruptedException` a bug, and what is the correct handling?</b>  <sub>mid</sub></summary>

Throwing `InterruptedException` clears the interrupt flag. If you catch it and log, the request to
cancel has now been destroyed: the loop above you keeps going, the pool's `shutdownNow` appears to do
nothing, and the JVM will not exit.

Two correct endings. Propagate it, if your signature allows, and let the caller decide. Or, if you
cannot, restore the flag before returning:

```java
try {
    task = queue.take();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();   // the next blocking call will see it
    return;                               // and stop doing work
}
```

Restoring without also stopping the work is the half-fix that still loses the cancellation.

</details>

<details>
<summary><b>5. What is the difference between a daemon thread and a user thread?</b>  <sub>junior</sub></summary>

The JVM stays alive while at least one non-daemon thread runs. Daemon threads are not counted, so when
the last user thread finishes the JVM exits and daemon threads are stopped where they stand: no
`finally` blocks, no shutdown of resources.

That makes daemons right for background housekeeping (a cache evictor, a metrics flusher) and wrong
for anything that owns state someone will miss, such as a writer with a buffer that has not been
flushed. `setDaemon` must be called before `start()`, otherwise it throws
`IllegalThreadStateException`.

</details>

<details>
<summary><b>6. What is the difference between `Runnable` and `Callable`, and where does `Future` fit?</b>  <sub>junior</sub></summary>

`Runnable.run()` returns void and cannot throw checked exceptions, so a failure inside it has nowhere
to go except the thread's uncaught exception handler. `Callable.call()` returns `V` and may throw,
which is what lets an executor capture the outcome.

`submit` wraps either into a `Future`. Calling `get()` blocks until the task finishes and then either
returns the value or throws `ExecutionException` with the original failure as its cause. That is the
important half: with `execute(Runnable)` an exception is printed and lost, with `submit` it is stored
in the `Future` and is lost only if nobody ever calls `get()`.

</details>

<details>
<summary><b>7. Why must you call `start()` rather than `run()`?</b>  <sub>junior</sub></summary>

`run()` is an ordinary method call. It executes on the calling thread, the new thread is never
created, and everything is sequential. `start()` registers the thread with the OS scheduler, which
then calls `run()` on the new stack.

A second detail interviewers like: a `Thread` object is single use. Calling `start()` twice throws
`IllegalThreadStateException`, because the state machine only ever moves forward towards TERMINATED.

</details>

<details>
<summary><b>8. What is the difference between BLOCKED, WAITING and TIMED_WAITING in a thread dump?</b>  <sub>mid</sub></summary>

BLOCKED means the thread is trying to enter a `synchronized` block whose monitor someone else owns.
The dump names the owner, which makes lock contention and deadlock visible at a glance.

WAITING means the thread called `Object.wait()`, `Thread.join()`, `LockSupport.park()` or an untimed
`Condition.await()`, and will stay there until something signals it. TIMED_WAITING is the same with a
deadline.

The trap: a thread waiting on `ReentrantLock` shows as WAITING at `LockSupport.park`, never as
BLOCKED, because the lock is implemented with `AbstractQueuedSynchronizer` rather than a monitor. If
you grep a dump for BLOCKED to find contention, `java.util.concurrent` locks are invisible to you.

</details>

<details>
<summary><b>9. Does `Thread.yield()` do anything you can rely on?</b>  <sub>mid</sub></summary>

`yield()` suggests to the scheduler that the current thread is willing to give up its slice. The
scheduler may immediately reschedule the same thread, and the specification requires nothing. Code
whose correctness depends on `yield()` is broken code that happens to pass on one machine.

It also does not release locks, which is what separates it from `Object.wait()`. If a spin loop is
genuinely what you want, `Thread.onSpinWait()` (Java 9) is the right tool: it emits a PAUSE
instruction and tells the CPU that this is a spin, without involving the scheduler at all.

</details>

<details>
<summary><b>10. Two threads, one `Thread.sleep(1000)` inside a `synchronized` block. What does the other thread see?</b>  <sub>junior</sub></summary>

`sleep` suspends the thread and keeps every lock it holds. `Object.wait()` is the one that releases
the monitor it was called on, and reacquires it before returning, which is precisely why `wait` must
be called while holding that monitor and `sleep` may be called anywhere.

This is the mechanism behind a whole family of production incidents: an I/O call with a long timeout
inside a `synchronized` block. Nothing is deadlocked, nothing is a race, and the service still stops
responding because a hundred threads are queued behind one slow monitor.

</details>

<details>
<summary><b>11. Can a thread be garbage collected while it is running?</b>  <sub>senior</sub></summary>

Live threads are GC roots. A running thread keeps its stack, every object reachable from it and its
`ThreadLocal` map alive, whether or not anyone still holds a reference to the `Thread` object.

That is the mechanism behind two classic leaks. A thread you started and forgot ("thread leakage")
holds its whole object graph forever. And a `ThreadLocal` on a pooled thread is never collected
between tasks, because the thread outlives them all, which is why a `ThreadLocal` that is not removed
in a `finally` block leaks in a web container.

</details>

[back to top](#contents)

### The Java memory model

The rules every other topic depends on. An answer here that says "volatile makes it thread safe" ends
the interview early; an answer that can name the happens-before edge passes almost any follow-up.

Runnable: [memorymodel](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/memorymodel), [happens-before](https://github.com/alxkm/java-concurrency-patterns/blob/master/docs/diagrams/01-happens-before.md), [reordering](https://github.com/alxkm/java-concurrency-patterns/blob/master/docs/diagrams/03-reordering.md).

```
   thread A                       thread B
   --------                       --------
   x = 42;                        while (!ready) { }
   ready = true;   <-- volatile write     ^
        |                                 |
        +--- happens-before ---> volatile read sees true
                                         |
                                 x is guaranteed to be 42 here,
                                 even though x is a plain field
```

<details>
<summary><b>12. What is happens-before, in one sentence, and why does it matter?</b>  <sub>mid</sub></summary>

Happens-before is not about time, it is about visibility and ordering. If write A happens-before read
B, then B must observe A, and the compiler, JIT and CPU may not move things across that edge in a way
you could detect.

Without such an edge there is no guarantee at all. The read may see the new value, the old value, or
values in an order the source code never wrote. Nothing is "probably fine": it is unspecified, and
unspecified means it will hold on your laptop and break on a 64-core ARM server.

The practical skill is naming the edge. "This is safe because the volatile write to `ready`
happens-before the volatile read of `ready`" is an answer. "It works when I run it" is not.

</details>

<details>
<summary><b>13. Which happens-before edges do you get for free?</b>  <sub>mid</sub></summary>

The list worth memorising, because interviewers ask for it directly:

- **Program order** inside a single thread.
- **Monitor**: unlocking a monitor happens-before every later lock of the same monitor.
- **Volatile**: a write happens-before every later read of that field.
- **Thread start**: everything before `t.start()` happens-before everything in `t`.
- **Thread join**: everything in `t` happens-before `t.join()` returning.
- **Final fields**: correctly constructed final fields are visible without synchronisation.
- **Transitivity**: if A happens-before B and B happens-before C, then A happens-before C.

Everything in `java.util.concurrent` builds on these and documents its own: putting into a
`BlockingQueue` happens-before taking that element out, submitting a task happens-before it running,
and a `CountDownLatch.countDown` happens-before a returning `await`.

</details>

<details>
<summary><b>14. What does `volatile` guarantee, and what does it not?</b>  <sub>junior</sub></summary>

A volatile write is guaranteed to be seen by any subsequent volatile read of the same field, and it
carries with it everything written before it (release/acquire semantics). It also forbids the
compiler from hoisting the read out of a loop, which is what makes a plain flag loop hang forever
while a volatile one exits.

What it does not do is make read-modify-write atomic. `count++` is a read, an add and a write;
another thread can slip between them whatever `volatile` says. For a counter you want `AtomicInteger`
or `LongAdder`; for a flag or a "publish this reference once" field, `volatile` is exactly right.

A runnable demonstration of both halves: [VisibilityExample](https://github.com/alxkm/java-concurrency-patterns/blob/master/src/main/java/org/alxkm/memorymodel/VisibilityExample.java).

</details>

<details>
<summary><b>15. Why does double-checked locking need the field to be volatile?</b>  <sub>senior</sub></summary>

`instance = new Helper()` is three steps: allocate, run the constructor, publish the reference. The
JIT is allowed to publish before the constructor finishes, because within the constructing thread
nothing can tell the difference. Another thread taking the fast path sees a non-null reference and
reads fields that are still at their defaults.

`volatile` forbids that reordering and gives the reader the acquire edge that makes the constructor's
writes visible. This was genuinely broken before Java 5, which is why the "broken DCL" folklore still
circulates.

```java
private static volatile Helper instance;   // volatile is the whole fix

static Helper get() {
    Helper local = instance;               // one volatile read on the fast path
    if (local == null) {
        synchronized (Helper.class) {
            local = instance;
            if (local == null) {
                instance = local = new Helper();
            }
        }
    }
    return local;
}
```

For a static singleton, the holder idiom is simpler and needs no volatile at all: class
initialisation is already synchronised by the JVM.

</details>

<details>
<summary><b>16. What is safe publication, and which ways of achieving it can you name?</b>  <sub>senior</sub></summary>

Publishing an object means making a reference reachable by another thread. Unsafely published, the
reader may see the reference before the fields, and observe a half-built object even though the code
never mutates it after construction.

The safe ways, all of which come down to a happens-before edge between the construction and the read:

- Initialise it from a static initialiser.
- Store the reference into a `volatile` field or an `AtomicReference`.
- Store it into a `final` field of a properly constructed object.
- Guard it with a lock, written and read under the same lock.
- Hand it over through a thread-safe collection, a `BlockingQueue`, or an executor task.

An immutable object with all-final fields is the exception that needs none of this: the final-field
guarantee makes it safe to share by any means, including a data race.

</details>

<details>
<summary><b>17. What does `final` guarantee, and what breaks that guarantee?</b>  <sub>senior</sub></summary>

There is a freeze at the end of the constructor: any thread that obtains a reference to the object
through a normal read is guaranteed to see the final fields with their constructed values, with no
synchronisation of any kind. This is what makes `String` and the immutable collections safe to share
freely.

The guarantee has one condition and one limit. The condition: `this` must not escape during
construction. Registering a listener, starting a thread or passing `this` to a callback from a
constructor hands out a reference before the freeze, and the guarantee is void. The limit: `final`
freezes the reference, not the object behind it. A `final List` field whose list is mutated is not
thread safe in the slightest.

</details>

<details>
<summary><b>18. What is instruction reordering, and can you observe it from ordinary Java?</b>  <sub>senior</sub></summary>

Compilers, the JIT and the CPU all reorder, as long as a single thread cannot tell. Another thread
can tell. In the classic Dekker probe two threads each write one field and read the other, and `0, 0`
is a legal outcome that no interleaving of the source explains.

The interesting part is how hard it is to demonstrate. Lining two threads up requires a latch or a
barrier, and that is a memory barrier, which drains the store buffer producing the effect. Measured
in the sibling repository, the probe found zero reorderings in 20,000 hand-written attempts and
9,914,377 under [jcstress](https://github.com/openjdk/jcstress), 3.74% of samples, in a single run.

The lesson is what makes this a senior question. These bugs do not fail in testing. They fail in
production, rarely, on someone else's hardware.

</details>

<details>
<summary><b>19. Where do memory barriers come into this, and which ones does the JVM emit?</b>  <sub>senior</sub></summary>

The JMM is written in terms of happens-before so that it is portable, but a real CPU implements it
with fences. Conceptually there are four: LoadLoad, LoadStore, StoreStore and StoreLoad. A volatile
write is StoreStore before, StoreLoad after; a volatile read is LoadLoad and LoadStore after.

The costs differ by architecture, which is why "it works on my machine" is so misleading here. x86 is
total-store-order: ordinary loads and stores are already mostly ordered, and only the StoreLoad after
a volatile write needs a real instruction (`lock addl` or `mfence`). ARM and POWER are weakly ordered
and need explicit `dmb`-class instructions at almost every edge, so races that are invisible on x86
surface immediately there.

</details>

<details>
<summary><b>20. Is `long` and `double` assignment atomic in Java?</b>  <sub>mid</sub></summary>

The JLS explicitly permits a non-volatile 64-bit write to be treated as two 32-bit writes, so a
reader can observe the high half of one value with the low half of another: a value that was never
written by anyone. Declaring the field `volatile` makes the access atomic and the tearing
disappears.

In practice every 64-bit HotSpot build writes a `long` atomically, so this is difficult to reproduce
and often dismissed. It is still the right answer to the question as asked, and it is the honest
reason `AtomicLong` exists as a type rather than as a convenience wrapper.

</details>

<details>
<summary><b>21. Two threads increment a shared `int` a million times each with no synchronisation. What is the final value and why?</b>  <sub>junior</sub></summary>

`i++` compiles to `getfield`, `iadd`, `putfield`. Two threads reading 41, both adding one and both
writing 42 lose an increment. Run it and you will typically get something like 1.3 million, and the
number changes every run.

Interviewers usually follow up with "what is the theoretical minimum?" The answer is 2, or even 1 if
you allow a single thread to finish before the other starts: one thread can read a stale value, be
descheduled for the whole run, and write its own stale result at the end, discarding a million
updates in one store.

Three fixes with different costs: `synchronized` (correct, contended, about 18 ops/us at 8 threads),
`AtomicInteger` (a CAS loop, about 115), `LongAdder` (striped cells, about 1256, at the cost of a
`sum()` that walks them all).

</details>

[back to top](#contents)

### synchronized, monitors, wait and notify

The oldest tool in the language, and still the one most production code uses. The questions here
separate people who have written `synchronized` from people who know what the JVM does with it.

Runnable: [monitor object](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/monitorobject), [guarded suspension](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/guardedsuspension), [excessive synchronization](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/antipatterns/excessivesynchronization).

```
   object header
   +--------------------+--------+
   | mark word          | klass  |
   +--------------------+--------+
          |
          +-- unlocked -> thin (CAS, no OS call) -> inflated (real monitor, OS park)
                                                        |
                          entry set (BLOCKED) ----> [ owner ] ----> wait set (WAITING)
                                   ^                                      |
                                   +--------- notify() moves one back ----+
```

<details>
<summary><b>22. What exactly does `synchronized` lock?</b>  <sub>junior</sub></summary>

`synchronized` is mutual exclusion on one object's monitor, nothing more. Two threads can run the same
synchronized method at the same time if they are on different instances, and two threads can be inside
completely different methods and still block each other if those methods lock the same object.

Which object matters. A `synchronized` instance method and a `synchronized static` method in the same
class do not exclude each other: one holds the instance monitor, the other holds the `Class` monitor.
That mismatch is a real and common bug in code that has both a shared static cache and per-instance
state.

</details>

<details>
<summary><b>23. Why is locking on a `String` literal or a boxed `Integer` a bug?</b>  <sub>mid</sub></summary>

String literals live in the string pool and small `Integer` values in the integer cache, so
`synchronized ("lock")` in your class and the same literal in a library three layers away take the
same monitor. You get contention you cannot see, and deadlocks between modules that know nothing
about each other.

The same objection applies to locking on anything public: the object itself when it is handed to
callers, or a `Class` object. The fix is a dedicated private field:

```java
private final Object lock = new Object();
```

Note `Boolean` too. `synchronized (someBoolean)` locks one of exactly two objects in the whole JVM.

</details>

<details>
<summary><b>24. What is reentrancy, and what would break without it?</b>  <sub>mid</sub></summary>

A monitor records its owner and a hold count. The owning thread entering again just increments the
count, and the monitor is released when the count reaches zero.

Without reentrancy, an ordinary pattern would hang: a synchronized method calling another synchronized
method on the same object, or a synchronized `equals` on a subclass calling `super.equals`. Both
`synchronized` and `ReentrantLock` are reentrant, which is what the name says. `ReadWriteLock` is the
sharp edge here: a read lock cannot be upgraded to a write lock, and trying deadlocks the thread
against itself.

</details>

<details>
<summary><b>25. Why must `wait()` be called inside a loop?</b>  <sub>mid</sub></summary>

Two independent reasons, and the second is the important one. The JLS permits spurious wakeups, so a
`wait()` may return with nothing having happened. Worse, a woken waiter must reacquire the monitor
before returning, and in that window a third thread can take the item that was just made available.
The condition that was true at `notify()` time is false again by the time you resume.

```java
synchronized (lock) {
    while (queue.isEmpty()) {   // while, never if
        lock.wait();
    }
    return queue.remove();
}
```

Rewriting that `while` as an `if` produces a bug that survives every test and fails under load. This
is the guarded suspension pattern, and `Condition.await()` has exactly the same rule.

</details>

<details>
<summary><b>26. When should you prefer `notifyAll()` over `notify()`?</b>  <sub>mid</sub></summary>

`notify()` wakes one arbitrary waiter. In a bounded buffer where producers and consumers wait on the
same monitor, the JVM may wake a producer when a consumer was needed. The producer rechecks its
condition, finds the buffer still full, waits again, and the consumer who could have proceeded was
never told. The system stops with work available: a lost wakeup.

`notify()` is safe only when every waiter waits for the same condition and any one of them can make
progress. Otherwise `notifyAll()`, and pay the thundering herd. The better answer to the follow-up is
that `ReentrantLock` with two `Condition` objects, one per role, gets you single-waiter wakeups back
without the hazard.

</details>

<details>
<summary><b>27. Why does `wait()` throw `IllegalMonitorStateException` sometimes?</b>  <sub>junior</sub></summary>

`wait()` releases the monitor, so it must hold it first. Calling `obj.wait()` outside
`synchronized (obj)` throws immediately, and so does calling `obj.wait()` inside
`synchronized (otherObj)`. The exception is one of the friendlier parts of the API: the alternative
would be a silent race.

Remember that `wait` releases only the monitor it was called on. A thread holding two monitors and
waiting on one keeps the other, which is a fine way to build a deadlock.

</details>

<details>
<summary><b>28. What is the difference between `synchronized` and `ReentrantLock` in practice?</b>  <sub>mid</sub></summary>

`synchronized` is scoped to a block and released by the JVM on any exit path, including an exception.
That is a real safety advantage, and it is why it remains the default.

`ReentrantLock` buys you the things a monitor cannot express: `tryLock()` to back off instead of
blocking, `tryLock(timeout)` to bound the wait, `lockInterruptibly()` to stay cancellable, fairness if
you need it, several `Condition` queues per lock, and the ability to lock in one method and unlock in
another. The price is that forgetting `finally { lock.unlock(); }` leaks the lock forever.

On performance, biased locking used to make uncontended `synchronized` nearly free, but it was
disabled in JDK 15 and removed in 18. Measured under contention at 8 threads: `synchronized` about 18
ops/us, `ReentrantLock` about 67.

</details>

<details>
<summary><b>29. Is an uncontended `synchronized` block expensive?</b>  <sub>senior</sub></summary>

An uncontended monitor is taken with a CAS on the mark word in the object header, no kernel
involvement. Only when a second thread actually contends does the monitor inflate into a heavyweight
one with an OS-level wait.

Two JIT optimisations go further. Lock elision removes the lock when escape analysis proves the object
never leaves the thread, which is why `StringBuffer` in a local variable costs the same as
`StringBuilder`. Lock coarsening merges adjacent synchronized blocks on the same object into one.

The nuance that dates a candidate: biased locking made repeated uncontended locking by the same thread
free, and it is gone as of JDK 18. Advice that assumes it is now wrong.

</details>

<details>
<summary><b>30. Can you `synchronized` on the same object from two methods and still have a race?</b>  <sub>mid</sub></summary>

Locking is a convention, not a property of the data. If nine methods synchronize and one getter does
not, the unsynchronized one can read a torn or stale value and the whole discipline is void. This is
the "forgotten synchronization" antipattern, and it is usually one method added later by someone who
did not notice.

The other half is compound actions. Both calls below are individually synchronized and the sequence is
still a race:

```java
if (!map.containsKey(k)) {   // thread-safe call
    map.put(k, v);           // also thread-safe, and this is still check-then-act
}
```

Atomicity must cover the whole invariant, which is what `putIfAbsent` and `computeIfAbsent` are for.

</details>

<details>
<summary><b>31. What is lock striping, and when does it stop helping?</b>  <sub>senior</sub></summary>

One lock over a whole map serialises every operation. Striping guards partition N with lock N, so
threads touching different partitions proceed in parallel. `ConcurrentHashMap` before Java 8 used 16
segments this way; since Java 8 it locks the individual bin instead, which is striping taken to its
limit.

It stops helping in three situations, and naming them is the senior part of the answer. When access is
skewed and one partition is hot, you are back to a single lock. When an operation needs a global view
(`size()`, resizing, a range query), you must take every stripe, which is more expensive than one lock
would have been. And when stripes share a cache line, false sharing turns independent locks into
contention at the hardware level.

</details>

[back to top](#contents)

### Locks, conditions and AQS

Everything in `java.util.concurrent.locks` is one class in a trench coat: `AbstractQueuedSynchronizer`.
Knowing that turns a dozen separate facts into one mechanism you can reason about.

Runnable: [locks](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/locks), [reentrant lock](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/reentrantlock), [lock contention](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/antipatterns/lockcontention).

```
   AQS: one int state + a FIFO queue of parked threads

   state=0 free          acquire: CAS state 0->1
   state=1 held            fail -> enqueue node -> LockSupport.park()
   state=n reentrant n     release: state-- ; if 0 -> unpark(successor)

   ReentrantLock  : state = hold count
   Semaphore      : state = permits
   CountDownLatch : state = remaining count
   ReadWriteLock  : state = readers in high 16 bits, writers in low 16
```

<details>
<summary><b>32. What is `AbstractQueuedSynchronizer` and which classes are built on it?</b>  <sub>senior</sub></summary>

AQS gives you one `volatile int state`, CAS operations on it, and a FIFO queue of parked threads. A
synchroniser subclasses it and defines what the state means and when acquisition succeeds. Everything
else, the queueing, parking, unparking and cancellation, is inherited.

`ReentrantLock` uses state as a hold count. `Semaphore` uses it as a permit count. `CountDownLatch`
uses it as the remaining count and never lets it go back up. `ReentrantReadWriteLock` packs readers
and writers into two halves of the same int. `FutureTask` and `ThreadPoolExecutor.Worker` use it too.

Note that `synchronized` is not on the list: monitors are implemented in the JVM, not in Java, which is
why a thread waiting on a `ReentrantLock` appears as WAITING at `LockSupport.park` rather than as
BLOCKED.

</details>

<details>
<summary><b>33. What does a fair lock actually do, and what does it cost?</b>  <sub>mid</sub></summary>

An unfair lock lets an arriving thread barge: if the lock is free at the moment it asks, it takes it,
even though others have been queued for a while. That is fast, because the running thread is already
on a CPU with warm caches and no context switch is needed.

A fair lock checks the queue first and always yields to the longest waiter. Every handoff then costs a
park and an unpark. Throughput typically drops by an order of magnitude under contention.

Unfair is the default, and usually right. Choose fairness when starvation is a real risk and hold
times are long enough that the handoff cost does not dominate. Note what fairness does not promise:
the scheduler still decides who runs, and `tryLock()` barges even on a fair lock, by design.

</details>

<details>
<summary><b>34. When is `ReentrantReadWriteLock` a win, and when is it a trap?</b>  <sub>senior</sub></summary>

Read/write locks pay off when reads dominate *and* each critical section is long enough for the
parallelism to be worth the extra bookkeeping. Every acquisition has to update a shared counter, which
is itself a contended write, so for a two-field getter a plain `ReentrantLock` usually wins.

Three traps worth naming. Upgrading a read lock to a write lock is not supported and deadlocks the
thread against itself; you must release and reacquire, and recheck your state. Under a steady stream
of readers a non-fair write lock can be starved. And a reader that blocks on I/O holds the lock and
blocks every writer behind it.

`StampedLock` exists because of this, and immutable snapshots or `CopyOnWriteArrayList` often beat both.

</details>

<details>
<summary><b>35. What is optimistic reading in `StampedLock`?</b>  <sub>senior</sub></summary>

`tryOptimisticRead()` returns a stamp without acquiring anything: no CAS, no write to shared state, so
no cache-line ping-pong between readers. You copy the fields you need, then call `validate(stamp)`. If
a writer took the lock in the meantime, validation fails and you fall back to `readLock()`.

```java
long stamp = lock.tryOptimisticRead();
double currentX = x, currentY = y;      // may be an inconsistent snapshot
if (!lock.validate(stamp)) {            // so it must be validated
    stamp = lock.readLock();
    try { currentX = x; currentY = y; } finally { lock.unlockRead(stamp); }
}
```

The rules that go with it: the values you read before validating may be torn, so do not act on them
until validation passes; `StampedLock` is not reentrant, so recursion deadlocks; and it has no
`Condition` support.

</details>

<details>
<summary><b>36. What does `Condition` give you that `wait`/`notify` does not?</b>  <sub>mid</sub></summary>

A monitor has exactly one wait set, so a bounded buffer with producers and consumers waiting on the
same object has to use `notifyAll()` and wake everyone. A `Lock` can create as many `Condition`
objects as you have reasons to wait:

```java
final Condition notFull  = lock.newCondition();
final Condition notEmpty = lock.newCondition();
```

Now `signal()` on `notEmpty` wakes a consumer and only a consumer. That is a targeted wakeup with no
thundering herd, and it is what `ArrayBlockingQueue` does internally.

Everything else carries over unchanged: `await()` must be in a `while` loop for the same two reasons,
and you must hold the lock to call `await` or `signal`, or you get `IllegalMonitorStateException`.

</details>

<details>
<summary><b>37. How do you implement a timeout on acquiring a lock, and why would you?</b>  <sub>mid</sub></summary>

`synchronized` has no timed acquisition at all: you wait forever or not at all. `tryLock(time, unit)`
returns a boolean, and that boolean is the whole point. It turns a potential deadlock into a failed
operation you can retry, log or fail fast on.

```java
if (!lock.tryLock(500, MILLISECONDS)) {
    throw new TimeoutException("could not acquire " + name);
}
try { ... } finally { lock.unlock(); }
```

The classic use is locking two accounts for a transfer: take the first, `tryLock` the second, and if
it fails release the first, back off a random amount and retry. That breaks the hold-and-wait
condition, so the deadlock cannot form. Note the bug people write here: calling `unlock()` in a
`finally` when `tryLock` returned false throws `IllegalMonitorStateException`.

</details>

<details>
<summary><b>38. What is `LockSupport.park`, and why is it better than `suspend`/`resume`?</b>  <sub>senior</sub></summary>

`park()` and `unpark(thread)` are the primitives AQS blocks on. Each thread has a single permit. An
`unpark` makes the permit available, and a `park` consumes it, blocking only if none is there. Because
the permit is remembered, ordering does not matter, which is exactly the race that made the deprecated
`suspend`/`resume` pair unusable: a `resume` arriving first was lost and the thread suspended forever.

Two details. `park` may return spuriously, so callers loop, the same rule as `wait`. And a parked
thread appears as WAITING in a dump with `LockSupport.park` at the top of the stack, which is what
every `java.util.concurrent` blocking operation looks like.

</details>

<details>
<summary><b>39. What is a spin lock, and when is spinning better than blocking?</b>  <sub>senior</sub></summary>

Parking and unparking a thread costs a few microseconds of kernel time plus the cache damage of being
rescheduled. If the lock is held for tens of nanoseconds, spinning until it frees is cheaper. HotSpot
does adaptive spinning for exactly that reason, and tunes the spin count on how long the lock was held
last time.

Spinning is wrong when hold times are long, when there are more runnable threads than cores (you spin
waiting for a thread that is not even running), or on a single core, where it is a guaranteed waste of
a whole slice. Use `Thread.onSpinWait()` inside the loop: it emits a PAUSE hint that lowers power draw
and helps the sibling hyperthread.

</details>

<details>
<summary><b>40. Can a thread deadlock against itself?</b>  <sub>mid</sub></summary>

`synchronized` and `ReentrantLock` are reentrant, so recursion is fine there. `StampedLock` is not
reentrant at all, and reacquiring any of its locks on the same thread parks that thread forever with no
owner ever coming to release it.

The subtler case is lock upgrade. Holding a read lock and asking for the write lock waits for all
readers to leave, including the caller, which never will. Downgrading is legal and supported: take the
write lock, acquire the read lock while still holding it, then release the write lock.

`Thread.currentThread().join()` is a third way to hang forever, and appears in interviews as a trick
question rather than as real code.

</details>

<details>
<summary><b>41. Why is `lock()` outside `try` and `unlock()` inside `finally` the required shape?</b>  <sub>junior</sub></summary>

The idiom is exact, and both deviations are bugs:

```java
lock.lock();              // outside the try
try {
    ...
} finally {
    lock.unlock();        // always runs
}
```

Put `lock()` inside the `try` and a failure to acquire (an `Error`, or an interrupt with
`lockInterruptibly`) still runs the `finally`, which calls `unlock()` on a lock this thread does not
own: `IllegalMonitorStateException`, masking the original failure. Put `unlock()` outside the
`finally` and any exception in the body leaks the lock permanently, which is a hang rather than a
crash, in production, at 3am.

</details>

[back to top](#contents)

### Synchronizers: latches, barriers, semaphores

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

<details>
<summary><b>42. What is the difference between `CountDownLatch` and `CyclicBarrier`?</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>43. When would you use a latch as a starting gate rather than as a finish line?</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>44. What does a `Semaphore` actually protect?</b>  <sub>junior</sub></summary>

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

</details>

<details>
<summary><b>45. Why is `Semaphore` the right way to limit concurrency with virtual threads?</b>  <sub>senior</sub></summary>

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

</details>

<details>
<summary><b>46. What is a `Phaser` and when does it beat a `CyclicBarrier`?</b>  <sub>senior</sub></summary>

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

</details>

<details>
<summary><b>47. What is an `Exchanger` for?</b>  <sub>senior</sub></summary>

`Exchanger<T>` is a two-party meeting point: each thread calls `exchange(myObject)`, blocks, and
receives what the other one passed. It is a `SynchronousQueue` that goes both ways.

The canonical use is double buffering in a producer/consumer pair. The producer fills a buffer while
the consumer drains another, and when both are done they swap buffers at the exchanger, with no
allocation and no copying.

It is rare in application code, and that is fine to say. What it demonstrates in an interview is that
you know the `java.util.concurrent` catalogue rather than just the three classes everyone names.

</details>

<details>
<summary><b>48. How does `CompletableFuture.allOf` compare to a `CountDownLatch` for "wait for N tasks"?</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>49. Why must a `Semaphore` release live in a `finally`, and what goes wrong if it does not?</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>50. Is `CountDownLatch.await()` a happens-before edge?</b>  <sub>senior</sub></summary>

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

</details>

<details>
<summary><b>51. Which synchroniser would you choose, and how do you decide?</b>  <sub>mid</sub></summary>

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

</details>

[back to top](#contents)

### Atomics and compare-and-swap

Lock-free code is not magic, it is one CPU instruction plus a retry loop. Interviewers use this topic
to find out whether "lock-free" is a word you have read or a mechanism you can draw.

Runnable: [atomics](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/atomics), [false sharing](https://github.com/alxkm/java-concurrency-patterns/blob/master/docs/diagrams/04-false-sharing.md).

```
   CAS(address, expected, newValue) -> boolean      one instruction: lock cmpxchg

   do {                                  thread A: read 7 ---- CAS(7->8) ok
       old = value.get();                thread B: read 7 ---- CAS(7->8) FAILS, retry
       next = f(old);                              reads 8 ---- CAS(8->9) ok
   } while (!value.compareAndSet(old, next));

   no thread blocks, but under heavy contention everyone burns CPU retrying
```

<details>
<summary><b>52. What is compare-and-swap, and how does `AtomicInteger.incrementAndGet` use it?</b>  <sub>mid</sub></summary>

CAS takes an address, the value you expect to find and the value you want to install, and performs the
swap atomically only if the expectation holds. On x86 it is `lock cmpxchg`; on ARM it is a
load-linked / store-conditional pair. It returns whether it succeeded.

`incrementAndGet` reads the current value, computes value plus one, and calls `compareAndSet`. If
another thread got in first, the CAS fails and the whole thing is retried with the new value. No lock
is ever taken and no thread is ever parked, which is why a failing thread does not block anyone else.

The consequence to state in an interview: CAS is lock-free, not wait-free. The system always makes
progress, but a given unlucky thread can retry many times.

</details>

<details>
<summary><b>53. What is the ABA problem, and how do you avoid it?</b>  <sub>senior</sub></summary>

CAS compares values, not history. A thread reads A, is descheduled, and meanwhile another thread
changes the value to B and back to A. The first thread's CAS succeeds, because A is what it expected,
even though everything it inferred from seeing A is now wrong.

With an `AtomicInteger` counter this is usually harmless. With references it is not: in a lock-free
stack, the node you read may have been popped, reused and pushed again, and your CAS quietly links a
freed node back into the list.

The fix is a version stamp, so the pair (value, counter) never repeats:
`AtomicStampedReference` for an int stamp, `AtomicMarkableReference` for a single boolean. Java's
garbage collector removes the worst form of this, which is why ABA bites C++ far harder.

</details>

<details>
<summary><b>54. Why is `LongAdder` faster than `AtomicLong`, and when is it not?</b>  <sub>senior</sub></summary>

Under contention `AtomicLong` is limited by the cache line holding its value: every CAS invalidates
that line on every other core, so throughput falls as threads are added. `LongAdder` keeps a base
value plus an array of cells, one per contending thread, each padded onto its own cache line. Threads
update different cells and never invalidate each other.

Measured in the sibling repository at 8 threads: `AtomicLong` 115 ops/us, `LongAdder` 1256. Single
threaded they are about equal, 204 against 210, so the striping costs nothing when there is nobody to
contend with.

The trade-off is reading. `sum()` walks every cell and is not atomic with respect to concurrent
updates, so it is an estimate under load, and it gets slower as the cell array grows. A value read as
often as it is written belongs in an `AtomicLong`; a metric counter written constantly and read once a
minute belongs in a `LongAdder`.

</details>

<details>
<summary><b>55. What is false sharing, and how do you fix it?</b>  <sub>senior</sub></summary>

Caches move data in lines, typically 64 bytes. If two threads write two different fields that happen
to sit on the same line, the hardware coherence protocol bounces that line between cores as though
they were sharing a variable. The program is correct and roughly 2.8x slower, measured.

The fix is padding so the hot fields land on separate lines: `@Contended` (with
`-XX:-RestrictContended`, since the annotation is internal), or manual padding fields. This is what
`LongAdder`'s cells and `ForkJoinPool`'s queues do.

The diagnosis matters as much as the fix: it shows up as scaling that gets worse with more threads,
with no lock contention anywhere in the profile.

</details>

<details>
<summary><b>56. What do `getAndUpdate`, `updateAndGet` and `accumulateAndGet` do, and what is the catch?</b>  <sub>mid</sub></summary>

These methods let you apply an arbitrary function atomically:

```java
counter.updateAndGet(n -> Math.min(n + 1, max));
```

The implementation reads, applies the function, and CASes. On failure it re-reads and applies the
function again. That is the catch: your lambda may be invoked several times for one logical update, so
it must be side-effect free. Logging, incrementing a second counter or sending a message inside it will
happen more than once, silently, only under contention.

</details>

<details>
<summary><b>57. How would you write a thread-safe non-blocking counter without atomics?</b>  <sub>senior</sub></summary>

The atomic classes are thin wrappers. Since Java 9 the supported way to do it yourself is `VarHandle`:

```java
private volatile long value;
private static final VarHandle VALUE;
static {
    VALUE = MethodHandles.lookup().findVarHandle(Counter.class, "value", long.class);
}

void increment() {
    long old;
    do {
        old = value;
    } while (!VALUE.compareAndSet(this, old, old + 1));
}
```

`VarHandle` also exposes the weaker modes that make this interesting at the senior level:
`getPlain`, `getOpaque`, `getAcquire`/`setRelease` and the full volatile mode. Release/acquire is
enough for most publication and is cheaper than a full fence on weakly ordered hardware. In real code,
use `AtomicLong` unless you have measured a reason not to.

</details>

<details>
<summary><b>58. What is the difference between `compareAndSet` and `weakCompareAndSet`?</b>  <sub>senior</sub></summary>

`weakCompareAndSet` is allowed to return false even when the value matched. On ARM and POWER, where
CAS is a load-linked / store-conditional pair, the store can fail for unrelated reasons such as an
intervening interrupt, and forcing a retry in hardware costs more than letting the caller retry.

It is only correct inside a loop that would retry anyway. Using it for a one-shot "set this flag if it
is still null" is a bug, because a spurious failure is indistinguishable from a real one. On x86 the
two compile to the same instruction, which is why testing on x86 proves nothing here.

</details>

<details>
<summary><b>59. Is `AtomicReference` enough to make the object it points at thread safe?</b>  <sub>mid</sub></summary>

`AtomicReference` guarantees exactly one thing: reads and writes of the reference are atomic and
visible. Mutating the object it points to is entirely unguarded.

That is why the idiomatic use pairs it with an immutable value and a CAS loop, which gives you an
atomic multi-field update without a lock:

```java
record State(int count, long lastSeen) { }

void touch() {
    State old;
    State next;
    do {
        old = state.get();
        next = new State(old.count() + 1, System.nanoTime());
    } while (!state.compareAndSet(old, next));
}
```

Both fields change together or not at all, and readers always see a consistent pair.

</details>

<details>
<summary><b>60. What does "lock-free" mean, and how does it differ from "wait-free"?</b>  <sub>senior</sub></summary>

Obstruction-free, lock-free and wait-free are three increasingly strong guarantees. Lock-free means at
least one thread always makes progress, so the system cannot stall even if a thread is descheduled
mid-operation. Individual threads can still starve, retrying forever while others succeed.

Wait-free adds a bound per thread, which is much harder and usually slower in the common case.
`AtomicInteger.get` and `getAndIncrement` (implemented with `getAndAddInt`'s hardware fetch-and-add on
x86) are wait-free; a hand-written CAS loop is only lock-free.

The practical point: absence of locks does not imply absence of starvation, and a lock-free structure
under heavy contention can be slower than a plain lock, because every failed CAS is wasted work plus a
cache-line invalidation.

</details>

<details>
<summary><b>61. Can you replace `volatile boolean running` with `AtomicBoolean`, and should you?</b>  <sub>junior</sub></summary>

For a stop flag that one thread sets and others read, `volatile boolean` is exactly right: one field,
no allocation, and the visibility guarantee you need.

`AtomicBoolean` is for when the transition itself has to be atomic, which is the "do this exactly once"
pattern:

```java
if (shutdown.compareAndSet(false, true)) {
    // exactly one thread gets here, no matter how many call close()
    closeResources();
}
```

Reach for it when the answer to "what if two threads do this at once" is "one of them must win", and
for a flag otherwise.

</details>

[back to top](#contents)

### Concurrent collections

Choosing the wrong collection is the most common real-world concurrency bug, because it does not
crash. It just loses data occasionally. Interviewers ask this topic to see whether you know the
difference between "thread safe" and "correct for my access pattern".

Runnable: [collections](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/collections), [queues](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/queue), [ConcurrentHashMap](https://github.com/alxkm/java-concurrency-patterns/blob/master/docs/diagrams/09-concurrenthashmap.md).

```
   need a map?           shared list?              queue between threads?
        |                     |                          |
   ConcurrentHashMap     mostly reads?            need backpressure?
        |                  /       \                 /        \
   compound update?   CopyOnWrite  synchronized   yes: ArrayBlockingQueue (bounded)
        |                List        List          no: ConcurrentLinkedQueue
   compute/merge
```

<details>
<summary><b>62. Why is `Hashtable` or `Collections.synchronizedMap` worse than `ConcurrentHashMap`?</b>  <sub>junior</sub></summary>

A synchronized wrapper takes one lock around every method, so all threads serialise even when touching
unrelated keys. `ConcurrentHashMap` locks a single bin, so writers to different keys proceed in
parallel and readers usually take no lock at all.

The second half matters more. Per-method atomicity does not make sequences atomic:

```java
if (!map.containsKey(k)) {  // atomic
    map.put(k, compute());  // atomic, and the pair is still a race
}
```

`ConcurrentHashMap` gives you the atomic compound operations that fix this: `putIfAbsent`,
`computeIfAbsent`, `compute`, `merge`, and the two-argument `remove` and `replace`.

</details>

<details>
<summary><b>63. How does `ConcurrentHashMap` work since Java 8?</b>  <sub>senior</sub></summary>

Java 7 used segments: a fixed array of sub-maps, each with its own lock, giving 16 writers in parallel
by default. Java 8 dropped that. Writes CAS an empty bin directly; if the bin is occupied, the writer
synchronizes on the head node, so the lock granularity is one bucket rather than one sixteenth of the
table.

Two more pieces worth naming. A bin whose chain exceeds 8 entries, in a table of at least 64, becomes a
red-black tree, so a hash collision attack degrades to O(log n) rather than O(n). And resizing is
cooperative: a thread that finds a resize in progress helps transfer bins instead of blocking.

`size()` is not a counter under a lock. It sums a `LongAdder`-style set of striped cells, so it is
accurate only when nobody is writing.

</details>

<details>
<summary><b>64. Why does `ConcurrentHashMap` forbid null keys and values?</b>  <sub>mid</sub></summary>

In a `HashMap`, `get(k) == null` is ambiguous between "absent" and "mapped to null", and you resolve
it with `containsKey`. In a concurrent map that second call is a separate point in time: the mapping
can appear or vanish in between, so the ambiguity cannot be resolved at all. Doug Lea's position is
that allowing null buys nothing and hides bugs.

The practical follow-up is `computeIfAbsent` returning null from the mapping function, which means "do
not store anything", not "store null". And `merge` treats a null result as a removal. Both surprise
people porting code from `HashMap`.

</details>

<details>
<summary><b>65. What is the iteration behaviour of a concurrent collection, and what is `ConcurrentModificationException`?</b>  <sub>mid</sub></summary>

`HashMap` and `ArrayList` iterators are fail-fast: they keep a modification counter and throw
`ConcurrentModificationException` when they notice a change. That is a bug detector, not a thread
safety mechanism, and it is best-effort even single threaded (removing an element via the collection
inside a for-each is the usual trigger).

Concurrent collections instead promise weak consistency. You will see every element that was present
for the whole traversal, you may or may not see concurrent insertions, and nothing throws. The price is
that an iteration is not a snapshot, so counting with it gives an approximation.

`CopyOnWriteArrayList` is the exception: its iterator is a true immutable snapshot of the array at
creation time, so it never sees later changes and does not support `remove`.

</details>

<details>
<summary><b>66. When is `CopyOnWriteArrayList` the right choice?</b>  <sub>mid</sub></summary>

Every mutation allocates a new array and copies the whole thing, under a lock. Writes are O(n) and
produce garbage; reads take no lock at all and never block, because they read a final snapshot
reference.

That makes it excellent for listener and subscriber lists: written at startup, read on every event,
never large. It is a disaster for anything written in a loop or holding thousands of elements, where
each add copies the lot.

The snapshot iterator also has a semantic consequence: a listener added during an event dispatch will
not receive that event, which is usually what you want and occasionally a surprise.

</details>

<details>
<summary><b>67. `ArrayBlockingQueue` or `LinkedBlockingQueue`, and does bounded matter?</b>  <sub>mid</sub></summary>

The two-lock design of `LinkedBlockingQueue` (separate put and take locks) is often quoted as faster,
and measured in the sibling repository it was not: `ArrayBlockingQueue` won both single threaded (23.6
against 11.1 ops/us) and with four producers against four consumers (49.5 against 40.9). It reuses a
ring buffer instead of allocating a node per element.

The choice that actually matters is the bound. Unbounded means a producer that outruns its consumer
fills the heap until the process dies, usually at the worst moment. Bounded means the producer blocks,
the pressure propagates upstream, and the system degrades instead of falling over.

Know the rest of the family too: `SynchronousQueue` has no capacity at all and hands off directly,
`PriorityBlockingQueue` orders by comparator and is unbounded, `DelayQueue` releases elements only when
their delay expires, and `LinkedTransferQueue` lets a producer wait until its element is actually
taken.

</details>

<details>
<summary><b>68. What is `ConcurrentSkipListMap` for, and how does it differ from `ConcurrentHashMap`?</b>  <sub>mid</sub></summary>

When you need ordering, range queries, `firstKey`, `headMap` or `ceilingEntry` from several threads,
there is no concurrent `TreeMap`. `ConcurrentSkipListMap` fills that gap with a skip list, which is a
probabilistic balanced structure that can be updated with CAS rather than the rotations a red-black
tree would need under a lock.

The trade-off is the usual one: O(log n) instead of O(1) and worse constants, so do not use it as a
general-purpose map. Its `size()` is also O(n), because there is no counter to read.

</details>

<details>
<summary><b>69. How would you build a thread-safe cache with `computeIfAbsent`, and what is the trap?</b>  <sub>senior</sub></summary>

`computeIfAbsent` is the right tool for a memoising cache: exactly one thread computes, everyone else
waits and gets the same value, with no double computation and no check-then-act race.

The trap is what runs under the bin lock. If the mapping function updates the same map, you can get an
`IllegalStateException` ("recursive update") or, in the pre-Java-9 versions, a corrupted table. If it
is slow (an I/O call, a remote lookup), every other writer that hashes to the same bin is blocked for
its whole duration, and if two threads compute entries that recursively depend on each other, they
deadlock.

For expensive values the classic alternative is storing a `FutureTask` or `CompletableFuture`: insert
the future atomically, then compute outside the lock.

</details>

<details>
<summary><b>70. What does `Collections.unmodifiableList` guarantee about thread safety?</b>  <sub>mid</sub></summary>

An unmodifiable wrapper only blocks writes through that reference. The underlying list is unchanged,
and if anyone retains a reference and mutates it, readers of the view see those changes with no
synchronisation at all: a data race, and possibly a torn view of the internal array.

`List.copyOf` and `List.of` are different: they build a genuinely immutable list whose fields are
final, which makes it safe to publish by any means, including a race. That is the final-field
guarantee, and it is the reason immutability is the cheapest thread safety there is.

</details>

<details>
<summary><b>71. Is `ConcurrentHashMap.size()` reliable, and what should you use instead?</b>  <sub>mid</sub></summary>

`size()` sums a base value plus an array of striped counter cells, the same design as `LongAdder`. With
no concurrent writers the answer is exact; with writers it is a snapshot that was true at no single
instant. `mappingCount()` is the same thing returning a `long`, and is the preferred method because a
concurrent map can exceed `Integer.MAX_VALUE` entries.

`isEmpty()` has the same caveat, and using it to decide whether to shut something down is a classic
race. If you need an authoritative count, count it yourself with a `LongAdder` updated alongside the
map.

</details>

<details>
<summary><b>72. When is a plain `HashMap` behind your own lock better than `ConcurrentHashMap`?</b>  <sub>senior</sub></summary>

`ConcurrentHashMap` gives per-operation atomicity plus a handful of compound operations. If your
invariant spans two maps, or a map and a counter, or requires iterating and then updating based on what
you saw, no amount of concurrent collection will help: the state between your calls is unprotected.

In that case a `HashMap` guarded by a `ReentrantLock` you hold across the whole operation is both
simpler and more correct, and the honest answer in an interview. The follow-up is usually about scope:
keep the lock as narrow as the invariant, and never call unknown code (a listener, a callback) while
holding it.

</details>

<details>
<summary><b>73. Many threads count words into one map. How do you make the counting correct?</b>  <sub>junior</sub></summary>

Every call on a `ConcurrentHashMap` is thread safe, but two calls in a row are not one operation.
With `get` then `put`, two threads read the same count, both add one, and one increment is lost. The
map is fine; the read-modify-write around it is the race. A synchronized wrapper has exactly the same
hole, because it locks each call, not the pair.

The fix is to hand the whole update to the map, which runs it atomically for that key:

```java
ConcurrentHashMap<String, Long> counts = new ConcurrentHashMap<>();
counts.merge(word, 1L, Long::sum);
```

`compute` and `computeIfAbsent` give the same per-key guarantee. When one key is very hot, the
threads queue on that key's bin, and the usual next step is a `LongAdder` per key:
`counts.computeIfAbsent(word, k -> new LongAdder()).increment()`. The map lookup is then read-only for
existing words, and the increment spreads over cells.

</details>

[back to top](#contents)

### Executors and thread pools

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

<details>
<summary><b>74. Walk through what `ThreadPoolExecutor` does with a submitted task.</b>  <sub>mid</sub></summary>

The order is the part people get backwards, and it explains most misconfigured pools:

1. Fewer than `corePoolSize` threads: start a new thread, even if others are idle.
2. Otherwise offer to the queue. If it accepts, done.
3. Only if the queue refuses (a bounded queue that is full) start threads up to `maximumPoolSize`.
4. If that fails too, hand the task to the `RejectedExecutionHandler`.

The consequence: with an unbounded queue, step 3 never happens, so `maximumPoolSize` is dead
configuration and the pool never grows past core. People set a maximum of 200, watch the pool stay at
10, and conclude the setting is broken.

</details>

<details>
<summary><b>75. Why is `Executors.newFixedThreadPool` considered dangerous in production?</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>76. How do you size a thread pool?</b>  <sub>mid</sub></summary>

For CPU-bound tasks, more threads than cores only adds context switches: `availableProcessors()`, or
one less if a thread is needed elsewhere. For blocking tasks the threads are mostly asleep, so the
useful count scales with how much of the time is spent waiting: `N = cores * utilisation * (1 + W/S)`.
A task that waits 90 ms per 10 ms of compute wants roughly ten threads per core.

Say the rest of it, because that is what distinguishes an engineer from a formula: the number is a
starting point to be measured, separate pools for separate workloads so a slow dependency cannot
starve fast ones, and `availableProcessors()` respects cgroup limits in a container only on a
reasonably modern JVM, so check what the pod actually reports.

Virtual threads change this: for blocking work the answer becomes "do not pool at all".

</details>

<details>
<summary><b>77. What is the difference between `shutdown()` and `shutdownNow()`, and how do you shut down properly?</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>78. What happens to an exception thrown inside a pooled task?</b>  <sub>mid</sub></summary>

This asymmetry swallows more bugs than any other part of the API. `execute(Runnable)` lets the
exception propagate: the thread's `UncaughtExceptionHandler` runs (by default printing to stderr) and
`ThreadPoolExecutor` quietly replaces the dead worker.

`submit` wraps the task in a `FutureTask`, which catches everything and stores it. No log line, no
handler, nothing, until someone calls `get()` and receives an `ExecutionException`. A fire-and-forget
`submit` whose `Future` is discarded loses failures completely.

Defences: wrap task bodies in try/catch, override `afterExecute` to inspect both paths, or use
`CompletableFuture` with `whenComplete`. For scheduled tasks it is worse, see the next question.

</details>

<details>
<summary><b>79. What happens when a `ScheduledExecutorService` task throws?</b>  <sub>senior</sub></summary>

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

</details>

<details>
<summary><b>80. Which rejection policy would you choose and why?</b>  <sub>senior</sub></summary>

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

</details>

<details>
<summary><b>81. What is `invokeAll` versus `invokeAny`, and where does `CompletionService` fit?</b>  <sub>mid</sub></summary>

`invokeAll` submits a batch and blocks until every task is done (or the timeout expires), returning
futures in the order of the input, all of them already complete. `invokeAny` is the racing variant: the
first task to return normally wins, its value is returned, and the others are cancelled.

Neither helps when you want to process results in completion order rather than submission order, and
writing that with `Future.get()` in a loop wastes time on a slow first element.
`ExecutorCompletionService` solves it: it pushes each finished task onto a queue you `take()` from, so
you always handle whatever is ready.

In modern code `CompletableFuture.allOf` / `anyOf` covers the same ground non-blockingly, and
`StructuredTaskScope` covers it with cancellation built in.

</details>

<details>
<summary><b>82. Why name your threads, and how?</b>  <sub>junior</sub></summary>

Thread names are the primary label in thread dumps, profilers, APM traces and log MDCs. When
production is on fire and the dump shows 200 threads called `pool-2-thread-N`, you cannot tell which
subsystem is stuck. `payment-callback-7` answers the question instantly.

A `ThreadFactory` is also where you centralise the rest: daemon status, an `UncaughtExceptionHandler`
that logs rather than prints, a context classloader, and a per-pool counter. Any small library or a
handful of lines will do, and it costs nothing at runtime.

</details>

<details>
<summary><b>83. Why must a `ThreadLocal` be removed in a pooled thread?</b>  <sub>senior</sub></summary>

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

</details>

<details>
<summary><b>84. What is work stealing, and which executors use it?</b>  <sub>senior</sub></summary>

In a classic pool, all workers contend on one shared queue, and that queue is the bottleneck. In a
work-stealing pool each worker owns a double-ended queue. It pushes and pops from its own end (LIFO,
which keeps the freshest and most cache-warm task), and an idle worker steals from the opposite end
(FIFO, taking the oldest and typically largest piece of work).

`ForkJoinPool`, the common pool behind parallel streams, `Executors.newWorkStealingPool` and the
virtual thread scheduler all work this way. It shines for recursive divide-and-conquer where tasks
spawn subtasks; it buys little for uniform, independent, blocking tasks, where a plain pool is simpler
and just as fast.

</details>

<details>
<summary><b>85. What breaks when a pooled task submits another task to the same pool and waits for it?</b>  <sub>senior</sub></summary>

With a pool of N threads, if N tasks each block on the result of a subtask they submitted to the same
pool, there is no thread left to run any subtask. Nothing is deadlocked in the lock sense, so a dump
shows no cycle, only every worker parked in `Future.get`. It is invisible under light load and appears
the moment concurrency reaches the pool size.

Three ways out: separate pools for the two layers, so the dependency crosses a boundary; a non-blocking
composition with `CompletableFuture` so nothing waits; or `ForkJoinPool`, whose `join` runs pending
tasks on the current thread (the reason `invokeAll` inside fork/join is safe).

The same hazard appears with nested parallel streams, which all share the common pool by default.

</details>

[back to top](#contents)

### CompletableFuture and async composition

Where interviews go once "I would use a thread pool" is settled. The trap in this topic is that
everything compiles and runs, and only the thread it runs on and the exception you never see are
wrong.

Runnable: [future](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/future).

```
   supplyAsync(A) --thenApply--> map --thenCompose--> B --thenCombine(C)--> result
        |                         |                                           |
   runs on pool            runs on whichever              exceptionally / handle
                           thread completed A             catch the whole chain

   thenApply   : T -> U            (sync transform)
   thenCompose : T -> CF<U>        (flatMap, avoids CF<CF<U>>)
   thenCombine : CF<U> + BiFunction (join two independent futures)
```

<details>
<summary><b>86. What does `CompletableFuture` add over `Future`?</b>  <sub>junior</sub></summary>

`Future` gives you `get()` and `isDone()`, which means the only way to use a result is to block on it
or poll for it. Composing two of them means blocking on the first to start the second.

`CompletableFuture` is a promise you can attach continuations to: `thenApply`, `thenCompose`,
`thenCombine`, `allOf`, `exceptionally`, `handle`. Nothing blocks, and the chain runs when the value
arrives. It is also completable from the outside, via `complete(value)` or
`completeExceptionally(e)`, which is what makes it the natural bridge from a callback-based client
library.

</details>

<details>
<summary><b>87. What is the difference between `thenApply` and `thenCompose`?</b>  <sub>mid</sub></summary>

It is `map` versus `flatMap`. If your function returns a plain value, use `thenApply`. If it returns
another `CompletableFuture`, which is what any further async call gives you, `thenApply` wraps it and
you end up with `CompletableFuture<CompletableFuture<User>>`. `thenCompose` unwraps it for you.

```java
cf.thenApply(id -> loadUser(id));      // CF<CF<User>>  -- almost always a mistake
cf.thenCompose(id -> loadUser(id));    // CF<User>
```

`thenCombine` is the third member: two independent futures plus a `BiFunction` when you want both
results and neither depends on the other.

</details>

<details>
<summary><b>88. Which thread runs your callback if you use `thenApply` rather than `thenApplyAsync`?</b>  <sub>senior</sub></summary>

The non-async variants run on whatever thread happens to make the value available. If the previous
stage is still running, the callback runs on the thread that completes it. If the future was already
complete when you attached the callback, it runs immediately on your thread.

That means a chain of `thenApply` calls can execute on a Netty I/O thread, a client library's callback
thread, or the main thread, depending on timing. Put something slow or blocking there and you have
stalled someone else's event loop. This is the single most common `CompletableFuture` bug in
production.

The `*Async` variants take control back: with no executor argument they use the common
`ForkJoinPool`, which is also wrong for blocking work. Pass your own executor.

</details>

<details>
<summary><b>89. Why should you pass an explicit executor to the `*Async` methods?</b>  <sub>mid</sub></summary>

`supplyAsync(task)` and `thenApplyAsync(fn)` default to `ForkJoinPool.commonPool()`. It has
`availableProcessors() - 1` threads and is shared with every parallel stream in the JVM, including
those inside libraries you did not write. Block on I/O there and you have taken a scarce global
resource out of circulation.

There is a further trap: on a single-core machine, or in a container that reports one CPU, the common
pool has zero threads and runs everything on the caller, so your "async" code is fully synchronous.

Pass an executor sized for the work: a bounded pool for blocking calls, and ideally a separate one per
downstream dependency so a slow service cannot take the others with it.

</details>

<details>
<summary><b>90. How do exceptions propagate through a chain, and what do `exceptionally`, `handle` and `whenComplete` do?</b>  <sub>mid</sub></summary>

A stage that throws completes exceptionally, and every downstream `thenApply` is skipped, carrying the
failure along wrapped in a `CompletionException`.

- `exceptionally(fn)` runs only on failure and substitutes a fallback value.
- `handle((value, error) -> ...)` runs either way and produces a new value, so it can convert a failure
  into a result or the reverse.
- `whenComplete((value, error) -> ...)` runs either way for a side effect and leaves the result alone,
  which makes it the right place for logging and cleanup.

The wrapping catches people out: `get()` throws `ExecutionException`, `join()` throws
`CompletionException`, and the cause is your original exception in both cases. And a chain nobody ever
joins, gets or handles swallows its failure completely.

</details>

<details>
<summary><b>91. What does `allOf` return, and how do you collect the results?</b>  <sub>mid</sub></summary>

`allOf` cannot know the types are the same, so it gives you `CompletableFuture<Void>` as a
completion signal only. The idiom:

```java
List<CompletableFuture<Price>> calls = ids.stream().map(this::fetchAsync).toList();

CompletableFuture<List<Price>> all = CompletableFuture
        .allOf(calls.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> calls.stream().map(CompletableFuture::join).toList());
```

The `join` inside `thenApply` never blocks, because every future is already complete by then.

Two things to mention: `allOf` completes exceptionally if any input fails, but it does not cancel the
others, so add a timeout with `orTimeout` or `completeOnTimeout` per call. And `anyOf` returns
`CompletableFuture<Object>`, which is awkward enough that people usually write their own.

</details>

<details>
<summary><b>92. Does cancelling a `CompletableFuture` stop the work?</b>  <sub>senior</sub></summary>

`CompletableFuture.cancel(mayInterruptIfRunning)` ignores its argument. A `CompletableFuture` is a
value holder with no link back to whoever is computing it, so all cancelling does is complete it with a
`CancellationException` and release the dependent stages. The `supplyAsync` task keeps running to
completion, holding its thread and its connection.

This is a real difference from `FutureTask`, where `cancel(true)` does interrupt the worker. If you
need genuine cancellation you have to arrange it yourself: keep the `Future` returned by the executor,
check a flag inside the task, or use `StructuredTaskScope`, which was designed to make cancellation
propagate.

</details>

<details>
<summary><b>93. How do you add a timeout to a `CompletableFuture`?</b>  <sub>mid</sub></summary>

`get(timeout, unit)` bounds how long *you* wait, but it blocks a thread to do it and leaves the task
running. Since Java 9 there are two non-blocking alternatives:

```java
fetchPrice()
    .completeOnTimeout(Price.UNKNOWN, 200, MILLISECONDS)   // fall back
    .orTimeout(1, SECONDS);                                // or fail hard
```

Both are scheduled on an internal single-threaded timer, so they cost no thread while waiting.
Remember the previous question: the timeout completes the future, it does not stop the underlying
call, so a slow downstream request still occupies its connection until it finishes.

</details>

<details>
<summary><b>94. What is the difference between `join()` and `get()`?</b>  <sub>junior</sub></summary>

Functionally both block until the result is available. The difference is the exceptions: `get()` is
`Future`'s method with checked exceptions, and `join()` is unchecked, which is why it works inside
lambdas and stream pipelines where a checked exception would not compile.

`get()` is also the one with a timeout overload, and the one that responds to interruption. Use
`join()` inside a chain where the value is already known to be present, and `get(timeout)` at the edge
of your system, where you genuinely have to wait.

</details>

<details>
<summary><b>95. When is `CompletableFuture` the wrong tool now that virtual threads exist?</b>  <sub>senior</sub></summary>

Async composition exists because blocking a platform thread is expensive. Virtual threads remove that
premise: a blocked virtual thread unmounts from its carrier and costs almost nothing, so plain
sequential code with plain `try`/`catch`, a readable stack trace and a debugger that works is once
again affordable.

`CompletableFuture` still earns its place for genuine fan-out and fan-in, for bridging callback-based
APIs, and for composing results that arrive from elsewhere. But "we used it so we would not block"
should now be re-examined, and structured concurrency covers the fan-out case with better
cancellation.

The nuanced answer to give: async is about the shape of the dependencies, not about the cost of a
thread, and the cost of a thread was the only reason most codebases adopted it.

</details>

[back to top](#contents)

### Fork/join and parallel streams

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

<details>
<summary><b>96. How does `ForkJoinPool` differ from a fixed thread pool?</b>  <sub>mid</sub></summary>

A fixed pool has one shared queue, and every worker contends on it. A fork/join pool gives each worker
a deque it owns, pushing and popping its own end with no contention, and stealing from the other end of
someone else's when it runs dry.

The other half is `join`. In a plain pool, waiting for a subtask blocks a worker and can starve the
pool. In fork/join, `join` first tries to execute other pending tasks on the current thread, so the
thread stays useful and recursive decomposition does not deadlock.

That design targets CPU-bound divide-and-conquer. For independent blocking tasks it brings nothing
over a plain pool, and its assumptions actively hurt.

</details>

<details>
<summary><b>97. What is the correct shape of a `RecursiveTask`?</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>98. What is the common pool, and why does it cause trouble?</b>  <sub>senior</sub></summary>

`ForkJoinPool.commonPool()` is a static, shared, lazily created pool sized at
`availableProcessors() - 1`. Every parallel stream in the JVM uses it, including ones inside libraries,
and `CompletableFuture`'s async methods use it when you do not pass an executor.

So a single parallel stream doing blocking I/O occupies a scarce global resource, and every other
parallel operation in the process slows down or stalls. On a machine reporting one CPU, the common pool
has zero threads and everything runs on the caller.

You can raise its size with `-Djava.util.concurrent.ForkJoinPool.common.parallelism=N`, which is a
blunt global setting, or run the stream inside your own `ForkJoinPool` by submitting it there, which
works because the stream uses the pool of the thread it runs on.

</details>

<details>
<summary><b>99. When is a parallel stream faster, and when is it slower?</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>100. Why is `forEach` on a parallel stream dangerous, and what should you use?</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>101. What does `Collectors.toConcurrentMap` change, and when is `groupingByConcurrent` actually used?</b>  <sub>senior</sub></summary>

An ordinary `groupingBy` in a parallel stream gives each thread its own map and merges them at the end,
which allocates and copies. The concurrent versions declare the `CONCURRENT` characteristic, so the
framework lets every thread write into a single `ConcurrentHashMap` instead.

That is faster only when merging was actually expensive, and it requires the collector to be
`UNORDERED` too, otherwise the framework falls back to merging to preserve encounter order. Since the
result of a concurrent collector is in unspecified order anyway, using one on a stream where order
matters is a correctness bug rather than an optimisation.

In practice: profile first. The sequential collector plus merge wins more often than people expect.

</details>

<details>
<summary><b>102. What is a `Spliterator` and why does it matter for parallelism?</b>  <sub>senior</sub></summary>

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

</details>

<details>
<summary><b>103. Can you run a parallel stream in your own pool, and should you?</b>  <sub>senior</sub></summary>

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

</details>

<details>
<summary><b>104. Why is `Stream.iterate` a poor source for a parallel stream?</b>  <sub>mid</sub></summary>

`Stream.iterate(0, i -> i + 1)` is inherently sequential: element N cannot be produced without
producing N-1. Its spliterator cannot split usefully, so the framework buffers chunks and you get the
cost of task management with almost none of the parallelism.

`IntStream.range(0, n)` is the fix where it applies: the range is known, so splitting is exact and
free. The general lesson transfers to any source whose next value depends on its last, including
reading a file line by line and iterating a `LinkedList`. If the source cannot be split, parallelism
cannot help, no matter how expensive the per-element work is.

</details>

<details>
<summary><b>105. What happens if a fork/join task blocks on I/O?</b>  <sub>senior</sub></summary>

Fork/join is sized for CPU work, with roughly one thread per core, and its whole model assumes tasks
finish quickly and often spawn subtasks. A worker blocked on a socket does none of that: it holds a
thread, and the work it would have stolen goes undone. Block all of them and the pool is dead while the
CPU sits idle.

`ForkJoinPool.ManagedBlocker` is the official escape. It tells the pool "I am about to block", and the
pool may compensate by starting an extra thread. `ConcurrentHashMap.computeIfAbsent` and the
`CompletableFuture` join paths use it internally.

The better answer in 2026 is that blocking work belongs on virtual threads or a dedicated pool, and
fork/join should keep to what it was built for.

</details>

<details>
<summary><b>106. What does `parallelStream()` change compared with `stream()`?</b>  <sub>junior</sub></summary>

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

</details>

[back to top](#contents)

### Virtual threads and structured concurrency

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

<details>
<summary><b>107. What is a virtual thread, and how is it scheduled?</b>  <sub>mid</sub></summary>

A virtual thread is an ordinary `java.lang.Thread` whose execution is a continuation the JVM can park
and resume. When it blocks, the JVM saves its stack to the heap, unmounts it from its carrier, and the
carrier runs someone else. When the blocking call finishes, the continuation is mounted on any
available carrier and resumed.

The scheduler is a dedicated `ForkJoinPool` in FIFO mode, sized to `availableProcessors()` by default.
Creating one costs roughly a few hundred bytes rather than a megabyte of reserved stack, so millions
are practical.

Everything else stays the same: `Thread.currentThread()`, thread dumps, `try`/`catch`, debuggers and
stack traces all work, which is the entire point.

</details>

<details>
<summary><b>108. What is pinning, and what causes it?</b>  <sub>senior</sub></summary>

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

</details>

<details>
<summary><b>109. Should you pool virtual threads?</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>110. When do virtual threads not help?</b>  <sub>senior</sub></summary>

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

</details>

<details>
<summary><b>111. What problem does structured concurrency solve?</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>112. What are `ShutdownOnFailure` and `ShutdownOnSuccess` for?</b>  <sub>senior</sub></summary>

`ShutdownOnFailure` is the "all must succeed" case, which is most fan-out: three service calls to
assemble one page, where any failure makes the whole page impossible. The first exception cancels the
other forks immediately rather than leaving them running for results nobody will use.

`ShutdownOnSuccess` is the racing case: query three replicas, or a cache and an origin, and take
whichever answers first, cancelling the losers.

In JDK 25 these became joiners passed to `StructuredTaskScope.open(...)`
(`Joiner.allSuccessfulOrThrow()`, `Joiner.anySuccessfulResultOrThrow()`), with custom joiners for
policies such as "wait for a quorum". The concepts are the same; only the spelling changed.

</details>

<details>
<summary><b>113. What is a `ScopedValue`, and why is it preferred to `ThreadLocal` here?</b>  <sub>senior</sub></summary>

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

</details>

<details>
<summary><b>114. How do you debug a system running a million virtual threads?</b>  <sub>senior</sub></summary>

`jstack` and the classic dump show only platform threads, so your carriers appear and the million
virtual threads on top of them do not. The replacement is
`jcmd <pid> Thread.dump_to_file -format=json threads.json`, which walks the virtual threads too and,
for structured concurrency, nests them by scope so the output reads as a tree of who forked whom.

Also worth naming: JFR events `jdk.VirtualThreadStart`, `jdk.VirtualThreadEnd`,
`jdk.VirtualThreadPinned` and `jdk.VirtualThreadSubmitFailed`; and the fact that a million threads
produce a dump measured in hundreds of megabytes, so grep it rather than reading it.

</details>

<details>
<summary><b>115. Do virtual threads change how you use `synchronized` in library code?</b>  <sub>senior</sub></summary>

On JDK 21 through 23, blocking inside `synchronized` pins the carrier thread. A library that wraps its
I/O in a monitor (plenty of older JDBC drivers and HTTP clients did) will pin every virtual thread that
calls it, and the application's throughput collapses in a way that points at the wrong place.

The guidance for that era: audit your own blocking paths, replace monitors around them with
`ReentrantLock`, and check dependencies with `-Djdk.tracePinnedThreads=full`.

JDK 24's JEP 491 made monitors unmount properly, which retires most of this. Give both halves in an
interview: the mechanism, and the version where it stopped mattering, because the answer depends
entirely on which JVM the team is on.

</details>

<details>
<summary><b>116. Is `Thread.sleep` still a bad idea on a virtual thread?</b>  <sub>mid</sub></summary>

`Thread.sleep` is one of the JDK calls made virtual-thread aware. On a virtual thread it parks the
continuation and frees the carrier, so a million sleeping virtual threads consume essentially no OS
resources. That is the standard demonstration: start a million threads that each sleep a second, and
watch it finish in about a second.

The same retrofit covers `java.net` sockets, `NIO` channels, `java.util.concurrent` locks and queues,
`Object.wait` and `Process.waitFor`. What is not covered: `synchronized` on older JVMs, native calls,
and file I/O on some platforms, where the JVM falls back to a platform thread underneath.

Sleeping as a way to coordinate threads remains a bad idea, but for reasons of correctness rather than
cost.

</details>

<details>
<summary><b>117. What does `Executors.newVirtualThreadPerTaskExecutor().close()` do?</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>118. Is a virtual thread faster than a platform thread?</b>  <sub>junior</sub></summary>

A virtual thread runs ordinary bytecode on an ordinary platform thread, its carrier. Nothing about
the code itself gets faster, and one request takes as long as it did before.

What changes is the price of waiting. A platform thread is an OS thread with a stack reserved up
front, so a server can afford a few thousand of them. A virtual thread is a small heap object; when it
blocks on I/O or a lock it unmounts and frees the carrier for another one. A million of them waiting
on sockets is fine.

So the win is **throughput for I/O-bound work**: with many requests each spending most of their time
waiting, you can have one thread per request instead of a pool that runs out. For CPU-bound work
there is no win at all: the carriers are as many as the cores, and the cores were already busy.

A good answer ends with the caveat: more concurrent requests means more pressure on whatever they
wait for. The database pool is still ten connections, so bound it with a `Semaphore`.

</details>

[back to top](#contents)

### Concurrency in real systems

Where core Java meets the frameworks people actually ship. These questions are the ones a backend
interview reaches after the language questions are settled, and the answers are usually about which
thread your code is on and which pool is about to run out.

Runnable: [object pool](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/objectpool), [thread local](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/threadlocal).

```
   a request through a typical Spring service

   HTTP thread pool (Tomcat, 200)
        |
        +-- @Transactional  : the connection is bound to THIS thread
        |        |
        |        +-- @Async  -> another pool, another thread, NO transaction, NO context
        |
        +-- HikariCP (10 connections)  <- the real limit, not the 200
        |
        +-- RestTemplate / WebClient   <- a timeout here, or the 200 threads are gone
```

<details>
<summary><b>119. Which thread runs a Spring `@Async` method, and what does it lose?</b>  <sub>mid</sub></summary>

`@Async` returns immediately and runs the method on the `TaskExecutor` bean. Three things do not travel
with it, and all three are `ThreadLocal`-based: the transaction bound to the calling thread, the
`SecurityContext`, and anything request-scoped, including the MDC values your logs correlate on.

The defaults are the part to warn about, and there are three of them. Plain Spring Framework with no
executor bean falls back to `SimpleAsyncTaskExecutor`, which starts a thread per call and never
refuses one, so a burst is an outage. Spring Boot auto-configures a `ThreadPoolTaskExecutor` instead,
but its defaults are eight core threads and an unbounded queue, which replaces the thread explosion
with a silent backlog. Since Boot 3.2, `spring.threads.virtual.enabled=true` makes it a virtual thread
per task.

So define your own: a `ThreadPoolTaskExecutor` with a bounded queue and a rejection policy you chose,
plus a `TaskDecorator` or `DelegatingSecurityContextAsyncTaskExecutor` if the context has to travel.

Two more traps worth naming: `@Async` on a method called from inside the same bean does nothing,
because the proxy is bypassed, and a `void` `@Async` method throws into the void unless you return
`CompletableFuture` or register an `AsyncUncaughtExceptionHandler`.

</details>

<details>
<summary><b>120. Why does `@Transactional` not work across threads?</b>  <sub>mid</sub></summary>

Spring keeps the active transaction and its connection in `TransactionSynchronizationManager`, which is
`ThreadLocal`-backed. Work handed to another thread, by `@Async`, an executor or a parallel stream,
starts outside that transaction. It will either run with no transaction at all or open its own on a
second connection, which cannot see the first one's uncommitted writes and can deadlock against it at
the database level.

That last part is the war story to tell: a parent transaction holding row locks, a child thread on
another connection waiting for those same rows, and the parent waiting for the child. Neither is a Java
deadlock, so a thread dump shows nothing and the database's lock monitor shows everything.

The rule: one transaction, one thread. Do the concurrent work outside the transaction and commit once,
or give each task its own short transaction and make the whole thing idempotent.

</details>

<details>
<summary><b>121. Your service has 200 HTTP threads and a pool of 10 database connections. Where is the bottleneck?</b>  <sub>senior</sub></summary>

The scarcest resource sets the throughput. With 10 connections, at most 10 requests are doing database
work at any moment; the other 190 threads are either idle or queued inside the pool's borrow call,
holding memory and a request each. Raising the HTTP pool to 400 doubles the queue and the latency and
changes nothing about throughput.

The right moves, in order: put a timeout on connection acquisition so a saturated pool sheds load
rather than piling up, size the connection pool from the database's capacity and the query time (a
small number, often smaller than people expect), and make the HTTP pool a similar order of magnitude so
backpressure reaches the client instead of being absorbed into a queue.

This is also the answer to "will virtual threads fix it". They will not: a million virtual threads
still queue on 10 connections. What changes is that the queue is cheap and visible, instead of being an
invisible limit imposed by a thread pool.

</details>

<details>
<summary><b>122. Is a Spring singleton bean thread safe?</b>  <sub>junior</sub></summary>

A singleton bean is one instance for the whole container, called by every request thread at once.
Spring adds no synchronisation. Injected collaborators and configuration read at startup are fine
because they are effectively immutable; a mutable field is a data race shared by every user of the
application.

The failure is worse than a lost update when the field holds per-request data: a `currentUser` field,
or a non-thread-safe helper such as `SimpleDateFormat`, and now two requests see each other's data.
That is a security incident, not a bug report.

Keep state on the stack, in the request, or in a properly synchronised structure. `@Scope("request")`
and `ThreadLocal` both work and both need care: the scope proxy has a cost, and the thread local must
be cleared, because the container's thread is pooled and the next request inherits whatever was left.

</details>

<details>
<summary><b>123. What is the servlet thread model, and what does async servlet processing change?</b>  <sub>senior</sub></summary>

Classic servlet processing binds a container thread to a request from the first byte to the last. A
request that spends 500 ms waiting on a downstream service occupies a thread for 500 ms, so the thread
pool size caps concurrency at pool size divided by latency.

`AsyncContext.startAsync` (and returning `DeferredResult`, `Callable` or a reactive type from a Spring
controller) releases the container thread and lets another thread complete the response later. The
thread is freed; the socket and the request state are not, so memory and connections still bound you.

The catch is the one from the rest of this repository: everything `ThreadLocal` is now on the wrong
thread, including the security context, the MDC and any open transaction. Virtual threads are the other
answer to the same problem, and the reason Spring Boot 3.2's
`spring.threads.virtual.enabled=true` exists: keep the simple blocking model and make the thread cheap
instead of removing it.

</details>

<details>
<summary><b>124. What does a reactive framework buy you over threads, and what does it cost?</b>  <sub>senior</sub></summary>

Reactor, RxJava and the rest run your pipeline on a small event loop, typically one thread per core.
Because no thread ever blocks, tens of thousands of in-flight requests cost almost nothing, and
backpressure is part of the protocol rather than something you bolt on.

The costs are real and worth stating plainly: stack traces that do not describe your control flow,
debuggers that cannot step through it, `ThreadLocal` that does not work (hence the `Context`), and the
absolute rule that no blocking call may appear anywhere on the loop. One JDBC driver in a reactive
chain takes down throughput for everything sharing that loop, which is why `BlockHound` exists.

The 2026 answer to "would you start a new service reactive": usually no, if the reason was only thread
cost, because virtual threads deliver most of that with ordinary code. Yes, if you want streaming
semantics, backpressure across a pipeline, or the operator vocabulary for composing event streams.

</details>

<details>
<summary><b>125. How do you propagate request context (trace id, user) across threads?</b>  <sub>mid</sub></summary>

Context lives in `ThreadLocal`s (MDC, `SecurityContextHolder`, tracing spans), and a `ThreadLocal` does
not follow work to another thread. Submitting to an executor loses all of it, which is why async logs
have no trace id.

The mechanisms, in increasing order of tidiness: capture the values before the handoff and restore them
inside the task, usually via a decorator that wraps every `Runnable`; use the framework's own bridge
(`TaskDecorator`, `ContextSnapshot` from Micrometer Context Propagation, Reactor's `Context`); or, on
Java 21 and newer, use `ScopedValue`, which is inherited by structured concurrency forks by design.

Whatever you use, the matching `finally` that clears the value belongs there too. Pooled threads keep
whatever you leave behind, and leaked context is worse than missing context: the next request logs
someone else's user id.

</details>

<details>
<summary><b>126. `HttpClient`, `RestTemplate` or `WebClient`: what concurrency questions do you ask before choosing?</b>  <sub>mid</sub></summary>

All three are thread safe and meant to be shared. Creating one per request is the common mistake: it
allocates a fresh connection pool, discards keep-alive, and in the worst case leaks threads, which
looks like a slow memory leak and a rising thread count.

The two numbers that actually decide behaviour under load are the client's own connection pool size,
which caps concurrency independently of your thread pool, and the timeouts. A missing read timeout is
the single most common cause of a service that hangs with no CPU load: every worker ends up waiting
forever on a dependency that will never answer.

Add a circuit breaker or a bulkhead per dependency if one slow service must not consume the capacity
the others need. That bulkhead is usually a `Semaphore`, which is the same primitive from two topics
back, applied where it belongs.

</details>

<details>
<summary><b>127. What breaks when you use a parallel stream inside a web request?</b>  <sub>senior</sub></summary>

A parallel stream uses `ForkJoinPool.commonPool()`, which has `availableProcessors() - 1` threads for
the entire JVM. Under one request it looks fast. Under a hundred concurrent requests, they all compete
for those few threads, and the latency you measured in isolation disappears.

Put a blocking call in the stream and it is worse: the common pool is now held by I/O waits, and every
other parallel operation in the process, including ones inside libraries, stalls behind it. Add the
transaction and context problems from the earlier questions, since the work is now on pool threads.

When the collection is large and the work is genuinely CPU-bound, run it in an executor you own and
size deliberately. When it is not, a sequential stream inside the request thread is both faster and
easier to reason about.

</details>

<details>
<summary><b>128. How does a connection pool interact with your thread pool, and what should you monitor?</b>  <sub>senior</sub></summary>

Every borrow from a connection pool is a blocking wait inside a worker thread. That makes the two pools
a two-stage queue: requests queue for a thread, then threads queue for a connection. Sizing one without
the other produces either idle threads or a hidden backlog.

What to monitor, because this is where the answer becomes operational: active and idle connections,
connection acquisition time (the leading indicator, it rises long before errors), thread pool queue
depth, rejected task count, and the 99th percentile of both. HikariCP exposes all of it, and a
`leakDetectionThreshold` catches the connection someone forgot to close in a `finally`.

Two failure modes to name. A connection held across a remote call multiplies its hold time by the
slowest dependency. And a thread that borrows two connections at once, typically a nested transaction,
can deadlock the pool entirely when enough threads hold one and wait for another.

</details>

<details>
<summary><b>129. Why can one slow `@Scheduled` job delay every other scheduled job in a Spring app?</b>  <sub>mid</sub></summary>

Without configuration, Spring Framework schedules on a single-threaded executor, and Spring Boot's
auto-configured `ThreadPoolTaskScheduler` has `spring.task.scheduling.pool.size=1`. Every
`@Scheduled` method in the application shares that one thread. A report that takes ten minutes means
the cache refresh due every minute runs ten minutes late, and nothing in the logs says so.

The fixes, depending on what the jobs are:

- Raise `spring.task.scheduling.pool.size`, or define your own `TaskScheduler` bean.
- Keep the scheduled method short and hand the heavy work to a dedicated executor.
- On Boot 3.2 and later, `spring.threads.virtual.enabled=true` switches scheduling to a virtual
  thread per run.

Two related facts come up in the follow-up. A single job never overlaps itself: with `fixedRate`, a
run that overruns makes the next one start late, not concurrently. And an exception is logged and the
schedule continues, unlike a raw `ScheduledExecutorService`, where one exception silently cancels
every future run. Finally, with several instances of the service, each one runs the job; making it
run once per cluster needs a lock outside the JVM, such as ShedLock.

</details>

<details>
<summary><b>130. `KafkaConsumer` is not thread safe. How do you process its records in parallel?</b>  <sub>senior</sub></summary>

A `KafkaConsumer` refuses to be used from two threads at once and throws
`ConcurrentModificationException` when it is. The only method safe to call from another thread is
`wakeup()`, which exists to break a blocked `poll()` during shutdown. So there are two shapes:

**One consumer per thread.** Each consumer in the group owns some partitions and processes them in
order. It is simple and keeps per-partition ordering, but parallelism is capped by the number of
partitions: the eleventh consumer on a ten-partition topic sits idle. Spring Kafka's `concurrency`
setting is exactly this.

**One poller, many workers.** A single thread polls and hands records to a pool. Parallelism is no
longer tied to partitions, but three things become your job:

- **Commits.** Commit an offset only when everything before it in that partition has finished, or a
  crash skips records. Auto-commit here commits work that never ran.
- **Liveness.** Keep calling `poll()`; if the gap exceeds `max.poll.interval.ms`, the group decides
  the consumer is dead and rebalances. Use `pause()` and `resume()` for backpressure instead.
- **Ordering.** Records for one key must go to the same worker if order matters, for example by
  hashing the key onto a fixed set of single-threaded executors.

Libraries such as Confluent's parallel consumer package the second shape with per-key ordering, and
knowing that they exist is part of a senior answer.

</details>

[back to top](#contents)

### Deadlock, livelock and diagnostics

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

<details>
<summary><b>131. What four conditions must hold for a deadlock, and which is easiest to break?</b>  <sub>mid</sub></summary>

All four Coffman conditions must hold at once, so removing any one prevents deadlock. In Java you
cannot remove mutual exclusion (that is the point of a lock) and you cannot preempt a monitor.

That leaves two. Break hold-and-wait with `tryLock` and a timeout: acquire what you can, release
everything and retry if you cannot. Break circular wait with a global lock ordering: if every thread
takes locks in the same order, no cycle can form. For dynamic objects the order can be derived, for
instance from `System.identityHashCode`, with a tie-breaker lock for the rare collision.

The cheapest defence of all is not holding two locks, which is usually achievable by narrowing the
critical sections.

</details>

<details>
<summary><b>132. Show the classic transfer deadlock and fix it.</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>133. How do you diagnose a deadlock in a running JVM?</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>134. What is a livelock, and how does it differ from a deadlock?</b>  <sub>mid</sub></summary>

The corridor analogy: two people step aside for each other, repeatedly, in the same direction. Nobody
is blocked, everybody is polite, nobody gets through.

In code it usually comes from naive deadlock avoidance: two threads both detect a conflict, both
release their locks, both retry immediately, and both collide again. It shows up as high CPU with no
throughput, which is the opposite signature to a deadlock, where CPU is at zero and threads are
BLOCKED.

The standard fix is randomised exponential backoff, so the retries desynchronise. Ethernet's collision
handling is the same idea. Message-driven systems produce a variant where two actors bounce a message
back and forth forever.

</details>

<details>
<summary><b>135. What is starvation, and what causes it in a JVM?</b>  <sub>mid</sub></summary>

Starvation is progress denied indefinitely to one thread while others proceed. The usual causes: an
unfair lock where arriving threads barge ahead of the queue; long-running tasks in a small pool leaving
no slot for others; a write lock behind a continuous stream of readers; and thread priorities, which
are a hint the OS may ignore entirely and should never be used as a correctness mechanism.

Thread starvation deadlock is the variant worth naming separately: every thread in a pool waits for a
task that can only run on that same pool. Nothing is BLOCKED, no cycle exists, and the pool is simply
dead.

Fair locks and bounded task durations are the fixes; fairness costs throughput, so it is a trade you
make deliberately.

</details>

<details>
<summary><b>136. How do you read a thread dump? What do you look for first?</b>  <sub>senior</sub></summary>

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

</details>

<details>
<summary><b>137. Your service stops responding but CPU is at zero. What is your first hypothesis?</b>  <sub>senior</sub></summary>

Zero CPU with no progress means nobody is running, so look for where they are all parked. The three
usual shapes: a genuine deadlock cycle; a thread pool whose every worker is waiting on a slow or
unresponsive dependency, with the queue growing behind it; or one lock held across an I/O call, with
everyone else BLOCKED behind it.

The dump tells you which within a minute. A missing timeout is the root cause an embarrassing amount of
the time: a socket read with no `SO_TIMEOUT`, a `Future.get()` with no bound, an HTTP client whose
default is infinite.

High CPU with no progress is the opposite diagnosis: a livelock, a spin loop, or a GC death spiral,
which you separate with `jcmd GC.heap_info` or a GC log rather than a thread dump.

</details>

<details>
<summary><b>138. Which JDK tools do you use for concurrency problems?</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>139. Why does a debugger so often make a concurrency bug disappear?</b>  <sub>mid</sub></summary>

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

</details>

<details>
<summary><b>140. A colleague reports a bug that happens once a week in production and never locally. How do you approach it?</b>  <sub>senior</sub></summary>

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

</details>

<details>
<summary><b>141. What is a deadlock, and why does it not resolve itself?</b>  <sub>junior</sub></summary>

The smallest example is two threads taking the same two locks in opposite order:

```java
Object a = new Object(), b = new Object();
new Thread(() -> { synchronized (a) { pause(); synchronized (b) { } } }).start();
new Thread(() -> { synchronized (b) { pause(); synchronized (a) { } } }).start();
```

Each holds one lock and waits for the other. `synchronized` has no timeout and cannot be interrupted,
and the JVM does not break deadlocks. A database picks a victim and rolls it back; the JVM just
leaves both threads blocked, forever, with zero CPU.

That is also how it looks from outside: the service stops answering but is not busy. A thread dump
(`jstack <pid>`) says it directly, with a section titled "Found one Java-level deadlock" that names
the threads and the locks.

The fixes follow from the example: always take locks in one global order, hold as few as possible at
once, or use `ReentrantLock.tryLock` with a timeout so a thread can back off instead of waiting forever.

</details>

[back to top](#contents)

### Patterns and antipatterns

Design judgement, which is what a senior interview is actually testing. The right answer here is
usually the one that removes shared mutable state rather than the one that guards it more cleverly.

Runnable: [patterns](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns), [antipatterns](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/antipatterns).

```
   the ladder, cheapest and safest first

   1. no shared state        thread confinement, a copy per thread
   2. immutable state        final fields, publish freely
   3. safe publication       volatile, final, or a concurrent collection
   4. one lock               narrow, private, documented
   5. several locks          only with a global ordering
   6. lock-free CAS          only with a benchmark that justifies it
```

<details>
<summary><b>142. What does thread confinement mean, and what are the three kinds?</b>  <sub>mid</sub></summary>

Data that only one thread can reach needs no synchronisation at all, which makes confinement the
cheapest correct answer available.

Stack confinement is the strongest form: a local variable that never escapes cannot be shared, and the
compiler enforces it. Ad-hoc confinement is a convention ("only the UI thread touches this"), which is
free and fragile, so it must be documented and ideally asserted. `ThreadLocal` is confinement with a
lifetime, which is useful for a per-thread `SimpleDateFormat` or a request context, and dangerous in a
pool if not removed.

Swing and most UI frameworks are built entirely on ad-hoc confinement, which is why calling a component
from a background thread is a bug even though nothing throws.

</details>

<details>
<summary><b>143. Why is immutability the strongest thread safety guarantee?</b>  <sub>junior</sub></summary>

An immutable object has no writes after construction, so there is nothing for two threads to disagree
about. The final-field guarantee means that any thread obtaining a reference sees the fields fully
constructed, even through a data race, so you can share it freely.

The requirements are exact: all fields final, no setters, `this` never escaping the constructor, and
defensive copies of any mutable component both in and out. A `final List` field is not enough; the list
must be `List.copyOf`'d or wrapped.

Records give you most of this for free, with the caveat that a record holding a mutable array or list
is only as immutable as its components.

</details>

<details>
<summary><b>144. What is the producer/consumer pattern and what does the queue actually provide?</b>  <sub>junior</sub></summary>

Producers and consumers never touch each other's data; the only shared object is the queue, and
`BlockingQueue` handles the synchronisation, the blocking and the memory visibility (a `put`
happens-before the corresponding `take`). That reduces a concurrency problem to a data structure
choice.

The bound is the design decision. Bounded means a fast producer is slowed down by a slow consumer,
which is backpressure, and the system degrades gracefully. Unbounded means the mismatch accumulates in
memory until the process dies.

The poison pill is the idiomatic shutdown: put a sentinel per consumer so each one sees exactly one and
exits, rather than interrupting mid-work. A thread pool is this pattern wearing a nicer API.

</details>

<details>
<summary><b>145. What is the balking pattern, and where would you use it?</b>  <sub>mid</sub></summary>

Balking is the "if you are already doing it, do nothing" pattern: check the state under the lock, and
if the action is not appropriate right now, return at once rather than blocking.

```java
void save() {
    synchronized (this) {
        if (!changed) {
            return;           // balk: nothing to do
        }
        changed = false;
    }
    writeToDisk();            // the slow part, outside the lock
}
```

Autosave, lazy refresh and idempotent start/stop methods are typical. Its opposite is guarded
suspension, which waits for the state instead of giving up. Choosing between them is choosing whether a
caller would rather be delayed or told no.

</details>

<details>
<summary><b>146. What is the double-checked locking antipattern really about?</b>  <sub>mid</sub></summary>

The idiom exists to avoid taking a lock on every read of a lazily initialised field. Without `volatile`
it is broken, because another thread can see a published reference to a partially constructed object.
With `volatile` it is correct.

The better answer is to avoid needing it. For a static singleton, the holder idiom relies on the JVM's
class initialisation lock and needs no volatile, no synchronized and no double check. For an instance
field, `AtomicReference` with `compareAndSet` or `Suppliers.memoize`-style wrapping says what you mean.
And eager initialisation is usually fine: the object is almost always cheaper than the argument about
it.

Interviewers ask this to see if you reach for the clever construct or for the simple one.

</details>

<details>
<summary><b>147. Why is "synchronize everything" a bad strategy?</b>  <sub>mid</sub></summary>

Locking everything converts a concurrent program into a sequential one with extra overhead, and the
scaling curve goes flat or downwards as threads are added.

The correctness half is less obvious and more dangerous. A broad lock is likely to be held while
calling code you do not control: a listener, an overridden method, a callback, a `toString` on a
user-supplied object. That is an alien call with a lock held, and it is how deadlocks form between
components that were each individually correct.

The discipline: hold the lock for the shortest interval that keeps the invariant, never call unknown
code while holding it, prepare data outside and mutate inside, and document which lock guards which
field (`@GuardedBy` is worth the annotation).

</details>

<details>
<summary><b>148. What is the thread-per-task versus task-per-thread distinction, and why did pools exist?</b>  <sub>mid</sub></summary>

`new Thread(task).start()` per request works until it does not: each one reserves a stack, each start
is a syscall, and thousands of them thrash the scheduler. Pools decouple the unit of work (a task) from
the unit of execution (a thread), so you can have a million tasks and eight threads.

The cost of that decoupling is everything this repository has a topic about: queueing, rejection
policies, thread locals that outlive tasks, cancellation that no longer maps to a thread, and stack
traces that stop at the pool boundary.

Virtual threads restore the simple model. `Executors.newVirtualThreadPerTaskExecutor()` is thread per
task, and the reason it is now sensible is that the expensive resource the pool was amortising no
longer costs anything.

</details>

<details>
<summary><b>149. Why should you not start a thread from a constructor?</b>  <sub>senior</sub></summary>

`new Thread(this).start()` inside a constructor publishes `this` before the object is finished. The
new thread may see default values in fields the constructor is about to set, and the final-field
guarantee does not apply because the reference escaped before the freeze.

Subclassing makes it worse: the superclass constructor runs first, so the thread starts while every
subclass field is still zero, and the object it sees may never have existed in a valid state.

The same argument applies to registering a listener, publishing to a static registry or passing `this`
to anything from a constructor. The fix is a factory method or a separate `start()` call, so
construction completes before the object is shared. This is the escape half of safe publication.

</details>

<details>
<summary><b>150. When would you choose an object pool, and when is it the wrong answer?</b>  <sub>senior</sub></summary>

Pooling made sense when allocation was expensive and collectors were slow. On a modern JVM, allocating
a short-lived object costs a pointer bump and dying young is nearly free, so pooling ordinary objects
usually makes things worse: the pool itself is contended shared state, pooled objects are long lived
and get promoted to the old generation, and every borrow risks a leak or a dirty object handed back
with state left over.

It remains right when the resource is scarce or expensive beyond memory: database connections, sockets,
threads, native handles, large direct byte buffers. Note what those have in common: the pool's real job
is limiting concurrency against something external, not saving allocation.

If you do build one, the checklist is validation on borrow, a return in a `finally`, a bounded size, a
timeout on acquisition, and eviction of idle entries.

</details>

<details>
<summary><b>151. What is the two-phase termination pattern?</b>  <sub>mid</sub></summary>

The pattern separates "please stop" from "has stopped". The requester sets a `volatile` flag and
interrupts the worker, so both a busy worker and a blocked one notice. The worker checks the flag at
safe points, catches `InterruptedException` as a stop signal, and then runs its own shutdown: flush
buffers, release resources, notify a latch.

```java
void shutdown() {
    running = false;      // volatile
    worker.interrupt();   // wake it if it is blocked
}
```

Why both: the flag alone leaves a blocked thread asleep, and the interrupt alone can be missed if it
arrives between a check and a blocking call. The termination is complete only when the worker says so,
which is what the `join` or the latch is for.

</details>

<details>
<summary><b>152. How do you make a class document its own thread safety?</b>  <sub>mid</sub></summary>

A class is thread safe, conditionally thread safe, or not, and the reader cannot tell by looking. State
it in one sentence at the top of the javadoc: "Thread safe. All mutable state is guarded by `lock`."
Or: "Not thread safe. Confine instances to one thread."

`@GuardedBy("lock")` on a field names the invariant precisely, is checkable by static analysis tools
(SpotBugs, ErrorProne), and survives the refactoring that a comment would not. For conditionally
thread-safe classes, say which compound sequences the caller must lock, and which lock to use.

This is not paperwork: an undocumented thread safety policy is how correct code becomes incorrect two
maintainers later.

</details>

[back to top](#contents)

### Testing concurrent code

Asked far less often than it should be, and a strong differentiator when you can answer it. Most
candidates describe a test with a `Thread.sleep` in it, which is exactly the thing that does not work.

Runnable: [testing concurrency](https://github.com/alxkm/java-concurrency-patterns/blob/master/docs/diagrams/08-testing-concurrency.md), [jcstress tests](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/jcstress/java/org/alxkm/memorymodel).

```
   what a test can and cannot catch

   lost update / bad invariant   ->  yes, with enough threads and a barrier
   missing happens-before        ->  almost never: the latch that lines threads
                                     up IS a barrier, and it hides the bug
   deadlock                      ->  yes, with a timeout and a dump on failure
   reordering                    ->  jcstress only
```

<details>
<summary><b>153. Why is `Thread.sleep` in a test a bug rather than a delay?</b>  <sub>mid</sub></summary>

A sleep says "this will probably have happened by now". On a loaded CI machine it will not, and the
test fails for reasons unrelated to the code. Lengthen it and every run pays the cost, so a suite of
two hundred such tests takes ten minutes to tell you nothing.

Wait for the condition instead of for the clock: a `CountDownLatch` the code under test counts down, a
`BlockingQueue.poll(timeout)`, a `Future.get(timeout)`, or a small polling helper that checks a
predicate every millisecond up to a deadline. Each fails fast with a clear message and passes as soon
as the work is done.

```java
static void await(Duration timeout, BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (!condition.getAsBoolean()) {
        if (System.nanoTime() > deadline) {
            throw new AssertionError("condition not met within " + timeout);
        }
        Thread.sleep(1);
    }
}
```

</details>

<details>
<summary><b>154. How do you write a test that actually creates contention?</b>  <sub>mid</sub></summary>

Threads started in a loop tend to run one after another, because starting a thread takes longer than
the body of a small test. A `CyclicBarrier` or a `CountDownLatch` used as a starting gate lines them up
so they collide:

```java
int threads = 8;
var start = new CountDownLatch(1);
var done  = new CountDownLatch(threads);
for (int i = 0; i < threads; i++) {
    new Thread(() -> {
        start.await();              // everyone waits here (try/catch omitted)
        for (int n = 0; n < 10_000; n++) counter.increment();
        done.countDown();
    }).start();
}
start.countDown();                  // released together
assertTrue(done.await(10, SECONDS));
assertEquals(threads * 10_000, counter.get());
```

Then say the caveat, because it is the interesting half: the barrier is itself a memory barrier, so
this catches lost updates but hides visibility bugs.

</details>

<details>
<summary><b>155. Why can a unit test not reliably catch a visibility or reordering bug?</b>  <sub>senior</sub></summary>

To observe a reordering you need two threads executing a few instructions at precisely overlapping
moments. Arranging that requires a latch or a barrier, and that construct emits fences and creates
happens-before edges, which is exactly what suppresses the behaviour you were trying to see. The test
destroys what it measures.

Measured in the sibling repository, the textbook Dekker probe found zero reorderings in 20,000
hand-written attempts and zero in 500,000 barrier-synchronised iterations. jcstress found 9,914,377 in
a single run, 3.74% of samples.

The conclusion to state plainly: absence of failure in a concurrency test is not evidence of
correctness. Correctness here comes from reasoning about happens-before, and from tools built for the
job.

</details>

<details>
<summary><b>156. What is jcstress and when do you reach for it?</b>  <sub>senior</sub></summary>

jcstress writes the test for you in a shape ordinary code cannot: two `@Actor` methods run with no
synchronisation between them, the harness repeats them for millions of samples, shuffles JIT decisions
between forks, and records the frequency of every observed result. Outcomes are labelled `ACCEPTABLE`,
`INTERESTING` or `FORBIDDEN`, so the test fails if a guarantee is ever violated.

Use it to answer questions of the form "can this be observed at all": does this idiom need `volatile`,
is this publication safe, can this field be seen at its default. Its output is also the most persuasive
artefact you can put in a code review, because it replaces an argument with a frequency table.

What it is not: a test for your business logic. It is slow, deliberately, and belongs in a separate
source set that CI compiles but does not run on every build.

</details>

<details>
<summary><b>157. How do you test that something is *not* possible, such as a deadlock?</b>  <sub>senior</sub></summary>

A hang is the worst test outcome, because the build sits there until a job timeout kills it with no
information. Make it a failure with evidence instead: `assertTimeoutPreemptively`, a bounded
`future.get(5, SECONDS)`, or a latch `await` whose boolean you assert.

Then add the diagnosis to the failure path:

```java
ThreadMXBean threads = ManagementFactory.getThreadMXBean();
long[] deadlocked = threads.findDeadlockedThreads();
assertNull(deadlocked, () -> Arrays.toString(threads.getThreadInfo(deadlocked, true, true)));
```

That turns "the build hung" into a report naming both threads and both locks. As always, a passing run
proves the deadlock did not occur this time, not that the ordering is correct.

</details>

<details>
<summary><b>158. What makes a concurrency test flaky, and how do you deal with flakiness?</b>  <sub>mid</sub></summary>

The usual causes are all fixable: sleeps standing in for conditions, assertions on how long something
took, shared static state or singletons leaking across tests, ports and temp files that collide, and
pools not shut down so threads from one test run during another.

The cultural part is the real answer. An intermittent failure in a concurrency test is evidence of a
race somewhere, in the test or in the code, and adding a retry annotation discards that evidence. Quarantine
it if it blocks the build, but keep it running and keep the failure output, including a thread dump.

Useful hygiene: make each test create and shut down its own executor, give threads names that identify
the test, and run the suite occasionally with more threads than cores to change the interleavings.

</details>

<details>
<summary><b>159. How would you benchmark two concurrent implementations honestly?</b>  <sub>senior</sub></summary>

A hand-rolled timing loop measures the interpreter, then the C1 compiler, then dead code the JIT
removed because the result was unused. JMH exists to remove those: warmup iterations, blackholes for
results, several forks to shuffle JIT decisions, and a reported error interval.

For concurrency add the parts specific to it: `@Threads` or a thread group per role to set the
contention level, `@State(Scope.Benchmark)` for the shared object, and separate numbers for one thread
and for N, because the interesting result is the shape of the curve rather than a single figure.

Say the honest part too: benchmark results are workload-specific, and two claims in the sibling
repository's README turned out to be wrong once measured, which is why they now carry numbers.

</details>

<details>
<summary><b>160. Can you unit test code that uses the current time or a scheduler?</b>  <sub>mid</sub></summary>

Hard-coded `System.currentTimeMillis()` and a privately constructed `ScheduledExecutorService` are
what make a class untestable. Inject both and the tests become ordinary:

- A `java.time.Clock` you can fix or advance, so expiry and timeout logic is exact.
- An `Executor` you can substitute with `Runnable::run` to make everything synchronous, or with a
  deterministic scheduler that runs queued tasks when you tell it to.

This is a design answer as much as a testing one: the same injection that makes the class testable also
makes it configurable and lets the application own thread lifecycle in one place. If a class creates
its own threads, nobody can control them.

</details>

<details>
<summary><b>161. What static analysis helps with concurrency?</b>  <sub>mid</sub></summary>

The compiler says nothing about thread safety, but analysers catch a real subset. SpotBugs has a
concurrency category: inconsistent synchronisation (a field synchronised on 90% of accesses),
`wait` outside a loop, naked `notify`, a lock not released on every path, locking on a boxed value,
double-checked locking without volatile. ErrorProne enforces `@GuardedBy` properly and flags a family of
misuse patterns at compile time.

They cannot prove correctness and they produce false positives, so they belong as a warning gate rather
than a build-breaker for existing code. Their real value is on new code, where they catch the mistake
before it reaches review.

</details>

<details>
<summary><b>162. What would you add to a code review checklist for concurrent code?</b>  <sub>senior</sub></summary>

A checklist that finds real bugs:

- For each mutable field shared between threads, which lock or happens-before edge covers it, and is it
  documented?
- Is any sequence of individually safe calls a compound action that should be atomic?
- Is any unknown code (a listener, a callback, an overridable method) called while holding a lock?
- Is `InterruptedException` propagated or the flag restored, never swallowed?
- Does every blocking call have a timeout?
- Is every executor shut down, and is every `ThreadLocal` removed in a `finally`?
- Are the collections chosen for the access pattern, not just for being thread safe?
- Do the tests bound their waits, and is the thread safety policy written in the javadoc?

Offering this list unprompted is usually a stronger signal than any single technical answer.

</details>

<details>
<summary><b>163. What is the difference between `assertTimeout` and `assertTimeoutPreemptively` in JUnit 5?</b>  <sub>junior</sub></summary>

`assertTimeout` runs the code on the test's own thread, lets it finish, and only then compares the
time taken with the limit. That is fine for "this should be fast", and useless for "this might
deadlock": a deadlocked call never returns, so the assertion never runs and the build hangs until
CI kills it, with no message.

`assertTimeoutPreemptively` runs the code on a separate thread and stops waiting at the deadline, so
the test fails on time and says why. Two costs come with the other thread:

- The stuck thread is interrupted, and a thread blocked on `synchronized` ignores that, so it stays
  behind for the rest of the run. Harmless for one failing test, worth knowing when many fail.
- Anything bound to the test thread through a `ThreadLocal` is missing there. Spring's test-managed
  transaction and security context are the usual surprises.

The annotation form is `@Timeout`, with `threadMode = SEPARATE_THREAD` for the preemptive behaviour,
and `junit.jupiter.execution.timeout.default` sets a limit for every test, which is a cheap safety
net for a concurrency-heavy suite.

</details>

[back to top](#contents)

### Live coding puzzles

The whiteboard round. Each of these has a canonical solution short enough to write in ten minutes, and
an obvious wrong version the interviewer is hoping you avoid.

Runnable: [odd/even printer](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/oddevenprinter), [producer/consumer](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/producerconsumer), [dining philosophers](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/philosopher).

```
   what they are really checking

   can you name the coordination primitive that fits?
   do you use a while loop around every wait?
   do you handle interruption instead of swallowing it?
   is your shutdown defined, or does the program just stop?
```

<details>
<summary><b>164. Print numbers 1 to 100 with two threads, one printing odd and one even, in order.</b>  <sub>mid</sub></summary>

The shape they want, with the `while` loop and the `notifyAll` in the right places:

```java
class OddEven {
    private final Object lock = new Object();
    private int n = 1;

    void print(boolean odd, int limit) throws InterruptedException {
        while (true) {
            synchronized (lock) {
                while (n <= limit && (n % 2 == 1) != odd) {
                    lock.wait();
                }
                if (n > limit) {
                    lock.notifyAll();      // release the other thread before leaving
                    return;
                }
                System.out.println(Thread.currentThread().getName() + ": " + n++);
                lock.notifyAll();
            }
        }
    }
}
```

Two details separate a pass from a fail: the `while` rather than `if` around `wait`, and the final
`notifyAll` before returning, without which the other thread waits forever at the end.

A `Semaphore` pair is an equally good answer and often shorter: each thread releases the other's
permit after printing.

</details>

<details>
<summary><b>165. Implement a bounded blocking queue with `wait` and `notify`.</b>  <sub>mid</sub></summary>

```java
class BoundedQueue<T> {
    private final Queue<T> items = new ArrayDeque<>();
    private final int capacity;

    BoundedQueue(int capacity) {
        this.capacity = capacity;
    }

    synchronized void put(T item) throws InterruptedException {
        while (items.size() == capacity) {
            wait();
        }
        items.add(item);
        notifyAll();
    }

    synchronized T take() throws InterruptedException {
        while (items.isEmpty()) {
            wait();
        }
        T item = items.remove();
        notifyAll();
        return item;
    }
}
```

Expect the follow-up: why `notifyAll` and not `notify`? Because producers and consumers wait on the
same monitor, so `notify` can wake a producer when only a consumer can proceed, and the wakeup is lost.
The better version uses a `ReentrantLock` with two `Condition`s, `notFull` and `notEmpty`, so each
`signal` reaches a thread that can actually run. That is exactly what `ArrayBlockingQueue` does.

</details>

<details>
<summary><b>166. Make three tasks run concurrently but print their results in a fixed order.</b>  <sub>mid</sub></summary>

Two good answers, and picking the right one depends on whether the printing itself must be ordered or
only the final output.

If you only need ordered output, do not coordinate at all: submit the tasks, collect the futures and
print them in order. The work overlaps, the printing is sequential, and there is no shared state.

If each stage must print before the next begins, chain latches: task N counts down latch N, and task
N+1 awaits it first. `CompletableFuture.thenRun` expresses the same chain more readably. The mistake to
avoid is a shared `volatile int turn` with a spin loop, which burns a core to save a latch.

</details>

<details>
<summary><b>167. Implement a rate limiter that allows N operations per second.</b>  <sub>senior</sub></summary>

The simplest correct version is a `Semaphore` with N permits and a scheduled refill:

```java
Semaphore permits = new Semaphore(n);
scheduler.scheduleAtFixedRate(
        () -> permits.release(n - permits.availablePermits()), 1, 1, SECONDS);

void call() throws InterruptedException {
    permits.acquire();
    doWork();
}
```

Be ready for the critique: this is a fixed window, so 2N calls can land across a window boundary in a
moment. A token bucket with fractional refill (tokens accumulate at n per second up to a burst
capacity) smooths that out, and a leaky bucket removes bursts entirely.

Points for mentioning fairness (`new Semaphore(n, true)` so a caller is not starved), a timeout on
`tryAcquire` so callers are not blocked forever, and that in real systems the limiter belongs where
the resource is, not per JVM.

</details>

<details>
<summary><b>168. Solve the dining philosophers problem without deadlock.</b>  <sub>mid</sub></summary>

Five philosophers, five forks, everyone grabbing left then right: a perfect circular wait. The three
standard fixes, each breaking a different Coffman condition:

1. **Lock ordering**: number the forks and always take the lower-numbered one first. The last
   philosopher then reaches right-then-left, which breaks the cycle. One line of change.
2. **A waiter**: a `Semaphore` with four permits, so at most four philosophers sit down and someone can
   always eat. Breaks hold-and-wait.
3. **tryLock with backoff**: take the first fork, `tryLock` the second, and put the first down if it
   fails, retrying after a random delay. Breaks hold-and-wait too, but needs the randomness or it
   becomes a livelock.

Say which you would ship: ordering, because it is deterministic and has no retry storms.

</details>

<details>
<summary><b>169. Write a thread-safe lazily initialised cache where each key is computed once.</b>  <sub>senior</sub></summary>

`computeIfAbsent` is the one-liner and the right first answer:

```java
V get(K key) {
    return cache.computeIfAbsent(key, this::compute);
}
```

Exactly one thread computes, the others wait, no duplicate work. Then volunteer the caveat, because it
is what the question is for: the function runs while the bin is locked, so a slow or recursive
computation blocks other writers to that bin or throws `IllegalStateException`.

The classic alternative from *Java Concurrency in Practice* keeps the lock short by storing a future:

```java
Future<V> future = cache.get(key);
if (future == null) {
    FutureTask<V> task = new FutureTask<>(() -> compute(key));
    future = cache.putIfAbsent(key, task);
    if (future == null) {
        future = task;
        task.run();          // compute outside any map lock
    }
}
return future.get();
```

Mention cache eviction and the failure case too: a failed computation must be removed, or you have
cached an exception forever.

</details>

<details>
<summary><b>170. Implement a simple `CountDownLatch` yourself.</b>  <sub>senior</sub></summary>

```java
class Latch {
    private int count;

    Latch(int count) {
        this.count = count;
    }

    synchronized void countDown() {
        if (count > 0 && --count == 0) {
            notifyAll();          // once, when it actually reaches zero
        }
    }

    synchronized void await() throws InterruptedException {
        while (count > 0) {
            wait();
        }
    }
}
```

The points being checked: `while` around `wait`, `notifyAll` rather than `notify` (every waiter must be
released, not one), not counting below zero, and the fact that a latch is one-shot, which is what
distinguishes it from a `CyclicBarrier` that resets.

The real one is an AQS subclass using the state as the count, which is worth naming as the follow-up.

</details>

<details>
<summary><b>171. Write a non-blocking stack.</b>  <sub>senior</sub></summary>

```java
class Stack<T> {
    private record Node<T>(T value, Node<T> next) { }
    private final AtomicReference<Node<T>> head = new AtomicReference<>();

    void push(T value) {
        Node<T> oldHead;
        Node<T> newHead;
        do {
            oldHead = head.get();
            newHead = new Node<>(value, oldHead);
        } while (!head.compareAndSet(oldHead, newHead));
    }

    T pop() {
        Node<T> oldHead;
        Node<T> newHead;
        do {
            oldHead = head.get();
            if (oldHead == null) {
                return null;
            }
            newHead = oldHead.next();
        } while (!head.compareAndSet(oldHead, newHead));
        return oldHead.value();
    }
}
```

Then discuss what you would be asked next: this is lock-free but not wait-free; under heavy contention
the retry loop wastes work, so a real implementation backs off; and in a language without a garbage
collector this is the textbook ABA hazard, which is why C++ needs hazard pointers and Java does not.

</details>

<details>
<summary><b>172. Run a task exactly once no matter how many threads call it.</b>  <sub>mid</sub></summary>

```java
private final AtomicBoolean started = new AtomicBoolean();

void start() {
    if (started.compareAndSet(false, true)) {
        doStart();          // exactly one caller reaches here
    }
}
```

The wrong version is a `volatile boolean`: check and set are two operations, so two threads can both
see false. That is the check-then-act race in its smallest possible form, and it is the whole point of
the question.

Worth adding: other callers return immediately without knowing whether initialisation has *finished*.
If they must wait for it, add a `CountDownLatch` the winner counts down and everyone else awaits.

</details>

<details>
<summary><b>173. Two threads, one increments and one prints a shared counter. What could go wrong, and how do you fix it by hand?</b>  <sub>junior</sub></summary>

Two separate bugs live in this three-line program, and naming both is the test.

Visibility: with a plain field, the reader has no happens-before edge and the JIT may hoist the read
out of its loop, so it can print the same value forever. Atomicity: `count++` is read-modify-write, so
with a second incrementer, updates are lost. With exactly one writer the second problem does not arise,
which is why `volatile` alone is a correct fix for *this* program and not for the obvious next version
of it.

The safe answers, in order of preference: `AtomicInteger` (covers both, no lock), a lock around both
the increment and the read (covers both, and extends to more state), or `volatile` (covers visibility
only, and only correct while there is exactly one writer). Saying which of these stops working when a
third thread appears is the answer they want.

</details>

[back to top](#contents)

<!-- END QUESTIONS -->

## Contributing

A question earns its place if a real interviewer asks it and the answer teaches a mechanism. Corrections
are even more welcome than additions, especially where a JDK version has moved under an answer.

Everything lives in [questions/](./questions), one markdown file per topic, in a format that renders on
GitHub and is read directly by the quiz:

```markdown
## The question, worded the way an interviewer asks it
- id: unique-slug
- level: junior | mid | senior
- tags: volatile, visibility

* [ ] a wrong option that someone would plausibly pick
* [x] the right one

The explanation, in markdown, until the next question heading.
```

Then run `./gradlew readme` to regenerate the catalogue in this file, and `./gradlew test` to check the
content parses. See [CONTRIBUTING.md](./CONTRIBUTING.md) for the rest.

## Related

- [java-concurrency-patterns](https://github.com/alxkm/java-concurrency-patterns): the runnable half.
  119 examples, JMH benchmarks and jcstress tests, which is where the numbers quoted here come from.
- [Java Concurrency in Practice](https://jcip.net/), Goetz et al. Twenty years old and still the book.
- [JSR 133 FAQ](https://www.cs.umd.edu/~pugh/java/memoryModel/jsr-133-faq.html), Manson and Goetz. The
  memory model in plain language.
- [jcstress](https://github.com/openjdk/jcstress), the OpenJDK harness for memory model experiments.

## License

MIT. See [LICENSE](./LICENSE).
