package com.jcraw.mud.core.repository

/**
 * Repository interface for world seed persistence
 * Manages global world seed and lore (singleton pattern)
 */
data class WorldSeedInfo(
    val seed: String,
    val globalLore: String,
    val startingSpaceId: String?
)

interface WorldSeedRepository {
    /**
     * Save or update world seed and global lore
     * Only one seed exists per database (id=1)
     * Updates existing seed if already present
     */
    fun save(seed: String, globalLore: String, startingSpaceId: String? = null): Result<Unit>

    /**
     * Get world seed and global lore
     * Returns WorldSeedInfo or null if not initialized
     */
    fun get(): Result<WorldSeedInfo?>
}
