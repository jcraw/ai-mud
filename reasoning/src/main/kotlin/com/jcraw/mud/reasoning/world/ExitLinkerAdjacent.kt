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
import com.jcraw.mud.core.world.ExitData

/**
 * Adjacent-subzone and within-subzone exit linking (MUD-034g pure move).
 */
internal object ExitLinkerAdjacent {

    /**
     * Links to an adjacent subzone by generating a space within it.
     */
    suspend fun linkToAdjacentSubzone(
        worldGenerator: WorldGenerator,
        worldChunkRepo: WorldChunkRepository,
        spacePropsRepo: SpacePropertiesRepository,
        spaceId: String,
        exit: ExitData,
        adjacentSubzoneId: String,
        adjacentSubzone: WorldChunkComponent
    ): Result<ExitData> = runCatching {
        // Generate space in adjacent subzone
        val (newSpace, newSpaceId) = worldGenerator.generateSpace(
            parentSubzoneId = adjacentSubzoneId,
            parentSubzone = adjacentSubzone,
            directionHint = exit.direction
        ).getOrThrow()

        // Create reciprocal exit
        val reciprocalDirection = ExitLinkerDirections.createReciprocalExit(exit.direction)
        val reciprocalExit = ExitData(
            targetId = spaceId,
            direction = reciprocalDirection,
            description = ExitLinkerDirections.createReciprocalDescription(
                exit.description, exit.direction, reciprocalDirection
            ),
            conditions = exit.conditions,
            isHidden = exit.isHidden,
            hiddenDifficulty = exit.hiddenDifficulty
        )

        // Add reciprocal exit to new space
        val updatedNewSpace = newSpace.addExit(reciprocalExit)
        spacePropsRepo.save(updatedNewSpace, newSpaceId).getOrThrow()

        // Add to adjacent subzone if not already present
        if (!adjacentSubzone.children.contains(newSpaceId)) {
            val updated = adjacentSubzone.addChild(newSpaceId)
            worldChunkRepo.save(updated, adjacentSubzoneId).getOrThrow()
        }

        exit.copy(targetId = newSpaceId)
    }

    /**
     * Links within the current subzone by generating a new space.
     * Returns the linked exit and updated parent subzone.
     */
    suspend fun linkWithinSubzone(
        worldGenerator: WorldGenerator,
        worldChunkRepo: WorldChunkRepository,
        spacePropsRepo: SpacePropertiesRepository,
        spaceId: String,
        exit: ExitData,
        parentSubzoneId: String,
        parentSubzone: WorldChunkComponent
    ): Result<Pair<ExitData, WorldChunkComponent>> = runCatching {
        val (newSpace, newSpaceId) = worldGenerator.generateSpace(
            parentSubzoneId = parentSubzoneId,
            parentSubzone = parentSubzone,
            directionHint = exit.direction
        ).getOrThrow()

        // Create reciprocal exit
        val reciprocalDirection = ExitLinkerDirections.createReciprocalExit(exit.direction)
        val reciprocalExit = ExitData(
            targetId = spaceId,
            direction = reciprocalDirection,
            description = ExitLinkerDirections.createReciprocalDescription(
                exit.description, exit.direction, reciprocalDirection
            ),
            conditions = exit.conditions,
            isHidden = exit.isHidden,
            hiddenDifficulty = exit.hiddenDifficulty
        )

        // Add reciprocal exit to new space
        val updatedNewSpace = newSpace.addExit(reciprocalExit)
        spacePropsRepo.save(updatedNewSpace, newSpaceId).getOrThrow()

        // Add to parent subzone if not already present
        var updatedSubzone = parentSubzone
        if (!parentSubzone.children.contains(newSpaceId)) {
            updatedSubzone = parentSubzone.addChild(newSpaceId)
            worldChunkRepo.save(updatedSubzone, parentSubzoneId).getOrThrow()
        }

        exit.copy(targetId = newSpaceId) to updatedSubzone
    }
}
