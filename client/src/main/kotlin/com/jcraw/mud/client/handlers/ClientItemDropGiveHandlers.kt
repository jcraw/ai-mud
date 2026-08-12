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
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.reasoning.QuestAction
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
        when (val result = FloorItemDropApply.apply(
            world = game.worldState,
            player = player,
            spaceId = spaceId,
            target = target,
            templates = templates
        )) {
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
        val entities = game.worldState.getEntitiesInSpace(spaceId)
        val player = game.worldState.player
        val npc = findNpc(entities, npcTarget)
        if (npc == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }
        applyGive(game, player, itemTarget, npc)
    }

    private fun findNpc(entities: List<Entity>, npcTarget: String): Entity.NPC? =
        entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(npcTarget.lowercase()) ||
                    entity.id.lowercase().contains(npcTarget.lowercase())
            }

    private fun applyGive(
        game: EngineGameClient,
        player: PlayerState,
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
                game.emitEvent(GameEvent.Narrative("You give the ${result.itemName} to ${npc.name}."))
                game.trackQuests(QuestAction.DeliveredItem(result.instanceId, npc.id))
            }
            is GiveItemApply.Result.Failure -> {
                game.emitEvent(GameEvent.System(result.message, GameEvent.MessageLevel.WARNING))
            }
        }
    }
}
