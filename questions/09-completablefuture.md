# CompletableFuture and async composition

Where interviews go once "I would use a thread pool" is settled. The trap in this topic is that
everything compiles and runs, and only the thread it runs on and the exception you never see are
wrong.

Runnable: [future](https://github.com/alxkm/java-concurrency-patterns/tree/master/src/main/java/org/alxkm/patterns/future).

```
   supplyAsync(A) --thenApply--> map --thenCompose--> B --thenCombine(C)--> result
        |                         |                                           |
   runs on pool            runs on whichever              exceptionally / handle
                           thread completed A             catch the whole chain

   thenApply   : T -> U            (sync transform)
   thenCompose : T -> CF<U>        (flatMap, avoids CF<CF<U>>)
   thenCombine : CF<U> + BiFunction (join two independent futures)
```

## What does `CompletableFuture` add over `Future`?
- id: cf-vs-future
- level: junior
- tags: completablefuture, basics

* [ ] Nothing, it is a renamed `Future`
* [x] Composition and callbacks: you can chain, combine and handle errors without blocking, and complete it from outside
* [ ] It runs tasks faster
* [ ] It does not need an executor

`Future` gives you `get()` and `isDone()`, which means the only way to use a result is to block on it
or poll for it. Composing two of them means blocking on the first to start the second.

`CompletableFuture` is a promise you can attach continuations to: `thenApply`, `thenCompose`,
`thenCombine`, `allOf`, `exceptionally`, `handle`. Nothing blocks, and the chain runs when the value
arrives. It is also completable from the outside, via `complete(value)` or
`completeExceptionally(e)`, which is what makes it the natural bridge from a callback-based client
library.

## What is the difference between `thenApply` and `thenCompose`?
- id: thenapply-vs-thencompose
- level: mid
- tags: completablefuture, api

* [ ] `thenCompose` runs on a different thread
* [x] `thenApply` maps a value to a value; `thenCompose` flat-maps a value to another future, avoiding a nested `CompletableFuture<CompletableFuture<T>>`
* [ ] `thenApply` is asynchronous, `thenCompose` is not
* [ ] They are the same

It is `map` versus `flatMap`. If your function returns a plain value, use `thenApply`. If it returns
another `CompletableFuture`, which is what any further async call gives you, `thenApply` wraps it and
you end up with `CompletableFuture<CompletableFuture<User>>`. `thenCompose` unwraps it for you.

```java
cf.thenApply(id -> loadUser(id));      // CF<CF<User>>  -- almost always a mistake
cf.thenCompose(id -> loadUser(id));    // CF<User>
```

`thenCombine` is the third member: two independent futures plus a `BiFunction` when you want both
results and neither depends on the other.

## Which thread runs your callback if you use `thenApply` rather than `thenApplyAsync`?
- id: cf-which-thread
- level: senior
- tags: completablefuture, threading

* [ ] Always the common ForkJoinPool
* [x] Whichever thread completed the previous stage, or the calling thread if it was already complete
* [ ] Always the thread that created the future
* [ ] A new thread each time

The non-async variants run on whatever thread happens to make the value available. If the previous
stage is still running, the callback runs on the thread that completes it. If the future was already
complete when you attached the callback, it runs immediately on your thread.

That means a chain of `thenApply` calls can execute on a Netty I/O thread, a client library's callback
thread, or the main thread, depending on timing. Put something slow or blocking there and you have
stalled someone else's event loop. This is the single most common `CompletableFuture` bug in
production.

The `*Async` variants take control back: with no executor argument they use the common
`ForkJoinPool`, which is also wrong for blocking work. Pass your own executor.

## Why should you pass an explicit executor to the `*Async` methods?
- id: cf-common-pool
- level: mid
- tags: completablefuture, executors

* [ ] To make the code more verbose
* [x] Because the default is the common ForkJoinPool, sized to cores minus one and shared with parallel streams, so a blocking task starves everything else
* [ ] Because the default executor is single threaded
* [ ] Because the default is deprecated

`supplyAsync(task)` and `thenApplyAsync(fn)` default to `ForkJoinPool.commonPool()`. It has
`availableProcessors() - 1` threads and is shared with every parallel stream in the JVM, including
those inside libraries you did not write. Block on I/O there and you have taken a scarce global
resource out of circulation.

There is a further trap: on a single-core machine, or in a container that reports one CPU, the common
pool has zero threads and runs everything on the caller, so your "async" code is fully synchronous.

Pass an executor sized for the work: a bounded pool for blocking calls, and ideally a separate one per
downstream dependency so a slow service cannot take the others with it.

## How do exceptions propagate through a chain, and what do `exceptionally`, `handle` and `whenComplete` do?
- id: cf-exceptions
- level: mid
- tags: completablefuture, error-handling

* [ ] They all behave identically
* [x] A failure skips every dependent stage until one that can handle it: `exceptionally` recovers a value, `handle` sees both outcomes and maps them, `whenComplete` observes without changing the result
* [ ] Exceptions are thrown on the calling thread immediately
* [ ] Only `get()` can observe a failure

A stage that throws completes exceptionally, and every downstream `thenApply` is skipped, carrying the
failure along wrapped in a `CompletionException`.

- `exceptionally(fn)` runs only on failure and substitutes a fallback value.
- `handle((value, error) -> ...)` runs either way and produces a new value, so it can convert a failure
  into a result or the reverse.
- `whenComplete((value, error) -> ...)` runs either way for a side effect and leaves the result alone,
  which makes it the right place for logging and cleanup.

The wrapping catches people out: `get()` throws `ExecutionException`, `join()` throws
`CompletionException`, and the cause is your original exception in both cases. And a chain nobody ever
joins, gets or handles swallows its failure completely.

## What does `allOf` return, and how do you collect the results?
- id: cf-allof
- level: mid
- tags: completablefuture, api

* [ ] A `CompletableFuture<List<T>>`
* [x] A `CompletableFuture<Void>` that completes when all inputs do; you collect by joining each input afterwards
* [ ] The first result
* [ ] A stream of results

`allOf` cannot know the types are the same, so it gives you `CompletableFuture<Void>` as a
completion signal only. The idiom:

```java
List<CompletableFuture<Price>> calls = ids.stream().map(this::fetchAsync).toList();

CompletableFuture<List<Price>> all = CompletableFuture
        .allOf(calls.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> calls.stream().map(CompletableFuture::join).toList());
```

The `join` inside `thenApply` never blocks, because every future is already complete by then.

Two things to mention: `allOf` completes exceptionally if any input fails, but it does not cancel the
others, so add a timeout with `orTimeout` or `completeOnTimeout` per call. And `anyOf` returns
`CompletableFuture<Object>`, which is awkward enough that people usually write their own.

## Does cancelling a `CompletableFuture` stop the work?
- id: cf-cancel
- level: senior
- tags: completablefuture, cancellation

* [ ] Yes, it interrupts the running thread
* [x] No: `cancel` completes the future exceptionally but the running task carries on, because there is no thread associated with the future
* [ ] Only if you pass `true`
* [ ] It throws if the task has started

`CompletableFuture.cancel(mayInterruptIfRunning)` ignores its argument. A `CompletableFuture` is a
value holder with no link back to whoever is computing it, so all cancelling does is complete it with a
`CancellationException` and release the dependent stages. The `supplyAsync` task keeps running to
completion, holding its thread and its connection.

This is a real difference from `FutureTask`, where `cancel(true)` does interrupt the worker. If you
need genuine cancellation you have to arrange it yourself: keep the `Future` returned by the executor,
check a flag inside the task, or use `StructuredTaskScope`, which was designed to make cancellation
propagate.

## How do you add a timeout to a `CompletableFuture`?
- id: cf-timeout
- level: mid
- tags: completablefuture, api

* [ ] `get(timeout)` is the only option and it is fine
* [x] `orTimeout(n, unit)` fails the future, `completeOnTimeout(value, n, unit)` supplies a fallback, both without blocking a thread
* [ ] `Thread.sleep` in a `whenComplete`
* [ ] Timeouts are not supported

`get(timeout, unit)` bounds how long *you* wait, but it blocks a thread to do it and leaves the task
running. Since Java 9 there are two non-blocking alternatives:

```java
fetchPrice()
    .completeOnTimeout(Price.UNKNOWN, 200, MILLISECONDS)   // fall back
    .orTimeout(1, SECONDS);                                // or fail hard
```

Both are scheduled on an internal single-threaded timer, so they cost no thread while waiting.
Remember the previous question: the timeout completes the future, it does not stop the underlying
call, so a slow downstream request still occupies its connection until it finishes.

## What is the difference between `join()` and `get()`?
- id: cf-join-vs-get
- level: junior
- tags: completablefuture, api

* [ ] `join()` does not block
* [x] `join()` throws the unchecked `CompletionException`; `get()` is checked and takes a timeout
* [ ] `get()` is deprecated
* [ ] `join()` only works inside a stream

Functionally both block until the result is available. The difference is the exceptions: `get()` is
`Future`'s method with checked exceptions, and `join()` is unchecked, which is why it works inside
lambdas and stream pipelines where a checked exception would not compile.

`get()` is also the one with a timeout overload, and the one that responds to interruption. Use
`join()` inside a chain where the value is already known to be present, and `get(timeout)` at the edge
of your system, where you genuinely have to wait.

## When is `CompletableFuture` the wrong tool now that virtual threads exist?
- id: cf-vs-virtual-threads
- level: senior
- tags: completablefuture, virtual-threads, design

* [ ] Never, async is always better
* [x] When the only reason for it was to avoid blocking a pooled thread: on virtual threads, straight-line blocking code is cheaper to read and to debug
* [ ] When you have more than three stages
* [ ] When the tasks are CPU-bound

Async composition exists because blocking a platform thread is expensive. Virtual threads remove that
premise: a blocked virtual thread unmounts from its carrier and costs almost nothing, so plain
sequential code with plain `try`/`catch`, a readable stack trace and a debugger that works is once
again affordable.

`CompletableFuture` still earns its place for genuine fan-out and fan-in, for bridging callback-based
APIs, and for composing results that arrive from elsewhere. But "we used it so we would not block"
should now be re-examined, and structured concurrency covers the fan-out case with better
cancellation.

The nuanced answer to give: async is about the shape of the dependencies, not about the cost of a
thread, and the cost of a thread was the only reason most codebases adopted it.
