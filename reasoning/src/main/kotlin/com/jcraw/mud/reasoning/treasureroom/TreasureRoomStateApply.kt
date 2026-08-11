package com.jcraw.mud.reasoning.treasureroom

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SpaceId
import com.jcraw.mud.core.WorldState

/**
 * Pure apply for treasure-room Success results onto WorldState.
 * Shared by console and GUI handlers so inventory + pedestal locks stay in lockstep.
 */
object TreasureRoomStateApply {

    /**
     * Apply a successful take/return to world state:
     * player [inventoryComponent] + treasure room pedestals at [spaceId].
     */
    fun applySuccess(
        world: WorldState,
        spaceId: SpaceId,
        player: PlayerState,
        success: TreasureRoomHandler.TreasureRoomResult.Success
    ): WorldState {
        return world
            .updatePlayer(player.copy(inventoryComponent = success.playerInventory))
            .updateTreasureRoom(spaceId, success.treasureRoomComponent)
    }
}
