package com.jcraw.mud.perception

import com.jcraw.mud.core.Direction
import kotlinx.serialization.Serializable

/**
 * Represents the player's parsed intent from text input.
 * This sealed class hierarchy defines all possible actions a player can take.
 */
@Serializable
sealed class Intent {
    /**
     * Move in a specified direction
     */
    @Serializable
    data class Move(val direction: Direction) : Intent()

    /**
     * Look at the current room or a specific target
     * @param target If null, look at the room; otherwise look at a specific entity/exit
     */
    @Serializable
    data class Look(val target: String? = null) : Intent()

    /**
     * Interact with a specific entity or object in the room
     * @param target The name/identifier of what to interact with
     */
    @Serializable
    data class Interact(val target: String) : Intent()

    /**
     * View player inventory
     */
    @Serializable
    data object Inventory : Intent()

    /**
     * Request help/available commands
     */
    @Serializable
    data object Help : Intent()

    /**
     * Quit the game
     */
    @Serializable
    data object Quit : Intent()

    /**
     * Failed to parse input - contains the error message
     */
    @Serializable
    data class Invalid(val message: String) : Intent()
}
