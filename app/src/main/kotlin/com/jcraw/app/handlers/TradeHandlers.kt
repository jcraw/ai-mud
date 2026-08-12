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

/**
 * Thin facade for trading handlers.
 * Public handleTrade / handleListStock preserved for MudGameEngine; bodies in Trade* extracts (MUD-034l).
 */
object TradeHandlers {

    fun handleTrade(
        game: MudGame,
        action: String,
        target: String,
        quantity: Int,
        merchantTarget: String?
    ) = TradeBuyHandlers.handleTrade(game, action, target, quantity, merchantTarget)

    fun handleListStock(game: MudGame, merchantTarget: String?) =
        TradeListStockHandlers.handleListStock(game, merchantTarget)
}
