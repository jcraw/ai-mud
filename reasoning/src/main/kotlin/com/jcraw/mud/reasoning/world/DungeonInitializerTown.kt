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
 * Town zone generation helpers (MUD-034g pure move).
 * Orchestrator remains on [DungeonInitializer] host (FN residual).
 */
internal object DungeonInitializerTown {

    fun townZoneContext(
        seed: String,
        globalLore: String,
        upperDepthsId: String,
        upperDepthsRegion: WorldChunkComponent
    ): GenerationContext = GenerationContext(
        seed = seed,
        globalLore = globalLore,
        parentChunk = upperDepthsRegion,
        parentChunkId = upperDepthsId,
        level = ChunkLevel.ZONE,
        direction = "town entrance"
    )

    suspend fun generateTownZone(
        worldGenerator: WorldGenerator,
        chunkRepo: WorldChunkRepository,
        seed: String,
        globalLore: String,
        upperDepthsId: String,
        upperDepthsRegion: WorldChunkComponent
    ): Result<Pair<WorldChunkComponent, String>> {
        val zoneContext = townZoneContext(seed, globalLore, upperDepthsId, upperDepthsRegion)
        val (zoneChunk, zoneId) = worldGenerator.generateChunk(zoneContext)
            .getOrElse { return Result.failure(it) }
        chunkRepo.save(zoneChunk, zoneId).getOrElse { return Result.failure(it) }
        val updatedRegion = upperDepthsRegion.copy(children = listOf(zoneId))
        chunkRepo.save(updatedRegion, upperDepthsId).getOrElse { return Result.failure(it) }
        return Result.success(zoneChunk to zoneId)
    }
}
