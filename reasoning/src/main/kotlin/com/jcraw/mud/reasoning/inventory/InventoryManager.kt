package com.jcraw.mud.reasoning.inventory

import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.skills.CapacityCalculator

/**
 * Manager for high-level inventory operations
 * Coordinates between InventoryComponent, SkillComponent, and capacity calculations
 *
 * This manager handles:
 * - Capacity recalculation when Strength changes
 * - Weight validation before adding items
 * - Equipped item validation
 *
 * Design principles:
 * - Stateless: All operations are pure functions
 * - No side effects: Returns new components, doesn't modify inputs
 * - Integration point: Bridges multiple systems (inventory, skills, items)
 */
class InventoryManager(
    private val capacityCalculator: CapacityCalculator = CapacityCalculator()
) {

    /**
     * Update inventory capacity based on current Strength skill
     * Call this when Strength level changes or perks are acquired
     *
     * @param inventory Current InventoryComponent
     * @param skills Current SkillComponent
     * @param templates Map of item templates for property lookup
     * @param activePerks List of active perk names affecting capacity
     * @return Updated InventoryComponent with new capacity
     */
    fun updateCapacity(
        inventory: InventoryComponent,
        skills: SkillComponent,
        templates: Map<String, ItemTemplate>,
        activePerks: List<String> = emptyList()
    ): InventoryComponent {
        val newCapacity = capacityCalculator.calculateFullCapacity(
            skills,
            inventory,
            templates,
            activePerks
        )
        return inventory.setCapacity(newCapacity)
    }

    /**
     * Validate and add item to inventory
     * Checks weight capacity before adding
     *
     * @param inventory Current InventoryComponent
     * @param instance ItemInstance to add
     * @param template ItemTemplate for weight lookup
     * @param templates All templates for weight calculation
     * @return AddResult with success/failure and updated inventory
     */
    fun addItem(
        inventory: InventoryComponent,
        instance: ItemInstance,
        template: ItemTemplate,
        templates: Map<String, ItemTemplate>
    ): AddResult {
        // Check if item can be added without exceeding capacity
        if (!inventory.canAdd(template, instance.quantity, templates)) {
            return AddResult.Failure("Too heavy to carry! You need ${template.getWeight() * instance.quantity}kg capacity.")
        }

        val updatedInventory = inventory.addItem(instance)
        return AddResult.Success(updatedInventory)
    }

    /**
     * Validate and remove item from inventory
     *
     * @param inventory Current InventoryComponent
     * @param instanceId ID of instance to remove
     * @return RemoveResult with success/failure and updated inventory
     */
    fun removeItem(
        inventory: InventoryComponent,
        instanceId: String
    ): RemoveResult {
        val item = inventory.getItem(instanceId)
            ?: return RemoveResult.Failure("Item not found in inventory")

        val updatedInventory = inventory.removeItem(instanceId)
            ?: return RemoveResult.Failure("Failed to remove item")

        return RemoveResult.Success(updatedInventory, item)
    }

    /**
     * Validate and equip item to slot
     * Checks if item is in inventory and matches slot type
     *
     * @param inventory Current InventoryComponent
     * @param instance ItemInstance to equip
     * @param template ItemTemplate for validation
     * @return EquipResult with success/failure and updated inventory
     */
    fun equipItem(
        inventory: InventoryComponent,
        instance: ItemInstance,
        template: ItemTemplate
    ): EquipResult {
        // Verify item has an equip slot
        val slot = template.equipSlot
            ?: return EquipResult.Failure("${template.name} cannot be equipped")

        // Equip to slot
        val updatedInventory = inventory.equip(instance, slot)
            ?: return EquipResult.Failure("Item not in inventory")

        return EquipResult.Success(updatedInventory, slot)
    }

    /**
     * Unequip item from slot
     *
     * @param inventory Current InventoryComponent
     * @param slot EquipSlot to unequip from
     * @return Updated InventoryComponent with item unequipped
     */
    fun unequipItem(
        inventory: InventoryComponent,
        slot: EquipSlot
    ): InventoryComponent {
        return inventory.unequip(slot)
    }

    /**
     * Check if inventory is over capacity
     * Returns true if current weight exceeds capacity
     *
     * @param inventory InventoryComponent to check
     * @param templates Map of templates for weight calculation
     * @return True if over capacity
     */
    fun isOverCapacity(
        inventory: InventoryComponent,
        templates: Map<String, ItemTemplate>
    ): Boolean {
        val currentWeight = inventory.currentWeight(templates)
        return currentWeight > inventory.capacityWeight
    }

    /**
     * Get current weight and capacity as formatted string
     *
     * @param inventory InventoryComponent
     * @param templates Map of templates for weight calculation
     * @return String like "45.5kg / 50.0kg"
     */
    fun getWeightDisplay(
        inventory: InventoryComponent,
        templates: Map<String, ItemTemplate>
    ): String {
        val currentWeight = inventory.currentWeight(templates)
        return String.format("%.1fkg / %.1fkg", currentWeight, inventory.capacityWeight)
    }
}

/**
 * Result of adding item to inventory
 */
sealed class AddResult {
    data class Success(val inventory: InventoryComponent) : AddResult()
    data class Failure(val reason: String) : AddResult()
}

/**
 * Result of removing item from inventory
 */
sealed class RemoveResult {
    data class Success(val inventory: InventoryComponent, val removedItem: ItemInstance) : RemoveResult()
    data class Failure(val reason: String) : RemoveResult()
}

/**
 * Result of equipping item
 */
sealed class EquipResult {
    data class Success(val inventory: InventoryComponent, val slot: EquipSlot) : EquipResult()
    data class Failure(val reason: String) : EquipResult()
}
