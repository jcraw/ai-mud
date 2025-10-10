package com.jcraw.mud.memory

/**
 * Interface for vector storage implementations.
 * Allows pluggable backends (in-memory, persistent, etc.)
 */
interface VectorStore {
    /**
     * Add a memory entry to the store
     */
    fun add(entry: MemoryEntry)

    /**
     * Search for the k most similar entries using cosine similarity
     */
    fun search(queryEmbedding: List<Double>, k: Int): List<ScoredMemoryEntry>

    /**
     * Get all entries (for debugging/testing)
     */
    fun getAll(): List<MemoryEntry>

    /**
     * Clear all entries
     */
    fun clear()

    /**
     * Get count of entries
     */
    fun size(): Int
}
