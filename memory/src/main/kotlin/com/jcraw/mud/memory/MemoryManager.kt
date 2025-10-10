package com.jcraw.mud.memory

import com.jcraw.sophia.llm.LLMClient
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * High-level memory manager for storing and retrieving game events.
 * Handles embedding generation and RAG retrieval.
 */
class MemoryManager(
    private val llmClient: LLMClient?,
    private val vectorStore: InMemoryVectorStore = InMemoryVectorStore()
) {
    /**
     * Store a game event in memory
     */
    suspend fun remember(
        content: String,
        metadata: Map<String, String> = emptyMap()
    ) {
        if (llmClient == null) {
            println("⚠️  No LLM client - skipping memory storage")
            return
        }

        try {
            val embedding = llmClient.createEmbedding(content)
            val entry = MemoryEntry(
                id = UUID.randomUUID().toString(),
                content = content,
                embedding = embedding,
                timestamp = Clock.System.now(),
                metadata = metadata
            )
            vectorStore.add(entry)
            println("💾 Stored memory: ${content.take(60)}...")
        } catch (e: Exception) {
            println("⚠️  Failed to store memory: ${e.message}")
        }
    }

    /**
     * Retrieve relevant memories for a given context
     */
    suspend fun recall(
        query: String,
        k: Int = 5
    ): List<String> {
        if (llmClient == null) {
            println("⚠️  No LLM client - skipping memory retrieval")
            return emptyList()
        }

        if (vectorStore.size() == 0) {
            println("💭 No memories stored yet")
            return emptyList()
        }

        try {
            val queryEmbedding = llmClient.createEmbedding(query)
            val results = vectorStore.search(queryEmbedding, k)

            println("🔍 Retrieved ${results.size} relevant memories")
            results.forEachIndexed { idx, scored ->
                println("   ${idx + 1}. [score=${String.format("%.3f", scored.score)}] ${scored.entry.content.take(60)}...")
            }

            return results.map { it.entry.content }
        } catch (e: Exception) {
            println("⚠️  Failed to retrieve memories: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Get all memories (for debugging)
     */
    fun getAllMemories(): List<MemoryEntry> = vectorStore.getAll()

    /**
     * Clear all memories
     */
    fun clearAllMemories() {
        vectorStore.clear()
        println("🗑️  Cleared all memories")
    }

    /**
     * Get memory count
     */
    fun getMemoryCount(): Int = vectorStore.size()
}
