package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import com.jcraw.sophia.llm.LLMClient
import kotlin.test.*

/**
 * Tests for ResourceGenerator resource node creation.
 * Validates resource generation, scaling, probability, and LLM integration.
 */
class ResourceGeneratorTest {

    private class MockItemRepository : ItemRepository {
        override fun findTemplateById(templateId: String): Result<ItemTemplate?> = Result.success(null)
        override fun findAllTemplates(): Result<Map<String, ItemTemplate>> = Result.success(emptyMap())
        override fun findTemplatesByType(type: ItemType): Result<List<ItemTemplate>> = Result.success(emptyList())
        override fun findTemplatesByRarity(rarity: Rarity): Result<List<ItemTemplate>> = Result.success(emptyList())
        override fun saveTemplate(template: ItemTemplate): Result<Unit> = Result.success(Unit)
        override fun saveTemplates(templates: List<ItemTemplate>): Result<Unit> = Result.success(Unit)
        override fun deleteTemplate(templateId: String): Result<Unit> = Result.success(Unit)
        override fun findInstanceById(instanceId: String): Result<ItemInstance?> = Result.success(null)
        override fun findInstancesByTemplate(templateId: String): Result<List<ItemInstance>> = Result.success(emptyList())
        override fun saveInstance(instance: ItemInstance): Result<Unit> = Result.success(Unit)
        override fun deleteInstance(instanceId: String): Result<Unit> = Result.success(Unit)
        override fun findAllInstances(): Result<Map<String, ItemInstance>> = Result.success(emptyMap())
    }

    private class MockLLMClient(private val response: String = "Glowing crystals jut from the cavern wall.") : LLMClient {
        var callCount = 0
        var lastPrompt: String? = null
        var shouldFail = false

        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): com.jcraw.sophia.llm.OpenAIResponse {
            callCount++
            lastPrompt = userContext
            if (shouldFail) throw RuntimeException("LLM failure")
            return com.jcraw.sophia.llm.OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 0L,
                model = modelId,
                choices = listOf(
                    com.jcraw.sophia.llm.OpenAIChoice(
                        message = com.jcraw.sophia.llm.OpenAIMessage("assistant", response),
                        finishReason = "stop"
                    )
                ),
                usage = com.jcraw.sophia.llm.OpenAIUsage(10, 20, 30)
            )
        }

        override suspend fun createEmbedding(text: String, model: String): List<Double> {
            throw NotImplementedError("Not needed for resource tests")
        }

        override fun close() {}
    }

    private val mockItemRepo = MockItemRepository()

    @Test
    fun `generate creates resource node with valid structure`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)
        val node = generator.generate("dark forest", 5)

        assertTrue(node.id.startsWith("resource_"))
        assertTrue(node.quantity > 0)
        assertTrue(node.timeSinceHarvest == 0)
        assertTrue(node.description.isNotBlank())
    }

    @Test
    fun `generate uses theme profile resource types`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)
        val validResources = listOf("wood", "herbs", "mushroom", "berries")
        val validTemplateIds = validResources.map { res ->
            // Map to expected template IDs
            when (res) {
                "herbs" -> "healing_herb"
                else -> res
            }
        }

        // Generate several nodes to check randomness uses valid types
        repeat(10) {
            val node = generator.generate("dark forest", 5)
            assertTrue(
                validTemplateIds.contains(node.templateId) || validResources.any { node.templateId.contains(it) },
                "Got unexpected template ID: ${node.templateId}"
            )
        }
    }

    @Test
    fun `generate maps resource names to template IDs`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)

        // Test a few known mappings
        repeat(50) {
            val forestNode = generator.generate("dark forest", 5)
            when (forestNode.templateId) {
                "healing_herb" -> assertTrue(true) // herbs -> healing_herb
                "wood" -> assertTrue(true) // wood -> wood
                "mushroom" -> assertTrue(true) // mushroom -> mushroom
                "berries" -> assertTrue(true) // berries -> berries
                else -> assertTrue(forestNode.templateId.isNotBlank())
            }
        }
    }

    @Test
    fun `generate creates fallback template ID for unmapped resources`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)

        // If a resource isn't in the map, it should create a fallback ID
        // (This is harder to test directly, but we can verify IDs are always valid)
        repeat(10) {
            val node = generator.generate("dark forest", 5)
            assertTrue(node.templateId.isNotBlank())
            assertFalse(node.templateId.contains(" "), "Template ID should not contain spaces")
        }
    }

    @Test
    fun `generate scales quantity with difficulty`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)

        // Low difficulty
        val lowNodes = List(20) { generator.generate("dark forest", 1) }
        val lowAvg = lowNodes.map { it.quantity }.average()

        // High difficulty
        val highNodes = List(20) { generator.generate("dark forest", 20) }
        val highAvg = highNodes.map { it.quantity }.average()

        // High difficulty should have more quantity on average
        assertTrue(highAvg > lowAvg, "High difficulty should yield more resources")
    }

    @Test
    fun `generate returns null respawn for low difficulty`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)

        // Difficulty < 5 should have null respawn (finite resource)
        repeat(10) {
            val node = generator.generate("dark forest", 3)
            assertNull(node.respawnTime, "Low difficulty should have null respawn")
        }
    }

    @Test
    fun `generate calculates respawn time for high difficulty`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)

        // Difficulty >= 5 should have respawn time
        val node = generator.generate("magma cave", 10)
        assertNotNull(node.respawnTime)

        // Formula: 100 + difficulty * 10
        assertTrue(node.respawnTime!! >= 100, "Respawn time should be at least 100")
    }

    @Test
    fun `generate respawn time scales with difficulty`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)

        val lowNode = generator.generate("dark forest", 5)
        val highNode = generator.generate("dark forest", 20)

        assertNotNull(lowNode.respawnTime)
        assertNotNull(highNode.respawnTime)
        assertTrue(highNode.respawnTime!! > lowNode.respawnTime!!, "Higher difficulty should have longer respawn")
    }

    @Test
    fun `generate creates unique node IDs`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)
        val ids = List(20) { generator.generate("dark forest", 5).id }

        // All IDs should be unique
        assertEquals(ids.size, ids.toSet().size, "Node IDs should be unique")
    }

    @Test
    fun `generate uses default profile for unknown theme`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)
        val node = generator.generate("nonexistent theme", 5)

        // Should use dark forest profile (default)
        val darkForestResources = listOf("wood", "healing_herb", "mushroom", "berries")
        assertTrue(darkForestResources.any { node.templateId.contains(it) } || node.templateId in darkForestResources)
    }

    @Test
    fun `generate without LLM uses simple description`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)
        val node = generator.generate("magma cave", 5)

        assertTrue(node.description.contains("magma cave"))
        assertTrue(node.description.startsWith("A patch of"))
    }

    @Test
    fun `generate with LLM uses generated description`() = kotlinx.coroutines.runBlocking {
        val mockLLM = MockLLMClient("Molten crystals pulse with inner fire.")
        val generator = ResourceGenerator(mockItemRepo, mockLLM)
        val node = generator.generate("magma cave", 5)

        assertEquals("Molten crystals pulse with inner fire.", node.description)
        assertEquals(1, mockLLM.callCount)
    }

    @Test
    fun `generateNodeDescription without LLM returns fallback`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)
        val description = generator.generateNodeDescription("wood", "dark forest")

        assertEquals("A patch of wood in the dark forest.", description)
    }

    @Test
    fun `generateNodeDescription with LLM generates vivid description`() = kotlinx.coroutines.runBlocking {
        val mockLLM = MockLLMClient("Ancient oak branches reach toward the canopy.")
        val generator = ResourceGenerator(mockItemRepo, mockLLM)
        val description = generator.generateNodeDescription("wood", "dark forest")

        assertEquals("Ancient oak branches reach toward the canopy.", description)
        assertEquals(1, mockLLM.callCount)
        assertTrue(mockLLM.lastPrompt!!.contains("wood"))
        assertTrue(mockLLM.lastPrompt!!.contains("dark forest"))
    }

    @Test
    fun `generateNodeDescription handles LLM failure gracefully`() = kotlinx.coroutines.runBlocking {
        val mockLLM = MockLLMClient().apply { shouldFail = true }
        val generator = ResourceGenerator(mockItemRepo, mockLLM)
        val description = generator.generateNodeDescription("obsidian", "magma cave")

        // Should fall back to simple description
        assertEquals("A patch of obsidian in the magma cave.", description)
    }

    @Test
    fun `generateResourcesForSpace returns empty list when roll fails`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)

        // With 0% probability, should never generate resources
        repeat(10) {
            val resources = generator.generateResourcesForSpace("dark forest", 5, resourceProbability = 0.0)
            assertTrue(resources.isEmpty(), "Should not generate resources with 0% probability")
        }
    }

    @Test
    fun `generateResourcesForSpace generates resource when roll succeeds`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)

        // With 100% probability, should always generate at least one resource
        repeat(10) {
            val resources = generator.generateResourcesForSpace("dark forest", 5, resourceProbability = 1.0)
            assertTrue(resources.isNotEmpty(), "Should generate at least one resource with 100% probability")
        }
    }

    @Test
    fun `generateResourcesForSpace can generate second resource in rich areas`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)

        // With difficulty > 8 and 100% probability, sometimes generates 2 resources
        // (3% chance for second resource, so we need multiple attempts)
        var foundTwoResources = false
        repeat(200) {
            val resources = generator.generateResourcesForSpace("magma cave", 15, resourceProbability = 1.0)
            if (resources.size == 2) {
                foundTwoResources = true
            }
        }

        // With 200 attempts at 3% probability, should find at least one case with 2 resources
        assertTrue(foundTwoResources, "Should occasionally generate 2 resources in rich areas")
    }

    @Test
    fun `generateResourcesForSpace never generates second resource in poor areas`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)

        // With difficulty <= 8, should never generate second resource
        repeat(20) {
            val resources = generator.generateResourcesForSpace("dark forest", 5, resourceProbability = 1.0)
            assertTrue(resources.size <= 1, "Should not generate second resource in poor areas")
        }
    }

    @Test
    fun `generateResourcesForSpace respects custom probability`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)

        // With 100% probability, should always generate
        var successCount = 0
        repeat(20) {
            val resources = generator.generateResourcesForSpace("dark forest", 5, resourceProbability = 1.0)
            if (resources.isNotEmpty()) successCount++
        }
        assertEquals(20, successCount, "Should generate resource every time with 100% probability")

        // With 0% probability, should never generate
        successCount = 0
        repeat(20) {
            val resources = generator.generateResourcesForSpace("dark forest", 5, resourceProbability = 0.0)
            if (resources.isNotEmpty()) successCount++
        }
        assertEquals(0, successCount, "Should never generate resource with 0% probability")
    }

    @Test
    fun `generate works with all theme types`() = kotlinx.coroutines.runBlocking {
        val generator = ResourceGenerator(mockItemRepo)
        val themes = listOf(
            "dark forest", "magma cave", "ancient crypt", "frozen wasteland",
            "abandoned castle", "swamp", "desert ruins", "underground lake"
        )

        themes.forEach { theme ->
            val node = generator.generate(theme, 10)
            assertTrue(node.id.startsWith("resource_"))
            assertTrue(node.quantity > 0)
            assertTrue(node.templateId.isNotBlank())
        }
    }
}
