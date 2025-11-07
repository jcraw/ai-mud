package com.jcraw.mud.core.world

import com.jcraw.mud.core.repository.WorldChunkRepository
import kotlinx.serialization.Serializable

/**
 * Tracks a player's current location in the world hierarchy.
 * This enables fast hierarchy queries and breadcrumb trails without repeated DB lookups.
 *
 * @param currentSpaceId The ID of the current SPACE (room-level) the player is in
 * @param currentSubzoneId The ID of the parent SUBZONE containing the space
 * @param currentZoneId The ID of the parent ZONE containing the subzone
 * @param currentRegionId The ID of the parent REGION containing the zone
 * @param worldId The ID of the root WORLD
 * @param breadcrumbs List of recently visited space IDs (for tracking player's path)
 */
@Serializable
data class NavigationState(
    val currentSpaceId: String,
    val currentSubzoneId: String,
    val currentZoneId: String,
    val currentRegionId: String,
    val worldId: String,
    val breadcrumbs: List<String> = emptyList()
) {
    /**
     * Updates the navigation state when moving to a new space.
     * Queries the repository to walk up the hierarchy and update all parent IDs.
     *
     * @param newSpaceId The ID of the space the player is moving to
     * @param repo Repository for querying chunk hierarchy
     * @return Updated NavigationState with new location and updated breadcrumbs
     */
    suspend fun updateLocation(
        newSpaceId: String,
        repo: WorldChunkRepository
    ): Result<NavigationState> = runCatching {
        // Extract the parent SUBZONE ID from the space ID using ChunkIdGenerator
        val newSubzoneId = ChunkIdGenerator.extractParentId(newSpaceId)
            ?: error("Space has no parent subzone: $newSpaceId")

        // Walk up the hierarchy
        val subzoneChunk = repo.findById(newSubzoneId).getOrThrow()
            ?: error("Subzone chunk not found: $newSubzoneId")

        val newZoneId = subzoneChunk.parentId
            ?: error("Subzone has no parent zone: $newSubzoneId")

        val zoneChunk = repo.findById(newZoneId).getOrThrow()
            ?: error("Zone chunk not found: $newZoneId")

        val newRegionId = zoneChunk.parentId
            ?: error("Zone has no parent region: $newZoneId")

        val regionChunk = repo.findById(newRegionId).getOrThrow()
            ?: error("Region chunk not found: $newRegionId")

        val newWorldId = regionChunk.parentId
            ?: error("Region has no parent world: $newRegionId")

        // Add current space to breadcrumbs (keep last 20)
        val newBreadcrumbs = (breadcrumbs + newSpaceId).takeLast(20)

        NavigationState(
            currentSpaceId = newSpaceId,
            currentSubzoneId = newSubzoneId,
            currentZoneId = newZoneId,
            currentRegionId = newRegionId,
            worldId = newWorldId,
            breadcrumbs = newBreadcrumbs
        )
    }

    /**
     * Adds a space to breadcrumbs without changing location (for viewing adjacent spaces)
     */
    fun recordVisit(spaceId: String): NavigationState {
        val newBreadcrumbs = (breadcrumbs + spaceId).takeLast(20)
        return copy(breadcrumbs = newBreadcrumbs)
    }

    companion object {
        /**
         * Creates a new NavigationState from a starting space ID by walking up the hierarchy.
         *
         * @param startingSpaceId The ID of the space to start at
         * @param repo Repository for querying chunk hierarchy
         * @return NavigationState with all hierarchy IDs populated
         */
        suspend fun fromSpaceId(
            startingSpaceId: String,
            repo: WorldChunkRepository
        ): Result<NavigationState> = runCatching {
            // Extract the parent SUBZONE ID from the space ID using ChunkIdGenerator
            val subzoneId = ChunkIdGenerator.extractParentId(startingSpaceId)
                ?: error("Space has no parent subzone: $startingSpaceId")

            // Walk up the hierarchy
            val subzoneChunk = repo.findById(subzoneId).getOrThrow()
                ?: error("Subzone chunk not found: $subzoneId")

            val zoneId = subzoneChunk.parentId
                ?: error("Subzone has no parent zone: $subzoneId")

            val zoneChunk = repo.findById(zoneId).getOrThrow()
                ?: error("Zone chunk not found: $zoneId")

            val regionId = zoneChunk.parentId
                ?: error("Zone has no parent region: $zoneId")

            val regionChunk = repo.findById(regionId).getOrThrow()
                ?: error("Region chunk not found: $regionId")

            val worldId = regionChunk.parentId
                ?: error("Region has no parent world: $regionId")

            NavigationState(
                currentSpaceId = startingSpaceId,
                currentSubzoneId = subzoneId,
                currentZoneId = zoneId,
                currentRegionId = regionId,
                worldId = worldId,
                breadcrumbs = listOf(startingSpaceId)
            )
        }
    }
}
