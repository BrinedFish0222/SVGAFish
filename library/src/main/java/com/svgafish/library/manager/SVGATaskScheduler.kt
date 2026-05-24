package com.svgafish.library.manager

import java.util.ArrayDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 任务调度器：提交可取消的异步任务。
 */
interface SVGATaskScheduler {

    fun enqueue(task: (CancellationSignal) -> Unit): CancelHandle

    class CancellationSignal {
        private val lock = Any()
        private var cancelled = false
        private val cancelActions = mutableListOf<() -> Unit>()

        fun isCancelled(): Boolean = synchronized(lock) { cancelled }

        fun onCancel(action: () -> Unit) {
            val invokeNow = synchronized(lock) {
                if (cancelled) {
                    true
                } else {
                    cancelActions.add(action)
                    false
                }
            }
            if (invokeNow) {
                action()
            }
        }

        fun cancel() {
            val actions = synchronized(lock) {
                if (cancelled) {
                    return
                }
                cancelled = true
                cancelActions.toList().also { cancelActions.clear() }
            }
            actions.forEach { it() }
        }
    }

    fun interface CancelHandle {
        fun cancel()
    }
}

internal class ConcurrentTaskScheduler(
    maxConcurrency: Int
) : SVGATaskScheduler {
    init {
        require(maxConcurrency >= 1) { "maxConcurrency must be at least 1" }
    }

    private val lock = Any()
    private val pendingTasks = ArrayDeque<QueuedTask>()
    private val runningTasks = LinkedHashMap<Int, RunningTask>()
    private val nextTaskId = AtomicInteger(0)
    private val threadNum = AtomicInteger(0)
    private val executor: ExecutorService = Executors.newFixedThreadPool(maxConcurrency) { runnable ->
        Thread(runnable, "SVGA-TaskScheduler-${threadNum.getAndIncrement()}")
    }

    override fun enqueue(task: (SVGATaskScheduler.CancellationSignal) -> Unit): SVGATaskScheduler.CancelHandle {
        val taskId = nextTaskId.getAndIncrement()
        val signal = SVGATaskScheduler.CancellationSignal()
        val queuedTask = QueuedTask(taskId = taskId, signal = signal, task = task)
        synchronized(lock) {
            pendingTasks.addLast(queuedTask)
        }
        drain()
        return SVGATaskScheduler.CancelHandle { cancel(taskId) }
    }

    private fun drain() {
        while (true) {
            val queuedTask = synchronized(lock) {
                if (runningTasks.size >= maximumPoolSize() || pendingTasks.isEmpty()) {
                    return
                }
                val nextTask = pendingTasks.removeFirst()
                if (nextTask.signal.isCancelled()) {
                    null
                } else {
                    runningTasks[nextTask.taskId] = RunningTask(nextTask.signal)
                    nextTask
                }
            } ?: continue

            executor.execute {
                try {
                    if (!queuedTask.signal.isCancelled()) {
                        queuedTask.task(queuedTask.signal)
                    }
                } finally {
                    synchronized(lock) {
                        runningTasks.remove(queuedTask.taskId)
                    }
                    drain()
                }
            }
        }
    }

    private fun cancel(taskId: Int) {
        var runningSignal: SVGATaskScheduler.CancellationSignal? = null
        synchronized(lock) {
            val iterator = pendingTasks.iterator()
            while (iterator.hasNext()) {
                val task = iterator.next()
                if (task.taskId == taskId) {
                    iterator.remove()
                    task.signal.cancel()
                    return
                }
            }
            runningSignal = runningTasks[taskId]?.signal
        }
        runningSignal?.cancel()
    }

    private fun maximumPoolSize(): Int = (executor as java.util.concurrent.ThreadPoolExecutor).maximumPoolSize

    private data class QueuedTask(
        val taskId: Int,
        val signal: SVGATaskScheduler.CancellationSignal,
        val task: (SVGATaskScheduler.CancellationSignal) -> Unit
    )

    private data class RunningTask(
        val signal: SVGATaskScheduler.CancellationSignal
    )
}
