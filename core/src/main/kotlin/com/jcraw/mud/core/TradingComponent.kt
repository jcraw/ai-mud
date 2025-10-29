package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Trading component for merchant NPCs
 * Manages stock, merchant gold, and pricing
 *
 * Design principles:
 * - Immutable: All methods return new instances
 * - Finite gold: Merchants have limited gold for buying from players
 * - Finite stock: Items deplete when bought, replenish when sold
 * - Disposition-based pricing: Friendly NPCs give discounts
 *
 * @param merchantGold Gold available for buying items from players
 * @param stock List of items available for purchase
 * @param buyAnything If true, merchant will buy any item from player
 * @param priceModBase Base price modifier (1.0 = normal, 0.8 = 20% discount)
 */
@Serializable
data class TradingComponent(
    val merchantGold: Int = 500,
    val stock: List<ItemInstance> = emptyList(),
    val buyAnything: Boolean = true,
    val priceModBase: Double = 1.0,
    override val componentType: ComponentType = ComponentType.TRADING
) : Component {

    /**
     * Calculate sell price (player selling to merchant)
     * Incorporates base price, disposition modifier, and instance quality
     *
     * @param template ItemTemplate for base value
     * @param instance ItemInstance for quality
     * @param disposition NPC disposition (0-100)
     * @return Price in gold
     */
    fun calculateSellPrice(template: ItemTemplate, instance: ItemInstance, disposition: Int): Int {
        val baseValue = template.getValue()
        val dispositionMod = 1.0 + (disposition - 50) / 100.0
        val qualityMod = instance.quality / 10.0
        val totalMod = priceModBase * dispositionMod * qualityMod
        return (baseValue * totalMod).toInt().coerceAtLeast(1)
    }

    /**
     * Calculate buy price (player buying from merchant)
     * Friendly NPCs give better prices
     *
     * @param template ItemTemplate for base value
     * @param instance ItemInstance for quality
     * @param disposition NPC disposition (0-100)
     * @return Price in gold
     */
    fun calculateBuyPrice(template: ItemTemplate, instance: ItemInstance, disposition: Int): Int {
        val baseValue = template.getValue()
        val dispositionMod = 1.0 + (disposition - 50) / 100.0
        val qualityMod = instance.quality / 10.0
        val totalMod = priceModBase * dispositionMod * qualityMod
        return (baseValue * totalMod).toInt().coerceAtLeast(1)
    }

    /**
     * Add item to merchant stock
     * Used when player sells to merchant
     *
     * @param instance ItemInstance to add
     * @return New TradingComponent with item in stock
     */
    fun addToStock(instance: ItemInstance): TradingComponent {
        // Check if we can stack with existing item
        val existingIndex = stock.indexOfFirst {
            it.templateId == instance.templateId &&
            it.quality == instance.quality &&
            it.charges == instance.charges
        }

        val newStock = if (existingIndex >= 0) {
            // Stack with existing
            val existing = stock[existingIndex]
            stock.toMutableList().apply {
                set(existingIndex, existing.addQuantity(instance.quantity))
            }
        } else {
            // Add new item
            stock + instance
        }

        return copy(stock = newStock)
    }

    /**
     * Remove item from merchant stock by instance ID
     * Used when player buys from merchant
     *
     * @param instanceId ID of instance to remove
     * @return New TradingComponent with item removed, or null if item not in stock
     */
    fun removeFromStock(instanceId: String): TradingComponent? {
        val item = stock.find { it.id == instanceId } ?: return null
        val newStock = stock.filterNot { it.id == instanceId }
        return copy(stock = newStock)
    }

    /**
     * Remove quantity from item stack in stock
     *
     * @param instanceId ID of instance to reduce
     * @param quantity Amount to remove
     * @return New TradingComponent with reduced quantity, or null if item not found
     */
    fun removeQuantityFromStock(instanceId: String, quantity: Int): TradingComponent? {
        val itemIndex = stock.indexOfFirst { it.id == instanceId }
        if (itemIndex < 0) return null

        val item = stock[itemIndex]
        val newItem = item.reduceQuantity(quantity)

        val newStock = if (newItem == null) {
            // Item fully consumed
            stock.filterNot { it.id == instanceId }
        } else {
            stock.toMutableList().apply { set(itemIndex, newItem) }
        }

        return copy(stock = newStock)
    }

    /**
     * Add gold to merchant
     * Used when player buys from merchant
     *
     * @param amount Amount of gold to add
     * @return New TradingComponent with increased gold
     */
    fun addGold(amount: Int): TradingComponent {
        return copy(merchantGold = (merchantGold + amount).coerceAtLeast(0))
    }

    /**
     * Remove gold from merchant
     * Used when merchant buys from player
     *
     * @param amount Amount of gold to remove
     * @return New TradingComponent with reduced gold, or null if insufficient gold
     */
    fun removeGold(amount: Int): TradingComponent? {
        if (merchantGold < amount) return null
        return copy(merchantGold = merchantGold - amount)
    }

    /**
     * Get item from stock by instance ID
     *
     * @param instanceId ID to look up
     * @return ItemInstance if found, null otherwise
     */
    fun getItem(instanceId: String): ItemInstance? {
        return stock.find { it.id == instanceId }
    }

    /**
     * Find items in stock by template ID
     *
     * @param templateId Template ID to search for
     * @return List of matching instances
     */
    fun findItemsByTemplate(templateId: String): List<ItemInstance> {
        return stock.filter { it.templateId == templateId }
    }

    /**
     * Check if merchant can afford to buy item
     *
     * @param price Price in gold
     * @return True if merchant has sufficient gold
     */
    fun canAfford(price: Int): Boolean {
        return merchantGold >= price
    }
}
