@file:Suppress("ReturnCount", "MaxLineLength")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomExitLogic

/**
 * Treasure-room exit finalization for movement travel branches.
 */
object MovementTreasureExit {

    fun finalizeTreasureRoomExit(
        game: MudGame,
        previousSpaceId: String,
        previousTreasureRoom: TreasureRoomComponent?
    ): String? {
        val treasureRoom = previousTreasureRoom ?: return null
        val result = TreasureRoomExitLogic.finalizeExit(treasureRoom, game.itemRepository) ?: return null
        game.worldState = game.worldState.updateTreasureRoom(previousSpaceId, result.updatedComponent)
        return result.narration
    }
}
