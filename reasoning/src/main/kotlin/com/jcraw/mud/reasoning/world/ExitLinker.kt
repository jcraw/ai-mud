package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.world.ExitData
import com.jcraw.mud.core.world.GenerationContext
import com.jcraw.mud.core.world.SpacePropertiesComponent
import com.jcraw.mud.core.world.WorldChunkComponent
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.repository.WorldChunkRepository

/**
 * Links placeholder exit targets after space generation.
 * Implements two-pass generation: first create chunks, then link exits.
 *
 * This avoids circular dependencies where Exit A needs to know Exit B's ID
 * before Exit B has been generated.
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
     * @param parentSubzone The parent subzone chunk (for context in generation)
     * @return Result containing updated SpacePropertiesComponent with linked exits
     */
    suspend fun linkExits(
        spaceId: String,
        space: SpacePropertiesComponent,
        parentSubzone: WorldChunkComponent
    ): Result<SpacePropertiesComponent> = runCatching {
        var updatedSpace = space
        val linkedExits = mutableMapOf<String, ExitData>()

        // Process each exit
        for ((direction, exit) in space.exits) {
            if (exit.targetId == "PLACEHOLDER") {
                // Generate adjacent space in this direction
                val (newSpace, newSpaceId) = worldGenerator.generateSpace(
                    parentSubzoneId = parentSubzone.id ?: error("Parent subzone has no ID"),
                    parentSubzone = parentSubzone
                ).getOrThrow()

                // Update this exit to point to new space
                val linkedExit = exit.copy(targetId = newSpaceId)
                linkedExits[direction] = linkedExit

                // Create reciprocal exit in target space
                val reciprocalDirection = createReciprocalExit(direction)
                val reciprocalExit = ExitData(
                    targetId = spaceId,
                    direction = reciprocalDirection,
                    description = createReciprocalDescription(exit.description, direction, reciprocalDirection),
                    conditions = exit.conditions,  // Same conditions apply in reverse
                    isHidden = exit.isHidden,
                    hiddenDifficulty = exit.hiddenDifficulty
                )

                // Add reciprocal exit to new space
                val updatedNewSpace = newSpace.addExit(reciprocalDirection, reciprocalExit)

                // Save new space to DB
                spacePropsRepo.save(updatedNewSpace, newSpaceId).getOrThrow()
            } else {
                // Keep existing linked exit
                linkedExits[direction] = exit
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
