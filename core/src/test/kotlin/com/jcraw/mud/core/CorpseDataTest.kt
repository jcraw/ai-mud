package com.jcraw.mud.core

import kotlin.test.*

class CorpseDataTest {

    private fun createTestInventory(): InventoryComponent {
        return InventoryComponent(
            items = listOf(
                ItemInstance.create("sword_1", "Iron Sword", ItemType.WEAPON, 1),
                ItemInstance.create("potion_1", "Health Potion", ItemType.CONSUMABLE, 1)
            ),
            maxWeight = 50.0f,
            currentWeight = 15.0f
        )
    }

    private fun createTestEquipment(): List<ItemInstance> {
        return listOf(
            ItemInstance.create("armor_1", "Leather Armor", ItemType.ARMOR, 1)
        )
    }

    @Test
    fun `hasDecayed returns false when not yet decayed`() {
        val corpse = CorpseData(
            id = "corpse_1",
            playerId = "player_1",
            spaceId = "space_1",
            inventory = createTestInventory(),
            equipment = createTestEquipment(),
            gold = 100,
            decayTimer = 5000L,
            looted = false
        )

        assertFalse(corpse.hasDecayed(1000L))
        assertFalse(corpse.hasDecayed(4999L))
    }

    @Test
    fun `hasDecayed returns true when decay time reached`() {
        val corpse = CorpseData(
            id = "corpse_1",
            playerId = "player_1",
            spaceId = "space_1",
            inventory = createTestInventory(),
            equipment = createTestEquipment(),
            gold = 100,
            decayTimer = 5000L,
            looted = false
        )

        assertTrue(corpse.hasDecayed(5000L))
        assertTrue(corpse.hasDecayed(6000L))
    }

    @Test
    fun `turnsUntilDecay calculates remaining time correctly`() {
        val corpse = CorpseData(
            id = "corpse_1",
            playerId = "player_1",
            spaceId = "space_1",
            inventory = createTestInventory(),
            equipment = createTestEquipment(),
            gold = 100,
            decayTimer = 5000L,
            looted = false
        )

        assertEquals(4000L, corpse.turnsUntilDecay(1000L))
        assertEquals(1000L, corpse.turnsUntilDecay(4000L))
    }

    @Test
    fun `turnsUntilDecay returns 0 when already decayed`() {
        val corpse = CorpseData(
            id = "corpse_1",
            playerId = "player_1",
            spaceId = "space_1",
            inventory = createTestInventory(),
            equipment = createTestEquipment(),
            gold = 100,
            decayTimer = 5000L,
            looted = false
        )

        assertEquals(0L, corpse.turnsUntilDecay(5000L))
        assertEquals(0L, corpse.turnsUntilDecay(6000L))
    }

    @Test
    fun `markLooted sets looted flag to true`() {
        val corpse = CorpseData(
            id = "corpse_1",
            playerId = "player_1",
            spaceId = "space_1",
            inventory = createTestInventory(),
            equipment = createTestEquipment(),
            gold = 100,
            decayTimer = 5000L,
            looted = false
        )

        val looted = corpse.markLooted()

        assertTrue(looted.looted)
        assertEquals(corpse.id, looted.id)
        assertEquals(corpse.inventory, looted.inventory)
    }

    @Test
    fun `itemCount returns correct total count`() {
        val corpse = CorpseData(
            id = "corpse_1",
            playerId = "player_1",
            spaceId = "space_1",
            inventory = createTestInventory(), // 2 items
            equipment = createTestEquipment(), // 1 item
            gold = 100,
            decayTimer = 5000L,
            looted = false
        )

        assertEquals(3, corpse.itemCount())
    }

    @Test
    fun `itemCount returns 0 for empty corpse`() {
        val corpse = CorpseData(
            id = "corpse_1",
            playerId = "player_1",
            spaceId = "space_1",
            inventory = InventoryComponent(),
            equipment = emptyList(),
            gold = 0,
            decayTimer = 5000L,
            looted = false
        )

        assertEquals(0, corpse.itemCount())
    }

    @Test
    fun `contentsSummary includes gold`() {
        val corpse = CorpseData(
            id = "corpse_1",
            playerId = "player_1",
            spaceId = "space_1",
            inventory = InventoryComponent(),
            equipment = emptyList(),
            gold = 150,
            decayTimer = 5000L,
            looted = false
        )

        val summary = corpse.contentsSummary()
        assertTrue(summary.contains("150 gold"))
    }

    @Test
    fun `contentsSummary includes items and equipment`() {
        val corpse = CorpseData(
            id = "corpse_1",
            playerId = "player_1",
            spaceId = "space_1",
            inventory = createTestInventory(), // 2 items
            equipment = createTestEquipment(), // 1 item
            gold = 100,
            decayTimer = 5000L,
            looted = false
        )

        val summary = corpse.contentsSummary()
        assertTrue(summary.contains("100 gold"))
        assertTrue(summary.contains("2 items"))
        assertTrue(summary.contains("1 equipped items"))
    }

    @Test
    fun `contentsSummary handles empty corpse`() {
        val corpse = CorpseData(
            id = "corpse_1",
            playerId = "player_1",
            spaceId = "space_1",
            inventory = InventoryComponent(),
            equipment = emptyList(),
            gold = 0,
            decayTimer = 5000L,
            looted = false
        )

        val summary = corpse.contentsSummary()
        assertEquals("", summary)
    }
}
