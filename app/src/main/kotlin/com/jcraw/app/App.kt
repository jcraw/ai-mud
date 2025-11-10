package com.jcraw.app

import com.jcraw.mud.core.WorldState
import com.jcraw.mud.reasoning.RoomDescriptionGenerator
import com.jcraw.mud.reasoning.NPCInteractionGenerator
import com.jcraw.mud.reasoning.CombatResolver
import com.jcraw.mud.reasoning.CombatNarrator
import com.jcraw.mud.reasoning.SkillCheckResolver
import com.jcraw.mud.reasoning.procedural.DungeonTheme
import com.jcraw.mud.reasoning.procedural.QuestGenerator
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.sophia.llm.OpenAIClient

/**
 * Main game application - console-based MUD interface
 */
fun main() {
    // Initialize shared database configuration
    com.jcraw.mud.core.DatabaseConfig.init()

    // Get OpenAI API key from environment or system property (from local.properties)
    val apiKey = System.getenv("OPENAI_API_KEY")
        ?: System.getProperty("openai.api.key")
        ?: loadApiKeyFromLocalProperties()

    println("=" * 60)
    println("  AI-Powered MUD - Alpha Version")
    println("=" * 60)

    // Ask user for game mode
    println("\nSelect game mode:")
    println("  1. Single-player mode")
    println("  2. Multi-user mode (local)")
    print("\nEnter choice (1-2) [default: 1]: ")

    val modeChoice = readLine()?.trim() ?: "1"
    val isMultiUser = modeChoice == "2"

    println()

    if (apiKey.isNullOrBlank()) {
        println("⚠️  The Ancient Abyss requires an OpenAI API key.")
        println("    Set OPENAI_API_KEY or add openai.api.key to local.properties.")
        return
    }

    println("Initializing the Ancient Abyss (graph-based mega-dungeon)...")
    println("(This may take a moment on first run while the world is generated.)")
    val llmClient = OpenAIClient(apiKey)
    val worldDatabase = com.jcraw.mud.memory.world.WorldDatabase(com.jcraw.mud.core.DatabaseConfig.WORLD_DB)
    val worldSeedRepository = com.jcraw.mud.memory.world.SQLiteWorldSeedRepository(worldDatabase)
    val worldChunkRepository = com.jcraw.mud.memory.world.SQLiteWorldChunkRepository(worldDatabase)
    val spacePropertiesRepository = com.jcraw.mud.memory.world.SQLiteSpacePropertiesRepository(worldDatabase)
    val graphNodeRepository = com.jcraw.mud.memory.world.SQLiteGraphNodeRepository(worldDatabase)
    val spaceEntityRepository = com.jcraw.mud.memory.world.SQLiteSpaceEntityRepository(worldDatabase)

    val loreEngine = com.jcraw.mud.reasoning.world.LoreInheritanceEngine(llmClient)
    val graphGenerator = com.jcraw.mud.reasoning.worldgen.GraphGenerator(
        rng = kotlin.random.Random.Default,
        difficultyLevel = 1
    )
    val graphValidator = com.jcraw.mud.reasoning.worldgen.GraphValidator()
    val worldGenerator = com.jcraw.mud.reasoning.world.WorldGenerator(
        llmClient = llmClient,
        loreEngine = loreEngine,
        graphGenerator = graphGenerator,
        graphValidator = graphValidator,
        memoryManager = MemoryManager(llmClient)
    )

    val townGenerator = com.jcraw.mud.reasoning.world.TownGenerator(
        worldGenerator,
        worldChunkRepository,
        spacePropertiesRepository,
        spaceEntityRepository
    )
    val bossGenerator = com.jcraw.mud.reasoning.world.BossGenerator(worldGenerator, spacePropertiesRepository)
    val hiddenExitPlacer = com.jcraw.mud.reasoning.world.HiddenExitPlacer(
        worldGenerator,
        worldChunkRepository,
        spacePropertiesRepository
    )
    val dungeonInitializer = com.jcraw.mud.reasoning.world.DungeonInitializer(
        worldGenerator,
        worldSeedRepository,
        worldChunkRepository,
        spacePropertiesRepository,
        townGenerator,
        bossGenerator,
        hiddenExitPlacer,
        graphNodeRepository
    )
    val abyssStarter = com.jcraw.mud.reasoning.world.AncientAbyssStarter(
        worldSeedRepository,
        worldChunkRepository,
        dungeonInitializer
    )
    val worldStateSeeder = com.jcraw.mud.reasoning.world.WorldStateSeeder(
        worldChunkRepository,
        graphNodeRepository,
        spacePropertiesRepository,
        worldGenerator
    )

    val abyssStart = kotlinx.coroutines.runBlocking {
        abyssStarter.ensureAncientAbyss().getOrElse {
            println("⚠️  Failed to prepare the Ancient Abyss: ${it.message}")
            return@runBlocking null
        }
    } ?: return
    val navigationState = abyssStart.navigationState

    val player = com.jcraw.mud.core.PlayerState(
        id = "player_cli",
        name = "Adventurer",
        currentRoomId = abyssStart.startingSpaceId
    )

    var worldState: WorldState = WorldState(
        players = mapOf(player.id to player)
    )
    worldState = worldState.copy(
        gameProperties = worldState.gameProperties + ("starting_space" to abyssStart.startingSpaceId)
    )
    worldState = worldStateSeeder.seedWorldState(
        worldState,
        abyssStart.startingSpaceId,
        onWarning = { println("⚠️  $it") },
        onError = { println("⚠️  $it") }
    )
    val dungeonTheme = DungeonTheme.CRYPT

    // Generate initial quests
    val questGenerator = QuestGenerator()
    val initialQuests = questGenerator.generateQuestPool(worldState, dungeonTheme, count = 3)
    var worldStateWithQuests = worldState
    initialQuests.forEach { quest ->
        worldStateWithQuests = worldStateWithQuests.addAvailableQuest(quest)
    }

    println()
    println("✅ Using LLM-powered descriptions, NPC dialogue, combat narration, and RAG memory\n")

    if (isMultiUser) {
        // Multi-user mode
        val memoryManager = llmClient?.let { MemoryManager(it) }
        val descriptionGenerator = llmClient?.let { RoomDescriptionGenerator(it, memoryManager!!) }
        val npcInteractionGenerator = llmClient?.let { NPCInteractionGenerator(it, memoryManager!!) }
        val combatNarrator = llmClient?.let { CombatNarrator(it, memoryManager!!) }
        val skillCheckResolver = SkillCheckResolver()
        val combatResolver = CombatResolver()

        val multiUserGame = MultiUserGame(
            initialWorldState = worldStateWithQuests,
            descriptionGenerator = descriptionGenerator,
            npcInteractionGenerator = npcInteractionGenerator,
            combatNarrator = combatNarrator,
            memoryManager = memoryManager,
            combatResolver = combatResolver,
            skillCheckResolver = skillCheckResolver,
            llmClient = llmClient
        )

        multiUserGame.start()
    } else {
        // Single-player mode
        val game = if (llmClient != null) {
            val memoryManager = MemoryManager(llmClient)
            val descriptionGenerator = RoomDescriptionGenerator(llmClient, memoryManager)
            val npcInteractionGenerator = NPCInteractionGenerator(llmClient, memoryManager)
            val combatNarrator = CombatNarrator(llmClient, memoryManager)
            MudGame(
                initialWorldState = worldStateWithQuests,
                descriptionGenerator = descriptionGenerator,
                npcInteractionGenerator = npcInteractionGenerator,
                combatNarrator = combatNarrator,
                memoryManager = memoryManager,
                llmClient = llmClient
            )
        } else {
            MudGame(
                initialWorldState = worldStateWithQuests,
                descriptionGenerator = null,
                npcInteractionGenerator = null,
                combatNarrator = null,
                memoryManager = null,
                llmClient = null
            )
        }

        // Initialize navigation state for Ancient Abyss runs
        game.navigationState = navigationState
        println("✓ Navigation initialized at starting space")

        game.start()
    }
}

/**
 * Load API key from local.properties file.
 * This allows the app to work when run directly from IDE.
 * Checks both current directory and project root.
 */
private fun loadApiKeyFromLocalProperties(): String? {
    // Try current directory first
    var localPropertiesFile = java.io.File("local.properties")

    // If not found, try project root (when run via Gradle from submodule)
    if (!localPropertiesFile.exists()) {
        localPropertiesFile = java.io.File("../local.properties")
    }

    // If still not found, try going up two levels (just in case)
    if (!localPropertiesFile.exists()) {
        localPropertiesFile = java.io.File("../../local.properties")
    }

    if (!localPropertiesFile.exists()) {
        return null
    }

    return try {
        localPropertiesFile.readLines()
            .firstOrNull { it.trim().startsWith("openai.api.key=") }
            ?.substringAfter("openai.api.key=")
            ?.trim()
    } catch (e: Exception) {
        null
    }
}
