package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.inventory.EntityNameMatch
import com.jcraw.mud.reasoning.inventory.FloorItemDropApply
import com.jcraw.mud.reasoning.inventory.GiveItemApply

/**
 * Drop / give for [ItemHandlers] facade.
 */
object ItemDropGiveHandlers {

    fun handleDrop(game: MudGame, target: String) {
        val spaceId = game.worldState.player.currentRoomId
        val player = game.worldState.player
        val templates = floorDropTemplates(game.itemRepository, player)
        when (val result = FloorItemDropApply.apply(game.worldState, player, spaceId, target, templates)) {
            is FloorItemDropApply.Result.Success -> {
                game.worldState = result.world
                println("You drop the ${result.itemName}.")
            }
            is FloorItemDropApply.Result.Failure -> println(result.message)
        }
    }

    fun handleGive(game: MudGame, itemTarget: String, npcTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val npc = EntityNameMatch.findNpc(game.worldState.getEntitiesInSpace(spaceId), npcTarget)
        if (npc == null) {
            println("There's no one here by that name.")
            return
        }
        applyGive(game, game.worldState.player, itemTarget, npc)
    }

    private fun applyGive(game: MudGame, player: PlayerState, itemTarget: String, npc: Entity.NPC) {
        val templates = floorDropTemplates(game.itemRepository, player)
        when (val result = GiveItemApply.apply(game.worldState, player, itemTarget, templates)) {
            is GiveItemApply.Result.Success -> {
                game.worldState = result.world
                println("You give the ${result.itemName} to ${npc.name}.")
                game.trackQuests(QuestAction.DeliveredItem(result.instanceId, npc.id))
            }
            is GiveItemApply.Result.Failure -> println(result.message)
        }
    }
}
