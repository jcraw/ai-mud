package com.jcraw.mud.reasoning

import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.sophia.llm.LLMClient

/**
 * Generates descriptions for scenery objects that aren't physical entities
 * but are mentioned in space descriptions (walls, floor, ceiling, throne, etc.)
 */
class SceneryDescriptionGenerator(
    private val llmClient: LLMClient?
) {

    /**
     * Generate a description for a scenery object based on space context.
     * Returns null if the scenery doesn't seem to exist in this space.
     */
    suspend fun describeScenery(
        sceneryTarget: String,
        space: SpacePropertiesComponent,
        spaceDescription: String,
        hintTraits: List<String> = emptyList()
    ): String? {
        if (llmClient == null) {
            return fallbackSceneryDescription(sceneryTarget, space, hintTraits, spaceDescription)
        }

        return try {
            val response = llmClient.chatCompletion(
                modelId = "gpt-4o-mini",
                systemPrompt = buildSystemPrompt(),
                userContext = buildUserContext(sceneryTarget, space, spaceDescription, hintTraits),
                maxTokens = 150,
                temperature = 0.7
            )

            val result = response.choices.firstOrNull()?.message?.content?.trim()

            if (result != null && (result.contains("does not exist", ignoreCase = true) ||
                                   result.contains("don't see", ignoreCase = true) ||
                                   result.contains("no such", ignoreCase = true))) {
                return null
            }

            result
        } catch (e: Exception) {
            println("⚠️ Scenery description generation failed: ${e.message}")
            fallbackSceneryDescription(sceneryTarget, space, hintTraits, spaceDescription)
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

    private fun buildUserContext(
        sceneryTarget: String,
        space: SpacePropertiesComponent,
        spaceDescription: String,
        hintTraits: List<String>
    ): String = """
        Space: ${space.name}
        Terrain: ${space.terrainType}
        Traits: ${if (hintTraits.isNotEmpty()) hintTraits.joinToString(", ") else "none"}
        Space description: $spaceDescription

        Player is examining: "$sceneryTarget"

        Provide a description of this scenery feature, or say "You don't see that here." if it doesn't exist in this space.
    """.trimIndent()

    /**
     * Fallback logic when LLM is not available.
     */
    private fun fallbackSceneryDescription(
        sceneryTarget: String,
        space: SpacePropertiesComponent,
        hintTraits: List<String>,
        spaceDescription: String
    ): String? {
        val targetLower = sceneryTarget.lowercase()

        val matchingHint = hintTraits.find { trait ->
            trait.lowercase().contains(targetLower)
        }

        val matchingDescription = if (matchingHint == null) {
            spaceDescription.split(".").find { sentence ->
                sentence.contains(targetLower)
            }?.trim()
        } else null

        val base = matchingHint ?: matchingDescription

        return if (base != null) {
            "You examine the $sceneryTarget. ${base.trim()}."
        } else {
            when {
                targetLower in listOf("wall", "walls") -> "The walls echo the mood of ${space.name}."
                targetLower in listOf("floor", "ground") -> "The floor beneath your feet mirrors the terrain here."
                targetLower in listOf("ceiling") -> "You glance upward at the ceiling and take in its details."
                targetLower in listOf("air", "atmosphere") -> "The air here carries a distinct quality."
                else -> null
            }
        }
    }
}
