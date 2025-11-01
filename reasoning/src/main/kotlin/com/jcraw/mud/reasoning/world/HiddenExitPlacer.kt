package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.Condition
import com.jcraw.mud.core.world.ExitData
import com.jcraw.mud.core.world.GenerationContext

/**
 * Places hidden exits that lead to new regions (e.g., Surface Wilderness)
 * Hidden exits reward high Perception or specific skills
 */
class HiddenExitPlacer(
    private val worldGenerator: WorldGenerator,
    private val chunkRepo: WorldChunkRepository,
    private val spaceRepo: SpacePropertiesRepository
) {
    /**
     * Place hidden exit to Surface Wilderness in Mid Depths region
     * Creates multiple discovery methods:
     * - Perception 40: Spot hidden crack in wall
     * - Lockpicking 30: Pick hidden lock mechanism
     * - Strength 50: Brute force through weak wall
     *
     * @param midDepthsRegionId Mid Depths region ID
     * @param seed World seed for consistency
     * @param globalLore World lore
     * @return Result with space ID where exit was placed
     */
    suspend fun placeHiddenExit(
        midDepthsRegionId: String,
        seed: String,
        globalLore: String
    ): Result<String> {
        // Load Mid Depths region
        val midDepthsRegion = chunkRepo.findById(midDepthsRegionId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Mid Depths region not found: $midDepthsRegionId"))

        // Find Zone 2 (or create if doesn't exist)
        val zone2Id = if (midDepthsRegion.children.size >= 2) {
            midDepthsRegion.children[1] // Second zone (index 1)
        } else {
            // Generate Zone 2 if it doesn't exist
            val zoneContext = GenerationContext(
                seed = seed,
                globalLore = globalLore,
                parentChunk = midDepthsRegion,
                parentChunkId = midDepthsRegionId,
                level = ChunkLevel.ZONE,
                direction = "deeper passage"
            )
            val (zoneChunk, zoneId) = worldGenerator.generateChunk(zoneContext)
                .getOrElse { return Result.failure(it) }

            chunkRepo.save(zoneChunk, zoneId).getOrElse { return Result.failure(it) }

            // Update region with new zone
            val updatedRegion = midDepthsRegion.copy(children = midDepthsRegion.children + zoneId)
            chunkRepo.save(updatedRegion, midDepthsRegionId).getOrElse { return Result.failure(it) }

            zoneId
        }

        // Load Zone 2
        val zone2 = chunkRepo.findById(zone2Id).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Zone 2 not found: $zone2Id"))

        // Find a random subzone in zone 2 (or create if doesn't exist)
        val targetSubzoneId = if (zone2.children.isNotEmpty()) {
            zone2.children.first() // Use first subzone
        } else {
            // Generate subzone for Zone 2
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

            // Update zone with new subzone
            val updatedZone = zone2.copy(children = listOf(subzoneId))
            chunkRepo.save(updatedZone, zone2Id).getOrElse { return Result.failure(it) }

            subzoneId
        }

        val targetSubzone = chunkRepo.findById(targetSubzoneId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Subzone not found: $targetSubzoneId"))

        // Find a space in this subzone to place the exit (or create if doesn't exist)
        val targetSpaceId = if (targetSubzone.children.isNotEmpty()) {
            targetSubzone.children.first() // Use first space
        } else {
            // Generate space for subzone
            val (spaceProps, spaceId) = worldGenerator.generateSpace(targetSubzone, targetSubzoneId)
                .getOrElse { return Result.failure(it) }

            spaceRepo.save(spaceProps, spaceId).getOrElse { return Result.failure(it) }

            // Update subzone with new space
            val updatedSubzone = targetSubzone.copy(children = listOf(spaceId))
            chunkRepo.save(updatedSubzone, targetSubzoneId).getOrElse { return Result.failure(it) }

            spaceId
        }

        // Load the space
        val space = spaceRepo.findByChunkId(targetSpaceId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Space not found: $targetSpaceId"))

        // Create placeholder for Surface Wilderness region (lazy generation)
        val surfaceWildernessId = "world_region_surface_wilderness"

        // Create hidden exit with multiple discovery methods
        // Note: Current system requires ALL conditions, so we create perception check as primary
        // Future: Implement OR condition logic or multiple exits to same destination
        val perceptionExit = ExitData(
            targetId = surfaceWildernessId,
            direction = "cracked wall",
            description = "A faint crack in the eastern wall reveals starlight beyond. Fresh air drifts through.",
            conditions = listOf(
                Condition.SkillCheck("Perception", 40)
            ),
            isHidden = true,
            hiddenDifficulty = 40
        )

        // Alternative: Lockpicking hidden door
        val lockpickExit = ExitData(
            targetId = surfaceWildernessId,
            direction = "hidden door",
            description = "A cleverly concealed door with a complex lock mechanism. It seems to lead upward.",
            conditions = listOf(
                Condition.SkillCheck("Lockpicking", 30)
            ),
            isHidden = true,
            hiddenDifficulty = 35
        )

        // Alternative: Strength brute force
        val strengthExit = ExitData(
            targetId = surfaceWildernessId,
            direction = "weak wall",
            description = "This section of wall looks structurally weak. With enough force, it might break.",
            conditions = listOf(
                Condition.SkillCheck("Strength", 50)
            ),
            isHidden = true,
            hiddenDifficulty = 45
        )

        // Add all three exits to space (multiple paths to same destination)
        val updatedSpace = space
            .addExit(perceptionExit)
            .addExit(lockpickExit)
            .addExit(strengthExit)
            .copy(
                description = space.description + "\n\nSomething feels different about this place. " +
                              "Perhaps there are secrets hidden here for those with the skill to find them."
            )

        spaceRepo.save(updatedSpace, targetSpaceId).getOrElse { return Result.failure(it) }

        return Result.success(targetSpaceId)
    }

    /**
     * Generate Surface Wilderness region (lazy generation on first access)
     * Creates a new world region outside the dungeon
     *
     * @param seed World seed
     * @param globalLore World lore
     * @param worldId Parent world ID
     * @return Result with Surface Wilderness region ID
     */
    suspend fun generateSurfaceWilderness(
        seed: String,
        globalLore: String,
        worldId: String
    ): Result<String> {
        // Load world chunk
        val worldChunk = chunkRepo.findById(worldId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("World not found: $worldId"))

        // Generate Surface Wilderness REGION
        val regionContext = GenerationContext(
            seed = seed,
            globalLore = "$globalLore\n\nYou have escaped the dungeon depths and emerged into the Surface Wilderness.",
            parentChunk = worldChunk.copy(
                lore = "${worldChunk.lore}\n\nThe Surface Wilderness awaits those who escape the Abyss."
            ),
            parentChunkId = worldId,
            level = ChunkLevel.REGION,
            direction = "surface"
        )

        val (surfaceRegion, surfaceRegionId) = worldGenerator.generateChunk(regionContext)
            .getOrElse { return Result.failure(it) }

        // Set as open world region (lower difficulty, nature theme)
        val adjustedRegion = surfaceRegion.copy(
            difficultyLevel = 15, // Moderate difficulty
            biomeTheme = "wilderness",
            lore = """
                You emerge from the darkness into blinding daylight. A vast wilderness stretches before you -
                ancient forests, rolling hills, and distant mountains. The air is fresh and clean, a stark
                contrast to the stale dungeon depths. Freedom at last.

                This is the Surface Wilderness, a vast open world awaiting exploration.
            """.trimIndent()
        )

        chunkRepo.save(adjustedRegion, "world_region_surface_wilderness")
            .getOrElse { return Result.failure(it) }

        // Update world with new region
        val updatedWorld = worldChunk.copy(children = worldChunk.children + "world_region_surface_wilderness")
        chunkRepo.save(updatedWorld, worldId).getOrElse { return Result.failure(it) }

        return Result.success("world_region_surface_wilderness")
    }

    /**
     * Check if hidden exit conditions are met for any skill
     * Useful for UI hints
     *
     * @param perceptionLevel Player's Perception skill
     * @param lockpickingLevel Player's Lockpicking skill
     * @param strengthLevel Player's Strength stat
     * @return True if any condition is met
     */
    fun canDiscoverHiddenExit(
        perceptionLevel: Int,
        lockpickingLevel: Int,
        strengthLevel: Int
    ): Boolean {
        return perceptionLevel >= 40 ||
               lockpickingLevel >= 30 ||
               strengthLevel >= 50
    }

    /**
     * Get hint for hidden exit based on player's highest relevant skill
     *
     * @param perceptionLevel Player's Perception skill
     * @param lockpickingLevel Player's Lockpicking skill
     * @param strengthLevel Player's Strength stat
     * @return Hint string
     */
    fun getHiddenExitHint(
        perceptionLevel: Int,
        lockpickingLevel: Int,
        strengthLevel: Int
    ): String {
        return when {
            perceptionLevel >= 40 -> "Your keen perception reveals a faint crack in the wall..."
            lockpickingLevel >= 30 -> "You notice a hidden door mechanism that you could pick..."
            strengthLevel >= 50 -> "This weak wall could be broken through with enough force..."
            perceptionLevel >= 30 -> "You sense something unusual about this place, but can't quite make it out. (Perception 40 needed)"
            lockpickingLevel >= 20 -> "There might be a hidden mechanism here. (Lockpicking 30 needed)"
            strengthLevel >= 40 -> "The wall seems weak in places. (Strength 50 needed)"
            else -> "The walls appear solid. Perhaps higher skills would reveal secrets."
        }
    }
}
