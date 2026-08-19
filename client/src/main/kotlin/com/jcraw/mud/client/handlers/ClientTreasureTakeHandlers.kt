package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomHandler
import com.jcraw.mud.reasoning.treasureroom.TreasurePedestalOps
import com.jcraw.mud.reasoning.treasureroom.TreasurePedestalSupport as PedestalPures

/**
 * Take-from-pedestal for [ClientTreasureRoomHandlers] facade (MUD-039 pures).
 */
internal object ClientTreasureTakeHandlers {

    fun handleTakeTreasure(game: EngineGameClient, itemTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val room = game.worldState.getTreasureRoom(spaceId)
        if (room == null) {
            warn(game, "This isn't a treasure room. Use 'take' for regular items.")
            return
        }
        val templates = ClientTreasurePedestalSupport.buildItemTemplatesMap(game, room)
        val itemTemplateId = PedestalPures.findItemTemplateByName(itemTarget, templates, room)
        if (itemTemplateId == null) {
            warn(game, PedestalPures.availableItemsLine(room, templates))
            return
        }
        applyTake(game, spaceId, room, itemTemplateId, templates)
    }

    private fun warn(game: EngineGameClient, message: String) {
        game.emitEvent(GameEvent.System(message, GameEvent.MessageLevel.WARNING))
    }

    private fun applyTake(
        game: EngineGameClient,
        spaceId: String,
        room: TreasureRoomComponent,
        itemTemplateId: String,
        templates: Map<String, ItemTemplate>
    ) {
        val applied = TreasurePedestalOps.takeAndApply(
            game.treasureRoomHandler, game.worldState, spaceId, room, itemTemplateId, templates
        )
        game.worldState = applied.world
        when (val result = applied.result) {
            is TreasureRoomHandler.TreasureRoomResult.Success ->
                emitTakeSuccess(game, spaceId, room, itemTemplateId, result)
            is TreasureRoomHandler.TreasureRoomResult.Failure -> warn(game, result.reason)
        }
    }

    private fun emitTakeSuccess(
        game: EngineGameClient,
        spaceId: String,
        room: TreasureRoomComponent,
        itemTemplateId: String,
        result: TreasureRoomHandler.TreasureRoomResult.Success
    ) {
        game.emitEvent(
            GameEvent.Narrative(
                TreasurePedestalOps.takeFromPedestalLine(
                    result.itemName,
                    PedestalPures.getPedestalDescription(room, itemTemplateId)
                )
            )
        )
        emitBarriers(game, room, result)
        ClientTreasurePedestalSupport.emitStatusUpdate(game, spaceId)
    }

    private fun emitBarriers(
        game: EngineGameClient,
        room: TreasureRoomComponent,
        result: TreasureRoomHandler.TreasureRoomResult.Success
    ) {
        if (result.treasureRoomComponent.currentlyTakenItem == null) return
        game.emitEvent(
            GameEvent.Narrative(
                TreasurePedestalOps.takeBarrierNarrative(
                    result.itemName,
                    PedestalPures.getBarrierTypeForBiome(room.biomeTheme)
                )
            )
        )
        game.emitEvent(GameEvent.Narrative(TreasurePedestalOps.takeSwapHint()))
    }
}
