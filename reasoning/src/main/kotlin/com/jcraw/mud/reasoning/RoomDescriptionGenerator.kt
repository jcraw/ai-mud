package com.jcraw.mud.reasoning

import com.jcraw.mud.core.Room
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.sophia.llm.LLMClient

/**
 * Generates vivid room descriptions from traits using LLM with RAG context
 */
class RoomDescriptionGenerator(
    private val llmClient: LLMClient,
    private val memoryManager: MemoryManager? = null
) {

    /**
     * Generate a narrative description of a room from its traits with historical context
     */
    suspend fun generateDescription(room: Room): String {
        // Retrieve relevant memories for context
        val memories = memoryManager?.recall("room ${room.name}", k = 3) ?: emptyList()

        val systemPrompt = buildSystemPrompt()
        val userContext = buildUserContext(room, memories)

        return try {
            val response = llmClient.chatCompletion(
                modelId = "gpt-4o-mini",  // Cost-effective model for descriptions
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = 200,
                temperature = 0.8  // Higher temperature for creative descriptions
            )

            val description = response.choices.firstOrNull()?.message?.content?.trim()
                ?: fallbackDescription(room)

            // Store this room visit in memory
            memoryManager?.remember(
                "Player entered ${room.name}: $description",
                mapOf("type" to "room_entry", "room" to room.id)
            )

            description
        } catch (e: Exception) {
            println("⚠️ LLM description generation failed: ${e.message}")
            fallbackDescription(room)
        }
    }

    private fun buildSystemPrompt(): String = """
        You are a descriptive narrator for a text-based adventure game (MUD).

        Your task is to create vivid, atmospheric room descriptions that immerse the player.

        Guidelines:
        - Write in second person present tense ("You see...", "The air feels...")
        - Create 2-4 sentences that paint a sensory picture
        - Weave the provided traits naturally into the description
        - Focus on atmosphere and mood, not gameplay mechanics
        - Be concise but evocative
        - Do NOT mention exits or items/entities (those are listed separately)
        - If provided with recent history, subtly incorporate changes or continuity
        - Vary your descriptions - avoid repeating exact phrases from history

        Example traits: ["crumbling stone walls", "flickering torchlight", "musty air"]
        Example output: "You stand in a chamber with crumbling stone walls that speak of centuries past. Flickering torchlight casts dancing shadows across the ancient masonry. The air hangs heavy with the musty scent of age and decay."
    """.trimIndent()

    private fun buildUserContext(room: Room, memories: List<String>): String {
        val historySection = if (memories.isNotEmpty()) {
            "\n\nRecent history:\n${memories.joinToString("\n") { "- $it" }}"
        } else {
            ""
        }

        return """
            Room: ${room.name}
            Traits: ${room.traits.joinToString(", ")}$historySection

            Generate an atmospheric description for this room.
        """.trimIndent()
    }

    /**
     * Fallback to simple trait concatenation if LLM fails
     */
    private fun fallbackDescription(room: Room): String {
        return room.traits.joinToString(". ") + "."
    }
}
