package com.svgafish.library.internal

import android.os.Handler
import android.os.Looper
import com.svgafish.library.SVGAResourceManager
import com.svgafish.library.manager.SVGATaskScheduler
import com.svgafish.library.session.SVGAVideoSession
import java.util.LinkedHashMap

internal data class RequestWaiter(
    val callback: SVGAResourceManager.LoadCompletion?
)

internal typealias CancelHandle = SVGATaskScheduler.CancelHandle

internal interface RequestCallbacks {
    fun onComplete(session: SVGAVideoSession)
    fun onError(exception: Exception)
}

internal interface RequestEventDispatcher {
    fun dispatchComplete(waiters: List<RequestWaiter>, session: SVGAVideoSession)
    fun dispatchError(waiters: List<RequestWaiter>, exception: Exception)
}

internal class MainThreadRequestEventDispatcher : RequestEventDispatcher {
    private val handler = Handler(Looper.getMainLooper())

    override fun dispatchComplete(
        waiters: List<RequestWaiter>,
        session: SVGAVideoSession
    ) {
        handler.post {
            waiters.forEach { it.callback?.onComplete(session) }
        }
    }

    override fun dispatchError(
        waiters: List<RequestWaiter>,
        exception: Exception
    ) {
        exception.printStackTrace()
        handler.post {
            waiters.forEach { it.callback?.onError() }
        }
    }
}

internal class RequestCoordinator(
    private val eventDispatcher: RequestEventDispatcher
) {
    private val lock = Any()
    private val inProgressRequests = HashMap<String, InProgressRequest>()
    private var nextWaiterId = 0

    fun startOrJoin(
        requestKey: String,
        waiter: RequestWaiter,
        start: (RequestCallbacks) -> CancelHandle?
    ): CancelHandle? {
        val waiterId: Int
        var shouldStartRequest = false

        synchronized(lock) {
            val request = inProgressRequests[requestKey]
            if (request == null) {
                waiterId = nextWaiterId++
                val newRequest = InProgressRequest()
                newRequest.waiters[waiterId] = waiter
                inProgressRequests[requestKey] = newRequest
                shouldStartRequest = true
            } else {
                waiterId = nextWaiterId++
                request.waiters[waiterId] = waiter
            }
        }

        val cancelWaiter = CancelHandle { cancelWaiter(requestKey, waiterId) }
        if (!shouldStartRequest) {
            return cancelWaiter
        }

        val requestCallbacks = object : RequestCallbacks {
            override fun onComplete(session: SVGAVideoSession) {
                dispatchComplete(requestKey, session)
            }

            override fun onError(exception: Exception) {
                dispatchError(requestKey, exception)
            }
        }

        val cancelUnderlying = try {
            start(requestCallbacks)
        } catch (exception: Exception) {
            requestCallbacks.onError(exception)
            null
        }

        synchronized(lock) {
            inProgressRequests[requestKey]?.cancelUnderlying = cancelUnderlying
        }

        return if (cancelUnderlying != null) cancelWaiter else null
    }

    private fun dispatchComplete(requestKey: String, session: SVGAVideoSession) {
        val request = synchronized(lock) {
            inProgressRequests.remove(requestKey)
        } ?: return
        eventDispatcher.dispatchComplete(request.waiters.values.toList(), session)
    }

    private fun dispatchError(requestKey: String, exception: Exception) {
        val request = synchronized(lock) {
            inProgressRequests.remove(requestKey)
        } ?: return
        eventDispatcher.dispatchError(request.waiters.values.toList(), exception)
    }

    private fun cancelWaiter(requestKey: String, waiterId: Int) {
        var cancelUnderlying: CancelHandle? = null
        synchronized(lock) {
            val request = inProgressRequests[requestKey] ?: return
            request.waiters.remove(waiterId)
            if (request.waiters.isEmpty()) {
                cancelUnderlying = request.cancelUnderlying
                inProgressRequests.remove(requestKey)
            }
        }
        cancelUnderlying?.cancel()
    }

    private class InProgressRequest {
        val waiters = LinkedHashMap<Int, RequestWaiter>()
        var cancelUnderlying: CancelHandle? = null
    }
}
