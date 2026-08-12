@file:Suppress("TooManyFunctions", "ReturnCount")

package com.jcraw.mud.client

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.world.WorldGenerator
import kotlinx.coroutines.runBlocking

/**
 * Space load/populate helpers for [EngineGameClient]. Pure extract — no behavior change.
 */
object ClientSpaceContent {

    fun loadSpace(game: EngineGameClient, spaceId: String): SpacePropertiesComponent? = runBlocking {
        game.spacePropertiesRepository.findByChunkId(spaceId)
    }.getOrNull()?.also { loaded ->
        game.worldState = game.worldState.updateSpace(spaceId, loaded)
    }

    fun loadEntity(game: EngineGameClient, entityId: String): Entity? = runBlocking {
        game.spaceEntityRepository.findById(entityId)
    }.getOrNull()

    fun currentSpace(game: EngineGameClient): SpacePropertiesComponent? =
        loadSpace(game, game.worldState.player.currentRoomId)

    fun ensureSpaceContent(game: EngineGameClient, spaceId: String) {
        val generator = game.worldGenerator ?: return
        val currentSpace = game.worldState.getSpace(spaceId) ?: return
        if (currentSpace.description.isNotEmpty() && !currentSpace.descriptionStale) return
        val node = ensureGraphNodeLoaded(game, spaceId) ?: return
        val chunk = loadChunk(game, node.chunkId) ?: return
        fillSpaceDescription(game, generator, FillArgs(currentSpace, node, chunk, spaceId))
    }

    private data class FillArgs(
        val currentSpace: SpacePropertiesComponent,
        val node: GraphNodeComponent,
        val chunk: WorldChunkComponent,
        val spaceId: String
    )

    private fun loadChunk(game: EngineGameClient, chunkId: String): WorldChunkComponent? =
        game.worldState.getChunk(chunkId) ?: runBlocking {
            game.worldChunkRepository.findById(chunkId).getOrNull()
        }?.also { loaded ->
            game.worldState = game.worldState.addChunk(chunkId, loaded)
        }

    private fun fillSpaceDescription(
        game: EngineGameClient,
        generator: WorldGenerator,
        args: FillArgs
    ) {
        val filledSpace = runBlocking {
            generator.fillSpaceContent(args.currentSpace, args.node, args.chunk)
        }.getOrElse {
            warn(game, "Failed to describe ${args.spaceId}: ${it.message}")
            return
        }
        val described = filledSpace.withDescription(filledSpace.description)
        game.worldState = game.worldState.updateSpace(args.spaceId, described)
        game.spacePropertiesRepository.save(filledSpace, args.spaceId).onFailure {
            warn(game, "Failed to persist description for ${args.spaceId}: ${it.message}")
        }
    }

    private fun warn(game: EngineGameClient, message: String) {
        game.emitEvent(GameEvent.System(message, GameEvent.MessageLevel.WARNING))
    }

    fun populateSpaceIfNeeded(game: EngineGameClient, spaceId: String) {
        val space = game.worldState.getSpace(spaceId) ?: return
        if (space.stateFlags["populated"] == true) return
        if (space.entities.isNotEmpty()) return
        if (space.isSafeZone) return
        val node = ensureGraphNodeLoaded(game, spaceId) ?: return
        val chunk = loadChunkForPopulation(game, node.chunkId) ?: return
        val populationResult = runBlocking {
            game.spacePopulationService.populateSpace(spaceId, space, chunk)
        }
        populationResult.onSuccess { (populatedSpace, spawnedEntities) ->
            applyPopulation(game, spaceId, populatedSpace, spawnedEntities)
        }.onFailure { error ->
            warn(game, "Population failed for $spaceId: ${error.message}")
        }
    }

    private fun loadChunkForPopulation(game: EngineGameClient, chunkId: String): WorldChunkComponent? {
        return game.worldState.getChunk(chunkId) ?: game.worldChunkRepository.findById(chunkId).getOrElse {
            warn(game, "Failed to load chunk $chunkId: ${it.message}")
            null
        }
    }

    private fun applyPopulation(
        game: EngineGameClient,
        spaceId: String,
        populatedSpace: SpacePropertiesComponent,
        spawnedEntities: List<Entity>
    ) {
        val flagged = populatedSpace.copy(stateFlags = populatedSpace.stateFlags + ("populated" to true))
        var world = game.worldState.updateSpace(spaceId, flagged)
        world = persistSpawnedEntities(game, world, spawnedEntities)
        game.spacePropertiesRepository.save(flagged, spaceId).onFailure {
            warn(game, "Failed to persist space $spaceId: ${it.message}")
        }
        game.worldState = world
    }

    private fun persistSpawnedEntities(
        game: EngineGameClient,
        world: WorldState,
        spawnedEntities: List<Entity>
    ): WorldState {
        var updated = world
        spawnedEntities.forEach { entity ->
            game.spaceEntityRepository.save(entity).onFailure {
                warn(game, "Failed to persist entity ${entity.id}: ${it.message}")
            }
            updated = updated.updateEntity(entity)
        }
        return updated
    }

    fun describeCurrentRoom(game: EngineGameClient) {
        val currentRoomId = game.worldState.player.currentRoomId
        ensureSpaceContent(game, currentRoomId)
        populateSpaceIfNeeded(game, currentRoomId)
        val space = game.worldState.getCurrentSpace()
        if (space == null) {
            game.emitEvent(GameEvent.System("Error: No current space", GameEvent.MessageLevel.ERROR))
        } else {
            ClientSpaceDescribe.describeWorldV2Space(game, space, currentRoomId)
        }
    }

    fun handlePlayerMovement(
        game: EngineGameClient,
        movementLabel: String,
        treasureExitMessage: String? = null
    ) {
        game.emitEvent(GameEvent.Narrative("You move $movementLabel."))
        treasureExitMessage?.let { game.emitEvent(GameEvent.Narrative(it)) }
        val currentSpaceId = game.worldState.player.currentRoomId
        ensureSpaceContent(game, currentSpaceId)
        game.worldState.getCurrentGraphNode()?.let {
            ClientFrontierExpansion.maybeExpandFrontier(game, it)
        }
        game.trackQuests(QuestAction.VisitedRoom(currentSpaceId))
        describeCurrentRoom(game)
    }

    fun ensureGraphNodeLoaded(game: EngineGameClient, spaceId: String): GraphNodeComponent? {
        game.worldState.getGraphNode(spaceId)?.let { return it }
        val node = runBlocking { game.graphNodeRepository.findById(spaceId).getOrNull() } ?: return null
        game.worldState = game.worldState.updateGraphNode(spaceId, node)
        return node
    }

    fun buildExitsWithNames(game: EngineGameClient, node: GraphNodeComponent): Map<Direction, String> =
        node.neighbors.mapNotNull { edge ->
            val direction = Direction.fromString(edge.direction) ?: return@mapNotNull null
            val targetName = game.worldState.getSpace(edge.targetId)?.name
                ?: loadSpace(game, edge.targetId)?.name
                ?: edge.targetId
            direction to targetName
        }.toMap()
}
