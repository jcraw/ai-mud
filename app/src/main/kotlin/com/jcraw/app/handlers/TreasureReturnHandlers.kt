package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomHandler
import com.jcraw.mud.reasoning.treasureroom.TreasurePedestalOps
import com.jcraw.mud.reasoning.treasureroom.TreasurePedestalSupport as PedestalPures

/**
 * Return-to-pedestal for [TreasureRoomHandlers] facade (MUD-039 pures).
 */
internal object TreasureReturnHandlers {

    fun handleReturnTreasure(game: MudGame, itemTarget: String) {
        val spaceId = game.worldState.player.currentRoomId
        val room = game.worldState.getTreasureRoom(spaceId)
        if (room == null) {
            println("This isn't a treasure room.")
            return
        }
        val templates = TreasurePedestalSupport.buildItemTemplatesMap(game, room)
        val item = PedestalPures.findInventoryItem(
            game.worldState.player.inventoryComponent.items, templates, itemTarget
        )
        if (item == null) {
            println("You don't have that item in your inventory.")
            return
        }
        applyReturn(game, spaceId, room, item, templates)
    }

    private fun applyReturn(
        game: MudGame,
        spaceId: String,
        room: TreasureRoomComponent,
        item: ItemInstance,
        templates: Map<String, com.jcraw.mud.core.ItemTemplate>
    ) {
        val applied = TreasurePedestalOps.returnAndApply(
            game.treasureRoomHandler, game.worldState, spaceId, room, item.id, templates
        )
        game.worldState = applied.world
        when (val result = applied.result) {
            is TreasureRoomHandler.TreasureRoomResult.Success -> printReturn(room, item, result)
            is TreasureRoomHandler.TreasureRoomResult.Failure -> println(result.reason)
        }
    }

    private fun printReturn(
        room: TreasureRoomComponent,
        item: ItemInstance,
        result: TreasureRoomHandler.TreasureRoomResult.Success
    ) {
        println(
            TreasurePedestalOps.returnToPedestalLine(
                result.itemName,
                PedestalPures.getPedestalDescription(room, item.templateId)
            )
        )
        println(
            TreasurePedestalOps.returnBarrierNarrative(
                PedestalPures.getBarrierTypeForBiome(room.biomeTheme)
            )
        )
    }
}
