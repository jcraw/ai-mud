@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "UnusedParameter",
    "TooGenericExceptionCaught",
    "TooGenericExceptionThrown",
    "SwallowedException",
    "WildcardImport",
    "MayBeConst",
    "ImplicitDefaultLocale",
    "ForbiddenComment",
    "UnusedPrivateProperty",
)

package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.sophia.llm.LLMClient
import kotlinx.serialization.json.Json

/**
 * LLM space-data generation (MUD-034g pure move).
 */
internal object WorldGeneratorLlmSpace {

    private val json = Json { ignoreUnknownKeys = true }

    val SYSTEM_PROMPT = """
            You are a world-building assistant for a fantasy dungeon MUD.
            Generate space (room) data in JSON format only. No additional text.
        """.trimIndent()

    val SPACE_REQUIREMENTS_TEMPLATE = """
            Generate a room/space with:
            - Vivid description (2-3 sentences)
            - 3-6 exits (mix cardinal directions like "north", "south" and descriptive like "climb ladder", "through archway")
            - If Direction Hint references vertical movement (e.g., "down", "climb ladder"), ensure at least one exit that fits that motion
            - Describe how this space lies RELATIVE so navigation text stays coherent
            - Brightness (0=pitch black, 50=dim, 100=bright)
            - Terrain type (NORMAL, DIFFICULT, or IMPASSABLE)
        """.trimIndent()

    val SPACE_OUTPUT_SCHEMA = """
            Output JSON only:
            {
              "description": "atmospheric room description",
              "exits": [
                {"direction": "north", "description": "dark passage", "targetId": "PLACEHOLDER"},
                {"direction": "climb ladder", "description": "rusty iron ladder leading up", "targetId": "PLACEHOLDER"}
              ],
              "brightness": 50,
              "terrainType": "NORMAL"
            }
        """.trimIndent()

    fun buildUserContext(
        parentSubzone: WorldChunkComponent,
        directionHint: String?
    ): String {
        val relative = directionHint ?: "relative to its parent"
        val requirements = SPACE_REQUIREMENTS_TEMPLATE.replace("RELATIVE", relative)
        return """
            Theme: ${parentSubzone.biomeTheme}
            Lore: ${parentSubzone.lore}
            Difficulty: ${parentSubzone.difficultyLevel}
            Direction Hint: ${directionHint ?: "unspecified"}

            $requirements

            $SPACE_OUTPUT_SCHEMA
        """.trimIndent()
    }

    suspend fun generateSpaceData(
        llmClient: LLMClient,
        parentSubzone: WorldChunkComponent,
        directionHint: String?
    ): Result<SpaceData> {
        val userContext = buildUserContext(parentSubzone, directionHint)
        return try {
            val response = llmClient.chatCompletion(
                modelId = WorldGeneratorConsts.MODEL,
                systemPrompt = SYSTEM_PROMPT,
                userContext = userContext,
                maxTokens = WorldGeneratorConsts.MAX_TOKENS,
                temperature = WorldGeneratorConsts.TEMPERATURE
            )
            val content = response.choices.firstOrNull()?.message?.content?.trim()
                ?: return Result.failure(Exception("LLM returned empty response"))
            Result.success(json.decodeFromString(WorldGeneratorLlmChunk.stripJsonFences(content)))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to generate space data: ${e.message}", e))
        }
    }
}
