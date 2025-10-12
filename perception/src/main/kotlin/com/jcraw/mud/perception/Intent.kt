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
     * Search for hidden items or secrets in the current room
     * @param target Optional specific area to search (e.g., "moss", "walls")
     */
    @Serializable
    data class Search(val target: String? = null) : Intent()

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
     * Pick up an item from the current room
     * @param target The name/identifier of the item to pick up
     */
    @Serializable
    data class Take(val target: String) : Intent()

    /**
     * Drop an item from inventory into the current room
     * @param target The name/identifier of the item to drop
     */
    @Serializable
    data class Drop(val target: String) : Intent()

    /**
     * Talk to an NPC
     * @param target The name/identifier of the NPC to talk to
     */
    @Serializable
    data class Talk(val target: String) : Intent()

    /**
     * Attack an NPC or continue attacking in combat
     * @param target The name/identifier of the NPC to attack (optional if already in combat)
     */
    @Serializable
    data class Attack(val target: String? = null) : Intent()

    /**
     * Equip a weapon or armor from inventory
     * @param target The name/identifier of the item to equip
     */
    @Serializable
    data class Equip(val target: String) : Intent()

    /**
     * Use a consumable item (potion, food, etc.)
     * @param target The name/identifier of the item to use
     */
    @Serializable
    data class Use(val target: String) : Intent()

    /**
     * Perform a skill check against a target or feature
     * @param target The name/identifier of what to check against
     */
    @Serializable
    data class Check(val target: String) : Intent()

    /**
     * Attempt to persuade an NPC (CHA check)
     * @param target The name/identifier of the NPC to persuade
     */
    @Serializable
    data class Persuade(val target: String) : Intent()

    /**
     * Attempt to intimidate an NPC (CHA check)
     * @param target The name/identifier of the NPC to intimidate
     */
    @Serializable
    data class Intimidate(val target: String) : Intent()

    /**
     * Save the current game state
     * @param saveName Optional name for the save file (defaults to "quicksave")
     */
    @Serializable
    data class Save(val saveName: String = "quicksave") : Intent()

    /**
     * Load a saved game state
     * @param saveName Optional name of the save file to load (defaults to "quicksave")
     */
    @Serializable
    data class Load(val saveName: String = "quicksave") : Intent()

    /**
     * View active quests and quest progress
     */
    @Serializable
    data object Quests : Intent()

    /**
     * Accept a new quest
     * @param questId Optional quest identifier to accept (if omitted, shows available quests)
     */
    @Serializable
    data class AcceptQuest(val questId: String? = null) : Intent()

    /**
     * Abandon an active quest
     * @param questId The identifier of the quest to abandon
     */
    @Serializable
    data class AbandonQuest(val questId: String) : Intent()

    /**
     * Claim reward for a completed quest
     * @param questId The identifier of the quest to claim reward for
     */
    @Serializable
    data class ClaimReward(val questId: String) : Intent()

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
