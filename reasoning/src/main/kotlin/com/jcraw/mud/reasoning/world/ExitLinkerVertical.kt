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
 * Vertical exit linking (MUD-034g pure move).
 */
internal object ExitLinkerVertical {

    /**
     * Handles vertical exits by spawning new subzones.
     * TODO: For very deep descents, spawn new zones instead of subzones.
     */
    suspend fun handleVerticalExit(
        worldGenerator: WorldGenerator,
        worldChunkRepo: WorldChunkRepository,
        spacePropsRepo: SpacePropertiesRepository,
        spaceId: String,
        exit: ExitData,
        parentSubzoneId: String,
        parentSubzone: WorldChunkComponent
    ): Result<ExitData> = runCatching {
        // Generate new subzone for vertical movement
        // This creates a new "level" in the dungeon
        // TODO: Track depth and create zones for multi-level dungeons

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
        if (!parentSubzone.children.contains(newSpaceId)) {
            val updated = parentSubzone.addChild(newSpaceId)
            worldChunkRepo.save(updated, parentSubzoneId).getOrThrow()
        }

        exit.copy(targetId = newSpaceId)
    }
}
