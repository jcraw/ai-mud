package com.jcraw.mud.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Unit tests for InventoryComponent
 *
 * Focus: Behavioral tests for weight calculation, capacity limits, equipping,
 * gold management, and inventory operations
 */
class InventoryComponentTest {

    // Test fixtures
    private val swordTemplate = ItemTemplate(
        id = "iron_sword",
        name = "Iron Sword",
        type = ItemType.WEAPON,
        tags = listOf("sharp", "metal"),
        properties = mapOf("weight" to "3.0", "damage" to "10"),
        rarity = Rarity.COMMON,
        description = "A sturdy iron sword",
        equipSlot = EquipSlot.HANDS_MAIN
    )

    private val axeTemplate = ItemTemplate(
        id = "battle_axe",
        name = "Battle Axe",
        type = ItemType.WEAPON,
        tags = listOf("sharp", "metal", "heavy"),
        properties = mapOf("weight" to "8.0", "damage" to "15"),
        rarity = Rarity.UNCOMMON,
        description = "A heavy two-handed axe",
        equipSlot = EquipSlot.HANDS_BOTH
    )

    private val armorTemplate = ItemTemplate(
        id = "leather_armor",
        name = "Leather Armor",
        type = ItemType.ARMOR,
        tags = listOf("protective", "leather"),
        properties = mapOf("weight" to "5.0", "defense" to "5"),
        rarity = Rarity.COMMON,
        description = "Light leather armor",
        equipSlot = EquipSlot.CHEST
    )

    private val potionTemplate = ItemTemplate(
        id = "health_potion",
        name = "Health Potion",
        type = ItemType.CONSUMABLE,
        tags = listOf("edible", "liquid", "restorative"),
        properties = mapOf("weight" to "0.5", "healing" to "20"),
        rarity = Rarity.COMMON,
        description = "Restores health"
    )

    private val oreTemplate = ItemTemplate(
        id = "iron_ore",
        name = "Iron Ore",
        type = ItemType.RESOURCE,
        tags = listOf("metal", "heavy"),
        properties = mapOf("weight" to "2.0", "value" to "10"),
        rarity = Rarity.COMMON,
        description = "Raw iron ore"
    )

    private val bagTemplate = ItemTemplate(
        id = "leather_bag",
        name = "Leather Bag",
        type = ItemType.CONTAINER,
        tags = listOf("container", "wearable"),
        properties = mapOf("weight" to "1.0", "capacity" to "10"),
        rarity = Rarity.COMMON,
        description = "Increases carrying capacity",
        equipSlot = EquipSlot.BACK
    )

    private val templates = mapOf(
        swordTemplate.id to swordTemplate,
        axeTemplate.id to axeTemplate,
        armorTemplate.id to armorTemplate,
        potionTemplate.id to potionTemplate,
        oreTemplate.id to oreTemplate,
        bagTemplate.id to bagTemplate
    )

    private fun createInstance(template: ItemTemplate, quality: Int = 5, quantity: Int = 1): ItemInstance {
        return ItemInstance(
            templateId = template.id,
            quality = quality,
            quantity = quantity
        )
    }

    // ========== Weight Calculation Tests ==========

    @Test
    fun `empty inventory has zero weight`() {
        val inventory = InventoryComponent()
        assertEquals(0.0, inventory.currentWeight(templates))
    }

    @Test
    fun `single item weight calculated correctly`() {
        val sword = createInstance(swordTemplate)
        val inventory = InventoryComponent(items = listOf(sword))
        assertEquals(3.0, inventory.currentWeight(templates))
    }

    @Test
    fun `multiple items weight calculated correctly`() {
        val sword = createInstance(swordTemplate)
        val armor = createInstance(armorTemplate)
        val inventory = InventoryComponent(items = listOf(sword, armor))
        assertEquals(8.0, inventory.currentWeight(templates)) // 3.0 + 5.0
    }

    @Test
    fun `stacked items weight calculated correctly`() {
        val oreStack = createInstance(oreTemplate, quantity = 5)
        val inventory = InventoryComponent(items = listOf(oreStack))
        assertEquals(10.0, inventory.currentWeight(templates)) // 2.0 * 5
    }

    @Test
    fun `equipped items count toward weight`() {
        val sword = createInstance(swordTemplate)
        val inventory = InventoryComponent(
            items = listOf(sword),
            equipped = mapOf(EquipSlot.HANDS_MAIN to sword)
        )
        assertEquals(3.0, inventory.currentWeight(templates))
    }

    @Test
    fun `mixed equipped and unequipped items weight calculated correctly`() {
        val sword = createInstance(swordTemplate)
        val armor = createInstance(armorTemplate)
        val potion = createInstance(potionTemplate)
        val inventory = InventoryComponent(
            items = listOf(sword, armor, potion),
            equipped = mapOf(
                EquipSlot.HANDS_MAIN to sword,
                EquipSlot.CHEST to armor
            )
        )
        assertEquals(8.5, inventory.currentWeight(templates)) // 3.0 + 5.0 + 0.5
    }

    // ========== Capacity Tests ==========

    @Test
    fun `canAdd returns true when under capacity`() {
        val inventory = InventoryComponent(capacityWeight = 50.0)
        assertTrue(inventory.canAdd(swordTemplate, 1, templates))
    }

    @Test
    fun `canAdd returns false when would exceed capacity`() {
        val inventory = InventoryComponent(capacityWeight = 5.0)
        assertFalse(inventory.canAdd(axeTemplate, 1, templates)) // 8.0kg axe > 5.0kg capacity
    }

    @Test
    fun `canAdd accounts for existing weight`() {
        val sword = createInstance(swordTemplate)
        val inventory = InventoryComponent(
            items = listOf(sword),
            capacityWeight = 10.0
        )
        // Current: 3.0kg, Adding: 8.0kg, Total: 11.0kg > 10.0kg capacity
        assertFalse(inventory.canAdd(axeTemplate, 1, templates))
    }

    @Test
    fun `canAdd with quantity accounts for total weight`() {
        val inventory = InventoryComponent(capacityWeight = 10.0)
        assertFalse(inventory.canAdd(oreTemplate, 10, templates)) // 2.0 * 10 = 20.0kg
        assertTrue(inventory.canAdd(oreTemplate, 4, templates)) // 2.0 * 4 = 8.0kg
    }

    // ========== Add/Remove Tests ==========

    @Test
    fun `addItem adds item to inventory`() {
        val inventory = InventoryComponent()
        val sword = createInstance(swordTemplate)
        val updated = inventory.addItem(sword)

        assertEquals(1, updated.items.size)
        assertEquals(sword.id, updated.items[0].id)
    }

    @Test
    fun `addItem stacks identical items`() {
        val ore1 = createInstance(oreTemplate, quantity = 3)
        val inventory = InventoryComponent(items = listOf(ore1))

        val ore2 = createInstance(oreTemplate, quantity = 2)
        val updated = inventory.addItem(ore2)

        assertEquals(1, updated.items.size)
        assertEquals(5, updated.items[0].quantity) // Stacked 3 + 2
    }

    @Test
    fun `addItem does not stack different quality items`() {
        val sword1 = createInstance(swordTemplate, quality = 5)
        val inventory = InventoryComponent(items = listOf(sword1))

        val sword2 = createInstance(swordTemplate, quality = 8)
        val updated = inventory.addItem(sword2)

        assertEquals(2, updated.items.size) // Not stacked due to different quality
    }

    @Test
    fun `removeItem removes item by ID`() {
        val sword = createInstance(swordTemplate)
        val inventory = InventoryComponent(items = listOf(sword))

        val updated = inventory.removeItem(sword.id)

        assertNotNull(updated)
        assertEquals(0, updated.items.size)
    }

    @Test
    fun `removeItem returns null if item not found`() {
        val inventory = InventoryComponent()
        val updated = inventory.removeItem("nonexistent")
        assertNull(updated)
    }

    @Test
    fun `removeItem also removes from equipped`() {
        val sword = createInstance(swordTemplate)
        val inventory = InventoryComponent(
            items = listOf(sword),
            equipped = mapOf(EquipSlot.HANDS_MAIN to sword)
        )

        val updated = inventory.removeItem(sword.id)

        assertNotNull(updated)
        assertEquals(0, updated.items.size)
        assertEquals(0, updated.equipped.size)
    }

    @Test
    fun `removeQuantity reduces item stack`() {
        val ore = createInstance(oreTemplate, quantity = 5)
        val inventory = InventoryComponent(items = listOf(ore))

        val updated = inventory.removeQuantity(ore.id, 2)

        assertNotNull(updated)
        assertEquals(1, updated.items.size)
        assertEquals(3, updated.items[0].quantity)
    }

    @Test
    fun `removeQuantity removes item when fully consumed`() {
        val potion = createInstance(potionTemplate, quantity = 1)
        val inventory = InventoryComponent(items = listOf(potion))

        val updated = inventory.removeQuantity(potion.id, 1)

        assertNotNull(updated)
        assertEquals(0, updated.items.size)
    }

    // ========== Equip/Unequip Tests ==========

    @Test
    fun `equip adds item to equipped map`() {
        val sword = createInstance(swordTemplate)
        val inventory = InventoryComponent(items = listOf(sword))

        val updated = inventory.equip(sword, EquipSlot.HANDS_MAIN)

        assertNotNull(updated)
        assertEquals(sword.id, updated.equipped[EquipSlot.HANDS_MAIN]?.id)
    }

    @Test
    fun `equip returns null if item not in inventory`() {
        val inventory = InventoryComponent()
        val sword = createInstance(swordTemplate)

        val updated = inventory.equip(sword, EquipSlot.HANDS_MAIN)

        assertNull(updated)
    }

    @Test
    fun `equip two-handed weapon clears main and off-hand`() {
        val sword = createInstance(swordTemplate)
        val axe = createInstance(axeTemplate)
        val inventory = InventoryComponent(
            items = listOf(sword, axe),
            equipped = mapOf(
                EquipSlot.HANDS_MAIN to sword
            )
        )

        val updated = inventory.equip(axe, EquipSlot.HANDS_BOTH)

        assertNotNull(updated)
        assertNull(updated.equipped[EquipSlot.HANDS_MAIN])
        assertNull(updated.equipped[EquipSlot.HANDS_OFF])
        assertEquals(axe.id, updated.equipped[EquipSlot.HANDS_BOTH]?.id)
    }

    @Test
    fun `equip one-handed weapon clears two-handed weapon`() {
        val sword = createInstance(swordTemplate)
        val axe = createInstance(axeTemplate)
        val inventory = InventoryComponent(
            items = listOf(sword, axe),
            equipped = mapOf(EquipSlot.HANDS_BOTH to axe)
        )

        val updated = inventory.equip(sword, EquipSlot.HANDS_MAIN)

        assertNotNull(updated)
        assertNull(updated.equipped[EquipSlot.HANDS_BOTH])
        assertEquals(sword.id, updated.equipped[EquipSlot.HANDS_MAIN]?.id)
    }

    @Test
    fun `unequip removes item from equipped map`() {
        val sword = createInstance(swordTemplate)
        val inventory = InventoryComponent(
            items = listOf(sword),
            equipped = mapOf(EquipSlot.HANDS_MAIN to sword)
        )

        val updated = inventory.unequip(EquipSlot.HANDS_MAIN)

        assertEquals(0, updated.equipped.size)
        assertEquals(1, updated.items.size) // Item stays in inventory
    }

    @Test
    fun `isEquipped returns true for equipped items`() {
        val sword = createInstance(swordTemplate)
        val inventory = InventoryComponent(
            items = listOf(sword),
            equipped = mapOf(EquipSlot.HANDS_MAIN to sword)
        )

        assertTrue(inventory.isEquipped(sword.id))
    }

    @Test
    fun `isEquipped returns false for unequipped items`() {
        val sword = createInstance(swordTemplate)
        val inventory = InventoryComponent(items = listOf(sword))

        assertFalse(inventory.isEquipped(sword.id))
    }

    @Test
    fun `getEquipped returns equipped item`() {
        val sword = createInstance(swordTemplate)
        val inventory = InventoryComponent(
            items = listOf(sword),
            equipped = mapOf(EquipSlot.HANDS_MAIN to sword)
        )

        val equipped = inventory.getEquipped(EquipSlot.HANDS_MAIN)
        assertNotNull(equipped)
        assertEquals(sword.id, equipped.id)
    }

    // ========== Gold Tests ==========

    @Test
    fun `addGold increases gold amount`() {
        val inventory = InventoryComponent(gold = 100)
        val updated = inventory.addGold(50)
        assertEquals(150, updated.gold)
    }

    @Test
    fun `addGold with negative amount is clamped to zero`() {
        val inventory = InventoryComponent(gold = 50)
        val updated = inventory.addGold(-100)
        assertEquals(0, updated.gold)
    }

    @Test
    fun `removeGold decreases gold amount`() {
        val inventory = InventoryComponent(gold = 100)
        val updated = inventory.removeGold(30)
        assertNotNull(updated)
        assertEquals(70, updated.gold)
    }

    @Test
    fun `removeGold returns null if insufficient gold`() {
        val inventory = InventoryComponent(gold = 10)
        val updated = inventory.removeGold(50)
        assertNull(updated)
    }

    @Test
    fun `removeGold exact amount works`() {
        val inventory = InventoryComponent(gold = 50)
        val updated = inventory.removeGold(50)
        assertNotNull(updated)
        assertEquals(0, updated.gold)
    }

    // ========== Capacity Management Tests ==========

    @Test
    fun `augmentCapacity increases capacity`() {
        val inventory = InventoryComponent(capacityWeight = 50.0)
        val updated = inventory.augmentCapacity(10.0)
        assertEquals(60.0, updated.capacityWeight)
    }

    @Test
    fun `setCapacity updates capacity`() {
        val inventory = InventoryComponent(capacityWeight = 50.0)
        val updated = inventory.setCapacity(75.0)
        assertEquals(75.0, updated.capacityWeight)
    }

    @Test
    fun `setCapacity enforces minimum capacity`() {
        val inventory = InventoryComponent(capacityWeight = 50.0)
        val updated = inventory.setCapacity(5.0)
        assertEquals(10.0, updated.capacityWeight) // Minimum is 10.0
    }

    // ========== Utility Tests ==========

    @Test
    fun `getItem finds item by ID`() {
        val sword = createInstance(swordTemplate)
        val inventory = InventoryComponent(items = listOf(sword))

        val found = inventory.getItem(sword.id)
        assertNotNull(found)
        assertEquals(sword.id, found.id)
    }

    @Test
    fun `getItem returns null if not found`() {
        val inventory = InventoryComponent()
        val found = inventory.getItem("nonexistent")
        assertNull(found)
    }

    @Test
    fun `findItemsByTemplate finds all matching instances`() {
        val ore1 = createInstance(oreTemplate, quantity = 3)
        val ore2 = createInstance(oreTemplate, quantity = 5, quality = 7)
        val sword = createInstance(swordTemplate)
        val inventory = InventoryComponent(items = listOf(ore1, ore2, sword))

        val ores = inventory.findItemsByTemplate(oreTemplate.id)
        assertEquals(2, ores.size)
        assertTrue(ores.all { it.templateId == oreTemplate.id })
    }

    // ========== Integration Tests ==========

    @Test
    fun `full lifecycle - add, equip, unequip, remove`() {
        var inventory = InventoryComponent(capacityWeight = 50.0)

        // Add item
        val sword = createInstance(swordTemplate)
        inventory = inventory.addItem(sword)
        assertEquals(1, inventory.items.size)

        // Equip item
        inventory = inventory.equip(sword, EquipSlot.HANDS_MAIN) ?: inventory
        assertTrue(inventory.isEquipped(sword.id))

        // Unequip item
        inventory = inventory.unequip(EquipSlot.HANDS_MAIN)
        assertFalse(inventory.isEquipped(sword.id))

        // Remove item
        inventory = inventory.removeItem(sword.id) ?: inventory
        assertEquals(0, inventory.items.size)
    }

    @Test
    fun `complex inventory scenario with weight management`() {
        var inventory = InventoryComponent(capacityWeight = 20.0)

        // Add items
        val sword = createInstance(swordTemplate) // 3kg
        val armor = createInstance(armorTemplate) // 5kg
        val ore = createInstance(oreTemplate, quantity = 4) // 8kg total
        inventory = inventory.addItem(sword)
        inventory = inventory.addItem(armor)
        inventory = inventory.addItem(ore)

        // Total weight: 3 + 5 + 8 = 16kg
        assertEquals(16.0, inventory.currentWeight(templates))

        // Can't add axe (8kg) - would exceed capacity
        assertFalse(inventory.canAdd(axeTemplate, 1, templates))

        // Can add potion (0.5kg)
        assertTrue(inventory.canAdd(potionTemplate, 1, templates))

        // Augment capacity with bag
        inventory = inventory.augmentCapacity(10.0)
        assertEquals(30.0, inventory.capacityWeight)

        // Now can add axe
        assertTrue(inventory.canAdd(axeTemplate, 1, templates))
    }
}
