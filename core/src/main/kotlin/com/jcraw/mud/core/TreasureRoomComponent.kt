package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Treasure room component for Brogue-inspired item selection mechanics
 * Manages pedestals with playstyle-defining items where players can take one item,
 * with barriers/cages locking others, but can return to swap freely until leaving room
 *
 * Design principles:
 * - Immutable: All methods return new instances
 * - One active item: currentlyTakenItem tracks which item player has
 * - Swap mechanic: Returning item unlocks all pedestals
 * - One-time loot: hasBeenLooted prevents re-farming after leaving with item
 * - Biome themed: altars adapt to dungeon atmosphere
 *
 * @param roomType Type of treasure room (STARTER, COMBAT, MAGIC, etc.)
 * @param pedestals List of pedestals with items and states
 * @param currentlyTakenItem Item template ID currently taken by player, or null
 * @param hasBeenLooted True if player has left room with item (pedestals empty)
 * @param biomeTheme Dungeon biome for altar theming ("ancient_abyss", "magma_cave", etc.)
 */
@Serializable
data class TreasureRoomComponent(
    val roomType: TreasureRoomType,
    val pedestals: List<Pedestal>,
    val currentlyTakenItem: String?, // Item template ID or null
    val hasBeenLooted: Boolean = false,
    val biomeTheme: String,
    override val componentType: ComponentType = ComponentType.TREASURE_ROOM
) : Component {

    /**
     * Take item from pedestal
     * Locks all other pedestals and sets currentlyTakenItem
     *
     * @param itemTemplateId Item template ID to take
     * @return New TreasureRoomComponent with item taken and pedestals locked
     * @throws IllegalStateException if room is looted, item already taken, or pedestal not available
     */
    fun takeItem(itemTemplateId: String): TreasureRoomComponent {
        require(!hasBeenLooted) { "Cannot take item from looted treasure room" }
        require(currentlyTakenItem == null) { "Already holding item: $currentlyTakenItem. Return it first." }

        val pedestalIndex = pedestals.indexOfFirst { it.itemTemplateId == itemTemplateId }
        require(pedestalIndex >= 0) { "Item $itemTemplateId not found in treasure room" }
        require(pedestals[pedestalIndex].state == PedestalState.AVAILABLE) {
            "Pedestal with $itemTemplateId is ${pedestals[pedestalIndex].state}"
        }

        return copy(
            currentlyTakenItem = itemTemplateId,
            pedestals = lockPedestalsExcept(itemTemplateId)
        )
    }

    /**
     * Return item to pedestal
     * Unlocks all pedestals and clears currentlyTakenItem
     *
     * @param itemTemplateId Item template ID to return
     * @return New TreasureRoomComponent with item returned and pedestals unlocked
     * @throws IllegalStateException if item doesn't match currentlyTakenItem
     */
    fun returnItem(itemTemplateId: String): TreasureRoomComponent {
        require(currentlyTakenItem == itemTemplateId) {
            "Cannot return $itemTemplateId - currently holding ${currentlyTakenItem ?: "nothing"}"
        }

        return copy(
            currentlyTakenItem = null,
            pedestals = unlockPedestals()
        )
    }

    /**
     * Mark room as looted (called when player leaves with item)
     * Sets hasBeenLooted=true and all pedestals to EMPTY
     *
     * @return New TreasureRoomComponent marked as looted
     */
    fun markAsLooted(): TreasureRoomComponent {
        return copy(
            hasBeenLooted = true,
            pedestals = pedestals.map { it.copy(state = PedestalState.EMPTY) }
        )
    }

    /**
     * Lock all pedestals except the specified one
     * @param exceptItemId Item template ID to keep AVAILABLE
     * @return List of pedestals with appropriate states
     */
    private fun lockPedestalsExcept(exceptItemId: String): List<Pedestal> {
        return pedestals.map { pedestal ->
            if (pedestal.itemTemplateId == exceptItemId) {
                pedestal.copy(state = PedestalState.AVAILABLE) // Taken pedestal stays available for return
            } else {
                pedestal.copy(state = PedestalState.LOCKED)
            }
        }
    }

    /**
     * Unlock all pedestals (when item is returned)
     * @return List of pedestals all set to AVAILABLE
     */
    private fun unlockPedestals(): List<Pedestal> {
        return pedestals.map { it.copy(state = PedestalState.AVAILABLE) }
    }

    /**
     * Get pedestal by item template ID
     * @param itemTemplateId Item template ID to find
     * @return Pedestal or null if not found
     */
    fun getPedestal(itemTemplateId: String): Pedestal? {
        return pedestals.firstOrNull { it.itemTemplateId == itemTemplateId }
    }

    /**
     * Check if a specific item can be taken
     * Note: When an item is taken, the pedestal remains AVAILABLE for returning
     * @param itemTemplateId Item template ID to check
     * @return True if item pedestal is AVAILABLE
     */
    fun canTakeItem(itemTemplateId: String): Boolean {
        if (hasBeenLooted) return false
        val pedestal = getPedestal(itemTemplateId) ?: return false
        return pedestal.state == PedestalState.AVAILABLE
    }

    /**
     * Get all available pedestals
     * @return List of pedestals that can be interacted with
     */
    fun getAvailablePedestals(): List<Pedestal> {
        return pedestals.filter { it.state == PedestalState.AVAILABLE }
    }
}

/**
 * Pedestal data for treasure room
 * Represents a single altar/pedestal with an item
 *
 * @param itemTemplateId Item template ID for this pedestal
 * @param state Current pedestal state (AVAILABLE, LOCKED, EMPTY)
 * @param themeDescription Themed description for altar ("ancient stone altar", "obsidian shrine", etc.)
 * @param pedestalIndex Position in room (0-based index)
 */
@Serializable
data class Pedestal(
    val itemTemplateId: String,
    val state: PedestalState,
    val themeDescription: String,
    val pedestalIndex: Int
)

/**
 * State of a pedestal in treasure room
 */
@Serializable
enum class PedestalState {
    /** Pedestal is available and item can be taken */
    AVAILABLE,

    /** Pedestal is locked by barrier/cage (another item was taken) */
    LOCKED,

    /** Pedestal is empty (room has been looted) */
    EMPTY
}

/**
 * Type of treasure room
 * Different types may have different item selections or mechanics
 */
@Serializable
enum class TreasureRoomType {
    /** Starter treasure room with playstyle-defining items (5 pedestals) */
    STARTER,

    /** Combat-focused treasure room (future enhancement) */
    COMBAT,

    /** Magic-focused treasure room (future enhancement) */
    MAGIC,

    /** Boss reward treasure room (future enhancement) */
    BOSS
}
