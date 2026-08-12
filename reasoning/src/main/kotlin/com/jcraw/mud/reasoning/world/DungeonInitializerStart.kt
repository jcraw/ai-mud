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
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.GenerationContext

/**
 * Starting location pipeline: ZONE → SUBZONE → SPACE (MUD-034g pure move).
 */
internal object DungeonInitializerStart {

    suspend fun generateStartZone(
        worldGenerator: WorldGenerator,
        chunkRepo: WorldChunkRepository,
        seed: String,
        globalLore: String,
        regionId: String,
        regionChunk: WorldChunkComponent
    ): Result<Pair<WorldChunkComponent, String>> {
        val zoneContext = GenerationContext(
            seed = seed,
            globalLore = globalLore,
            parentChunk = regionChunk,
            parentChunkId = regionId,
            level = ChunkLevel.ZONE,
            direction = "entrance"
        )
        val (zoneChunk, zoneId) = worldGenerator.generateChunk(zoneContext).getOrElse { return Result.failure(it) }
        chunkRepo.save(zoneChunk, zoneId).getOrElse { return Result.failure(it) }

        val updatedRegion = regionChunk.copy(children = listOf(zoneId))
        chunkRepo.save(updatedRegion, regionId).getOrElse { return Result.failure(it) }

        return Result.success(zoneChunk to zoneId)
    }

    suspend fun generateStartSubzone(
        worldGenerator: WorldGenerator,
        chunkRepo: WorldChunkRepository,
        seed: String,
        globalLore: String,
        zoneId: String,
        zoneChunk: WorldChunkComponent
    ): Result<Pair<WorldChunkComponent, String>> {
        val subzoneContext = GenerationContext(
            seed = seed,
            globalLore = globalLore,
            parentChunk = zoneChunk,
            parentChunkId = zoneId,
            level = ChunkLevel.SUBZONE,
            direction = "entrance hall"
        )
        val (subzoneChunk, subzoneId) = worldGenerator.generateChunk(subzoneContext).getOrElse { return Result.failure(it) }
        chunkRepo.save(subzoneChunk, subzoneId).getOrElse { return Result.failure(it) }

        val updatedZone = zoneChunk.copy(children = listOf(subzoneId))
        chunkRepo.save(updatedZone, zoneId).getOrElse { return Result.failure(it) }

        return Result.success(subzoneChunk to subzoneId)
    }

    suspend fun generateStartSpace(
        worldGenerator: WorldGenerator,
        chunkRepo: WorldChunkRepository,
        spaceRepo: SpacePropertiesRepository,
        subzoneChunk: WorldChunkComponent,
        subzoneId: String
    ): Result<String> {
        val (space, spaceId) = worldGenerator.generateSpace(subzoneChunk, subzoneId).getOrElse { return Result.failure(it) }
        spaceRepo.save(space, spaceId).getOrElse { return Result.failure(it) }

        val updatedSubzone = subzoneChunk.copy(children = listOf(spaceId))
        chunkRepo.save(updatedSubzone, subzoneId).getOrElse { return Result.failure(it) }

        return Result.success(spaceId)
    }

    /**
     * Generates the starting location (ZONE → SUBZONE → SPACE) for player spawn.
     */
    suspend fun generateStartingLocation(
        worldGenerator: WorldGenerator,
        chunkRepo: WorldChunkRepository,
        spaceRepo: SpacePropertiesRepository,
        seed: String,
        globalLore: String,
        regionId: String
    ): Result<String> {
        val regionChunk = chunkRepo.findById(regionId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Region not found: $regionId"))

        val (zoneChunk, zoneId) = generateStartZone(
            worldGenerator, chunkRepo, seed, globalLore, regionId, regionChunk
        ).getOrElse { return Result.failure(it) }

        val (subzoneChunk, subzoneId) = generateStartSubzone(
            worldGenerator, chunkRepo, seed, globalLore, zoneId, zoneChunk
        ).getOrElse { return Result.failure(it) }

        return generateStartSpace(worldGenerator, chunkRepo, spaceRepo, subzoneChunk, subzoneId)
    }
}
