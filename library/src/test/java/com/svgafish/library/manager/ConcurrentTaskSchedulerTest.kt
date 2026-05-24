package com.svgafish.library.manager

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ConcurrentTaskSchedulerTest {

    @Test
    fun enqueue_runsTask() {
        val scheduler = ConcurrentTaskScheduler(1)
        val latch = CountDownLatch(1)

        scheduler.enqueue { _ ->
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun enqueue_runsWithCancellationSignal() {
        val scheduler = ConcurrentTaskScheduler(1)
        val latch = CountDownLatch(1)
        var capturedSignal: SVGATaskScheduler.CancellationSignal? = null

        scheduler.enqueue { signal ->
            capturedSignal = signal
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS))
        assertNotNull(capturedSignal)
        assertFalse(capturedSignal!!.isCancelled())
    }

    @Test
    fun maxConcurrency_limitsParallelTasks() {
        val maxConcurrency = 2
        val scheduler = ConcurrentTaskScheduler(maxConcurrency)
        val runningCount = AtomicInteger(0)
        val maxObserved = AtomicInteger(0)
        val keepRunning = CountDownLatch(1)
        val startedLatch = CountDownLatch(maxConcurrency)
        val doneLatch = CountDownLatch(maxConcurrency + 1)

        for (i in 1..(maxConcurrency + 1)) {
            scheduler.enqueue { _ ->
                val c = runningCount.incrementAndGet()
                maxObserved.updateAndGet { maxOf(it, c) }
                startedLatch.countDown()
                keepRunning.await()
                runningCount.decrementAndGet()
                doneLatch.countDown()
            }
        }

        assertTrue(startedLatch.await(3, TimeUnit.SECONDS))
        assertEquals(maxConcurrency, maxObserved.get())

        keepRunning.countDown()
        assertTrue(doneLatch.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun cancelQueued_removesBeforeExecution() {
        val scheduler = ConcurrentTaskScheduler(1)
        val firstStarted = CountDownLatch(1)
        val keepRunning = CountDownLatch(1)
        val firstDone = CountDownLatch(1)
        val secondRan = AtomicBoolean(false)

        scheduler.enqueue { _ ->
            firstStarted.countDown()
            keepRunning.await()
            firstDone.countDown()
        }

        assertTrue(firstStarted.await(3, TimeUnit.SECONDS))

        val handle = scheduler.enqueue { _ ->
            secondRan.set(true)
        }
        handle.cancel()

        keepRunning.countDown()
        assertTrue(firstDone.await(3, TimeUnit.SECONDS))
        assertFalse(secondRan.get())
    }

    @Test
    fun cancelRunning_signalsTask() {
        val scheduler = ConcurrentTaskScheduler(1)
        val startedLatch = CountDownLatch(1)
        val keepRunning = CountDownLatch(1)
        var capturedSignal: SVGATaskScheduler.CancellationSignal? = null

        val handle = scheduler.enqueue { signal ->
            capturedSignal = signal
            startedLatch.countDown()
            keepRunning.await()
        }

        assertTrue(startedLatch.await(3, TimeUnit.SECONDS))
        assertNotNull(capturedSignal)
        assertFalse(capturedSignal!!.isCancelled())

        handle.cancel()
        assertTrue(capturedSignal.isCancelled())

        keepRunning.countDown()
    }

    @Test
    fun cancelRunning_firesOnCancelActions() {
        val scheduler = ConcurrentTaskScheduler(1)
        val startedLatch = CountDownLatch(1)
        val onCancelFired = CountDownLatch(1)
        val keepRunning = CountDownLatch(1)

        val handle = scheduler.enqueue { signal ->
            signal.onCancel { onCancelFired.countDown() }
            startedLatch.countDown()
            keepRunning.await()
        }

        assertTrue(startedLatch.await(3, TimeUnit.SECONDS))
        handle.cancel()
        assertTrue(onCancelFired.await(3, TimeUnit.SECONDS))

        keepRunning.countDown()
    }

    @Test
    fun drain_picksUpPendingAfterCompletion() {
        val scheduler = ConcurrentTaskScheduler(1)
        val firstStarted = CountDownLatch(1)
        val keepRunning = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val secondDone = CountDownLatch(1)

        scheduler.enqueue { _ ->
            firstStarted.countDown()
            keepRunning.await()
        }

        assertTrue(firstStarted.await(3, TimeUnit.SECONDS))

        scheduler.enqueue { _ ->
            secondStarted.countDown()
            secondDone.countDown()
        }

        keepRunning.countDown()
        assertTrue(secondStarted.await(3, TimeUnit.SECONDS))
        assertTrue(secondDone.await(3, TimeUnit.SECONDS))
    }

    @Test
    fun cancelNonExistent_doesNotThrow() {
        val scheduler = ConcurrentTaskScheduler(1)
        val doneLatch = CountDownLatch(1)

        val handle = scheduler.enqueue { _ ->
            doneLatch.countDown()
        }

        assertTrue(doneLatch.await(3, TimeUnit.SECONDS))
        // Task has completed; the handle's taskId is no longer present
        handle.cancel() // Should not throw
    }

    @Test
    fun doubleCancel_isIdempotent() {
        val scheduler = ConcurrentTaskScheduler(1)
        val startedLatch = CountDownLatch(1)
        val keepRunning = CountDownLatch(1)

        val handle = scheduler.enqueue { _ ->
            startedLatch.countDown()
            keepRunning.await()
        }

        assertTrue(startedLatch.await(3, TimeUnit.SECONDS))
        handle.cancel()
        handle.cancel() // Second cancel should not throw

        keepRunning.countDown()
    }

    @Test
    fun cancellationSignal_onCancelAfterCancelled_invokesImmediately() {
        val signal = SVGATaskScheduler.CancellationSignal()
        signal.cancel()

        val fired = CountDownLatch(1)
        signal.onCancel { fired.countDown() }

        assertEquals(0L, fired.count)
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructor_rejectsZeroConcurrency() {
        ConcurrentTaskScheduler(0)
    }
}
