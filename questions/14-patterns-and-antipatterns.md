# Patterns and antipatterns

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

## What does thread confinement mean, and what are the three kinds?
- id: thread-confinement
- level: mid
- tags: design, confinement

* [ ] Pinning a thread to a CPU
* [x] Keeping data reachable from one thread only: ad-hoc by convention, stack confinement in locals, and `ThreadLocal`
* [ ] Limiting the number of threads
* [ ] Restricting a thread to one class

Data that only one thread can reach needs no synchronisation at all, which makes confinement the
cheapest correct answer available.

Stack confinement is the strongest form: a local variable that never escapes cannot be shared, and the
compiler enforces it. Ad-hoc confinement is a convention ("only the UI thread touches this"), which is
free and fragile, so it must be documented and ideally asserted. `ThreadLocal` is confinement with a
lifetime, which is useful for a per-thread `SimpleDateFormat` or a request context, and dangerous in a
pool if not removed.

Swing and most UI frameworks are built entirely on ad-hoc confinement, which is why calling a component
from a background thread is a bug even though nothing throws.

## Why is immutability the strongest thread safety guarantee?
- id: immutability
- level: junior
- tags: design, immutability

* [ ] Because immutable objects are faster
* [x] Because state that never changes cannot race: all-final fields are safely published by any means, with no synchronisation at all
* [ ] Because the GC handles them specially
* [ ] Because they cannot be null

An immutable object has no writes after construction, so there is nothing for two threads to disagree
about. The final-field guarantee means that any thread obtaining a reference sees the fields fully
constructed, even through a data race, so you can share it freely.

The requirements are exact: all fields final, no setters, `this` never escaping the constructor, and
defensive copies of any mutable component both in and out. A `final List` field is not enough; the list
must be `List.copyOf`'d or wrapped.

Records give you most of this for free, with the caveat that a record holding a mutable array or list
is only as immutable as its components.

## What is the producer/consumer pattern and what does the queue actually provide?
- id: producer-consumer
- level: junior
- tags: patterns, queues

* [ ] A way to run tasks faster
* [x] Decoupling of rate and of lifecycle: the queue is the only shared state, it provides the happens-before edge, and a bounded one provides backpressure
* [ ] A replacement for a thread pool
* [ ] A design that requires `wait`/`notify`

Producers and consumers never touch each other's data; the only shared object is the queue, and
`BlockingQueue` handles the synchronisation, the blocking and the memory visibility (a `put`
happens-before the corresponding `take`). That reduces a concurrency problem to a data structure
choice.

The bound is the design decision. Bounded means a fast producer is slowed down by a slow consumer,
which is backpressure, and the system degrades gracefully. Unbounded means the mismatch accumulates in
memory until the process dies.

The poison pill is the idiomatic shutdown: put a sentinel per consumer so each one sees exactly one and
exits, rather than interrupting mid-work. A thread pool is this pattern wearing a nicer API.

## What is the balking pattern, and where would you use it?
- id: balking-pattern
- level: mid
- tags: patterns

* [ ] Retrying until the state is right
* [x] If the object is not in the right state when a method is called, return immediately instead of waiting
* [ ] Blocking until another thread balks
* [ ] A rejection policy for thread pools

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

## What is the double-checked locking antipattern really about?
- id: dcl-antipattern
- level: mid
- tags: antipatterns, singleton

* [ ] It is always wrong
* [x] It is correct only with a volatile field, and for most cases there is a simpler construct that avoids it entirely
* [ ] It is a performance optimisation with no correctness issue
* [ ] It only works in a single-threaded program

The idiom exists to avoid taking a lock on every read of a lazily initialised field. Without `volatile`
it is broken, because another thread can see a published reference to a partially constructed object.
With `volatile` it is correct.

The better answer is to avoid needing it. For a static singleton, the holder idiom relies on the JVM's
class initialisation lock and needs no volatile, no synchronized and no double check. For an instance
field, `AtomicReference` with `compareAndSet` or `Suppliers.memoize`-style wrapping says what you mean.
And eager initialisation is usually fine: the object is almost always cheaper than the argument about
it.

Interviewers ask this to see if you reach for the clever construct or for the simple one.

## Why is "synchronize everything" a bad strategy?
- id: excessive-synchronization
- level: mid
- tags: antipatterns, performance

* [ ] It is safe but slow
* [x] It is both slow and not actually safe: it serialises the application, and holding broad locks across calls to unknown code invites deadlock
* [ ] It only affects startup time
* [ ] It prevents the JIT from compiling

Locking everything converts a concurrent program into a sequential one with extra overhead, and the
scaling curve goes flat or downwards as threads are added.

The correctness half is less obvious and more dangerous. A broad lock is likely to be held while
calling code you do not control: a listener, an overridden method, a callback, a `toString` on a
user-supplied object. That is an alien call with a lock held, and it is how deadlocks form between
components that were each individually correct.

The discipline: hold the lock for the shortest interval that keeps the invariant, never call unknown
code while holding it, prepare data outside and mutate inside, and document which lock guards which
field (`@GuardedBy` is worth the annotation).

## What is the thread-per-task versus task-per-thread distinction, and why did pools exist?
- id: threads-instead-of-tasks
- level: mid
- tags: design, executors

* [ ] They are the same thing
* [x] Thread per work item does not scale on platform threads, so pools bound and reuse them
* [ ] Pools exist only to limit memory
* [ ] Tasks are always slower than threads

`new Thread(task).start()` per request works until it does not: each one reserves a stack, each start
is a syscall, and thousands of them thrash the scheduler. Pools decouple the unit of work (a task) from
the unit of execution (a thread), so you can have a million tasks and eight threads.

The cost of that decoupling is everything this repository has a topic about: queueing, rejection
policies, thread locals that outlive tasks, cancellation that no longer maps to a thread, and stack
traces that stop at the pool boundary.

Virtual threads restore the simple model. `Executors.newVirtualThreadPerTaskExecutor()` is thread per
task, and the reason it is now sensible is that the expensive resource the pool was amortising no
longer costs anything.

## Why should you not start a thread from a constructor?
- id: thread-in-constructor
- level: senior
- tags: antipatterns, publication

* [ ] It is slower than starting it later
* [x] Because `this` escapes before construction finishes, so the new thread can observe uninitialised fields, including in a subclass whose constructor has not run yet
* [ ] Because constructors cannot throw `InterruptedException`
* [ ] Because the thread would be a daemon

`new Thread(this).start()` inside a constructor publishes `this` before the object is finished. The
new thread may see default values in fields the constructor is about to set, and the final-field
guarantee does not apply because the reference escaped before the freeze.

Subclassing makes it worse: the superclass constructor runs first, so the thread starts while every
subclass field is still zero, and the object it sees may never have existed in a valid state.

The same argument applies to registering a listener, publishing to a static registry or passing `this`
to anything from a constructor. The fix is a factory method or a separate `start()` call, so
construction completes before the object is shared. This is the escape half of safe publication.

## When would you choose an object pool, and when is it the wrong answer?
- id: object-pool
- level: senior
- tags: patterns, performance

* [ ] Always, allocation is expensive
* [x] For scarce external resources such as connections, not for ordinary objects
* [ ] Only for immutable objects
* [ ] Only in Android

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

## What is the two-phase termination pattern?
- id: two-phase-termination
- level: mid
- tags: patterns, lifecycle

* [ ] Calling `stop()` twice
* [x] Signalling a thread to finish (a flag plus an interrupt), then letting it do its own cleanup before it exits
* [ ] Shutting down two pools in order
* [ ] A GC phase

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

## How do you make a class document its own thread safety?
- id: documenting-thread-safety
- level: mid
- tags: design, documentation

* [ ] By naming it `SafeFoo`
* [x] By stating the policy in the javadoc and marking fields with `@GuardedBy`, so the reader knows which lock covers what
* [ ] By making every method synchronized
* [ ] Thread safety cannot be documented

A class is thread safe, conditionally thread safe, or not, and the reader cannot tell by looking. State
it in one sentence at the top of the javadoc: "Thread safe. All mutable state is guarded by `lock`."
Or: "Not thread safe. Confine instances to one thread."

`@GuardedBy("lock")` on a field names the invariant precisely, is checkable by static analysis tools
(SpotBugs, ErrorProne), and survives the refactoring that a comment would not. For conditionally
thread-safe classes, say which compound sequences the caller must lock, and which lock to use.

This is not paperwork: an undocumented thread safety policy is how correct code becomes incorrect two
maintainers later.
