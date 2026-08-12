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

import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.world.NodeType
import com.jcraw.sophia.llm.LLMClient
import kotlinx.serialization.json.Json

/**
 * Lazy-fill node name/description LLM generation (MUD-034g pure move).
 */
internal object WorldGeneratorNodeContent {

    private val json = Json { ignoreUnknownKeys = true }

    val SYSTEM_PROMPT = """
            You are a world-building assistant for a fantasy dungeon MUD.
            Generate atmospheric space names and descriptions in JSON format only.
        """.trimIndent()

    val NAME_DESC_INSTRUCTIONS = """
            Generate a vivid name and description for this space.
            Name should be 2-4 words, evocative and thematic (e.g., "Treacherous Alley", "Crystal Cavern").
            IMPORTANT: The name must be unique and different from any existing names listed above.
            Description should be 2-3 sentences reflecting the node type and available exits.
            Keep exit mentions directional and atmospheric; do not invent specific destinations (towns, villages, etc.) unless explicitly referenced in the lore or exit list.
        """.trimIndent()

    val NAME_DESC_SCHEMA = """
            Output JSON only:
            {
              "name": "space name",
              "description": "vivid 2-3 sentence description"
            }
        """.trimIndent()

    fun nodeTypeDescription(nodeType: NodeType): String = when (nodeType) {
        is NodeType.Hub -> "safe zone or gathering point"
        is NodeType.Linear -> "corridor or passage"
        is NodeType.Branching -> "junction or crossroads"
        is NodeType.DeadEnd -> "dead-end chamber"
        is NodeType.TreasureRoom -> "treasure vault filled with pedestals"
        is NodeType.Boss -> "ominous boss chamber"
        is NodeType.Frontier -> "unexplored frontier"
        is NodeType.Questable -> "significant quest location"
    }

    fun exitContext(node: GraphNodeComponent): Pair<String, String> {
        val visible = node.neighbors.filterNot { it.hidden }.map { it.direction }
        val exitDirections = if (visible.isEmpty()) {
            "None (visible paths are concealed or require discovery)"
        } else {
            visible.joinToString(", ")
        }
        val hiddenHint = if (node.neighbors.any { it.hidden }) {
            "\nHidden exits: ${node.neighbors.count { it.hidden }} (hint at secrets without naming directions)"
        } else {
            ""
        }
        return exitDirections to hiddenHint
    }

    fun existingNamesContext(existingNames: Set<String>): String {
        return if (existingNames.isNotEmpty()) {
            "\nExisting space names in this area (avoid duplicates): ${existingNames.joinToString(", ")}"
        } else {
            ""
        }
    }

    fun buildUserContext(
        chunk: WorldChunkComponent,
        nodeTypeDescription: String,
        exitDirections: String,
        hiddenHint: String,
        existingNamesContext: String
    ): String = """
            Theme: ${chunk.biomeTheme}
            Lore: ${chunk.lore}
            Node Type: $nodeTypeDescription
            Exits: $exitDirections$hiddenHint$existingNamesContext

            $NAME_DESC_INSTRUCTIONS

            $NAME_DESC_SCHEMA
        """.trimIndent()

    fun composeUserContext(
        node: GraphNodeComponent,
        chunk: WorldChunkComponent,
        chunkId: String,
        generatedNamesPerChunk: Map<String, Set<String>>
    ): String {
        val typeDesc = nodeTypeDescription(node.type)
        val (exitDirections, hiddenHint) = exitContext(node)
        val namesCtx = existingNamesContext(generatedNamesPerChunk[chunkId] ?: emptySet())
        return buildUserContext(chunk, typeDesc, exitDirections, hiddenHint, namesCtx)
    }

    fun parseNameDescription(content: String): Pair<String, String> {
        val data = json.decodeFromString<SpaceNameAndDescription>(
            WorldGeneratorLlmChunk.stripJsonFences(content)
        )
        return data.name to data.description
    }

    /**
     * Call LLM and parse name/description JSON (orchestrator residual may live on host).
     */
    suspend fun requestNameDescription(
        llmClient: LLMClient,
        userContext: String
    ): Result<Pair<String, String>> {
        return try {
            val response = llmClient.chatCompletion(
                modelId = WorldGeneratorConsts.MODEL,
                systemPrompt = SYSTEM_PROMPT,
                userContext = userContext,
                maxTokens = 250,
                temperature = WorldGeneratorConsts.TEMPERATURE
            )
            val content = response.choices.firstOrNull()?.message?.content?.trim()
                ?: return Result.failure(Exception("LLM returned empty response"))
            Result.success(parseNameDescription(content))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to generate node name and description: ${e.message}", e))
        }
    }
}
