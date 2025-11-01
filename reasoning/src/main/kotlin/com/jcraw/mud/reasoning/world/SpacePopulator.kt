package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SpacePropertiesComponent

/**
 * Orchestrates all content placement for spaces.
 * Coordinates trap generation, resource placement, and mob spawning.
 * Provides both initial population and respawn (mob-only) functionality.
 */
class SpacePopulator(
    private val trapGenerator: TrapGenerator,
    private val resourceGenerator: ResourceGenerator,
    private val mobSpawner: MobSpawner
) {
    /**
     * Populate a space with all content types.
     * Generates traps (10-20%), resources (5%), and mobs based on density.
     * Returns updated SpacePropertiesComponent with generated content.
     *
     * Safe zones skip mob spawning and trap generation.
     */
    suspend fun populate(
        space: SpacePropertiesComponent,
        theme: String,
        difficulty: Int,
        mobDensity: Double,
        spaceSize: Int = 10
    ): SpacePropertiesComponent {
        // Skip traps and mobs in safe zones
        if (space.isSafeZone) {
            // Only generate resources in safe zones
            val resources = resourceGenerator.generateResourcesForSpace(
                theme = theme,
                difficulty = difficulty,
                resourceProbability = 0.05
            )

            return space.copy(
                resources = space.resources + resources
            )
        }

        // Generate traps (10-20% base probability)
        val traps = trapGenerator.generateTrapsForSpace(
            theme = theme,
            difficulty = difficulty,
            trapProbability = 0.15
        )

        // Generate resources (5% base probability)
        val resources = resourceGenerator.generateResourcesForSpace(
            theme = theme,
            difficulty = difficulty,
            resourceProbability = 0.05
        )

        // Spawn mobs
        val mobs = mobSpawner.spawnEntities(
            theme = theme,
            mobDensity = mobDensity,
            difficulty = difficulty,
            spaceSize = spaceSize
        )

        // Extract entity IDs (space stores only IDs, full entities managed separately)
        val entityIds = mobs.map { it.id }

        // Return updated space with all generated content
        return space.copy(
            traps = space.traps + traps,
            resources = space.resources + resources,
            entities = space.entities + entityIds
        )
    }

    /**
     * Repopulate space with mobs only.
     * Preserves existing traps, resources, flags, and items.
     * Used on game restart for murder-hobo viable gameplay.
     *
     * NOTE: This method only updates the entity ID list in SpacePropertiesComponent.
     * The actual Entity.NPC objects must be managed separately in the world state.
     */
    suspend fun repopulate(
        space: SpacePropertiesComponent,
        theme: String,
        difficulty: Int,
        mobDensity: Double,
        spaceSize: Int = 10
    ): Pair<SpacePropertiesComponent, List<Entity.NPC>> {
        // Clear existing entities (mobs respawn)
        val clearedSpace = space.copy(entities = emptyList())

        // Spawn fresh mobs
        val newMobs = mobSpawner.respawn(
            theme = theme,
            mobDensity = mobDensity,
            difficulty = difficulty,
            spaceSize = spaceSize
        )

        // Extract entity IDs
        val entityIds = newMobs.map { it.id }

        // Return updated space with new mobs
        val updatedSpace = clearedSpace.copy(entities = entityIds)

        return updatedSpace to newMobs
    }

    /**
     * Get full mob list for a populated space.
     * Helper method that spawns mobs and returns both component and entities.
     * Useful for initial world generation.
     */
    suspend fun populateWithEntities(
        space: SpacePropertiesComponent,
        theme: String,
        difficulty: Int,
        mobDensity: Double,
        spaceSize: Int = 10
    ): Pair<SpacePropertiesComponent, List<Entity.NPC>> {
        // Generate traps
        val traps = trapGenerator.generateTrapsForSpace(
            theme = theme,
            difficulty = difficulty,
            trapProbability = 0.15
        )

        // Generate resources
        val resources = resourceGenerator.generateResourcesForSpace(
            theme = theme,
            difficulty = difficulty,
            resourceProbability = 0.05
        )

        // Spawn mobs
        val mobs = mobSpawner.spawnEntities(
            theme = theme,
            mobDensity = mobDensity,
            difficulty = difficulty,
            spaceSize = spaceSize
        )

        // Extract entity IDs
        val entityIds = mobs.map { it.id }

        // Return updated space and mob list
        val updatedSpace = space.copy(
            traps = space.traps + traps,
            resources = space.resources + resources,
            entities = space.entities + entityIds
        )

        return updatedSpace to mobs
    }

    /**
     * Calculate expected mob count for a space.
     * Helper for testing and validation.
     */
    fun calculateMobCount(mobDensity: Double, spaceSize: Int = 10): Int {
        return (spaceSize * mobDensity).toInt().coerceAtLeast(0)
    }

    /**
     * Clear all dynamic content from a space.
     * Removes traps, resources, and entities.
     * Preserves state flags and dropped items (player modifications).
     */
    fun clearDynamicContent(space: SpacePropertiesComponent): SpacePropertiesComponent {
        return space.copy(
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList()
        )
    }

    /**
     * Populate a space with respawn tracking enabled.
     * Generates traps, resources, and mobs with respawn registration.
     * Safe zones skip mob spawning and trap generation.
     *
     * @param space Space to populate
     * @param spaceId Space ID for respawn tracking
     * @param theme Biome theme
     * @param difficulty Difficulty level
     * @param mobDensity Mob density (0.0-1.0)
     * @param respawnChecker RespawnChecker for registration
     * @param spaceSize Space size for calculations
     * @return Pair of (updated space, spawned entities)
     */
    suspend fun populateWithRespawn(
        space: SpacePropertiesComponent,
        spaceId: String,
        theme: String,
        difficulty: Int,
        mobDensity: Double,
        respawnChecker: RespawnChecker,
        spaceSize: Int = 10
    ): Result<Pair<SpacePropertiesComponent, List<Entity.NPC>>> {
        // Skip mobs and traps in safe zones
        if (space.isSafeZone) {
            val resources = resourceGenerator.generateResourcesForSpace(
                theme = theme,
                difficulty = difficulty,
                resourceProbability = 0.05
            )

            val updatedSpace = space.copy(
                resources = space.resources + resources
            )

            return Result.success(updatedSpace to emptyList())
        }

        // Generate traps
        val traps = trapGenerator.generateTrapsForSpace(
            theme = theme,
            difficulty = difficulty,
            trapProbability = 0.15
        )

        // Generate resources
        val resources = resourceGenerator.generateResourcesForSpace(
            theme = theme,
            difficulty = difficulty,
            resourceProbability = 0.05
        )

        // Spawn mobs with respawn tracking
        val mobsResult = mobSpawner.spawnWithRespawn(
            theme = theme,
            mobDensity = mobDensity,
            difficulty = difficulty,
            spaceId = spaceId,
            respawnChecker = respawnChecker,
            spaceSize = spaceSize
        )

        return mobsResult.map { mobs ->
            // Extract entity IDs
            val entityIds = mobs.map { it.id }

            // Return updated space and mob list
            val updatedSpace = space.copy(
                traps = space.traps + traps,
                resources = space.resources + resources,
                entities = space.entities + entityIds
            )

            updatedSpace to mobs
        }
    }
}
