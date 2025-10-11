package com.jcraw.mud.core

import kotlinx.coroutines.flow.Flow

/**
 * Interface for game clients to interact with the game engine.
 * Provides pluggable I/O for different UI implementations (console, GUI, network, etc.).
 */
interface GameClient {
    /**
     * Send player input to the game engine.
     */
    suspend fun sendInput(text: String)

    /**
     * Observe game output as a flow of structured events.
     */
    fun observeEvents(): Flow<GameEvent>

    /**
     * Get current player state (for status display, etc.).
     */
    fun getCurrentState(): PlayerState?

    /**
     * Close the client connection and cleanup resources.
     */
    suspend fun close()
}
