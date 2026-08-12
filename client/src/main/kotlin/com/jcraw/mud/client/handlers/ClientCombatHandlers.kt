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
 * Thin facade for client combat handlers.
 * Public handleAttack preserved; body in ClientCombatAttack* extracts (MUD-034k).
 */
object ClientCombatHandlers {

    fun handleAttack(game: EngineGameClient, target: String?) =
        ClientCombatAttackHandlers.handleAttack(game, target)
}
