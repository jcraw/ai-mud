package com.jcraw.mud.core

import kotlinx.serialization.Serializable

typealias SpaceId = String

/**
 * WorldState V3 - Component-based world model
 *
 * V3 uses ECS components (GraphNodeComponent + SpacePropertiesComponent) instead of Room.
 * Graph-based navigation with lazy-filled content.
 */
@Serializable
data class WorldState(
    // V3: Component storage (graph nodes define topology, spaces define content)
    val graphNodes: Map<SpaceId, GraphNodeComponent> = emptyMap(),
    val spaces: Map<SpaceId, SpacePropertiesComponent> = emptyMap(),
    val chunks: Map<String, WorldChunkComponent> = emptyMap(), // V3: Chunk hierarchy storage
    val entities: Map<String, Entity> = emptyMap(), // V3: Entity storage (SpacePropertiesComponent refs by ID)

    val players: Map<PlayerId, PlayerState>,
    val turnCount: Int = 0,
    val gameTime: Long = 0L,
    val gameProperties: Map<String, String> = emptyMap(),
    val availableQuests: List<Quest> = emptyList()
) {
    // Backward compatibility: get the "main" player (first player, if any)
    val player: PlayerState
        get() = players.values.firstOrNull() ?: throw IllegalStateException("No players in world")

    fun getPlayer(playerId: PlayerId): PlayerState? = players[playerId]

    fun updatePlayer(newPlayerState: PlayerState): WorldState =
        copy(players = players + (newPlayerState.id to newPlayerState))

    fun addPlayer(playerState: PlayerState): WorldState =
        copy(players = players + (playerState.id to playerState))

    fun removePlayer(playerId: PlayerId): WorldState =
        copy(players = players - playerId)

    fun incrementTurn(): WorldState = copy(turnCount = turnCount + 1)

    /**
     * Advances the game clock by the specified number of ticks.
     * Used for asynchronous turn-based combat timing.
     */
    fun advanceTime(ticks: Long): WorldState = copy(gameTime = gameTime + ticks)

    fun addAvailableQuest(quest: Quest): WorldState =
        copy(availableQuests = availableQuests + quest)

    fun removeAvailableQuest(questId: QuestId): WorldState =
        copy(availableQuests = availableQuests.filter { it.id != questId })

    fun getAvailableQuest(questId: QuestId): Quest? =
        availableQuests.find { it.id == questId }

    // ========================================
    // V3: Component-based methods
    // ========================================

    /**
     * Get current space for player (V3)
     */
    fun getCurrentSpace(playerId: PlayerId): SpacePropertiesComponent? {
        val playerState = players[playerId] ?: return null
        return spaces[playerState.currentRoomId]
    }

    /**
     * Get current space for main player (V3)
     */
    fun getCurrentSpace(): SpacePropertiesComponent? = getCurrentSpace(player.id)

    /**
     * Get graph node for player's current location (V3)
     */
    fun getCurrentGraphNode(playerId: PlayerId): GraphNodeComponent? {
        val playerState = players[playerId] ?: return null
        return graphNodes[playerState.currentRoomId]
    }

    /**
     * Get graph node for main player (V3)
     */
    fun getCurrentGraphNode(): GraphNodeComponent? = getCurrentGraphNode(player.id)

    /**
     * Get space by ID (V3)
     */
    fun getSpace(spaceId: SpaceId): SpacePropertiesComponent? = spaces[spaceId]

    /**
     * Get graph node by ID (V3)
     */
    fun getGraphNode(spaceId: SpaceId): GraphNodeComponent? = graphNodes[spaceId]

    /**
     * Update space properties (V3)
     */
    fun updateSpace(spaceId: SpaceId, space: SpacePropertiesComponent): WorldState =
        copy(spaces = spaces + (spaceId to space))

    /**
     * Update graph node (V3)
     */
    fun updateGraphNode(spaceId: SpaceId, node: GraphNodeComponent): WorldState =
        copy(graphNodes = graphNodes + (spaceId to node))

    /**
     * Add new space with graph node (V3)
     */
    fun addSpace(spaceId: SpaceId, node: GraphNodeComponent, space: SpacePropertiesComponent): WorldState =
        copy(
            graphNodes = graphNodes + (spaceId to node),
            spaces = spaces + (spaceId to space)
        )

    /**
     * Move player using graph-based navigation (V3)
     * Returns null if movement fails (no exit, space not generated, etc.)
     */
    fun movePlayerV3(playerId: PlayerId, direction: Direction): WorldState? {
        val playerState = players[playerId] ?: return null
        val currentNode = graphNodes[playerState.currentRoomId] ?: return null

        // Find edge matching direction
        val edge = currentNode.getEdge(direction.displayName) ?: return null

        // Check if target space exists
        if (!spaces.containsKey(edge.targetId)) {
            return null // Space not yet generated
        }

        // Move player to target
        return updatePlayer(playerState.moveToRoom(edge.targetId))
    }

    /**
     * Move main player using graph-based navigation (V3)
     */
    fun movePlayerV3(direction: Direction): WorldState? = movePlayerV3(player.id, direction)

    /**
     * Get available exits from current location (V3)
     * Only returns visible edges (filters hidden exits player can't see)
     */
    fun getAvailableExitsV3(playerId: PlayerId): List<Direction> {
        val playerState = players[playerId] ?: return emptyList()
        val node = graphNodes[playerState.currentRoomId] ?: return emptyList()
        val space = spaces[playerState.currentRoomId] ?: return emptyList()

        // Get visible exits from space (handles Perception checks for hidden exits)
        val visibleExits = space.getVisibleExits(playerState)

        // Convert exit directions to Direction enum (filter out non-cardinal)
        return visibleExits.mapNotNull { exit ->
            Direction.fromString(exit.direction)
        }
    }

    /**
     * Get available exits for main player (V3)
     */
    fun getAvailableExitsV3(): List<Direction> = getAvailableExitsV3(player.id)

    // ========================================
    // V3: Chunk management methods
    // ========================================

    /**
     * Get chunk by ID (V3)
     */
    fun getChunk(chunkId: String): WorldChunkComponent? = chunks[chunkId]

    /**
     * Update chunk (V3)
     */
    fun updateChunk(chunkId: String, chunk: WorldChunkComponent): WorldState =
        copy(chunks = chunks + (chunkId to chunk))

    /**
     * Add chunk (V3)
     */
    fun addChunk(chunkId: String, chunk: WorldChunkComponent): WorldState =
        copy(chunks = chunks + (chunkId to chunk))

    // ========================================
    // V3: Entity management methods
    // ========================================

    /**
     * Get entity by ID (V3)
     */
    fun getEntity(entityId: String): Entity? = entities[entityId]

    /**
     * Add or update entity (V3)
     */
    fun updateEntity(entity: Entity): WorldState =
        copy(entities = entities + (entity.id to entity))

    /**
     * Remove entity (V3)
     */
    fun removeEntity(entityId: String): WorldState =
        copy(entities = entities - entityId)

    /**
     * Get all entities in a space (V3)
     * Returns entities whose IDs are in the space's entity list
     */
    fun getEntitiesInSpace(spaceId: SpaceId): List<Entity> {
        val space = getSpace(spaceId) ?: return emptyList()
        return space.entities.mapNotNull { entityId -> entities[entityId] }
    }

    /**
     * Add entity to space (V3)
     * Adds entity to global storage and links it to the space
     */
    fun addEntityToSpace(spaceId: SpaceId, entity: Entity): WorldState? {
        val space = getSpace(spaceId) ?: return null
        val updatedSpace = space.addEntity(entity.id)
        return updateEntity(entity).updateSpace(spaceId, updatedSpace)
    }

    /**
     * Remove entity from space (V3)
     * Removes entity from space's list AND global storage
     */
    fun removeEntityFromSpace(spaceId: SpaceId, entityId: String): WorldState? {
        val space = getSpace(spaceId) ?: return null
        val updatedSpace = space.removeEntity(entityId)
        return updateSpace(spaceId, updatedSpace).removeEntity(entityId)
    }

    /**
     * Replace entity in space (V3)
     * Useful for entity transformations (e.g., NPC → Corpse)
     */
    fun replaceEntityInSpace(spaceId: SpaceId, oldEntityId: String, newEntity: Entity): WorldState? {
        val space = getSpace(spaceId) ?: return null
        val updatedSpace = space.removeEntity(oldEntityId).addEntity(newEntity.id)
        return removeEntity(oldEntityId)
            .updateEntity(newEntity)
            .updateSpace(spaceId, updatedSpace)
    }
}