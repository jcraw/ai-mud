package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.world.ExitData
import com.jcraw.mud.core.world.GenerationContext
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
        val collapsedExits = collapseDuplicateExits(space.exits)

        var updatedSpace = space.copy(exits = collapsedExits)
        var currentParentSubzone = parentSubzone
        val linkedExits = mutableListOf<ExitData>()

        // Process each exit
        for (exit in updatedSpace.exits) {
            if (exit.targetId == "PLACEHOLDER") {
                val normalizedDir = exit.direction.trim().lowercase()

                // Step 2: Check if this is a vertical exit (up/down)
                if (isVerticalDirection(normalizedDir)) {
                    // Handle vertical exits: spawn new subzone/zone
                    val result = handleVerticalExit(
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

                        val result = linkToAdjacentSubzone(
                            spaceId, exit, adjacentSubzoneId, adjacentSubzone
                        ).getOrThrow()
                        linkedExits.add(result)
                    } else {
                        // Generate new space in current subzone
                        val (linkedExit, updatedSubzone) = linkWithinSubzone(
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
     * Collapses duplicate exits in the same direction, keeping the first occurrence.
     */
    private fun collapseDuplicateExits(exits: List<ExitData>): List<ExitData> {
        val seen = mutableSetOf<String>()
        return exits.filter { exit ->
            val normalized = exit.direction.trim().lowercase()
            if (normalized in seen) {
                false
            } else {
                seen.add(normalized)
                true
            }
        }
    }

    /**
     * Checks if a direction is vertical (up/down).
     */
    private fun isVerticalDirection(direction: String): Boolean {
        return direction in setOf("u", "up", "d", "down") ||
               direction.contains("climb") || direction.contains("descend") ||
               direction.contains("ascend") || direction.contains("ladder") ||
               direction.contains("stairs")
    }

    /**
     * Handles vertical exits by spawning new subzones.
     * TODO: For very deep descents, spawn new zones instead of subzones.
     */
    private suspend fun handleVerticalExit(
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
        val reciprocalDirection = createReciprocalExit(exit.direction)
        val reciprocalExit = ExitData(
            targetId = spaceId,
            direction = reciprocalDirection,
            description = createReciprocalDescription(exit.description, exit.direction, reciprocalDirection),
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

    /**
     * Links to an adjacent subzone by generating a space within it.
     */
    private suspend fun linkToAdjacentSubzone(
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
        val reciprocalDirection = createReciprocalExit(exit.direction)
        val reciprocalExit = ExitData(
            targetId = spaceId,
            direction = reciprocalDirection,
            description = createReciprocalDescription(exit.description, exit.direction, reciprocalDirection),
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
    private suspend fun linkWithinSubzone(
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
        val reciprocalDirection = createReciprocalExit(exit.direction)
        val reciprocalExit = ExitData(
            targetId = spaceId,
            direction = reciprocalDirection,
            description = createReciprocalDescription(exit.description, exit.direction, reciprocalDirection),
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
        val normalized = direction.trim().lowercase()

        return when (normalized) {
            "n", "north" -> "south"
            "s", "south" -> "north"
            "e", "east" -> "west"
            "w", "west" -> "east"
            "ne", "northeast" -> "southwest"
            "nw", "northwest" -> "southeast"
            "se", "southeast" -> "northwest"
            "sw", "southwest" -> "northeast"
            "u", "up" -> "down"
            "d", "down" -> "up"
            else -> reverseNaturalLanguageDirection(normalized)
        }
    }

    /**
     * Attempts to reverse a natural language direction description.
     * Uses simple keyword substitution for common patterns.
     *
     * @param description The original description
     * @return A reversed description if possible, otherwise the original
     */
    private fun reverseNaturalLanguageDirection(description: String): String {
        return when {
            description.contains("climb") -> description.replace("climb", "descend")
            description.contains("ascend") -> description.replace("ascend", "descend")
            description.contains("descend") -> description.replace("descend", "ascend")
            description.contains("enter") -> description.replace("enter", "exit")
            description.contains("exit") -> description.replace("exit", "enter")
            description.contains("through") -> description // Symmetric
            description.contains("into") -> description.replace("into", "out of")
            description.contains("out of") -> description.replace("out of", "into")
            else -> description  // Can't reverse, keep original
        }
    }

    /**
     * Creates a description for the reciprocal exit based on the original.
     * Attempts to maintain narrative coherence.
     *
     * @param originalDescription The original exit description
     * @param originalDirection The original direction
     * @param reciprocalDirection The reciprocal direction
     * @return A description for the reciprocal exit
     */
    private fun createReciprocalDescription(
        originalDescription: String,
        originalDirection: String,
        reciprocalDirection: String
    ): String {
        // For cardinal directions, create a simple description
        if (reciprocalDirection in setOf("north", "south", "east", "west", "up", "down",
                "northeast", "northwest", "southeast", "southwest")) {
            return "A passage leading $reciprocalDirection"
        }

        // For natural language, try to preserve the original description with reversed direction
        return originalDescription.replace(
            originalDirection,
            reciprocalDirection,
            ignoreCase = true
        ).ifEmpty {
            "The way back"
        }
    }
}
