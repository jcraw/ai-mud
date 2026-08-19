@file:Suppress("UnusedParameter")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.reasoning.treasureroom.TreasurePedestalOps

/**
 * Examine pedestals for [ClientTreasureRoomHandlers] facade. Text from [TreasurePedestalOps] (MUD-039).
 */
internal object ClientTreasureExamineHandlers {

    fun handleExaminePedestal(game: EngineGameClient, target: String?) {
        val spaceId = game.worldState.player.currentRoomId
        val treasureRoomComponent = game.worldState.getTreasureRoom(spaceId)
        if (treasureRoomComponent == null) {
            game.emitEvent(
                GameEvent.System("There are no pedestals or altars here.", GameEvent.MessageLevel.INFO)
            )
            return
        }
        emitExamine(game, treasureRoomComponent)
    }

    private fun emitExamine(game: EngineGameClient, room: com.jcraw.mud.core.TreasureRoomComponent) {
        val templates = ClientTreasurePedestalSupport.buildItemTemplatesMap(game, room)
        val pedestalInfos = game.treasureRoomHandler.getPedestalInfo(room, templates)
        if (room.hasBeenLooted) {
            game.emitEvent(
                GameEvent.Narrative("The treasure room stands empty, its magic spent. Only bare altars remain.")
            )
            return
        }
        game.emitEvent(
            GameEvent.Narrative(TreasurePedestalOps.buildExamineText(room, templates, pedestalInfos))
        )
    }
}
