# synchronized, monitors, wait and notify

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

## What exactly does `synchronized` lock?
- id: what-synchronized-locks
- level: junior
- tags: synchronized, basics

* [ ] The block of code, so no two threads can run it at all
* [x] One object's monitor: an instance method locks `this`, a static method locks the `Class` object, a block locks whatever you name
* [ ] The class, always
* [ ] The variables mentioned inside the block

`synchronized` is mutual exclusion on one object's monitor, nothing more. Two threads can run the same
synchronized method at the same time if they are on different instances, and two threads can be inside
completely different methods and still block each other if those methods lock the same object.

Which object matters. A `synchronized` instance method and a `synchronized static` method in the same
class do not exclude each other: one holds the instance monitor, the other holds the `Class` monitor.
That mismatch is a real and common bug in code that has both a shared static cache and per-instance
state.

## Why is locking on a `String` literal or a boxed `Integer` a bug?
- id: locking-on-interned
- level: mid
- tags: synchronized, antipattern

* [ ] Because they are immutable
* [x] Because they are interned and shared JVM-wide, so unrelated code can lock the same object
* [ ] Because their `hashCode` changes
* [ ] Because the JIT removes the lock

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

## What is reentrancy, and what would break without it?
- id: reentrancy
- level: mid
- tags: synchronized, locks

* [ ] Reentrancy means two threads can enter the same block
* [x] A thread already holding a monitor can acquire it again; without it, any synchronized method calling another would self-deadlock
* [ ] It allows recursion only up to the stack depth
* [ ] It applies to `ReentrantLock` but not to `synchronized`

A monitor records its owner and a hold count. The owning thread entering again just increments the
count, and the monitor is released when the count reaches zero.

Without reentrancy, an ordinary pattern would hang: a synchronized method calling another synchronized
method on the same object, or a synchronized `equals` on a subclass calling `super.equals`. Both
`synchronized` and `ReentrantLock` are reentrant, which is what the name says. `ReadWriteLock` is the
sharp edge here: a read lock cannot be upgraded to a write lock, and trying deadlocks the thread
against itself.

## Why must `wait()` be called inside a loop?
- id: wait-in-loop
- level: mid
- tags: wait-notify, guarded-suspension

* [ ] Because `wait()` can throw at random
* [x] Because of spurious wakeups, and because another thread may consume the condition between the notify and the waiter reacquiring the lock
* [ ] Because `notify()` only wakes one thread
* [ ] Only if you use `notifyAll()`

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

## When should you prefer `notifyAll()` over `notify()`?
- id: notify-vs-notifyall
- level: mid
- tags: wait-notify

* [ ] Always use `notify()`, it is faster
* [x] Use `notifyAll()` whenever waiters wait for different conditions on the same monitor, otherwise a wakeup can be delivered to a thread that cannot use it and the useful waiter sleeps on
* [ ] They are identical
* [ ] `notifyAll()` releases the lock, `notify()` does not

`notify()` wakes one arbitrary waiter. In a bounded buffer where producers and consumers wait on the
same monitor, the JVM may wake a producer when a consumer was needed. The producer rechecks its
condition, finds the buffer still full, waits again, and the consumer who could have proceeded was
never told. The system stops with work available: a lost wakeup.

`notify()` is safe only when every waiter waits for the same condition and any one of them can make
progress. Otherwise `notifyAll()`, and pay the thundering herd. The better answer to the follow-up is
that `ReentrantLock` with two `Condition` objects, one per role, gets you single-waiter wakeups back
without the hazard.

## Why does `wait()` throw `IllegalMonitorStateException` sometimes?
- id: illegal-monitor-state
- level: junior
- tags: wait-notify

* [ ] Because the object is null
* [x] Because `wait`, `notify` and `notifyAll` may only be called by a thread that owns that object's monitor
* [ ] Because `wait()` was called on a different thread
* [ ] Because the timeout was negative

`wait()` releases the monitor, so it must hold it first. Calling `obj.wait()` outside
`synchronized (obj)` throws immediately, and so does calling `obj.wait()` inside
`synchronized (otherObj)`. The exception is one of the friendlier parts of the API: the alternative
would be a silent race.

Remember that `wait` releases only the monitor it was called on. A thread holding two monitors and
waiting on one keeps the other, which is a fine way to build a deadlock.

## What is the difference between `synchronized` and `ReentrantLock` in practice?
- id: synchronized-vs-reentrantlock
- level: mid
- tags: synchronized, locks

* [ ] `ReentrantLock` is always faster
* [x] `ReentrantLock` adds `tryLock`, timeouts, interruptible acquisition, fairness and multiple conditions, at the cost of a mandatory `finally` to unlock
* [ ] `synchronized` cannot be used on static methods
* [ ] `ReentrantLock` does not need to be released

`synchronized` is scoped to a block and released by the JVM on any exit path, including an exception.
That is a real safety advantage, and it is why it remains the default.

`ReentrantLock` buys you the things a monitor cannot express: `tryLock()` to back off instead of
blocking, `tryLock(timeout)` to bound the wait, `lockInterruptibly()` to stay cancellable, fairness if
you need it, several `Condition` queues per lock, and the ability to lock in one method and unlock in
another. The price is that forgetting `finally { lock.unlock(); }` leaks the lock forever.

On performance, biased locking used to make uncontended `synchronized` nearly free, but it was
disabled in JDK 15 and removed in 18. Measured under contention at 8 threads: `synchronized` about 18
ops/us, `ReentrantLock` about 67.

## Is an uncontended `synchronized` block expensive?
- id: uncontended-synchronized-cost
- level: senior
- tags: synchronized, performance

* [ ] Yes, it always makes a system call
* [x] Cheap: a CAS on the object header, and the JIT may elide the lock entirely
* [ ] It costs nothing at all
* [ ] It costs the same as a contended one

An uncontended monitor is taken with a CAS on the mark word in the object header, no kernel
involvement. Only when a second thread actually contends does the monitor inflate into a heavyweight
one with an OS-level wait.

Two JIT optimisations go further. Lock elision removes the lock when escape analysis proves the object
never leaves the thread, which is why `StringBuffer` in a local variable costs the same as
`StringBuilder`. Lock coarsening merges adjacent synchronized blocks on the same object into one.

The nuance that dates a candidate: biased locking made repeated uncontended locking by the same thread
free, and it is gone as of JDK 18. Advice that assumes it is now wrong.

## Can you `synchronized` on the same object from two methods and still have a race?
- id: partial-synchronization
- level: mid
- tags: synchronized, antipattern

* [ ] No, that is exactly what synchronization prevents
* [x] Yes, if any access path to the state is not synchronized, or if a compound action spans two synchronized calls
* [ ] Only if one method is static
* [ ] Only with more than eight threads

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

## What is lock striping, and when does it stop helping?
- id: lock-striping
- level: senior
- tags: locks, performance, scalability

* [ ] Splitting one lock into thread-local copies
* [x] Splitting one lock into several, each guarding a partition of the data, so unrelated operations do not contend
* [ ] Holding locks in stripes to avoid deadlock
* [ ] A GC technique

One lock over a whole map serialises every operation. Striping guards partition N with lock N, so
threads touching different partitions proceed in parallel. `ConcurrentHashMap` before Java 8 used 16
segments this way; since Java 8 it locks the individual bin instead, which is striping taken to its
limit.

It stops helping in three situations, and naming them is the senior part of the answer. When access is
skewed and one partition is hot, you are back to a single lock. When an operation needs a global view
(`size()`, resizing, a range query), you must take every stripe, which is more expensive than one lock
would have been. And when stripes share a cache line, false sharing turns independent locks into
contention at the hardware level.
