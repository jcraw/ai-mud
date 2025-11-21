package com.jcraw.mud.core

/**
 * Interface for interacting with the game engine in a test environment.
 * Allows the test bot to send inputs and receive outputs without using stdio.
 */
interface GameEngineInterface {
    /**
     * Process a player input and return the game's response.
     */
    suspend fun processInput(input: String): String

    /**
     * Get the current world state (for validation and context).
     */
    fun getWorldState(): WorldState

    /**
     * Reset the game to initial state.
     */
    fun reset()

    /**
     * Check if the game is still running.
     */
    fun isRunning(): Boolean
}
