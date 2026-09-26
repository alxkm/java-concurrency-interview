# Threads and lifecycle

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

## What is the difference between a process and a thread, and what does that cost you in Java?
- id: process-vs-thread
- level: junior
- tags: basics, memory

* [ ] Threads have separate heaps, processes share one
* [x] Threads of one process share heap and metaspace but each has its own stack and program counter
* [ ] A thread cannot have its own local variables
* [ ] Processes are always faster to create than threads

A process owns an address space. Threads inside it share the heap, static fields and open file
descriptors, and each gets its own stack, program counter and thread-local storage. That sharing is
exactly why concurrency in Java is hard: two threads reach the same object with no coordination unless
you add some.

The cost side matters in interviews. A platform thread in HotSpot is a 1:1 mapping onto an OS thread,
with a reserved stack (1 MB by default on 64-bit Linux, `-Xss` to change it) and a context switch that
goes through the kernel. That is why a thread per request stops scaling in the thousands, and why
virtual threads exist.

## Why is `Thread.stop()` deprecated, and what replaced it?
- id: thread-stop-deprecated
- level: junior
- tags: interruption, lifecycle

* [ ] It was too slow
* [x] It released every held lock at an arbitrary point, leaving shared objects half-updated
* [ ] It only worked on Windows
* [ ] It was replaced by `Thread.destroy()`

`Thread.stop()` threw a `ThreadDeath` error into the target wherever it happened to be, and unwound
its stack. Every monitor it held was released, so any object it was halfway through mutating stayed
broken and was now visible to everyone else with no exception anywhere to explain it. There is no way
to write code that is safe against that, which is why it was degraded to throwing
`UnsupportedOperationException` in Java 20.

The replacement is cooperative: `interrupt()` sets a flag, blocking calls throw
`InterruptedException`, and the task decides where it is safe to stop. A task that never checks the
flag and never blocks cannot be cancelled, and that is a property of the task, not a missing JDK
feature.

## What actually happens when you call `interrupt()` on a thread?
- id: what-interrupt-does
- level: mid
- tags: interruption

* [ ] The thread is terminated immediately
* [x] A flag is set, and any blocking call that is interruptible throws `InterruptedException` and clears the flag
* [ ] The thread moves to BLOCKED
* [ ] Nothing, unless the thread is inside `synchronized`

`interrupt()` sets an internal flag. If the thread is parked in an interruptible call such as
`sleep`, `wait`, `join`, `BlockingQueue.take` or `Lock.lockInterruptibly`, the call throws
`InterruptedException` and the flag is cleared as part of throwing. If the thread is running, nothing
observable happens until it checks `Thread.interrupted()` or `isInterrupted()`, or reaches a blocking
call.

Some blocking is not interruptible: acquiring `synchronized`, `Lock.lock()`, and most plain socket or
file I/O. `InterruptibleChannel` is the exception, closing itself and throwing
`ClosedByInterruptException`.

## Why is swallowing `InterruptedException` a bug, and what is the correct handling?
- id: swallowing-interrupted
- level: mid
- tags: interruption, antipattern

* [ ] It is not a bug, the exception is informational
* [x] Catching it clears the flag, so the layers above lose the only signal that cancellation was requested
* [ ] You must always call `Thread.stop()` afterwards
* [ ] You must rethrow it as a `RuntimeException`

Throwing `InterruptedException` clears the interrupt flag. If you catch it and log, the request to
cancel has now been destroyed: the loop above you keeps going, the pool's `shutdownNow` appears to do
nothing, and the JVM will not exit.

Two correct endings. Propagate it, if your signature allows, and let the caller decide. Or, if you
cannot, restore the flag before returning:

```java
catch (InterruptedException e) {
    Thread.currentThread().interrupt();   // the next blocking call will see it
    return;                               // and stop doing work
}
```

Restoring without also stopping the work is the half-fix that still loses the cancellation.

## What is the difference between a daemon thread and a user thread?
- id: daemon-threads
- level: junior
- tags: basics, lifecycle

* [ ] Daemon threads get a lower priority
* [x] The JVM exits when only daemon threads are left, and they are not given a chance to finish
* [ ] Daemon threads cannot be interrupted
* [ ] Daemon threads run in a separate process

The JVM stays alive while at least one non-daemon thread runs. Daemon threads are not counted, so when
the last user thread finishes the JVM exits and daemon threads are stopped where they stand: no
`finally` blocks, no shutdown of resources.

That makes daemons right for background housekeeping (a cache evictor, a metrics flusher) and wrong
for anything that owns state someone will miss, such as a writer with a buffer that has not been
flushed. `setDaemon` must be called before `start()`, otherwise it throws
`IllegalThreadStateException`.

## What is the difference between `Runnable` and `Callable`, and where does `Future` fit?
- id: runnable-vs-callable
- level: junior
- tags: basics, executors

* [ ] `Callable` is faster because it does not allocate
* [x] `Callable` returns a value and may throw a checked exception; `Runnable` does neither
* [ ] `Runnable` can only be used with raw threads
* [ ] `Future` is only usable with `Runnable`

`Runnable.run()` returns void and cannot throw checked exceptions, so a failure inside it has nowhere
to go except the thread's uncaught exception handler. `Callable.call()` returns `V` and may throw,
which is what lets an executor capture the outcome.

`submit` wraps either into a `Future`. Calling `get()` blocks until the task finishes and then either
returns the value or throws `ExecutionException` with the original failure as its cause. That is the
important half: with `execute(Runnable)` an exception is printed and lost, with `submit` it is stored
in the `Future` and is lost only if nobody ever calls `get()`.

## Why must you call `start()` rather than `run()`?
- id: start-vs-run
- level: junior
- tags: basics

* [ ] `run()` throws `IllegalThreadStateException`
* [x] `run()` executes the body on the current thread; only `start()` asks the JVM for a new one
* [ ] There is no difference, `start()` is a convenience method
* [ ] `run()` starts the thread but without a stack

`run()` is an ordinary method call. It executes on the calling thread, the new thread is never
created, and everything is sequential. `start()` registers the thread with the OS scheduler, which
then calls `run()` on the new stack.

A second detail interviewers like: a `Thread` object is single use. Calling `start()` twice throws
`IllegalThreadStateException`, because the state machine only ever moves forward towards TERMINATED.

## What is the difference between BLOCKED, WAITING and TIMED_WAITING in a thread dump?
- id: thread-states
- level: mid
- tags: diagnostics, lifecycle

* [ ] They are the same state under different names
* [x] BLOCKED is waiting for a monitor; WAITING and TIMED_WAITING are waiting to be signalled, with and without a deadline
* [ ] BLOCKED means the thread was interrupted
* [ ] WAITING means the thread is on a CPU run queue

BLOCKED means the thread is trying to enter a `synchronized` block whose monitor someone else owns.
The dump names the owner, which makes lock contention and deadlock visible at a glance.

WAITING means the thread called `Object.wait()`, `Thread.join()`, `LockSupport.park()` or an untimed
`Condition.await()`, and will stay there until something signals it. TIMED_WAITING is the same with a
deadline.

The trap: a thread waiting on `ReentrantLock` shows as WAITING at `LockSupport.park`, never as
BLOCKED, because the lock is implemented with `AbstractQueuedSynchronizer` rather than a monitor. If
you grep a dump for BLOCKED to find contention, `java.util.concurrent` locks are invisible to you.

## Does `Thread.yield()` do anything you can rely on?
- id: thread-yield
- level: mid
- tags: scheduling

* [ ] Yes, it guarantees another thread runs next
* [x] No, it is a hint to the scheduler with no guarantees, useful mainly in spin loops via `onSpinWait`
* [ ] It releases every lock the thread holds
* [ ] It puts the thread into WAITING

`yield()` suggests to the scheduler that the current thread is willing to give up its slice. The
scheduler may immediately reschedule the same thread, and the specification requires nothing. Code
whose correctness depends on `yield()` is broken code that happens to pass on one machine.

It also does not release locks, which is what separates it from `Object.wait()`. If a spin loop is
genuinely what you want, `Thread.onSpinWait()` (Java 9) is the right tool: it emits a PAUSE
instruction and tells the CPU that this is a spin, without involving the scheduler at all.

## Two threads, one `Thread.sleep(1000)` inside a `synchronized` block. What does the other thread see?
- id: sleep-holds-lock
- level: junior
- tags: synchronized, sleep

* [ ] It enters the block, sleep releases the monitor
* [x] It stays BLOCKED for the full second, because `sleep` does not release the monitor
* [ ] It throws `IllegalMonitorStateException`
* [ ] It gets the lock after 500 ms because of fairness

`sleep` suspends the thread and keeps every lock it holds. `Object.wait()` is the one that releases
the monitor it was called on, and reacquires it before returning, which is precisely why `wait` must
be called while holding that monitor and `sleep` may be called anywhere.

This is the mechanism behind a whole family of production incidents: an I/O call with a long timeout
inside a `synchronized` block. Nothing is deadlocked, nothing is a race, and the service still stops
responding because a hundred threads are queued behind one slow monitor.

## Can a thread be garbage collected while it is running?
- id: thread-gc
- level: senior
- tags: gc, memory

* [ ] Yes, if you drop the last reference to the `Thread` object
* [x] No, a started and not yet terminated thread is a GC root, so it and everything it references stay alive
* [ ] Only daemon threads are collectable
* [ ] Only if it is parked

Live threads are GC roots. A running thread keeps its stack, every object reachable from it and its
`ThreadLocal` map alive, whether or not anyone still holds a reference to the `Thread` object.

That is the mechanism behind two classic leaks. A thread you started and forgot ("thread leakage")
holds its whole object graph forever. And a `ThreadLocal` on a pooled thread is never collected
between tasks, because the thread outlives them all, which is why a `ThreadLocal` that is not removed
in a `finally` block leaks in a web container.
