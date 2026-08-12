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
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.GenerationContext

/**
 * Biome theme resolution and cascade logging (MUD-034g pure move).
 */
internal object WorldGeneratorBiome {

    /**
     * Determine final biome theme for the child chunk.
     * Defaults to parent's biome unless a surface breakout flag is present.
     */
    fun resolveBiomeTheme(
        context: GenerationContext,
        requestedTheme: String
    ): String {
        val parent = context.parentChunk ?: return requestedTheme.ifBlank { WorldGeneratorConsts.ROOT_BIOME }
        val parentTheme = parent.biomeTheme.ifBlank { WorldGeneratorConsts.ROOT_BIOME }
        val breakout = hasSurfaceShift(context)

        val newTheme = when {
            breakout -> "surface_wilderness"
            !context.biomeTheme.isNullOrBlank() -> context.biomeTheme!! // Explicit override from context (safe: null check above)
            parentTheme.isNotBlank() -> parentTheme
            requestedTheme.isNotBlank() -> requestedTheme
            else -> WorldGeneratorConsts.ROOT_BIOME
        }

        val parentId = context.parentChunkId ?: "UNKNOWN_PARENT"
        println("[INHERIT] Child ${context.level.name} from parent $parentId (biome='${parentTheme}'): Setting to '$newTheme'")

        return newTheme
    }

    fun hasSurfaceShift(context: GenerationContext): Boolean {
        val hint = context.direction?.lowercase() ?: return false
        return hint.contains("surface_shift")
    }

    fun enforceRootBiome(
        chunkId: String,
        chunk: WorldChunkComponent,
        context: GenerationContext
    ): WorldChunkComponent {
        if (chunk.level != ChunkLevel.WORLD || context.parentChunk != null) {
            return chunk
        }

        val adjusted = chunk.copy(biomeTheme = WorldGeneratorConsts.ROOT_BIOME)
        val overrideNote = if (chunk.biomeTheme != WorldGeneratorConsts.ROOT_BIOME) " (overrode '${chunk.biomeTheme}')" else ""
        println("[GEN] Root WORLD chunk ID=$chunkId: biomeTheme='${adjusted.biomeTheme}', parent=null$overrideNote")
        return adjusted
    }

    fun logPromptCascade(
        chunkId: String,
        chunk: WorldChunkComponent,
        parentChunk: WorldChunkComponent
    ) {
        val parentTheme = parentChunk.biomeTheme.ifBlank { WorldGeneratorConsts.ROOT_BIOME }
        println("[PROMPT] For chunk $chunkId (${chunk.level}): biome='${chunk.biomeTheme}', inheriting from '$parentTheme'")
    }
}
