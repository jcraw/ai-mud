package com.jcraw.mud.memory.world

import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.world.GenerationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory cache for world chunk generation.
 *
 * Prevents race conditions (multiple players approach same boundary) and improves performance
 * by caching recently generated chunks. Uses LRU eviction with 1000 chunk limit.
 *
 * Thread-safe for concurrent access.
 */
class GenerationCache(
    private val maxSize: Int = 1000
) {
    private val mutex = Mutex()
    private val pendingGenerations = mutableMapOf<String, GenerationContext>()
    private val completedChunks = object : LinkedHashMap<String, WorldChunkComponent>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WorldChunkComponent>?): Boolean {
            return size > maxSize
        }
    }

    /**
     * Marks a chunk as pending generation.
     *
     * @param id Chunk ID being generated
     * @param context Generation context
     */
    suspend fun cachePending(id: String, context: GenerationContext) {
        mutex.withLock {
            pendingGenerations[id] = context
        }
    }

    /**
     * Marks a chunk as complete and caches it.
     *
     * @param id Chunk ID
     * @param chunk Generated chunk
     */
    suspend fun cacheComplete(id: String, chunk: WorldChunkComponent) {
        mutex.withLock {
            pendingGenerations.remove(id)
            completedChunks[id] = chunk
        }
    }

    /**
     * Retrieves a cached chunk.
     *
     * @param id Chunk ID
     * @return Cached chunk if present, null otherwise
     */
    suspend fun getCached(id: String): WorldChunkComponent? {
        return mutex.withLock {
            completedChunks[id]
        }
    }

    /**
     * Checks if a chunk is currently being generated.
     *
     * @param id Chunk ID
     * @return True if generation in progress
     */
    suspend fun isPending(id: String): Boolean {
        return mutex.withLock {
            pendingGenerations.containsKey(id)
        }
    }

    /**
     * Gets the generation context for a pending chunk.
     *
     * @param id Chunk ID
     * @return Generation context if pending, null otherwise
     */
    suspend fun getPendingContext(id: String): GenerationContext? {
        return mutex.withLock {
            pendingGenerations[id]
        }
    }

    /**
     * Clears all cached data.
     */
    suspend fun clear() {
        mutex.withLock {
            pendingGenerations.clear()
            completedChunks.clear()
        }
    }

    /**
     * Gets the current cache size.
     *
     * @return Number of cached chunks
     */
    suspend fun size(): Int {
        return mutex.withLock {
            completedChunks.size
        }
    }

    /**
     * Gets the number of pending generations.
     *
     * @return Number of chunks being generated
     */
    suspend fun pendingCount(): Int {
        return mutex.withLock {
            pendingGenerations.size
        }
    }
}
