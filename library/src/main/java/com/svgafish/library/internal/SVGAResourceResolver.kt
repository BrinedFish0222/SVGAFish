package com.svgafish.library.internal

import com.svgafish.library.manager.SVGACachePathResolver
import com.svgafish.library.manager.SVGATaskScheduler
import com.svgafish.library.model.SVGAVideoModel
import java.io.InputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal interface SVGAResourceResolver {

    fun loadFromUrl(
        url: URL,
        requestKey: String,
        alias: String,
        cancellationSignal: SVGATaskScheduler.CancellationSignal,
        onComplete: (SVGAVideoModel) -> Unit,
        onError: (Exception) -> Unit
    )

    fun loadFromInputStream(
        inputStream: InputStream,
        requestKey: String,
        alias: String,
        onComplete: (SVGAVideoModel) -> Unit,
        onError: (Exception) -> Unit
    )
}

internal class DefaultSVGAResourceResolver(
    private val videoEntityFactory: VideoEntityFactory,
    private val cachePathResolver: SVGACachePathResolver
) : SVGAResourceResolver {
    private val fileDownloader = FileDownloader()

    override fun loadFromUrl(
        url: URL,
        requestKey: String,
        alias: String,
        cancellationSignal: SVGATaskScheduler.CancellationSignal,
        onComplete: (SVGAVideoModel) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (cancellationSignal.isCancelled()) {
            return
        }

        if (cachePathResolver.hasSourceCache(requestKey)) {
            try {
                FileInputStream(cachePathResolver.buildSourceFile(requestKey)).use { inputStream ->
                    videoEntityFactory.decodeFromInputStream(
                        inputStream = inputStream,
                        cacheKey = requestKey,
                        closeInputStream = false,
                        alias = alias,
                        onComplete = onComplete,
                        onError = onError
                    )
                }
            } catch (exception: Exception) {
                onError(exception)
            }
            return
        }

        if (cachePathResolver.hasDecodedCache(requestKey)) {
            try {
                videoEntityFactory.decodeFromCacheKey(
                    cacheKey = requestKey,
                    alias = alias,
                    onComplete = onComplete,
                    onError = onError
                )
            } catch (exception: Exception) {
                onError(exception)
            }
            return
        }

        fileDownloader.resume(
            url = url,
            cacheDir = cachePathResolver.buildCacheDir(requestKey),
            sourceFile = cachePathResolver.buildSourceFile(requestKey),
            sourceTempFile = cachePathResolver.buildSourceTempFile(requestKey),
            cancellationSignal = cancellationSignal,
            complete = { sourceFile ->
                if (cancellationSignal.isCancelled()) {
                    return@resume
                }
                FileInputStream(sourceFile).use { inputStream ->
                    videoEntityFactory.decodeFromInputStream(
                        inputStream = inputStream,
                        cacheKey = requestKey,
                        closeInputStream = false,
                        alias = alias,
                        onComplete = onComplete,
                        onError = onError
                    )
                }
            },
            failure = { exception ->
                if (!cancellationSignal.isCancelled()) {
                    onError(exception)
                }
            }
        )
    }

    override fun loadFromInputStream(
        inputStream: InputStream,
        requestKey: String,
        alias: String,
        onComplete: (SVGAVideoModel) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (cachePathResolver.hasDecodedCache(requestKey)) {
            try {
                videoEntityFactory.decodeFromCacheKey(
                    cacheKey = requestKey,
                    alias = alias,
                    onComplete = onComplete,
                    onError = onError
                )
            } catch (exception: Exception) {
                onError(exception)
            }
            return
        }

        try {
            videoEntityFactory.decodeFromInputStream(
                inputStream = inputStream,
                cacheKey = requestKey,
                closeInputStream = false,
                alias = alias,
                onComplete = onComplete,
                onError = onError
            )
        } catch (exception: Exception) {
            onError(exception)
        }
    }

    private class FileDownloader {
        fun resume(
            url: URL,
            cacheDir: File,
            sourceFile: File,
            sourceTempFile: File,
            cancellationSignal: SVGATaskScheduler.CancellationSignal,
            complete: (sourceFile: File) -> Unit,
            failure: (e: Exception) -> Unit
        ) {
            try {
                (url.openConnection() as? HttpURLConnection)?.let { connection ->
                    connection.connectTimeout = 20 * 1000
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Connection", "close")
                    cancellationSignal.onCancel(connection::disconnect)
                    connection.connect()
                    connection.inputStream.use { inputStream ->
                        cacheDir.mkdirs()
                        if (sourceTempFile.exists()) {
                            sourceTempFile.delete()
                        }
                        FileOutputStream(sourceTempFile).use { outputStream ->
                            val buffer = ByteArray(4096)
                            while (true) {
                                if (cancellationSignal.isCancelled()) {
                                    sourceTempFile.delete()
                                    return
                                }
                                val count = inputStream.read(buffer, 0, buffer.size)
                                if (count == -1) {
                                    break
                                }
                                outputStream.write(buffer, 0, count)
                            }
                            outputStream.flush()
                            if (cancellationSignal.isCancelled()) {
                                sourceTempFile.delete()
                                return
                            }
                        }
                        if (cancellationSignal.isCancelled()) {
                            sourceTempFile.delete()
                            return
                        }
                        if (sourceFile.exists() && !sourceFile.delete()) {
                            sourceTempFile.delete()
                            throw IllegalStateException("Failed to replace cached source file")
                        }
                        if (!sourceTempFile.renameTo(sourceFile)) {
                            sourceTempFile.delete()
                            throw IllegalStateException("Failed to rename temp source file")
                        }
                        if (cancellationSignal.isCancelled()) {
                            return
                        }
                        complete(sourceFile)
                    }
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
                sourceTempFile.delete()
                if (!cancellationSignal.isCancelled()) {
                    failure(exception)
                }
            }
        }
    }
}
