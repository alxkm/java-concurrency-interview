package org.alxkm.interview;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * This file is content, not ceremony.
 *
 * <p>"How would you test that?" is a question this repository asks in
 * {@code questions/13-testing-concurrency.md}, and an answer is more convincing as a test that runs
 * than as a paragraph. Each method below is one of the techniques from that topic, written the way it
 * should be written: no {@code Thread.sleep} standing in for a condition, every wait bounded, and a
 * failure that says what went wrong rather than hanging the build.
 */
class HowToTestConcurrencyTest {

    /**
     * Wait for a condition, not for the clock.
     *
     * <p>This is the helper that replaces {@code Thread.sleep(200)} in a test. It returns as soon as
     * the condition holds, so the suite stays fast, and it fails with a message instead of leaving a
     * later assertion to fail mysteriously.
     */
    static void await(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within " + timeout);
            }
            Thread.sleep(1);
        }
    }

    /**
     * Start every thread, then release them together.
     *
     * <p>Threads started in a loop tend to run one after another, because starting a thread costs more
     * than the body of a small test. A starting gate makes them collide, which is the only way a test
     * has a chance of catching a lost update.
     */
    private static void runConcurrently(int threads, Runnable body) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        body.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(finished.await(30, SECONDS), "threads did not finish, something is stuck");
        }
    }

    @Test
    @DisplayName("a barrier-released race: AtomicInteger keeps every increment")
    void atomicIncrementLosesNothing() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        int threads = 8;
        int perThread = 50_000;

        runConcurrently(threads, () -> {
            for (int i = 0; i < perThread; i++) {
                counter.incrementAndGet();
            }
        });

        // Deterministic: this either passes always or the JDK is broken. Contrast with the next test,
        // where the interesting outcome is the one that cannot be asserted.
        assertEquals(threads * perThread, counter.get());
    }

    @Test
    @DisplayName("the same race on a plain int usually loses updates, and cannot be asserted to")
    void plainIncrementIsNotAtomic() throws InterruptedException {
        var counter = new Object() {
            int value;
        };
        int threads = 8;
        int perThread = 50_000;

        runConcurrently(threads, () -> {
            for (int i = 0; i < perThread; i++) {
                counter.value++;          // read, add, write: three steps, no atomicity
            }
        });

        // What can honestly be asserted is only the upper bound. The count is almost always far below
        // it, but "almost always" is not a test, and asserting the failure would make this suite flaky
        // on a single-core machine. That gap is the lesson: a passing concurrency test is weak
        // evidence, which is why the memory model questions in this repository point at jcstress.
        assertTrue(counter.value <= threads * perThread);
        assertTrue(counter.value > 0);
    }

    @Test
    @DisplayName("wait for the condition the code signals, never for a duration")
    void awaitInsteadOfSleep() throws InterruptedException {
        AtomicInteger processed = new AtomicInteger();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                pool.execute(processed::incrementAndGet);
            }
            await(Duration.ofSeconds(5), () -> processed.get() == 100);
        }
        assertEquals(100, processed.get());
    }

    @Test
    @DisplayName("a hang becomes a failure with a diagnosis, not a stuck build")
    void deadlockIsReportedRatherThanHung() throws InterruptedException {
        Object first = new Object();
        Object second = new Object();
        CountDownLatch bothHoldOne = new CountDownLatch(2);

        // Two threads taking the same two locks in opposite orders: the textbook cycle.
        startDaemon("deadlock-a", first, second, bothHoldOne);
        startDaemon("deadlock-b", second, first, bothHoldOne);
        assertTrue(bothHoldOne.await(5, SECONDS), "threads never reached the second lock");

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        await(Duration.ofSeconds(5), () -> threads.findDeadlockedThreads() != null);

        long[] deadlocked = threads.findDeadlockedThreads();
        assertNotNull(deadlocked, "expected the JVM to detect the cycle");
        assertEquals(2, deadlocked.length,
                () -> "unexpected cycle: " + Arrays.toString(threads.getThreadInfo(deadlocked)));

        // The message is what you would read in a CI log, and it names both threads and both locks.
        // The two threads above stay deadlocked forever, which is exactly the point: they are daemons,
        // so the JVM can still exit, and nothing short of a restart would recover them.
        assertTrue(threads.getThreadInfo(deadlocked, true, true)[0].getLockInfo() != null,
                "a deadlocked thread should report the lock it is waiting for");
    }

    private static void startDaemon(String name, Object outer, Object inner, CountDownLatch holding) {
        Thread thread = new Thread(() -> {
            synchronized (outer) {
                holding.countDown();
                try {
                    // Give the other thread time to take its first lock, otherwise one thread may
                    // acquire both and no cycle forms. A sleep is acceptable here because it makes the
                    // bad interleaving likely rather than standing in for a condition.
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                synchronized (inner) {
                    throw new IllegalStateException("unreachable: this is a deadlock by construction");
                }
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
    }
}
