# Testing concurrent code

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

## Why is `Thread.sleep` in a test a bug rather than a delay?
- id: sleep-in-tests
- level: mid
- tags: testing, flakiness

* [ ] It is fine if the sleep is long enough
* [x] It encodes a guess about timing: too short and the test is flaky, too long and the suite crawls, and it never proves the condition was met
* [ ] It does not compile in JUnit 5
* [ ] It only fails on Windows

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

## How do you write a test that actually creates contention?
- id: testing-contention
- level: mid
- tags: testing

* [ ] Start two threads and hope
* [x] Start N threads that all block on one barrier, release them together, then join with a timeout and assert the invariant
* [ ] Run the same test a thousand times
* [ ] Use a single thread and a mock

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

## Why can a unit test not reliably catch a visibility or reordering bug?
- id: tests-cannot-catch-reordering
- level: senior
- tags: testing, memory-model, jcstress

* [ ] Because JUnit runs tests sequentially
* [x] Because the synchronisation used to line the threads up is itself a memory barrier that drains the store buffer producing the effect
* [ ] Because the JIT does not run in tests
* [ ] It can, with enough iterations

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

## What is jcstress and when do you reach for it?
- id: jcstress
- level: senior
- tags: testing, jcstress, memory-model

* [ ] A load testing tool
* [x] The OpenJDK harness that runs unsynchronised actors millions of times over
* [ ] A JUnit extension for parallel tests
* [ ] A static analyser

jcstress writes the test for you in a shape ordinary code cannot: two `@Actor` methods run with no
synchronisation between them, the harness repeats them for millions of samples, shuffles JIT decisions
between forks, and records the frequency of every observed result. Outcomes are labelled `ACCEPTABLE`,
`INTERESTING` or `FORBIDDEN`, so the test fails if a guarantee is ever violated.

Use it to answer questions of the form "can this be observed at all": does this idiom need `volatile`,
is this publication safe, can this field be seen at its default. Its output is also the most persuasive
artefact you can put in a code review, because it replaces an argument with a frequency table.

What it is not: a test for your business logic. It is slow, deliberately, and belongs in a separate
source set that CI compiles but does not run on every build.

## How do you test that something is *not* possible, such as a deadlock?
- id: testing-deadlock
- level: senior
- tags: testing, deadlock

* [ ] Run it and see if it hangs
* [x] Bound every wait so a hang fails, and report `ThreadMXBean.findDeadlockedThreads`
* [ ] Use `assertDoesNotThrow`
* [ ] It cannot be tested

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

## What makes a concurrency test flaky, and how do you deal with flakiness?
- id: flaky-tests
- level: mid
- tags: testing, ci

* [ ] Flaky tests should be annotated `@Disabled`
* [x] Timing assumptions and shared state; fix the assumption instead of retrying the test
* [ ] Flakiness is inherent and unavoidable
* [ ] Running tests in parallel

The usual causes are all fixable: sleeps standing in for conditions, assertions on how long something
took, shared static state or singletons leaking across tests, ports and temp files that collide, and
pools not shut down so threads from one test run during another.

The cultural part is the real answer. An intermittent failure in a concurrency test is evidence of a
race somewhere, in the test or in the code, and adding a retry annotation discards that evidence. Quarantine
it if it blocks the build, but keep it running and keep the failure output, including a thread dump.

Useful hygiene: make each test create and shut down its own executor, give threads names that identify
the test, and run the suite occasionally with more threads than cores to change the interleavings.

## How would you benchmark two concurrent implementations honestly?
- id: benchmarking-concurrency
- level: senior
- tags: testing, jmh, performance

* [ ] Time a loop with `System.currentTimeMillis`
* [x] Use JMH: warm up the JIT, several forks, report the error bars, and measure under the contention level you actually care about
* [ ] Run it in production and watch the dashboard
* [ ] Count the lines of bytecode

A hand-rolled timing loop measures the interpreter, then the C1 compiler, then dead code the JIT
removed because the result was unused. JMH exists to remove those: warmup iterations, blackholes for
results, several forks to shuffle JIT decisions, and a reported error interval.

For concurrency add the parts specific to it: `@Threads` or a thread group per role to set the
contention level, `@State(Scope.Benchmark)` for the shared object, and separate numbers for one thread
and for N, because the interesting result is the shape of the curve rather than a single figure.

Say the honest part too: benchmark results are workload-specific, and two claims in the sibling
repository's README turned out to be wrong once measured, which is why they now carry numbers.

## Can you unit test code that uses the current time or a scheduler?
- id: testing-time
- level: mid
- tags: testing, design

* [ ] No, you must sleep
* [x] Yes, by injecting the clock and the executor: a fixed `Clock` and a deterministic or manually driven executor make the behaviour reproducible
* [ ] Only with a mocking framework that rewrites bytecode
* [ ] Only in integration tests

Hard-coded `System.currentTimeMillis()` and a privately constructed `ScheduledExecutorService` are
what make a class untestable. Inject both and the tests become ordinary:

- A `java.time.Clock` you can fix or advance, so expiry and timeout logic is exact.
- An `Executor` you can substitute with `Runnable::run` to make everything synchronous, or with a
  deterministic scheduler that runs queued tasks when you tell it to.

This is a design answer as much as a testing one: the same injection that makes the class testable also
makes it configurable and lets the application own thread lifecycle in one place. If a class creates
its own threads, nobody can control them.

## What static analysis helps with concurrency?
- id: static-analysis
- level: mid
- tags: testing, tooling

* [ ] None, concurrency is a runtime property
* [x] ErrorProne and SpotBugs catch a useful class of mistakes: `@GuardedBy` violations, unsynchronised access next to synchronised access, and known-bad idioms
* [ ] Checkstyle
* [ ] The compiler warns about all of them

The compiler says nothing about thread safety, but analysers catch a real subset. SpotBugs has a
concurrency category: inconsistent synchronisation (a field synchronised on 90% of accesses),
`wait` outside a loop, naked `notify`, a lock not released on every path, locking on a boxed value,
double-checked locking without volatile. ErrorProne enforces `@GuardedBy` properly and flags a family of
misuse patterns at compile time.

They cannot prove correctness and they produce false positives, so they belong as a warning gate rather
than a build-breaker for existing code. Their real value is on new code, where they catch the mistake
before it reaches review.

## What would you add to a code review checklist for concurrent code?
- id: review-checklist
- level: senior
- tags: testing, review, design

* [ ] That every shared method is synchronized
* [x] For every mutable field, name the lock or edge that protects it; check compound actions, alien calls under locks, interrupt handling, timeouts and shutdown
* [ ] That the code uses `java.util.concurrent`
* [ ] That there are no `synchronized` keywords

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

## What is the difference between `assertTimeout` and `assertTimeoutPreemptively` in JUnit 5?
- id: junit-timeouts
- level: junior
- tags: junit, timeouts, deadlock

* [ ] None, one is an alias for the other
* [ ] `assertTimeout` interrupts the code when the deadline passes
* [x] `assertTimeout` waits for the code and fails if it was slow; the other gives up at the deadline
* [ ] `assertTimeoutPreemptively` retries the code until it passes

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
