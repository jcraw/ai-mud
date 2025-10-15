package com.jcraw.mud.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Tests for WorldState and Room navigation/entity lookup
 *
 * Focus: Behavioral tests for room navigation, entity management, and player lookups
 */
class WorldStateTest {

    // Test fixtures
    private val entranceRoom = Room(
        id = "entrance",
        name = "Entrance Hall",
        traits = listOf("stone", "large"),
        exits = mapOf(
            Direction.NORTH to "hallway",
            Direction.EAST to "armory"
        )
    )

    private val hallwayRoom = Room(
        id = "hallway",
        name = "Dark Hallway",
        traits = listOf("dark", "narrow"),
        exits = mapOf(Direction.SOUTH to "entrance")
    )

    private val armoryRoom = Room(
        id = "armory",
        name = "Armory",
        traits = listOf("weapons", "dusty"),
        exits = mapOf(Direction.WEST to "entrance")
    )

    private val player1 = PlayerState(
        id = "player1",
        name = "Hero",
        currentRoomId = "entrance"
    )

    private val player2 = PlayerState(
        id = "player2",
        name = "Companion",
        currentRoomId = "entrance"
    )

    private val baseWorldState = WorldState(
        rooms = mapOf(
            "entrance" to entranceRoom,
            "hallway" to hallwayRoom,
            "armory" to armoryRoom
        ),
        players = mapOf("player1" to player1)
    )

    private val sword = Entity.Item(
        id = "sword",
        name = "Iron Sword",
        description = "A sturdy sword",
        itemType = ItemType.WEAPON,
        damageBonus = 5
    )

    private val guard = Entity.NPC(
        id = "guard",
        name = "Guard",
        description = "A vigilant guard",
        isHostile = false
    )

    // ========== Room Navigation Tests ==========

    @Test
    fun `moving player to valid exit updates player location`() {
        val world = baseWorldState
        assertEquals("entrance", world.player.currentRoomId)

        val updated = world.movePlayer(Direction.NORTH)

        assertNotNull(updated)
        assertEquals("hallway", updated?.player?.currentRoomId)
    }

    @Test
    fun `moving player to invalid direction returns null`() {
        val world = baseWorldState
        // Entrance has no WEST exit
        val updated = world.movePlayer(Direction.WEST)

        assertNull(updated, "Should return null for invalid direction")
    }

    @Test
    fun `moving player to exit with non-existent room returns null`() {
        val brokenRoom = entranceRoom.copy(
            exits = mapOf(Direction.SOUTH to "nonexistent_room")
        )
        val world = baseWorldState.updateRoom(brokenRoom)

        val updated = world.movePlayer(Direction.SOUTH)

        assertNull(updated, "Should return null when target room doesn't exist")
    }

    @Test
    fun `moving specific player by ID works correctly`() {
        val world = baseWorldState.addPlayer(player2)
        assertEquals("entrance", world.getPlayer("player2")?.currentRoomId)

        val updated = world.movePlayer("player2", Direction.NORTH)

        assertNotNull(updated)
        assertEquals("hallway", updated?.getPlayer("player2")?.currentRoomId)
        // Player1 should not move
        assertEquals("entrance", updated?.getPlayer("player1")?.currentRoomId)
    }

    @Test
    fun `moving non-existent player returns null`() {
        val world = baseWorldState
        val updated = world.movePlayer("nonexistent_player", Direction.NORTH)

        assertNull(updated)
    }

    @Test
    fun `navigation maintains room connectivity`() {
        var world = baseWorldState

        // Move north to hallway
        world = world.movePlayer(Direction.NORTH)!!
        assertEquals("hallway", world.player.currentRoomId)

        // Move south back to entrance
        world = world.movePlayer(Direction.SOUTH)!!
        assertEquals("entrance", world.player.currentRoomId)

        // Move east to armory
        world = world.movePlayer(Direction.EAST)!!
        assertEquals("armory", world.player.currentRoomId)

        // Move west back to entrance
        world = world.movePlayer(Direction.WEST)!!
        assertEquals("entrance", world.player.currentRoomId)
    }

    // ========== Room Lookup Tests ==========

    @Test
    fun `getting room by ID returns correct room`() {
        val world = baseWorldState

        val entrance = world.getRoom("entrance")
        val hallway = world.getRoom("hallway")

        assertEquals("Entrance Hall", entrance?.name)
        assertEquals("Dark Hallway", hallway?.name)
    }

    @Test
    fun `getting non-existent room returns null`() {
        val world = baseWorldState

        val room = world.getRoom("nonexistent")

        assertNull(room)
    }

    @Test
    fun `getting current room returns player's room`() {
        val world = baseWorldState
        assertEquals("entrance", world.player.currentRoomId)

        val currentRoom = world.getCurrentRoom()

        assertNotNull(currentRoom)
        assertEquals("entrance", currentRoom?.id)
        assertEquals("Entrance Hall", currentRoom?.name)
    }

    @Test
    fun `getting current room for specific player works`() {
        val world = baseWorldState
            .addPlayer(player2.copy(currentRoomId = "hallway"))

        val room1 = world.getCurrentRoom("player1")
        val room2 = world.getCurrentRoom("player2")

        assertEquals("entrance", room1?.id)
        assertEquals("hallway", room2?.id)
    }

    @Test
    fun `getting current room for non-existent player returns null`() {
        val world = baseWorldState

        val room = world.getCurrentRoom("nonexistent")

        assertNull(room)
    }

    // ========== Player Management Tests ==========

    @Test
    fun `adding player works correctly`() {
        val world = baseWorldState
        assertEquals(1, world.players.size)

        val updated = world.addPlayer(player2)

        assertEquals(2, updated.players.size)
        assertNotNull(updated.getPlayer("player2"))
    }

    @Test
    fun `removing player works correctly`() {
        val world = baseWorldState.addPlayer(player2)
        assertEquals(2, world.players.size)

        val updated = world.removePlayer("player2")

        assertEquals(1, updated.players.size)
        assertNull(updated.getPlayer("player2"))
    }

    @Test
    fun `updating player state replaces existing player`() {
        val world = baseWorldState
        val updatedPlayer = player1.copy(health = 50)

        val updated = world.updatePlayer(updatedPlayer)

        assertEquals(50, updated.getPlayer("player1")?.health)
        assertEquals(1, updated.players.size)
    }

    @Test
    fun `getting players in room returns all players in that room`() {
        val world = baseWorldState
            .addPlayer(player2)
            .addPlayer(player1.copy(id = "player3", currentRoomId = "hallway"))

        val entrancePlayers = world.getPlayersInRoom("entrance")
        val hallwayPlayers = world.getPlayersInRoom("hallway")

        assertEquals(2, entrancePlayers.size, "Two players in entrance")
        assertEquals(1, hallwayPlayers.size, "One player in hallway")
        assertTrue(entrancePlayers.any { it.id == "player1" })
        assertTrue(entrancePlayers.any { it.id == "player2" })
        assertTrue(hallwayPlayers.any { it.id == "player3" })
    }

    @Test
    fun `getting players in empty room returns empty list`() {
        val world = baseWorldState

        val players = world.getPlayersInRoom("hallway")

        assertTrue(players.isEmpty())
    }

    // ========== Entity Management Tests ==========

    @Test
    fun `adding entity to room works`() {
        val world = baseWorldState
        assertEquals(0, world.getRoom("entrance")?.entities?.size)

        val updated = world.addEntityToRoom("entrance", sword)

        assertNotNull(updated)
        assertEquals(1, updated?.getRoom("entrance")?.entities?.size)
        assertEquals(sword, updated?.getRoom("entrance")?.getEntity("sword"))
    }

    @Test
    fun `adding entity to non-existent room returns null`() {
        val world = baseWorldState

        val updated = world.addEntityToRoom("nonexistent", sword)

        assertNull(updated)
    }

    @Test
    fun `removing entity from room works`() {
        val roomWithSword = entranceRoom.addEntity(sword)
        val world = baseWorldState.updateRoom(roomWithSword)
        assertEquals(1, world.getRoom("entrance")?.entities?.size)

        val updated = world.removeEntityFromRoom("entrance", "sword")

        assertNotNull(updated)
        assertEquals(0, updated?.getRoom("entrance")?.entities?.size)
    }

    @Test
    fun `removing entity from non-existent room returns null`() {
        val world = baseWorldState

        val updated = world.removeEntityFromRoom("nonexistent", "sword")

        assertNull(updated)
    }

    @Test
    fun `replacing entity in room works`() {
        val roomWithGuard = entranceRoom.addEntity(guard)
        val world = baseWorldState.updateRoom(roomWithGuard)

        val damagedGuard = guard.copy(health = 50)
        val updated = world.replaceEntity("entrance", "guard", damagedGuard)

        assertNotNull(updated)
        val updatedGuard = updated?.getRoom("entrance")?.getEntity("guard") as? Entity.NPC
        assertEquals(50, updatedGuard?.health)
    }

    @Test
    fun `replacing entity in non-existent room returns null`() {
        val world = baseWorldState

        val updated = world.replaceEntity("nonexistent", "guard", guard)

        assertNull(updated)
    }

    // ========== Exit Management Tests ==========

    @Test
    fun `getting available exits returns correct directions`() {
        val world = baseWorldState

        val exits = world.getAvailableExits()

        assertEquals(2, exits.size)
        assertTrue(exits.contains(Direction.NORTH))
        assertTrue(exits.contains(Direction.EAST))
    }

    @Test
    fun `getting available exits for specific player works`() {
        val world = baseWorldState
            .addPlayer(player2.copy(currentRoomId = "hallway"))

        val exits1 = world.getAvailableExits("player1")
        val exits2 = world.getAvailableExits("player2")

        assertEquals(2, exits1.size) // Entrance has 2 exits
        assertEquals(1, exits2.size) // Hallway has 1 exit
        assertTrue(exits2.contains(Direction.SOUTH))
    }

    @Test
    fun `room hasExit checks work correctly`() {
        assertTrue(entranceRoom.hasExit(Direction.NORTH))
        assertTrue(entranceRoom.hasExit(Direction.EAST))
        assertFalse(entranceRoom.hasExit(Direction.SOUTH))
        assertFalse(entranceRoom.hasExit(Direction.WEST))
    }

    @Test
    fun `room getExit returns correct room ID`() {
        assertEquals("hallway", entranceRoom.getExit(Direction.NORTH))
        assertEquals("armory", entranceRoom.getExit(Direction.EAST))
        assertNull(entranceRoom.getExit(Direction.WEST))
    }

    @Test
    fun `room getAvailableDirections returns all exits`() {
        val directions = entranceRoom.getAvailableDirections()

        assertEquals(2, directions.size)
        assertTrue(directions.contains(Direction.NORTH))
        assertTrue(directions.contains(Direction.EAST))
    }

    // ========== Room Entity Tests ==========

    @Test
    fun `room can contain multiple entities`() {
        val room = entranceRoom
            .addEntity(sword)
            .addEntity(guard)

        assertEquals(2, room.entities.size)
        assertNotNull(room.getEntity("sword"))
        assertNotNull(room.getEntity("guard"))
    }

    @Test
    fun `room getEntity returns correct entity`() {
        val room = entranceRoom.addEntity(sword)

        val entity = room.getEntity("sword")

        assertEquals(sword, entity)
    }

    @Test
    fun `room getEntity returns null for non-existent entity`() {
        val room = entranceRoom

        val entity = room.getEntity("nonexistent")

        assertNull(entity)
    }

    @Test
    fun `room getEntitiesByType filters correctly`() {
        val room = entranceRoom
            .addEntity(sword)
            .addEntity(guard)

        val items = room.getEntitiesByType(Entity.Item::class.java)
        val npcs = room.getEntitiesByType(Entity.NPC::class.java)

        assertEquals(1, items.size)
        assertEquals(1, npcs.size)
        assertTrue(items[0] is Entity.Item)
        assertTrue(npcs[0] is Entity.NPC)
    }

    // ========== Immutability Tests ==========

    @Test
    fun `world state operations maintain immutability`() {
        val original = baseWorldState
        val originalPlayerLocation = original.player.currentRoomId

        // Various operations
        val moved = original.movePlayer(Direction.NORTH)
        val withEntity = original.addEntityToRoom("entrance", sword)
        val withPlayer = original.addPlayer(player2)

        // Original unchanged
        assertEquals(originalPlayerLocation, original.player.currentRoomId)
        assertEquals(0, original.getRoom("entrance")?.entities?.size)
        assertEquals(1, original.players.size)

        // New states are different
        assertEquals("hallway", moved?.player?.currentRoomId)
        assertEquals(1, withEntity?.getRoom("entrance")?.entities?.size)
        assertEquals(2, withPlayer.players.size)
    }

    @Test
    fun `room operations maintain immutability`() {
        val original = entranceRoom
        val originalEntityCount = original.entities.size

        // Various operations
        val withSword = original.addEntity(sword)
        val withGuard = original.addEntity(guard)

        // Original unchanged
        assertEquals(originalEntityCount, original.entities.size)

        // New states are different
        assertEquals(1, withSword.entities.size)
        assertEquals(1, withGuard.entities.size)
    }
}
