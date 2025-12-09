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

        val worldState = createWorld("room1", listOf(corpse))

        val manager = CorpseDecayManager()
        val result = manager.tickDecay(worldState)

        val updatedCorpse = result.worldState.getEntitiesInSpace("room1")
            .filterIsInstance<Entity.Corpse>()
            .firstOrNull()
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

        val worldState = createWorld("room1", listOf(corpse))

        val manager = CorpseDecayManager()
        val result = manager.tickDecay(worldState)

        val corpses = result.worldState.getEntitiesInSpace("room1")
            .filterIsInstance<Entity.Corpse>()
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

        val worldState = createWorld("room1", listOf(corpse))

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

        // Corpse and items should not be in room
        val corpses = result.worldState.getEntitiesInSpace("room1")
            .filterIsInstance<Entity.Corpse>()
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

        val worldState = createWorld("room1", listOf(corpse1, corpse2))

        val manager = CorpseDecayManager()
        val result = manager.tickDecay(worldState)

        val corpses = result.worldState.getEntitiesInSpace("room1")
            .filterIsInstance<Entity.Corpse>()
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

        val worldState = createMultiRoomWorld(
            "room1" to listOf(corpse1),
            "room2" to listOf(corpse2)
        )

        val manager = CorpseDecayManager()
        val result = manager.tickDecay(worldState)

        // Both corpses should be decayed
        assertEquals(2, result.decayedCorpses.size)

        // Both rooms should have no corpses
        assertTrue(result.worldState.getEntitiesInSpace("room1")
            .filterIsInstance<Entity.Corpse>().isEmpty())
        assertTrue(result.worldState.getEntitiesInSpace("room2")
            .filterIsInstance<Entity.Corpse>().isEmpty())
    }

    @Test
    fun `tickDecay handles rooms with no corpses`() {
        val npc = Entity.NPC(
            id = "npc1",
            name = "Guard",
            description = "A guard"
        )

        val worldState = createWorld("room1", listOf(npc))

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

        val manager = CorpseDecayManager()
        val worldState = createWorld("room1", listOf(corpse1, item, corpse2))
        val corpses = manager.getCorpsesInSpace("room1", worldState)

        assertEquals(2, corpses.size)
        assertTrue(corpses.any { it.id == "corpse1" })
        assertTrue(corpses.any { it.id == "corpse2" })
    }

    @Test
    fun `getCorpsesInRoom returns empty list when no corpses`() {
        val item = Entity.Item(id = "item1", name = "Sword", description = "A sword")

        val manager = CorpseDecayManager()
        val worldState = createWorld("room1", listOf(item))
        val corpses = manager.getCorpsesInSpace("room1", worldState)

        assertTrue(corpses.isEmpty())
    }

    @Test
    fun `getTotalCorpses returns correct count across all rooms`() {
        val corpse1 = Entity.Corpse(id = "corpse1", name = "Corpse 1", description = "First")
        val corpse2 = Entity.Corpse(id = "corpse2", name = "Corpse 2", description = "Second")
        val corpse3 = Entity.Corpse(id = "corpse3", name = "Corpse 3", description = "Third")

        val worldState = createMultiRoomWorld(
            "room1" to listOf(corpse1, corpse2),
            "room2" to listOf(corpse3),
            "room3" to emptyList()
        )

        val manager = CorpseDecayManager()
        val totalCorpses = manager.getTotalCorpses(worldState)

        assertEquals(3, totalCorpses)
    }

    @Test
    fun `getTotalCorpses returns 0 when no corpses exist`() {
        val worldState = createWorld("room1", emptyList())

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

/**
 * Helper function to create a simple world with one space containing entities
 */
private fun createWorld(spaceId: String, entities: List<Entity>): WorldState {
    val space = SpacePropertiesComponent(
        name = "Test Space",
        entities = entities.map { it.id }
    )

    val player = PlayerState(
        id = "player_test",
        name = "Tester",
        currentRoomId = spaceId
    )

    return WorldState(
        spaces = mapOf(spaceId to space),
        entities = entities.associateBy { it.id },
        players = mapOf(player.id to player)
    )
}

/**
 * Helper function to create a world with multiple spaces
 */
private fun createMultiRoomWorld(vararg roomsWithEntities: Pair<String, List<Entity>>): WorldState {
    val spaces = roomsWithEntities.associate { (roomId, entities) ->
        roomId to SpacePropertiesComponent(
            name = "Test Space $roomId",
            entities = entities.map { it.id }
        )
    }

    val allEntities = roomsWithEntities.flatMap { it.second }.associateBy { it.id }

    val primaryRoom = roomsWithEntities.first().first
    val player = PlayerState(
        id = "player_test",
        name = "Tester",
        currentRoomId = primaryRoom
    )

    return WorldState(
        spaces = spaces,
        entities = allEntities,
        players = mapOf(player.id to player)
    )
}
