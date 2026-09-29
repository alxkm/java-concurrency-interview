# Live coding puzzles

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

## Print numbers 1 to 100 with two threads, one printing odd and one even, in order.
- id: odd-even-printer
- level: mid
- tags: puzzle, wait-notify

* [ ] Two threads with `Thread.sleep(1)` between prints
* [x] One shared monitor, a counter, and each thread waiting in a `while` loop until the parity matches
* [ ] Two `AtomicInteger`s
* [ ] A `CountDownLatch` per number

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

## Implement a bounded blocking queue with `wait` and `notify`.
- id: bounded-buffer
- level: mid
- tags: puzzle, wait-notify, producer-consumer

* [ ] Wrap an `ArrayList` in `synchronized`
* [x] A ring buffer or deque guarded by one monitor, with `while (full) wait()` in put, `while (empty) wait()` in take, and `notifyAll` after each
* [ ] Use `Collections.synchronizedList` and poll in a loop
* [ ] Use a `ConcurrentLinkedQueue` and check the size

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

## Make three tasks run concurrently but print their results in a fixed order.
- id: print-in-order
- level: mid
- tags: puzzle, latches

* [ ] Run them sequentially
* [x] Compute in parallel, then order the output with latches or by index
* [ ] Use thread priorities
* [ ] Synchronize on a shared lock

Two good answers, and picking the right one depends on whether the printing itself must be ordered or
only the final output.

If you only need ordered output, do not coordinate at all: submit the tasks, collect the futures and
print them in order. The work overlaps, the printing is sequential, and there is no shared state.

If each stage must print before the next begins, chain latches: task N counts down latch N, and task
N+1 awaits it first. `CompletableFuture.thenRun` expresses the same chain more readably. The mistake to
avoid is a shared `volatile int turn` with a spin loop, which burns a core to save a latch.

## Implement a rate limiter that allows N operations per second.
- id: rate-limiter
- level: senior
- tags: puzzle, semaphore, scheduling

* [ ] A `synchronized` counter reset by a sleeping thread
* [x] A token bucket: refill permits on a schedule and acquire one per operation, or compute the next allowed instant from the last, under a lock
* [ ] `Thread.sleep(1000 / n)` in every caller
* [ ] A fixed thread pool of size N

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

## Solve the dining philosophers problem without deadlock.
- id: dining-philosophers
- level: mid
- tags: puzzle, deadlock, lock-ordering

* [ ] Give each philosopher a timeout and retry immediately
* [x] Impose a global order on the forks, so one philosopher picks up in the opposite order and no cycle can form; or use a waiter semaphore allowing N-1 to eat
* [ ] Make eating synchronized on one lock
* [ ] Give each philosopher their own pair of forks

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

## Write a thread-safe lazily initialised cache where each key is computed once.
- id: memoizer
- level: senior
- tags: puzzle, chm, futures

* [ ] `synchronized` around a `HashMap` lookup and compute
* [x] `ConcurrentHashMap` with `computeIfAbsent`, or with a `FutureTask` value when the computation is slow enough that holding the bin lock is unacceptable
* [ ] `Collections.synchronizedMap` plus a double check
* [ ] A `ThreadLocal` cache per thread

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

## Implement a simple `CountDownLatch` yourself.
- id: implement-latch
- level: senior
- tags: puzzle, wait-notify, aqs

* [ ] Spin on a volatile counter
* [x] A guarded wait: decrement under a lock and `notifyAll` at zero, with `await` looping while the count is above zero
* [ ] A `Semaphore` with zero permits
* [ ] `Thread.join` on every thread

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

## Write a non-blocking stack.
- id: lock-free-stack
- level: senior
- tags: puzzle, cas, aba

* [ ] `Collections.synchronizedList` with add and remove at the end
* [x] An `AtomicReference` to the head node, with push and pop in CAS retry loops
* [ ] A `ConcurrentLinkedDeque` with a lock
* [ ] An `AtomicInteger` index into an array

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

## Run a task exactly once no matter how many threads call it.
- id: exactly-once
- level: mid
- tags: puzzle, atomics

* [ ] A `volatile boolean done` flag checked before running
* [x] `AtomicBoolean.compareAndSet(false, true)`, or class initialisation, or `synchronized` with a double check
* [ ] A `ThreadLocal` flag
* [ ] `Collections.synchronizedSet` of task names

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

## Two threads, one increments and one prints a shared counter. What could go wrong, and how do you fix it by hand?
- id: increment-and-print
- level: junior
- tags: puzzle, visibility, atomicity

* [ ] Nothing, one writer is always safe
* [x] The reader may never see updates (no happens-before) and `count++` is not atomic; fix both with a lock, or with an `AtomicInteger` which covers both
* [ ] Only ordering is a problem
* [ ] Only if there are more than two threads

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
