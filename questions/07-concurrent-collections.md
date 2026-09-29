# Concurrent collections

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

## Why is `Hashtable` or `Collections.synchronizedMap` worse than `ConcurrentHashMap`?
- id: synchronizedmap-vs-chm
- level: junior
- tags: collections, chm

* [ ] They are not thread safe
* [x] They lock the whole map for every operation, and compound actions still race even though each call is atomic
* [ ] They do not allow null values
* [ ] They are deprecated

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

## How does `ConcurrentHashMap` work since Java 8?
- id: chm-internals
- level: senior
- tags: collections, chm, internals

* [ ] It uses 16 segments, each with its own lock
* [x] It locks the head node of a single bin with `synchronized`, uses CAS for empty bins, and turns a long collision chain into a red-black tree
* [ ] It copies the whole table on every write
* [ ] It is backed by a `ConcurrentSkipListMap`

Java 7 used segments: a fixed array of sub-maps, each with its own lock, giving 16 writers in parallel
by default. Java 8 dropped that. Writes CAS an empty bin directly; if the bin is occupied, the writer
synchronizes on the head node, so the lock granularity is one bucket rather than one sixteenth of the
table.

Two more pieces worth naming. A bin whose chain exceeds 8 entries, in a table of at least 64, becomes a
red-black tree, so a hash collision attack degrades to O(log n) rather than O(n). And resizing is
cooperative: a thread that finds a resize in progress helps transfer bins instead of blocking.

`size()` is not a counter under a lock. It sums a `LongAdder`-style set of striped cells, so it is
accurate only when nobody is writing.

## Why does `ConcurrentHashMap` forbid null keys and values?
- id: chm-no-nulls
- level: mid
- tags: collections, chm, api

* [ ] An oversight kept for compatibility
* [x] Because `get` returning null would be ambiguous, and with concurrent writers you cannot resolve it with `containsKey`
* [ ] Because null cannot be hashed
* [ ] Because it would break serialisation

In a `HashMap`, `get(k) == null` is ambiguous between "absent" and "mapped to null", and you resolve
it with `containsKey`. In a concurrent map that second call is a separate point in time: the mapping
can appear or vanish in between, so the ambiguity cannot be resolved at all. Doug Lea's position is
that allowing null buys nothing and hides bugs.

The practical follow-up is `computeIfAbsent` returning null from the mapping function, which means "do
not store anything", not "store null". And `merge` treats a null result as a removal. Both surprise
people porting code from `HashMap`.

## What is the iteration behaviour of a concurrent collection, and what is `ConcurrentModificationException`?
- id: weakly-consistent-iterators
- level: mid
- tags: collections, iteration

* [ ] Concurrent collections throw CME too, just less often
* [x] Their iterators are weakly consistent: never throw CME, may or may not reflect changes made after creation, and traverse each element once
* [ ] They snapshot the whole collection
* [ ] They lock the collection during iteration

`HashMap` and `ArrayList` iterators are fail-fast: they keep a modification counter and throw
`ConcurrentModificationException` when they notice a change. That is a bug detector, not a thread
safety mechanism, and it is best-effort even single threaded (removing an element via the collection
inside a for-each is the usual trigger).

Concurrent collections instead promise weak consistency. You will see every element that was present
for the whole traversal, you may or may not see concurrent insertions, and nothing throws. The price is
that an iteration is not a snapshot, so counting with it gives an approximation.

`CopyOnWriteArrayList` is the exception: its iterator is a true immutable snapshot of the array at
creation time, so it never sees later changes and does not support `remove`.

## When is `CopyOnWriteArrayList` the right choice?
- id: copyonwrite
- level: mid
- tags: collections, performance

* [ ] Whenever a list is shared
* [x] When reads massively outnumber writes and the list is small, such as a listener registry
* [ ] When the list is very large
* [ ] When you need index-based writes

Every mutation allocates a new array and copies the whole thing, under a lock. Writes are O(n) and
produce garbage; reads take no lock at all and never block, because they read a final snapshot
reference.

That makes it excellent for listener and subscriber lists: written at startup, read on every event,
never large. It is a disaster for anything written in a loop or holding thousands of elements, where
each add copies the lot.

The snapshot iterator also has a semantic consequence: a listener added during an event dispatch will
not receive that event, which is usually what you want and occasionally a surprise.

## `ArrayBlockingQueue` or `LinkedBlockingQueue`, and does bounded matter?
- id: blocking-queue-choice
- level: mid
- tags: queues, backpressure

* [ ] `LinkedBlockingQueue` is always faster because it has two locks
* [x] Bounded is the important decision: an unbounded queue turns a slow consumer into an OutOfMemoryError instead of backpressure
* [ ] They are interchangeable
* [ ] `ArrayBlockingQueue` is unbounded by default

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

## What is `ConcurrentSkipListMap` for, and how does it differ from `ConcurrentHashMap`?
- id: skiplist-map
- level: mid
- tags: collections, sorted

* [ ] It is a faster hash map
* [x] It is a sorted, navigable concurrent map with O(log n) operations, which a hash map cannot provide
* [ ] It is the concurrent version of `LinkedHashMap`
* [ ] It allows null keys

When you need ordering, range queries, `firstKey`, `headMap` or `ceilingEntry` from several threads,
there is no concurrent `TreeMap`. `ConcurrentSkipListMap` fills that gap with a skip list, which is a
probabilistic balanced structure that can be updated with CAS rather than the rotations a red-black
tree would need under a lock.

The trade-off is the usual one: O(log n) instead of O(1) and worse constants, so do not use it as a
general-purpose map. Its `size()` is also O(n), because there is no counter to read.

## How would you build a thread-safe cache with `computeIfAbsent`, and what is the trap?
- id: computeifabsent-trap
- level: senior
- tags: chm, api, deadlock

* [ ] There is no trap, that is what it is for
* [x] The mapping function runs while the bin is locked, so a recursive or slow function can deadlock or block every writer to that bin
* [ ] It is not atomic
* [ ] It cannot store null

`computeIfAbsent` is the right tool for a memoising cache: exactly one thread computes, everyone else
waits and gets the same value, with no double computation and no check-then-act race.

The trap is what runs under the bin lock. If the mapping function updates the same map, you can get an
`IllegalStateException` ("recursive update") or, in the pre-Java-9 versions, a corrupted table. If it
is slow (an I/O call, a remote lookup), every other writer that hashes to the same bin is blocked for
its whole duration, and if two threads compute entries that recursively depend on each other, they
deadlock.

For expensive values the classic alternative is storing a `FutureTask` or `CompletableFuture`: insert
the future atomically, then compute outside the lock.

## What does `Collections.unmodifiableList` guarantee about thread safety?
- id: unmodifiable-vs-immutable
- level: mid
- tags: collections, immutability

* [ ] It makes the list thread safe
* [x] Nothing: it is a read-only view over a list someone else can still mutate, with no happens-before edge for those changes
* [ ] It copies the list
* [ ] It is the same as `List.copyOf`

An unmodifiable wrapper only blocks writes through that reference. The underlying list is unchanged,
and if anyone retains a reference and mutates it, readers of the view see those changes with no
synchronisation at all: a data race, and possibly a torn view of the internal array.

`List.copyOf` and `List.of` are different: they build a genuinely immutable list whose fields are
final, which makes it safe to publish by any means, including a race. That is the final-field
guarantee, and it is the reason immutability is the cheapest thread safety there is.

## Is `ConcurrentHashMap.size()` reliable, and what should you use instead?
- id: chm-size
- level: mid
- tags: chm, api

* [ ] Yes, it is exact and O(1)
* [x] It is an estimate under concurrent modification; for a running total, keep your own `LongAdder`
* [ ] It throws if the map is being modified
* [ ] It locks the whole map

`size()` sums a base value plus an array of striped counter cells, the same design as `LongAdder`. With
no concurrent writers the answer is exact; with writers it is a snapshot that was true at no single
instant. `mappingCount()` is the same thing returning a `long`, and is the preferred method because a
concurrent map can exceed `Integer.MAX_VALUE` entries.

`isEmpty()` has the same caveat, and using it to decide whether to shut something down is a classic
race. If you need an authoritative count, count it yourself with a `LongAdder` updated alongside the
map.

## When is a plain `HashMap` behind your own lock better than `ConcurrentHashMap`?
- id: when-not-chm
- level: senior
- tags: collections, design

* [ ] Never
* [x] When you need several operations to be atomic together, which a concurrent map cannot give you across calls
* [ ] When the map is large
* [ ] When there is only one thread

`ConcurrentHashMap` gives per-operation atomicity plus a handful of compound operations. If your
invariant spans two maps, or a map and a counter, or requires iterating and then updating based on what
you saw, no amount of concurrent collection will help: the state between your calls is unprotected.

In that case a `HashMap` guarded by a `ReentrantLock` you hold across the whole operation is both
simpler and more correct, and the honest answer in an interview. The follow-up is usually about scope:
keep the lock as narrow as the invariant, and never call unknown code (a listener, a callback) while
holding it.

## Many threads count words into one map. How do you make the counting correct?
- id: concurrent-word-count
- level: junior
- tags: concurrenthashmap, merge, atomicity

* [ ] A `HashMap`, with `get` and then `put`
* [ ] A `ConcurrentHashMap`, with `get` and then `put`, since each call is thread safe
* [x] `ConcurrentHashMap.merge(word, 1L, Long::sum)`, which updates one key atomically
* [ ] `Collections.synchronizedMap`, with `get` and then `put`

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
