package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomHandler
import com.jcraw.mud.reasoning.treasureroom.TreasurePedestalOps
import com.jcraw.mud.reasoning.treasureroom.TreasurePedestalSupport as PedestalPures

/**
 * Return-to-pedestal for [ClientTreasureRoomHandlers] facade (MUD-039 pures).
 */
internal object ClientTreasureReturnHandlers {

    fun handleReturnTreasure(game: EngineGameClient, itemTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val room = game.worldState.getTreasureRoom(spaceId)
        if (room == null) {
            game.emitEvent(GameEvent.System("This isn't a treasure room.", GameEvent.MessageLevel.WARNING))
            return
        }
        val templates = ClientTreasurePedestalSupport.buildItemTemplatesMap(game, room)
        val item = PedestalPures.findInventoryItem(
            game.worldState.player.inventoryComponent.items, templates, itemTarget
        )
        if (item == null) {
            game.emitEvent(
                GameEvent.System("You don't have that item in your inventory.", GameEvent.MessageLevel.WARNING)
            )
            return
        }
        applyReturn(game, spaceId, room, item, templates)
    }

    private fun applyReturn(
        game: EngineGameClient,
        spaceId: String,
        room: TreasureRoomComponent,
        item: ItemInstance,
        templates: Map<String, ItemTemplate>
    ) {
        val applied = TreasurePedestalOps.returnAndApply(
            game.treasureRoomHandler, game.worldState, spaceId, room, item.id, templates
        )
        game.worldState = applied.world
        when (val result = applied.result) {
            is TreasureRoomHandler.TreasureRoomResult.Success -> emitReturn(game, spaceId, room, item, result)
            is TreasureRoomHandler.TreasureRoomResult.Failure ->
                game.emitEvent(GameEvent.System(result.reason, GameEvent.MessageLevel.WARNING))
        }
    }

    private fun emitReturn(
        game: EngineGameClient,
        spaceId: String,
        room: TreasureRoomComponent,
        item: ItemInstance,
        result: TreasureRoomHandler.TreasureRoomResult.Success
    ) {
        game.emitEvent(
            GameEvent.Narrative(
                TreasurePedestalOps.returnToPedestalLine(
                    result.itemName,
                    PedestalPures.getPedestalDescription(room, item.templateId)
                )
            )
        )
        game.emitEvent(
            GameEvent.Narrative(
                TreasurePedestalOps.returnBarrierNarrative(
                    PedestalPures.getBarrierTypeForBiome(room.biomeTheme)
                )
            )
        )
        ClientTreasurePedestalSupport.emitStatusUpdate(game, spaceId)
    }
}
