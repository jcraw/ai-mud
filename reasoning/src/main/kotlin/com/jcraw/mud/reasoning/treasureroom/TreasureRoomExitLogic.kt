package com.jcraw.mud.reasoning.treasureroom

import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.core.repository.ItemRepository

/**
 * Shared logic for Brogue-style treasure rooms when a player leaves with an item.
 */
object TreasureRoomExitLogic {
    data class ExitResult(
        val updatedComponent: TreasureRoomComponent,
        val narration: String
    )

    /**
     * Finalize a treasure room choice when the player departs while holding an item.
     *
     * @return ExitResult with updated component + narration, or null if nothing to finalize.
     */
    fun finalizeExit(
        treasureRoom: TreasureRoomComponent,
        itemRepository: ItemRepository
    ): ExitResult? {
        val takenItemId = treasureRoom.currentlyTakenItem ?: return null
        if (treasureRoom.hasBeenLooted) return null

        val updatedComponent = treasureRoom.markAsLooted()
        val itemName = itemRepository.findTemplateById(takenItemId)
            .getOrNull()
            ?.name
            ?: takenItemId

        val narration = "As you depart with the $itemName, the remaining treasures shimmer and fade. Your choice is final."
        return ExitResult(updatedComponent, narration)
    }
}
