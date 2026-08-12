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

import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.repository.WorldChunkRepository

/**
 * Places hidden exits that lead to new regions (e.g., Surface Wilderness)
 * Hidden exits reward high Perception or specific skills
 *
 * Thin facade — place/surface stages extracted (MUD-034g).
 * Residual orchestrators on host for FN_E (Added cannot grandfather).
 */
class HiddenExitPlacer(
    private val worldGenerator: WorldGenerator,
    private val chunkRepo: WorldChunkRepository,
    private val spaceRepo: SpacePropertiesRepository
) {
    /**
     * Place hidden exit to Surface Wilderness in Mid Depths region
     */
    suspend fun placeHiddenExit(
        midDepthsRegionId: String,
        seed: String,
        globalLore: String
    ): Result<String> {
        val midDepthsRegion = HiddenExitPlacerPlace.loadRegionOrFail(chunkRepo, midDepthsRegionId)
            .getOrElse { return Result.failure(it) }

        val zone2Id = HiddenExitPlacerPlace.resolveZone2Id(
            worldGenerator, chunkRepo, midDepthsRegionId, midDepthsRegion, seed, globalLore
        ).getOrElse { return Result.failure(it) }

        val zone2 = chunkRepo.findById(zone2Id).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Zone 2 not found: $zone2Id"))

        val targetSubzoneId = HiddenExitPlacerPlace.resolveTargetSubzoneId(
            worldGenerator, chunkRepo, zone2Id, zone2, seed, globalLore
        ).getOrElse { return Result.failure(it) }

        val targetSubzone = chunkRepo.findById(targetSubzoneId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Subzone not found: $targetSubzoneId"))

        val targetSpaceId = HiddenExitPlacerPlace.resolveTargetSpaceId(
            worldGenerator, chunkRepo, spaceRepo, targetSubzoneId, targetSubzone
        ).getOrElse { return Result.failure(it) }

        val space = spaceRepo.findByChunkId(targetSpaceId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Space not found: $targetSpaceId"))

        val updatedSpace = HiddenExitPlacerPlace.applyHiddenExitsToSpace(space)
        spaceRepo.save(updatedSpace, targetSpaceId).getOrElse { return Result.failure(it) }

        return Result.success(targetSpaceId)
    }

    /**
     * Generate Surface Wilderness region (lazy generation on first access)
     */
    suspend fun generateSurfaceWilderness(
        seed: String,
        globalLore: String,
        worldId: String
    ): Result<String> {
        val worldChunk = chunkRepo.findById(worldId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("World not found: $worldId"))

        val regionContext = HiddenExitPlacerSurface.surfaceRegionContext(
            seed, globalLore, worldId, worldChunk
        )
        val (surfaceRegion, _) = worldGenerator.generateChunk(regionContext)
            .getOrElse { return Result.failure(it) }

        val adjustedRegion = surfaceRegion.copy(
            difficultyLevel = 15,
            biomeTheme = "wilderness",
            lore = HiddenExitPlacerSurface.SURFACE_LORE
        )

        chunkRepo.save(adjustedRegion, "world_region_surface_wilderness")
            .getOrElse { return Result.failure(it) }

        val updatedWorld = worldChunk.copy(children = worldChunk.children + "world_region_surface_wilderness")
        chunkRepo.save(updatedWorld, worldId).getOrElse { return Result.failure(it) }

        return Result.success("world_region_surface_wilderness")
    }

    fun canDiscoverHiddenExit(
        perceptionLevel: Int,
        lockpickingLevel: Int,
        strengthLevel: Int
    ): Boolean {
        return HiddenExitPlacerSurface.canDiscoverHiddenExit(
            perceptionLevel, lockpickingLevel, strengthLevel
        )
    }

    fun getHiddenExitHint(
        perceptionLevel: Int,
        lockpickingLevel: Int,
        strengthLevel: Int
    ): String {
        return HiddenExitPlacerSurface.getHiddenExitHint(
            perceptionLevel, lockpickingLevel, strengthLevel
        )
    }
}
