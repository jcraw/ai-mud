package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.world.NavigationState
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.repository.WorldSeedRepository

/**
 * Ensures the Ancient Abyss world exists and returns a starting space + navigation state.
 * Shared by both console and GUI front-ends so they boot the exact same experience.
 */
class AncientAbyssStarter(
    private val worldSeedRepository: WorldSeedRepository,
    private val worldChunkRepository: WorldChunkRepository,
    private val dungeonInitializer: DungeonInitializer
) {
    /**
     * Ensure the Ancient Abyss is generated and return starting context.
     *
     * @param seed Optional override for deterministic world generation.
     */
    suspend fun ensureAncientAbyss(seed: String = DEFAULT_SEED): Result<AncientAbyssStart> {
        val existingSeed = worldSeedRepository.get().getOrElse { return Result.failure(it) }

        val existingStartingSpaceId = existingSeed?.startingSpaceId
        if (!existingStartingSpaceId.isNullOrBlank()) {
            val navResult = NavigationState.fromSpaceId(existingStartingSpaceId, worldChunkRepository)
            if (navResult.isSuccess) {
                return Result.success(
                    AncientAbyssStart(
                        startingSpaceId = existingStartingSpaceId,
                        navigationState = navResult.getOrThrow()
                    )
                )
            }
            // Fall through to regeneration if the persisted hierarchy is corrupted.
        }

        val abyssData = dungeonInitializer.initializeAncientAbyss(seed)
            .getOrElse { return Result.failure(it) }

        val navigationState = NavigationState
            .fromSpaceId(abyssData.townSpaceId, worldChunkRepository)
            .getOrElse { return Result.failure(it) }

        return Result.success(
            AncientAbyssStart(
                startingSpaceId = abyssData.townSpaceId,
                navigationState = navigationState
            )
        )
    }

    companion object {
        private const val DEFAULT_SEED = "dark fantasy DnD"
    }
}

/**
 * Data returned by AncientAbyssStarter after ensuring/generating the dungeon.
 */
data class AncientAbyssStart(
    val startingSpaceId: String,
    val navigationState: NavigationState
)
