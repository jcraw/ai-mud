package com.jcraw.mud.core

import kotlinx.serialization.Serializable

@Serializable
data class WorldState(
    val rooms: Map<RoomId, Room>,
    val players: Map<PlayerId, PlayerState>,
    val turnCount: Int = 0,
    val gameTime: Long = 0L,
    val gameProperties: Map<String, String> = emptyMap(),
    val availableQuests: List<Quest> = emptyList()
) {
    // Backward compatibility: get the "main" player (first player, if any)
    val player: PlayerState
        get() = players.values.firstOrNull() ?: throw IllegalStateException("No players in world")

    fun getCurrentRoom(): Room? = rooms[player.currentRoomId]

    fun getCurrentRoom(playerId: PlayerId): Room? {
        val playerState = players[playerId] ?: return null
        return rooms[playerState.currentRoomId]
    }

    fun getPlayer(playerId: PlayerId): PlayerState? = players[playerId]

    fun getRoom(roomId: RoomId): Room? = rooms[roomId]

    fun updateRoom(room: Room): WorldState = copy(rooms = rooms + (room.id to room))

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

    fun movePlayer(playerId: PlayerId, direction: Direction): WorldState? {
        val playerState = players[playerId] ?: return null
        val currentRoom = rooms[playerState.currentRoomId] ?: return null
        val targetRoomId = currentRoom.getExit(direction) ?: return null

        return if (rooms.containsKey(targetRoomId)) {
            updatePlayer(playerState.moveToRoom(targetRoomId))
        } else {
            null
        }
    }

    fun movePlayer(direction: Direction): WorldState? {
        val currentRoom = getCurrentRoom() ?: return null
        val targetRoomId = currentRoom.getExit(direction) ?: return null

        return if (rooms.containsKey(targetRoomId)) {
            updatePlayer(player.moveToRoom(targetRoomId))
        } else {
            null
        }
    }

    fun addEntityToRoom(roomId: RoomId, entity: Entity): WorldState? {
        val room = getRoom(roomId) ?: return null
        val updatedRoom = room.addEntity(entity)
        return updateRoom(updatedRoom)
    }

    fun removeEntityFromRoom(roomId: RoomId, entityId: String): WorldState? {
        val room = getRoom(roomId) ?: return null
        val updatedRoom = room.removeEntity(entityId)
        return updateRoom(updatedRoom)
    }

    fun replaceEntity(roomId: RoomId, entityId: String, newEntity: Entity): WorldState? {
        val room = getRoom(roomId) ?: return null
        val updatedRoom = room.removeEntity(entityId).addEntity(newEntity)
        return updateRoom(updatedRoom)
    }

    fun getAvailableExits(): List<Direction> = getCurrentRoom()?.getAvailableDirections() ?: emptyList()

    fun getAvailableExits(playerId: PlayerId): List<Direction> =
        getCurrentRoom(playerId)?.getAvailableDirections() ?: emptyList()

    fun getPlayersInRoom(roomId: RoomId): List<PlayerState> =
        players.values.filter { it.currentRoomId == roomId }

    fun addAvailableQuest(quest: Quest): WorldState =
        copy(availableQuests = availableQuests + quest)

    fun removeAvailableQuest(questId: QuestId): WorldState =
        copy(availableQuests = availableQuests.filter { it.id != questId })

    fun getAvailableQuest(questId: QuestId): Quest? =
        availableQuests.find { it.id == questId }
}