package com.jcraw.mud.core.repository

import com.jcraw.mud.core.TreasureRoomComponent

/**
 * Repository interface for treasure room persistence
 * Manages treasure room state including pedestals, taken items, and loot status
 */
interface TreasureRoomRepository {
    /**
     * Save or update treasure room component
     * Overwrites existing treasure room if spaceId matches
     *
     * @param component TreasureRoomComponent to save
     * @param spaceId Space ID where treasure room is located
     * @return Result with Unit on success, exception on failure
     */
    fun save(component: TreasureRoomComponent, spaceId: String): Result<Unit>

    /**
     * Find treasure room by space ID
     *
     * @param spaceId Space ID to search for
     * @return Result with TreasureRoomComponent if found, null if not found, exception on error
     */
    fun findBySpaceId(spaceId: String): Result<TreasureRoomComponent?>

    /**
     * Update currently taken item (for swap mechanics)
     * Optimized for single field update
     *
     * @param spaceId Space ID of treasure room
     * @param itemTemplateId Item template ID currently taken, or null if returned
     * @return Result with Unit on success, exception on failure
     */
    fun updateCurrentlyTakenItem(spaceId: String, itemTemplateId: String?): Result<Unit>

    /**
     * Mark treasure room as looted (sets hasBeenLooted=true, all pedestals EMPTY)
     * Called when player leaves room with item
     *
     * @param spaceId Space ID of treasure room
     * @return Result with Unit on success, exception on failure
     */
    fun markAsLooted(spaceId: String): Result<Unit>

    /**
     * Update pedestal state (AVAILABLE, LOCKED, EMPTY)
     * Optimized for single pedestal update
     *
     * @param spaceId Space ID of treasure room
     * @param itemTemplateId Item template ID of pedestal to update
     * @param newState New pedestal state
     * @return Result with Unit on success, exception on failure
     */
    fun updatePedestalState(spaceId: String, itemTemplateId: String, newState: String): Result<Unit>

    /**
     * Delete treasure room by space ID
     *
     * @param spaceId Space ID to delete
     * @return Result with Unit on success, exception on failure
     */
    fun delete(spaceId: String): Result<Unit>

    /**
     * Find all treasure rooms (for debugging/testing)
     *
     * @return Result with list of all treasure room components
     */
    fun findAll(): Result<List<Pair<String, TreasureRoomComponent>>>
}
