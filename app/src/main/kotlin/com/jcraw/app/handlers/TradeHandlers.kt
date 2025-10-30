package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.core.TradingComponent
import com.jcraw.mud.reasoning.trade.TradeHandler

/**
 * Handlers for trading: buy, sell
 * TODO: Integration with InventoryComponent when available
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
        val room = game.worldState.getCurrentRoom() ?: return

        // TODO: Get TradeHandler instance from game
        // TODO: Find merchant in room with TradingComponent
        // TODO: Get player InventoryComponent
        // TODO: Find item by name in player inventory or merchant stock
        // TODO: Get NPC disposition from SocialComponent
        // TODO: Call TradeHandler.buyFromMerchant or TradeHandler.sellToMerchant
        // TODO: Update world state with new inventory and trading components

        println("Trade system not yet integrated. Coming in next chunk!")
    }

    /**
     * List merchant's stock
     *
     * @param game Current game instance
     * @param merchantTarget Optional merchant name
     */
    fun handleListStock(game: MudGame, merchantTarget: String?) {
        val room = game.worldState.getCurrentRoom() ?: return

        // TODO: Implement when TradingComponent is fully integrated with ECS
        println("Listing stock not yet fully integrated. Coming soon!")
    }
}
