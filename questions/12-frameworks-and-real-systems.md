# Concurrency in real systems

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

## Which thread runs a Spring `@Async` method, and what does it lose?
- id: spring-async
- level: mid
- tags: spring, executors

* [ ] The caller's thread, asynchronously
* [x] A thread from the configured task executor, which loses the transaction, the security context and the request scope unless they are propagated
* [ ] A new thread per call, always
* [ ] The common ForkJoinPool

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

## Why does `@Transactional` not work across threads?
- id: transactional-across-threads
- level: mid
- tags: spring, transactions

* [ ] It does, the transaction is shared by the whole request
* [x] The transaction and its connection are held in a `ThreadLocal`, so a new thread starts with no transaction and its own connection
* [ ] Because JDBC connections are not thread safe
* [ ] Only when using JPA

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

## Your service has 200 HTTP threads and a pool of 10 database connections. Where is the bottleneck?
- id: pool-sizing-in-services
- level: senior
- tags: scenario, pools, backpressure

* [ ] The HTTP threads, add more
* [x] The connection pool, and adding HTTP threads only moves the queue to a worse place
* [ ] The garbage collector
* [ ] The database, always

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

## Is a Spring singleton bean thread safe?
- id: singleton-bean-thread-safety
- level: junior
- tags: spring, design

* [ ] Yes, Spring synchronizes bean methods
* [x] Only if it is stateless: one instance serves every concurrent request, so any mutable field is shared
* [ ] Yes, because each request gets its own copy
* [ ] Only with `@Scope("prototype")`

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

## What is the servlet thread model, and what does async servlet processing change?
- id: servlet-thread-model
- level: senior
- tags: servlets, async

* [ ] One thread per application, dispatching all requests
* [x] One container thread per in-flight request; async processing releases that thread while the work continues elsewhere and completes the response later
* [ ] A single event loop, like Node
* [ ] One thread per connection, kept for the connection's lifetime

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

## What does a reactive framework buy you over threads, and what does it cost?
- id: reactive-vs-threads
- level: senior
- tags: reactive, design

* [ ] Faster CPU-bound code
* [x] High concurrency on few threads through non-blocking I/O, at the cost of a programming model where one blocking call poisons the event loop
* [ ] Automatic parallelism with no code change
* [ ] Thread safety by construction

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

## How do you propagate request context (trace id, user) across threads?
- id: context-propagation
- level: mid
- tags: threadlocal, observability

* [ ] Static fields
* [x] Copy it explicitly at the handoff: a `TaskDecorator`, a wrapping executor, or `ScopedValue` with structured concurrency
* [ ] It propagates automatically through executors
* [ ] Pass it in a database table

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

## `HttpClient`, `RestTemplate` or `WebClient`: what concurrency questions do you ask before choosing?
- id: http-client-concurrency
- level: mid
- tags: io, timeouts, design

* [ ] Which one has the nicest API
* [x] Is the instance shareable and thread safe, what is its connection pool limit, and are both connect and read timeouts set
* [ ] Whether it supports HTTP/2
* [ ] How many threads the JVM has

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

## What breaks when you use a parallel stream inside a web request?
- id: parallel-stream-in-request
- level: senior
- tags: parallel-streams, scenario

* [ ] Nothing, it makes the request faster
* [x] Every request shares one common pool sized to the cores, so requests interfere with each other and any blocking call inside starves the whole JVM
* [ ] The stream runs sequentially inside a servlet
* [ ] It breaks the transaction only

A parallel stream uses `ForkJoinPool.commonPool()`, which has `availableProcessors() - 1` threads for
the entire JVM. Under one request it looks fast. Under a hundred concurrent requests, they all compete
for those few threads, and the latency you measured in isolation disappears.

Put a blocking call in the stream and it is worse: the common pool is now held by I/O waits, and every
other parallel operation in the process, including ones inside libraries, stalls behind it. Add the
transaction and context problems from the earlier questions, since the work is now on pool threads.

When the collection is large and the work is genuinely CPU-bound, run it in an executor you own and
size deliberately. When it is not, a sequential stream inside the request thread is both faster and
easier to reason about.

## How does a connection pool interact with your thread pool, and what should you monitor?
- id: connection-pool-interaction
- level: senior
- tags: pools, diagnostics

* [ ] They are independent and need no coordination
* [x] They are nested queues: threads wait for connections, so the pool sizes must be chosen together and both queues need timeouts and metrics
* [ ] The connection pool should always be larger than the thread pool
* [ ] Connection pools make thread pools unnecessary

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

## Why can one slow `@Scheduled` job delay every other scheduled job in a Spring app?
- id: spring-scheduled-single-thread
- level: mid
- tags: spring, scheduling, executors

* [ ] Spring deliberately runs scheduled jobs one at a time, to prevent races
* [x] The default scheduler has a single thread, so a long job holds up every job due meanwhile
* [ ] Each job has its own thread, but they all share one lock
* [ ] The JVM allows only one scheduled task per core

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

## `KafkaConsumer` is not thread safe. How do you process its records in parallel?
- id: kafka-consumer-threads
- level: senior
- tags: kafka, executors, ordering

* [ ] Share one consumer across a thread pool and synchronize every call on it
* [ ] Call `poll()` from many threads, since each call returns different records
* [x] One consumer per thread, up to the partition count, or one poller feeding workers and committing only finished work
* [ ] Turn on auto-commit and process each batch with a parallel stream

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
