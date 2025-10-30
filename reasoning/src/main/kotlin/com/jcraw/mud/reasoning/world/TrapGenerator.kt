package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.world.TrapData
import com.jcraw.mud.llm.LLMService
import java.util.UUID
import kotlin.random.Random

/**
 * Generates traps based on theme and difficulty.
 * Uses ThemeRegistry for trap type selection and optional LLM for descriptions.
 */
class TrapGenerator(
    private val llmService: LLMService? = null
) {
    /**
     * Generate a trap for the given theme and difficulty level.
     * Selects trap type from ThemeRegistry and scales difficulty with variance.
     */
    fun generate(theme: String, difficulty: Int): TrapData {
        val profile = ThemeRegistry.getProfileSemantic(theme)
            ?: ThemeRegistry.getDefaultProfile()

        // Select random trap type from profile
        val trapType = profile.traps.random()

        // Scale difficulty with random variance (-2 to +2)
        val variance = Random.nextInt(-2, 3)
        val scaledDifficulty = (difficulty + variance).coerceIn(1, 25)

        // Generate unique ID
        val trapId = "trap_${UUID.randomUUID()}"

        // Generate description (use LLM if available, fallback to simple description)
        val description = if (llmService != null) {
            generateTrapDescription(trapType, theme)
        } else {
            "A $trapType in the $theme."
        }

        return TrapData(
            id = trapId,
            type = trapType,
            difficulty = scaledDifficulty,
            triggered = false,
            description = description
        )
    }

    /**
     * Generate vivid trap description using LLM.
     * Creates 1-2 sentence description for immersion.
     */
    fun generateTrapDescription(trapType: String, theme: String): String {
        if (llmService == null) {
            return "A $trapType in the $theme."
        }

        val prompt = """
            Describe a $trapType trap in a $theme setting.
            Be vivid but concise (1-2 sentences).
            Focus on visual details and danger hints.
        """.trimIndent()

        return try {
            val response = llmService.complete(
                prompt = prompt,
                model = "gpt-4o-mini",
                temperature = 0.7
            )
            response.trim()
        } catch (e: Exception) {
            // Fallback on LLM failure
            "A $trapType in the $theme."
        }
    }

    /**
     * Generate multiple traps for a space.
     * Probability-based generation (~15% base chance per call).
     */
    fun generateTrapsForSpace(
        theme: String,
        difficulty: Int,
        trapProbability: Double = 0.15
    ): List<TrapData> {
        val traps = mutableListOf<TrapData>()

        // Roll for trap generation
        if (Random.nextDouble() < trapProbability) {
            traps.add(generate(theme, difficulty))

            // Small chance for second trap in dangerous areas (difficulty > 10)
            if (difficulty > 10 && Random.nextDouble() < 0.05) {
                traps.add(generate(theme, difficulty + 2))
            }
        }

        return traps
    }
}
