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

import com.jcraw.mud.core.world.ExitData
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.repository.WorldChunkRepository

/**
 * Links placeholder exit targets after space generation.
 * Implements two-pass generation: first create chunks, then link exits.
 *
 * Features:
 * - Collapses duplicate directional exits from LLM output
 * - Consults adjacency map to reuse known neighbor chunks
 * - Spawns new subzones/zones for vertical exits (up/down)
 * - Updates adjacency maps when creating new adjacent chunks
 *
 * Thin facade — vertical/adj/directions extracted (MUD-034g).
 */
class ExitLinker(
    private val worldGenerator: WorldGenerator,
    private val worldChunkRepo: WorldChunkRepository,
    private val spacePropsRepo: SpacePropertiesRepository
) {
    /**
     * Links all placeholder exits in a space to actual generated spaces.
     * Creates reciprocal exits in target spaces for bidirectional navigation.
     *
     * @param spaceId The ID of the space whose exits to link
     * @param space The space properties component
     * @param parentSubzoneId The ID of the parent subzone entity
     * @param parentSubzone The parent subzone chunk (for context in generation)
     * @return Result containing updated SpacePropertiesComponent with linked exits
     */
    suspend fun linkExits(
        spaceId: String,
        space: SpacePropertiesComponent,
        parentSubzoneId: String,
        parentSubzone: WorldChunkComponent
    ): Result<SpacePropertiesComponent> = runCatching {
        // Step 1: Collapse duplicate directional exits from LLM output
        val collapsedExits = ExitLinkerDirections.collapseDuplicateExits(space.exits)

        var updatedSpace = space.copy(exits = collapsedExits)
        var currentParentSubzone = parentSubzone
        val linkedExits = mutableListOf<ExitData>()

        // Process each exit
        for (exit in updatedSpace.exits) {
            if (exit.targetId == "PLACEHOLDER") {
                val normalizedDir = exit.direction.trim().lowercase()

                // Step 2: Check if this is a vertical exit (up/down)
                if (ExitLinkerDirections.isVerticalDirection(normalizedDir)) {
                    // Handle vertical exits: spawn new subzone/zone
                    val result = ExitLinkerVertical.handleVerticalExit(
                        worldGenerator, worldChunkRepo, spacePropsRepo,
                        spaceId, exit, parentSubzoneId, currentParentSubzone
                    ).getOrThrow()
                    linkedExits.add(result)
                } else {
                    // Step 3: Check adjacency map for horizontal exits
                    val adjacentSubzoneId = currentParentSubzone.adjacency[normalizedDir]

                    if (adjacentSubzoneId != null) {
                        // Reuse adjacent subzone: generate space within it
                        val adjacentSubzone = worldChunkRepo.findById(adjacentSubzoneId).getOrThrow()
                            ?: throw IllegalStateException("Adjacent subzone not found: $adjacentSubzoneId")

                        val result = ExitLinkerAdjacent.linkToAdjacentSubzone(
                            worldGenerator, worldChunkRepo, spacePropsRepo,
                            spaceId, exit, adjacentSubzoneId, adjacentSubzone
                        ).getOrThrow()
                        linkedExits.add(result)
                    } else {
                        // Generate new space in current subzone
                        val (linkedExit, updatedSubzone) = ExitLinkerAdjacent.linkWithinSubzone(
                            worldGenerator, worldChunkRepo, spacePropsRepo,
                            spaceId, exit, parentSubzoneId, currentParentSubzone
                        ).getOrThrow()
                        linkedExits.add(linkedExit)
                        currentParentSubzone = updatedSubzone
                    }
                }
            } else {
                // Keep existing linked exit
                linkedExits.add(exit)
            }
        }

        // Update space with all linked exits
        updatedSpace = updatedSpace.copy(exits = linkedExits)

        // Save updated space to DB
        spacePropsRepo.save(updatedSpace, spaceId).getOrThrow()

        updatedSpace
    }

    /**
     * Creates the opposite direction for a given direction.
     * Maps cardinal directions to their opposites and attempts to reverse
     * natural language descriptions.
     *
     * Examples:
     * - "north" → "south"
     * - "up" → "down"
     * - "climb ladder" → "descend ladder"
     * - "through door" → "through door" (symmetric)
     *
     * @param direction The original direction
     * @return The reciprocal direction
     */
    fun createReciprocalExit(direction: String): String {
        return ExitLinkerDirections.createReciprocalExit(direction)
    }
}
