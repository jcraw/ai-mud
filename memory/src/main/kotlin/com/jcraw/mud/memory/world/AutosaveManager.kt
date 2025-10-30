package com.jcraw.mud.memory.world

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldChunkComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Manages periodic autosave of world state.
 * Prevents data loss with non-intrusive background saves.
 */
class AutosaveManager(
    private val worldPersistence: WorldPersistence,
    private val coroutineScope: CoroutineScope
) {
    private var autosaveJob: Job? = null
    private var moveCounter: Int = 0
    private val autosaveInterval: Duration = 2.minutes
    private val autosaveMovesThreshold: Int = 5

    /**
     * Starts periodic autosave based on time interval.
     * Saves every 2 minutes while the game is running.
     *
     * @param worldId The world root chunk ID
     * @param getPlayerState Function to get current player state
     * @param getLoadedChunks Function to get currently loaded chunks
     * @param getLoadedSpaces Function to get currently loaded spaces
     */
    fun startPeriodicAutosave(
        worldId: String,
        getPlayerState: () -> PlayerState,
        getLoadedChunks: () -> Map<String, WorldChunkComponent>,
        getLoadedSpaces: () -> Map<String, SpacePropertiesComponent>
    ) {
        // Cancel any existing autosave job
        cancelAutosave()

        // Start new autosave coroutine
        autosaveJob = coroutineScope.launch {
            while (true) {
                delay(autosaveInterval)

                // Perform autosave
                performAutosave(
                    worldId = worldId,
                    playerState = getPlayerState(),
                    loadedChunks = getLoadedChunks(),
                    loadedSpaces = getLoadedSpaces()
                )
            }
        }
    }

    /**
     * Triggers autosave on player movement.
     * Saves after every 5 moves.
     *
     * @param worldId The world root chunk ID
     * @param playerState Current player state
     * @param loadedChunks Currently loaded chunks
     * @param loadedSpaces Currently loaded spaces
     */
    suspend fun onPlayerMove(
        worldId: String,
        playerState: PlayerState,
        loadedChunks: Map<String, WorldChunkComponent>,
        loadedSpaces: Map<String, SpacePropertiesComponent>
    ) {
        moveCounter++

        if (moveCounter >= autosaveMovesThreshold) {
            performAutosave(worldId, playerState, loadedChunks, loadedSpaces)
            moveCounter = 0
        }
    }

    /**
     * Performs immediate autosave (bypassing timers).
     * Used for manual save triggers or critical events.
     *
     * @param worldId The world root chunk ID
     * @param playerState Current player state
     * @param loadedChunks Currently loaded chunks
     * @param loadedSpaces Currently loaded spaces
     * @return Result indicating success or failure
     */
    suspend fun performAutosave(
        worldId: String,
        playerState: PlayerState,
        loadedChunks: Map<String, WorldChunkComponent>,
        loadedSpaces: Map<String, SpacePropertiesComponent>
    ): Result<Unit> {
        return worldPersistence.saveWorldState(
            worldId = worldId,
            playerState = playerState,
            loadedChunks = loadedChunks,
            loadedSpaces = loadedSpaces
        )
    }

    /**
     * Cancels the autosave coroutine.
     * Call this on game exit to stop background saves.
     */
    fun cancelAutosave() {
        autosaveJob?.cancel()
        autosaveJob = null
        moveCounter = 0
    }

    /**
     * Resets the move counter (useful after manual saves).
     */
    fun resetMoveCounter() {
        moveCounter = 0
    }

    /**
     * Gets the current move count since last autosave.
     * Useful for UI indicators.
     */
    fun getMoveCount(): Int = moveCounter

    /**
     * Gets the autosave interval duration.
     */
    fun getAutosaveInterval(): Duration = autosaveInterval

    /**
     * Gets the move threshold for autosave.
     */
    fun getMoveThreshold(): Int = autosaveMovesThreshold
}
