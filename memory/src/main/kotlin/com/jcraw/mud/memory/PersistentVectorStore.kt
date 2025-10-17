package com.jcraw.mud.memory

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.sqrt

/**
 * Persistent vector store that saves to disk.
 * Wraps in-memory operations with file I/O.
 */
class PersistentVectorStore(
    private val filePath: String
) : VectorStore {
    private val entries = mutableListOf<MemoryEntry>()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        loadFromDisk()
    }

    override fun add(entry: MemoryEntry) {
        entries.add(entry)
        saveToDisk()
    }

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

    override fun searchWithMetadata(
        queryEmbedding: List<Double>,
        k: Int,
        metadataFilter: Map<String, String>
    ): List<ScoredMemoryEntry> {
        if (entries.isEmpty()) return emptyList()

        // Filter entries matching ALL metadata key-value pairs
        val filteredEntries = entries.filter { entry ->
            metadataFilter.all { (key, value) ->
                entry.metadata[key] == value
            }
        }

        if (filteredEntries.isEmpty()) return emptyList()

        // Perform similarity search on filtered entries
        return filteredEntries
            .map { entry ->
                val similarity = cosineSimilarity(queryEmbedding, entry.embedding)
                ScoredMemoryEntry(entry, similarity)
            }
            .sortedByDescending { it.score }
            .take(k)
    }

    override fun getAll(): List<MemoryEntry> = entries.toList()

    override fun clear() {
        entries.clear()
        saveToDisk()
    }

    override fun size(): Int = entries.size

    private fun loadFromDisk() {
        val file = File(filePath)
        if (file.exists()) {
            try {
                val data = json.decodeFromString<MemoryStore>(file.readText())
                entries.clear()
                entries.addAll(data.entries)
            } catch (e: Exception) {
                println("Warning: Could not load memory store from $filePath: ${e.message}")
            }
        }
    }

    private fun saveToDisk() {
        try {
            val file = File(filePath)
            file.parentFile?.mkdirs()
            val data = MemoryStore(entries.toList())
            file.writeText(json.encodeToString(data))
        } catch (e: Exception) {
            println("Warning: Could not save memory store to $filePath: ${e.message}")
        }
    }

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

@Serializable
private data class MemoryStore(
    val entries: List<MemoryEntry>
)
