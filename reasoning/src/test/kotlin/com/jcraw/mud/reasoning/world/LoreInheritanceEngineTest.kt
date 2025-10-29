package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.sophia.llm.LLMClient
import com.jcraw.sophia.llm.OpenAIChoice
import com.jcraw.sophia.llm.OpenAIMessage
import com.jcraw.sophia.llm.OpenAIResponse
import com.jcraw.sophia.llm.OpenAIUsage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for LoreInheritanceEngine - lore variation and theme blending for world generation.
 *
 * Focus on: prompt structure, temperature configuration, output validation, and failure handling.
 */
class LoreInheritanceEngineTest {

    @Test
    fun `varyLore generates lore variation with parent keywords`() = runBlocking {
        val mockLLM = MockLLMClient()
        val engine = LoreInheritanceEngine(mockLLM)

        val parentLore = "The ancient kingdom of Valdor is ruled by warring factions."
        val result = engine.varyLore(parentLore, ChunkLevel.REGION, "north")

        assertTrue(result.isSuccess)
        val lore = result.getOrNull()!!
        assertTrue(lore.contains("kingdom of Valdor"), "Child lore should reference parent")
        assertTrue(lore.contains("northern"), "Lore should reflect direction hint")
    }

    @Test
    fun `varyLore without direction hint still generates valid lore`() = runBlocking {
        val mockLLM = MockLLMClient()
        val engine = LoreInheritanceEngine(mockLLM)

        val parentLore = "The dark forest is corrupted by ancient magic."
        val result = engine.varyLore(parentLore, ChunkLevel.ZONE, null)

        assertTrue(result.isSuccess)
        val lore = result.getOrNull()!!
        assertTrue(lore.contains("dark forest"), "Child lore should reference parent")
        assertFalse(lore.contains("null"), "Should not include 'null' in output")
    }

    @Test
    fun `varyLore handles LLM failure gracefully`() = runBlocking {
        val failingLLM = FailingMockLLMClient()
        val engine = LoreInheritanceEngine(failingLLM)

        val result = engine.varyLore("test lore", ChunkLevel.SUBZONE, "south")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to generate lore") == true)
    }

    @Test
    fun `varyLore uses correct model and temperature`() = runBlocking {
        val capturingLLM = CapturingMockLLMClient()
        val engine = LoreInheritanceEngine(capturingLLM)

        engine.varyLore("parent lore", ChunkLevel.ZONE, "east")

        assertEquals("gpt-4o-mini", capturingLLM.lastModelId)
        assertEquals(0.7, capturingLLM.lastTemperature)
        assertEquals(300, capturingLLM.lastMaxTokens)
    }

    @Test
    fun `varyLore prompt includes level and direction`() = runBlocking {
        val capturingLLM = CapturingMockLLMClient()
        val engine = LoreInheritanceEngine(capturingLLM)

        engine.varyLore("parent lore", ChunkLevel.SUBZONE, "west")

        assertTrue(capturingLLM.lastUserContext.contains("SUBZONE"))
        assertTrue(capturingLLM.lastUserContext.contains("west"))
    }

    @Test
    fun `blendThemes combines parent and variation`() = runBlocking {
        val mockLLM = MockLLMClient()
        val engine = LoreInheritanceEngine(mockLLM)

        val result = engine.blendThemes("snowy mountain", "hot caves")

        assertTrue(result.isSuccess)
        val theme = result.getOrNull()!!
        assertTrue(theme.contains("volcanic") || theme.contains("geothermal"))
        assertTrue(theme.split(" ").size in 2..4, "Theme should be 2-4 words")
    }

    @Test
    fun `blendThemes handles LLM failure gracefully`() = runBlocking {
        val failingLLM = FailingMockLLMClient()
        val engine = LoreInheritanceEngine(failingLLM)

        val result = engine.blendThemes("theme1", "theme2")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to blend themes") == true)
    }

    @Test
    fun `blendThemes uses correct model and temperature`() = runBlocking {
        val capturingLLM = CapturingMockLLMClient()
        val engine = LoreInheritanceEngine(capturingLLM)

        engine.blendThemes("parent theme", "variation")

        assertEquals("gpt-4o-mini", capturingLLM.lastModelId)
        assertEquals(0.7, capturingLLM.lastTemperature)
        assertEquals(50, capturingLLM.lastMaxTokens)
    }

    // Mock LLM client with realistic responses
    private class MockLLMClient : LLMClient {
        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            // Extract key data from user context for realistic responses
            val isLoreVariation = userContext.contains("lore variation")
            val isThemeBlend = userContext.contains("Blend these")

            val content = when {
                isLoreVariation -> {
                    val directionMatch = Regex("direction").find(userContext)
                    val direction = if (directionMatch != null) "northern" else ""
                    val parentMatch = Regex("Parent lore: (.+)").find(userContext)
                    val parentKeyword = parentMatch?.groupValues?.get(1)?.split(" ")?.take(3)?.joinToString(" ") ?: "unknown"
                    "The $direction $parentKeyword has unique local features. Factions operate here."
                }
                isThemeBlend -> {
                    val parentMatch = Regex("Parent theme: (.+)").find(userContext)
                    val variationMatch = Regex("Local variation: (.+)").find(userContext)
                    "volcanic tunnels beneath glacier"
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

    // Mock LLM that captures call parameters for verification
    private class CapturingMockLLMClient : LLMClient {
        var lastModelId: String = ""
        var lastSystemPrompt: String = ""
        var lastUserContext: String = ""
        var lastMaxTokens: Int = 0
        var lastTemperature: Double = 0.0

        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            lastModelId = modelId
            lastSystemPrompt = systemPrompt
            lastUserContext = userContext
            lastMaxTokens = maxTokens
            lastTemperature = temperature

            return OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 0L,
                model = modelId,
                choices = listOf(
                    OpenAIChoice(
                        message = OpenAIMessage("assistant", "generated content"),
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

    // Mock LLM that always fails
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
}
