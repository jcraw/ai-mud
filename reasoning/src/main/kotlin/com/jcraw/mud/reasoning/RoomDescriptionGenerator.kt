package com.jcraw.mud.reasoning

import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.sophia.llm.LLMClient

/**
 * Generates vivid location descriptions for V3 spaces using LLM with RAG context.
 */
class RoomDescriptionGenerator(
    private val llmClient: LLMClient,
    private val memoryManager: MemoryManager? = null
) {

    /**
     * Generate a narrative description of a space from its metadata with historical context.
     *
     * @param space The space whose description needs to be generated/refreshed.
     * @param hintTraits Optional environmental hints (e.g., legacy Room traits) to seed the prompt.
     */
    suspend fun generateDescription(
        space: SpacePropertiesComponent,
        hintTraits: List<String> = emptyList()
    ): String {
        val memories = memoryManager?.recall("space ${space.name}", k = 3) ?: emptyList()

        val systemPrompt = buildSystemPrompt()
        val userContext = buildUserContext(space, hintTraits, memories)

        return try {
            val response = llmClient.chatCompletion(
                modelId = "gpt-4o-mini",  // Cost-effective model for descriptions
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = 200,
                temperature = 0.8  // Higher temperature for creative descriptions
            )

            val description = response.choices.firstOrNull()?.message?.content?.trim()
                ?: fallbackDescription(space, hintTraits)

            memoryManager?.remember(
                "Player entered ${space.name}: $description",
                mapOf("type" to "space_entry", "space" to space.name)
            )

            description
        } catch (e: Exception) {
            println("⚠️ LLM description generation failed: ${e.message}")
            fallbackDescription(space, hintTraits)
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

    private fun buildUserContext(
        space: SpacePropertiesComponent,
        traits: List<String>,
        memories: List<String>
    ): String {
        val historySection = if (memories.isNotEmpty()) {
            "\n\nRecent history:\n${memories.joinToString("\n") { "- $it" }}"
        } else {
            ""
        }

        val traitSection = if (traits.isNotEmpty()) {
            traits.joinToString(", ")
        } else {
            "none specified"
        }

        val baseDescription = if (space.description.isNotBlank()) {
            space.description
        } else {
            "No prior description exists. Use the traits and terrain to establish atmosphere."
        }

        return """
            Space: ${space.name}
            Terrain: ${space.terrainType}
            Traits: $traitSection
            Existing description (if any): $baseDescription$historySection

            Generate an atmospheric description for this space.
        """.trimIndent()
    }

    /**
     * Fallback to using existing description or traits if LLM fails.
     */
    private fun fallbackDescription(
        space: SpacePropertiesComponent,
        traits: List<String>
    ): String {
        return when {
            space.description.isNotBlank() -> space.description
            traits.isNotEmpty() -> traits.joinToString(". ") + "."
            else -> "You are in ${space.name}. The surroundings feel unremarkable for now."
        }
    }
}
