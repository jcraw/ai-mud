package com.jcraw.mud.memory.world

import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.GenerationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for GenerationCache - thread-safe LRU cache for world chunks.
 *
 * Focus on: cache flow, LRU eviction, concurrent access safety, and helper methods.
 */
class GenerationCacheTest {

    @Test
    fun `cachePending marks chunk as pending`() = runBlocking {
        val cache = GenerationCache()
        val context = GenerationContext(
            seed = "test-seed",
            globalLore = "test lore",
            parentChunk = null,
            parentChunkId = null,
            level = ChunkLevel.WORLD,
            direction = null
        )

        cache.cachePending("test-id", context)

        assertTrue(cache.isPending("test-id"))
        assertEquals(1, cache.pendingCount())
    }

    @Test
    fun `cacheComplete removes from pending and adds to completed`() = runBlocking {
        val cache = GenerationCache()
        val context = GenerationContext(
            seed = "test-seed",
            globalLore = "test lore",
            parentChunk = null,
            parentChunkId = null,
            level = ChunkLevel.WORLD,
            direction = null
        )
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            parentId = null,
            children = emptyList(),
            lore = "test lore",
            biomeTheme = "test theme",
            sizeEstimate = 10,
            mobDensity = 0.5,
            difficultyLevel = 5
        )

        cache.cachePending("test-id", context)
        cache.cacheComplete("test-id", chunk)

        assertFalse(cache.isPending("test-id"))
        assertEquals(0, cache.pendingCount())
        assertNotNull(cache.getCached("test-id"))
        assertEquals(1, cache.size())
    }

    @Test
    fun `getCached returns cached chunk`() = runBlocking {
        val cache = GenerationCache()
        val chunk = WorldChunkComponent(
            level = ChunkLevel.REGION,
            parentId = "WORLD_root",
            children = emptyList(),
            lore = "region lore",
            biomeTheme = "forest",
            sizeEstimate = 50,
            mobDensity = 0.3,
            difficultyLevel = 3
        )

        cache.cacheComplete("region-1", chunk)

        val retrieved = cache.getCached("region-1")
        assertNotNull(retrieved)
        assertEquals(ChunkLevel.REGION, retrieved.level)
        assertEquals("forest", retrieved.biomeTheme)
    }

    @Test
    fun `getCached returns null for non-existent chunk`() = runBlocking {
        val cache = GenerationCache()

        val retrieved = cache.getCached("non-existent")

        assertNull(retrieved)
    }

    @Test
    fun `isPending returns false for non-pending chunk`() = runBlocking {
        val cache = GenerationCache()

        assertFalse(cache.isPending("non-existent"))
    }

    @Test
    fun `getPendingContext returns context for pending chunk`() = runBlocking {
        val cache = GenerationCache()
        val context = GenerationContext(
            seed = "test-seed",
            globalLore = "test lore",
            parentChunk = null,
            parentChunkId = null,
            level = ChunkLevel.ZONE,
            direction = "north"
        )

        cache.cachePending("zone-1", context)

        val retrieved = cache.getPendingContext("zone-1")
        assertNotNull(retrieved)
        assertEquals("north", retrieved.direction)
        assertEquals(ChunkLevel.ZONE, retrieved.level)
    }

    @Test
    fun `getPendingContext returns null for non-pending chunk`() = runBlocking {
        val cache = GenerationCache()

        val retrieved = cache.getPendingContext("non-existent")

        assertNull(retrieved)
    }

    @Test
    fun `LRU eviction removes oldest entry when max size exceeded`() = runBlocking {
        val cache = GenerationCache(maxSize = 3)

        // Add 4 chunks (should evict the first)
        for (i in 1..4) {
            val chunk = WorldChunkComponent(
                level = ChunkLevel.SPACE,
                parentId = "parent",
                children = emptyList(),
                lore = "lore $i",
                biomeTheme = "theme $i",
                sizeEstimate = 1,
                mobDensity = 0.5,
                difficultyLevel = 1
            )
            cache.cacheComplete("chunk-$i", chunk)
        }

        assertEquals(3, cache.size(), "Cache should maintain max size of 3")
        assertNull(cache.getCached("chunk-1"), "Oldest entry should be evicted")
        assertNotNull(cache.getCached("chunk-2"))
        assertNotNull(cache.getCached("chunk-3"))
        assertNotNull(cache.getCached("chunk-4"))
    }

    @Test
    fun `LRU updates access order on getCached`() = runBlocking {
        val cache = GenerationCache(maxSize = 3)

        // Add 3 chunks
        for (i in 1..3) {
            val chunk = WorldChunkComponent(
                level = ChunkLevel.SPACE,
                parentId = "parent",
                children = emptyList(),
                lore = "lore $i",
                biomeTheme = "theme $i",
                sizeEstimate = 1,
                mobDensity = 0.5,
                difficultyLevel = 1
            )
            cache.cacheComplete("chunk-$i", chunk)
        }

        // Access chunk-1 (makes it most recently used)
        cache.getCached("chunk-1")

        // Add chunk-4 (should evict chunk-2, not chunk-1)
        val chunk4 = WorldChunkComponent(
            level = ChunkLevel.SPACE,
            parentId = "parent",
            children = emptyList(),
            lore = "lore 4",
            biomeTheme = "theme 4",
            sizeEstimate = 1,
            mobDensity = 0.5,
            difficultyLevel = 1
        )
        cache.cacheComplete("chunk-4", chunk4)

        assertNotNull(cache.getCached("chunk-1"), "Recently accessed chunk should remain")
        assertNull(cache.getCached("chunk-2"), "Least recently used chunk should be evicted")
        assertNotNull(cache.getCached("chunk-3"))
        assertNotNull(cache.getCached("chunk-4"))
    }

    @Test
    fun `clear removes all cached data`() = runBlocking {
        val cache = GenerationCache()
        val context = GenerationContext(
            seed = "test",
            globalLore = "lore",
            parentChunk = null,
            parentChunkId = null,
            level = ChunkLevel.WORLD,
            direction = null
        )
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            parentId = null,
            children = emptyList(),
            lore = "lore",
            biomeTheme = "theme",
            sizeEstimate = 10,
            mobDensity = 0.5,
            difficultyLevel = 1
        )

        cache.cachePending("pending-1", context)
        cache.cacheComplete("completed-1", chunk)

        cache.clear()

        assertEquals(0, cache.size())
        assertEquals(0, cache.pendingCount())
        assertFalse(cache.isPending("pending-1"))
        assertNull(cache.getCached("completed-1"))
    }

    @Test
    fun `concurrent access is thread-safe`() = runBlocking {
        val cache = GenerationCache()
        val chunk = WorldChunkComponent(
            level = ChunkLevel.SPACE,
            parentId = "parent",
            children = emptyList(),
            lore = "lore",
            biomeTheme = "theme",
            sizeEstimate = 1,
            mobDensity = 0.5,
            difficultyLevel = 1
        )

        // Launch 100 concurrent operations
        val jobs = (1..100).map { i ->
            async {
                cache.cacheComplete("chunk-$i", chunk)
                cache.getCached("chunk-$i")
            }
        }

        // Wait for all to complete
        jobs.awaitAll()

        // All operations should succeed without race conditions
        assertEquals(100, cache.size())
    }

    @Test
    fun `pendingCount and size track correct values`() = runBlocking {
        val cache = GenerationCache()
        val context = GenerationContext(
            seed = "test",
            globalLore = "lore",
            parentChunk = null,
            parentChunkId = null,
            level = ChunkLevel.WORLD,
            direction = null
        )
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            parentId = null,
            children = emptyList(),
            lore = "lore",
            biomeTheme = "theme",
            sizeEstimate = 10,
            mobDensity = 0.5,
            difficultyLevel = 1
        )

        assertEquals(0, cache.pendingCount())
        assertEquals(0, cache.size())

        cache.cachePending("pending-1", context)
        assertEquals(1, cache.pendingCount())
        assertEquals(0, cache.size())

        cache.cacheComplete("pending-1", chunk)
        assertEquals(0, cache.pendingCount())
        assertEquals(1, cache.size())
    }
}
