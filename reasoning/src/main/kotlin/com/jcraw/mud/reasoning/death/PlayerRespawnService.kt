package com.jcraw.mud.reasoning.death

import com.jcraw.mud.core.PlayerId
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.repository.CorpseRepository
import com.jcraw.mud.core.repository.TreasureRoomRepository

/**
 * Orchestrates permadeath restart flow for any client.
 *
 * Responsibilities:
 * - Persist player corpses via [CorpseRepository]
 * - Produce pending restart tokens that UIs can hold while prompting
 * - Create fresh player avatars once the user confirms and names them
 * - Reset treasure rooms when a new character is created
 *
 * Stateless aside from the repository dependency, so it is safe to share
 * across multiple clients/players (multiplayer-ready).
 */
class PlayerRespawnService(
    private val corpseRepository: CorpseRepository,
    private val treasureRoomRepository: TreasureRoomRepository
) {

    /**
     * Snapshot of a death event. Callers hold onto this while the UI asks the
     * player whether to continue and what to name the new character.
     */
    data class PendingRespawn(
        val playerId: PlayerId,
        val deathResult: DeathResult
    )

    /**
     * Result of completing the respawn flow.
     */
    data class RespawnOutcome(
        val worldState: WorldState,
        val player: PlayerState,
        val deathResult: DeathResult,
        val respawnMessage: String
    )

    /**
     * Persist corpse + produce pending restart token.
     */
    fun createPendingRespawn(
        worldState: WorldState,
        playerId: PlayerId,
        spawnSpaceIdOverride: String? = null
    ): Result<PendingRespawn> {
        val player = worldState.getPlayer(playerId)
            ?: return Result.failure(IllegalStateException("Player $playerId not found"))
        val spawnSpaceId = spawnSpaceIdOverride
            ?: resolveSpawnSpaceId(worldState)
            ?: return Result.failure(IllegalStateException("No spawn location configured"))

        return handlePlayerDeath(
            player = player,
            currentSpaceId = player.currentRoomId,
            townSpaceId = spawnSpaceId,
            gameTime = worldState.gameTime,
            corpseRepository = corpseRepository
        ).map { deathResult ->
            PendingRespawn(playerId = playerId, deathResult = deathResult)
        }
    }

    /**
     * Finalize restart once the player confirms permadeath and provides a new name.
     * Also resets all treasure rooms in the world to pristine condition.
     */
    fun completeRespawn(
        worldState: WorldState,
        pending: PendingRespawn,
        newCharacterName: String
    ): Result<RespawnOutcome> {
        val trimmedName = newCharacterName.trim()
        if (trimmedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Character name cannot be blank"))
        }

        // Reset all treasure rooms when new character spawns
        resetAllTreasureRooms().onFailure { error ->
            // Log error but don't fail respawn - treasure rooms are non-critical
            println("Warning: Failed to reset treasure rooms during respawn: ${error.message}")
        }

        val newPlayer = pending.deathResult.createNewPlayer(trimmedName)
        val updatedWorld = worldState.updatePlayer(newPlayer)
        val spawnSpaceName = updatedWorld
            .getSpace(newPlayer.currentRoomId)
            ?.name
            ?: "the dungeon entrance"

        val message = buildString {
            append("A new adventurer named $trimmedName awakens at $spawnSpaceName.")
            append(" Corpse ${pending.deathResult.corpseId} still lies in ${pending.deathResult.deathSpaceId}.")
        }

        return Result.success(
            RespawnOutcome(
                worldState = updatedWorld,
                player = newPlayer,
                deathResult = pending.deathResult,
                respawnMessage = message
            )
        )
    }

    /**
     * Reset all treasure rooms in the world to pristine condition.
     * Called when a new character is created after death.
     */
    private fun resetAllTreasureRooms(): Result<Unit> {
        // Get all treasure rooms
        val allTreasureRooms = treasureRoomRepository.findAll().getOrElse { error ->
            return Result.failure(error)
        }

        // Reset each treasure room
        allTreasureRooms.forEach { (spaceId, treasureRoom) ->
            val resetRoom = treasureRoom.reset()
            treasureRoomRepository.save(resetRoom, spaceId).getOrElse { error ->
                return Result.failure(error)
            }
        }

        return Result.success(Unit)
    }

    private fun resolveSpawnSpaceId(worldState: WorldState): String? {
        return worldState.gameProperties["starting_space"]
            ?: worldState.graphNodes.keys.firstOrNull()
            ?: worldState.spaces.keys.firstOrNull()
    }
}
