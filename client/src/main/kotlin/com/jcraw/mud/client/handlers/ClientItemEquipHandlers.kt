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
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.reasoning.inventory.EquipItemApply

/**
 * Equip for [ClientItemHandlers] facade.
 */
object ClientItemEquipHandlers {

    fun handleEquip(game: EngineGameClient, target: String) {
        val player = game.worldState.player
        val templates = floorDropTemplates(game.itemRepository, player)
        when (val result = EquipItemApply.apply(player, target, templates)) {
            is EquipItemApply.Result.Success -> {
                game.worldState = game.worldState.updatePlayer(result.player)
                game.emitEvent(GameEvent.Narrative("You equip the ${result.itemName}."))
            }
            is EquipItemApply.Result.Failure -> game.emitEvent(
                GameEvent.System(result.message, GameEvent.MessageLevel.WARNING)
            )
        }
    }
}
