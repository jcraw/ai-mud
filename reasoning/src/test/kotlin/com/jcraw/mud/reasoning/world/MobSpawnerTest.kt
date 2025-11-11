package com.jcraw.mud.reasoning.world

import com.jcraw.sophia.llm.LLMClient
import com.jcraw.sophia.llm.OpenAIResponse
import com.jcraw.sophia.llm.OpenAIChoice
import com.jcraw.sophia.llm.OpenAIMessage
import com.jcraw.sophia.llm.OpenAIUsage
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Tests for MobSpawner entity generation.
 * Validates mob spawning, stat scaling, and LLM integration.
 */
class MobSpawnerTest {

    private class MockLLMClient(private val jsonResponse: String = """
        [
            {
                "name": "Wolf Alpha",
                "description": "A fierce pack leader.",
                "health": 150,
                "lootTableId": "dark_forest_5",
                "goldDrop": 25,
                "isHostile": true,
                "strength": 14,
                "dexterity": 16,
                "constitution": 12,
                "intelligence": 6,
                "wisdom": 12,
                "charisma": 8
            }
        ]
    """.trimIndent()) : LLMClient {
        var callCount = 0
        var shouldFail = false

        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            callCount++
            if (shouldFail) throw RuntimeException("LLM failure")

            return OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = System.currentTimeMillis(),
                model = modelId,
                choices = listOf(
                    OpenAIChoice(
                        message = OpenAIMessage(role = "assistant", content = jsonResponse),
                        finishReason = "stop"
                    )
                ),
                usage = OpenAIUsage(promptTokens = 100, completionTokens = 50, totalTokens = 150)
            )
        }

        override suspend fun createEmbedding(text: String, model: String): List<Double> {
            throw NotImplementedError("Not needed")
        }

        override fun close() {}
    }

    @Test
    fun `spawnEntities returns correct count based on density`() = runBlocking {
        val spawner = MobSpawner()

        val low = spawner.spawnEntities("dark forest", 0.2, 5, 10)
        val high = spawner.spawnEntities("dark forest", 0.8, 5, 10)

        assertEquals(1, low.size)
        assertEquals(3, high.size)
    }

    @Test
    fun `spawnEntities returns empty list for zero density`() = runBlocking {
        val spawner = MobSpawner()
        val mobs = spawner.spawnEntities("dark forest", 0.0, 5, 10)

        assertTrue(mobs.isEmpty())
    }

    @Test
    fun `spawnEntities without LLM uses fallback generation`() = runBlocking {
        val spawner = MobSpawner()
        val mobs = spawner.spawnEntities("dark forest", 0.3, 5, 10)

        assertEquals(1, mobs.size)
        mobs.forEach { mob ->
            assertTrue(mob.id.startsWith("npc_"))
            assertTrue(mob.name.isNotBlank())
            assertTrue(mob.health > 0)
            assertTrue(mob.stats.strength in 3..20)
        }
    }

    @Test
    fun `spawnEntities with LLM uses generated mobs`() = runBlocking {
        val mockLLM = MockLLMClient()
        val spawner = MobSpawner(mockLLM)
        val mobs = spawner.spawnEntities("dark forest", 0.1, 5, 10)

        assertEquals(1, mockLLM.callCount)
        assertEquals(1, mobs.size)
        assertEquals("Wolf Alpha", mobs[0].name)
        assertEquals(150, mobs[0].health)
    }

    @Test
    fun `spawnEntities with LLM handles failure gracefully`() = runBlocking {
        val mockLLM = MockLLMClient().apply { shouldFail = true }
        val spawner = MobSpawner(mockLLM)
        val mobs = spawner.spawnEntities("dark forest", 0.3, 5, 10)

        // Should fall back to deterministic generation
        assertEquals(1, mobs.size)
        mobs.forEach { mob ->
            assertTrue(mob.id.startsWith("npc_"))
            assertTrue(mob.name.contains("#"))
        }
    }

    @Test
    fun `spawnEntities fallback scales stats with difficulty`() = runBlocking {
        val spawner = MobSpawner()

        val lowDiff = spawner.spawnEntities("dark forest", 0.2, 2, 10)
        val highDiff = spawner.spawnEntities("dark forest", 0.2, 18, 10)

        val lowAvgStr = lowDiff.map { it.stats.strength }.average()
        val highAvgStr = highDiff.map { it.stats.strength }.average()

        assertTrue(highAvgStr > lowAvgStr, "High difficulty should have higher stats")
    }

    @Test
    fun `spawnEntities fallback scales health with difficulty`() = runBlocking {
        val spawner = MobSpawner()

        val lowDiff = spawner.spawnEntities("dark forest", 0.2, 2, 10)
        val highDiff = spawner.spawnEntities("dark forest", 0.2, 18, 10)

        val lowAvgHealth = lowDiff.map { it.health }.average()
        val highAvgHealth = highDiff.map { it.health }.average()

        assertTrue(highAvgHealth > lowAvgHealth, "High difficulty should have higher health")
    }

    @Test
    fun `spawnEntities fallback scales gold with difficulty`() = runBlocking {
        val spawner = MobSpawner()

        val lowDiff = spawner.spawnEntities("dark forest", 0.2, 2, 10)
        val highDiff = spawner.spawnEntities("dark forest", 0.2, 18, 10)

        val lowAvgGold = lowDiff.map { it.goldDrop }.average()
        val highAvgGold = highDiff.map { it.goldDrop }.average()

        assertTrue(highAvgGold > lowAvgGold, "High difficulty should have higher gold")
    }

    @Test
    fun `spawnEntities generates unique mob IDs`() = runBlocking {
        val spawner = MobSpawner()
        val mobs = spawner.spawnEntities("dark forest", 1.0, 5, 10)

        val ids = mobs.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Mob IDs should be unique")
    }

    @Test
    fun `spawnEntities uses theme-appropriate archetypes`() = runBlocking {
        val spawner = MobSpawner()
        val forestMobs = spawner.spawnEntities("dark forest", 0.3, 5, 10)

        // Should use dark forest archetypes
        val validNames = listOf("wolf", "bandit", "goblin", "spider")
        forestMobs.forEach { mob ->
            assertTrue(
                validNames.any { mob.name.lowercase().contains(it) },
                "Mob name '${mob.name}' should contain forest archetype"
            )
        }
    }

    @Test
    fun `spawnEntities generates proper loot table IDs`() = runBlocking {
        val spawner = MobSpawner()
        val mobs = spawner.spawnEntities("dark forest", 0.3, 5, 10)

        mobs.forEach { mob ->
            assertEquals("dark_forest_5", mob.lootTableId)
        }
    }

    @Test
    fun `spawnEntities creates hostile mobs by default`() = runBlocking {
        val spawner = MobSpawner()
        val mobs = spawner.spawnEntities("dark forest", 0.5, 5, 10)

        mobs.forEach { mob ->
            assertTrue(mob.isHostile, "Mobs should be hostile by default")
        }
    }

    @Test
    fun `spawnEntities coerces stats to valid range`() = runBlocking {
        val spawner = MobSpawner()
        val mobs = spawner.spawnEntities("dark forest", 1.0, 20, 10)

        mobs.forEach { mob ->
            assertTrue(mob.stats.strength in 3..20)
            assertTrue(mob.stats.dexterity in 3..20)
            assertTrue(mob.stats.constitution in 3..20)
            assertTrue(mob.stats.intelligence in 3..20)
            assertTrue(mob.stats.wisdom in 3..20)
            assertTrue(mob.stats.charisma in 3..20)
        }
    }

    @Test
    fun `spawnEntities coerces health to minimum 1`() = runBlocking {
        val spawner = MobSpawner()
        val mobs = spawner.spawnEntities("dark forest", 0.5, 1, 10)

        mobs.forEach { mob ->
            assertTrue(mob.health >= 1, "Health should be at least 1")
            assertTrue(mob.maxHealth >= 1, "Max health should be at least 1")
        }
    }

    @Test
    fun `spawnEntities coerces gold to non-negative`() = runBlocking {
        val spawner = MobSpawner()
        val mobs = spawner.spawnEntities("dark forest", 0.5, 1, 10)

        mobs.forEach { mob ->
            assertTrue(mob.goldDrop >= 0, "Gold should be non-negative")
        }
    }

    @Test
    fun `respawn generates fresh mob list`() = runBlocking {
        val spawner = MobSpawner()

        val first = spawner.spawnEntities("dark forest", 0.3, 5, 10)
        val second = spawner.respawn("dark forest", 0.3, 5, 10)

        // Same count, but different IDs (fresh generation)
        assertEquals(first.size, second.size)
        assertNotEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun `spawnEntities works with all theme types`() = runBlocking {
        val spawner = MobSpawner()
        val themes = listOf(
            "dark forest", "magma cave", "ancient crypt", "frozen wasteland",
            "abandoned castle", "swamp", "desert ruins", "underground lake"
        )

        themes.forEach { theme ->
            val mobs = spawner.spawnEntities(theme, 0.2, 10, 10)
            assertEquals(1, mobs.size)
            mobs.forEach { mob ->
                assertTrue(mob.id.startsWith("npc_"))
                assertTrue(mob.lootTableId?.contains(theme.replace(" ", "_")) ?: false)
            }
        }
    }

    @Test
    fun `spawnEntities with different space sizes`() = runBlocking {
        val spawner = MobSpawner()

        val small = spawner.spawnEntities("dark forest", 0.5, 5, 5)
        val large = spawner.spawnEntities("dark forest", 0.5, 5, 20)

        // spaceSize affects mob count
        assertTrue(large.size > small.size)
    }

    @Test
    fun `spawnEntities with LLM malformed JSON falls back`() = runBlocking {
        val mockLLM = MockLLMClient("not valid json")
        val spawner = MobSpawner(mockLLM)
        val mobs = spawner.spawnEntities("dark forest", 0.3, 5, 10)

        // Should fall back to deterministic generation
        assertEquals(1, mobs.size)
    }

    @Test
    fun `spawnEntities with LLM incomplete data uses defaults`() = runBlocking {
        val mockLLM = MockLLMClient("""
            [
                {
                    "name": "Skeleton",
                    "description": "A walking skeleton.",
                    "health": 50
                }
            ]
        """.trimIndent())
        val spawner = MobSpawner(mockLLM)
        val mobs = spawner.spawnEntities("ancient crypt", 0.1, 5, 10)

        // Should handle missing fields gracefully
        assertEquals(1, mobs.size)
        assertEquals("Skeleton", mobs[0].name)
    }

    @Test
    fun `spawnEntities generates descriptions`() = runBlocking {
        val spawner = MobSpawner()
        val mobs = spawner.spawnEntities("dark forest", 0.3, 5, 10)

        mobs.forEach { mob ->
            assertTrue(mob.description.isNotBlank(), "Mob should have description")
        }
    }
}
