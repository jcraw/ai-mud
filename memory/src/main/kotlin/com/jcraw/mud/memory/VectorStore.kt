package com.jcraw.mud.memory

import kotlin.math.sqrt

/**
 * Simple in-memory vector store with cosine similarity search.
 * KISS principle - no external database for MVP.
 */
class InMemoryVectorStore : VectorStore {
    private val entries = mutableListOf<MemoryEntry>()

    /**
     * Add a memory entry to the store
     */
    override fun add(entry: MemoryEntry) {
        entries.add(entry)
    }

    /**
     * Search for the k most similar entries using cosine similarity
     */
    override fun search(queryEmbedding: List<Double>, k: Int): List<ScoredMemoryEntry> {
        if (entries.isEmpty()) return emptyList()

        return entries
            .map { entry ->
                val similarity = cosineSimilarity(queryEmbedding, entry.embedding)
                ScoredMemoryEntry(entry, similarity)
            }
            .sortedByDescending { it.score }
            .take(k)
    }

    /**
     * Get all entries (for debugging/testing)
     */
    override fun getAll(): List<MemoryEntry> = entries.toList()

    /**
     * Clear all entries
     */
    override fun clear() {
        entries.clear()
    }

    /**
     * Get count of entries
     */
    override fun size(): Int = entries.size

    /**
     * Calculate cosine similarity between two vectors
     * Returns value between -1 (opposite) and 1 (identical)
     */
    private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        require(a.size == b.size) { "Vectors must have same dimension" }

        val dotProduct = a.zip(b).sumOf { (x, y) -> x * y }
        val magnitudeA = sqrt(a.sumOf { it * it })
        val magnitudeB = sqrt(b.sumOf { it * it })

        return if (magnitudeA == 0.0 || magnitudeB == 0.0) {
            0.0
        } else {
            dotProduct / (magnitudeA * magnitudeB)
        }
    }
}

/**
 * Memory entry with similarity score
 */
data class ScoredMemoryEntry(
    val entry: MemoryEntry,
    val score: Double
)
