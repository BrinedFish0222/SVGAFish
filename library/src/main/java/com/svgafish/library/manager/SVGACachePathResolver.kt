package com.svgafish.library.manager

import java.io.File
import java.net.URL
import java.security.MessageDigest

/**
 * SVGA 磁盘缓存路径解析与命中判断。
 *
 * 通过接口抽象路径生成策略，方便外部扩展不同的缓存目录布局或 key 生成规则。
 */
interface SVGACachePathResolver {

    /** 根据字符串生成缓存 key */
    fun buildCacheKey(str: String): String

    /** 根据 URL 生成缓存 key */
    fun buildCacheKey(url: URL): String

    /** 根据 cacheKey 获取缓存目录 */
    fun buildCacheDir(cacheKey: String): File

    /** 根据 cacheKey 获取原始下载包正式缓存文件 */
    fun buildSourceFile(cacheKey: String): File

    /** 根据 cacheKey 获取原始下载包临时缓存文件 */
    fun buildSourceTempFile(cacheKey: String): File

    /** 判断是否存在原始下载包缓存 */
    fun hasSourceCache(cacheKey: String): Boolean

    /** 判断是否存在解析后缓存 */
    fun hasDecodedCache(cacheKey: String): Boolean

    /** 根据音频名获取音频缓存文件 */
    fun buildAudioFile(audio: String): File
}

/**
 * 基于 MD5 的默认实现，使用文件系统目录结构组织缓存。
 */
internal class Md5CachePathResolver(cacheDir: File) : SVGACachePathResolver {
    private companion object {
        private const val SOURCE_FILE_NAME = "source.svga"
        private const val SOURCE_TEMP_FILE_NAME = "source.svga.tmp"
        private const val MOVIE_BINARY_FILE_NAME = "movie.binary"
        private const val MOVIE_SPEC_FILE_NAME = "movie.spec"
    }

    private val cacheDir: String = cacheDir.absolutePath.ensureTrailingSeparator().also {
        File(it).takeIf { dir -> !dir.exists() }?.mkdirs()
    }

    override fun buildCacheKey(str: String): String {
        val messageDigest = MessageDigest.getInstance("MD5")
        messageDigest.update(str.toByteArray(charset("UTF-8")))
        val digest = messageDigest.digest()
        var sb = ""
        for (b in digest) {
            sb += String.format("%02x", b)
        }
        return sb
    }

    override fun buildCacheKey(url: URL): String = buildCacheKey(url.toString())

    override fun buildCacheDir(cacheKey: String): File {
        return File("$cacheDir$cacheKey/")
    }

    override fun buildSourceFile(cacheKey: String): File {
        return File(buildCacheDir(cacheKey), SOURCE_FILE_NAME)
    }

    override fun buildSourceTempFile(cacheKey: String): File {
        return File(buildCacheDir(cacheKey), SOURCE_TEMP_FILE_NAME)
    }

    override fun hasSourceCache(cacheKey: String): Boolean {
        return buildSourceFile(cacheKey).isFile
    }

    override fun hasDecodedCache(cacheKey: String): Boolean {
        val cacheDir = buildCacheDir(cacheKey)
        return File(cacheDir, MOVIE_BINARY_FILE_NAME).isFile ||
                File(cacheDir, MOVIE_SPEC_FILE_NAME).isFile
    }

    override fun buildAudioFile(audio: String): File {
        return File("$cacheDir$audio.mp3")
    }

    private fun String.ensureTrailingSeparator(): String {
        return if (endsWith(File.separator)) this else this + File.separator
    }
}
