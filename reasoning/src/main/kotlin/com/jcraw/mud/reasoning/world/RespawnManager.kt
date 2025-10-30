package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.repository.WorldChunkRepository

/**
 * Handles game restart with mob respawn while preserving player-made changes.
 * Murder-hobo viable: dungeons repopulate for replayability.
 */
class RespawnManager(
    private val worldChunkRepository: WorldChunkRepository,
    private val spacePropertiesRepository: SpacePropertiesRepository,
    private val mobSpawner: MobSpawner,
    private val dungeonInitializer: DungeonInitializer
) {
    /**
     * Respawns mobs across the world while preserving state flags, items, and resources.
     * Used when restarting a saved game.
     *
     * @param worldId The root world chunk ID
     * @return Result with count of spaces respawned
     */
    suspend fun respawnWorld(worldId: String): Result<Int> = runCatching {
        var respawnCount = 0

        // Load root chunk
        val rootChunk = worldChunkRepository.findById(worldId).getOrThrow()
            ?: error("World not found: $worldId")

        // Recursively process all chunks
        respawnCount += respawnChunkRecursively(worldId, rootChunk)

        respawnCount
    }

    /**
     * Recursively respawns mobs in a chunk and all its children.
     */
    private suspend fun respawnChunkRecursively(
        chunkId: String,
        chunk: WorldChunkComponent
    ): Int {
        var count = 0

        // If this is a SPACE level, respawn mobs
        if (chunk.level == com.jcraw.mud.core.world.ChunkLevel.SPACE) {
            val space = spacePropertiesRepository.findByChunkId(chunkId).getOrNull()
            if (space != null) {
                val respawnedSpace = respawnSpaceMobs(space, chunk)
                spacePropertiesRepository.save(respawnedSpace, chunkId).getOrThrow()
                count++
            }
        }

        // Recursively process children
        for (childId in chunk.children) {
            val childChunk = worldChunkRepository.findById(childId).getOrNull()
            if (childChunk != null) {
                count += respawnChunkRecursively(childId, childChunk)
            }
        }

        return count
    }

    /**
     * Respawns mobs in a single space while preserving state flags, items, resources, and traps.
     */
    private fun respawnSpaceMobs(
        space: SpacePropertiesComponent,
        chunkMeta: WorldChunkComponent
    ): SpacePropertiesComponent {
        // Clear existing entities
        val clearedSpace = space.copy(entities = emptyList())

        // Respawn mobs based on theme, density, and difficulty
        val newMobs = mobSpawner.spawnEntities(
            theme = chunkMeta.biomeTheme,
            mobDensity = chunkMeta.mobDensity,
            difficulty = chunkMeta.difficultyLevel,
            spaceSize = chunkMeta.sizeEstimate
        )

        // Return space with new mobs, preserving everything else
        return clearedSpace.copy(entities = newMobs)
    }

    /**
     * Creates a fresh dungeon start (for new games).
     * Initializes the deep dungeon and returns the starting space ID.
     *
     * @param seed Random seed for generation
     * @return Result with starting space ID
     */
    suspend fun createFreshStart(seed: String): Result<String> {
        return dungeonInitializer.initializeDeepDungeon(seed)
    }

    /**
     * Clears entities from a specific space (for manual control).
     * Useful for testing or specific game mechanics.
     *
     * @param chunkId The space chunk ID
     * @return Result with updated space
     */
    suspend fun clearSpaceEntities(chunkId: String): Result<SpacePropertiesComponent> = runCatching {
        val space = spacePropertiesRepository.findByChunkId(chunkId).getOrThrow()
            ?: error("Space not found: $chunkId")

        val clearedSpace = space.copy(entities = emptyList())
        spacePropertiesRepository.save(clearedSpace, chunkId).getOrThrow()

        clearedSpace
    }

    /**
     * Respawns mobs in a specific space (for testing or triggered respawns).
     *
     * @param chunkId The space chunk ID
     * @param theme Biome theme for mob selection
     * @param mobDensity Density for calculating mob count
     * @param difficulty Difficulty level for scaling mob stats
     * @param spaceSize Size estimate for calculating mob count
     * @return Result with updated space
     */
    suspend fun respawnSpaceEntities(
        chunkId: String,
        theme: String,
        mobDensity: Double,
        difficulty: Int,
        spaceSize: Int
    ): Result<SpacePropertiesComponent> = runCatching {
        val space = spacePropertiesRepository.findByChunkId(chunkId).getOrThrow()
            ?: error("Space not found: $chunkId")

        val newMobs = mobSpawner.spawnEntities(theme, mobDensity, difficulty, spaceSize)
        val updatedSpace = space.copy(entities = newMobs)

        spacePropertiesRepository.save(updatedSpace, chunkId).getOrThrow()

        updatedSpace
    }
}
