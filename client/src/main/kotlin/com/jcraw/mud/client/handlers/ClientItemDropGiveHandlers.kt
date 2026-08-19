package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.inventory.EntityNameMatch
import com.jcraw.mud.reasoning.inventory.FloorItemDropApply
import com.jcraw.mud.reasoning.inventory.GiveItemApply

/**
 * Drop / give for [ClientItemHandlers] facade.
 */
object ClientItemDropGiveHandlers {

    fun handleDrop(game: EngineGameClient, target: String) {
        val spaceId = game.worldState.player.currentRoomId
        val player = game.worldState.player
        val templates = floorDropTemplates(game.itemRepository, player)
        when (val result = FloorItemDropApply.apply(game.worldState, player, spaceId, target, templates)) {
            is FloorItemDropApply.Result.Success -> {
                game.worldState = result.world
                game.emitEvent(GameEvent.Narrative("You drop the ${result.itemName}."))
            }
            is FloorItemDropApply.Result.Failure -> {
                game.emitEvent(GameEvent.System(result.message, GameEvent.MessageLevel.WARNING))
            }
        }
    }

    fun handleGive(game: EngineGameClient, itemTarget: String, npcTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val npc = EntityNameMatch.findNpc(game.worldState.getEntitiesInSpace(spaceId), npcTarget)
        if (npc == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }
        applyGive(game, game.worldState.player, itemTarget, npc)
    }

    private fun applyGive(
        game: EngineGameClient,
        player: PlayerState,
        itemTarget: String,
        npc: Entity.NPC
    ) {
        val templates = floorDropTemplates(game.itemRepository, player)
        when (val result = GiveItemApply.apply(game.worldState, player, itemTarget, templates)) {
            is GiveItemApply.Result.Success -> {
                game.worldState = result.world
                game.emitEvent(GameEvent.Narrative("You give the ${result.itemName} to ${npc.name}."))
                game.trackQuests(QuestAction.DeliveredItem(result.instanceId, npc.id))
            }
            is GiveItemApply.Result.Failure -> {
                game.emitEvent(GameEvent.System(result.message, GameEvent.MessageLevel.WARNING))
            }
        }
    }
}
