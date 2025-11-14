package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.sophia.llm.LLMClient

/**
 * Handles lore variation and theme blending for hierarchical world generation.
 *
 * Creates child lore that maintains consistency with parent while introducing local details.
 * Reusable across all chunk types.
 */
class LoreInheritanceEngine(
    private val llmClient: LLMClient
) {
    companion object {
        private const val MODEL = "gpt-4o-mini" // Cost-effective model for lore generation
        private const val LORE_TEMPERATURE = 0.7 // Balance consistency with creativity
        private const val MAX_TOKENS = 300
    }

    /**
     * Generates a lore variation based on parent lore.
     *
     * Maintains consistency with parent while introducing local details appropriate
     * for the chunk level and spatial direction.
     *
     * @param parentLore Parent chunk lore (politics, factions, overall theme)
     * @param level Chunk level being generated
     * @param direction Optional spatial hint ("north", "down", etc.)
     * @return Varied lore (2-4 sentences)
     */
    suspend fun varyLore(
        parentLore: String,
        level: ChunkLevel,
        direction: String? = null
    ): Result<String> {
        val directionHint = if (direction != null) " for $direction direction" else ""

        val systemPrompt = """
            You are a world-building assistant creating hierarchical lore for an underground abyssal dungeon.
            Maintain consistency with parent lore but introduce local details.
            Output exactly 2-4 sentences.

            CRITICAL CONSTRAINT: This is a strictly underground dungeon. Use only stone, cavern,
            underground, and abyssal motifs. NO surface elements (trees, sky, foliage, sun, clouds,
            grass, etc.) unless explicitly marked as magical anomalies.
        """.trimIndent()

        val userContext = """
            Parent lore: $parentLore

            Create a ${level.name}-level lore variation$directionHint.

            Guidelines:
            - Maintain consistency (same faction names, overall themes)
            - Introduce local details (specific inhabitants, patrol routes, geological features)
            - Match granularity to level:
              * WORLD: Global politics, major factions
              * REGION: Regional rulers, large-scale geography
              * ZONE: Local settlements, specific threats
              * SUBZONE: Immediate area, patrol routes
              * SPACE: Room-specific events, current state
            ${if (direction != null) "- Consider spatial implications: 'down' = deeper/darker/older, 'up' = toward surface/lighter, horizontal = different cavern systems" else ""}
            - Use underground vocabulary: caverns, tunnels, chasms, stone passages, darkness, depths, etc.
            - NO surface elements: no trees, sky, sun, clouds, grass, forest, foliage, etc.

            Output 2-4 sentences only.
        """.trimIndent()

        return try {
            val response = llmClient.chatCompletion(
                modelId = MODEL,
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = MAX_TOKENS,
                temperature = LORE_TEMPERATURE
            )

            val lore = response.choices.firstOrNull()?.message?.content?.trim()
                ?: return Result.failure(Exception("LLM returned empty lore"))

            Result.success(lore)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to generate lore variation: ${e.message}", e))
        }
    }

    /**
     * Blends parent theme with local variation to create a cohesive theme name.
     *
     * Example: "snowy mountain" + "hot caves" → "volcanic tunnels beneath glacier"
     *
     * @param parentTheme Parent biome theme (2-4 words)
     * @param variation Local variation hint
     * @return Blended theme name (2-4 words)
     */
    suspend fun blendThemes(
        parentTheme: String,
        variation: String
    ): Result<String> {
        val systemPrompt = """
            You are a world-building assistant creating cohesive biome theme names.
            Blend two themes into a single concise name.
            Output exactly 2-4 words.
        """.trimIndent()

        val userContext = """
            Parent theme: $parentTheme
            Local variation: $variation

            Blend these into a single cohesive theme name.

            Examples:
            - "snowy mountain" + "hot caves" → "volcanic tunnels beneath glacier"
            - "ancient crypt" + "flooded" → "drowned tomb halls"
            - "dark forest" + "corrupted" → "blighted woodland"

            Output 2-4 words only.
        """.trimIndent()

        return try {
            val response = llmClient.chatCompletion(
                modelId = MODEL,
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = 50,
                temperature = LORE_TEMPERATURE
            )

            val theme = response.choices.firstOrNull()?.message?.content?.trim()
                ?: return Result.failure(Exception("LLM returned empty theme"))

            Result.success(theme)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to blend themes: ${e.message}", e))
        }
    }
}
