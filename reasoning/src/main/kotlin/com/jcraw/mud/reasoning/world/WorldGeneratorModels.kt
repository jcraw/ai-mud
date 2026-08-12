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
import kotlinx.serialization.Serializable

/**
 * LLM/JSON models and public generation result (MUD-034g pure move).
 */

@Serializable
internal data class ChunkData(
    val biomeTheme: String,
    val sizeEstimate: Int,
    val mobDensity: Double,
    val difficultyLevel: Int
)

@Serializable
internal data class SpaceData(
    val description: String,
    val exits: List<ExitDataJson>,
    val brightness: Int,
    val terrainType: String
)

@Serializable
internal data class ExitDataJson(
    val direction: String,
    val description: String,
    val targetId: String
)

@Serializable
internal data class SpaceNameAndDescription(
    val name: String,
    val description: String
)

/**
 * Result of chunk generation including graph topology for V3
 *
 * @param chunk The generated chunk component
 * @param chunkId The entity ID for the chunk
 * @param graphNodes List of graph nodes (empty if not SUBZONE or V2 mode)
 */
data class ChunkGenerationResult(
    val chunk: WorldChunkComponent,
    val chunkId: String,
    val graphNodes: List<GraphNodeComponent> = emptyList()
)

/**
 * Shared generation constants for [WorldGenerator] extracts (MUD-034g).
 */
internal object WorldGeneratorConsts {
    const val MODEL = "gpt-4o-mini"
    const val TEMPERATURE = 0.7
    const val MAX_TOKENS = 600
    const val ROOT_BIOME = "abyssal_dungeon"

    const val TRAP_PROBABILITY = 0.15 // 15% chance per space
    const val RESOURCE_PROBABILITY = 0.05 // 5% chance per space
    const val HIDDEN_EXIT_PROBABILITY = 0.20 // 20% of exits are hidden
}
