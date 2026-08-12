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
import com.jcraw.mud.reasoning.inventory.UseConsumableApply

/**
 * Consumable use (heal/consume) for [ItemHandlers] facade.
 * Distinct from multipurpose [handleUseItem] in ItemUseHandlers.
 */
object ItemConsumableHandlers {

    fun handleUse(game: MudGame, target: String) {
        val player = game.worldState.player
        val templates = floorDropTemplates(game.itemRepository, player)
        when (val result = UseConsumableApply.apply(player, target, templates)) {
            is UseConsumableApply.Result.Success -> {
                game.worldState = game.worldState.updatePlayer(result.player)
                if (result.healedAmount > 0) {
                    println("\nYou consume the ${result.itemName} and restore ${result.healedAmount} HP.")
                    println("Current health: ${result.player.health}/${result.player.maxHealth}")
                } else {
                    println("\nYou consume the ${result.itemName}, but you're already at full health.")
                }
            }
            is UseConsumableApply.Result.Failure -> {
                println(result.message)
            }
        }
    }
}
