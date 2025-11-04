package com.jcraw.mud.core.world

import com.jcraw.mud.core.ItemInstance
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sealed class representing player-initiated world modifications.
 * These actions modify the state of a space and may trigger description regeneration.
 */
@Serializable
sealed class WorldAction {
    /**
     * Destroy an obstacle in the space (e.g., break boulder, cut vines).
     * Sets the flag to true, potentially opening new exits or revealing items.
     */
    @Serializable
    @SerialName("DestroyObstacle")
    data class DestroyObstacle(
        val flag: String,
        val skillRequired: String? = null,
        val difficulty: Int? = null
    ) : WorldAction()

    /**
     * Trigger a trap (either deliberately or accidentally).
     * Marks the trap as triggered, preventing future triggers.
     */
    @Serializable
    @SerialName("TriggerTrap")
    data class TriggerTrap(val trapId: String) : WorldAction()

    /**
     * Harvest a resource node (e.g., mine ore, gather herbs).
     * Removes or depletes the resource from the space.
     */
    @Serializable
    @SerialName("HarvestResource")
    data class HarvestResource(val nodeId: String) : WorldAction()

    /**
     * Place an item in the space (drop from inventory).
     * Adds item to the space's itemsDropped list.
     */
    @Serializable
    @SerialName("PlaceItem")
    data class PlaceItem(val item: ItemInstance) : WorldAction()

    /**
     * Remove an item from the space (pick up).
     * Removes item from the space's itemsDropped list.
     */
    @Serializable
    @SerialName("RemoveItem")
    data class RemoveItem(val itemId: String) : WorldAction()

    /**
     * Unlock an exit (e.g., unlock door with key, solve puzzle).
     * Removes conditions from the exit or marks unlock flag.
     */
    @Serializable
    @SerialName("UnlockExit")
    data class UnlockExit(
        val exitDirection: String,
        val keyItem: String? = null
    ) : WorldAction()

    /**
     * Generic state flag change (flexible for custom actions).
     * Can be used for any world state modification not covered by specific actions.
     */
    @Serializable
    @SerialName("SetFlag")
    data class SetFlag(
        val flag: String,
        val value: Boolean = true
    ) : WorldAction()
}
