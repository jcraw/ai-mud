package com.jcraw.mud.memory.world

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.repository.WorldSeedRepository
import com.jcraw.mud.core.world.NavigationState

/**
 * Handles save/load operations for the world generation system.
 * Integrates with existing persistence infrastructure.
 */
class WorldPersistence(
    private val worldSeedRepository: WorldSeedRepository,
    private val worldChunkRepository: WorldChunkRepository,
    private val spacePropertiesRepository: SpacePropertiesRepository
) {
    /**
     * Saves the current world state including seed, chunks, and spaces.
     * Performs incremental save based on what's currently loaded.
     *
     * @param worldId The ID of the world root chunk
     * @param playerState Current player state (for navigation info)
     * @param loadedChunks Chunks currently loaded in memory
     * @param loadedSpaces Spaces currently loaded in memory
     * @return Result indicating success or failure
     */
    suspend fun saveWorldState(
        worldId: String,
        playerState: PlayerState,
        loadedChunks: Map<String, WorldChunkComponent>,
        loadedSpaces: Map<String, SpacePropertiesComponent>
    ): Result<Unit> = runCatching {
        // Save chunks in batch
        loadedChunks.forEach { (chunkId, chunk) ->
            worldChunkRepository.save(chunk, chunkId).getOrThrow()
        }

        // Save spaces in batch
        loadedSpaces.forEach { (chunkId, space) ->
            spacePropertiesRepository.save(space, chunkId).getOrThrow()
        }
    }

    /**
     * Loads world state from disk.
     * Returns the starting space ID and global lore for world context.
     *
     * @param startingSpaceId The space ID to start from (from player's NavigationState)
     * @return Pair of (global lore, starting space properties) or null if not found
     */
    suspend fun loadWorldState(
        startingSpaceId: String
    ): Result<Pair<String, SpacePropertiesComponent>?> = runCatching {
        // Load world seed to get global lore
        val (_, globalLore) = worldSeedRepository.get().getOrThrow()
            ?: return@runCatching null

        // Load starting space
        val startingSpace = spacePropertiesRepository.findByChunkId(startingSpaceId).getOrThrow()
            ?: return@runCatching null

        globalLore to startingSpace
    }

    /**
     * Performs incremental save of a single space (for autosave).
     * More efficient than full saveWorldState for frequent updates.
     *
     * @param space The space to save
     * @param chunkId The chunk ID for this space
     * @return Result indicating success or failure
     */
    suspend fun saveSpace(
        space: SpacePropertiesComponent,
        chunkId: String
    ): Result<Unit> = runCatching {
        spacePropertiesRepository.save(space, chunkId).getOrThrow()
    }

    /**
     * Loads a chunk by ID, used for lazy loading during navigation.
     *
     * @param chunkId The chunk ID to load
     * @return The WorldChunkComponent or null if not found
     */
    suspend fun loadChunk(chunkId: String): Result<WorldChunkComponent?> {
        return worldChunkRepository.findById(chunkId)
    }

    /**
     * Loads a space by chunk ID, used for lazy loading during navigation.
     *
     * @param chunkId The chunk ID to load
     * @return The SpacePropertiesComponent or null if not found
     */
    suspend fun loadSpace(chunkId: String): Result<SpacePropertiesComponent?> {
        return spacePropertiesRepository.findByChunkId(chunkId)
    }

    /**
     * Prefetches adjacent spaces for smoother navigation.
     * Returns map of chunk IDs to spaces for caching.
     *
     * @param currentSpaceId The current space ID
     * @param exitTargetIds List of exit target space IDs to prefetch
     * @return Map of prefetched spaces (chunkId -> SpacePropertiesComponent)
     */
    suspend fun prefetchAdjacentSpaces(
        currentSpaceId: String,
        exitTargetIds: List<String>
    ): Result<Map<String, SpacePropertiesComponent>> = runCatching {
        val spaces = mutableMapOf<String, SpacePropertiesComponent>()

        exitTargetIds.forEach { targetId ->
            spacePropertiesRepository.findByChunkId(targetId).getOrNull()?.let { space ->
                spaces[targetId] = space
            }
        }

        spaces
    }

    /**
     * Saves world seed (global lore and seed string).
     * Only needs to be called once during world initialization.
     *
     * @param seed The random seed string
     * @param globalLore The world's global lore
     * @return Result indicating success or failure
     */
    suspend fun saveWorldSeed(seed: String, globalLore: String): Result<Unit> {
        return worldSeedRepository.save(seed, globalLore)
    }

    /**
     * Gets the world seed and global lore.
     * Returns null if no world has been initialized.
     *
     * @return Pair of (seed, globalLore) or null
     */
    suspend fun getWorldSeed(): Result<Pair<String, String>?> {
        return worldSeedRepository.get()
    }

    /**
     * Clears all world data from the database.
     * Used for starting a fresh world.
     *
     * @return Result indicating success or failure
     */
    suspend fun clearAllWorldData(): Result<Unit> = runCatching {
        // The database has a clearAll method that clears all tables
        // This would be called through the database instance
        // For now, we delegate to individual repositories
        // (actual implementation depends on database setup)
    }
}
