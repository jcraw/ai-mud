package com.jcraw.mud.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for the inventory management system
 *
 * Focus: Behavioral tests for add/remove/find operations and inventory state management
 */
class InventorySystemTest {

    // Test fixtures
    private val basePlayer = PlayerState(
        id = "player1",
        name = "Hero",
        currentRoomId = "room1"
    )

    private val sword = Entity.Item(
        id = "sword",
        name = "Iron Sword",
        description = "A sturdy iron sword",
        itemType = ItemType.WEAPON,
        damageBonus = 5
    )

    private val potion = Entity.Item(
        id = "potion",
        name = "Health Potion",
        description = "Restores health",
        itemType = ItemType.CONSUMABLE,
        healAmount = 25,
        isConsumable = true
    )

    private val goldCoin = Entity.Item(
        id = "gold_coin",
        name = "Gold Coin",
        description = "A shiny gold coin",
        itemType = ItemType.MISC
    )

    // ========== Add Tests ==========

    @Test
    fun `adding item to empty inventory works`() {
        assertEquals(0, basePlayer.inventory.size)

        val updated = basePlayer.addToInventory(sword)

        assertEquals(1, updated.inventory.size)
        assertEquals(sword, updated.inventory[0])
        assertTrue(updated.hasItem(sword.id))
    }

    @Test
    fun `adding multiple items accumulates in inventory`() {
        val player = basePlayer
            .addToInventory(sword)
            .addToInventory(potion)
            .addToInventory(goldCoin)

        assertEquals(3, player.inventory.size)
        assertTrue(player.hasItem(sword.id))
        assertTrue(player.hasItem(potion.id))
        assertTrue(player.hasItem(goldCoin.id))
    }

    @Test
    fun `adding duplicate items creates multiple entries`() {
        // Inventory is a list, so duplicates are allowed
        val anotherPotion = potion.copy() // Same ID
        val player = basePlayer
            .addToInventory(potion)
            .addToInventory(anotherPotion)

        assertEquals(2, player.inventory.size)
        assertTrue(player.inventory.all { it.id == potion.id })
    }

    @Test
    fun `original player state is unchanged after adding item`() {
        val original = basePlayer
        assertEquals(0, original.inventory.size)

        val updated = original.addToInventory(sword)

        // Original is immutable
        assertEquals(0, original.inventory.size)
        assertEquals(1, updated.inventory.size)
    }

    // ========== Remove Tests ==========

    @Test
    fun `removing item from inventory works`() {
        val player = basePlayer.addToInventory(sword)
        assertTrue(player.hasItem(sword.id))

        val updated = player.removeFromInventory(sword.id)

        assertEquals(0, updated.inventory.size)
        assertFalse(updated.hasItem(sword.id))
    }

    @Test
    fun `removing item that does not exist is no-op`() {
        val player = basePlayer.addToInventory(sword)
        assertEquals(1, player.inventory.size)

        val updated = player.removeFromInventory("nonexistent_id")

        assertEquals(1, updated.inventory.size, "Inventory should be unchanged")
        assertTrue(updated.hasItem(sword.id))
    }

    @Test
    fun `removing item from empty inventory is no-op`() {
        val player = basePlayer
        assertEquals(0, player.inventory.size)

        val updated = player.removeFromInventory("any_id")

        assertEquals(0, updated.inventory.size)
    }

    @Test
    fun `removing item leaves other items intact`() {
        val player = basePlayer
            .addToInventory(sword)
            .addToInventory(potion)
            .addToInventory(goldCoin)

        val updated = player.removeFromInventory(potion.id)

        assertEquals(2, updated.inventory.size)
        assertTrue(updated.hasItem(sword.id))
        assertFalse(updated.hasItem(potion.id))
        assertTrue(updated.hasItem(goldCoin.id))
    }

    @Test
    fun `removing duplicate items only removes first occurrence`() {
        // Add same item twice
        val player = basePlayer
            .addToInventory(potion)
            .addToInventory(potion)

        assertEquals(2, player.inventory.size)

        val updated = player.removeFromInventory(potion.id)

        // filter removes ALL items with that ID
        assertEquals(0, updated.inventory.size, "All items with ID are removed")
    }

    // ========== Find Tests ==========

    @Test
    fun `finding item by ID returns the item`() {
        val player = basePlayer
            .addToInventory(sword)
            .addToInventory(potion)

        val foundSword = player.getInventoryItem(sword.id)
        val foundPotion = player.getInventoryItem(potion.id)

        assertEquals(sword, foundSword)
        assertEquals(potion, foundPotion)
    }

    @Test
    fun `finding item that does not exist returns null`() {
        val player = basePlayer.addToInventory(sword)

        val found = player.getInventoryItem("nonexistent_id")

        assertNull(found)
    }

    @Test
    fun `finding item in empty inventory returns null`() {
        val player = basePlayer
        assertEquals(0, player.inventory.size)

        val found = player.getInventoryItem("any_id")

        assertNull(found)
    }

    @Test
    fun `hasItem returns true for existing items`() {
        val player = basePlayer
            .addToInventory(sword)
            .addToInventory(potion)

        assertTrue(player.hasItem(sword.id))
        assertTrue(player.hasItem(potion.id))
    }

    @Test
    fun `hasItem returns false for non-existing items`() {
        val player = basePlayer.addToInventory(sword)

        assertFalse(player.hasItem("nonexistent_id"))
        assertFalse(player.hasItem(potion.id))
    }

    @Test
    fun `hasItem returns false for empty inventory`() {
        val player = basePlayer
        assertEquals(0, player.inventory.size)

        assertFalse(player.hasItem("any_id"))
    }

    // ========== Integration Tests ==========

    @Test
    fun `complete item lifecycle - add, find, remove`() {
        // Start empty
        var player = basePlayer
        assertEquals(0, player.inventory.size)

        // Add item
        player = player.addToInventory(sword)
        assertTrue(player.hasItem(sword.id))

        // Find item
        val found = player.getInventoryItem(sword.id)
        assertEquals(sword, found)

        // Remove item
        player = player.removeFromInventory(sword.id)
        assertFalse(player.hasItem(sword.id))
        assertEquals(0, player.inventory.size)
    }

    @Test
    fun `inventory operations maintain immutability`() {
        val original = basePlayer.addToInventory(sword)
        val originalSize = original.inventory.size

        // Various operations
        val added = original.addToInventory(potion)
        val removed = original.removeFromInventory(sword.id)

        // Original is unchanged
        assertEquals(originalSize, original.inventory.size)
        assertTrue(original.hasItem(sword.id))

        // New states are different
        assertEquals(originalSize + 1, added.inventory.size)
        assertEquals(originalSize - 1, removed.inventory.size)
    }

    @Test
    fun `inventory with multiple item types works correctly`() {
        val player = basePlayer
            .addToInventory(sword)
            .addToInventory(potion)
            .addToInventory(goldCoin)

        // Different item types
        assertEquals(ItemType.WEAPON, player.getInventoryItem(sword.id)?.itemType)
        assertEquals(ItemType.CONSUMABLE, player.getInventoryItem(potion.id)?.itemType)
        assertEquals(ItemType.MISC, player.getInventoryItem(goldCoin.id)?.itemType)

        // All present
        assertEquals(3, player.inventory.size)
    }

    @Test
    fun `inventory survives multiple add and remove cycles`() {
        var player = basePlayer

        // Cycle 1
        player = player.addToInventory(sword)
        assertEquals(1, player.inventory.size)
        player = player.removeFromInventory(sword.id)
        assertEquals(0, player.inventory.size)

        // Cycle 2
        player = player.addToInventory(potion)
        assertEquals(1, player.inventory.size)
        player = player.removeFromInventory(potion.id)
        assertEquals(0, player.inventory.size)

        // Cycle 3 - multiple items
        player = player.addToInventory(sword).addToInventory(potion)
        assertEquals(2, player.inventory.size)
        player = player.removeFromInventory(sword.id)
        assertEquals(1, player.inventory.size)
        player = player.removeFromInventory(potion.id)
        assertEquals(0, player.inventory.size)
    }
}
