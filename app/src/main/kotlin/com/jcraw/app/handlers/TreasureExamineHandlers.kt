@file:Suppress("UnusedParameter")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.reasoning.treasureroom.TreasurePedestalOps

/**
 * Examine pedestals for [TreasureRoomHandlers] facade. Text from [TreasurePedestalOps] (MUD-039).
 */
internal object TreasureExamineHandlers {

    fun handleExaminePedestal(game: MudGame, target: String?) {
        val spaceId = game.worldState.player.currentRoomId
        val treasureRoomComponent = game.worldState.getTreasureRoom(spaceId)
        if (treasureRoomComponent == null) {
            println("There are no pedestals or altars here.")
            return
        }
        val templates = TreasurePedestalSupport.buildItemTemplatesMap(game, treasureRoomComponent)
        val pedestalInfos = game.treasureRoomHandler.getPedestalInfo(treasureRoomComponent, templates)
        if (treasureRoomComponent.hasBeenLooted) {
            println("The treasure room stands empty, its magic spent. Only bare altars remain.")
            return
        }
        print(TreasurePedestalOps.buildExamineText(treasureRoomComponent, templates, pedestalInfos))
    }
}
