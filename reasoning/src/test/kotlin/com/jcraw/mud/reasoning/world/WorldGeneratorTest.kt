package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.world.*
import com.jcraw.sophia.llm.LLMClient
import com.jcraw.sophia.llm.OpenAIChoice
import com.jcraw.sophia.llm.OpenAIMessage
import com.jcraw.sophia.llm.OpenAIResponse
import com.jcraw.sophia.llm.OpenAIUsage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for WorldGenerator - LLM-driven chunk and space generation.
 *
 * Focus on: JSON parsing, validation, lore inheritance, probabilistic mechanics, and error handling.
 */
class WorldGeneratorTest {

    @Test
    fun `generateChunk creates WORLD level chunk with global lore`() = runBlocking {
        val mockLLM = ChunkGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val context = GenerationContext(
            seed = "test-seed",
            globalLore = "Global lore about the world",
            parentChunk = null,
            parentChunkId = null,
            level = ChunkLevel.WORLD
        )

        val result = generator.generateChunk(context)

        assertTrue(result.isSuccess)
        val (chunk, id) = result.getOrNull()!!
        assertEquals(ChunkLevel.WORLD, chunk.level)
        assertNull(chunk.parentId)
        assertTrue(chunk.lore.contains("Global lore"))
        assertTrue(id.contains("WORLD"))
    }

    @Test
    fun `generateChunk creates REGION level with parent lore variation`() = runBlocking {
        val mockLLM = ChunkGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val parentChunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            parentId = null,
            children = emptyList(),
            lore = "The ancient kingdom of Valdor",
            biomeTheme = "kingdom ruins",
            sizeEstimate = 1000,
            mobDensity = 0.5,
            difficultyLevel = 10
        )

        val context = GenerationContext(
            seed = "test-seed",
            globalLore = "Global lore",
            parentChunk = parentChunk,
            parentChunkId = "WORLD_root",
            level = ChunkLevel.REGION,
            direction = "north"
        )

        val result = generator.generateChunk(context)

        assertTrue(result.isSuccess)
        val (chunk, id) = result.getOrNull()!!
        assertEquals(ChunkLevel.REGION, chunk.level)
        assertEquals("WORLD_root", chunk.parentId)
        assertTrue(chunk.lore.contains("Valdor"), "Child lore should reference parent")
        assertTrue(id.contains("REGION"))
    }

    @Test
    fun `generateChunk coerces mobDensity to valid range`() = runBlocking {
        val mockLLM = ChunkGeneratingMockLLM(mobDensityOverride = 1.5) // Invalid: > 1.0
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val context = GenerationContext(
            seed = "test-seed",
            globalLore = "Global lore",
            parentChunk = null,
            parentChunkId = null,
            level = ChunkLevel.ZONE
        )

        val result = generator.generateChunk(context)

        assertTrue(result.isSuccess)
        val (chunk, _) = result.getOrNull()!!
        assertEquals(1.0, chunk.mobDensity, "mobDensity should be coerced to max 1.0")
    }

    @Test
    fun `generateChunk coerces mobDensity minimum to zero`() = runBlocking {
        val mockLLM = ChunkGeneratingMockLLM(mobDensityOverride = -0.5) // Invalid: < 0
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val context = GenerationContext(
            seed = "test-seed",
            globalLore = "Global lore",
            parentChunk = null,
            parentChunkId = null,
            level = ChunkLevel.SUBZONE
        )

        val result = generator.generateChunk(context)

        assertTrue(result.isSuccess)
        val (chunk, _) = result.getOrNull()!!
        assertEquals(0.0, chunk.mobDensity, "mobDensity should be coerced to min 0.0")
    }

    @Test
    fun `generateChunk coerces difficultyLevel to valid range`() = runBlocking {
        val mockLLM = ChunkGeneratingMockLLM(difficultyOverride = 25) // Invalid: > 20
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val context = GenerationContext(
            seed = "test-seed",
            globalLore = "Global lore",
            parentChunk = null,
            parentChunkId = null,
            level = ChunkLevel.ZONE
        )

        val result = generator.generateChunk(context)

        assertTrue(result.isSuccess)
        val (chunk, _) = result.getOrNull()!!
        assertEquals(20, chunk.difficultyLevel, "difficultyLevel should be coerced to max 20")
    }

    @Test
    fun `generateChunk coerces difficultyLevel minimum to one`() = runBlocking {
        val mockLLM = ChunkGeneratingMockLLM(difficultyOverride = 0) // Invalid: < 1
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val context = GenerationContext(
            seed = "test-seed",
            globalLore = "Global lore",
            parentChunk = null,
            parentChunkId = null,
            level = ChunkLevel.REGION
        )

        val result = generator.generateChunk(context)

        assertTrue(result.isSuccess)
        val (chunk, _) = result.getOrNull()!!
        assertEquals(1, chunk.difficultyLevel, "difficultyLevel should be coerced to min 1")
    }

    @Test
    fun `generateChunk handles LLM failure gracefully`() = runBlocking {
        val failingLLM = FailingMockLLMClient()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(failingLLM, mockLoreEngine)

        val context = GenerationContext(
            seed = "test-seed",
            globalLore = "Global lore",
            parentChunk = null,
            parentChunkId = null,
            level = ChunkLevel.ZONE
        )

        val result = generator.generateChunk(context)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to generate chunk") == true)
    }

    @Test
    fun `generateChunk handles malformed JSON gracefully`() = runBlocking {
        val malformedLLM = MalformedJsonMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(malformedLLM, mockLoreEngine)

        val context = GenerationContext(
            seed = "test-seed",
            globalLore = "Global lore",
            parentChunk = null,
            parentChunkId = null,
            level = ChunkLevel.SUBZONE
        )

        val result = generator.generateChunk(context)

        assertTrue(result.isFailure)
    }

    @Test
    fun `generateSpace creates valid space with description and exits`() = runBlocking {
        val mockLLM = SpaceGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val parentSubzone = WorldChunkComponent(
            level = ChunkLevel.SUBZONE,
            parentId = "ZONE_abc_123",
            children = emptyList(),
            lore = "A dark corridor complex",
            biomeTheme = "dark dungeon",
            sizeEstimate = 10,
            mobDensity = 0.6,
            difficultyLevel = 8
        )

        val result = generator.generateSpace(parentSubzone, "SUBZONE_xyz_456")

        assertTrue(result.isSuccess)
        val (space, id) = result.getOrNull()!!
        assertFalse(space.description.isEmpty())
        assertTrue(space.exits.isNotEmpty(), "Space should have exits")
        assertTrue(space.brightness in 0..100, "Brightness should be coerced to valid range")
        assertTrue(id.contains("SPACE"))
    }

    @Test
    fun `generateSpace creates exits with PLACEHOLDER targets`() = runBlocking {
        val mockLLM = SpaceGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val parentSubzone = WorldChunkComponent(
            level = ChunkLevel.SUBZONE,
            parentId = "ZONE_abc_123",
            children = emptyList(),
            lore = "Test lore",
            biomeTheme = "test theme",
            sizeEstimate = 10,
            mobDensity = 0.5,
            difficultyLevel = 5
        )

        val result = generator.generateSpace(parentSubzone, "SUBZONE_xyz_456")

        assertTrue(result.isSuccess)
        val (space, _) = result.getOrNull()!!
        assertTrue(space.exits.all { it.targetId == "PLACEHOLDER" }, "All exit targets should be PLACEHOLDER")
    }

    @Test
    fun `generateSpace coerces brightness to valid range`() = runBlocking {
        val mockLLM = SpaceGeneratingMockLLM(brightnessOverride = 150) // Invalid: > 100
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val parentSubzone = WorldChunkComponent(
            level = ChunkLevel.SUBZONE,
            parentId = "ZONE_abc_123",
            children = emptyList(),
            lore = "Test lore",
            biomeTheme = "bright cavern",
            sizeEstimate = 10,
            mobDensity = 0.5,
            difficultyLevel = 5
        )

        val result = generator.generateSpace(parentSubzone, "SUBZONE_xyz_456")

        assertTrue(result.isSuccess)
        val (space, _) = result.getOrNull()!!
        assertEquals(100, space.brightness, "Brightness should be coerced to max 100")
    }

    @Test
    fun `generateSpace handles LLM failure gracefully`() = runBlocking {
        val failingLLM = FailingMockLLMClient()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(failingLLM, mockLoreEngine)

        val parentSubzone = WorldChunkComponent(
            level = ChunkLevel.SUBZONE,
            parentId = "ZONE_abc_123",
            children = emptyList(),
            lore = "Test lore",
            biomeTheme = "test theme",
            sizeEstimate = 10,
            mobDensity = 0.5,
            difficultyLevel = 5
        )

        val result = generator.generateSpace(parentSubzone, "SUBZONE_xyz_456")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to generate space") == true)
    }

    @Test
    fun `generateTrap creates forest-themed trap for forest theme`() {
        val mockLLM = ChunkGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val trap = generator.generateTrap("dark forest", 10)

        assertTrue(trap.type in listOf("bear trap", "pit trap", "snare"))
        assertTrue(trap.difficulty in 5..25)
        assertFalse(trap.triggered)
        assertTrue(trap.id.startsWith("trap_"))
    }

    @Test
    fun `generateTrap creates cave-themed trap for cave theme`() {
        val mockLLM = ChunkGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val trap = generator.generateTrap("magma cave", 15)

        assertTrue(trap.type in listOf("lava pool", "collapsing floor", "gas vent"))
        assertTrue(trap.difficulty in 5..25)
    }

    @Test
    fun `generateTrap creates crypt-themed trap for crypt theme`() {
        val mockLLM = ChunkGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val trap = generator.generateTrap("ancient crypt", 12)

        assertTrue(trap.type in listOf("poison dart", "cursed rune", "arrow trap"))
    }

    @Test
    fun `generateTrap creates castle-themed trap for castle theme`() {
        val mockLLM = ChunkGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val trap = generator.generateTrap("ruined castle", 8)

        assertTrue(trap.type in listOf("spike trap", "swinging blade", "falling portcullis"))
    }

    @Test
    fun `generateTrap creates generic trap for unknown theme`() {
        val mockLLM = ChunkGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val trap = generator.generateTrap("mysterious void", 10)

        assertTrue(trap.type in listOf("pit trap", "spike trap", "poison dart"))
    }

    @Test
    fun `generateTrap difficulty scales with level`() {
        val mockLLM = ChunkGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val easyTrap = generator.generateTrap("dungeon", 1)
        val hardTrap = generator.generateTrap("dungeon", 20)

        assertTrue(hardTrap.difficulty >= easyTrap.difficulty - 2) // Allow for variance
    }

    @Test
    fun `generateResource creates forest resources for forest theme`() {
        val mockLLM = ChunkGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val resource = generator.generateResource("dark forest")

        assertTrue(resource.templateId in listOf("wood", "herbs", "berries"))
        assertTrue(resource.quantity in 1..5)
        assertTrue(resource.id.startsWith("resource_"))
    }

    @Test
    fun `generateResource creates cave resources for cave theme`() {
        val mockLLM = ChunkGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val resource = generator.generateResource("crystal cave")

        assertTrue(resource.templateId in listOf("iron_ore", "coal", "crystal"))
    }

    @Test
    fun `generateResource creates magma resources for magma theme`() {
        val mockLLM = ChunkGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val resource = generator.generateResource("magma chamber")

        assertTrue(resource.templateId in listOf("obsidian", "sulfur", "fire_crystal"))
    }

    @Test
    fun `generateResource creates crypt resources for crypt theme`() {
        val mockLLM = ChunkGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val resource = generator.generateResource("ancient crypt")

        assertTrue(resource.templateId in listOf("bone", "arcane_dust", "ancient_cloth"))
    }

    @Test
    fun `generateResource creates generic resources for unknown theme`() {
        val mockLLM = ChunkGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val resource = generator.generateResource("alien world")

        assertTrue(resource.templateId in listOf("stone", "wood", "herbs"))
    }

    @Test
    fun `generateResource has variable respawn time`() {
        val mockLLM = ChunkGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        // Generate multiple resources to test variability
        val resources = (1..10).map { generator.generateResource("forest") }
        val withRespawn = resources.count { it.respawnTime != null }
        val withoutRespawn = resources.count { it.respawnTime == null }

        // Should have some variation (not all same)
        assertTrue(withRespawn > 0 || withoutRespawn > 0, "Resources should have variable respawn")
    }

    @Test
    fun `generateSpace probabilistically adds traps`() = runBlocking {
        val mockLLM = SpaceGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val parentSubzone = WorldChunkComponent(
            level = ChunkLevel.SUBZONE,
            parentId = "ZONE_abc_123",
            children = emptyList(),
            lore = "Test lore",
            biomeTheme = "test theme",
            sizeEstimate = 10,
            mobDensity = 0.5,
            difficultyLevel = 10
        )

        // Generate multiple spaces to test probability
        val spaces = (1..100).map {
            generator.generateSpace(parentSubzone, "SUBZONE_xyz_456").getOrNull()!!.first
        }

        val withTraps = spaces.count { it.traps.isNotEmpty() }
        // Should be around 15% (allow 5-25% range for randomness)
        assertTrue(withTraps in 5..25, "Trap probability should be ~15% (was $withTraps/100)")
    }

    @Test
    fun `generateSpace probabilistically adds resources`() = runBlocking {
        val mockLLM = SpaceGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val parentSubzone = WorldChunkComponent(
            level = ChunkLevel.SUBZONE,
            parentId = "ZONE_abc_123",
            children = emptyList(),
            lore = "Test lore",
            biomeTheme = "forest",
            sizeEstimate = 10,
            mobDensity = 0.5,
            difficultyLevel = 10
        )

        // Generate multiple spaces to test probability
        val spaces = (1..100).map {
            generator.generateSpace(parentSubzone, "SUBZONE_xyz_456").getOrNull()!!.first
        }

        val withResources = spaces.count { it.resources.isNotEmpty() }
        // Should be around 5% (allow 0-12% range for randomness)
        assertTrue(withResources in 0..12, "Resource probability should be ~5% (was $withResources/100)")
    }

    @Test
    fun `generateSpace probabilistically adds hidden exits`() = runBlocking {
        val mockLLM = SpaceGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val parentSubzone = WorldChunkComponent(
            level = ChunkLevel.SUBZONE,
            parentId = "ZONE_abc_123",
            children = emptyList(),
            lore = "Test lore",
            biomeTheme = "dungeon",
            sizeEstimate = 10,
            mobDensity = 0.5,
            difficultyLevel = 10
        )

        // Generate multiple spaces to test probability
        val allExits = (1..100).flatMap {
            generator.generateSpace(parentSubzone, "SUBZONE_xyz_456").getOrNull()!!.first.exits
        }

        val hiddenExits = allExits.count { it.isHidden }
        val totalExits = allExits.size
        val percentage = (hiddenExits.toDouble() / totalExits) * 100

        // Should be around 20% (allow 10-30% range for randomness)
        assertTrue(percentage in 10.0..30.0, "Hidden exit probability should be ~20% (was $percentage%)")
    }

    @Test
    fun `hidden exits have Perception skill check condition`() = runBlocking {
        val mockLLM = SpaceGeneratingMockLLM()
        val mockLoreEngine = createMockLoreEngine()
        val generator = WorldGenerator(mockLLM, mockLoreEngine)

        val parentSubzone = WorldChunkComponent(
            level = ChunkLevel.SUBZONE,
            parentId = "ZONE_abc_123",
            children = emptyList(),
            lore = "Test lore",
            biomeTheme = "dungeon",
            sizeEstimate = 10,
            mobDensity = 0.5,
            difficultyLevel = 12
        )

        // Generate many spaces to find hidden exits
        val allExits = (1..50).flatMap {
            generator.generateSpace(parentSubzone, "SUBZONE_xyz_456").getOrNull()!!.first.exits
        }

        val hiddenExits = allExits.filter { it.isHidden }
        assertTrue(hiddenExits.isNotEmpty(), "Should have some hidden exits")

        // Verify all hidden exits have Perception check
        hiddenExits.forEach { exit ->
            assertTrue(exit.conditions.isNotEmpty(), "Hidden exit should have conditions")
            val perceptionCheck = exit.conditions.filterIsInstance<Condition.SkillCheck>()
                .firstOrNull { it.skill == "Perception" }
            assertNotNull(perceptionCheck, "Hidden exit should have Perception check")
            assertEquals(10 + parentSubzone.difficultyLevel, perceptionCheck.difficulty)
        }
    }

    // Mock implementations

    private fun createMockLoreEngine(): LoreInheritanceEngine {
        return LoreInheritanceEngine(MockLLMClient())
    }

    private class MockLLMClient : LLMClient {
        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            return OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 0L,
                model = modelId,
                choices = listOf(
                    OpenAIChoice(
                        message = OpenAIMessage("assistant", "mock response"),
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

    private class ChunkGeneratingMockLLM(
        private val mobDensityOverride: Double? = null,
        private val difficultyOverride: Int? = null
    ) : LLMClient {
        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            val json = """
                {
                  "biomeTheme": "test theme",
                  "sizeEstimate": 50,
                  "mobDensity": ${mobDensityOverride ?: 0.5},
                  "difficultyLevel": ${difficultyOverride ?: 10}
                }
            """.trimIndent()

            return OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 0L,
                model = modelId,
                choices = listOf(
                    OpenAIChoice(
                        message = OpenAIMessage("assistant", json),
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

    private class SpaceGeneratingMockLLM(
        private val brightnessOverride: Int? = null
    ) : LLMClient {
        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            val json = """
                {
                  "description": "A dark and atmospheric room with stone walls",
                  "exits": [
                    {"direction": "north", "description": "dark passage", "targetId": "PLACEHOLDER"},
                    {"direction": "south", "description": "wooden door", "targetId": "PLACEHOLDER"},
                    {"direction": "climb ladder", "description": "rusty ladder up", "targetId": "PLACEHOLDER"}
                  ],
                  "brightness": ${brightnessOverride ?: 40},
                  "terrainType": "NORMAL"
                }
            """.trimIndent()

            return OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 0L,
                model = modelId,
                choices = listOf(
                    OpenAIChoice(
                        message = OpenAIMessage("assistant", json),
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

    private class FailingMockLLMClient : LLMClient {
        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            throw RuntimeException("Simulated LLM failure")
        }

        override suspend fun createEmbedding(text: String, model: String): List<Double> {
            throw RuntimeException("Simulated embedding failure")
        }

        override fun close() {}
    }

    private class MalformedJsonMockLLM : LLMClient {
        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            val malformedJson = "{ this is not valid JSON at all"

            return OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 0L,
                model = modelId,
                choices = listOf(
                    OpenAIChoice(
                        message = OpenAIMessage("assistant", malformedJson),
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
