# Atomics and compare-and-swap

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

## What is compare-and-swap, and how does `AtomicInteger.incrementAndGet` use it?
- id: cas-basics
- level: mid
- tags: cas, atomics

* [ ] A synchronized block optimised by the JIT
* [x] A single CPU instruction that sets a value only if it still equals the expected one, wrapped by the JDK in a retry loop
* [ ] A lock that spins instead of blocking
* [ ] A GC write barrier

CAS takes an address, the value you expect to find and the value you want to install, and performs the
swap atomically only if the expectation holds. On x86 it is `lock cmpxchg`; on ARM it is a
load-linked / store-conditional pair. It returns whether it succeeded.

`incrementAndGet` reads the current value, computes value plus one, and calls `compareAndSet`. If
another thread got in first, the CAS fails and the whole thing is retried with the new value. No lock
is ever taken and no thread is ever parked, which is why a failing thread does not block anyone else.

The consequence to state in an interview: CAS is lock-free, not wait-free. The system always makes
progress, but a given unlucky thread can retry many times.

## What is the ABA problem, and how do you avoid it?
- id: aba-problem
- level: senior
- tags: cas, atomics

* [ ] A CAS that fails twice in a row
* [x] A value changes from A to B and back to A, so a CAS succeeds even though the state it assumed has been replaced
* [ ] Two threads reading the same value
* [ ] A deadlock between two atomics

CAS compares values, not history. A thread reads A, is descheduled, and meanwhile another thread
changes the value to B and back to A. The first thread's CAS succeeds, because A is what it expected,
even though everything it inferred from seeing A is now wrong.

With an `AtomicInteger` counter this is usually harmless. With references it is not: in a lock-free
stack, the node you read may have been popped, reused and pushed again, and your CAS quietly links a
freed node back into the list.

The fix is a version stamp, so the pair (value, counter) never repeats:
`AtomicStampedReference` for an int stamp, `AtomicMarkableReference` for a single boolean. Java's
garbage collector removes the worst form of this, which is why ABA bites C++ far harder.

## Why is `LongAdder` faster than `AtomicLong`, and when is it not?
- id: longadder-vs-atomiclong
- level: senior
- tags: atomics, performance, false-sharing

* [ ] It uses a lock internally
* [x] It spreads updates across padded cells so threads stop fighting over one cache line, but `sum()` must walk every cell and is not atomic
* [ ] It is a drop-in replacement with no trade-off
* [ ] It only works with 64-bit values

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

## What is false sharing, and how do you fix it?
- id: false-sharing
- level: senior
- tags: performance, hardware, false-sharing

* [ ] Two threads reading the same variable
* [x] Two unrelated variables landing on one 64-byte cache line, so writing one invalidates the other core's copy
* [ ] Sharing a lock between unrelated objects
* [ ] A JIT deoptimisation

Caches move data in lines, typically 64 bytes. If two threads write two different fields that happen
to sit on the same line, the hardware coherence protocol bounces that line between cores as though
they were sharing a variable. The program is correct and roughly 2.8x slower, measured.

The fix is padding so the hot fields land on separate lines: `@Contended` (with
`-XX:-RestrictContended`, since the annotation is internal), or manual padding fields. This is what
`LongAdder`'s cells and `ForkJoinPool`'s queues do.

The diagnosis matters as much as the fix: it shows up as scaling that gets worse with more threads,
with no lock contention anywhere in the profile.

## What do `getAndUpdate`, `updateAndGet` and `accumulateAndGet` do, and what is the catch?
- id: atomic-lambdas
- level: mid
- tags: atomics, api

* [ ] They lock the atomic while the function runs
* [x] They run your function inside the CAS retry loop, so the function must be pure and side-effect free because it may run several times
* [ ] They are slower versions of `set`
* [ ] They only work on `AtomicReference`

These methods let you apply an arbitrary function atomically:

```java
counter.updateAndGet(n -> Math.min(n + 1, max));
```

The implementation reads, applies the function, and CASes. On failure it re-reads and applies the
function again. That is the catch: your lambda may be invoked several times for one logical update, so
it must be side-effect free. Logging, incrementing a second counter or sending a message inside it will
happen more than once, silently, only under contention.

## How would you write a thread-safe non-blocking counter without atomics?
- id: cas-by-hand
- level: senior
- tags: cas, varhandle

* [ ] It is impossible without `synchronized`
* [x] With a `VarHandle` (or, historically, `Unsafe`) doing the compare-and-set on a volatile field yourself
* [ ] With a `ThreadLocal`
* [ ] With `Collections.synchronizedList`

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

## What is the difference between `compareAndSet` and `weakCompareAndSet`?
- id: weak-cas
- level: senior
- tags: cas, atomics

* [ ] `weakCompareAndSet` compares with `equals`
* [x] The weak form may fail spuriously and has weaker ordering, which makes it cheaper on some architectures inside a loop that retries anyway
* [ ] The weak form is deprecated
* [ ] The weak form does not use CAS at all

`weakCompareAndSet` is allowed to return false even when the value matched. On ARM and POWER, where
CAS is a load-linked / store-conditional pair, the store can fail for unrelated reasons such as an
intervening interrupt, and forcing a retry in hardware costs more than letting the caller retry.

It is only correct inside a loop that would retry anyway. Using it for a one-shot "set this flag if it
is still null" is a bug, because a spurious failure is indistinguishable from a real one. On x86 the
two compile to the same instruction, which is why testing on x86 proves nothing here.

## Is `AtomicReference` enough to make the object it points at thread safe?
- id: atomicreference-scope
- level: mid
- tags: atomics, immutability

* [ ] Yes, all access through it is synchronized
* [x] No, it makes replacing the reference atomic; the object behind it must be immutable or separately guarded
* [ ] Only for final fields
* [ ] Only if the object implements `Serializable`

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

## What does "lock-free" mean, and how does it differ from "wait-free"?
- id: lock-free-vs-wait-free
- level: senior
- tags: theory, cas

* [ ] They are synonyms
* [x] Lock-free guarantees the system as a whole makes progress; wait-free guarantees every individual thread finishes in a bounded number of steps
* [ ] Lock-free means no `synchronized` keyword appears
* [ ] Wait-free means no thread ever waits for I/O

Obstruction-free, lock-free and wait-free are three increasingly strong guarantees. Lock-free means at
least one thread always makes progress, so the system cannot stall even if a thread is descheduled
mid-operation. Individual threads can still starve, retrying forever while others succeed.

Wait-free adds a bound per thread, which is much harder and usually slower in the common case.
`AtomicInteger.get` and `getAndIncrement` (implemented with `getAndAddInt`'s hardware fetch-and-add on
x86) are wait-free; a hand-written CAS loop is only lock-free.

The practical point: absence of locks does not imply absence of starvation, and a lock-free structure
under heavy contention can be slower than a plain lock, because every failed CAS is wasted work plus a
cache-line invalidation.

## Can you replace `volatile boolean running` with `AtomicBoolean`, and should you?
- id: atomicboolean-vs-volatile
- level: junior
- tags: atomics, volatile

* [ ] No, they behave differently for a flag
* [x] Yes, and for a plain set-and-read flag `volatile` is the cheaper, clearer choice; `AtomicBoolean` earns its place when you need `compareAndSet`
* [ ] Yes, and `AtomicBoolean` is always better
* [ ] No, `AtomicBoolean` is not thread safe

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
