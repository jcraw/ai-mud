package com.jcraw.app

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.perception.IntentRecognizer
import com.jcraw.mud.reasoning.RoomDescriptionGenerator
import com.jcraw.mud.reasoning.SceneryDescriptionGenerator
import com.jcraw.mud.reasoning.NPCInteractionGenerator
import com.jcraw.mud.reasoning.CombatResolver
import com.jcraw.mud.reasoning.CombatNarrator
import com.jcraw.mud.reasoning.SkillCheckResolver
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.social.SocialDatabase
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.coroutines.runBlocking

/**
 * Multi-user game mode that runs GameServer and manages multiple player sessions.
 *
 * This class demonstrates the multi-user architecture with a local console player.
 * Future versions will support network connections for true multi-player gameplay.
 */
class MultiUserGame(
    private val initialWorldState: WorldState,
    private val descriptionGenerator: RoomDescriptionGenerator?,
    private val npcInteractionGenerator: NPCInteractionGenerator?,
    private val combatNarrator: CombatNarrator?,
    private val memoryManager: MemoryManager?,
    private val combatResolver: CombatResolver,
    private val skillCheckResolver: SkillCheckResolver,
    private val llmClient: OpenAIClient?
) {
    private lateinit var gameServer: GameServer
    private val intentRecognizer = IntentRecognizer(llmClient)

    /**
     * Start the multi-user game server and run a local player session.
     */
    fun start() = runBlocking {
        // Create fallback components if needed
        val effectiveMemoryManager = memoryManager ?: createFallbackMemoryManager()
        val effectiveDescGenerator = descriptionGenerator ?: createFallbackDescriptionGenerator(effectiveMemoryManager)
        val effectiveNpcGenerator = npcInteractionGenerator ?: createFallbackNPCGenerator(effectiveMemoryManager)
        val effectiveCombatNarrator = combatNarrator ?: createFallbackCombatNarrator(effectiveMemoryManager)

        // Initialize game server with social system
        val socialDatabase = SocialDatabase(com.jcraw.mud.core.DatabaseConfig.SOCIAL_DB)
        val sceneryGenerator = SceneryDescriptionGenerator(llmClient)
        gameServer = GameServer(
            worldState = initialWorldState,
            memoryManager = effectiveMemoryManager,
            roomDescriptionGenerator = effectiveDescGenerator,
            npcInteractionGenerator = effectiveNpcGenerator,
            combatResolver = combatResolver,
            combatNarrator = effectiveCombatNarrator,
            skillCheckResolver = skillCheckResolver,
            sceneryGenerator = sceneryGenerator,
            socialDatabase = socialDatabase
        )

        println("\n🎮 Multi-User Mode Enabled")
        println("=" * 60)
        println("This mode uses the multi-user server architecture.")
        println("Future versions will support network connections for true multi-player.")
        println("\nEnter your player name: ")

        val playerName = readLine()?.trim()?.ifBlank { "Adventurer" } ?: "Adventurer"

        println("\n🌟 Starting game for $playerName...")
        println("=" * 60)

        // V3: Get starting location from first space
        val startingLocationId = initialWorldState.graphNodes.keys.first()

        // Create player session
        val playerId = "player_main"
        val session = PlayerSession(
            playerId = playerId,
            playerName = playerName,
            input = System.`in`.bufferedReader(),
            output = System.out.writer().buffered().let { java.io.PrintWriter(it) }
        )

        gameServer.addPlayerSession(session, startingLocationId)

        // Run session
        runPlayerSession(session)

        println("\n\n🎮 Game ended. Thanks for playing!")
    }

    /**
     * Run a player session, processing input and events.
     */
    private suspend fun runPlayerSession(session: PlayerSession) {
        var running = true

        // Welcome message
        session.sendMessage("\n" + "=" * 60)
        session.sendMessage("  Welcome, ${session.playerName}!")
        session.sendMessage("=" * 60)

        // V3: Show initial location
        val worldState = gameServer.getWorldState()
        val initialSpace = worldState.getCurrentSpace(session.playerId)!!
        val description = initialSpace.description.ifBlank {
            "An unexplored area awaits."
        }
        val locationName = description.lines().firstOrNull()?.take(50) ?: "Current Location"
        session.sendMessage("\n$locationName")
        session.sendMessage("-" * locationName.length)
        session.sendMessage(description)

        session.sendMessage("\nType 'help' for commands.\n")

        while (running) {
            // Process any pending events
            val events = session.processEvents()
            events.forEach { session.sendMessage(it) }

            // Show prompt
            session.sendMessage("\n[${session.playerName}] > ")

            // Read input
            val input = session.readLine() ?: break
            if (input.trim().isBlank()) continue

            // V3: Parse intent using LLM with space + graph node
            val currentWorldState = gameServer.getWorldState()
            val space = currentWorldState.getCurrentSpace(session.playerId)!!
            val graphNode = currentWorldState.getCurrentGraphNode(session.playerId)!!

            val locationName = space.description.lines().firstOrNull()?.take(50) ?: "Current Location"
            val brightness = if (space.brightness > 30) "lit" else "dark"
            val locationContext = "$locationName: ${space.terrainType}, $brightness"
            val exitsWithNames = buildExitsWithNamesV3(graphNode, currentWorldState)

            val intent = intentRecognizer.parseIntent(input.trim(), locationContext, exitsWithNames)

            // Check for quit
            if (intent is Intent.Quit) {
                session.sendMessage("Goodbye, ${session.playerName}!")
                running = false
                gameServer.removePlayerSession(session.playerId)
                break
            }

            // Process intent through game server
            val response = gameServer.processIntent(session.playerId, intent)
            session.sendMessage(response)
        }
    }

    /**
     * Build a map of exits with their destination space names for navigation parsing (V3).
     */
    private fun buildExitsWithNamesV3(
        graphNode: GraphNodeComponent,
        worldState: WorldState
    ): Map<Direction, String> {
        return graphNode.neighbors.mapNotNull { edge ->
            val destSpace = worldState.getSpace(edge.targetId)
            val direction = Direction.fromString(edge.direction)
            if (destSpace != null && direction != null) {
                // Extract name from first line of destination description
                val destName = destSpace.description.lines().firstOrNull()?.take(50) ?: "Unknown"
                direction to destName
            } else {
                null
            }
        }.toMap()
    }

    /**
     * Create a fallback memory manager for when no LLM client is available.
     */
    private fun createFallbackMemoryManager(): MemoryManager {
        // Create memory manager with null client (will use in-memory store only)
        return MemoryManager(null)
    }

    /**
     * Create a fallback description generator with a mock client.
     */
    private fun createFallbackDescriptionGenerator(memoryManager: MemoryManager): RoomDescriptionGenerator {
        // Create a simple mock LLM client that always throws to trigger fallback logic
        val mockClient = object : com.jcraw.sophia.llm.LLMClient {
            override suspend fun chatCompletion(
                modelId: String,
                systemPrompt: String,
                userContext: String,
                maxTokens: Int,
                temperature: Double
            ): com.jcraw.sophia.llm.OpenAIResponse {
                throw UnsupportedOperationException("Mock client - fallback mode")
            }

            override suspend fun createEmbedding(text: String, model: String): List<Double> {
                return emptyList()
            }

            override fun close() {
                // No-op for mock
            }
        }
        return RoomDescriptionGenerator(mockClient, memoryManager)
    }

    /**
     * Create a fallback NPC interaction generator with a mock client.
     */
    private fun createFallbackNPCGenerator(memoryManager: MemoryManager): NPCInteractionGenerator {
        val mockClient = object : com.jcraw.sophia.llm.LLMClient {
            override suspend fun chatCompletion(
                modelId: String,
                systemPrompt: String,
                userContext: String,
                maxTokens: Int,
                temperature: Double
            ): com.jcraw.sophia.llm.OpenAIResponse {
                throw UnsupportedOperationException("Mock client - fallback mode")
            }

            override suspend fun createEmbedding(text: String, model: String): List<Double> {
                return emptyList()
            }

            override fun close() {
                // No-op for mock
            }
        }
        return NPCInteractionGenerator(mockClient, memoryManager)
    }

    /**
     * Create a fallback combat narrator with a mock client.
     */
    private fun createFallbackCombatNarrator(memoryManager: MemoryManager): CombatNarrator {
        val mockClient = object : com.jcraw.sophia.llm.LLMClient {
            override suspend fun chatCompletion(
                modelId: String,
                systemPrompt: String,
                userContext: String,
                maxTokens: Int,
                temperature: Double
            ): com.jcraw.sophia.llm.OpenAIResponse {
                throw UnsupportedOperationException("Mock client - fallback mode")
            }

            override suspend fun createEmbedding(text: String, model: String): List<Double> {
                return emptyList()
            }

            override fun close() {
                // No-op for mock
            }
        }
        return CombatNarrator(mockClient, memoryManager)
    }
}
