package com.jcraw.mud.reasoning.trade

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository

/**
 * Handles trading logic between player and merchants
 * Supports buy/sell with disposition-based pricing and finite gold constraints
 */
class TradeHandler(
    private val itemRepository: ItemRepository
) {

    /**
     * Result of a trade attempt
     */
    sealed class TradeResult {
        /**
         * Trade succeeded
         * @param playerInventory Updated player inventory
         * @param merchantTrading Updated merchant trading component
         * @param price Final price paid/received
         * @param itemName Name of the item traded
         * @param quantity Number of items traded
         */
        data class Success(
            val playerInventory: InventoryComponent,
            val merchantTrading: TradingComponent,
            val price: Int,
            val itemName: String,
            val quantity: Int,
            val action: String // "bought" or "sold"
        ) : TradeResult()

        /**
         * Trade failed
         * @param reason Human-readable error message
         */
        data class Failure(val reason: String) : TradeResult()
    }

    /**
     * Buy item from merchant
     *
     * @param playerInventory Current player inventory
     * @param merchantTrading Merchant's trading component
     * @param instanceId Item instance ID to buy
     * @param quantity Quantity to buy
     * @param disposition NPC disposition (0-100) for price calculation
     * @param templates All item templates for weight/value lookups
     * @return TradeResult with updated states or failure reason
     */
    fun buyFromMerchant(
        playerInventory: InventoryComponent,
        merchantTrading: TradingComponent,
        instanceId: String,
        quantity: Int,
        disposition: Int,
        templates: Map<String, ItemTemplate>
    ): TradeResult {
        // Find item in merchant stock
        val item = merchantTrading.getItem(instanceId)
            ?: return TradeResult.Failure("Merchant doesn't have that item")

        // Validate quantity
        if (quantity > item.quantity) {
            return TradeResult.Failure("Merchant only has ${item.quantity} of that item")
        }

        // Get template for price calculation
        val templateResult = itemRepository.findTemplateById(item.templateId)
        if (templateResult.isFailure || templateResult.getOrNull() == null) {
            return TradeResult.Failure("Item template not found")
        }
        val template = templateResult.getOrNull()!!

        // Calculate price (disposition affects price: friendly NPCs give discounts)
        val unitPrice = merchantTrading.calculateBuyPrice(template, item, disposition)
        val totalPrice = unitPrice * quantity

        // Check player can afford
        if (playerInventory.gold < totalPrice) {
            return TradeResult.Failure("You need $totalPrice gold (you have ${playerInventory.gold})")
        }

        // Check player can carry
        if (!playerInventory.canAdd(template, quantity, templates)) {
            return TradeResult.Failure("You can't carry that much (weight limit exceeded)")
        }

        // Create item instance for player (split if quantity < item.quantity)
        val playerItem = if (quantity == item.quantity) {
            item
        } else {
            item.copy(id = java.util.UUID.randomUUID().toString(), quantity = quantity)
        }

        // Update player inventory (deduct gold, add item)
        val updatedPlayerInventory = playerInventory
            .removeGold(totalPrice)!!
            .addItem(playerItem)

        // Update merchant (remove from stock, add gold)
        val updatedMerchantTrading = if (quantity == item.quantity) {
            // Remove entire stack
            merchantTrading.removeFromStock(instanceId)!!
        } else {
            // Reduce quantity
            merchantTrading.removeQuantityFromStock(instanceId, quantity)!!
        }
            .addGold(totalPrice)

        return TradeResult.Success(
            playerInventory = updatedPlayerInventory,
            merchantTrading = updatedMerchantTrading,
            price = totalPrice,
            itemName = template.name,
            quantity = quantity,
            action = "bought"
        )
    }

    /**
     * Sell item to merchant
     *
     * @param playerInventory Current player inventory
     * @param merchantTrading Merchant's trading component
     * @param instanceId Item instance ID to sell
     * @param quantity Quantity to sell
     * @param disposition NPC disposition (0-100) for price calculation
     * @param templates All item templates for price lookups
     * @return TradeResult with updated states or failure reason
     */
    fun sellToMerchant(
        playerInventory: InventoryComponent,
        merchantTrading: TradingComponent,
        instanceId: String,
        quantity: Int,
        disposition: Int,
        templates: Map<String, ItemTemplate>
    ): TradeResult {
        // Find item in player inventory
        val item = playerInventory.getItem(instanceId)
            ?: return TradeResult.Failure("You don't have that item")

        // Validate quantity
        if (quantity > item.quantity) {
            return TradeResult.Failure("You only have ${item.quantity} of that item")
        }

        // Check if item is equipped
        if (playerInventory.isEquipped(instanceId)) {
            return TradeResult.Failure("You must unequip that item first")
        }

        // Check if merchant buys this type of item
        if (!merchantTrading.buyAnything) {
            return TradeResult.Failure("This merchant doesn't buy that type of item")
        }

        // Get template for price calculation
        val templateResult = itemRepository.findTemplateById(item.templateId)
        if (templateResult.isFailure || templateResult.getOrNull() == null) {
            return TradeResult.Failure("Item template not found")
        }
        val template = templateResult.getOrNull()!!

        // Calculate price (disposition affects price)
        val unitPrice = merchantTrading.calculateSellPrice(template, item, disposition)
        val totalPrice = unitPrice * quantity

        // Check merchant can afford
        if (!merchantTrading.canAfford(totalPrice)) {
            return TradeResult.Failure("Merchant doesn't have enough gold (only ${merchantTrading.merchantGold} gold available)")
        }

        // Create item instance for merchant (split if quantity < item.quantity)
        val merchantItem = if (quantity == item.quantity) {
            item
        } else {
            item.copy(id = java.util.UUID.randomUUID().toString(), quantity = quantity)
        }

        // Update player inventory (add gold, remove item)
        val updatedPlayerInventory = if (quantity == item.quantity) {
            playerInventory.removeItem(instanceId)!!
        } else {
            playerInventory.removeQuantity(instanceId, quantity)!!
        }
            .addGold(totalPrice)

        // Update merchant (add to stock, remove gold)
        val updatedMerchantTrading = merchantTrading
            .removeGold(totalPrice)!!
            .addToStock(merchantItem)

        return TradeResult.Success(
            playerInventory = updatedPlayerInventory,
            merchantTrading = updatedMerchantTrading,
            price = totalPrice,
            itemName = template.name,
            quantity = quantity,
            action = "sold"
        )
    }

}
