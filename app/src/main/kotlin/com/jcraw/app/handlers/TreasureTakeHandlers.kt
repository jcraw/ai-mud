package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomHandler
import com.jcraw.mud.reasoning.treasureroom.TreasurePedestalOps
import com.jcraw.mud.reasoning.treasureroom.TreasurePedestalSupport as PedestalPures

/**
 * Take-from-pedestal for [TreasureRoomHandlers] facade (MUD-039 pures).
 */
internal object TreasureTakeHandlers {

    fun handleTakeTreasure(game: MudGame, itemTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val room = game.worldState.getTreasureRoom(spaceId)
        if (room == null) {
            println("This isn't a treasure room. Use 'take' for regular items.")
            return
        }
        val templates = TreasurePedestalSupport.buildItemTemplatesMap(game, room)
        val itemTemplateId = PedestalPures.findItemTemplateByName(itemTarget, templates, room)
        if (itemTemplateId == null) {
            println(PedestalPures.availableItemsLine(room, templates))
            return
        }
        applyTake(game, spaceId, room, itemTemplateId, templates)
    }

    private fun applyTake(
        game: MudGame,
        spaceId: String,
        room: TreasureRoomComponent,
        itemTemplateId: String,
        templates: Map<String, com.jcraw.mud.core.ItemTemplate>
    ) {
        val applied = TreasurePedestalOps.takeAndApply(
            game.treasureRoomHandler, game.worldState, spaceId, room, itemTemplateId, templates
        )
        game.worldState = applied.world
        when (val result = applied.result) {
            is TreasureRoomHandler.TreasureRoomResult.Success -> printTakeSuccess(room, itemTemplateId, result)
            is TreasureRoomHandler.TreasureRoomResult.Failure -> println(result.reason)
        }
    }

    private fun printTakeSuccess(
        room: TreasureRoomComponent,
        itemTemplateId: String,
        result: TreasureRoomHandler.TreasureRoomResult.Success
    ) {
        println(
            TreasurePedestalOps.takeFromPedestalLine(
                result.itemName,
                PedestalPures.getPedestalDescription(room, itemTemplateId)
            )
        )
        if (result.treasureRoomComponent.currentlyTakenItem == null) return
        println(
            TreasurePedestalOps.takeBarrierNarrative(
                result.itemName,
                PedestalPures.getBarrierTypeForBiome(room.biomeTheme)
            )
        )
        println(TreasurePedestalOps.takeSwapHint())
    }
}
