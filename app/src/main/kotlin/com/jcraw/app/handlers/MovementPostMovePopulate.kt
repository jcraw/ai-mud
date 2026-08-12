@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList"
)

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.SpacePropertiesComponent
import kotlinx.coroutines.runBlocking

/**
 * Space population fragment for [MovementPostMoveHandlers].
 */
internal object MovementPostMovePopulate {

    fun populateSpaceIfNeeded(
        game: MudGame,
        spaceId: String,
        space: SpacePropertiesComponent,
        node: GraphNodeComponent
    ) {
        if (!needsPopulation(space)) return
        val chunk = game.worldState.getChunk(node.chunkId) ?: return
        val populationService = game.spacePopulationService
        runBlocking {
            val populationResult = populationService.populateSpace(
                spaceId = spaceId,
                space = space,
                chunk = chunk
            )
            populationResult.onSuccess { (populatedSpace, spawnedEntities) ->
                commitPopulation(game, spaceId, populatedSpace, spawnedEntities)
            }.onFailure { error ->
                println("(Population failed: ${error.message})")
            }
        }
    }

    private fun needsPopulation(space: SpacePropertiesComponent): Boolean {
        if (space.stateFlags["populated"] == true) return false
        if (space.entities.isNotEmpty()) return false
        if (space.isSafeZone) return false
        return true
    }

    private fun commitPopulation(
        game: MudGame,
        spaceId: String,
        populatedSpace: SpacePropertiesComponent,
        spawnedEntities: List<com.jcraw.mud.core.Entity>
    ) {
        val flaggedSpace = populatedSpace.copy(
            stateFlags = populatedSpace.stateFlags + ("populated" to true)
        )
        var updatedWorld = game.worldState.updateSpace(spaceId, flaggedSpace)
        spawnedEntities.forEach { entity ->
            updatedWorld = updatedWorld.updateEntity(entity)
        }
        game.spacePropertiesRepository.save(flaggedSpace, spaceId)
            .onFailure { println("Warning: Failed to persist space $spaceId: ${it.message}") }
        game.worldState = updatedWorld
    }
}
