package com.jcraw.mud.core

import kotlinx.serialization.Serializable

@Serializable
data class WorldState(
    val rooms: Map<RoomId, Room>,
    val player: PlayerState,
    val turnCount: Int = 0,
    val gameProperties: Map<String, String> = emptyMap(),
    val activeCombat: CombatState? = null
) {
    fun getCurrentRoom(): Room? = rooms[player.currentRoomId]

    fun getRoom(roomId: RoomId): Room? = rooms[roomId]

    fun updateRoom(room: Room): WorldState = copy(rooms = rooms + (room.id to room))

    fun updatePlayer(newPlayerState: PlayerState): WorldState = copy(player = newPlayerState)

    fun incrementTurn(): WorldState = copy(turnCount = turnCount + 1)

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

    fun getAvailableExits(): List<Direction> = getCurrentRoom()?.getAvailableDirections() ?: emptyList()

    fun isInCombat(): Boolean = activeCombat?.isActive() == true

    fun startCombat(npcId: String, npcHealth: Int): WorldState = copy(
        activeCombat = CombatState(
            combatantNpcId = npcId,
            playerHealth = player.health,
            npcHealth = npcHealth,
            isPlayerTurn = true,
            turnCount = 0
        )
    )

    fun updateCombat(newCombatState: CombatState?): WorldState = copy(activeCombat = newCombatState)

    fun endCombat(): WorldState = copy(activeCombat = null)
}