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
import com.jcraw.mud.memory.MemoryManager

/**
 * Vector-store caching helpers for [WorldGenerator] (MUD-034g pure move).
 */
internal object WorldGeneratorCache {

    /**
     * Cache chunk-level lore into the vector store for fast recall.
     */
    suspend fun cacheChunkLoreEntry(
        memoryManager: MemoryManager?,
        chunkId: String,
        chunk: WorldChunkComponent
    ) {
        val manager = memoryManager ?: return
        if (chunk.lore.isBlank() && chunk.biomeTheme.isBlank()) return

        val metadata = mapOf(
            "type" to "chunk_lore",
            "chunkId" to chunkId,
            "chunkLevel" to chunk.level.name
        )
        val mobDensityFormatted = String.format("%.2f", chunk.mobDensity)
        val content = """
            Chunk ${chunk.level.name} [$chunkId]
            Theme: ${chunk.biomeTheme.ifBlank { "unspecified" }}
            Difficulty: ${chunk.difficultyLevel}
            Mob density: $mobDensityFormatted
            Lore: ${chunk.lore.take(600)}
        """.trimIndent()

        manager.remember(content, metadata)
    }

    /**
     * Cache generated space descriptions for reuse on re-entry.
     */
    suspend fun cacheSpaceDescription(
        memoryManager: MemoryManager?,
        spaceId: String,
        name: String,
        description: String,
        parentChunk: WorldChunkComponent
    ) {
        val manager = memoryManager ?: return
        if (description.isBlank()) return

        val metadata = mapOf(
            "type" to "space_description",
            "chunkId" to spaceId,
            "chunkLevel" to ChunkLevel.SPACE.name,
            "parentChunkLevel" to parentChunk.level.name,
            "parentTheme" to parentChunk.biomeTheme
        )
        val content = """
            Space [$spaceId] - ${name.ifBlank { "Unnamed space" }}
            Parent chunk: ${parentChunk.level.name} (${parentChunk.biomeTheme.ifBlank { "unspecified theme" }})
            Description: ${description.take(600)}
        """.trimIndent()

        manager.remember(content, metadata)
    }
}
