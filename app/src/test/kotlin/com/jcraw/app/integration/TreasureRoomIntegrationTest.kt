package com.jcraw.app.integration

import com.jcraw.mud.core.*
import com.jcraw.mud.testbot.InMemoryGameEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Integration tests for treasure room system
 *
 * Tests the full treasure room workflow including:
 * - Taking items from pedestals
 * - Returning items to pedestals
 * - Examining pedestals
 * - Room finalization on exit
 * - Barrier mechanics
 */
class TreasureRoomIntegrationTest {

    @Test
    fun `leaving treasure room without taking item keeps room unlocked`() = runBlocking {
        val world = createWorldWithTreasureRoom()
        val engine = InMemoryGameEngine(world)

        // Player starts in treasure room
        assertEquals("treasure_room", engine.getWorldState().player.currentRoomId)

        // Verify treasure room is not looted
        val treasureRoom = engine.getWorldState().getTreasureRoom("treasure_room")
        assertNotNull(treasureRoom)
        assertFalse(treasureRoom.hasBeenLooted)
        assertNull(treasureRoom.currentlyTakenItem)

        // Leave room without taking anything
        engine.processInput("north")
        assertEquals("corridor", engine.getWorldState().player.currentRoomId)

        // Come back - room should still be unlocked
        engine.processInput("south")
        assertEquals("treasure_room", engine.getWorldState().player.currentRoomId)

        val updatedRoom = engine.getWorldState().getTreasureRoom("treasure_room")
        assertNotNull(updatedRoom)
        assertFalse(updatedRoom.hasBeenLooted)
        assertNull(updatedRoom.currentlyTakenItem)
    }

    @Test
    fun `leaving treasure room with item finalizes room`() = runBlocking {
        val world = createWorldWithTreasureRoom()
        val engine = InMemoryGameEngine(world)

        // Take an item from the treasure room
        engine.processInput("examine pedestals")
        engine.processInput("take treasure flamebrand")

        // Verify item was taken
        val treasureRoomBefore = engine.getWorldState().getTreasureRoom("treasure_room")
        assertNotNull(treasureRoomBefore)
        assertEquals("flamebrand_longsword", treasureRoomBefore.currentlyTakenItem)
        assertFalse(treasureRoomBefore.hasBeenLooted)

        // Leave the room
        engine.processInput("north")
        assertEquals("corridor", engine.getWorldState().player.currentRoomId)

        // Room should now be finalized
        val treasureRoomAfter = engine.getWorldState().getTreasureRoom("treasure_room")
        assertNotNull(treasureRoomAfter)
        assertTrue(treasureRoomAfter.hasBeenLooted)
        assertNull(treasureRoomAfter.currentlyTakenItem)

        // All pedestals should be EMPTY
        treasureRoomAfter.pedestals.forEach { pedestal ->
            assertEquals(PedestalState.EMPTY, pedestal.state)
        }
    }

    @Test
    fun `taking item locks other pedestals`() = runBlocking {
        val world = createWorldWithTreasureRoom()
        val engine = InMemoryGameEngine(world)

        // Take an item
        engine.processInput("take treasure flamebrand")

        val treasureRoom = engine.getWorldState().getTreasureRoom("treasure_room")
        assertNotNull(treasureRoom)

        // Currently taken item should be set
        assertEquals("flamebrand_longsword", treasureRoom.currentlyTakenItem)

        // Other pedestals should be locked
        val lockedCount = treasureRoom.pedestals.count { it.state == PedestalState.LOCKED }
        val emptyCount = treasureRoom.pedestals.count { it.state == PedestalState.EMPTY }

        assertEquals(4, lockedCount)
        assertEquals(1, emptyCount) // The taken item's pedestal
    }

    @Test
    fun `returning item unlocks all pedestals`() = runBlocking {
        val world = createWorldWithTreasureRoom()
        val engine = InMemoryGameEngine(world)

        // Take an item
        engine.processInput("take treasure flamebrand")

        // Verify locked state
        val treasureRoomLocked = engine.getWorldState().getTreasureRoom("treasure_room")
        assertNotNull(treasureRoomLocked)
        assertTrue(treasureRoomLocked.pedestals.any { it.state == PedestalState.LOCKED })

        // Return the item
        engine.processInput("return treasure flamebrand")

        // All pedestals should be available again
        val treasureRoomUnlocked = engine.getWorldState().getTreasureRoom("treasure_room")
        assertNotNull(treasureRoomUnlocked)
        assertNull(treasureRoomUnlocked.currentlyTakenItem)

        treasureRoomUnlocked.pedestals.forEach { pedestal ->
            assertEquals(PedestalState.AVAILABLE, pedestal.state)
        }
    }

    @Test
    fun `can swap item choice before leaving room`() = runBlocking {
        val world = createWorldWithTreasureRoom()
        val engine = InMemoryGameEngine(world)

        // Take first item
        engine.processInput("take treasure flamebrand")
        val room1 = engine.getWorldState().getTreasureRoom("treasure_room")
        assertEquals("flamebrand_longsword", room1?.currentlyTakenItem)

        // Return it
        engine.processInput("return treasure flamebrand")

        // Take second item
        engine.processInput("take treasure shadowweave")
        val room2 = engine.getWorldState().getTreasureRoom("treasure_room")
        assertEquals("shadowweave_cloak", room2?.currentlyTakenItem)

        // Player inventory should have the second item
        val inventory = engine.getWorldState().player.inventoryComponent
        assertNotNull(inventory)
        assertTrue(inventory.items.any { it.templateId == "shadowweave_cloak" })
        assertFalse(inventory.items.any { it.templateId == "flamebrand_longsword" })
    }

    @Test
    fun `examine pedestals shows correct state`() = runBlocking {
        val world = createWorldWithTreasureRoom()
        val engine = InMemoryGameEngine(world)

        // Get initial output
        engine.processInput("examine pedestals")
        val output1 = engine.getLastOutput()

        // Should show all items as available
        assertTrue(output1.contains("Available"))
        assertFalse(output1.contains("Locked"))

        // Take an item
        engine.processInput("take treasure flamebrand")

        // Examine again
        engine.processInput("examine pedestals")
        val output2 = engine.getLastOutput()

        // Should show current choice and locked items
        assertTrue(output2.contains("Current choice"))
        assertTrue(output2.contains("Locked"))
    }

    @Test
    fun `finalized room shows empty state on examine`() = runBlocking {
        val world = createWorldWithTreasureRoom()
        val engine = InMemoryGameEngine(world)

        // Take item and leave
        engine.processInput("take treasure flamebrand")
        engine.processInput("north")
        engine.processInput("south")

        // Examine pedestals
        engine.processInput("examine pedestals")
        val output = engine.getLastOutput()

        // Should show empty/looted state
        assertTrue(output.contains("empty") || output.contains("spent") || output.contains("looted"))
    }

    @Test
    fun `cannot take from finalized treasure room`() = runBlocking {
        val world = createWorldWithTreasureRoom()
        val engine = InMemoryGameEngine(world)

        // Take item and leave to finalize
        engine.processInput("take treasure flamebrand")
        engine.processInput("north")
        engine.processInput("south")

        // Try to take another item
        engine.processInput("take treasure shadowweave")
        val output = engine.getLastOutput()

        // Should show error message
        assertTrue(output.contains("looted") || output.contains("empty") || output.contains("spent"))
    }

    // Helper to create a world with a treasure room
    private fun createWorldWithTreasureRoom(): WorldState {
        val player = PlayerState(
            id = "player1",
            name = "TestPlayer",
            currentRoomId = "treasure_room",
            health = 100,
            maxHealth = 100,
            stats = Stats(),
            inventoryComponent = InventoryComponent(
                items = emptyList(),
                maxWeight = 100.0,
                currentWeight = 0.0
            )
        )

        val treasureRoom = TreasureRoomComponent(
            roomType = TreasureRoomType.STARTER,
            pedestals = listOf(
                Pedestal(
                    pedestalIndex = 0,
                    itemTemplateId = "flamebrand_longsword",
                    state = PedestalState.AVAILABLE,
                    themeDescription = "stone altar bearing a warrior's blade"
                ),
                Pedestal(
                    pedestalIndex = 1,
                    itemTemplateId = "shadowweave_cloak",
                    state = PedestalState.AVAILABLE,
                    themeDescription = "shadowed stone pedestal wreathed in darkness"
                ),
                Pedestal(
                    pedestalIndex = 2,
                    itemTemplateId = "stormcaller_staff",
                    state = PedestalState.AVAILABLE,
                    themeDescription = "glowing stone shrine pulsing with arcane energy"
                ),
                Pedestal(
                    pedestalIndex = 3,
                    itemTemplateId = "titans_band",
                    state = PedestalState.AVAILABLE,
                    themeDescription = "sturdy stone stand adorned with fortitude runes"
                ),
                Pedestal(
                    pedestalIndex = 4,
                    itemTemplateId = "arcane_blade",
                    state = PedestalState.AVAILABLE,
                    themeDescription = "ornate stone dais marked with dual symbols"
                )
            ),
            currentlyTakenItem = null,
            hasBeenLooted = false,
            biomeTheme = "ancient_abyss"
        )

        val corridorRoom = Room(
            id = "corridor",
            name = "Corridor",
            description = "A simple corridor",
            exits = mapOf(Direction.SOUTH to "treasure_room")
        )

        val treasureRoomRoom = Room(
            id = "treasure_room",
            name = "Treasure Room",
            description = "A room with five glowing pedestals",
            exits = mapOf(Direction.NORTH to "corridor")
        )

        return WorldState(
            players = mapOf(player.id to player),
            rooms = mapOf(
                "treasure_room" to treasureRoomRoom,
                "corridor" to corridorRoom
            ),
            treasureRooms = mapOf("treasure_room" to treasureRoom)
        )
    }
}
