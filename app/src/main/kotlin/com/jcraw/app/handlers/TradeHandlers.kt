package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.TradingComponent
import com.jcraw.mud.reasoning.trade.TradeHandler

/**
 * Handlers for trading: buy, sell, list stock
 * Integrates TradeHandler (reasoning) with game state management
 */
object TradeHandlers {

    /**
     * Handle buy/sell trade intent
     *
     * @param game Current game instance
     * @param action "buy" or "sell"
     * @param target Item name to trade
     * @param quantity Quantity to trade
     * @param merchantTarget Optional merchant name
     */
    fun handleTrade(
        game: MudGame,
        action: String,
        target: String,
        quantity: Int,
        merchantTarget: String?
    ) {
        val spaceId = game.worldState.player.currentRoomId

        // Find merchant in space
        val merchant = findMerchant(game, spaceId, merchantTarget)
        if (merchant == null) {
            println("There's no merchant here to trade with.")
            return
        }

        // Get merchant's TradingComponent
        val tradingComponent = merchant.getComponent<TradingComponent>(ComponentType.TRADING)
        if (tradingComponent == null) {
            println("${merchant.name} doesn't appear to be a merchant.")
            return
        }

        // Get player inventory
        val playerInventory = game.worldState.player.inventoryComponent
        if (playerInventory == null) {
            println("You don't have an inventory to trade with.")
            return
        }

        // Build template map for lookups
        val templates = buildTemplateMap(game, playerInventory, tradingComponent)

        // Get merchant disposition
        val disposition = merchant.getDisposition()

        // Create trade handler
        val tradeHandler = TradeHandler(game.itemRepository)

        when (action.lowercase()) {
            "buy" -> handleBuy(game, tradeHandler, merchant, tradingComponent, playerInventory, target, quantity, disposition, templates, spaceId)
            "sell" -> handleSell(game, tradeHandler, merchant, tradingComponent, playerInventory, target, quantity, disposition, templates, spaceId)
            else -> println("Unknown trade action: $action")
        }
    }

    private fun handleBuy(
        game: MudGame,
        tradeHandler: TradeHandler,
        merchant: Entity.NPC,
        tradingComponent: TradingComponent,
        playerInventory: com.jcraw.mud.core.InventoryComponent,
        target: String,
        quantity: Int,
        disposition: Int,
        templates: Map<String, ItemTemplate>,
        spaceId: String
    ) {
        // Find item in merchant stock by name
        val stockItem = tradingComponent.stock.find { instance ->
            val template = templates[instance.templateId]
            template != null && (
                template.name.lowercase().contains(target.lowercase()) ||
                instance.templateId.lowercase().contains(target.lowercase())
            )
        }

        if (stockItem == null) {
            println("${merchant.name} doesn't have that item in stock.")
            return
        }

        val result = tradeHandler.buyFromMerchant(
            playerInventory = playerInventory,
            merchantTrading = tradingComponent,
            instanceId = stockItem.id,
            quantity = quantity,
            disposition = disposition,
            templates = templates
        )

        when (result) {
            is TradeHandler.TradeResult.Success -> {
                // Update player inventory
                val updatedPlayer = game.worldState.player.copy(inventoryComponent = result.playerInventory)

                // Update merchant's trading component
                val updatedMerchant = merchant.withComponent(result.merchantTrading) as Entity.NPC

                // Update world state
                val newState = game.worldState
                    .updatePlayer(updatedPlayer)
                    .replaceEntityInSpace(spaceId, merchant.id, updatedMerchant)

                game.worldState = newState

                val qtyText = if (result.quantity > 1) "${result.quantity}x " else ""
                println("You bought $qtyText${result.itemName} from ${merchant.name} for ${result.price} gold.")
            }
            is TradeHandler.TradeResult.Failure -> {
                println(result.reason)
            }
        }
    }

    private fun handleSell(
        game: MudGame,
        tradeHandler: TradeHandler,
        merchant: Entity.NPC,
        tradingComponent: TradingComponent,
        playerInventory: com.jcraw.mud.core.InventoryComponent,
        target: String,
        quantity: Int,
        disposition: Int,
        templates: Map<String, ItemTemplate>,
        spaceId: String
    ) {
        // Find item in player inventory by name
        val playerItem = playerInventory.items.find { instance ->
            val template = templates[instance.templateId]
            template != null && (
                template.name.lowercase().contains(target.lowercase()) ||
                instance.templateId.lowercase().contains(target.lowercase())
            )
        }

        if (playerItem == null) {
            println("You don't have that item to sell.")
            return
        }

        val result = tradeHandler.sellToMerchant(
            playerInventory = playerInventory,
            merchantTrading = tradingComponent,
            instanceId = playerItem.id,
            quantity = quantity,
            disposition = disposition,
            templates = templates
        )

        when (result) {
            is TradeHandler.TradeResult.Success -> {
                // Update player inventory
                val updatedPlayer = game.worldState.player.copy(inventoryComponent = result.playerInventory)

                // Update merchant's trading component
                val updatedMerchant = merchant.withComponent(result.merchantTrading) as Entity.NPC

                // Update world state
                val newState = game.worldState
                    .updatePlayer(updatedPlayer)
                    .replaceEntityInSpace(spaceId, merchant.id, updatedMerchant)

                game.worldState = newState

                val qtyText = if (result.quantity > 1) "${result.quantity}x " else ""
                println("You sold $qtyText${result.itemName} to ${merchant.name} for ${result.price} gold.")
            }
            is TradeHandler.TradeResult.Failure -> {
                println(result.reason)
            }
        }
    }

    /**
     * List merchant's stock
     *
     * @param game Current game instance
     * @param merchantTarget Optional merchant name
     */
    fun handleListStock(game: MudGame, merchantTarget: String?) {
        val spaceId = game.worldState.player.currentRoomId

        // Find merchant in space
        val merchant = findMerchant(game, spaceId, merchantTarget)
        if (merchant == null) {
            println("There's no merchant here.")
            return
        }

        // Get merchant's TradingComponent
        val tradingComponent = merchant.getComponent<TradingComponent>(ComponentType.TRADING)
        if (tradingComponent == null) {
            println("${merchant.name} doesn't appear to be a merchant.")
            return
        }

        // Get player inventory for disposition-based pricing
        val playerInventory = game.worldState.player.inventoryComponent
        val disposition = merchant.getDisposition()

        println("\n${merchant.name}'s Stock:")
        println("=" .repeat(40))

        if (tradingComponent.stock.isEmpty()) {
            println("  (no items in stock)")
        } else {
            tradingComponent.stock.forEach { instance ->
                val templateResult = game.itemRepository.findTemplateById(instance.templateId)
                templateResult.getOrNull()?.let { template ->
                    val price = tradingComponent.calculateBuyPrice(template, instance, disposition)
                    val qtyText = if (instance.quantity > 1) " x${instance.quantity}" else ""
                    val qualityText = if (instance.quality != 5) " [quality ${instance.quality}/10]" else ""
                    println("  - ${template.name}$qtyText$qualityText - $price gold")
                }
            }
        }

        println("\nMerchant has ${tradingComponent.merchantGold} gold available.")
        if (playerInventory != null) {
            println("You have ${playerInventory.gold} gold.")
        }
    }

    /**
     * Find a merchant in the current space
     */
    private fun findMerchant(game: MudGame, spaceId: String, merchantTarget: String?): Entity.NPC? {
        val npcs = game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.NPC>()
            .filter { it.hasComponent(ComponentType.TRADING) }

        return if (merchantTarget != null) {
            npcs.find { npc ->
                npc.name.lowercase().contains(merchantTarget.lowercase()) ||
                npc.id.lowercase().contains(merchantTarget.lowercase())
            }
        } else {
            npcs.firstOrNull()
        }
    }

    /**
     * Build a map of item templates for lookups
     */
    private fun buildTemplateMap(
        game: MudGame,
        playerInventory: com.jcraw.mud.core.InventoryComponent,
        tradingComponent: TradingComponent
    ): Map<String, ItemTemplate> {
        val templates = mutableMapOf<String, ItemTemplate>()

        // Get templates for player items
        playerInventory.items.forEach { instance ->
            val result = game.itemRepository.findTemplateById(instance.templateId)
            result.getOrNull()?.let { templates[it.id] = it }
        }

        // Get templates for merchant stock
        tradingComponent.stock.forEach { instance ->
            val result = game.itemRepository.findTemplateById(instance.templateId)
            result.getOrNull()?.let { templates[it.id] = it }
        }

        return templates
    }
}
