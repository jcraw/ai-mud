@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList"
)

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.TradingComponent

/**
 * List merchant stock for [TradeHandlers] facade (MUD-034l pure-move).
 */
internal object TradeListStockHandlers {

    fun handleListStock(game: MudGame, merchantTarget: String?) {
        val spaceId = game.worldState.player.currentRoomId
        val merchant = TradeMerchantSupport.findMerchant(game, spaceId, merchantTarget)
        if (merchant == null) {
            println("There's no merchant here.")
            return
        }
        val tradingComponent = merchant.getComponent<TradingComponent>(ComponentType.TRADING)
        if (tradingComponent == null) {
            println("${merchant.name} doesn't appear to be a merchant.")
            return
        }
        printStock(game, merchant, tradingComponent)
    }

    private fun printStock(
        game: MudGame,
        merchant: Entity.NPC,
        tradingComponent: TradingComponent
    ) {
        val playerInventory = game.worldState.player.inventoryComponent
        val disposition = merchant.getDisposition()
        println("\n${merchant.name}'s Stock:")
        println("=" .repeat(40))
        if (tradingComponent.stock.isEmpty()) {
            println("  (no items in stock)")
        } else {
            printStockLines(game, tradingComponent, disposition)
        }
        println("\nMerchant has ${tradingComponent.merchantGold} gold available.")
        if (playerInventory != null) {
            println("You have ${playerInventory.gold} gold.")
        }
    }

    private fun printStockLines(
        game: MudGame,
        tradingComponent: TradingComponent,
        disposition: Int
    ) {
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
}
