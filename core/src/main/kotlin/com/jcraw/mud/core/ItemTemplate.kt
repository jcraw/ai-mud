package com.jcraw.mud.core

import kotlinx.serialization.Serializable

const val GOLD_TEMPLATE_ID: String = "gold_coin"


/**
 * Template definition for items loaded from database
 * Templates are shared definitions referenced by ItemInstances
 *
 * Properties map contains flexible key-value pairs for item attributes:
 * - weight: Item weight in kg (e.g., "2.5")
 * - damage: Base damage for weapons (e.g., "10")
 * - defense: Defense bonus for armor (e.g., "5")
 * - value: Base merchant value in gold (e.g., "50")
 * - healing: HP restoration for consumables (e.g., "20")
 * - capacity: Carrying capacity bonus for containers (e.g., "10")
 * - skill_bonus_<skill>: Bonus to specific skill (e.g., "sword_fighting_bonus" = "2")
 */
@Serializable
data class ItemTemplate(
    val id: String,
    val name: String,
    val type: ItemType,
    val tags: List<String> = emptyList(), // e.g., ["sharp", "metal", "flammable"]
    val properties: Map<String, String> = emptyMap(),
    val rarity: Rarity = Rarity.COMMON,
    val description: String,
    val equipSlot: EquipSlot? = null // null for non-equippable items
) {
    /**
     * Get property as Double, or default if not present or invalid
     */
    fun getPropertyDouble(key: String, default: Double = 0.0): Double {
        return properties[key]?.toDoubleOrNull() ?: default
    }

    /**
     * Get property as Int, or default if not present or invalid
     */
    fun getPropertyInt(key: String, default: Int = 0): Int {
        return properties[key]?.toIntOrNull() ?: default
    }

    /**
     * Get item weight in kg
     */
    fun getWeight(): Double = getPropertyDouble("weight", 0.0)

    /**
     * Check if item has specific tag
     */
    fun hasTag(tag: String): Boolean = tags.contains(tag)
}
