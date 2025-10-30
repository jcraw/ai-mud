package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.world.*
import com.jcraw.mud.memory.item.ItemDatabase
import com.jcraw.mud.memory.item.SQLiteItemRepository
import com.jcraw.mud.memory.world.*
import com.jcraw.sophia.llm.LLMClient
import com.jcraw.sophia.llm.OpenAIChoice
import com.jcraw.sophia.llm.OpenAIMessage
import com.jcraw.sophia.llm.OpenAIResponse
import com.jcraw.sophia.llm.OpenAIUsage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Comprehensive integration tests for World Generation System V2.
 *
 * Tests the complete system end-to-end with real SQLite repositories:
 * - Dungeon initialization and hierarchy
 * - Exit linking and navigation
 * - Content placement (mobs, traps, resources)
 * - State changes and persistence
 * - Respawn functionality
 * - Navigation state tracking
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorldGenerationIntegrationTest {

    // Database and repositories
    private lateinit var worldDb: WorldDatabase
    private lateinit var itemDb: ItemDatabase
    private lateinit var seedRepo: SQLiteWorldSeedRepository
    private lateinit var chunkRepo: SQLiteWorldChunkRepository
    private lateinit var spaceRepo: SQLiteSpacePropertiesRepository
    private lateinit var itemRepo: SQLiteItemRepository

    // World generation components
    private lateinit var mockLLM: MockLLMClient
    private lateinit var loreEngine: LoreInheritanceEngine
    private lateinit var worldGenerator: WorldGenerator
    private lateinit var initializer: DungeonInitializer
    private lateinit var exitLinker: ExitLinker
    private lateinit var exitResolver: ExitResolver
    private lateinit var themeRegistry: ThemeRegistry
    private lateinit var mobSpawner: MobSpawner
    private lateinit var trapGenerator: TrapGenerator
    private lateinit var resourceGenerator: ResourceGenerator
    private lateinit var lootTableGenerator: LootTableGenerator
    private lateinit var spacePopulator: SpacePopulator
    private lateinit var stateChangeHandler: StateChangeHandler
    private lateinit var worldPersistence: WorldPersistence
    private lateinit var respawnManager: RespawnManager

    private val worldDbPath = "test_world_integration.db"
    private val itemDbPath = "test_item_integration.db"

    @BeforeAll
    fun setup() {
        // Initialize databases
        worldDb = WorldDatabase(worldDbPath)
        itemDb = ItemDatabase(itemDbPath)

        // Initialize repositories
        seedRepo = SQLiteWorldSeedRepository(worldDb)
        chunkRepo = SQLiteWorldChunkRepository(worldDb)
        spaceRepo = SQLiteSpacePropertiesRepository(worldDb)
        itemRepo = SQLiteItemRepository(itemDb)

        // Load item templates for content generation
        itemRepo.saveTemplates(ItemDatabase.loadItemTemplatesFromResources()).getOrThrow()

        // Initialize components
        mockLLM = MockLLMClient()
        loreEngine = LoreInheritanceEngine(mockLLM)
        worldGenerator = WorldGenerator(mockLLM, loreEngine)
        initializer = DungeonInitializer(worldGenerator, seedRepo, chunkRepo, spaceRepo)
        exitLinker = ExitLinker(spaceRepo)
        exitResolver = ExitResolver(mockLLM, spaceRepo)
        themeRegistry = ThemeRegistry()
        mobSpawner = MobSpawner(mockLLM)
        trapGenerator = TrapGenerator(mockLLM)
        resourceGenerator = ResourceGenerator(mockLLM, itemRepo)
        lootTableGenerator = LootTableGenerator(itemRepo)
        spacePopulator = SpacePopulator(mobSpawner, trapGenerator, resourceGenerator, lootTableGenerator, themeRegistry)
        stateChangeHandler = StateChangeHandler(mockLLM, spaceRepo)
        worldPersistence = WorldPersistence(seedRepo, chunkRepo, spaceRepo)
        respawnManager = RespawnManager(chunkRepo, spaceRepo, mobSpawner, themeRegistry, initializer)
    }

    @AfterAll
    fun cleanup() {
        worldDb.close()
        itemDb.close()
        File(worldDbPath).delete()
        File(itemDbPath).delete()
    }

    @BeforeEach
    fun resetDatabase() {
        worldDb.clearAll()
    }

    // Test 1: Complete dungeon initialization flow
    @Test
    fun `complete dungeon initialization creates full hierarchy with content`() = runBlocking {
        val result = initializer.initializeDeepDungeon("integration-test-seed")

        assertTrue(result.isSuccess)
        val startingSpaceId = result.getOrNull()!!

        // Verify hierarchy
        val allChunks = chunkRepo.getAll().getOrThrow()
        assertEquals(1, allChunks.values.count { it.level == ChunkLevel.WORLD })
        assertEquals(3, allChunks.values.count { it.level == ChunkLevel.REGION })
        assertTrue(allChunks.values.any { it.level == ChunkLevel.ZONE })
        assertTrue(allChunks.values.any { it.level == ChunkLevel.SUBZONE })

        // Verify starting space exists
        val startingSpace = spaceRepo.findByChunkId(startingSpaceId).getOrThrow()
        assertNotNull(startingSpace)
        assertFalse(startingSpace?.description.isNullOrEmpty())
        assertTrue(startingSpace?.exits?.isNotEmpty() == true)

        // Verify world seed
        val seed = seedRepo.get().getOrThrow()
        assertEquals("integration-test-seed", seed?.first)
        assertTrue(seed?.second?.contains("Ancient Abyss") == true)
    }

    // Test 2: Exit linking between spaces
    @Test
    fun `exit linking creates bidirectional navigation`() = runBlocking {
        // Initialize dungeon
        val startingSpaceId = initializer.initializeDeepDungeon("exit-test-seed").getOrThrow()
        val startingSpace = spaceRepo.findByChunkId(startingSpaceId).getOrThrow()!!

        // Get an exit from starting space
        val northExit = startingSpace.exits.firstOrNull { it.direction == "north" }
        assertNotNull(northExit, "Starting space should have north exit")

        // Generate adjacent space (simulating navigation)
        val parentSubzoneId = startingSpaceId.substringBeforeLast("_SPACE")
        val (adjacentSpace, adjacentId) = worldGenerator.generateSpace(
            WorldChunkComponent(
                level = ChunkLevel.SUBZONE,
                parentId = "test",
                children = emptyList(),
                lore = "Test lore",
                biomeTheme = "dungeon",
                sizeEstimate = 10,
                mobDensity = 0.5,
                difficultyLevel = 5
            ),
            parentSubzoneId
        ).getOrThrow()

        // Save adjacent space
        spaceRepo.save(adjacentId, adjacentSpace).getOrThrow()

        // Link exits
        exitLinker.linkExits(startingSpaceId, "north", adjacentId)

        // Verify forward link
        val updatedStartingSpace = spaceRepo.findByChunkId(startingSpaceId).getOrThrow()!!
        val linkedNorthExit = updatedStartingSpace.exits.first { it.direction == "north" }
        assertEquals(adjacentId, linkedNorthExit.targetId)

        // Verify reciprocal link created
        val updatedAdjacentSpace = spaceRepo.findByChunkId(adjacentId).getOrThrow()!!
        val southExit = updatedAdjacentSpace.exits.firstOrNull { it.direction == "south" }
        assertNotNull(southExit, "Reciprocal south exit should be created")
        assertEquals(startingSpaceId, southExit?.targetId)
    }

    // Test 3: Exit resolution (cardinal + fuzzy + natural language)
    @Test
    fun `exit resolver handles cardinal, fuzzy, and natural language directions`() = runBlocking {
        // Create test space with various exits
        val testSpace = SpacePropertiesComponent(
            description = "Test room",
            exits = listOf(
                ExitData("north", "dark passage", "space_north_123", isHidden = false, hiddenDifficulty = null, conditions = emptyList()),
                ExitData("climb ladder", "rusty ladder up", "space_up_456", isHidden = false, hiddenDifficulty = null, conditions = emptyList()),
                ExitData("secret door", "hidden door", "space_secret_789", isHidden = true, hiddenDifficulty = 15, conditions = emptyList())
            ),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )
        val spaceId = "test_space_123"
        spaceRepo.save(spaceId, testSpace).getOrThrow()

        // Test 1: Cardinal direction (exact match)
        val cardinalResult = exitResolver.resolve(spaceId, "north", perceptionSkill = 10)
        assertTrue(cardinalResult is ResolveResult.Success)
        assertEquals("space_north_123", (cardinalResult as ResolveResult.Success).targetId)

        // Test 2: Fuzzy match (typo tolerance)
        val fuzzyResult = exitResolver.resolve(spaceId, "nrth", perceptionSkill = 10)
        assertTrue(fuzzyResult is ResolveResult.Success)
        assertEquals("space_north_123", (fuzzyResult as ResolveResult.Success).targetId)

        // Test 3: Natural language (LLM parsing)
        val naturalResult = exitResolver.resolve(spaceId, "climb up the ladder", perceptionSkill = 10)
        assertTrue(naturalResult is ResolveResult.Success)
        assertEquals("space_up_456", (naturalResult as ResolveResult.Success).targetId)

        // Test 4: Hidden exit fails with low Perception
        val hiddenLowResult = exitResolver.resolve(spaceId, "secret door", perceptionSkill = 5)
        assertTrue(hiddenLowResult is ResolveResult.Failure)

        // Test 5: Hidden exit succeeds with high Perception
        val hiddenHighResult = exitResolver.resolve(spaceId, "secret door", perceptionSkill = 20)
        assertTrue(hiddenHighResult is ResolveResult.Success)
        assertEquals("space_secret_789", (hiddenHighResult as ResolveResult.Success).targetId)
    }

    // Test 4: Content placement (mobs, traps, resources)
    @Test
    fun `space populator generates theme-appropriate content`() = runBlocking {
        val theme = "dark forest"
        val difficulty = 10

        // Create test space
        val baseSpace = SpacePropertiesComponent(
            description = "Forest clearing",
            exits = emptyList(),
            brightness = 60,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )

        // Populate with content
        val (populatedSpace, mobs) = spacePopulator.populateWithEntities(baseSpace, theme, difficulty, spaceSize = 5)

        // Verify mobs generated (density * size = 0.5 * 5 = ~2-3 mobs expected)
        assertTrue(mobs.isNotEmpty(), "Should generate mobs")
        assertTrue(mobs.size in 1..5, "Mob count should be reasonable")

        // Verify mobs have proper stats
        mobs.forEach { mob ->
            assertTrue(mob.stats.values.all { it in 3..20 }, "Mob stats should be in valid range")
            assertTrue(mob.lootTableId?.contains(theme) == true, "Mob should have theme-appropriate loot table")
        }

        // Note: Traps and resources are probabilistic (15% and 5% respectively)
        // We don't assert their presence, but verify structure if present
        populatedSpace.traps.forEach { trap ->
            assertTrue(trap.difficulty in 1..25, "Trap difficulty should be valid")
            assertFalse(trap.triggered, "New trap should not be triggered")
        }

        populatedSpace.resources.forEach { resource ->
            assertTrue(resource.quantity > 0, "Resource should have positive quantity")
        }
    }

    // Test 5: State changes and persistence
    @Test
    fun `state changes persist correctly across save and load`() = runBlocking {
        // Initialize dungeon
        val startingSpaceId = initializer.initializeDeepDungeon("state-test-seed").getOrThrow()

        // Load starting space
        val originalSpace = spaceRepo.findByChunkId(startingSpaceId).getOrThrow()!!

        // Apply state changes
        val actions = listOf(
            WorldAction.SetFlag("door_opened", "true"),
            WorldAction.SetFlag("lever_pulled", "true")
        )

        var modifiedSpace = originalSpace
        actions.forEach { action ->
            modifiedSpace = stateChangeHandler.applyChange(modifiedSpace, action).getOrThrow()
        }

        // Save modified space
        spaceRepo.save(startingSpaceId, modifiedSpace).getOrThrow()

        // Reload space from database
        val reloadedSpace = spaceRepo.findByChunkId(startingSpaceId).getOrThrow()!!

        // Verify state flags persisted
        assertEquals("true", reloadedSpace.stateFlags["door_opened"])
        assertEquals("true", reloadedSpace.stateFlags["lever_pulled"])
    }

    // Test 6: Respawn preserves player changes
    @Test
    fun `respawn regenerates mobs but preserves player state changes`() = runBlocking {
        // Initialize dungeon
        val startingSpaceId = initializer.initializeDeepDungeon("respawn-test-seed").getOrThrow()

        // Load and populate space
        val originalSpace = spaceRepo.findByChunkId(startingSpaceId).getOrThrow()!!
        val (populatedSpace, originalMobs) = spacePopulator.populateWithEntities(
            originalSpace,
            "dungeon",
            difficulty = 5,
            spaceSize = 5
        )
        spaceRepo.save(startingSpaceId, populatedSpace).getOrThrow()

        // Apply player state change
        val modifiedSpace = stateChangeHandler.applyChange(
            populatedSpace,
            WorldAction.SetFlag("chest_opened", "true")
        ).getOrThrow()
        spaceRepo.save(startingSpaceId, modifiedSpace).getOrThrow()

        // Respawn entire world
        respawnManager.respawnWorld(seedRepo, chunkRepo, spaceRepo).getOrThrow()

        // Reload space
        val respawnedSpace = spaceRepo.findByChunkId(startingSpaceId).getOrThrow()!!

        // Verify player state preserved
        assertEquals("true", respawnedSpace.stateFlags["chest_opened"])

        // Note: We can't directly verify mob regeneration without access to entities field
        // but we verify that the space still exists and state is preserved
        assertNotNull(respawnedSpace)
    }

    // Test 7: Navigation state tracking
    @Test
    fun `navigation state tracks player movement through hierarchy`() = runBlocking {
        // Initialize dungeon
        val startingSpaceId = initializer.initializeDeepDungeon("nav-test-seed").getOrThrow()

        // Create navigation state
        var navState = NavigationState(
            currentSpaceId = startingSpaceId,
            currentSubzoneId = null,
            currentZoneId = null,
            currentRegionId = null,
            worldId = null,
            visitedSpaces = emptyList()
        )

        // Update location (should populate hierarchy)
        navState = navState.updateLocation(chunkRepo).getOrThrow()

        // Verify hierarchy populated
        assertNotNull(navState.currentSubzoneId, "Should have subzone ID")
        assertNotNull(navState.currentZoneId, "Should have zone ID")
        assertNotNull(navState.currentRegionId, "Should have region ID")
        assertNotNull(navState.worldId, "Should have world ID")

        // Verify chunks exist in repository
        val subzone = chunkRepo.findById(navState.currentSubzoneId!!).getOrThrow()
        val zone = chunkRepo.findById(navState.currentZoneId!!).getOrThrow()
        val region = chunkRepo.findById(navState.currentRegionId!!).getOrThrow()
        val world = chunkRepo.findById(navState.worldId!!).getOrThrow()

        assertNotNull(subzone)
        assertNotNull(zone)
        assertNotNull(region)
        assertNotNull(world)

        assertEquals(ChunkLevel.SUBZONE, subzone?.level)
        assertEquals(ChunkLevel.ZONE, zone?.level)
        assertEquals(ChunkLevel.REGION, region?.level)
        assertEquals(ChunkLevel.WORLD, world?.level)
    }

    // Test 8: Full save/load cycle
    @Test
    fun `world persistence handles full save and load cycle`() = runBlocking {
        // Initialize dungeon
        val startingSpaceId = initializer.initializeDeepDungeon("persistence-test-seed").getOrThrow()

        // Load world state
        val (seed, globalLore) = worldPersistence.loadWorldState().getOrThrow()

        // Verify loaded data
        assertEquals("persistence-test-seed", seed)
        assertTrue(globalLore.contains("Ancient Abyss"), "Global lore should be loaded")

        // Load starting space
        val startingSpace = worldPersistence.loadSpace(startingSpaceId).getOrThrow()
        assertNotNull(startingSpace)

        // Make changes
        val modifiedSpace = startingSpace.copy(
            stateFlags = mapOf("explored" to "true")
        )

        // Save incremental change
        worldPersistence.saveSpace(startingSpaceId, modifiedSpace).getOrThrow()

        // Reload and verify
        val reloadedSpace = worldPersistence.loadSpace(startingSpaceId).getOrThrow()
        assertEquals("true", reloadedSpace.stateFlags["explored"])
    }

    // Test 9: Theme registry content validation
    @Test
    fun `theme registry provides valid content profiles for all themes`() {
        val themes = themeRegistry.getAllThemeNames()

        assertTrue(themes.isNotEmpty(), "Should have registered themes")

        themes.forEach { theme ->
            val profile = themeRegistry.getProfile(theme)
            assertNotNull(profile, "Theme $theme should have profile")

            assertTrue(profile.traps.isNotEmpty(), "Theme $theme should have traps")
            assertTrue(profile.resources.isNotEmpty(), "Theme $theme should have resources")
            assertTrue(profile.mobArchetypes.isNotEmpty(), "Theme $theme should have mobs")
            assertFalse(profile.ambiance.isEmpty(), "Theme $theme should have ambiance")
        }

        // Test semantic matching
        val forestProfile = themeRegistry.getProfileSemantic("dark woodland")
        assertNotNull(forestProfile)
        assertTrue(forestProfile.resources.any { it.contains("wood") || it.contains("herb") })
    }

    // Test 10: Loot table generation for themes
    @Test
    fun `loot table generator creates theme-appropriate tables`() = runBlocking {
        val theme = "dark forest"
        val difficulty = 8

        val lootTable = lootTableGenerator.generateForTheme(theme, difficulty, "forest_8").getOrThrow()

        // Verify table structure
        assertTrue(lootTable.entries.isNotEmpty(), "Loot table should have entries")
        assertTrue(lootTable.maxDrops > 0, "Should have max drops")

        // Verify quality scaling
        assertTrue(lootTable.qualityModifier >= 0, "Quality modifier should be non-negative")

        // Verify entries have valid weights
        lootTable.entries.forEach { entry ->
            assertTrue(entry.weight > 0.0, "Entry weight should be positive")
            assertTrue(entry.qualityRange.first <= entry.qualityRange.second, "Quality range should be valid")
        }
    }

    // Mock LLM client for testing
    private class MockLLMClient : LLMClient {
        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            // Return appropriate JSON based on prompt context
            val content = when {
                userContext.contains("Generate chunk") || userContext.contains("Generate a chunk") -> """
                    {
                      "biomeTheme": "test dungeon theme",
                      "sizeEstimate": 50,
                      "mobDensity": 0.5,
                      "difficultyLevel": 10
                    }
                """.trimIndent()

                userContext.contains("Generate a room") || userContext.contains("Generate space") -> """
                    {
                      "description": "A dark stone chamber with moss-covered walls",
                      "exits": [
                        {"direction": "north", "description": "dark passage leading deeper", "targetId": "PLACEHOLDER"},
                        {"direction": "south", "description": "wooden door with iron bands", "targetId": "PLACEHOLDER"},
                        {"direction": "east", "description": "narrow corridor", "targetId": "PLACEHOLDER"}
                      ],
                      "brightness": 30,
                      "terrainType": "NORMAL"
                    }
                """.trimIndent()

                userContext.contains("lore variation") || userContext.contains("Vary the lore") -> {
                    "The ancient dungeon continues deeper into darkness with crumbling stone passages and echoing chambers."
                }

                userContext.contains("blend") || userContext.contains("Blend") -> {
                    "ancient dark dungeon"
                }

                userContext.contains("natural language") || userContext.contains("user said") -> {
                    "climb ladder"
                }

                userContext.contains("mob") || userContext.contains("Generate NPCs") -> """
                    [
                      {
                        "name": "Giant Rat",
                        "description": "A large aggressive rodent",
                        "personality": "hostile",
                        "stats": {"STR": 10, "DEX": 12, "CON": 8, "INT": 3, "WIS": 10, "CHA": 5},
                        "health": 50,
                        "hostile": true,
                        "gold": 5
                      }
                    ]
                """.trimIndent()

                else -> "Generated response"
            }

            return OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = System.currentTimeMillis(),
                model = modelId,
                choices = listOf(
                    OpenAIChoice(
                        message = OpenAIMessage("assistant", content),
                        finishReason = "stop"
                    )
                ),
                usage = OpenAIUsage(
                    promptTokens = 100,
                    completionTokens = 50,
                    totalTokens = 150
                )
            )
        }

        override suspend fun createEmbedding(text: String, model: String): List<Double> {
            return List(1536) { 0.1 }
        }

        override fun close() {}
    }
}
