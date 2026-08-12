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

import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.GenerationContext

/**
 * placeHiddenExit pipeline stages (MUD-034g pure move).
 * Fragmented so no Added FN exceeds global FN_E 250.
 */
internal object HiddenExitPlacerPlace {

    suspend fun loadRegionOrFail(
        chunkRepo: WorldChunkRepository,
        midDepthsRegionId: String
    ): Result<WorldChunkComponent> {
        val midDepthsRegion = chunkRepo.findById(midDepthsRegionId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Mid Depths region not found: $midDepthsRegionId"))
        return Result.success(midDepthsRegion)
    }

    fun zone2Context(
        seed: String,
        globalLore: String,
        midDepthsRegionId: String,
        midDepthsRegion: WorldChunkComponent
    ): GenerationContext = GenerationContext(
        seed = seed,
        globalLore = globalLore,
        parentChunk = midDepthsRegion,
        parentChunkId = midDepthsRegionId,
        level = ChunkLevel.ZONE,
        direction = "deeper passage"
    )

    suspend fun generateZone2(
        worldGenerator: WorldGenerator,
        chunkRepo: WorldChunkRepository,
        midDepthsRegionId: String,
        midDepthsRegion: WorldChunkComponent,
        seed: String,
        globalLore: String
    ): Result<String> {
        val zoneContext = zone2Context(seed, globalLore, midDepthsRegionId, midDepthsRegion)
        val (zoneChunk, zoneId) = worldGenerator.generateChunk(zoneContext)
            .getOrElse { return Result.failure(it) }
        chunkRepo.save(zoneChunk, zoneId).getOrElse { return Result.failure(it) }
        val updatedRegion = midDepthsRegion.copy(children = midDepthsRegion.children + zoneId)
        chunkRepo.save(updatedRegion, midDepthsRegionId).getOrElse { return Result.failure(it) }
        return Result.success(zoneId)
    }

    suspend fun resolveZone2Id(
        worldGenerator: WorldGenerator,
        chunkRepo: WorldChunkRepository,
        midDepthsRegionId: String,
        midDepthsRegion: WorldChunkComponent,
        seed: String,
        globalLore: String
    ): Result<String> {
        if (midDepthsRegion.children.size >= 2) {
            return Result.success(midDepthsRegion.children[1]) // Second zone (index 1)
        }
        return generateZone2(
            worldGenerator, chunkRepo, midDepthsRegionId, midDepthsRegion, seed, globalLore
        )
    }

    suspend fun generateSubzone(
        worldGenerator: WorldGenerator,
        chunkRepo: WorldChunkRepository,
        zone2Id: String,
        zone2: WorldChunkComponent,
        seed: String,
        globalLore: String
    ): Result<String> {
        val subzoneContext = GenerationContext(
            seed = seed,
            globalLore = globalLore,
            parentChunk = zone2,
            parentChunkId = zone2Id,
            level = ChunkLevel.SUBZONE,
            direction = "hidden chamber"
        )
        val (subzoneChunk, subzoneId) = worldGenerator.generateChunk(subzoneContext)
            .getOrElse { return Result.failure(it) }

        chunkRepo.save(subzoneChunk, subzoneId).getOrElse { return Result.failure(it) }

        val updatedZone = zone2.copy(children = listOf(subzoneId))
        chunkRepo.save(updatedZone, zone2Id).getOrElse { return Result.failure(it) }

        return Result.success(subzoneId)
    }

    suspend fun resolveTargetSubzoneId(
        worldGenerator: WorldGenerator,
        chunkRepo: WorldChunkRepository,
        zone2Id: String,
        zone2: WorldChunkComponent,
        seed: String,
        globalLore: String
    ): Result<String> {
        if (zone2.children.isNotEmpty()) {
            return Result.success(zone2.children.first()) // Use first subzone
        }
        return generateSubzone(worldGenerator, chunkRepo, zone2Id, zone2, seed, globalLore)
    }

    suspend fun generateSpace(
        worldGenerator: WorldGenerator,
        chunkRepo: WorldChunkRepository,
        spaceRepo: SpacePropertiesRepository,
        targetSubzoneId: String,
        targetSubzone: WorldChunkComponent
    ): Result<String> {
        val (spaceProps, spaceId) = worldGenerator.generateSpace(targetSubzone, targetSubzoneId)
            .getOrElse { return Result.failure(it) }

        spaceRepo.save(spaceProps, spaceId).getOrElse { return Result.failure(it) }

        val updatedSubzone = targetSubzone.copy(children = listOf(spaceId))
        chunkRepo.save(updatedSubzone, targetSubzoneId).getOrElse { return Result.failure(it) }

        return Result.success(spaceId)
    }

    suspend fun resolveTargetSpaceId(
        worldGenerator: WorldGenerator,
        chunkRepo: WorldChunkRepository,
        spaceRepo: SpacePropertiesRepository,
        targetSubzoneId: String,
        targetSubzone: WorldChunkComponent
    ): Result<String> {
        if (targetSubzone.children.isNotEmpty()) {
            return Result.success(targetSubzone.children.first()) // Use first space
        }
        return generateSpace(worldGenerator, chunkRepo, spaceRepo, targetSubzoneId, targetSubzone)
    }

    fun applyHiddenExitsToSpace(space: SpacePropertiesComponent): SpacePropertiesComponent {
        val surfaceWildernessId = "world_region_surface_wilderness"
        val hiddenExits = HiddenExitPlacerExits.buildHiddenExits(surfaceWildernessId)

        // Add all three exits to space (multiple paths to same destination)
        var updatedSpace = space
        for (exit in hiddenExits) {
            updatedSpace = updatedSpace.addExit(exit)
        }
        return updatedSpace.copy(
            description = space.description + "\n\nSomething feels different about this place. " +
                          "Perhaps there are secrets hidden here for those with the skill to find them."
        )
    }

}
