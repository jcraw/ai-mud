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
 * Thin facade for combat handlers.
 * Public handleAttack preserved for MudGameEngine dispatch; body in CombatAttack* extracts (MUD-034k).
 */
object CombatHandlers {

    /**
     * Handle player attack action
     *
     * @param game MudGame instance with world state and systems
     * @param target Target identifier (NPC name or ID)
     */
    fun handleAttack(game: MudGame, target: String?) =
        CombatAttackHandlers.handleAttack(game, target)
}
