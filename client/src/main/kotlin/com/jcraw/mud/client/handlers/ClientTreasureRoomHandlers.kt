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

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient

/**
 * Thin facade for GUI treasure room handlers.
 * Public handle* preserved for EngineGameClient / ClientItemTakeHandlers; bodies in ClientTreasure* (MUD-034l).
 */
object ClientTreasureRoomHandlers {

    fun handleTakeTreasure(game: EngineGameClient, itemTarget: String) =
        ClientTreasureTakeHandlers.handleTakeTreasure(game, itemTarget)

    fun handleReturnTreasure(game: EngineGameClient, itemTarget: String) =
        ClientTreasureReturnHandlers.handleReturnTreasure(game, itemTarget)

    fun handleExaminePedestal(game: EngineGameClient, target: String?) =
        ClientTreasureExamineHandlers.handleExaminePedestal(game, target)
}
