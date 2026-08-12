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
import com.jcraw.mud.core.Entity
import com.jcraw.mud.reasoning.QuestAction
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
        when (val result = FloorItemDropApply.apply(
            world = game.worldState,
            player = player,
            spaceId = spaceId,
            target = target,
            templates = templates
        )) {
            is FloorItemDropApply.Result.Success -> {
                game.worldState = result.world
                println("You drop the ${result.itemName}.")
            }
            is FloorItemDropApply.Result.Failure -> {
                println(result.message)
            }
        }
    }

    fun handleGive(game: MudGame, itemTarget: String, npcTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val player = game.worldState.player
        val npc = findNpc(game, spaceId, npcTarget)
        if (npc == null) {
            println("There's no one here by that name.")
            return
        }
        applyGive(game, player, itemTarget, npc)
    }

    private fun findNpc(game: MudGame, spaceId: String, npcTarget: String): Entity.NPC? =
        game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(npcTarget.lowercase()) ||
                    entity.id.lowercase().contains(npcTarget.lowercase())
            }

    private fun applyGive(
        game: MudGame,
        player: com.jcraw.mud.core.PlayerState,
        itemTarget: String,
        npc: Entity.NPC
    ) {
        val templates = floorDropTemplates(game.itemRepository, player)
        when (val result = GiveItemApply.apply(
            world = game.worldState,
            player = player,
            target = itemTarget,
            templates = templates
        )) {
            is GiveItemApply.Result.Success -> {
                game.worldState = result.world
                println("You give the ${result.itemName} to ${npc.name}.")
                game.trackQuests(QuestAction.DeliveredItem(result.instanceId, npc.id))
            }
            is GiveItemApply.Result.Failure -> {
                println(result.message)
            }
        }
    }
}
