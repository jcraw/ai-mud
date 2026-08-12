@file:Suppress("MagicNumber", "TooManyFunctions")

package com.jcraw.app

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.reasoning.CombatNarrator
import com.jcraw.mud.reasoning.NPCInteractionGenerator
import com.jcraw.mud.reasoning.RoomDescriptionGenerator
import com.jcraw.sophia.llm.LLMClient
import com.jcraw.sophia.llm.OpenAIResponse

/**
 * Fallback factories and V3 exit-name map for [MultiUserGame]. Pure extract.
 */
object MultiUserFallbacks {

    /**
     * Build a map of exits with their destination space names for navigation parsing (V3).
     */
    fun buildExitsWithNamesV3(
        graphNode: GraphNodeComponent,
        worldState: WorldState
    ): Map<Direction, String> {
        return graphNode.neighbors.mapNotNull { edge ->
            val destSpace = worldState.getSpace(edge.targetId)
            val direction = Direction.fromString(edge.direction)
            if (destSpace != null && direction != null) {
                // Extract name from first line of destination description
                val destName = destSpace.description.lines().firstOrNull()?.take(50) ?: "Unknown"
                direction to destName
            } else {
                null
            }
        }.toMap()
    }

    /**
     * Create a fallback memory manager for when no LLM client is available.
     */
    fun createFallbackMemoryManager(): MemoryManager {
        // Create memory manager with null client (will use in-memory store only)
        return MemoryManager(null)
    }

    /**
     * Create a fallback description generator with a mock client.
     */
    fun createFallbackDescriptionGenerator(memoryManager: MemoryManager): RoomDescriptionGenerator {
        return RoomDescriptionGenerator(createMockLlmClient(), memoryManager)
    }

    /**
     * Create a fallback NPC interaction generator with a mock client.
     */
    fun createFallbackNPCGenerator(memoryManager: MemoryManager): NPCInteractionGenerator {
        return NPCInteractionGenerator(createMockLlmClient(), memoryManager)
    }

    /**
     * Create a fallback combat narrator with a mock client.
     */
    fun createFallbackCombatNarrator(memoryManager: MemoryManager): CombatNarrator {
        return CombatNarrator(createMockLlmClient(), memoryManager)
    }

    /**
     * Simple mock LLM client that always throws to trigger fallback logic.
     */
    private fun createMockLlmClient(): LLMClient {
        return object : LLMClient {
            override suspend fun chatCompletion(
                modelId: String,
                systemPrompt: String,
                userContext: String,
                maxTokens: Int,
                temperature: Double
            ): OpenAIResponse {
                throw UnsupportedOperationException("Mock client - fallback mode")
            }

            override suspend fun createEmbedding(text: String, model: String): List<Double> {
                return emptyList()
            }

            override fun close() {
                // No-op for mock
            }
        }
    }
}
