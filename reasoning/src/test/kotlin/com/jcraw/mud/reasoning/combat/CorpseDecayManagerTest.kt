package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/**
 * Tests for CorpseDecayManager - corpse decay and item drop mechanics
 */
class CorpseDecayManagerTest {

    @Test
    fun `tickDecay decrements corpse timer`() {
        val corpse = Entity.Corpse(
            id = "corpse1",
            name = "Corpse",
            description = "A corpse",
            decayTimer = 10
        )

        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(corpse)
        )

        val worldState = createWorld(room)

        val manager = CorpseDecayManager()
        val result = manager.tickDecay(worldState)

        val updatedRoom = result.worldState.getRoomViews()["room1"]
        assertNotNull(updatedRoom)

        val updatedCorpse = updatedRoom.entities.filterIsInstance<Entity.Corpse>().firstOrNull()
        assertNotNull(updatedCorpse)
        assertEquals(9, updatedCorpse.decayTimer)
        assertTrue(result.decayedCorpses.isEmpty())
    }

    @Test
    fun `tickDecay removes corpse when timer reaches 0`() {
        val corpse = Entity.Corpse(
            id = "corpse1",
            name = "Corpse",
            description = "A corpse",
            decayTimer = 1,
            contents = emptyList()
        )

        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(corpse)
        )

        val worldState = createWorld(room)

        val manager = CorpseDecayManager()
        val result = manager.tickDecay(worldState)

        val updatedRoom = result.worldState.getRoomViews()["room1"]
        assertNotNull(updatedRoom)

        val corpses = updatedRoom.entities.filterIsInstance<Entity.Corpse>()
        assertTrue(corpses.isEmpty())
        assertEquals(1, result.decayedCorpses.size)
        assertEquals("corpse1", result.decayedCorpses[0].id)
    }

    @Test
    fun `tickDecay destroys items from decayed corpse`() {
        val sword = ItemInstance(
            id = "sword1",
            templateId = "iron_sword",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val potion = ItemInstance(
            id = "potion1",
            templateId = "health_potion",
            quality = 3,
            charges = 1,
            quantity = 1
        )

        val corpse = Entity.Corpse(
            id = "corpse1",
            name = "Corpse",
            description = "A corpse",
            decayTimer = 1,
            contents = listOf(sword, potion),
            goldAmount = 50
        )

        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(corpse)
        )

        val worldState = createWorld(room)

        val manager = CorpseDecayManager()
        val result = manager.tickDecay(worldState)

        // Corpse should be removed
        assertEquals(1, result.decayedCorpses.size)

        // Items should be destroyed (tracked in result by room)
        assertEquals(1, result.destroyedItems.size)
        val destroyedItemsInRoom = result.destroyedItems["room1"]
        assertNotNull(destroyedItemsInRoom)
        assertEquals(2, destroyedItemsInRoom.size)
        assertTrue(destroyedItemsInRoom.any { it.id == "sword1" })
        assertTrue(destroyedItemsInRoom.any { it.id == "potion1" })

        // Gold should be tracked by room
        assertEquals(50, result.destroyedGold["room1"])

        val updatedRoom = result.worldState.getRoomViews()["room1"]
        assertNotNull(updatedRoom)

        // Corpse and items should not be in room
        val corpses = updatedRoom.entities.filterIsInstance<Entity.Corpse>()
        assertTrue(corpses.isEmpty())
    }


    @Test
    fun `tickDecay processes multiple corpses in same room`() {
        val corpse1 = Entity.Corpse(
            id = "corpse1",
            name = "Corpse 1",
            description = "First corpse",
            decayTimer = 5
        )

        val corpse2 = Entity.Corpse(
            id = "corpse2",
            name = "Corpse 2",
            description = "Second corpse",
            decayTimer = 1
        )

        val room = Room(
            id = "room1",
            name = "Battlefield",
            traits = listOf("bloody"),
            entities = listOf(corpse1, corpse2)
        )

        val worldState = createWorld(room)

        val manager = CorpseDecayManager()
        val result = manager.tickDecay(worldState)

        val updatedRoom = result.worldState.getRoomViews()["room1"]
        assertNotNull(updatedRoom)

        val corpses = updatedRoom.entities.filterIsInstance<Entity.Corpse>()
        assertEquals(1, corpses.size) // Only corpse1 should remain
        assertEquals("corpse1", corpses[0].id)
        assertEquals(4, corpses[0].decayTimer) // Timer should be decremented

        assertEquals(1, result.decayedCorpses.size)
        assertEquals("corpse2", result.decayedCorpses[0].id)
    }

    @Test
    fun `tickDecay processes corpses in multiple rooms`() {
        val corpse1 = Entity.Corpse(
            id = "corpse1",
            name = "Corpse 1",
            description = "First corpse",
            decayTimer = 1
        )

        val corpse2 = Entity.Corpse(
            id = "corpse2",
            name = "Corpse 2",
            description = "Second corpse",
            decayTimer = 1
        )

        val room1 = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(corpse1)
        )

        val room2 = Room(
            id = "room2",
            name = "Dungeon",
            traits = listOf("damp"),
            entities = listOf(corpse2)
        )

        val worldState = createWorld(room1, room2)

        val manager = CorpseDecayManager()
        val result = manager.tickDecay(worldState)

        // Both corpses should be decayed
        assertEquals(2, result.decayedCorpses.size)

        // Both rooms should have no corpses
        val updatedRoom1 = result.worldState.getRoomViews()["room1"]
        val updatedRoom2 = result.worldState.getRoomViews()["room2"]
        assertNotNull(updatedRoom1)
        assertNotNull(updatedRoom2)

        assertTrue(updatedRoom1.entities.filterIsInstance<Entity.Corpse>().isEmpty())
        assertTrue(updatedRoom2.entities.filterIsInstance<Entity.Corpse>().isEmpty())
    }

    @Test
    fun `tickDecay handles rooms with no corpses`() {
        val npc = Entity.NPC(
            id = "npc1",
            name = "Guard",
            description = "A guard"
        )

        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(npc)
        )

        val worldState = createWorld(room)

        val manager = CorpseDecayManager()
        val result = manager.tickDecay(worldState)

        // Should not modify room
        assertEquals(worldState, result.worldState)
        assertTrue(result.decayedCorpses.isEmpty())
        assertTrue(result.destroyedItems.isEmpty())
        assertTrue(result.destroyedGold.isEmpty())
    }

    @Test
    fun `getCorpsesInRoom returns all corpses in room`() {
        val corpse1 = Entity.Corpse(id = "corpse1", name = "Corpse 1", description = "First")
        val corpse2 = Entity.Corpse(id = "corpse2", name = "Corpse 2", description = "Second")
        val item = Entity.Item(id = "item1", name = "Sword", description = "A sword")

        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(corpse1, item, corpse2)
        )

        val manager = CorpseDecayManager()
        val worldState = createWorld(room)
        val corpses = manager.getCorpsesInSpace(room.id, worldState)

        assertEquals(2, corpses.size)
        assertTrue(corpses.any { it.id == "corpse1" })
        assertTrue(corpses.any { it.id == "corpse2" })
    }

    @Test
    fun `getCorpsesInRoom returns empty list when no corpses`() {
        val item = Entity.Item(id = "item1", name = "Sword", description = "A sword")

        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(item)
        )

        val manager = CorpseDecayManager()
        val worldState = createWorld(room)
        val corpses = manager.getCorpsesInSpace(room.id, worldState)

        assertTrue(corpses.isEmpty())
    }

    @Test
    fun `getTotalCorpses returns correct count across all rooms`() {
        val corpse1 = Entity.Corpse(id = "corpse1", name = "Corpse 1", description = "First")
        val corpse2 = Entity.Corpse(id = "corpse2", name = "Corpse 2", description = "Second")
        val corpse3 = Entity.Corpse(id = "corpse3", name = "Corpse 3", description = "Third")

        val room1 = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(corpse1, corpse2)
        )

        val room2 = Room(
            id = "room2",
            name = "Dungeon",
            traits = listOf("damp"),
            entities = listOf(corpse3)
        )

        val room3 = Room(
            id = "room3",
            name = "Hall",
            traits = listOf("empty"),
            entities = emptyList()
        )

        val worldState = createWorld(room1, room2, room3)

        val manager = CorpseDecayManager()
        val totalCorpses = manager.getTotalCorpses(worldState)

        assertEquals(3, totalCorpses)
    }

    @Test
    fun `getTotalCorpses returns 0 when no corpses exist`() {
        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = emptyList()
        )

        val worldState = createWorld(room)

        val manager = CorpseDecayManager()
        val totalCorpses = manager.getTotalCorpses(worldState)

        assertEquals(0, totalCorpses)
    }

    @Test
    fun `corpse tick method decrements timer and returns null when expired`() {
        val corpse = Entity.Corpse(
            id = "corpse1",
            name = "Corpse",
            description = "A corpse",
            decayTimer = 2
        )

        // First tick
        val ticked1 = corpse.tick()
        assertNotNull(ticked1)
        assertEquals(1, ticked1.decayTimer)

        // Second tick (should expire)
        val ticked2 = ticked1.tick()
        assertNull(ticked2)
    }

    @Test
    fun `corpse removeItem removes item and returns updated corpse`() {
        val sword = ItemInstance(
            id = "sword1",
            templateId = "iron_sword",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val potion = ItemInstance(
            id = "potion1",
            templateId = "health_potion",
            quality = 3,
            charges = 1,
            quantity = 1
        )

        val corpse = Entity.Corpse(
            id = "corpse1",
            name = "Corpse",
            description = "A corpse",
            contents = listOf(sword, potion)
        )

        val updated = corpse.removeItem("sword1")

        assertEquals(1, updated.contents.size)
        assertEquals("potion1", updated.contents[0].id)
    }
}

private fun createWorld(vararg rooms: Room): WorldState {
    require(rooms.isNotEmpty())
    val roomMap = rooms.associateBy { it.id }
    val primaryRoom = rooms.first().id
    val player = PlayerState(
        id = "player_$primaryRoom",
        name = "Tester",
        currentRoomId = primaryRoom
    )
    return WorldState(
        rooms = roomMap,
        players = mapOf(player.id to player)
    )
}
