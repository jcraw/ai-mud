package com.jcraw.app

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
    private val skillManager: com.jcraw.mud.reasoning.skill.SkillManager,
    private val llmClient: OpenAIClient?
) {
    private lateinit var gameServer: GameServer
    private val intentRecognizer = IntentRecognizer(llmClient)

    /**
     * Start the multi-user game server and run a local player session.
     */
    fun start() = runBlocking {
        // Create fallback components if needed
        val effectiveMemoryManager = memoryManager ?: MultiUserFallbacks.createFallbackMemoryManager()
        val effectiveDescGenerator = descriptionGenerator
            ?: MultiUserFallbacks.createFallbackDescriptionGenerator(effectiveMemoryManager)
        val effectiveNpcGenerator = npcInteractionGenerator
            ?: MultiUserFallbacks.createFallbackNPCGenerator(effectiveMemoryManager)
        val effectiveCombatNarrator = combatNarrator
            ?: MultiUserFallbacks.createFallbackCombatNarrator(effectiveMemoryManager)

        // Initialize game server with social system + item templates (floor take → V2 inventory)
        val socialDatabase = SocialDatabase(com.jcraw.mud.core.DatabaseConfig.SOCIAL_DB)
        val sceneryGenerator = SceneryDescriptionGenerator(llmClient)
        val itemDatabase = com.jcraw.mud.memory.item.ItemDatabase(com.jcraw.mud.core.DatabaseConfig.ITEMS_DB)
        val itemRepository = com.jcraw.mud.memory.item.SQLiteItemRepository(itemDatabase)
        com.jcraw.mud.memory.item.ItemTemplateLoader.loadTemplatesFromResource(itemRepository)
        gameServer = GameServer(
            worldState = initialWorldState,
            memoryManager = effectiveMemoryManager,
            roomDescriptionGenerator = effectiveDescGenerator,
            npcInteractionGenerator = effectiveNpcGenerator,
            combatResolver = combatResolver,
            combatNarrator = effectiveCombatNarrator,
            skillCheckResolver = skillCheckResolver,
            sceneryGenerator = sceneryGenerator,
            skillManager = skillManager,
            socialDatabase = socialDatabase
        )
        gameServer.itemRepository = itemRepository

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
        sendSessionWelcome(session)
        var running = true
        while (running) {
            session.processEvents().forEach { session.sendMessage(it) }
            session.sendMessage("\n[${session.playerName}] > ")
            val input = session.readLine() ?: break
            if (input.trim().isBlank()) continue
            running = processSessionLine(session, input)
        }
    }

    private suspend fun sendSessionWelcome(session: PlayerSession) {
        session.sendMessage("\n" + "=" * 60)
        session.sendMessage("  Welcome, ${session.playerName}!")
        session.sendMessage("=" * 60)
        val worldState = gameServer.getWorldState()
        val initialSpace = worldState.getCurrentSpace(session.playerId)!!
        val description = initialSpace.description.ifBlank { "An unexplored area awaits." }
        val locationName = description.lines().firstOrNull()?.take(50) ?: "Current Location"
        session.sendMessage("\n$locationName")
        session.sendMessage("-" * locationName.length)
        session.sendMessage(description)
        session.sendMessage("\nType 'help' for commands.\n")
    }

    /** @return false when session should end */
    private suspend fun processSessionLine(session: PlayerSession, input: String): Boolean {
        val world = gameServer.getWorldState()
        val space = world.getCurrentSpace(session.playerId)!!
        val graphNode = world.getCurrentGraphNode(session.playerId)!!
        val locationName = space.description.lines().firstOrNull()?.take(50) ?: "Current Location"
        val brightness = if (space.brightness > 30) "lit" else "dark"
        val locationContext = "$locationName: ${space.terrainType}, $brightness"
        val exits = MultiUserFallbacks.buildExitsWithNamesV3(graphNode, world)
        val intent = intentRecognizer.parseIntent(input.trim(), locationContext, exits)
        if (intent is Intent.Quit) {
            session.sendMessage("Goodbye, ${session.playerName}!")
            gameServer.removePlayerSession(session.playerId)
            return false
        }
        session.sendMessage(gameServer.processIntent(session.playerId, intent))
        return true
    }
}
