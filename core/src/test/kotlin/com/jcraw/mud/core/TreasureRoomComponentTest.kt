package com.jcraw.mud.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith

/**
 * Unit tests for TreasureRoomComponent
 *
 * Focus: Behavioral tests for take/return/swap mechanics, state transitions,
 * pedestal locking/unlocking, and one-time loot mechanics
 */
class TreasureRoomComponentTest {

    // Test fixtures - 5 pedestals for starter room
    private fun createStarterTreasureRoom(): TreasureRoomComponent {
        return TreasureRoomComponent(
            roomType = TreasureRoomType.STARTER,
            pedestals = listOf(
                Pedestal("veterans_longsword", PedestalState.AVAILABLE, "ancient stone altar", 0),
                Pedestal("shadowcloak", PedestalState.AVAILABLE, "shadowed stone pedestal", 1),
                Pedestal("apprentice_staff", PedestalState.AVAILABLE, "glowing stone shrine", 2),
                Pedestal("enchanted_satchel", PedestalState.AVAILABLE, "sturdy stone stand", 3),
                Pedestal("spellblade", PedestalState.AVAILABLE, "ornate stone dais", 4)
            ),
            currentlyTakenItem = null,
            hasBeenLooted = false,
            biomeTheme = "ancient_abyss"
        )
    }

    // ========== Initialization Tests ==========

    @Test
    fun `fresh treasure room has all pedestals available`() {
        val room = createStarterTreasureRoom()

        assertEquals(5, room.pedestals.size)
        assertTrue(room.pedestals.all { it.state == PedestalState.AVAILABLE })
        assertNull(room.currentlyTakenItem)
        assertFalse(room.hasBeenLooted)
    }

    @Test
    fun `treasure room has correct component type`() {
        val room = createStarterTreasureRoom()
        assertEquals(ComponentType.TREASURE_ROOM, room.componentType)
    }

    // ========== Take Item Tests ==========

    @Test
    fun `take item locks other pedestals`() {
        val room = createStarterTreasureRoom()
        val updated = room.takeItem("veterans_longsword")

        assertEquals("veterans_longsword", updated.currentlyTakenItem)

        // Taken pedestal stays AVAILABLE (for return)
        val takenPedestal = updated.getPedestal("veterans_longsword")
        assertEquals(PedestalState.AVAILABLE, takenPedestal?.state)

        // Other pedestals are LOCKED
        val otherPedestals = updated.pedestals.filter { it.itemTemplateId != "veterans_longsword" }
        assertTrue(otherPedestals.all { it.state == PedestalState.LOCKED })
        assertEquals(4, otherPedestals.size)
    }

    @Test
    fun `take item updates currently taken item`() {
        val room = createStarterTreasureRoom()
        val updated = room.takeItem("shadowcloak")

        assertEquals("shadowcloak", updated.currentlyTakenItem)
    }

    @Test
    fun `cannot take item when already holding one`() {
        val room = createStarterTreasureRoom()
        val withItem = room.takeItem("veterans_longsword")

        assertFailsWith<IllegalArgumentException> {
            withItem.takeItem("shadowcloak")
        }
    }

    @Test
    fun `cannot take item from looted room`() {
        val room = createStarterTreasureRoom()
        val looted = room.markAsLooted()

        assertFailsWith<IllegalArgumentException> {
            looted.takeItem("veterans_longsword")
        }
    }

    @Test
    fun `cannot take item that doesn't exist`() {
        val room = createStarterTreasureRoom()

        assertFailsWith<IllegalArgumentException> {
            room.takeItem("nonexistent_item")
        }
    }

    @Test
    fun `cannot take item from locked pedestal`() {
        val room = createStarterTreasureRoom()
        val withItem = room.takeItem("veterans_longsword")

        // shadowcloak pedestal should be locked now
        assertFailsWith<IllegalArgumentException> {
            withItem.takeItem("shadowcloak")
        }
    }

    // ========== Return Item Tests ==========

    @Test
    fun `return item unlocks all pedestals`() {
        val room = createStarterTreasureRoom()
        val withItem = room.takeItem("veterans_longsword")
        val returned = withItem.returnItem("veterans_longsword")

        assertNull(returned.currentlyTakenItem)
        assertTrue(returned.pedestals.all { it.state == PedestalState.AVAILABLE })
    }

    @Test
    fun `return item clears currently taken item`() {
        val room = createStarterTreasureRoom()
        val withItem = room.takeItem("shadowcloak")
        val returned = withItem.returnItem("shadowcloak")

        assertNull(returned.currentlyTakenItem)
    }

    @Test
    fun `cannot return wrong item`() {
        val room = createStarterTreasureRoom()
        val withItem = room.takeItem("veterans_longsword")

        assertFailsWith<IllegalArgumentException> {
            withItem.returnItem("shadowcloak")
        }
    }

    @Test
    fun `cannot return when nothing taken`() {
        val room = createStarterTreasureRoom()

        assertFailsWith<IllegalArgumentException> {
            room.returnItem("veterans_longsword")
        }
    }

    // ========== Swap Sequence Tests ==========

    @Test
    fun `swap sequence - take, return, take different item`() {
        val room = createStarterTreasureRoom()

        // Take sword
        val withSword = room.takeItem("veterans_longsword")
        assertEquals("veterans_longsword", withSword.currentlyTakenItem)

        // Return sword
        val returned = withSword.returnItem("veterans_longsword")
        assertNull(returned.currentlyTakenItem)
        assertTrue(returned.pedestals.all { it.state == PedestalState.AVAILABLE })

        // Take staff
        val withStaff = returned.takeItem("apprentice_staff")
        assertEquals("apprentice_staff", withStaff.currentlyTakenItem)

        // Only staff pedestal is AVAILABLE, others are LOCKED
        val staffPedestal = withStaff.getPedestal("apprentice_staff")
        assertEquals(PedestalState.AVAILABLE, staffPedestal?.state)
        assertEquals(4, withStaff.pedestals.count { it.state == PedestalState.LOCKED })
    }

    @Test
    fun `multiple swaps allowed before leaving`() {
        var room = createStarterTreasureRoom()

        // Swap 5 times
        val items = listOf("veterans_longsword", "shadowcloak", "apprentice_staff", "enchanted_satchel", "spellblade")
        for (item in items) {
            room = room.takeItem(item)
            assertEquals(item, room.currentlyTakenItem)
            room = room.returnItem(item)
            assertNull(room.currentlyTakenItem)
        }

        // Room still not looted
        assertFalse(room.hasBeenLooted)
        assertTrue(room.pedestals.all { it.state == PedestalState.AVAILABLE })
    }

    // ========== Mark As Looted Tests ==========

    @Test
    fun `mark as looted sets flag and empties pedestals`() {
        val room = createStarterTreasureRoom()
        val looted = room.markAsLooted()

        assertTrue(looted.hasBeenLooted)
        assertTrue(looted.pedestals.all { it.state == PedestalState.EMPTY })
    }

    @Test
    fun `mark as looted works with item taken`() {
        val room = createStarterTreasureRoom()
        val withItem = room.takeItem("veterans_longsword")
        val looted = withItem.markAsLooted()

        assertTrue(looted.hasBeenLooted)
        assertTrue(looted.pedestals.all { it.state == PedestalState.EMPTY })
        assertEquals("veterans_longsword", looted.currentlyTakenItem) // Item reference preserved
    }

    // ========== Helper Methods Tests ==========

    @Test
    fun `getPedestal returns correct pedestal`() {
        val room = createStarterTreasureRoom()
        val pedestal = room.getPedestal("veterans_longsword")

        assertNotNull(pedestal)
        assertEquals("veterans_longsword", pedestal.itemTemplateId)
        assertEquals(PedestalState.AVAILABLE, pedestal.state)
    }

    @Test
    fun `getPedestal returns null for nonexistent item`() {
        val room = createStarterTreasureRoom()
        val pedestal = room.getPedestal("nonexistent_item")

        assertNull(pedestal)
    }

    @Test
    fun `canTakeItem returns true for available pedestal`() {
        val room = createStarterTreasureRoom()
        assertTrue(room.canTakeItem("veterans_longsword"))
    }

    @Test
    fun `canTakeItem returns false for locked pedestals`() {
        val room = createStarterTreasureRoom()
        val withItem = room.takeItem("veterans_longsword")

        // Locked pedestals return false
        assertFalse(withItem.canTakeItem("shadowcloak"))
        assertFalse(withItem.canTakeItem("apprentice_staff"))

        // Taken item's pedestal is still AVAILABLE (for returning), so canTakeItem returns true
        // This allows UI to highlight it as interactable (to return)
        assertTrue(withItem.canTakeItem("veterans_longsword"))
    }

    @Test
    fun `canTakeItem returns false when room looted`() {
        val room = createStarterTreasureRoom()
        val looted = room.markAsLooted()

        assertFalse(looted.canTakeItem("veterans_longsword"))
    }

    @Test
    fun `canTakeItem returns false for nonexistent item`() {
        val room = createStarterTreasureRoom()
        assertFalse(room.canTakeItem("nonexistent_item"))
    }

    @Test
    fun `getAvailablePedestals returns all pedestals initially`() {
        val room = createStarterTreasureRoom()
        val available = room.getAvailablePedestals()

        assertEquals(5, available.size)
        assertTrue(available.all { it.state == PedestalState.AVAILABLE })
    }

    @Test
    fun `getAvailablePedestals returns only taken pedestal when item taken`() {
        val room = createStarterTreasureRoom()
        val withItem = room.takeItem("veterans_longsword")
        val available = withItem.getAvailablePedestals()

        assertEquals(1, available.size)
        assertEquals("veterans_longsword", available.first().itemTemplateId)
    }

    @Test
    fun `getAvailablePedestals returns empty list when looted`() {
        val room = createStarterTreasureRoom()
        val looted = room.markAsLooted()
        val available = looted.getAvailablePedestals()

        assertEquals(0, available.size)
    }

    // ========== State Invariant Tests ==========

    @Test
    fun `after takeItem exactly one pedestal is AVAILABLE`() {
        val room = createStarterTreasureRoom()
        val withItem = room.takeItem("veterans_longsword")

        val availableCount = withItem.pedestals.count { it.state == PedestalState.AVAILABLE }
        val lockedCount = withItem.pedestals.count { it.state == PedestalState.LOCKED }

        assertEquals(1, availableCount)
        assertEquals(4, lockedCount)
    }

    @Test
    fun `after returnItem all pedestals are AVAILABLE`() {
        val room = createStarterTreasureRoom()
        val withItem = room.takeItem("veterans_longsword")
        val returned = withItem.returnItem("veterans_longsword")

        val availableCount = returned.pedestals.count { it.state == PedestalState.AVAILABLE }
        assertEquals(5, availableCount)
    }

    @Test
    fun `after markAsLooted all pedestals are EMPTY`() {
        val room = createStarterTreasureRoom()
        val looted = room.markAsLooted()

        val emptyCount = looted.pedestals.count { it.state == PedestalState.EMPTY }
        assertEquals(5, emptyCount)
    }

    // ========== Pedestal Data Class Tests ==========

    @Test
    fun `pedestal has correct fields`() {
        val pedestal = Pedestal(
            itemTemplateId = "test_item",
            state = PedestalState.AVAILABLE,
            themeDescription = "ancient altar",
            pedestalIndex = 0
        )

        assertEquals("test_item", pedestal.itemTemplateId)
        assertEquals(PedestalState.AVAILABLE, pedestal.state)
        assertEquals("ancient altar", pedestal.themeDescription)
        assertEquals(0, pedestal.pedestalIndex)
    }

    // ========== Biome Theme Tests ==========

    @Test
    fun `biome theme is preserved`() {
        val room = createStarterTreasureRoom()
        assertEquals("ancient_abyss", room.biomeTheme)

        val withItem = room.takeItem("veterans_longsword")
        assertEquals("ancient_abyss", withItem.biomeTheme)

        val looted = withItem.markAsLooted()
        assertEquals("ancient_abyss", looted.biomeTheme)
    }
}
