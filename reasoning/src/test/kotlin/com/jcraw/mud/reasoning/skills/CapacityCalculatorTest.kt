package com.jcraw.mud.reasoning.skills

import com.jcraw.mud.core.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CapacityCalculatorTest {

    private val calculator = CapacityCalculator()

    @Test
    fun `calculateCapacity - base capacity from Strength`() {
        val capacity = calculator.calculateCapacity(strengthLevel = 10)
        assertEquals(50.0, capacity, 0.01)
    }

    @Test
    fun `calculateCapacity - base capacity scales linearly`() {
        assertEquals(25.0, calculator.calculateCapacity(strengthLevel = 5), 0.01)
        assertEquals(100.0, calculator.calculateCapacity(strengthLevel = 20), 0.01)
        assertEquals(0.0, calculator.calculateCapacity(strengthLevel = 0), 0.01)
    }

    @Test
    fun `calculateCapacity - minimum capacity enforced`() {
        val capacity = calculator.calculateCapacity(strengthLevel = 0)
        assertEquals(CapacityCalculator.MINIMUM_CAPACITY, capacity, 0.01)
    }

    @Test
    fun `calculateCapacity - bag bonuses added`() {
        val capacity = calculator.calculateCapacity(
            strengthLevel = 10,
            bagBonuses = listOf(10.0, 5.0)
        )
        assertEquals(65.0, capacity, 0.01) // 50 + 10 + 5
    }

    @Test
    fun `calculateCapacity - perk multipliers applied to base`() {
        val capacity = calculator.calculateCapacity(
            strengthLevel = 10,
            perkMultipliers = listOf(0.2) // +20%
        )
        assertEquals(60.0, capacity, 0.01) // 50 + (50 * 0.2)
    }

    @Test
    fun `calculateCapacity - multiple perk multipliers stack additively`() {
        val capacity = calculator.calculateCapacity(
            strengthLevel = 10,
            perkMultipliers = listOf(0.2, 0.15) // +20% and +15% = +35%
        )
        assertEquals(67.5, capacity, 0.01) // 50 + (50 * 0.35)
    }

    @Test
    fun `calculateCapacity - all bonuses combined`() {
        val capacity = calculator.calculateCapacity(
            strengthLevel = 10,
            bagBonuses = listOf(10.0),
            perkMultipliers = listOf(0.2)
        )
        assertEquals(70.0, capacity, 0.01) // 50 + 10 + (50 * 0.2)
    }

    @Test
    fun `calculateBagBonuses - extracts capacity from equipped items`() {
        val template = ItemTemplate(
            id = "backpack",
            name = "Backpack",
            type = ItemType.CONTAINER,
            properties = mapOf("capacity" to "10.0"),
            rarity = Rarity.COMMON,
            description = "A sturdy backpack",
            equipSlot = EquipSlot.BACK
        )
        val instance = ItemInstance(
            id = "instance1",
            templateId = "backpack",
            quality = 5,
            charges = null,
            quantity = 1
        )
        val equipped = mapOf(EquipSlot.BACK to instance)
        val templates = mapOf("backpack" to template)

        val bonuses = calculator.calculateBagBonuses(equipped, templates)
        assertEquals(listOf(10.0), bonuses)
    }

    @Test
    fun `calculateBagBonuses - multiple bags stack`() {
        val backpack = ItemTemplate(
            id = "backpack",
            name = "Backpack",
            type = ItemType.CONTAINER,
            properties = mapOf("capacity" to "10.0"),
            rarity = Rarity.COMMON,
            description = "A backpack",
            equipSlot = EquipSlot.BACK
        )
        val pouch = ItemTemplate(
            id = "pouch",
            name = "Pouch",
            type = ItemType.CONTAINER,
            properties = mapOf("capacity" to "5.0"),
            rarity = Rarity.COMMON,
            description = "A small pouch",
            equipSlot = EquipSlot.ACCESSORY_1
        )
        val equipped = mapOf(
            EquipSlot.BACK to ItemInstance("i1", "backpack", 5, null, 1),
            EquipSlot.ACCESSORY_1 to ItemInstance("i2", "pouch", 5, null, 1)
        )
        val templates = mapOf("backpack" to backpack, "pouch" to pouch)

        val bonuses = calculator.calculateBagBonuses(equipped, templates)
        assertEquals(2, bonuses.size)
        assertEquals(15.0, bonuses.sum(), 0.01)
    }

    @Test
    fun `calculateBagBonuses - ignores items without capacity property`() {
        val sword = ItemTemplate(
            id = "sword",
            name = "Sword",
            type = ItemType.WEAPON,
            properties = mapOf("damage" to "10"),
            rarity = Rarity.COMMON,
            description = "A sword",
            equipSlot = EquipSlot.HANDS_MAIN
        )
        val equipped = mapOf(
            EquipSlot.HANDS_MAIN to ItemInstance("i1", "sword", 5, null, 1)
        )
        val templates = mapOf("sword" to sword)

        val bonuses = calculator.calculateBagBonuses(equipped, templates)
        assertEquals(emptyList<Double>(), bonuses)
    }

    @Test
    fun `calculatePerkMultipliers - Pack Mule gives 20 percent`() {
        val multipliers = calculator.calculatePerkMultipliers(listOf("Pack Mule"))
        assertEquals(listOf(0.2), multipliers)
    }

    @Test
    fun `calculatePerkMultipliers - multiple perks stack`() {
        val multipliers = calculator.calculatePerkMultipliers(listOf("Pack Mule", "Strong Back"))
        assertEquals(2, multipliers.size)
        assertEquals(0.35, multipliers.sum(), 0.01) // 0.2 + 0.15
    }

    @Test
    fun `calculatePerkMultipliers - unknown perks ignored`() {
        val multipliers = calculator.calculatePerkMultipliers(listOf("Unknown Perk"))
        assertEquals(emptyList<Double>(), multipliers)
    }

    @Test
    fun `calculateFullCapacity - integrates all systems`() {
        val skillComponent = SkillComponent(
            skills = mapOf(
                "Strength" to SkillState("Strength", 10, 0L, emptyList<String>(), isUnlocked = true)
            )
        )
        val backpack = ItemTemplate(
            id = "backpack",
            name = "Backpack",
            type = ItemType.CONTAINER,
            properties = mapOf("capacity" to "10.0"),
            rarity = Rarity.COMMON,
            description = "A backpack",
            equipSlot = EquipSlot.BACK
        )
        val inventoryComponent = InventoryComponent(
            items = emptyList(),
            equipped = mapOf(
                EquipSlot.BACK to ItemInstance("i1", "backpack", 5, null, 1)
            ),
            gold = 0,
            capacityWeight = 50.0
        )
        val templates = mapOf("backpack" to backpack)

        val capacity = calculator.calculateFullCapacity(
            skillComponent,
            inventoryComponent,
            templates,
            listOf("Pack Mule")
        )

        // Strength 10 = 50kg base
        // Backpack = +10kg
        // Pack Mule = +20% of base (50 * 0.2 = +10kg)
        // Total = 70kg
        assertEquals(70.0, capacity, 0.01)
    }
}
