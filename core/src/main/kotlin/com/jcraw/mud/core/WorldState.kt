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
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter",
    "UseCheckOrError"
)

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
    val treasureRooms: Map<SpaceId, TreasureRoomComponent> = emptyMap(), // V3: Treasure room components

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
     * Get treasure room by space ID (V3)
     */
    fun getTreasureRoom(spaceId: SpaceId): TreasureRoomComponent? = treasureRooms[spaceId]

    /**
     * Update treasure room component (V3)
     */
    fun updateTreasureRoom(spaceId: SpaceId, treasureRoom: TreasureRoomComponent): WorldState =
        copy(treasureRooms = treasureRooms + (spaceId to treasureRoom))

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
    fun movePlayerV3(playerId: PlayerId, direction: Direction, playerSkills: SkillComponent): WorldState? =
        WorldStateNav.move(this, playerId, direction, playerSkills)

    /**
     * Move main player using graph-based navigation (V3)
     */
    fun movePlayerV3(direction: Direction, playerSkills: SkillComponent): WorldState? =
        movePlayerV3(player.id, direction, playerSkills)

    /**
     * Move player using an arbitrary exit label (supports natural language directions).
     * Attempts to resolve an edge whose direction matches the given label.
     */
    fun movePlayerByExit(playerId: PlayerId, exitLabel: String, playerSkills: SkillComponent): WorldState? =
        WorldStateNav.moveByExit(this, playerId, exitLabel, playerSkills)

    fun movePlayerByExit(exitLabel: String, playerSkills: SkillComponent): WorldState? =
        movePlayerByExit(player.id, exitLabel, playerSkills)

    /**
     * Get available exits from current location (V3)
     * Only returns visible edges (filters hidden exits player can't see)
     */
    fun getAvailableExitsV3(playerId: PlayerId, playerSkills: SkillComponent): List<Direction> =
        WorldStateNav.availableExits(this, playerId, playerSkills)

    /**
     * Get available exits for main player (V3)
     */
    fun getAvailableExitsV3(playerSkills: SkillComponent): List<Direction> =
        getAvailableExitsV3(player.id, playerSkills)

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
    fun getEntity(entityId: String): Entity? = WorldStateEntities.get(this, entityId)

    /**
     * Add or update entity (V3)
     */
    fun updateEntity(entity: Entity): WorldState = WorldStateEntities.update(this, entity)

    /**
     * Remove entity (V3)
     */
    fun removeEntity(entityId: String): WorldState = WorldStateEntities.remove(this, entityId)

    /**
     * Get all entities in a space (V3)
     * Returns entities whose IDs are in the space's entity list
     */
    fun getEntitiesInSpace(spaceId: SpaceId): List<Entity> =
        WorldStateEntities.inSpace(this, spaceId)

    /**
     * Find the space ID that currently contains the given entity, if any.
     */
    fun findSpaceContainingEntity(entityId: String): SpaceId? =
        WorldStateEntities.findSpaceContaining(this, entityId)

    /**
     * Add entity to space (V3)
     * Adds entity to global storage and links it to the space
     */
    fun addEntityToSpace(spaceId: SpaceId, entity: Entity): WorldState =
        WorldStateEntities.addToSpace(this, spaceId, entity)

    /**
     * Remove entity from space (V3)
     * Removes entity from space's list AND global storage
     */
    fun removeEntityFromSpace(spaceId: SpaceId, entityId: String): WorldState =
        WorldStateEntities.removeFromSpace(this, spaceId, entityId)

    /**
     * Replace entity in space (V3)
     * Useful for entity transformations (e.g., NPC → Corpse)
     */
    fun replaceEntityInSpace(spaceId: SpaceId, oldEntityId: String, newEntity: Entity): WorldState =
        WorldStateEntities.replaceInSpace(this, spaceId, oldEntityId, newEntity)

    /**
     * Remove a dropped item instance from a space, optionally removing any linked entities.
     */
    fun removeDroppedItem(
        spaceId: SpaceId,
        instanceId: String,
        removeEntity: Boolean = false,
        updateCorpses: Boolean = true
    ): WorldState = WorldStateItems.removeDroppedItem(
        this,
        spaceId,
        instanceId,
        removeEntity,
        updateCorpses
    )
}
