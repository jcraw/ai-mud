package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.DatabaseConfig
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.world.NavigationState
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.world.*
import com.jcraw.mud.reasoning.procedural.DungeonTheme
import com.jcraw.mud.reasoning.procedural.QuestGenerator
import com.jcraw.mud.reasoning.worldgen.GraphGenerator
import com.jcraw.mud.reasoning.worldgen.GraphValidator
import com.jcraw.sophia.llm.OpenAIClient

/**
 * Result of Ancient Abyss world initialization.
 * Contains everything needed to run the game with the Ancient Abyss dungeon.
 */
data class AncientAbyssWorld(
    val worldState: WorldState,
    val worldDatabase: WorldDatabase,
    val llmClient: OpenAIClient,
    val worldGenerator: WorldGenerator,
    val navigationState: NavigationState,
    val dungeonTheme: DungeonTheme = DungeonTheme.CRYPT
)

/**
 * Shared helper for initializing the Ancient Abyss mega-dungeon.
 * Used by console app, GUI client, and testbots to ensure consistent world initialization.
 *
 * This encapsulates the complex setup of:
 * - 6 database repositories (world seed, chunks, spaces, treasure rooms, graph nodes, entities)
 * - 5 generators (lore engine, graph generator, validator, world generator, quest generator)
 * - 3 specialized builders (town, boss, hidden exit placer)
 * - DungeonInitializer, AncientAbyssStarter, WorldStateSeeder
 *
 * @param apiKey OpenAI API key for LLM-powered generation
 * @param playerId Player ID for the initial player (defaults to "player_cli")
 * @param playerName Player name (defaults to "Adventurer")
 * @param dbPath Optional custom database path (defaults to DatabaseConfig.WORLD_DB)
 * @param includeQuests Whether to generate initial quest pool (defaults to true)
 * @return AncientAbyssWorld containing initialized WorldState and all components
 */
suspend fun initializeAncientAbyssWorld(
    apiKey: String,
    playerId: String = "player_cli",
    playerName: String = "Adventurer",
    dbPath: String = DatabaseConfig.WORLD_DB,
    includeQuests: Boolean = true
): Result<AncientAbyssWorld> = runCatching {
    // Ensure data directory exists
    DatabaseConfig.init()

    // Initialize LLM and database
    val llmClient = OpenAIClient(apiKey)
    val worldDatabase = WorldDatabase(dbPath)

    // Create 6 repositories
    val worldSeedRepository = SQLiteWorldSeedRepository(worldDatabase)
    val worldChunkRepository = SQLiteWorldChunkRepository(worldDatabase)
    val spacePropertiesRepository = SQLiteSpacePropertiesRepository(worldDatabase)
    val treasureRoomRepository = SQLiteTreasureRoomRepository(worldDatabase)
    val graphNodeRepository = SQLiteGraphNodeRepository(worldDatabase)
    val spaceEntityRepository = SQLiteSpaceEntityRepository(worldDatabase)

    // Create 5 generators
    val loreEngine = LoreInheritanceEngine(llmClient)
    val graphGenerator = GraphGenerator(
        rng = kotlin.random.Random.Default,
        difficultyLevel = 1
    )
    val graphValidator = GraphValidator()
    val worldGenerator = WorldGenerator(
        llmClient = llmClient,
        loreEngine = loreEngine,
        graphGenerator = graphGenerator,
        graphValidator = graphValidator,
        memoryManager = MemoryManager(llmClient)
    )

    // Create 3 specialized builders
    val townGenerator = TownGenerator(
        worldGenerator,
        worldChunkRepository,
        spacePropertiesRepository,
        spaceEntityRepository,
        treasureRoomRepository,
        graphNodeRepository
    )
    val bossGenerator = BossGenerator(worldGenerator, spacePropertiesRepository)
    val hiddenExitPlacer = HiddenExitPlacer(
        worldGenerator,
        worldChunkRepository,
        spacePropertiesRepository
    )

    // Create dungeon initializer
    val dungeonInitializer = DungeonInitializer(
        worldGenerator,
        worldSeedRepository,
        worldChunkRepository,
        spacePropertiesRepository,
        townGenerator,
        bossGenerator,
        hiddenExitPlacer,
        graphNodeRepository,
        treasureRoomRepository
    )

    // Ensure Ancient Abyss exists (create or load)
    val abyssStarter = AncientAbyssStarter(
        worldSeedRepository,
        worldChunkRepository,
        dungeonInitializer
    )
    val abyssStart = abyssStarter.ensureAncientAbyss().getOrThrow()

    // Create world state seeder
    val worldStateSeeder = WorldStateSeeder(
        worldChunkRepository,
        graphNodeRepository,
        spacePropertiesRepository,
        treasureRoomRepository,
        spaceEntityRepository,
        worldGenerator
    )

    // Create initial player
    val player = PlayerState(
        id = playerId,
        name = playerName,
        currentRoomId = abyssStart.startingSpaceId,
        inventoryComponent = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 75,
            capacityWeight = 50.0
        )
    )

    // Create base WorldState
    var worldState = WorldState(
        players = mapOf(player.id to player)
    )
    worldState = worldState.copy(
        gameProperties = worldState.gameProperties + ("starting_space" to abyssStart.startingSpaceId)
    )

    // Seed world state with persisted chunks/spaces/nodes
    worldState = worldStateSeeder.seedWorldState(
        worldState,
        abyssStart.startingSpaceId,
        onWarning = { /* Silent in helper, caller can log if needed */ },
        onError = { /* Silent in helper, caller can log if needed */ }
    )

    // Generate initial quests if requested
    if (includeQuests) {
        val dungeonTheme = DungeonTheme.CRYPT
        val questGenerator = QuestGenerator()
        val initialQuests = questGenerator.generateQuestPool(worldState, dungeonTheme, count = 3)
        initialQuests.forEach { quest ->
            worldState = worldState.addAvailableQuest(quest)
        }
    }

    AncientAbyssWorld(
        worldState = worldState,
        worldDatabase = worldDatabase,
        llmClient = llmClient,
        worldGenerator = worldGenerator,
        navigationState = abyssStart.navigationState
    )
}
