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
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.GenerationContext

/**
 * Surface wilderness generation + discovery helpers (MUD-034g pure move).
 */
internal object HiddenExitPlacerSurface {

    val SURFACE_LORE = """
                You emerge from the darkness into blinding daylight. A vast wilderness stretches before you -
                ancient forests, rolling hills, and distant mountains. The air is fresh and clean, a stark
                contrast to the stale dungeon depths. Freedom at last.

                This is the Surface Wilderness, a vast open world awaiting exploration.
            """.trimIndent()

    fun surfaceRegionContext(
        seed: String,
        globalLore: String,
        worldId: String,
        worldChunk: WorldChunkComponent
    ): GenerationContext {
        return GenerationContext(
            seed = seed,
            globalLore = "$globalLore\n\nYou have escaped the dungeon depths and emerged into the Surface Wilderness.",
            parentChunk = worldChunk.copy(
                lore = "${worldChunk.lore}\n\nThe Surface Wilderness awaits those who escape the Abyss."
            ),
            parentChunkId = worldId,
            level = ChunkLevel.REGION,
            direction = "surface"
        )
    }

    fun canDiscoverHiddenExit(
        perceptionLevel: Int,
        lockpickingLevel: Int,
        strengthLevel: Int
    ): Boolean {
        return perceptionLevel >= 40 ||
               lockpickingLevel >= 30 ||
               strengthLevel >= 50
    }

    fun getHiddenExitHint(
        perceptionLevel: Int,
        lockpickingLevel: Int,
        strengthLevel: Int
    ): String {
        return when {
            perceptionLevel >= 40 -> "Your keen perception reveals a faint crack in the wall..."
            lockpickingLevel >= 30 -> "You notice a hidden door mechanism that you could pick..."
            strengthLevel >= 50 -> "This weak wall could be broken through with enough force..."
            perceptionLevel >= 30 -> "You sense something unusual about this place, but can't quite make it out. (Perception 40 needed)"
            lockpickingLevel >= 20 -> "There might be a hidden mechanism here. (Lockpicking 30 needed)"
            strengthLevel >= 40 -> "The wall seems weak in places. (Strength 50 needed)"
            else -> "The walls appear solid. Perhaps higher skills would reveal secrets."
        }
    }
}
