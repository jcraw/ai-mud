package com.jcraw.app

import com.jcraw.mud.core.SampleDungeon
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.Room
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

    // Ask user for dungeon type
    println("Select dungeon type:")
    println("  1. Sample Dungeon (handcrafted, 6 rooms)")
    println("  2. World Generation V2 (procedural deep dungeon)")
    print("\nEnter choice (1-2) [default: 1]: ")

    val dungeonChoice = readLine()?.trim() ?: "1"
    val useWorldGenV2 = dungeonChoice == "2"

    println()

    val worldState: WorldState
    val dungeonTheme: DungeonTheme
    var startingSpaceId: String? = null // For World V2

    if (useWorldGenV2) {
        println("Initializing procedural deep dungeon...")
        println("(This may take a moment as we generate the world...)")

        // World generation requires LLM
        if (apiKey.isNullOrBlank()) {
            println("⚠️  World Generation V2 requires OpenAI API key")
            println("   Falling back to Sample Dungeon")
            worldState = SampleDungeon.createInitialWorldState()
            dungeonTheme = DungeonTheme.CRYPT
        } else {
            // Initialize world generation components
            val llmClient = OpenAIClient(apiKey)
            val worldDatabase = com.jcraw.mud.memory.world.WorldDatabase(com.jcraw.mud.core.DatabaseConfig.WORLD_DB)
            val worldSeedRepo = com.jcraw.mud.memory.world.SQLiteWorldSeedRepository(worldDatabase)
            val chunkRepo = com.jcraw.mud.memory.world.SQLiteWorldChunkRepository(worldDatabase)
            val spaceRepo = com.jcraw.mud.memory.world.SQLiteSpacePropertiesRepository(worldDatabase)
            val entityRepo = com.jcraw.mud.memory.world.SQLiteSpaceEntityRepository(worldDatabase)

            val loreEngine = com.jcraw.mud.reasoning.world.LoreInheritanceEngine(llmClient)
            val worldGenerator = com.jcraw.mud.reasoning.world.WorldGenerator(llmClient, loreEngine)
            val townGenerator = com.jcraw.mud.reasoning.world.TownGenerator(worldGenerator, chunkRepo, spaceRepo, entityRepo)
            val bossGenerator = com.jcraw.mud.reasoning.world.BossGenerator(worldGenerator, spaceRepo)
            val hiddenExitPlacer = com.jcraw.mud.reasoning.world.HiddenExitPlacer(worldGenerator, chunkRepo, spaceRepo)
            val dungeonInitializer = com.jcraw.mud.reasoning.world.DungeonInitializer(
                worldGenerator, worldSeedRepo, chunkRepo, spaceRepo, townGenerator, bossGenerator, hiddenExitPlacer
            )

            // Generate world (this is async)
            val seed = "seed_${System.currentTimeMillis()}"
            startingSpaceId = kotlinx.coroutines.runBlocking {
                dungeonInitializer.initializeDeepDungeon(seed).getOrThrow()
            }

            println("✓ World generated! Starting space ID: $startingSpaceId")

            // Create minimal world state (world V2 uses different navigation)
            // For now, create empty world state - navigation will use world V2 system
            val player = com.jcraw.mud.core.PlayerState(
                id = "player_1",
                name = "Adventurer",
                currentRoomId = "world_v2_start" // Placeholder
            )
            worldState = WorldState(
                rooms = emptyMap<String, Room>(), // World V2 uses SpacePropertiesRepository instead
                players = mapOf(player.id to player)
            )
            dungeonTheme = DungeonTheme.CRYPT // Default theme
        }
    } else {
        println("Loading Sample Dungeon (handcrafted, 6 rooms)...")
        worldState = SampleDungeon.createInitialWorldState()
        dungeonTheme = DungeonTheme.CRYPT // Sample uses crypt theme
    }

    // Generate initial quests
    val questGenerator = QuestGenerator()
    val initialQuests = questGenerator.generateQuestPool(worldState, dungeonTheme, count = 3)
    var worldStateWithQuests = worldState
    initialQuests.forEach { quest ->
        worldStateWithQuests = worldStateWithQuests.addAvailableQuest(quest)
    }

    println()

    // Initialize LLM components if API key is available
    val llmClient = if (!apiKey.isNullOrBlank()) {
        println("✅ Using LLM-powered descriptions, NPC dialogue, combat narration, and RAG memory\n")
        OpenAIClient(apiKey)
    } else {
        println("⚠️  OpenAI API key not found - using simple fallback mode")
        println("   Set OPENAI_API_KEY environment variable or openai.api.key in local.properties\n")
        null
    }

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

        // Initialize navigation state for World V2
        if (startingSpaceId != null) {
            game.navigationState = kotlinx.coroutines.runBlocking {
                com.jcraw.mud.core.world.NavigationState.fromSpaceId(
                    startingSpaceId,
                    game.worldChunkRepository
                ).getOrThrow()
            }
            println("✓ Navigation initialized at starting space")
        }

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
