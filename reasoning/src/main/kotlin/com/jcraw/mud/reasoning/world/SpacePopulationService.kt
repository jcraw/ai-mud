package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldChunkComponent

/**
 * Shared wrapper around SpacePopulator so CLI + GUI populate spaces identically.
 */
class SpacePopulationService(
    private val spacePopulator: SpacePopulator,
    private val respawnChecker: RespawnChecker? = null
) {
    suspend fun populateSpace(
        spaceId: String,
        space: SpacePropertiesComponent,
        chunk: WorldChunkComponent,
        mobDensity: Double = chunk.mobDensity,
        spaceSize: Int = 10
    ): Result<Pair<SpacePropertiesComponent, List<Entity.NPC>>> {
        return if (respawnChecker != null) {
            spacePopulator.populateWithRespawn(
                space = space,
                spaceId = spaceId,
                theme = chunk.biomeTheme,
                difficulty = chunk.difficultyLevel,
                mobDensity = mobDensity,
                respawnChecker = respawnChecker,
                spaceSize = spaceSize
            )
        } else {
            Result.success(
                spacePopulator.populateWithEntities(
                    space = space,
                    theme = chunk.biomeTheme,
                    difficulty = chunk.difficultyLevel,
                    mobDensity = mobDensity,
                    spaceSize = spaceSize
                )
            )
        }
    }
}
