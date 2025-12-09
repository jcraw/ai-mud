package com.jcraw.mud.testbot

import com.jcraw.mud.core.DatabaseConfig
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.world.NavigationState
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.world.*
import com.jcraw.mud.reasoning.procedural.DungeonTheme
import com.jcraw.mud.reasoning.worldgen.GraphGenerator
import com.jcraw.mud.reasoning.worldgen.GraphValidator
import com.jcraw.mud.reasoning.world.*
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.coroutines.runBlocking

/**
 * Test helper for creating V3 worlds for automated testing.
 * Replaces the deprecated SampleDungeon with V3 Ancient Abyss initialization.
 */
object V3TestWorldHelper {

    /**
     * Creates an initialized V3 world state for testing.
     * Uses a simplified Ancient Abyss setup for consistent test environments.
     *
     * @param apiKey OpenAI API key (required for LLM-powered generation)
     * @param playerId Optional custom player ID (defaults to "test_player")
     * @param playerName Optional custom player name (defaults to "Test Bot")
     * @return Initialized WorldState with Ancient Abyss dungeon
     */
    fun createInitialWorldState(
        apiKey: String,
        playerId: String = "test_player",
        playerName: String = "Test Bot"
    ): WorldState = runBlocking {
        val result = com.jcraw.mud.reasoning.world.initializeAncientAbyssWorld(
            apiKey = apiKey,
            playerId = playerId,
            playerName = playerName,
            includeQuests = true
        )

        result.getOrElse {
            throw IllegalStateException("Failed to initialize V3 test world: ${it.message}", it)
        }.worldState
    }

    /**
     * Creates an initialized V3 world with all supporting components.
     * Returns a data class containing the world state, database, LLM client, etc.
     *
     * @param apiKey OpenAI API key (required for LLM-powered generation)
     * @param playerId Optional custom player ID (defaults to "test_player")
     * @param playerName Optional custom player name (defaults to "Test Bot")
     * @return AncientAbyssWorld with all components
     */
    suspend fun createFullTestWorld(
        apiKey: String,
        playerId: String = "test_player",
        playerName: String = "Test Bot"
    ): AncientAbyssWorld {
        val result = com.jcraw.mud.reasoning.world.initializeAncientAbyssWorld(
            apiKey = apiKey,
            playerId = playerId,
            playerName = playerName,
            includeQuests = true
        )

        return result.getOrElse {
            throw IllegalStateException("Failed to initialize V3 test world: ${it.message}", it)
        }
    }
}
