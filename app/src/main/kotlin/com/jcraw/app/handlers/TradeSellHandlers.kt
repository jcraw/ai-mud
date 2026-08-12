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

import com.jcraw.mud.reasoning.trade.TradeHandler

/**
 * Sell for [TradeHandlers] facade (MUD-034l pure-move).
 */
internal object TradeSellHandlers {

    fun handleSell(session: TradeSession) {
        val playerItem = session.playerInventory.items.find { instance ->
            val template = session.templates[instance.templateId]
            template != null && (
                template.name.lowercase().contains(session.target.lowercase()) ||
                    instance.templateId.lowercase().contains(session.target.lowercase())
                )
        }
        if (playerItem == null) {
            println("You don't have that item to sell.")
            return
        }
        applySell(session, playerItem.id)
    }

    private fun applySell(session: TradeSession, instanceId: String) {
        val result = session.tradeHandler.sellToMerchant(
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
                println("You sold $qtyText${result.itemName} to ${session.merchant.name} for ${result.price} gold.")
            }
            is TradeHandler.TradeResult.Failure -> {
                println(result.reason)
            }
        }
    }
}
