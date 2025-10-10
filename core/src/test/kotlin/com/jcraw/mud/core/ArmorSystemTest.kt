package com.jcraw.mud.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for armor system functionality
 */
class ArmorSystemTest {

    @Test
    fun `should equip armor from inventory`() {
        val armor = Entity.Item(
            id = "leather",
            name = "Leather Armor",
            description = "Basic armor",
            itemType = ItemType.ARMOR,
            defenseBonus = 2
        )

        val player = PlayerState(
            id = "test_player",
            name = "Test",
            currentRoomId = "room1",
            inventory = listOf(armor)
        )

        val updated = player.equipArmor(armor)

        assertEquals(armor, updated.equippedArmor)
        assertTrue(updated.inventory.none { it.id == armor.id })
    }

    @Test
    fun `should swap armor when equipping new one`() {
        val oldArmor = Entity.Item(
            id = "leather",
            name = "Leather Armor",
            description = "Basic armor",
            itemType = ItemType.ARMOR,
            defenseBonus = 2
        )

        val newArmor = Entity.Item(
            id = "chainmail",
            name = "Chainmail",
            description = "Heavy armor",
            itemType = ItemType.ARMOR,
            defenseBonus = 4
        )

        val player = PlayerState(
            id = "test_player",
            name = "Test",
            currentRoomId = "room1",
            inventory = listOf(newArmor),
            equippedArmor = oldArmor
        )

        val updated = player.equipArmor(newArmor)

        assertEquals(newArmor, updated.equippedArmor)
        assertTrue(updated.inventory.any { it.id == oldArmor.id })
        assertTrue(updated.inventory.none { it.id == newArmor.id })
    }

    @Test
    fun `should unequip armor back to inventory`() {
        val armor = Entity.Item(
            id = "leather",
            name = "Leather Armor",
            description = "Basic armor",
            itemType = ItemType.ARMOR,
            defenseBonus = 2
        )

        val player = PlayerState(
            id = "test_player",
            name = "Test",
            currentRoomId = "room1",
            equippedArmor = armor
        )

        val updated = player.unequipArmor()

        assertNull(updated.equippedArmor)
        assertTrue(updated.inventory.any { it.id == armor.id })
    }

    @Test
    fun `should return correct armor defense bonus`() {
        val armor = Entity.Item(
            id = "chainmail",
            name = "Chainmail",
            description = "Heavy armor",
            itemType = ItemType.ARMOR,
            defenseBonus = 4
        )

        val playerWithArmor = PlayerState(
            id = "test_player",
            name = "Test",
            currentRoomId = "room1",
            equippedArmor = armor
        )

        val playerWithoutArmor = PlayerState(
            id = "test_player",
            name = "Test",
            currentRoomId = "room1"
        )

        assertEquals(4, playerWithArmor.getArmorDefenseBonus())
        assertEquals(0, playerWithoutArmor.getArmorDefenseBonus())
    }

    @Test
    fun `unequipping when no armor equipped should return unchanged state`() {
        val player = PlayerState(
            id = "test_player",
            name = "Test",
            currentRoomId = "room1"
        )

        val updated = player.unequipArmor()

        assertEquals(player, updated)
    }
}
