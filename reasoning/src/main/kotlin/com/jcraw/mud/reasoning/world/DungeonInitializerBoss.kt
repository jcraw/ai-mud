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
 * Boss lair helpers (MUD-034g pure move).
 * Orchestrator on [DungeonInitializer] host.
 */
internal object DungeonInitializerBoss {

    fun bossZoneContext(
        seed: String,
        globalLore: String,
        abyssalCoreId: String,
        abyssalCoreRegion: WorldChunkComponent
    ): GenerationContext = GenerationContext(
        seed = seed,
        globalLore = globalLore,
        parentChunk = abyssalCoreRegion,
        parentChunkId = abyssalCoreId,
        level = ChunkLevel.ZONE,
        direction = "abyssal depths"
    )

    fun bossSubzoneContext(
        seed: String,
        globalLore: String,
        zoneId: String,
        zoneChunk: WorldChunkComponent
    ): GenerationContext = GenerationContext(
        seed = seed,
        globalLore = globalLore,
        parentChunk = zoneChunk,
        parentChunkId = zoneId,
        level = ChunkLevel.SUBZONE,
        direction = "throne room"
    )

    suspend fun generateBossZone(
        worldGenerator: WorldGenerator,
        chunkRepo: WorldChunkRepository,
        seed: String,
        globalLore: String,
        abyssalCoreId: String,
        abyssalCoreRegion: WorldChunkComponent
    ): Result<Pair<WorldChunkComponent, String>> {
        val ctx = bossZoneContext(seed, globalLore, abyssalCoreId, abyssalCoreRegion)
        val (zoneChunk, zoneId) = worldGenerator.generateChunk(ctx)
            .getOrElse { return Result.failure(it) }
        chunkRepo.save(zoneChunk, zoneId).getOrElse { return Result.failure(it) }
        chunkRepo.save(abyssalCoreRegion.copy(children = listOf(zoneId)), abyssalCoreId)
            .getOrElse { return Result.failure(it) }
        return Result.success(zoneChunk to zoneId)
    }

    suspend fun generateBossSubzone(
        worldGenerator: WorldGenerator,
        chunkRepo: WorldChunkRepository,
        seed: String,
        globalLore: String,
        zoneId: String,
        zoneChunk: WorldChunkComponent
    ): Result<Pair<WorldChunkComponent, String>> {
        val ctx = bossSubzoneContext(seed, globalLore, zoneId, zoneChunk)
        val (subzoneChunk, subzoneId) = worldGenerator.generateChunk(ctx)
            .getOrElse { return Result.failure(it) }
        chunkRepo.save(subzoneChunk, subzoneId).getOrElse { return Result.failure(it) }
        chunkRepo.save(zoneChunk.copy(children = listOf(subzoneId)), zoneId)
            .getOrElse { return Result.failure(it) }
        return Result.success(subzoneChunk to subzoneId)
    }
}
