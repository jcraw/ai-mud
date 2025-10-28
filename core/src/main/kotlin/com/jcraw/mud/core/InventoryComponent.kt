package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Inventory component for entities with item storage
 * Manages items, equipment, gold, and weight capacity
 *
 * Design principles:
 * - Immutable: All methods return new instances
 * - Weight-limited: Capacity based on Strength skill (Strength * 5kg + augments)
 * - Equipped items included: Items in 'equipped' are also in 'items' list
 *
 * @param items All items owned (equipped and unequipped)
 * @param equipped Map of equipped items by slot
 * @param gold Gold currency amount
 * @param capacityWeight Maximum carrying capacity in kg (Strength*5 + bags + perks)
 */
@Serializable
data class InventoryComponent(
    val items: List<ItemInstance> = emptyList(),
    val equipped: Map<EquipSlot, ItemInstance> = emptyMap(),
    val gold: Int = 0,
    val capacityWeight: Double = 50.0, // Default 50kg (Strength 10 baseline)
    override val componentType: ComponentType = ComponentType.INVENTORY
) : Component {

    /**
     * Calculate current total weight from all items
     * Requires templates to get item weights
     *
     * @param templates Map of template ID to ItemTemplate for weight lookup
     * @return Total weight in kg
     */
    fun currentWeight(templates: Map<String, ItemTemplate>): Double {
        return items.sumOf { instance ->
            val template = templates[instance.templateId] ?: return@sumOf 0.0
            template.getWeight() * instance.quantity
        }
    }

    /**
     * Check if item(s) can be added without exceeding capacity
     *
     * @param template ItemTemplate to check
     * @param quantity Number of items to add
     * @param templates All templates for weight calculation
     * @return True if adding would not exceed capacity
     */
    fun canAdd(template: ItemTemplate, quantity: Int, templates: Map<String, ItemTemplate>): Boolean {
        val addedWeight = template.getWeight() * quantity
        val newWeight = currentWeight(templates) + addedWeight
        return newWeight <= capacityWeight
    }

    /**
     * Add item to inventory
     * Does not check capacity - use canAdd() first
     *
     * @param instance ItemInstance to add
     * @return New InventoryComponent with item added
     */
    fun addItem(instance: ItemInstance): InventoryComponent {
        // Check if we can stack with existing item
        val existingIndex = items.indexOfFirst {
            it.templateId == instance.templateId &&
            it.quality == instance.quality &&
            it.charges == instance.charges
        }

        val newItems = if (existingIndex >= 0) {
            // Stack with existing
            val existing = items[existingIndex]
            items.toMutableList().apply {
                set(existingIndex, existing.addQuantity(instance.quantity))
            }
        } else {
            // Add new item
            items + instance
        }

        return copy(items = newItems)
    }

    /**
     * Remove item from inventory by instance ID
     *
     * @param instanceId ID of instance to remove
     * @return New InventoryComponent with item removed, or null if item not found
     */
    fun removeItem(instanceId: String): InventoryComponent? {
        val item = items.find { it.id == instanceId } ?: return null
        val newItems = items.filterNot { it.id == instanceId }

        // Also remove from equipped if present
        val newEquipped = equipped.filterValues { it.id != instanceId }

        return copy(items = newItems, equipped = newEquipped)
    }

    /**
     * Remove quantity from item stack
     *
     * @param instanceId ID of instance to reduce
     * @param quantity Amount to remove
     * @return New InventoryComponent with reduced quantity, or null if item not found
     */
    fun removeQuantity(instanceId: String, quantity: Int): InventoryComponent? {
        val itemIndex = items.indexOfFirst { it.id == instanceId }
        if (itemIndex < 0) return null

        val item = items[itemIndex]
        val newItem = item.reduceQuantity(quantity)

        val newItems = if (newItem == null) {
            // Item fully consumed
            items.filterNot { it.id == instanceId }
        } else {
            items.toMutableList().apply { set(itemIndex, newItem) }
        }

        // Remove from equipped if fully consumed
        val newEquipped = if (newItem == null) {
            equipped.filterValues { it.id != instanceId }
        } else {
            equipped
        }

        return copy(items = newItems, equipped = newEquipped)
    }

    /**
     * Equip item to slot
     * Item must be in inventory
     * Two-handed weapons (HANDS_BOTH) clear off-hand slot
     *
     * @param instance ItemInstance to equip (must be in items list)
     * @param slot EquipSlot to equip to
     * @return New InventoryComponent with item equipped, or null if item not in inventory
     */
    fun equip(instance: ItemInstance, slot: EquipSlot): InventoryComponent? {
        // Verify item is in inventory
        if (!items.any { it.id == instance.id }) return null

        var newEquipped = equipped.toMutableMap()

        // Handle two-handed weapons
        if (slot == EquipSlot.HANDS_BOTH) {
            newEquipped.remove(EquipSlot.HANDS_MAIN)
            newEquipped.remove(EquipSlot.HANDS_OFF)
            newEquipped[EquipSlot.HANDS_BOTH] = instance
        } else if (slot == EquipSlot.HANDS_MAIN || slot == EquipSlot.HANDS_OFF) {
            // Remove two-handed weapon if present
            newEquipped.remove(EquipSlot.HANDS_BOTH)
            newEquipped[slot] = instance
        } else {
            newEquipped[slot] = instance
        }

        return copy(equipped = newEquipped.toMap())
    }

    /**
     * Unequip item from slot
     *
     * @param slot EquipSlot to unequip from
     * @return New InventoryComponent with item unequipped
     */
    fun unequip(slot: EquipSlot): InventoryComponent {
        val newEquipped = equipped.toMutableMap()
        newEquipped.remove(slot)
        return copy(equipped = newEquipped.toMap())
    }

    /**
     * Check if instance is equipped
     *
     * @param instanceId ID of instance to check
     * @return True if equipped in any slot
     */
    fun isEquipped(instanceId: String): Boolean {
        return equipped.values.any { it.id == instanceId }
    }

    /**
     * Get equipped item in slot
     *
     * @param slot Slot to check
     * @return ItemInstance if equipped, null otherwise
     */
    fun getEquipped(slot: EquipSlot): ItemInstance? {
        return equipped[slot]
    }

    /**
     * Add gold to inventory
     *
     * @param amount Amount of gold to add
     * @return New InventoryComponent with increased gold
     */
    fun addGold(amount: Int): InventoryComponent {
        return copy(gold = (gold + amount).coerceAtLeast(0))
    }

    /**
     * Remove gold from inventory
     *
     * @param amount Amount of gold to remove
     * @return New InventoryComponent with reduced gold, or null if insufficient gold
     */
    fun removeGold(amount: Int): InventoryComponent? {
        if (gold < amount) return null
        return copy(gold = gold - amount)
    }

    /**
     * Augment carrying capacity (from bags, perks, spells)
     *
     * @param amount Capacity increase in kg
     * @return New InventoryComponent with increased capacity
     */
    fun augmentCapacity(amount: Double): InventoryComponent {
        return copy(capacityWeight = capacityWeight + amount)
    }

    /**
     * Set carrying capacity (typically from Strength changes)
     *
     * @param newCapacity New capacity in kg
     * @return New InventoryComponent with updated capacity
     */
    fun setCapacity(newCapacity: Double): InventoryComponent {
        return copy(capacityWeight = newCapacity.coerceAtLeast(10.0)) // Minimum 10kg
    }

    /**
     * Get item by instance ID
     *
     * @param instanceId ID to look up
     * @return ItemInstance if found, null otherwise
     */
    fun getItem(instanceId: String): ItemInstance? {
        return items.find { it.id == instanceId }
    }

    /**
     * Find items by template ID
     *
     * @param templateId Template ID to search for
     * @return List of matching instances
     */
    fun findItemsByTemplate(templateId: String): List<ItemInstance> {
        return items.filter { it.templateId == templateId }
    }
}
