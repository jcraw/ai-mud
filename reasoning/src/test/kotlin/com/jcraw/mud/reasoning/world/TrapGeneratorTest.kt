package com.jcraw.mud.reasoning.world

import com.jcraw.sophia.llm.LLMClient
import com.jcraw.sophia.llm.OpenAIChoice
import com.jcraw.sophia.llm.OpenAIMessage
import com.jcraw.sophia.llm.OpenAIResponse
import com.jcraw.sophia.llm.OpenAIUsage
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Tests for TrapGenerator trap creation.
 * Validates trap generation, scaling, probability, and LLM integration.
 */
class TrapGeneratorTest {

    private class MockLLMClient(private val response: String = "A deadly trap awaits.") : LLMClient {
        var callCount = 0
        var lastPrompt: String? = null
        var shouldFail = false

        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            callCount++
            lastPrompt = userContext
            if (shouldFail) throw RuntimeException("LLM failure")
            return OpenAIResponse(
                id = "test",
                `object` = "chat.completion",
                created = 0,
                model = modelId,
                choices = listOf(OpenAIChoice(OpenAIMessage("assistant", response), "stop")),
                usage = OpenAIUsage(0, 0, 0)
            )
        }

        override suspend fun createEmbedding(text: String, model: String): List<Double> =
            List(1536) { 0.0 }

        override fun close() {}
    }

    @Test
    fun `generate creates trap with valid structure`() = runBlocking {
        val generator = TrapGenerator()
        val trap = generator.generate("dark forest", 5)

        assertTrue(trap.id.startsWith("trap_"))
        assertFalse(trap.triggered)
        assertTrue(trap.difficulty in 1..25)
        assertTrue(trap.description.isNotBlank())
    }

    @Test
    fun `generate uses theme profile trap types`() = runBlocking {
        val generator = TrapGenerator()
        val validTraps = listOf("bear trap", "pit trap", "poisoned spike")

        // Generate several traps to check randomness uses valid types
        repeat(10) {
            val trap = generator.generate("dark forest", 5)
            assertTrue(validTraps.contains(trap.type), "Got unexpected trap type: ${trap.type}")
        }
    }

    @Test
    fun `generate scales difficulty with variance`() = runBlocking {
        val generator = TrapGenerator()
        val baseDifficulty = 10

        // Generate multiple traps to check variance
        val difficulties = MutableList(20) { generator.generate("dark forest", baseDifficulty).difficulty }

        // Should have variance (not all same)
        assertTrue(difficulties.toSet().size > 1, "Difficulty should vary")

        // All should be within base ± 2
        difficulties.forEach { diff ->
            assertTrue(diff in (baseDifficulty - 2)..(baseDifficulty + 2))
        }
    }

    @Test
    fun `generate coerces difficulty to 1-25 range`() = runBlocking {
        val generator = TrapGenerator()

        // Test low boundary
        val lowTrap = generator.generate("dark forest", -10)
        assertTrue(lowTrap.difficulty >= 1, "Difficulty should be at least 1")

        // Test high boundary
        val highTrap = generator.generate("dark forest", 100)
        assertTrue(highTrap.difficulty <= 25, "Difficulty should be at most 25")
    }

    @Test
    fun `generate creates unique trap IDs`() = runBlocking {
        val generator = TrapGenerator()
        val ids = MutableList(20) { generator.generate("dark forest", 5).id }

        // All IDs should be unique
        assertEquals(ids.size, ids.toSet().size, "Trap IDs should be unique")
    }

    @Test
    fun `generate uses default profile for unknown theme`() = runBlocking {
        val generator = TrapGenerator()
        val trap = generator.generate("nonexistent theme", 5)

        // Should use dark forest profile (default)
        val darkForestTraps = listOf("bear trap", "pit trap", "poisoned spike")
        assertTrue(darkForestTraps.contains(trap.type))
    }

    @Test
    fun `generate without LLM uses simple description`() = runBlocking {
        val generator = TrapGenerator()
        val trap = generator.generate("magma cave", 5)

        assertTrue(trap.description.contains("magma cave"))
        assertTrue(trap.description.startsWith("A "))
    }

    @Test
    fun `generate with LLM uses generated description`() = runBlocking {
        val mockLLM = MockLLMClient("The ground trembles as molten rock bubbles nearby.")
        val generator = TrapGenerator(mockLLM)
        val trap = generator.generate("magma cave", 5)

        assertEquals("The ground trembles as molten rock bubbles nearby.", trap.description)
        assertEquals(1, mockLLM.callCount)
    }

    @Test
    fun `generateTrapDescription without LLM returns fallback`() = runBlocking {
        val generator = TrapGenerator()
        val description = generator.generateTrapDescription("bear trap", "dark forest")

        assertEquals("A bear trap in the dark forest.", description)
    }

    @Test
    fun `generateTrapDescription with LLM generates vivid description`() = runBlocking {
        val mockLLM = MockLLMClient("Rusted jaws wait beneath fallen leaves, ready to snap shut.")
        val generator = TrapGenerator(mockLLM)
        val description = generator.generateTrapDescription("bear trap", "dark forest")

        assertEquals("Rusted jaws wait beneath fallen leaves, ready to snap shut.", description)
        assertEquals(1, mockLLM.callCount)
        assertTrue(mockLLM.lastPrompt!!.contains("bear trap"))
        assertTrue(mockLLM.lastPrompt!!.contains("dark forest"))
    }

    @Test
    fun `generateTrapDescription handles LLM failure gracefully`() = runBlocking {
        val mockLLM = MockLLMClient().apply { shouldFail = true }
        val generator = TrapGenerator(mockLLM)
        val description = generator.generateTrapDescription("poison dart", "ancient crypt")

        // Should fall back to simple description
        assertEquals("A poison dart in the ancient crypt.", description)
    }

    @Test
    fun `generateTrapsForSpace returns empty list when roll fails`() = runBlocking {
        val generator = TrapGenerator()

        // With 0% probability, should never generate traps
        repeat(10) {
            val traps = generator.generateTrapsForSpace("dark forest", 5, trapProbability = 0.0)
            assertTrue(traps.isEmpty(), "Should not generate traps with 0% probability")
        }
    }

    @Test
    fun `generateTrapsForSpace generates trap when roll succeeds`() = runBlocking {
        val generator = TrapGenerator()

        // With 100% probability, should always generate at least one trap
        repeat(10) {
            val traps = generator.generateTrapsForSpace("dark forest", 5, trapProbability = 1.0)
            assertTrue(traps.isNotEmpty(), "Should generate at least one trap with 100% probability")
        }
    }

    @Test
    fun `generateTrapsForSpace can generate second trap in high difficulty`() = runBlocking {
        val generator = TrapGenerator()

        // With difficulty > 10 and 100% probability, sometimes generates 2 traps
        // (5% chance for second trap, so we need multiple attempts)
        var foundTwoTraps = false
        repeat(100) {
            val traps = generator.generateTrapsForSpace("magma cave", 15, trapProbability = 1.0)
            if (traps.size == 2) {
                foundTwoTraps = true
            }
        }

        // With 100 attempts at 5% probability, should find at least one case with 2 traps
        assertTrue(foundTwoTraps, "Should occasionally generate 2 traps in high difficulty")
    }

    @Test
    fun `generateTrapsForSpace never generates second trap in low difficulty`() = runBlocking {
        val generator = TrapGenerator()

        // With difficulty <= 10, should never generate second trap
        repeat(20) {
            val traps = generator.generateTrapsForSpace("dark forest", 5, trapProbability = 1.0)
            assertTrue(traps.size <= 1, "Should not generate second trap in low difficulty")
        }
    }

    @Test
    fun `generateTrapsForSpace respects custom probability`() = runBlocking {
        val generator = TrapGenerator()

        // With 100% probability, should always generate
        var successCount = 0
        repeat(20) {
            val traps = generator.generateTrapsForSpace("dark forest", 5, trapProbability = 1.0)
            if (traps.isNotEmpty()) successCount++
        }
        assertEquals(20, successCount, "Should generate trap every time with 100% probability")

        // With 0% probability, should never generate
        successCount = 0
        repeat(20) {
            val traps = generator.generateTrapsForSpace("dark forest", 5, trapProbability = 0.0)
            if (traps.isNotEmpty()) successCount++
        }
        assertEquals(0, successCount, "Should never generate trap with 0% probability")
    }

    @Test
    fun `generateTrapsForSpace second trap has increased difficulty`() = runBlocking {
        val generator = TrapGenerator()

        // Find a case with 2 traps
        var secondTrapDifficulty: Int? = null
        repeat(200) {
            val traps = generator.generateTrapsForSpace("magma cave", 15, trapProbability = 1.0)
            if (traps.size == 2) {
                secondTrapDifficulty = traps[1].difficulty
                return@repeat
            }
        }

        // Second trap should have base difficulty + 2 (with variance)
        assertNotNull(secondTrapDifficulty)
        assertTrue(secondTrapDifficulty!! >= 15, "Second trap should have higher base difficulty")
    }

    @Test
    fun `generate works with all theme types`() = runBlocking {
        val generator = TrapGenerator()
        val themes = listOf(
            "dark forest", "magma cave", "ancient crypt", "frozen wasteland",
            "abandoned castle", "swamp", "desert ruins", "underground lake"
        )

        themes.forEach { theme ->
            val trap = generator.generate(theme, 10)
            assertTrue(trap.id.startsWith("trap_"))
            assertTrue(trap.difficulty in 1..25)
            assertFalse(trap.triggered)
        }
    }
}
