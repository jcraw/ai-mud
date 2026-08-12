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
import com.jcraw.mud.reasoning.inventory.UseConsumableApply

/**
 * Consumable use for [ClientItemHandlers] facade.
 * Name avoids clash with multipurpose ItemUseHandlers on app.
 */
object ClientItemConsumableHandlers {

    fun handleUse(game: EngineGameClient, target: String) {
        val player = game.worldState.player
        val templates = floorDropTemplates(game.itemRepository, player)
        when (val result = UseConsumableApply.apply(player, target, templates)) {
            is UseConsumableApply.Result.Success -> emitUseSuccess(game, result)
            is UseConsumableApply.Result.Failure -> emitUseFailure(game, result)
        }
    }

    private fun emitUseSuccess(
        game: EngineGameClient,
        result: UseConsumableApply.Result.Success
    ) {
        game.worldState = game.worldState.updatePlayer(result.player)
        if (result.healedAmount > 0) {
            game.emitEvent(
                GameEvent.Narrative(
                    "You consume the ${result.itemName} and restore ${result.healedAmount} HP.\n" +
                        "Current health: ${result.player.health}/${result.player.maxHealth}"
                )
            )
            game.emitEvent(
                GameEvent.StatusUpdate(
                    hp = result.player.health,
                    maxHp = result.player.maxHealth
                )
            )
        } else {
            game.emitEvent(
                GameEvent.Narrative(
                    "You consume the ${result.itemName}, but you're already at full health."
                )
            )
        }
    }

    private fun emitUseFailure(
        game: EngineGameClient,
        result: UseConsumableApply.Result.Failure
    ) {
        val level = if (result.message.startsWith("Try 'equip")) {
            GameEvent.MessageLevel.INFO
        } else if (result.message.contains("not sure", ignoreCase = true)) {
            GameEvent.MessageLevel.INFO
        } else {
            GameEvent.MessageLevel.WARNING
        }
        game.emitEvent(GameEvent.System(result.message, level))
    }
}
