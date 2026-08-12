@file:Suppress("ReturnCount", "MaxLineLength")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomExitLogic

/**
 * Treasure-room exit finalization for client movement travel branches.
 */
object ClientMovementTreasureExit {

    fun finalizeTreasureRoomExit(
        game: EngineGameClient,
        previousSpaceId: String,
        previousTreasureRoom: TreasureRoomComponent?
    ): String? {
        val treasureRoom = previousTreasureRoom ?: return null
        val result = TreasureRoomExitLogic.finalizeExit(treasureRoom, game.itemRepository) ?: return null
        game.worldState = game.worldState.updateTreasureRoom(previousSpaceId, result.updatedComponent)
        return result.narration
    }
}
