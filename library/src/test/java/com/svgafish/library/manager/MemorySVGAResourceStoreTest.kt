package com.svgafish.library.manager

import com.svgafish.library.model.SVGAVideoModel
import com.svgafish.library.utils.SVGARect
import org.junit.Assert.*
import org.junit.Test

class MemorySVGAResourceStoreTest {

    @Test
    fun put_and_get_returnsModel() {
        val store = MemorySVGAResourceStore(maxEntries = 2)
        val model = createVideoModel("key-1")

        store.put("key-1", model)
        val result = store.get("key-1")

        assertSame(model, result)
    }

    @Test
    fun get_unknownKey_returnsNull() {
        val store = MemorySVGAResourceStore(maxEntries = 2)

        val result = store.get("nonexistent")

        assertNull(result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun constructor_rejectsZeroMaxEntries() {
        MemorySVGAResourceStore(maxEntries = 0)
    }

    @Test
    fun put_exceedsCapacity_evictsEldestIdle() {
        val store = MemorySVGAResourceStore(maxEntries = 2)
        val first = createVideoModel("first")
        val second = createVideoModel("second")
        val third = createVideoModel("third")

        store.put("first", first)
        store.put("second", second)
        store.put("third", third)

        assertNull(store.get("first"))
        assertSame(second, store.get("second"))
        assertSame(third, store.get("third"))
    }

    @Test
    fun get_promotesToMRU_preventsEviction() {
        val store = MemorySVGAResourceStore(maxEntries = 2)
        val a = createVideoModel("a")
        val b = createVideoModel("b")
        val c = createVideoModel("c")

        store.put("a", a)
        store.put("b", b)
        store.get("a")             // Touch "a" to make it MRU
        store.put("c", c)          // Triggers eviction

        assertSame(a, store.get("a"))   // Survived (MRU)
        assertNull(store.get("b"))       // Evicted (LRU)
        assertSame(c, store.get("c"))   // Just added
    }

    @Test
    fun put_existingKey_replacesModel_preservesRetainCount() {
        val store = MemorySVGAResourceStore(maxEntries = 2)
        val m1 = createVideoModel("m1")
        val m2 = createVideoModel("m2")

        store.put("k", m1)
        store.acquire("k", m1)       // retainCount: 0 → 1
        store.put("k", m2)            // Replaces model but preserves retainCount
        val result = store.release("k")

        assertTrue(result.becameIdle)
        assertEquals(0, result.retainCount)
        assertSame(m2, store.get("k"))
    }

    @Test
    fun acquire_newKey_createsWithRetainCount1() {
        val store = MemorySVGAResourceStore(maxEntries = 2)
        val model = createVideoModel("key")

        val acquired = store.acquire("key", model)
        val result = store.release("key")

        assertSame(model, acquired)
        assertEquals(0, result.retainCount)
        assertTrue(result.becameIdle)
    }

    @Test
    fun release_returnsBecameIdle() {
        val store = MemorySVGAResourceStore(maxEntries = 2)
        val model = createVideoModel("key")

        store.put("key", model)
        store.acquire("key", model)
        val result = store.release("key")

        assertEquals(0, result.retainCount)
        assertTrue(result.becameIdle)
    }

    @Test
    fun doubleAcquire_singleRelease_retainCountNotZero() {
        val store = MemorySVGAResourceStore(maxEntries = 2)
        val model = createVideoModel("key")

        store.put("key", model)
        store.acquire("key", model)
        store.acquire("key", model)
        val result = store.release("key")

        assertEquals(1, result.retainCount)
        assertFalse(result.becameIdle)
    }

    @Test
    fun release_unknownKey_returnsZero() {
        val store = MemorySVGAResourceStore(maxEntries = 2)

        val result = store.release("nonexistent")

        assertEquals(0, result.retainCount)
        assertFalse(result.becameIdle)
    }

    @Test
    fun acquire_existingIdle_returnsStoredModel() {
        val store = MemorySVGAResourceStore(maxEntries = 2)
        val m1 = createVideoModel("m1")
        val m2 = createVideoModel("m2")

        store.put("k", m1)
        val result = store.acquire("k", m2)

        assertSame(m1, result)
        assertNotSame(m2, result)
    }

    @Test
    fun acquire_clearsTTL() {
        val store = MemorySVGAResourceStore(maxEntries = 2, idleTtlMillis = 0L)
        val model = createVideoModel("key")

        store.put("key", model)
        store.markPlaybackFinished("key")  // Set TTL
        store.acquire("key", model)         // Clears TTL (sets finishedAt to null)
        val result = store.release("key")

        assertEquals(0, result.retainCount)
        assertTrue(result.becameIdle)
        // Entry survives because acquire cleared the TTL timestamp
        assertNotNull(store.get("key"))
    }

    @Test
    fun release_withExpiredTTL_removesEntry() {
        val store = MemorySVGAResourceStore(maxEntries = 2, idleTtlMillis = 0L)
        val model = createVideoModel("key")

        store.put("key", model)
        store.acquire("key", model)
        store.markPlaybackFinished("key")
        val result = store.release("key")

        assertEquals(0, result.retainCount)
        assertTrue(result.becameIdle)
        assertNull(store.get("key"))
    }

    @Test
    fun release_withoutFinishedAt_staysEvenWithZeroTTL() {
        val store = MemorySVGAResourceStore(maxEntries = 2, idleTtlMillis = 0L)
        val model = createVideoModel("key")

        store.put("key", model)
        store.acquire("key", model)
        val result = store.release("key")

        assertEquals(0, result.retainCount)
        assertTrue(result.becameIdle)
        // Entry stays because markPlaybackFinished was never called (finishedAt is null)
        assertNotNull(store.get("key"))
    }

    @Test
    fun clear_removesIdle_preservesActive() {
        val store = MemorySVGAResourceStore(maxEntries = 4)
        val idle1 = createVideoModel("idle1")
        val idle2 = createVideoModel("idle2")
        val active = createVideoModel("active")

        store.put("idle1", idle1)
        store.put("idle2", idle2)
        store.put("active", active)
        store.acquire("active", active)  // Make active

        store.clear()

        assertNull(store.get("idle1"))
        assertNull(store.get("idle2"))
        assertNotNull(store.get("active"))
    }

    @Test
    fun doubleRelease_doesNotUnderflow() {
        val store = MemorySVGAResourceStore(maxEntries = 2)
        val model = createVideoModel("key")

        store.put("key", model)

        val first = store.release("key")
        assertEquals(0, first.retainCount)
        assertFalse(first.becameIdle)

        val second = store.release("key")
        assertEquals(0, second.retainCount)
        assertFalse(second.becameIdle)
        // No exception thrown
    }

    private fun createVideoModel(requestKey: String = "test-key"): SVGAVideoModel {
        return SVGAVideoModel(
            requestKey = requestKey,
            videoSize = SVGARect(0.0, 0.0, 100.0, 100.0),
            fps = 20,
            frames = 1,
            spriteList = emptyList(),
            imageMap = emptyMap(),
            audioTracks = emptyList()
        )
    }
}
