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
 * Thin facade for treasure room handlers.
 * Public handle* preserved for MudGameEngine / ItemTakeHandlers; bodies in Treasure* extracts (MUD-034l).
 */
object TreasureRoomHandlers {

    fun handleTakeTreasure(game: MudGame, itemTarget: String) =
        TreasureTakeHandlers.handleTakeTreasure(game, itemTarget)

    fun handleReturnTreasure(game: MudGame, itemTarget: String) =
        TreasureReturnHandlers.handleReturnTreasure(game, itemTarget)

    fun handleExaminePedestal(game: MudGame, target: String?) =
        TreasureExamineHandlers.handleExaminePedestal(game, target)
}
