# Locks, conditions and AQS

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

## What is `AbstractQueuedSynchronizer` and which classes are built on it?
- id: aqs-basics
- level: senior
- tags: aqs, locks, internals

* [ ] A base class for thread pools
* [x] A framework holding an atomic int state plus a CLH wait queue, used by ReentrantLock, Semaphore, CountDownLatch, ReadWriteLock and FutureTask
* [ ] The implementation of `synchronized`
* [ ] A lock-free queue implementation

AQS gives you one `volatile int state`, CAS operations on it, and a FIFO queue of parked threads. A
synchroniser subclasses it and defines what the state means and when acquisition succeeds. Everything
else, the queueing, parking, unparking and cancellation, is inherited.

`ReentrantLock` uses state as a hold count. `Semaphore` uses it as a permit count. `CountDownLatch`
uses it as the remaining count and never lets it go back up. `ReentrantReadWriteLock` packs readers
and writers into two halves of the same int. `FutureTask` and `ThreadPoolExecutor.Worker` use it too.

Note that `synchronized` is not on the list: monitors are implemented in the JVM, not in Java, which is
why a thread waiting on a `ReentrantLock` appears as WAITING at `LockSupport.park` rather than as
BLOCKED.

## What does a fair lock actually do, and what does it cost?
- id: fair-locks
- level: mid
- tags: locks, fairness, performance

* [ ] It gives every thread equal CPU time
* [x] It hands the lock to the longest-waiting thread instead of whoever asks first, which removes barging and costs a great deal of throughput
* [ ] It prevents deadlock
* [ ] It is the default for `ReentrantLock`

An unfair lock lets an arriving thread barge: if the lock is free at the moment it asks, it takes it,
even though others have been queued for a while. That is fast, because the running thread is already
on a CPU with warm caches and no context switch is needed.

A fair lock checks the queue first and always yields to the longest waiter. Every handoff then costs a
park and an unpark. Throughput typically drops by an order of magnitude under contention.

Unfair is the default, and usually right. Choose fairness when starvation is a real risk and hold
times are long enough that the handoff cost does not dominate. Note what fairness does not promise:
the scheduler still decides who runs, and `tryLock()` barges even on a fair lock, by design.

## When is `ReentrantReadWriteLock` a win, and when is it a trap?
- id: read-write-lock
- level: senior
- tags: locks, performance

* [ ] Always a win, reads are more common than writes
* [x] A win for long reads and rare writes; a loss for short critical sections, where its bookkeeping costs more than the exclusion it avoids
* [ ] It allows several writers as long as they touch different fields
* [ ] It upgrades a read lock to a write lock automatically

Read/write locks pay off when reads dominate *and* each critical section is long enough for the
parallelism to be worth the extra bookkeeping. Every acquisition has to update a shared counter, which
is itself a contended write, so for a two-field getter a plain `ReentrantLock` usually wins.

Three traps worth naming. Upgrading a read lock to a write lock is not supported and deadlocks the
thread against itself; you must release and reacquire, and recheck your state. Under a steady stream
of readers a non-fair write lock can be starved. And a reader that blocks on I/O holds the lock and
blocks every writer behind it.

`StampedLock` exists because of this, and immutable snapshots or `CopyOnWriteArrayList` often beat both.

## What is optimistic reading in `StampedLock`?
- id: stampedlock-optimistic
- level: senior
- tags: locks, stampedlock

* [ ] A read that retries until it succeeds
* [x] A read that takes no lock at all, then validates a stamp afterwards and falls back to a real read lock if a writer intervened
* [ ] A read that blocks writers
* [ ] Another name for a read lock

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

## What does `Condition` give you that `wait`/`notify` does not?
- id: condition-vs-wait
- level: mid
- tags: locks, condition

* [ ] Nothing, it is the same API with different names
* [x] Several independent wait queues on one lock, so you can signal exactly the waiters that can make progress
* [ ] It avoids spurious wakeups
* [ ] It does not require holding the lock

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

## How do you implement a timeout on acquiring a lock, and why would you?
- id: trylock-timeout
- level: mid
- tags: locks, deadlock

* [ ] `synchronized (obj, timeout)`
* [x] `lock.tryLock(500, MILLISECONDS)`, which returns false instead of waiting forever and gives you a way to back out of a deadlock
* [ ] `Thread.sleep` before entering the block
* [ ] `wait(timeout)` on the lock object

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

## What is `LockSupport.park`, and why is it better than `suspend`/`resume`?
- id: locksupport-park
- level: senior
- tags: locks, internals

* [ ] It is the same thing with a new name
* [x] It is permit-based, so an `unpark` that arrives before the `park` is remembered and the park returns immediately, which removes the lost-wakeup race
* [ ] It never blocks
* [ ] It only works inside `synchronized`

`park()` and `unpark(thread)` are the primitives AQS blocks on. Each thread has a single permit. An
`unpark` makes the permit available, and a `park` consumes it, blocking only if none is there. Because
the permit is remembered, ordering does not matter, which is exactly the race that made the deprecated
`suspend`/`resume` pair unusable: a `resume` arriving first was lost and the thread suspended forever.

Two details. `park` may return spuriously, so callers loop, the same rule as `wait`. And a parked
thread appears as WAITING in a dump with `LockSupport.park` at the top of the stack, which is what
every `java.util.concurrent` blocking operation looks like.

## What is a spin lock, and when is spinning better than blocking?
- id: spin-locks
- level: senior
- tags: locks, performance

* [ ] Never, spinning always wastes CPU
* [x] When the hold time is shorter than a context switch, which is why HotSpot spins before parking
* [ ] When there are more threads than cores
* [ ] Only in real-time systems

Parking and unparking a thread costs a few microseconds of kernel time plus the cache damage of being
rescheduled. If the lock is held for tens of nanoseconds, spinning until it frees is cheaper. HotSpot
does adaptive spinning for exactly that reason, and tunes the spin count on how long the lock was held
last time.

Spinning is wrong when hold times are long, when there are more runnable threads than cores (you spin
waiting for a thread that is not even running), or on a single core, where it is a guaranteed waste of
a whole slice. Use `Thread.onSpinWait()` inside the loop: it emits a PAUSE hint that lowers power draw
and helps the sibling hyperthread.

## Can a thread deadlock against itself?
- id: self-deadlock
- level: mid
- tags: locks, deadlock

* [ ] No, locks are reentrant
* [x] Yes: a non-reentrant lock such as `StampedLock`, or trying to upgrade a read lock to a write lock in `ReentrantReadWriteLock`
* [ ] Only with `synchronized`
* [ ] Only if it calls `join()` on itself

`synchronized` and `ReentrantLock` are reentrant, so recursion is fine there. `StampedLock` is not
reentrant at all, and reacquiring any of its locks on the same thread parks that thread forever with no
owner ever coming to release it.

The subtler case is lock upgrade. Holding a read lock and asking for the write lock waits for all
readers to leave, including the caller, which never will. Downgrading is legal and supported: take the
write lock, acquire the read lock while still holding it, then release the write lock.

`Thread.currentThread().join()` is a third way to hang forever, and appears in interviews as a trick
question rather than as real code.

## Why is `lock()` outside `try` and `unlock()` inside `finally` the required shape?
- id: lock-try-finally
- level: junior
- tags: locks, idiom

* [ ] Style only
* [x] Any other shape either leaks the lock or unlocks one this thread never acquired
* [ ] Because `unlock` can throw
* [ ] Because the JIT requires it

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
