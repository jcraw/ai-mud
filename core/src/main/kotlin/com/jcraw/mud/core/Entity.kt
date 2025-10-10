package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Type of item for usage/equipping behavior
 */
@Serializable
enum class ItemType {
    WEAPON,      // Can be equipped for damage bonus
    ARMOR,       // Can be equipped for defense (future)
    CONSUMABLE,  // Can be used once (potions, food)
    MISC         // Generic items
}

@Serializable
sealed class Entity {
    abstract val id: String
    abstract val name: String
    abstract val description: String

    @Serializable
    data class Item(
        override val id: String,
        override val name: String,
        override val description: String,
        val isPickupable: Boolean = true,
        val isUsable: Boolean = false,
        val itemType: ItemType = ItemType.MISC,
        val properties: Map<String, String> = emptyMap(),
        // Weapon properties
        val damageBonus: Int = 0,
        // Consumable properties
        val healAmount: Int = 0,
        val isConsumable: Boolean = false
    ) : Entity()

    @Serializable
    data class NPC(
        override val id: String,
        override val name: String,
        override val description: String,
        val isHostile: Boolean = false,
        val health: Int = 100,
        val maxHealth: Int = 100,
        val properties: Map<String, String> = emptyMap()
    ) : Entity()

    @Serializable
    data class Feature(
        override val id: String,
        override val name: String,
        override val description: String,
        val isInteractable: Boolean = false,
        val properties: Map<String, String> = emptyMap()
    ) : Entity()
}