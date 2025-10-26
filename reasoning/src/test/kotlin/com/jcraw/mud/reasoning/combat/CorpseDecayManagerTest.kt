package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import kotlin.random.Random
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

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = emptyMap()
        )

        val manager = CorpseDecayManager()
        val result = manager.tickDecay(worldState)

        val updatedRoom = result.worldState.rooms["room1"]
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

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = emptyMap()
        )

        val manager = CorpseDecayManager()
        val result = manager.tickDecay(worldState)

        val updatedRoom = result.worldState.rooms["room1"]
        assertNotNull(updatedRoom)

        val corpses = updatedRoom.entities.filterIsInstance<Entity.Corpse>()
        assertTrue(corpses.isEmpty())
        assertEquals(1, result.decayedCorpses.size)
        assertEquals("corpse1", result.decayedCorpses[0].id)
    }

    @Test
    fun `tickDecay drops items from decayed corpse based on drop chance`() {
        val sword = Entity.Item(
            id = "sword1",
            name = "Sword",
            description = "A sword",
            itemType = ItemType.WEAPON
        )

        val potion = Entity.Item(
            id = "potion1",
            name = "Potion",
            description = "A potion",
            itemType = ItemType.CONSUMABLE
        )

        val corpse = Entity.Corpse(
            id = "corpse1",
            name = "Corpse",
            description = "A corpse",
            decayTimer = 1,
            contents = listOf(sword, potion)
        )

        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(corpse)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = emptyMap()
        )

        // Use deterministic random that always drops items (return 0.0 < 0.3)
        val deterministicRandom = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextFloat(): Float = 0.0f
        }

        val manager = CorpseDecayManager(itemDropChance = 0.3f, random = deterministicRandom)
        val result = manager.tickDecay(worldState)

        // Both items should be dropped (100% chance with our mock random)
        assertEquals(1, result.droppedItems.size)
        assertEquals(2, result.droppedItems["room1"]?.size ?: 0)

        val updatedRoom = result.worldState.rooms["room1"]
        assertNotNull(updatedRoom)

        // Items should be in room
        val droppedSword = updatedRoom.entities.filterIsInstance<Entity.Item>().find { it.id == "sword1" }
        val droppedPotion = updatedRoom.entities.filterIsInstance<Entity.Item>().find { it.id == "potion1" }
        assertNotNull(droppedSword)
        assertNotNull(droppedPotion)
    }

    @Test
    fun `tickDecay destroys items that don't drop`() {
        val sword = Entity.Item(
            id = "sword1",
            name = "Sword",
            description = "A sword"
        )

        val corpse = Entity.Corpse(
            id = "corpse1",
            name = "Corpse",
            description = "A corpse",
            decayTimer = 1,
            contents = listOf(sword)
        )

        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(corpse)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = emptyMap()
        )

        // Use deterministic random that never drops items (return 1.0 > 0.3)
        val neverDropRandom = object : Random() {
            override fun nextBits(bitCount: Int): Int = Int.MAX_VALUE
            override fun nextFloat(): Float = 1.0f
        }

        val manager = CorpseDecayManager(itemDropChance = 0.3f, random = neverDropRandom)
        val result = manager.tickDecay(worldState)

        // No items should be dropped
        assertTrue(result.droppedItems.isEmpty())

        val updatedRoom = result.worldState.rooms["room1"]
        assertNotNull(updatedRoom)

        // Items should not be in room (destroyed)
        val items = updatedRoom.entities.filterIsInstance<Entity.Item>()
        assertTrue(items.isEmpty())
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

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = emptyMap()
        )

        val manager = CorpseDecayManager()
        val result = manager.tickDecay(worldState)

        val updatedRoom = result.worldState.rooms["room1"]
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

        val worldState = WorldState(
            rooms = mapOf("room1" to room1, "room2" to room2),
            players = emptyMap()
        )

        val manager = CorpseDecayManager()
        val result = manager.tickDecay(worldState)

        // Both corpses should be decayed
        assertEquals(2, result.decayedCorpses.size)

        // Both rooms should have no corpses
        val updatedRoom1 = result.worldState.rooms["room1"]
        val updatedRoom2 = result.worldState.rooms["room2"]
        assertNotNull(updatedRoom1)
        assertNotNull(updatedRoom2)

        assertTrue(updatedRoom1.entities.filterIsInstance<Entity.Corpse>().isEmpty())
        assertTrue(updatedRoom2.entities.filterIsInstance<Entity.Corpse>().isEmpty())
    }

    @Test
    fun `tickDecay handles rooms with no corpses`() {
        val item = Entity.Item(
            id = "item1",
            name = "Torch",
            description = "A torch"
        )

        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(item)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = emptyMap()
        )

        val manager = CorpseDecayManager()
        val result = manager.tickDecay(worldState)

        // Should not modify room
        assertEquals(worldState, result.worldState)
        assertTrue(result.decayedCorpses.isEmpty())
        assertTrue(result.droppedItems.isEmpty())
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
        val corpses = manager.getCorpsesInRoom(room)

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
        val corpses = manager.getCorpsesInRoom(room)

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

        val worldState = WorldState(
            rooms = mapOf("room1" to room1, "room2" to room2, "room3" to room3),
            players = emptyMap()
        )

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

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = emptyMap()
        )

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
        val sword = Entity.Item(id = "sword1", name = "Sword", description = "A sword")
        val potion = Entity.Item(id = "potion1", name = "Potion", description = "A potion")

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
