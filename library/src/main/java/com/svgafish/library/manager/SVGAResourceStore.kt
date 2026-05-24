package com.svgafish.library.manager

import com.svgafish.library.model.SVGAVideoModel
import java.util.LinkedHashMap

interface SVGAResourceStore {

    fun get(requestKey: String): SVGAVideoModel?

    fun put(requestKey: String, videoModel: SVGAVideoModel)

    fun acquire(requestKey: String, requestedModel: SVGAVideoModel): SVGAVideoModel

    fun release(requestKey: String): ReleaseResult

    fun markPlaybackFinished(requestKey: String): Long?

    /**
     * 仅清理当前没有活跃持有方的资源。
     */
    fun clear()

    data class ReleaseResult(
        val retainCount: Int,
        val becameIdle: Boolean
    )
}

/**
 * 基于条目数上限的线程安全 LRU 内存资源存储。
 *
 * 活跃资源不会因 LRU 淘汰而被移除；当所有条目都处于活跃状态时，
 * store 容量可能暂时超过上限，直到后续有条目释放为 idle。
 */
class MemorySVGAResourceStore(
    maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val idleTtlMillis: Long = DEFAULT_IDLE_TTL_MILLIS
) : SVGAResourceStore {

    companion object {
        const val DEFAULT_MAX_ENTRIES = 8
        const val DEFAULT_IDLE_TTL_MILLIS = 30_000L
    }

    private val lock = Any()
    private val entryLimit = maxEntries.also {
        require(it > 0) { "maxEntries must be greater than 0." }
    }
    private val entries = LinkedHashMap<String, ResourceEntry>(entryLimit, 0.75f, true)

    override fun get(requestKey: String): SVGAVideoModel? = synchronized(lock) {
        entries[requestKey]?.videoModel
    }

    override fun put(requestKey: String, videoModel: SVGAVideoModel) {
        synchronized(lock) {
            val existing = entries[requestKey]
            if (existing == null) {
                entries[requestKey] = ResourceEntry(videoModel = videoModel)
            } else {
                existing.videoModel = videoModel
            }
            evictExpiredIdleEntriesLocked()
            trimIdleEntriesLocked()
        }
    }

    override fun acquire(requestKey: String, requestedModel: SVGAVideoModel): SVGAVideoModel {
        return synchronized(lock) {
            val existing = entries[requestKey]
            if (existing == null) {
                entries[requestKey] = ResourceEntry(
                    videoModel = requestedModel,
                    retainCount = 1
                )
                trimIdleEntriesLocked()
                return@synchronized requestedModel
            }
            existing.retainCount++
            existing.lastPlaybackFinishedAtMillis = null
            existing.videoModel
        }
    }

    override fun release(requestKey: String): SVGAResourceStore.ReleaseResult {
        return synchronized(lock) {
            val entry = entries[requestKey]
                ?: return@synchronized SVGAResourceStore.ReleaseResult(
                    retainCount = 0,
                    becameIdle = false
                )

            val previousRetainCount = entry.retainCount
            if (previousRetainCount > 0) {
                entry.retainCount--
            }
            val becameIdle = previousRetainCount > 0 && entry.retainCount == 0
            if (becameIdle) {
                val finishedAt = entry.lastPlaybackFinishedAtMillis
                if (finishedAt != null && System.currentTimeMillis() - finishedAt >= idleTtlMillis) {
                    entries.remove(requestKey)
                } else {
                    trimIdleEntriesLocked()
                }
            }
            SVGAResourceStore.ReleaseResult(
                retainCount = entry.retainCount,
                becameIdle = becameIdle
            )
        }
    }

    override fun markPlaybackFinished(requestKey: String): Long? {
        return synchronized(lock) {
            val entry = entries[requestKey] ?: return@synchronized null
            val finishedAt = System.currentTimeMillis()
            entry.lastPlaybackFinishedAtMillis = finishedAt
            finishedAt
        }
    }

    override fun clear() {
        synchronized(lock) {
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().value.retainCount == 0) {
                    iterator.remove()
                }
            }
        }
    }

    private fun trimIdleEntriesLocked() {
        if (entries.size <= entryLimit) {
            return
        }
        val iterator = entries.entries.iterator()
        while (entries.size > entryLimit && iterator.hasNext()) {
            if (iterator.next().value.retainCount == 0) {
                iterator.remove()
            }
        }
    }

    private fun evictExpiredIdleEntriesLocked() {
        val now = System.currentTimeMillis()
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (entry.retainCount == 0) {
                val finishedAt = entry.lastPlaybackFinishedAtMillis
                if (finishedAt != null && now - finishedAt >= idleTtlMillis) {
                    iterator.remove()
                }
            }
        }
    }

    private data class ResourceEntry(
        var videoModel: SVGAVideoModel,
        var retainCount: Int = 0,
        var lastPlaybackFinishedAtMillis: Long? = null
    )
}
