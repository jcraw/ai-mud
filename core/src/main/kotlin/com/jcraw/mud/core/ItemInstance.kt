package com.jcraw.mud.core

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Instance of an item referencing a template
 * Lightweight class since thousands of instances may exist
 *
 * @param id Unique instance identifier
 * @param templateId Reference to ItemTemplate id
 * @param quality Item quality from 1-10 (affects bonuses, crafted items have quality = skill/10)
 * @param charges Current charges for consumables/tools (null for non-chargeable items)
 * @param quantity Stack quantity for stackable items (resources, consumables)
 */
@Serializable
data class ItemInstance(
    val id: String = UUID.randomUUID().toString(),
    val templateId: String,
    val quality: Int = 5, // 1-10, default average
    val charges: Int? = null,
    val quantity: Int = 1
) {
    init {
        require(quality in 1..10) { "Quality must be between 1 and 10" }
        require(quantity >= 1) { "Quantity must be at least 1" }
        charges?.let { require(it >= 0) { "Charges cannot be negative" } }
    }

    /**
     * Get quality multiplier for stat bonuses
     * Quality 5 = 1.0x (baseline)
     * Quality 10 = 1.5x
     * Quality 1 = 0.6x
     */
    fun getQualityMultiplier(): Double = 0.5 + (quality * 0.1)

    /**
     * Create copy with reduced charges (consumable use)
     * Returns null if item should be destroyed (charges <= 0)
     */
    fun reduceCharge(): ItemInstance? {
        if (charges == null) return this // Not chargeable
        val newCharges = charges - 1
        return if (newCharges <= 0) null else copy(charges = newCharges)
    }

    /**
     * Create copy with reduced quantity (stack consumption)
     * Returns null if quantity would be <= 0
     */
    fun reduceQuantity(amount: Int = 1): ItemInstance? {
        val newQuantity = quantity - amount
        return if (newQuantity <= 0) null else copy(quantity = newQuantity)
    }

    /**
     * Create copy with increased quantity (stacking)
     */
    fun addQuantity(amount: Int = 1): ItemInstance {
        return copy(quantity = quantity + amount)
    }
}
