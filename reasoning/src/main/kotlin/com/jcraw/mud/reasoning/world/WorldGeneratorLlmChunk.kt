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

import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.GenerationContext
import com.jcraw.sophia.llm.LLMClient
import kotlinx.serialization.json.Json

/**
 * LLM chunk-data generation (MUD-034g pure move).
 */
internal object WorldGeneratorLlmChunk {

    private val json = Json { ignoreUnknownKeys = true }

    val SYSTEM_PROMPT = """
            You are a world-building assistant for a fantasy dungeon MUD.
            Generate chunk data in JSON format only. No additional text.
        """.trimIndent()

    val DUNGEON_CONSTRAINTS =
        "Strictly underground abyssal dungeon—enclosed stone/cavern motifs only; no surface elements like trees, sky, or foliage unless explicitly magical anomalies. Emphasize vertical descent, increasing darkness/peril with depth."

    fun sizeRangeForLevel(level: ChunkLevel): String = when (level) {
        ChunkLevel.WORLD -> "global"
        ChunkLevel.REGION -> "1000-10000"
        ChunkLevel.ZONE -> "100-500"
        ChunkLevel.SUBZONE -> "5-100"
        ChunkLevel.SPACE -> "1"
    }

    fun inheritanceDirective(context: GenerationContext, parentLore: String, parentTheme: String): String {
        return if (context.parentChunk != null) {
            "Inherit from parent: $parentTheme (${parentLore.take(200)}) but vary toward deeper grit, claustrophobia, and abyssal descent."
        } else {
            "Inherit from parent: None (root level) — establish the primordial abyssal tone."
        }
    }

    fun parentLoreTheme(context: GenerationContext): Pair<String, String> {
        val parentLore = context.parentChunk?.lore ?: "None (root level)"
        val parentTheme = context.parentChunk?.biomeTheme?.takeIf { it.isNotBlank() } ?: "None (root level)"
        return parentLore to parentTheme
    }

    fun sizeEstimateExpr(context: GenerationContext, sizeRange: String): String =
        if (context.level == ChunkLevel.SPACE) "1" else "number in range $sizeRange"

    fun headerBlock(
        context: GenerationContext,
        lore: String,
        parentLore: String,
        parentTheme: String,
        inheritance: String,
        sizeRange: String
    ): String = """
            Seed: ${context.seed}
            Level: ${context.level.name}
            Parent lore: $parentLore
            Parent theme: $parentTheme
            $inheritance
            Generated lore: $lore
            Size range: $sizeRange spaces
            Direction: ${context.direction ?: "N/A"}
        """.trimIndent()

    fun schemaBlock(sizeEstimate: String): String = """
            Output JSON only:
            {
              "biomeTheme": "2-4 word theme matching lore",
              "sizeEstimate": $sizeEstimate,
              "mobDensity": "0.0-1.0 (0=empty, 1=packed)",
              "difficultyLevel": "1-20 (scales with depth)"
            }
        """.trimIndent()

    fun buildUserContext(context: GenerationContext, lore: String): String {
        val sizeRange = sizeRangeForLevel(context.level)
        val (parentLore, parentTheme) = parentLoreTheme(context)
        val inheritance = inheritanceDirective(context, parentLore, parentTheme)
        val header = headerBlock(context, lore, parentLore, parentTheme, inheritance, sizeRange)
        val schema = schemaBlock(sizeEstimateExpr(context, sizeRange))
        return """
            $header

            $DUNGEON_CONSTRAINTS

            Generate chunk details matching this lore and level.

            $schema
        """.trimIndent()
    }

    fun stripJsonFences(content: String): String = content
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    suspend fun generateChunkData(
        llmClient: LLMClient,
        context: GenerationContext,
        lore: String
    ): Result<ChunkData> {
        val userContext = buildUserContext(context, lore)
        return try {
            val response = llmClient.chatCompletion(
                modelId = WorldGeneratorConsts.MODEL,
                systemPrompt = SYSTEM_PROMPT,
                userContext = userContext,
                maxTokens = 200,
                temperature = WorldGeneratorConsts.TEMPERATURE
            )
            val content = response.choices.firstOrNull()?.message?.content?.trim()
                ?: return Result.failure(Exception("LLM returned empty response"))
            Result.success(json.decodeFromString(stripJsonFences(content)))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to generate chunk data: ${e.message}", e))
        }
    }
}
