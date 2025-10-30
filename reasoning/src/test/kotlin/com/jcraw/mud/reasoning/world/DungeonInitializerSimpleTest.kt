package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.memory.world.SQLiteWorldChunkRepository
import com.jcraw.mud.memory.world.SQLiteWorldSeedRepository
import com.jcraw.mud.memory.world.SQLiteSpacePropertiesRepository
import com.jcraw.mud.memory.world.WorldDatabase
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
 * Integration tests for DungeonInitializer - uses real SQLite database
 * Tests complete dungeon hierarchy creation and persistence
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DungeonInitializerSimpleTest {
    private lateinit var database: WorldDatabase
    private lateinit var seedRepo: SQLiteWorldSeedRepository
    private lateinit var chunkRepo: SQLiteWorldChunkRepository
    private lateinit var spaceRepo: SQLiteSpacePropertiesRepository
    private lateinit var worldGenerator: WorldGenerator
    private lateinit var initializer: DungeonInitializer

    private val testDbPath = "test_dungeon_init.db"

    @BeforeAll
    fun setup() {
        database = WorldDatabase(testDbPath)
        seedRepo = SQLiteWorldSeedRepository(database)
        chunkRepo = SQLiteWorldChunkRepository(database)
        spaceRepo = SQLiteSpacePropertiesRepository(database)

        // Create mock LLM and generator
        val mockLLM = MockLLMClient()
        val loreEngine = LoreInheritanceEngine(mockLLM)
        worldGenerator = WorldGenerator(mockLLM, loreEngine)

        initializer = DungeonInitializer(worldGenerator, seedRepo, chunkRepo, spaceRepo)
    }

    @AfterAll
    fun cleanup() {
        database.close()
        File(testDbPath).delete()
    }

    @BeforeEach
    fun resetDatabase() {
        database.clearAll()
    }

    @Test
    fun `initializeDeepDungeon creates complete hierarchy`() = runBlocking {
        val result = initializer.initializeDeepDungeon("test-seed-123")

        assertTrue(result.isSuccess)
        val startingSpaceId = result.getOrNull()!!
        assertTrue(startingSpaceId.contains("SPACE"))

        // Verify chunks saved
        val allChunks = chunkRepo.getAll().getOrThrow()
        assertTrue(allChunks.isNotEmpty())

        // Verify levels exist
        val worldChunks = allChunks.values.filter { it.level == ChunkLevel.WORLD }
        val regionChunks = allChunks.values.filter { it.level == ChunkLevel.REGION }
        val zoneChunks = allChunks.values.filter { it.level == ChunkLevel.ZONE }
        val subzoneChunks = allChunks.values.filter { it.level == ChunkLevel.SUBZONE }

        assertEquals(1, worldChunks.size, "Should have 1 WORLD")
        assertEquals(3, regionChunks.size, "Should have 3 REGIONs")
        assertTrue(zoneChunks.isNotEmpty(), "Should have at least 1 ZONE")
        assertTrue(subzoneChunks.isNotEmpty(), "Should have at least 1 SUBZONE")
    }

    @Test
    fun `initializeDeepDungeon saves world seed`() = runBlocking {
        val result = initializer.initializeDeepDungeon("test-seed-456")

        assertTrue(result.isSuccess)

        val seed = seedRepo.get().getOrThrow()
        assertNotNull(seed)
        assertEquals("test-seed-456", seed?.first)
        assertTrue(seed?.second?.contains("Ancient Abyss") == true)
    }

    @Test
    fun `initializeDeepDungeon creates regions with correct difficulty`() = runBlocking {
        val result = initializer.initializeDeepDungeon("test-seed")

        assertTrue(result.isSuccess)

        val allChunks = chunkRepo.getAll().getOrThrow()
        val regions = allChunks.values
            .filter { it.level == ChunkLevel.REGION }
            .sortedBy { it.difficultyLevel }

        assertEquals(3, regions.size)
        assertEquals(5, regions[0].difficultyLevel, "Upper Depths difficulty")
        assertEquals(12, regions[1].difficultyLevel, "Mid Depths difficulty")
        assertEquals(18, regions[2].difficultyLevel, "Lower Depths difficulty")
    }

    @Test
    fun `initializeDeepDungeon creates parent-child relationships`() = runBlocking {
        val result = initializer.initializeDeepDungeon("test-seed")

        assertTrue(result.isSuccess)

        val allChunks = chunkRepo.getAll().getOrThrow()

        // Find WORLD
        val worldEntry = allChunks.entries.first { it.value.level == ChunkLevel.WORLD }
        val worldChunk = worldEntry.value

        // WORLD should have 3 region children
        assertEquals(3, worldChunk.children.size)

        // Each REGION should reference WORLD as parent
        val regions = allChunks.filter { it.value.level == ChunkLevel.REGION }
        regions.forEach { (_, region) ->
            assertEquals(worldEntry.key, region.parentId)
        }
    }

    @Test
    fun `initializeDeepDungeon returns valid starting space ID`() = runBlocking {
        val result = initializer.initializeDeepDungeon("test-seed")

        assertTrue(result.isSuccess)
        val startingSpaceId = result.getOrNull()!!

        // Space should exist in repository
        val space = spaceRepo.findByChunkId(startingSpaceId).getOrThrow()
        assertNotNull(space)
        assertFalse(space?.description.isNullOrEmpty())
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
            // Return valid JSON for chunk or space generation
            val content = when {
                userContext.contains("Generate chunk") -> """
                    {
                      "biomeTheme": "test dungeon theme",
                      "sizeEstimate": 50,
                      "mobDensity": 0.5,
                      "difficultyLevel": 10
                    }
                """.trimIndent()
                userContext.contains("Generate a room") -> """
                    {
                      "description": "A dark stone chamber with ancient walls",
                      "exits": [
                        {"direction": "north", "description": "dark passage", "targetId": "PLACEHOLDER"},
                        {"direction": "south", "description": "wooden door", "targetId": "PLACEHOLDER"}
                      ],
                      "brightness": 30,
                      "terrainType": "NORMAL"
                    }
                """.trimIndent()
                userContext.contains("lore variation") -> {
                    "The ancient dungeon continues deeper into darkness. Local variation shows unique features."
                }
                else -> "Generated content"
            }

            return OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 0L,
                model = modelId,
                choices = listOf(
                    OpenAIChoice(
                        message = OpenAIMessage("assistant", content),
                        finishReason = "stop"
                    )
                ),
                usage = OpenAIUsage(10, 20, 30)
            )
        }

        override suspend fun createEmbedding(text: String, model: String): List<Double> {
            return List(1536) { 0.1 }
        }

        override fun close() {}
    }
}
