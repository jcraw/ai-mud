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
import com.jcraw.mud.reasoning.inventory.EquipItemApply

/**
 * Equip for [ItemHandlers] facade.
 */
object ItemEquipHandlers {

    fun handleEquip(game: MudGame, target: String) {
        val player = game.worldState.player
        val templates = floorDropTemplates(game.itemRepository, player)
        when (val result = EquipItemApply.apply(player, target, templates)) {
            is EquipItemApply.Result.Success -> {
                game.worldState = game.worldState.updatePlayer(result.player)
                println("You equip the ${result.itemName}.")
            }
            is EquipItemApply.Result.Failure -> println(result.message)
        }
    }
}
