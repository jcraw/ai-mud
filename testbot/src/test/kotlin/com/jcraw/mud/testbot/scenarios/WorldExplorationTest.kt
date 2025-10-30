package com.jcraw.mud.testbot.scenarios

import com.jcraw.mud.core.Component
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.repository.*
import com.jcraw.mud.core.world.*
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.world.*
import com.jcraw.mud.reasoning.CombatNarrator
import com.jcraw.mud.reasoning.NPCInteractionGenerator
import com.jcraw.mud.reasoning.RoomDescriptionGenerator
import com.jcraw.mud.reasoning.world.*
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * E2E scenario test for World Generation System V2.
 *
 * Tests the deep dungeon MVP with hierarchical world generation:
 * - On-demand chunk generation (WORLD > REGION > ZONE > SUBZONE > SPACE)
 * - Lore inheritance and theme coherence
 * - Exit resolution (cardinal + natural language)
 * - Content placement (traps, resources, mobs)
 * - State persistence (flags, corpses, items)
 * - Mob respawn on restart
 *
 * This test validates:
 * - DungeonInitializer creates valid hierarchy
 * - WorldGenerator produces consistent chunks
 * - LoreInheritanceEngine varies lore appropriately
 * - ExitResolver handles all resolution phases
 * - ThemeRegistry provides appropriate content
 * - StateChangeHandler preserves player modifications
 * - RespawnManager regenerates mobs correctly
 * - WorldPersistence saves and loads state
 *
 * Note: This test focuses on component-level validation since handler integration
 * is not yet complete (handlers are stubs in Chunk 7). Full gameplay integration
 * will be tested once handleTravel/handleScout are fully implemented.
 *
 * Replaces: test_world_exploration.sh (planned)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorldExplorationTest {

    private var apiKey: String? = null

    @BeforeEach
    fun checkApiKey() {
        // Try to load API key from environment or local.properties
        apiKey = System.getenv("OPENAI_API_KEY")
            ?: System.getProperty("openai.api.key")
            ?: loadApiKeyFromLocalProperties()

        // Skip test if no API key is available
        assumeTrue(
            apiKey != null && apiKey!!.isNotBlank(),
            "Skipping E2E test: OPENAI_API_KEY not set. " +
                "Set OPENAI_API_KEY environment variable or openai.api.key in local.properties to run this test."
        )
    }

    @Nested
    @DisplayName("World Generation System Integration")
    inner class WorldGenerationSystemIntegration {

        @Test
        @DisplayName("DungeonInitializer creates valid deep dungeon hierarchy")
        fun `dungeon initializer creates valid hierarchy`() = runBlocking {
            // ARRANGE
            val llmClient = OpenAIClient(apiKey!!)
            val memoryManager = MemoryManager(llmClient)

            // Create repositories
            val worldDb = WorldDatabase(":memory:")
            val chunkRepo = SQLiteWorldChunkRepository(worldDb)
            val spaceRepo = SQLiteSpacePropertiesRepository(worldDb)
            val seedRepo = SQLiteWorldSeedRepository(worldDb)

            // Create generator components
            val loreEngine = LoreInheritanceEngine(llmClient)
            val worldGenerator = WorldGenerator(llmClient, loreEngine, chunkRepo, spaceRepo)
            val dungeonInitializer = DungeonInitializer(worldGenerator, seedRepo, chunkRepo, spaceRepo, llmClient)

            try {
                // ACT: Initialize deep dungeon
                val startResult = dungeonInitializer.initializeDeepDungeon("test-seed-${System.currentTimeMillis()}")

                // ASSERT: Initialization successful
                assertTrue(
                    startResult.isSuccess,
                    "DungeonInitializer should successfully create dungeon"
                )

                val startingSpaceId = startResult.getOrThrow()
                assertTrue(
                    startingSpaceId.isNotBlank(),
                    "Starting space ID should not be blank"
                )

                // Verify world seed saved
                val seedResult = seedRepo.get()
                assertTrue(
                    seedResult.isSuccess && seedResult.getOrNull() != null,
                    "World seed should be saved"
                )

                // Verify hierarchy exists
                val allChunks = chunkRepo.getAll().getOrThrow()
                assertTrue(
                    allChunks.size >= 5,
                    "Hierarchy should have at least 5 chunks (WORLD + 3 REGIONS + starting ZONE/SUBZONE/SPACE chain)"
                )

                // Verify WORLD level exists
                val worldChunk = allChunks.find { it.level == ChunkLevel.WORLD }
                assertTrue(
                    worldChunk != null,
                    "WORLD level chunk should exist"
                )

                // Verify REGION levels exist (Upper, Mid, Lower Depths)
                val regions = allChunks.filter { it.level == ChunkLevel.REGION }
                assertTrue(
                    regions.size == 3,
                    "Should have 3 REGION chunks (Upper, Mid, Lower Depths), got ${regions.size}"
                )

                // Verify difficulty scaling across regions
                val sortedRegions = regions.sortedBy { it.difficultyLevel }
                assertTrue(
                    sortedRegions[0].difficultyLevel < sortedRegions[1].difficultyLevel,
                    "Region difficulty should scale (Upper < Mid)"
                )
                assertTrue(
                    sortedRegions[1].difficultyLevel < sortedRegions[2].difficultyLevel,
                    "Region difficulty should scale (Mid < Lower)"
                )

                // Verify starting space properties exist
                val startingSpaceProps = spaceRepo.findByChunkId(startingSpaceId).getOrThrow()
                assertTrue(
                    startingSpaceProps != null,
                    "Starting space properties should exist"
                )

                // Verify starting space has exits
                assertTrue(
                    startingSpaceProps!!.exits.isNotEmpty(),
                    "Starting space should have exits"
                )

                // Verify lore consistency (global lore should appear in chunks)
                val (_, globalLore) = seedResult.getOrThrow()!!
                assertTrue(
                    worldChunk!!.lore.contains("Abyss", ignoreCase = true) ||
                    worldChunk.biomeTheme.contains("dungeon", ignoreCase = true),
                    "World chunk lore should reference dungeon theme"
                )

                println("\n=== Dungeon Initialization Test Results ===")
                println("Starting Space ID: $startingSpaceId")
                println("Total Chunks: ${allChunks.size}")
                println("WORLD Chunks: ${allChunks.count { it.level == ChunkLevel.WORLD }}")
                println("REGION Chunks: ${allChunks.count { it.level == ChunkLevel.REGION }}")
                println("ZONE Chunks: ${allChunks.count { it.level == ChunkLevel.ZONE }}")
                println("SUBZONE Chunks: ${allChunks.count { it.level == ChunkLevel.SUBZONE }}")
                println("SPACE Chunks: ${allChunks.count { it.level == ChunkLevel.SPACE }}")
                println("Global Lore: $globalLore")
                println("Region Difficulties: ${regions.map { it.difficultyLevel }.sorted()}")
            } finally {
                worldDb.clearAll()
                llmClient.close()
            }
        }

        @Test
        @DisplayName("WorldGenerator creates consistent chunks with lore inheritance")
        fun `world generator creates consistent chunks`() = runBlocking {
            // ARRANGE
            val llmClient = OpenAIClient(apiKey!!)
            val memoryManager = MemoryManager(llmClient)
            val worldDb = WorldDatabase(":memory:")
            val chunkRepo = SQLiteWorldChunkRepository(worldDb)
            val spaceRepo = SQLiteSpacePropertiesRepository(worldDb)

            val loreEngine = LoreInheritanceEngine(llmClient)
            val worldGenerator = WorldGenerator(llmClient, loreEngine, chunkRepo, spaceRepo)

            try {
                // ACT: Generate WORLD chunk
                val worldContext = GenerationContext(
                    seed = "test-seed",
                    globalLore = "A vast underground labyrinth",
                    parentChunk = null,
                    parentChunkId = null,
                    level = ChunkLevel.WORLD,
                    direction = null
                )

                val (worldChunk, worldId) = worldGenerator.generateChunk(worldContext).getOrThrow()
                chunkRepo.save(worldChunk, worldId)

                // Generate REGION chunk
                val regionContext = GenerationContext(
                    seed = "test-seed",
                    globalLore = "A vast underground labyrinth",
                    parentChunk = worldChunk,
                    parentChunkId = worldId,
                    level = ChunkLevel.REGION,
                    direction = "north"
                )

                val (regionChunk, regionId) = worldGenerator.generateChunk(regionContext).getOrThrow()

                // ASSERT: Chunks have valid properties
                assertTrue(
                    worldChunk.level == ChunkLevel.WORLD,
                    "World chunk should have correct level"
                )

                assertTrue(
                    worldChunk.lore.isNotBlank(),
                    "World chunk should have lore"
                )

                assertTrue(
                    worldChunk.biomeTheme.isNotBlank(),
                    "World chunk should have biome theme"
                )

                assertTrue(
                    regionChunk.level == ChunkLevel.REGION,
                    "Region chunk should have correct level"
                )

                // Verify lore inheritance (region lore should relate to world lore)
                // This is validated by the LLM generation, but we can check it's not identical
                assertTrue(
                    regionChunk.lore != worldChunk.lore,
                    "Region lore should be varied from world lore"
                )

                // Verify difficulty constraints
                assertTrue(
                    worldChunk.difficultyLevel in 1..20,
                    "Difficulty should be in valid range (1-20), got ${worldChunk.difficultyLevel}"
                )

                assertTrue(
                    worldChunk.mobDensity in 0.0..1.0,
                    "Mob density should be in valid range (0-1), got ${worldChunk.mobDensity}"
                )

                println("\n=== Chunk Generation Test Results ===")
                println("World ID: $worldId")
                println("World Lore: ${worldChunk.lore.take(100)}...")
                println("World Theme: ${worldChunk.biomeTheme}")
                println("World Difficulty: ${worldChunk.difficultyLevel}")
                println("Region ID: $regionId")
                println("Region Lore: ${regionChunk.lore.take(100)}...")
                println("Region Theme: ${regionChunk.biomeTheme}")
                println("Region Difficulty: ${regionChunk.difficultyLevel}")
            } finally {
                worldDb.clearAll()
                llmClient.close()
            }
        }

        @Test
        @DisplayName("Exit resolution works for cardinal directions and natural language")
        fun `exit resolver handles all resolution phases`() = runBlocking {
            // ARRANGE
            val llmClient = OpenAIClient(apiKey!!)
            val exitResolver = ExitResolver(llmClient)

            val testSpace = SpacePropertiesComponent(
                description = "A dark hallway",
                exits = mapOf(
                    "north" to ExitData(
                        direction = "north",
                        targetId = "space_001",
                        description = "A wooden door",
                        conditions = emptyList(),
                        isHidden = false,
                        hiddenDifficulty = null
                    ),
                    "climb" to ExitData(
                        direction = "climb",
                        targetId = "space_002",
                        description = "A rusty iron ladder leading up",
                        conditions = listOf(ExitData.Condition.SkillCheck("Agility", 10)),
                        isHidden = false,
                        hiddenDifficulty = null
                    )
                ),
                brightness = 50,
                terrainType = TerrainType.NORMAL,
                traps = emptyList(),
                resources = emptyList(),
                entities = emptyList(),
                itemsDropped = emptyList(),
                stateFlags = emptyMap()
            )

            try {
                // ACT & ASSERT: Phase 1 (Exact match)
                val exactResult = exitResolver.resolve(
                    "north",
                    testSpace,
                    createTestPlayerState()
                )

                assertTrue(
                    exactResult is ExitResolver.ResolveResult.Success,
                    "Exact cardinal match should succeed"
                )

                assertEquals(
                    "space_001",
                    (exactResult as ExitResolver.ResolveResult.Success).targetId,
                    "Should resolve to correct target"
                )

                // Phase 2 (Fuzzy match)
                val fuzzyResult = exitResolver.resolve(
                    "nroth", // Typo: should match "north"
                    testSpace,
                    createTestPlayerState()
                )

                assertTrue(
                    fuzzyResult is ExitResolver.ResolveResult.Success ||
                    fuzzyResult is ExitResolver.ResolveResult.Ambiguous,
                    "Fuzzy match should succeed or suggest alternatives"
                )

                // Phase 3 (Natural language - LLM)
                val nlpResult = exitResolver.resolve(
                    "climb the rusty ladder",
                    testSpace,
                    createTestPlayerState()
                )

                assertTrue(
                    nlpResult is ExitResolver.ResolveResult.Success ||
                    nlpResult is ExitResolver.ResolveResult.Failure,
                    "Natural language should be resolved by LLM (may fail skill check)"
                )

                // Visible exits filtering
                val visibleExits = exitResolver.getVisibleExits(testSpace, createTestPlayerState())
                assertTrue(
                    visibleExits.size == 2,
                    "Should return all visible exits (not hidden)"
                )

                println("\n=== Exit Resolution Test Results ===")
                println("Exact Match: $exactResult")
                println("Fuzzy Match: $fuzzyResult")
                println("NLP Match: $nlpResult")
                println("Visible Exits: ${visibleExits.size}")
            } finally {
                llmClient.close()
            }
        }

        @Test
        @DisplayName("Theme registry provides appropriate content for biomes")
        fun `theme registry provides content profiles`() {
            // ARRANGE
            val themeRegistry = ThemeRegistry

            // ACT & ASSERT: Predefined themes exist
            val forestProfile = themeRegistry.getProfile("dark forest")
            assertTrue(
                forestProfile != null,
                "Dark forest theme should exist"
            )

            assertTrue(
                forestProfile!!.traps.isNotEmpty(),
                "Forest should have trap types"
            )

            assertTrue(
                forestProfile.resources.isNotEmpty(),
                "Forest should have resource types"
            )

            assertTrue(
                forestProfile.mobArchetypes.isNotEmpty(),
                "Forest should have mob archetypes"
            )

            val caveProfile = themeRegistry.getProfile("magma cave")
            assertTrue(
                caveProfile != null,
                "Magma cave theme should exist"
            )

            // Semantic matching
            val cryptProfile = themeRegistry.getProfileSemantic("undead crypt")
            assertTrue(
                cryptProfile != null,
                "Semantic matching should find crypt-like theme"
            )

            println("\n=== Theme Registry Test Results ===")
            println("Forest Traps: ${forestProfile.traps}")
            println("Forest Resources: ${forestProfile.resources}")
            println("Forest Mobs: ${forestProfile.mobArchetypes}")
            println("Cave Profile: ${caveProfile?.traps}")
            println("Crypt Profile: ${cryptProfile?.mobArchetypes}")
        }

        @Test
        @DisplayName("State persistence saves and loads world correctly")
        fun `world persistence handles save and load`() = runBlocking {
            // ARRANGE
            val llmClient = OpenAIClient(apiKey!!)
            val worldDb = WorldDatabase(":memory:")
            val chunkRepo = SQLiteWorldChunkRepository(worldDb)
            val spaceRepo = SQLiteSpacePropertiesRepository(worldDb)
            val seedRepo = SQLiteWorldSeedRepository(worldDb)

            val worldPersistence = WorldPersistence(seedRepo, chunkRepo, spaceRepo)

            try {
                // Create test world seed
                seedRepo.save("test-seed", "Ancient dungeon lore")

                // Create test chunk
                val testChunk = WorldChunkComponent(
                    level = ChunkLevel.SPACE,
                    parentId = "parent_123",
                    children = emptyList(),
                    lore = "A dark chamber",
                    biomeTheme = "crypt",
                    sizeEstimate = 10,
                    mobDensity = 0.5,
                    difficultyLevel = 5
                )

                val testSpaceId = "test_space_001"
                chunkRepo.save(testChunk, testSpaceId)

                // Create test space properties
                val testSpace = SpacePropertiesComponent(
                    description = "A cold stone room",
                    exits = mapOf(
                        "north" to ExitData(
                            direction = "north",
                            targetId = "space_002",
                            description = "A dark passage",
                            conditions = emptyList(),
                            isHidden = false,
                            hiddenDifficulty = null
                        )
                    ),
                    brightness = 30,
                    terrainType = TerrainType.NORMAL,
                    traps = emptyList(),
                    resources = emptyList(),
                    entities = listOf("npc_001", "npc_002"),
                    itemsDropped = emptyList(),
                    stateFlags = mapOf("door_unlocked" to true)
                )

                spaceRepo.save(testSpace, testSpaceId)

                // ACT: Load world state
                val loadResult = worldPersistence.loadWorldState()

                // ASSERT: Load successful
                assertTrue(
                    loadResult.isSuccess,
                    "World state should load successfully"
                )

                val (loadedSeed, loadedLore) = worldPersistence.getWorldSeed().getOrThrow()!!
                assertEquals(
                    "test-seed",
                    loadedSeed,
                    "Loaded seed should match saved seed"
                )

                assertEquals(
                    "Ancient dungeon lore",
                    loadedLore,
                    "Loaded lore should match saved lore"
                )

                // Load space
                val loadedSpace = worldPersistence.loadSpace(testSpaceId).getOrThrow()
                assertEquals(
                    testSpace.description,
                    loadedSpace.description,
                    "Space description should persist"
                )

                assertEquals(
                    testSpace.stateFlags,
                    loadedSpace.stateFlags,
                    "State flags should persist"
                )

                assertEquals(
                    2,
                    loadedSpace.entities.size,
                    "Entity list should persist"
                )

                println("\n=== Persistence Test Results ===")
                println("Loaded Seed: $loadedSeed")
                println("Loaded Lore: $loadedLore")
                println("Loaded Space Description: ${loadedSpace.description}")
                println("Loaded State Flags: ${loadedSpace.stateFlags}")
                println("Loaded Entities: ${loadedSpace.entities.size}")
            } finally {
                worldDb.clearAll()
                llmClient.close()
            }
        }

        @Test
        @DisplayName("Respawn manager regenerates mobs while preserving world changes")
        fun `respawn manager handles mob regeneration`() = runBlocking {
            // ARRANGE
            val llmClient = OpenAIClient(apiKey!!)
            val worldDb = WorldDatabase(":memory:")
            val chunkRepo = SQLiteWorldChunkRepository(worldDb)
            val spaceRepo = SQLiteSpacePropertiesRepository(worldDb)
            val seedRepo = SQLiteWorldSeedRepository(worldDb)

            val memoryManager = MemoryManager(llmClient)
            val loreEngine = LoreInheritanceEngine(llmClient)
            val worldGenerator = WorldGenerator(llmClient, loreEngine, chunkRepo, spaceRepo)
            val dungeonInitializer = DungeonInitializer(worldGenerator, seedRepo, chunkRepo, spaceRepo, llmClient)

            val themeRegistry = ThemeRegistry
            val mobSpawner = MobSpawner(llmClient, themeRegistry)
            val respawnManager = RespawnManager(mobSpawner, chunkRepo, spaceRepo)

            try {
                // Create test dungeon
                val startingSpaceId = dungeonInitializer.initializeDeepDungeon("respawn-test").getOrThrow()

                // Get starting space
                val originalSpace = spaceRepo.findByChunkId(startingSpaceId).getOrThrow()!!

                // Add state flags and items (simulating player changes)
                val modifiedSpace = originalSpace.copy(
                    stateFlags = mapOf("chest_opened" to true, "trap_disarmed" to true),
                    itemsDropped = listOf("item_001", "item_002"),
                    entities = emptyList() // Simulate all mobs killed
                )
                spaceRepo.save(modifiedSpace, startingSpaceId)

                // ACT: Respawn world
                respawnManager.respawnWorld().getOrThrow()

                // ASSERT: Check respawned space
                val respawnedSpace = spaceRepo.findByChunkId(startingSpaceId).getOrThrow()!!

                // Mobs should be regenerated
                assertTrue(
                    respawnedSpace.entities.isNotEmpty(),
                    "Mobs should be respawned"
                )

                // State flags should persist
                assertEquals(
                    modifiedSpace.stateFlags,
                    respawnedSpace.stateFlags,
                    "State flags should persist after respawn"
                )

                // Items should persist
                assertEquals(
                    modifiedSpace.itemsDropped,
                    respawnedSpace.itemsDropped,
                    "Dropped items should persist after respawn"
                )

                println("\n=== Respawn Manager Test Results ===")
                println("Original Entities: ${originalSpace.entities.size}")
                println("Modified Entities: ${modifiedSpace.entities.size}")
                println("Respawned Entities: ${respawnedSpace.entities.size}")
                println("Persisted Flags: ${respawnedSpace.stateFlags}")
                println("Persisted Items: ${respawnedSpace.itemsDropped.size}")
            } finally {
                worldDb.clearAll()
                llmClient.close()
            }
        }
    }

    /**
     * Helper: Create test player state with basic stats
     */
    private fun createTestPlayerState(): com.jcraw.mud.core.PlayerState {
        return com.jcraw.mud.core.PlayerState(
            name = "TestPlayer",
            stats = com.jcraw.mud.core.PlayerStats(
                strength = 12,
                dexterity = 14,
                constitution = 10,
                intelligence = 13,
                wisdom = 11,
                charisma = 8
            ),
            health = 50,
            maxHealth = 50,
            description = "A test adventurer",
            inventory = emptyList(),
            roomId = "test_room",
            experience = 0,
            level = 1,
            equippedItems = emptyMap(),
            components = emptyMap()
        )
    }

    /**
     * Load API key from local.properties file.
     * Checks current directory and parent directories.
     */
    private fun loadApiKeyFromLocalProperties(): String? {
        val locations = listOf(
            "local.properties",
            "../local.properties",
            "../../local.properties"
        )

        for (location in locations) {
            val file = java.io.File(location)
            if (file.exists()) {
                return try {
                    file.readLines()
                        .firstOrNull { it.trim().startsWith("openai.api.key=") }
                        ?.substringAfter("openai.api.key=")
                        ?.trim()
                } catch (e: Exception) {
                    null
                }
            }
        }
        return null
    }
}
