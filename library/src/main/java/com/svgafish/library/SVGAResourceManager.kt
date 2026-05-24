package com.svgafish.library

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.svgafish.library.internal.MainThreadRequestEventDispatcher
import com.svgafish.library.internal.RequestCoordinator
import com.svgafish.library.internal.RequestWaiter
import com.svgafish.library.internal.DefaultSVGAResourceResolver
import com.svgafish.library.internal.VideoEntityFactory
import com.svgafish.library.manager.ConcurrentTaskScheduler
import com.svgafish.library.manager.Md5CachePathResolver
import com.svgafish.library.manager.MemorySVGAResourceStore
import com.svgafish.library.manager.SVGACachePathResolver
import com.svgafish.library.manager.SVGAResourceStore
import com.svgafish.library.manager.SVGATaskScheduler
import com.svgafish.library.model.SVGAVideoModel
import com.svgafish.library.session.SVGAVideoSession
import java.io.InputStream
import java.io.File
import java.net.URL

class SVGAResourceManager private constructor(
    private val cachePathResolver: SVGACachePathResolver,
    private val taskScheduler: SVGATaskScheduler,
    private val resourceStore: SVGAResourceStore
) {
    private val callbackDispatcher = MainThreadRequestEventDispatcher()
    private val videoEntityFactory = VideoEntityFactory(cachePathResolver)
    private val resourceResolver = DefaultSVGAResourceResolver(
        videoEntityFactory = videoEntityFactory, cachePathResolver = cachePathResolver
    )
    private val requestCoordinator = RequestCoordinator(callbackDispatcher)
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun prepareSessionOnMain(
        videoModel: SVGAVideoModel,
        onReady: (SVGAVideoSession) -> Unit
    ) {
        mainHandler.post {
            val session = createSession(videoModel)
            session.prepare(callback = {
                onReady(session)
            })
        }
    }

    interface LoadCompletion {
        fun onComplete(session: SVGAVideoSession)
        fun onError()
    }

    fun interface LoadHandle {
        fun cancel()
    }

    companion object {
        private const val DEFAULT_MAX_CONCURRENCY = 3

        fun create(context: Context): SVGAResourceManager {
            return create(context.cacheDir)
        }

        fun create(cacheDir: File): SVGAResourceManager {
            return create(
                cacheDir = cacheDir,
                maxConcurrency = DEFAULT_MAX_CONCURRENCY,
                resourceStore = MemorySVGAResourceStore()
            )
        }

        fun create(
            cacheDir: File,
            maxConcurrency: Int = DEFAULT_MAX_CONCURRENCY,
            resourceStore: SVGAResourceStore = MemorySVGAResourceStore()
        ): SVGAResourceManager {
            return SVGAResourceManager(
                cachePathResolver = Md5CachePathResolver(cacheDir),
                taskScheduler = ConcurrentTaskScheduler(maxConcurrency),
                resourceStore = resourceStore
            )
        }
    }

    fun loadFromURL(
        url: URL, callback: LoadCompletion?
    ): LoadHandle? {
        val alias = url.toString()
        val requestKey = cachePathResolver.buildCacheKey(url)

        resourceStore.get(requestKey)?.let { videoModel ->
            prepareSessionOnMain(videoModel) { session ->
                callbackDispatcher.dispatchComplete(
                    waiters = listOf(RequestWaiter(callback)),
                    session = session
                )
            }
            return null
        }

        return requestCoordinator.startOrJoin(
            requestKey = requestKey, waiter = RequestWaiter(callback)
        ) { requestCallbacks ->
            taskScheduler.enqueue { cancellationSignal ->
                resourceResolver.loadFromUrl(
                    url = url,
                    requestKey = requestKey,
                    alias = alias,
                    cancellationSignal = cancellationSignal,
                    onComplete = { videoModel ->
                        resourceStore.put(requestKey, videoModel)
                        prepareSessionOnMain(videoModel) { session ->
                            requestCallbacks.onComplete(session)
                        }
                    },
                    onError = requestCallbacks::onError
                )
            }
        }?.let { cancel ->
            LoadHandle { cancel.cancel() }
        }
    }

    fun loadFromAssets(
        name: String,
        inputStreamProvider: () -> InputStream,
        callback: LoadCompletion?
    ) {
        val requestKey = cachePathResolver.buildCacheKey("assets:$name")

        resourceStore.get(requestKey)?.let { videoModel ->
            prepareSessionOnMain(videoModel) { session ->
                callbackDispatcher.dispatchComplete(
                    waiters = listOf(RequestWaiter(callback)),
                    session = session
                )
            }
            return
        }

        taskScheduler.enqueue {
            try {
                inputStreamProvider().use { inputStream ->
                    resourceResolver.loadFromInputStream(
                        inputStream = inputStream,
                        requestKey = requestKey,
                        alias = name,
                        onComplete = { videoModel ->
                            resourceStore.put(requestKey, videoModel)
                            prepareSessionOnMain(videoModel) { session ->
                                callbackDispatcher.dispatchComplete(
                                    waiters = listOf(RequestWaiter(callback)),
                                    session = session
                                )
                            }
                        },
                        onError = { exception ->
                            callbackDispatcher.dispatchError(
                                waiters = listOf(RequestWaiter(callback)),
                                exception = exception
                            )
                        }
                    )
                }
            } catch (exception: Exception) {
                callbackDispatcher.dispatchError(
                    waiters = listOf(RequestWaiter(callback)),
                    exception = exception
                )
            }
        }
    }

    internal fun createSession(videoModel: SVGAVideoModel): SVGAVideoSession {
        val activeModel = resourceStore.acquire(videoModel.requestKey, videoModel)
        return SVGAVideoSession(
            model = activeModel,
            onClose = { resourceStore.release(activeModel.requestKey) },
            onPlaybackFinished = { resourceStore.markPlaybackFinished(activeModel.requestKey) }
        )
    }

    fun clearResources() {
        resourceStore.clear()
    }
}
