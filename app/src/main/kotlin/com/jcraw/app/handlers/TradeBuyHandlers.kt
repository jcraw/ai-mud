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
import com.jcraw.mud.reasoning.trade.TradeHandler

/**
 * Buy + trade dispatch for [TradeHandlers] facade (MUD-034l pure-move).
 */
internal object TradeBuyHandlers {

    fun handleTrade(
        game: MudGame,
        action: String,
        target: String,
        quantity: Int,
        merchantTarget: String?
    ) {
        val session = TradeMerchantSupport.openSession(game, target, quantity, merchantTarget) ?: return
        when (action.lowercase()) {
            "buy" -> handleBuy(session)
            "sell" -> TradeSellHandlers.handleSell(session)
            else -> println("Unknown trade action: $action")
        }
    }

    fun handleBuy(session: TradeSession) {
        val stockItem = session.tradingComponent.stock.find { instance ->
            val template = session.templates[instance.templateId]
            template != null && (
                template.name.lowercase().contains(session.target.lowercase()) ||
                    instance.templateId.lowercase().contains(session.target.lowercase())
                )
        }
        if (stockItem == null) {
            println("${session.merchant.name} doesn't have that item in stock.")
            return
        }
        applyBuy(session, stockItem.id)
    }

    private fun applyBuy(session: TradeSession, instanceId: String) {
        val result = session.tradeHandler.buyFromMerchant(
            playerInventory = session.playerInventory,
            merchantTrading = session.tradingComponent,
            instanceId = instanceId,
            quantity = session.quantity,
            disposition = session.disposition,
            templates = session.templates
        )
        when (result) {
            is TradeHandler.TradeResult.Success -> {
                TradeMerchantSupport.applyTradeSuccess(session, result)
                val qtyText = if (result.quantity > 1) "${result.quantity}x " else ""
                println("You bought $qtyText${result.itemName} from ${session.merchant.name} for ${result.price} gold.")
            }
            is TradeHandler.TradeResult.Failure -> {
                println(result.reason)
            }
        }
    }
}
