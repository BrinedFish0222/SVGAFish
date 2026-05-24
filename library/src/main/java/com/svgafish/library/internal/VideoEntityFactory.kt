package com.svgafish.library.internal

import com.svgafish.library.proto.MovieEntity
import com.svgafish.library.manager.SVGACachePathResolver
import com.svgafish.library.model.SVGAVideoModel
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

internal class VideoEntityFactory(
    private val cachePathResolver: SVGACachePathResolver
) {
    private val modelFactory = SVGAVideoModelFactory(cachePathResolver)

    fun decodeFromInputStream(
        inputStream: InputStream,
        cacheKey: String,
        closeInputStream: Boolean,
        alias: String?,
        onComplete: (SVGAVideoModel) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {

            val bytes = readAsBytes(inputStream)
                ?: throw Exception("readAsBytes(inputStream) cause exception")
            if (isZipFile(bytes)) {
                CacheUnzipCoordinator.unzipIfNeeded(cachePathResolver.buildCacheDir(cacheKey)) {
                    ByteArrayInputStream(bytes).use {
                        unzip(it, cacheKey)
                    }
                }
                decodeFromCacheKey(cacheKey, alias, onComplete, onError)
            } else {
                val inflatedBytes = inflate(bytes)
                    ?: throw Exception("inflate(bytes) cause exception")
                val videoModel =
                    createVideoModel(MovieEntity.ADAPTER.decode(inflatedBytes), cacheKey)
                onComplete(videoModel)
            }
        } catch (exception: Exception) {
            onError(exception)
        } finally {
            if (closeInputStream) {
                inputStream.close()
            }
        }
    }

    fun decodeFromCacheKey(
        cacheKey: String,
        alias: String?,
        onComplete: (SVGAVideoModel) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val cacheDir = cachePathResolver.buildCacheDir(cacheKey)
            File(cacheDir, "movie.binary").takeIf { it.isFile }?.let { binaryFile ->
                try {
                    FileInputStream(binaryFile).use {
                        onComplete(createVideoModel(MovieEntity.ADAPTER.decode(it), cacheKey))
                    }
                    return
                } catch (exception: Exception) {
                    cacheDir.delete()
                    binaryFile.delete()
                    throw exception
                }
            }
            File(cacheDir, "movie.spec").takeIf { it.isFile }?.let { jsonFile ->
                try {
                    FileInputStream(jsonFile).use { fileInputStream ->
                        ByteArrayOutputStream().use { byteArrayOutputStream ->
                            val buffer = ByteArray(2048)
                            while (true) {
                                val size = fileInputStream.read(buffer, 0, buffer.size)
                                if (size == -1) {
                                    break
                                }
                                byteArrayOutputStream.write(buffer, 0, size)
                            }
                            val jsonObject = JSONObject(byteArrayOutputStream.toString())
                            onComplete(createVideoModel(jsonObject, cacheKey))
                            return
                        }
                    }
                } catch (exception: Exception) {
                    cacheDir.delete()
                    jsonFile.delete()
                    throw exception
                }
            }
            throw Exception("cache file missing")
        } catch (exception: Exception) {
            onError(exception)
        }
    }

    private fun createVideoModel(entity: MovieEntity, cacheKey: String): SVGAVideoModel {
        return modelFactory.fromMovieEntity(entity, cacheKey)
    }

    private fun createVideoModel(jsonObject: JSONObject, cacheKey: String): SVGAVideoModel {
        return modelFactory.fromJson(jsonObject, cacheKey)
    }

    private fun readAsBytes(inputStream: InputStream): ByteArray? {
        ByteArrayOutputStream().use { byteArrayOutputStream ->
            val byteArray = ByteArray(2048)
            while (true) {
                val count = inputStream.read(byteArray, 0, byteArray.size)
                if (count <= 0) {
                    break
                }
                byteArrayOutputStream.write(byteArray, 0, count)
            }
            return byteArrayOutputStream.toByteArray()
        }
    }

    private fun inflate(byteArray: ByteArray): ByteArray? {
        val inflater = Inflater()
        inflater.setInput(byteArray, 0, byteArray.size)
        val inflatedBytes = ByteArray(2048)
        ByteArrayOutputStream().use { inflatedOutputStream ->
            while (true) {
                val count = inflater.inflate(inflatedBytes, 0, inflatedBytes.size)
                if (count <= 0) {
                    break
                }
                inflatedOutputStream.write(inflatedBytes, 0, count)
            }
            inflater.end()
            return inflatedOutputStream.toByteArray()
        }
    }

    private fun isZipFile(bytes: ByteArray): Boolean {
        return bytes.size > 4 &&
                bytes[0].toInt() == 80 &&
                bytes[1].toInt() == 75 &&
                bytes[2].toInt() == 3 &&
                bytes[3].toInt() == 4
    }

    private fun unzip(inputStream: InputStream, cacheKey: String) {
        val cacheDir = cachePathResolver.buildCacheDir(cacheKey)
        cacheDir.mkdirs()
        try {
            BufferedInputStream(inputStream).use {
                ZipInputStream(it).use { zipInputStream ->
                    while (true) {
                        val zipItem = zipInputStream.nextEntry ?: break
                        if (zipItem.name.contains("../")) {
                            continue
                        }
                        if (zipItem.name.contains("/")) {
                            continue
                        }
                        val file = File(cacheDir, zipItem.name)
                        ensureUnzipSafety(file, cacheDir.absolutePath)
                        FileOutputStream(file).use { fileOutputStream ->
                            val buffer = ByteArray(2048)
                            while (true) {
                                val readBytes = zipInputStream.read(buffer)
                                if (readBytes <= 0) {
                                    break
                                }
                                fileOutputStream.write(buffer, 0, readBytes)
                            }
                        }
                        zipInputStream.closeEntry()
                    }
                }
            }
        } catch (exception: Exception) {
            clearDir(cacheDir)
            cacheDir.delete()
            throw exception
        }
    }

    private fun clearDir(dir: File) {
        runCatching {
            dir.listFiles()?.forEach { file ->
                if (!file.exists()) {
                    return@forEach
                }
                if (file.isDirectory) {
                    clearDir(file)
                }
                file.delete()
            }
        }
    }

    private fun ensureUnzipSafety(outputFile: File, dstDirPath: String) {
        val dstDirCanonicalPath = File(dstDirPath).canonicalPath
        val outputFileCanonicalPath = outputFile.canonicalPath
        if (!outputFileCanonicalPath.startsWith(dstDirCanonicalPath)) {
            throw IOException("Found Zip Path Traversal Vulnerability with $dstDirCanonicalPath")
        }
    }
}

private object CacheUnzipCoordinator {
    private val lock = Any()
    private var isUnzipping = false

    fun unzipIfNeeded(cacheDir: File, unzip: () -> Unit) {
        if (cacheDir.exists() && !isUnzipping) {
            return
        }
        synchronized(lock) {
            if (cacheDir.exists()) {
                return
            }
            isUnzipping = true
            try {
                unzip()
            } finally {
                isUnzipping = false
            }
        }
    }
}
