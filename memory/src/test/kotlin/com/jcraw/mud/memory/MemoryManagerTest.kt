package com.jcraw.mud.memory

import com.jcraw.sophia.llm.LLMClient
import com.jcraw.sophia.llm.OpenAIResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mock LLM client for testing
 */
class MockLLMClient : LLMClient {
    private var embeddingCounter = 0

    override suspend fun chatCompletion(
        modelId: String,
        systemPrompt: String,
        userContext: String,
        maxTokens: Int,
        temperature: Double
    ): OpenAIResponse {
        throw NotImplementedError("Not used in memory tests")
    }

    override suspend fun createEmbedding(text: String, model: String): List<Double> {
        // Generate deterministic embeddings based on text length
        // This ensures similar texts get similar embeddings
        embeddingCounter++
        val length = text.length.toDouble()
        return listOf(length / 100.0, embeddingCounter / 10.0, 0.5)
    }

    override fun close() {
        // No-op for mock
    }
}

/**
 * Tests for MemoryManager focusing on behavior
 */
class MemoryManagerTest {

    @Test
    fun `remember stores memory with embedding`() = runBlocking {
        val mockClient = MockLLMClient()
        val manager = MemoryManager(mockClient)

        manager.remember("Player entered dark forest")

        assertEquals(1, manager.getMemoryCount())
    }

    @Test
    fun `recall retrieves relevant memories based on similarity`() = runBlocking {
        val mockClient = MockLLMClient()
        val manager = MemoryManager(mockClient)

        // Store memories with different lengths (which affects our mock embeddings)
        manager.remember("Short text")  // length 10
        manager.remember("This is a much longer piece of text about combat")  // length 48

        // Query with similar length to the longer text
        val results = manager.recall("A query about combat and fighting monsters", k = 2)

        assertEquals(2, results.size)
        // The longer text should be more similar due to length-based embedding
        assertTrue(results.first().contains("longer"))
    }

    @Test
    fun `recall returns empty list when no memories stored`() = runBlocking {
        val mockClient = MockLLMClient()
        val manager = MemoryManager(mockClient)

        val results = manager.recall("Some query", k = 5)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `recall respects k parameter`() = runBlocking {
        val mockClient = MockLLMClient()
        val manager = MemoryManager(mockClient)

        // Store 5 memories
        repeat(5) { i ->
            manager.remember("Memory number $i with some content")
        }

        val results = manager.recall("Query", k = 3)

        assertEquals(3, results.size)
    }

    @Test
    fun `clearAllMemories removes all stored memories`() = runBlocking {
        val mockClient = MockLLMClient()
        val manager = MemoryManager(mockClient)

        manager.remember("Memory 1")
        manager.remember("Memory 2")

        assertEquals(2, manager.getMemoryCount())

        manager.clearAllMemories()

        assertEquals(0, manager.getMemoryCount())
    }

    @Test
    fun `remember with null client does not throw`() = runBlocking {
        val manager = MemoryManager(llmClient = null)

        // Should not throw, just log warning
        manager.remember("Some event")

        assertEquals(0, manager.getMemoryCount())
    }

    @Test
    fun `recall with null client returns empty list`() = runBlocking {
        val manager = MemoryManager(llmClient = null)

        val results = manager.recall("Some query")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `metadata is stored and retrievable`() = runBlocking {
        val mockClient = MockLLMClient()
        val manager = MemoryManager(mockClient)

        val metadata = mapOf("type" to "combat", "room" to "forest")
        manager.remember("Player fought goblin", metadata)

        val memories = manager.getAllMemories()

        assertEquals(1, memories.size)
        assertEquals(metadata, memories.first().metadata)
    }
}
