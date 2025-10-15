package com.jcraw.mud.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for the equipment system (weapons and armor)
 *
 * Focus: Behavioral tests for equip/unequip mechanics, item swapping, and stat bonuses
 */
class EquipmentSystemTest {

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

    private val battleAxe = Entity.Item(
        id = "axe",
        name = "Battle Axe",
        description = "A heavy battle axe",
        itemType = ItemType.WEAPON,
        damageBonus = 8
    )

    private val leatherArmor = Entity.Item(
        id = "leather",
        name = "Leather Armor",
        description = "Light leather armor",
        itemType = ItemType.ARMOR,
        defenseBonus = 3
    )

    private val chainmail = Entity.Item(
        id = "chainmail",
        name = "Chainmail",
        description = "Heavy chainmail armor",
        itemType = ItemType.ARMOR,
        defenseBonus = 6
    )

    // ========== Weapon Tests ==========

    @Test
    fun `equipping weapon removes it from inventory and sets it as equipped`() {
        val playerWithSword = basePlayer.addToInventory(sword)
        assertEquals(1, playerWithSword.inventory.size)
        assertNull(playerWithSword.equippedWeapon)

        val equipped = playerWithSword.equipWeapon(sword)

        assertEquals(sword, equipped.equippedWeapon)
        assertEquals(0, equipped.inventory.size, "Sword should be removed from inventory")
        assertFalse(equipped.hasItem(sword.id))
    }

    @Test
    fun `equipping weapon when already equipped swaps weapons`() {
        val playerWithSword = basePlayer
            .addToInventory(sword)
            .addToInventory(battleAxe)
            .equipWeapon(sword)

        // Now we have sword equipped, axe in inventory
        assertEquals(sword, playerWithSword.equippedWeapon)
        assertEquals(1, playerWithSword.inventory.size)
        assertTrue(playerWithSword.hasItem(battleAxe.id))

        // Equip the axe
        val playerWithAxe = playerWithSword.equipWeapon(battleAxe)

        assertEquals(battleAxe, playerWithAxe.equippedWeapon)
        assertEquals(1, playerWithAxe.inventory.size, "Should have 1 item (the old sword)")
        assertTrue(playerWithAxe.hasItem(sword.id), "Old weapon should be back in inventory")
        assertFalse(playerWithAxe.hasItem(battleAxe.id), "New weapon should not be in inventory")
    }

    @Test
    fun `unequipping weapon moves it back to inventory`() {
        val playerWithEquipped = basePlayer
            .addToInventory(sword)
            .equipWeapon(sword)

        assertEquals(sword, playerWithEquipped.equippedWeapon)
        assertEquals(0, playerWithEquipped.inventory.size)

        val unequipped = playerWithEquipped.unequipWeapon()

        assertNull(unequipped.equippedWeapon)
        assertEquals(1, unequipped.inventory.size)
        assertEquals(sword, unequipped.inventory[0])
    }

    @Test
    fun `unequipping weapon when none equipped does nothing`() {
        val player = basePlayer
        assertNull(player.equippedWeapon)

        val result = player.unequipWeapon()

        assertEquals(player, result, "State should be unchanged")
        assertNull(result.equippedWeapon)
    }

    @Test
    fun `weapon damage bonus returns correct value when equipped`() {
        val player = basePlayer
            .addToInventory(sword)
            .equipWeapon(sword)

        assertEquals(5, player.getWeaponDamageBonus())
    }

    @Test
    fun `weapon damage bonus returns zero when no weapon equipped`() {
        val player = basePlayer
        assertEquals(0, player.getWeaponDamageBonus())
    }

    // ========== Armor Tests ==========

    @Test
    fun `equipping armor removes it from inventory and sets it as equipped`() {
        val playerWithArmor = basePlayer.addToInventory(leatherArmor)
        assertEquals(1, playerWithArmor.inventory.size)
        assertNull(playerWithArmor.equippedArmor)

        val equipped = playerWithArmor.equipArmor(leatherArmor)

        assertEquals(leatherArmor, equipped.equippedArmor)
        assertEquals(0, equipped.inventory.size, "Armor should be removed from inventory")
        assertFalse(equipped.hasItem(leatherArmor.id))
    }

    @Test
    fun `equipping armor when already equipped swaps armor`() {
        val playerWithLeather = basePlayer
            .addToInventory(leatherArmor)
            .addToInventory(chainmail)
            .equipArmor(leatherArmor)

        // Now we have leather equipped, chainmail in inventory
        assertEquals(leatherArmor, playerWithLeather.equippedArmor)
        assertEquals(1, playerWithLeather.inventory.size)
        assertTrue(playerWithLeather.hasItem(chainmail.id))

        // Equip the chainmail
        val playerWithChainmail = playerWithLeather.equipArmor(chainmail)

        assertEquals(chainmail, playerWithChainmail.equippedArmor)
        assertEquals(1, playerWithChainmail.inventory.size, "Should have 1 item (the old leather)")
        assertTrue(playerWithChainmail.hasItem(leatherArmor.id), "Old armor should be back in inventory")
        assertFalse(playerWithChainmail.hasItem(chainmail.id), "New armor should not be in inventory")
    }

    @Test
    fun `unequipping armor moves it back to inventory`() {
        val playerWithEquipped = basePlayer
            .addToInventory(leatherArmor)
            .equipArmor(leatherArmor)

        assertEquals(leatherArmor, playerWithEquipped.equippedArmor)
        assertEquals(0, playerWithEquipped.inventory.size)

        val unequipped = playerWithEquipped.unequipArmor()

        assertNull(unequipped.equippedArmor)
        assertEquals(1, unequipped.inventory.size)
        assertEquals(leatherArmor, unequipped.inventory[0])
    }

    @Test
    fun `unequipping armor when none equipped does nothing`() {
        val player = basePlayer
        assertNull(player.equippedArmor)

        val result = player.unequipArmor()

        assertEquals(player, result, "State should be unchanged")
        assertNull(result.equippedArmor)
    }

    @Test
    fun `armor defense bonus returns correct value when equipped`() {
        val player = basePlayer
            .addToInventory(chainmail)
            .equipArmor(chainmail)

        assertEquals(6, player.getArmorDefenseBonus())
    }

    @Test
    fun `armor defense bonus returns zero when no armor equipped`() {
        val player = basePlayer
        assertEquals(0, player.getArmorDefenseBonus())
    }

    // ========== Combined Equipment Tests ==========

    @Test
    fun `player can have both weapon and armor equipped simultaneously`() {
        val fullyEquipped = basePlayer
            .addToInventory(sword)
            .addToInventory(leatherArmor)
            .equipWeapon(sword)
            .equipArmor(leatherArmor)

        assertEquals(sword, fullyEquipped.equippedWeapon)
        assertEquals(leatherArmor, fullyEquipped.equippedArmor)
        assertEquals(5, fullyEquipped.getWeaponDamageBonus())
        assertEquals(3, fullyEquipped.getArmorDefenseBonus())
        assertEquals(0, fullyEquipped.inventory.size)
    }

    @Test
    fun `equipment operations are independent`() {
        val player = basePlayer
            .addToInventory(sword)
            .addToInventory(leatherArmor)
            .equipWeapon(sword)

        // Equipping armor should not affect weapon
        val withArmor = player.equipArmor(leatherArmor)
        assertEquals(sword, withArmor.equippedWeapon, "Weapon should still be equipped")

        // Unequipping weapon should not affect armor
        val withoutWeapon = withArmor.unequipWeapon()
        assertEquals(leatherArmor, withoutWeapon.equippedArmor, "Armor should still be equipped")
        assertNull(withoutWeapon.equippedWeapon)
    }

    // ========== Edge Cases ==========

    @Test
    fun `equipping item not in inventory still works`() {
        // Note: The current implementation doesn't validate inventory ownership
        // It will equip the item and filter it from inventory (no-op since it's not there)
        val player = basePlayer
        assertFalse(player.hasItem(sword.id))

        val equipped = player.equipWeapon(sword)

        assertEquals(sword, equipped.equippedWeapon, "Item gets equipped even if not in inventory")
        assertEquals(0, equipped.inventory.size)
    }

    @Test
    fun `multiple equip and unequip cycles maintain consistency`() {
        var player = basePlayer.addToInventory(sword)

        // Cycle 1
        player = player.equipWeapon(sword)
        assertEquals(sword, player.equippedWeapon)
        assertEquals(0, player.inventory.size)

        player = player.unequipWeapon()
        assertNull(player.equippedWeapon)
        assertEquals(1, player.inventory.size)

        // Cycle 2
        player = player.equipWeapon(sword)
        assertEquals(sword, player.equippedWeapon)
        assertEquals(0, player.inventory.size)

        player = player.unequipWeapon()
        assertNull(player.equippedWeapon)
        assertEquals(1, player.inventory.size)
    }
}
