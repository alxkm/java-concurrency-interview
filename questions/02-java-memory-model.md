# The Java memory model

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

## What is happens-before, in one sentence, and why does it matter?
- id: happens-before-definition
- level: mid
- tags: memory-model, happens-before

* [ ] It means one statement executes before another in wall-clock time
* [x] It is an ordering guarantee: if A happens-before B, the effects of A are visible to B and may not be reordered past it
* [ ] It only applies to `synchronized` blocks
* [ ] It is a JVM flag that enables strict ordering

Happens-before is not about time, it is about visibility and ordering. If write A happens-before read
B, then B must observe A, and the compiler, JIT and CPU may not move things across that edge in a way
you could detect.

Without such an edge there is no guarantee at all. The read may see the new value, the old value, or
values in an order the source code never wrote. Nothing is "probably fine": it is unspecified, and
unspecified means it will hold on your laptop and break on a 64-core ARM server.

The practical skill is naming the edge. "This is safe because the volatile write to `ready`
happens-before the volatile read of `ready`" is an answer. "It works when I run it" is not.

## Which happens-before edges do you get for free?
- id: happens-before-edges
- level: mid
- tags: memory-model, happens-before

* [x] `Thread.start()` and `join()`, monitor unlock to lock, volatile write to read, and final fields
* [ ] Only `synchronized`
* [ ] Any method call on a shared object
* [ ] Anything inside a `try` block

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

## What does `volatile` guarantee, and what does it not?
- id: volatile-guarantees
- level: junior
- tags: memory-model, volatile

* [ ] It makes `counter++` atomic
* [x] It gives visibility and ordering for reads and writes of that field, but no atomicity for compound actions
* [ ] It locks the object while the field is written
* [ ] It only prevents caching, not reordering

A volatile write is guaranteed to be seen by any subsequent volatile read of the same field, and it
carries with it everything written before it (release/acquire semantics). It also forbids the
compiler from hoisting the read out of a loop, which is what makes a plain flag loop hang forever
while a volatile one exits.

What it does not do is make read-modify-write atomic. `count++` is a read, an add and a write;
another thread can slip between them whatever `volatile` says. For a counter you want `AtomicInteger`
or `LongAdder`; for a flag or a "publish this reference once" field, `volatile` is exactly right.

A runnable demonstration of both halves: [VisibilityExample](https://github.com/alxkm/java-concurrency-patterns/blob/master/src/main/java/org/alxkm/memorymodel/VisibilityExample.java).

## Why does double-checked locking need the field to be volatile?
- id: dcl-volatile
- level: senior
- tags: memory-model, volatile, singleton

* [ ] To stop two threads entering the synchronized block
* [x] Without it, another thread can see a non-null reference to an object whose constructor has not finished
* [ ] Because `synchronized` does not work on static fields
* [ ] It is not needed since Java 8

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

## What is safe publication, and which ways of achieving it can you name?
- id: safe-publication
- level: senior
- tags: memory-model, publication

* [ ] Making every field private
* [x] Publishing a reference so that the writes made during construction are guaranteed visible to whoever reads it
* [ ] Copying the object before sharing it
* [ ] Calling `System.gc()` after construction

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

## What does `final` guarantee, and what breaks that guarantee?
- id: final-field-semantics
- level: senior
- tags: memory-model, final, immutability

* [ ] Nothing, `final` is a compile-time check only
* [x] Final fields set in the constructor are visible to any thread that sees the object, unless `this` escaped during construction
* [ ] Final fields are automatically volatile
* [ ] It guarantees the referenced object is immutable too

There is a freeze at the end of the constructor: any thread that obtains a reference to the object
through a normal read is guaranteed to see the final fields with their constructed values, with no
synchronisation of any kind. This is what makes `String` and the immutable collections safe to share
freely.

The guarantee has one condition and one limit. The condition: `this` must not escape during
construction. Registering a listener, starting a thread or passing `this` to a callback from a
constructor hands out a reference before the freeze, and the guarantee is void. The limit: `final`
freezes the reference, not the object behind it. A `final List` field whose list is mutated is not
thread safe in the slightest.

## What is instruction reordering, and can you observe it from ordinary Java?
- id: reordering
- level: senior
- tags: memory-model, reordering

* [ ] No, the JVM never reorders anything
* [x] Yes, but almost never from a unit test: the synchronisation that lines two threads up is itself a barrier that hides the effect
* [ ] Only on x86
* [ ] Only when the JIT is disabled

Compilers, the JIT and the CPU all reorder, as long as a single thread cannot tell. Another thread
can tell. In the classic Dekker probe two threads each write one field and read the other, and `0, 0`
is a legal outcome that no interleaving of the source explains.

The interesting part is how hard it is to demonstrate. Lining two threads up requires a latch or a
barrier, and that is a memory barrier, which drains the store buffer producing the effect. Measured
in the sibling repository, the probe found zero reorderings in 20,000 hand-written attempts and
9,914,377 under [jcstress](https://github.com/openjdk/jcstress), 3.74% of samples, in a single run.

The lesson is what makes this a senior question. These bugs do not fail in testing. They fail in
production, rarely, on someone else's hardware.

## Where do memory barriers come into this, and which ones does the JVM emit?
- id: memory-barriers
- level: senior
- tags: memory-model, hardware

* [ ] The JVM emits a full barrier for every field access
* [x] Volatile and lock operations compile to load/store fences, with the exact instruction depending on the CPU architecture
* [ ] Barriers are a C++ concept and do not exist in the JVM
* [ ] `synchronized` uses no barriers, only OS locks

The JMM is written in terms of happens-before so that it is portable, but a real CPU implements it
with fences. Conceptually there are four: LoadLoad, LoadStore, StoreStore and StoreLoad. A volatile
write is StoreStore before, StoreLoad after; a volatile read is LoadLoad and LoadStore after.

The costs differ by architecture, which is why "it works on my machine" is so misleading here. x86 is
total-store-order: ordinary loads and stores are already mostly ordered, and only the StoreLoad after
a volatile write needs a real instruction (`lock addl` or `mfence`). ARM and POWER are weakly ordered
and need explicit `dmb`-class instructions at almost every edge, so races that are invisible on x86
surface immediately there.

## Is `long` and `double` assignment atomic in Java?
- id: long-tearing
- level: mid
- tags: memory-model, atomicity

* [ ] Yes, all primitive writes are atomic
* [x] Not guaranteed for non-volatile `long` and `double`: the JLS allows a 64-bit write to be split into two 32-bit halves
* [ ] Only atomic inside `synchronized`
* [ ] Only on 32-bit JVMs, which no longer exist

The JLS explicitly permits a non-volatile 64-bit write to be treated as two 32-bit writes, so a
reader can observe the high half of one value with the low half of another: a value that was never
written by anyone. Declaring the field `volatile` makes the access atomic and the tearing
disappears.

In practice every 64-bit HotSpot build writes a `long` atomically, so this is difficult to reproduce
and often dismissed. It is still the right answer to the question as asked, and it is the honest
reason `AtomicLong` exists as a type rather than as a convenience wrapper.

## Two threads increment a shared `int` a million times each with no synchronisation. What is the final value and why?
- id: lost-updates
- level: junior
- tags: atomicity, race-condition

* [ ] Exactly 2,000,000
* [x] Somewhere between 1 and 2,000,000, because `i++` is read, add and write, and updates are lost when they interleave
* [ ] Always exactly 1,000,000
* [ ] A `ConcurrentModificationException`

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
