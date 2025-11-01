package com.jcraw.mud.core.repository

import com.jcraw.mud.core.CorpseData
import com.jcraw.mud.core.PlayerId

/**
 * Repository interface for corpse data persistence
 * Manages player death corpses with Dark Souls-style retrieval
 */
interface CorpseRepository {
    /**
     * Save or update a corpse
     * Overwrites existing corpse if ID matches
     */
    fun save(corpse: CorpseData): Result<Unit>

    /**
     * Find corpse by ID
     * Returns null if not found
     */
    fun findById(id: String): Result<CorpseData?>

    /**
     * Find all corpses belonging to a specific player
     * Returns list ordered by decay timer (oldest first)
     * Useful for "you have X corpses" messages
     */
    fun findByPlayerId(playerId: PlayerId): Result<List<CorpseData>>

    /**
     * Find all corpses in a specific space
     * Returns list ordered by decay timer (oldest first)
     * Used for "look" command to show corpses in room
     */
    fun findBySpaceId(spaceId: String): Result<List<CorpseData>>

    /**
     * Find all corpses that have decayed
     * Returns list of corpses ready for cleanup
     *
     * @param currentTime Current game time (in turns)
     */
    fun findDecayed(currentTime: Long): Result<List<CorpseData>>

    /**
     * Mark corpse as looted
     * Optimized single-field update (avoids full object serialization)
     */
    fun markLooted(corpseId: String): Result<Unit>

    /**
     * Delete corpse by ID
     * Used when player retrieves corpse or when it decays
     */
    fun delete(id: String): Result<Unit>

    /**
     * Delete all corpses in a specific space
     * Useful for space cleanup operations
     */
    fun deleteBySpaceId(spaceId: String): Result<Unit>

    /**
     * Get all corpses
     * Returns map of corpseId -> corpse
     */
    fun getAll(): Result<Map<String, CorpseData>>
}
