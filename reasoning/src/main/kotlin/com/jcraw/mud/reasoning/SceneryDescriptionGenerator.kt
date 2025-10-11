package com.jcraw.mud.reasoning

import com.jcraw.mud.core.Room
import com.jcraw.sophia.llm.LLMClient

/**
 * Generates descriptions for scenery objects that aren't physical entities
 * but are mentioned in room descriptions (walls, floor, ceiling, throne, etc.)
 */
class SceneryDescriptionGenerator(
    private val llmClient: LLMClient?
) {

    /**
     * Generate a description for a scenery object based on room context.
     * Returns null if the scenery doesn't seem to exist in this room.
     */
    suspend fun describeScenery(sceneryTarget: String, room: Room, roomDescription: String): String? {
        if (llmClient == null) {
            return fallbackSceneryDescription(sceneryTarget, room)
        }

        return try {
            val response = llmClient.chatCompletion(
                modelId = "gpt-4o-mini",
                systemPrompt = buildSystemPrompt(),
                userContext = buildUserContext(sceneryTarget, room, roomDescription),
                maxTokens = 150,
                temperature = 0.7
            )

            val result = response.choices.firstOrNull()?.message?.content?.trim()

            // Check if the LLM indicates the scenery doesn't exist
            if (result != null && (result.contains("does not exist", ignoreCase = true) ||
                                   result.contains("don't see", ignoreCase = true) ||
                                   result.contains("no such", ignoreCase = true))) {
                return null
            }

            result
        } catch (e: Exception) {
            println("⚠️ Scenery description generation failed: ${e.message}")
            fallbackSceneryDescription(sceneryTarget, room)
        }
    }

    private fun buildSystemPrompt(): String = """
        You are a descriptive narrator for a text-based adventure game (MUD).

        The player is trying to examine a specific scenery feature in a room.

        Your task:
        1. Determine if the scenery object logically exists in this room based on context
        2. If it exists, provide a 1-3 sentence description focused on that specific feature
        3. If it does NOT exist or is not mentioned in the room, respond with "You don't see that here."

        Guidelines:
        - Write in second person present tense ("You notice...", "The walls are...")
        - Be specific to what the player is examining
        - Use details from the room description and traits to inform your description
        - Consider what would realistically be in such a room
        - Don't invent major features not implied by the room context

        Examples:
        - If player examines "throne" in a throne room, describe the throne in detail
        - If player examines "walls" in a stone chamber, describe the stone walls
        - If player examines "ceiling" in any room, describe what's overhead
        - If player examines "ocean" in a forest, say "You don't see that here."
    """.trimIndent()

    private fun buildUserContext(sceneryTarget: String, room: Room, roomDescription: String): String = """
        Room: ${room.name}
        Room traits: ${room.traits.joinToString(", ")}
        Room description: $roomDescription

        Player is examining: "$sceneryTarget"

        Provide a description of this scenery feature, or say "You don't see that here." if it doesn't exist in this room.
    """.trimIndent()

    /**
     * Fallback logic when LLM is not available
     */
    private fun fallbackSceneryDescription(sceneryTarget: String, room: Room): String? {
        val targetLower = sceneryTarget.lowercase()

        // Check if any room traits mention this scenery
        val matchingTrait = room.traits.find { trait ->
            trait.lowercase().contains(targetLower)
        }

        return if (matchingTrait != null) {
            "You examine the $sceneryTarget. $matchingTrait"
        } else {
            // Check for common scenery that would exist in any room
            when {
                targetLower in listOf("wall", "walls") -> "The walls are typical of this area."
                targetLower in listOf("floor", "ground") -> "The floor beneath your feet is solid."
                targetLower in listOf("ceiling") -> "You glance upward at the ceiling above."
                targetLower in listOf("air", "atmosphere") -> "The air here has a distinct quality to it."
                else -> null // Doesn't exist
            }
        }
    }
}
