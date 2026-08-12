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
import com.jcraw.mud.core.world.NodeType
import com.jcraw.mud.core.world.TerrainType

/**
 * Node property heuristics for lazy-fill (MUD-034g pure move).
 */
internal object WorldGeneratorNodeProps {

    /**
     * V3: Determine brightness and terrain based on node type.
     * Heuristic defaults that match node purpose.
     */
    fun determineNodeProperties(
        nodeType: NodeType,
        chunk: WorldChunkComponent
    ): Pair<Int, TerrainType> {
        return when (nodeType) {
            is NodeType.Hub -> 70 to TerrainType.NORMAL // Well-lit, safe
            is NodeType.Linear -> 40 to TerrainType.NORMAL // Dim passages
            is NodeType.Branching -> 50 to TerrainType.NORMAL // Moderate light
            is NodeType.DeadEnd -> 30 to TerrainType.DIFFICULT // Dark, challenging
            is NodeType.TreasureRoom -> 65 to TerrainType.NORMAL // Highlight treasures
            is NodeType.Boss -> 60 to TerrainType.NORMAL // Dramatic lighting
            is NodeType.Frontier -> 20 to TerrainType.DIFFICULT // Unexplored, rough
            is NodeType.Questable -> 55 to TerrainType.NORMAL // Interesting, accessible
        }
    }

    fun parseTerrainType(raw: String): TerrainType {
        val normalized = raw.trim().uppercase()
        return runCatching { TerrainType.valueOf(normalized) }
            .getOrElse {
                println("[WARN] Unknown terrain type '$raw', defaulting to NORMAL")
                TerrainType.NORMAL
            }
    }
}
