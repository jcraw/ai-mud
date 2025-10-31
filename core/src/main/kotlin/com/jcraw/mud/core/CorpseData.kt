package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Corpse data structure for player death handling
 * Corpses are stored in database, not as world entities (simpler persistence)
 * Dark Souls-style corpse retrieval: player dies → corpse created → new player spawns at town
 */
@Serializable
data class CorpseData(
    val id: String,                             // Unique corpse identifier
    val playerId: PlayerId,                     // Player who died
    val spaceId: String,                        // Where corpse is located
    val inventory: InventoryComponent,          // Full player inventory at death
    val equipment: List<ItemInstance>,          // Equipped items at death
    val gold: Int,                              // Gold carried at death
    val decayTimer: Long,                       // gameTime when corpse despawns
    val looted: Boolean = false                 // Whether corpse has been looted
) {
    /**
     * Check if corpse has decayed
     * @param currentTime Current game time
     * @return true if corpse should be removed
     */
    fun hasDecayed(currentTime: Long): Boolean {
        return currentTime >= decayTimer
    }

    /**
     * Calculate turns remaining until decay
     * @param currentTime Current game time
     * @return Turns remaining (0 if already decayed)
     */
    fun turnsUntilDecay(currentTime: Long): Long {
        return (decayTimer - currentTime).coerceAtLeast(0L)
    }

    /**
     * Mark corpse as looted
     * @return Updated corpse with looted flag set
     */
    fun markLooted(): CorpseData {
        return copy(looted = true)
    }

    /**
     * Get total item count in corpse
     */
    fun itemCount(): Int {
        return inventory.items.size + equipment.size
    }

    /**
     * Get summary description of corpse contents
     */
    fun contentsSummary(): String {
        val items = mutableListOf<String>()
        if (gold > 0) items.add("$gold gold")
        if (inventory.items.isNotEmpty()) items.add("${inventory.items.size} items")
        if (equipment.isNotEmpty()) items.add("${equipment.size} equipped items")
        return items.joinToString(", ")
    }
}
