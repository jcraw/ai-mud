package com.jcraw.app

import com.jcraw.mud.core.repository.CorpseRepository
import com.jcraw.mud.reasoning.death.cleanupDecayedCorpses
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Periodic corpse decay cleanup scheduler.
 *
 * Runs cleanup every N turns to:
 * - Find corpses past their decay timer
 * - Delete them from database
 * - Prevent database bloat
 *
 * Design:
 * - Kotlin coroutine-based scheduling
 * - Configurable delay (default: check every time gameTime advances 100 turns)
 * - Cancellable on game shutdown
 * - Non-blocking (runs in background)
 *
 * Usage:
 * ```kotlin
 * val scheduler = CorpseDecayScheduler(corpseRepository)
 * scheduler.start(gameTimeSupplier = { worldState.gameTime })
 * // ... game runs ...
 * scheduler.stop() // On game exit
 * ```
 */
class CorpseDecayScheduler(
    private val corpseRepository: CorpseRepository,
    private val cleanupIntervalTurns: Long = 100L
) {
    private var cleanupJob: Job? = null
    private var lastCleanupTime: Long = 0L

    /**
     * Start the decay cleanup scheduler.
     *
     * Runs periodic cleanup based on game time advancement.
     * Non-blocking - runs in background coroutine.
     *
     * @param scope Coroutine scope for scheduling
     * @param gameTimeSupplier Function to get current game time
     * @param pollInterval How often to check if cleanup is needed (real time)
     */
    fun start(
        scope: CoroutineScope,
        gameTimeSupplier: () -> Long,
        pollInterval: Duration = 5.seconds
    ) {
        // Cancel any existing job
        stop()

        // Start periodic cleanup job
        cleanupJob = scope.launch {
            while (isActive) {
                val currentGameTime = gameTimeSupplier()

                // Check if enough game time has passed since last cleanup
                if (currentGameTime - lastCleanupTime >= cleanupIntervalTurns) {
                    performCleanup(currentGameTime)
                    lastCleanupTime = currentGameTime
                }

                // Poll periodically (real time delay, not game time)
                delay(pollInterval)
            }
        }
    }

    /**
     * Stop the decay cleanup scheduler.
     *
     * Cancels background coroutine.
     * Should be called on game shutdown to prevent memory leaks.
     */
    fun stop() {
        cleanupJob?.cancel()
        cleanupJob = null
    }

    /**
     * Perform cleanup immediately (for manual triggering).
     *
     * Can be called directly from game loop if preferred over coroutine scheduling.
     *
     * @param currentGameTime Current game time
     * @return Number of corpses deleted, or null if cleanup failed
     */
    fun performCleanupNow(currentGameTime: Long): Int? {
        return performCleanup(currentGameTime)
    }

    /**
     * Internal cleanup implementation.
     *
     * @param currentGameTime Current game time
     * @return Number of corpses deleted, or null if cleanup failed
     */
    private fun performCleanup(currentGameTime: Long): Int? {
        val result = cleanupDecayedCorpses(currentGameTime, corpseRepository)

        return result.getOrElse { error ->
            System.err.println("Corpse cleanup failed: ${error.message}")
            null
        }
    }

    /**
     * Check if scheduler is currently running.
     *
     * @return True if background job is active
     */
    fun isRunning(): Boolean {
        return cleanupJob?.isActive == true
    }

    /**
     * Get time until next cleanup.
     *
     * @param currentGameTime Current game time
     * @return Turns remaining until next cleanup
     */
    fun turnsUntilNextCleanup(currentGameTime: Long): Long {
        val turnsSinceLastCleanup = currentGameTime - lastCleanupTime
        return (cleanupIntervalTurns - turnsSinceLastCleanup).coerceAtLeast(0L)
    }
}

/**
 * Manual cleanup helper for non-coroutine contexts.
 *
 * Performs immediate cleanup without scheduling.
 * Useful for:
 * - Console games without coroutine support
 * - Manual cleanup triggers
 * - Testing
 *
 * @param currentGameTime Current game time
 * @param corpseRepository Repository for corpse operations
 * @param lastCleanupTime Last time cleanup was performed (mutable)
 * @param cleanupIntervalTurns Minimum turns between cleanups
 * @return Number of corpses deleted, or null if not enough time has passed
 */
fun performManualCleanupIfNeeded(
    currentGameTime: Long,
    corpseRepository: CorpseRepository,
    lastCleanupTime: Long,
    cleanupIntervalTurns: Long = 100L
): Pair<Int?, Long> {
    // Check if enough time has passed
    if (currentGameTime - lastCleanupTime < cleanupIntervalTurns) {
        return null to lastCleanupTime // Not time yet
    }

    // Perform cleanup
    val deletedCount = cleanupDecayedCorpses(currentGameTime, corpseRepository).getOrNull()

    // Update last cleanup time
    val newLastCleanupTime = if (deletedCount != null) currentGameTime else lastCleanupTime

    return deletedCount to newLastCleanupTime
}
