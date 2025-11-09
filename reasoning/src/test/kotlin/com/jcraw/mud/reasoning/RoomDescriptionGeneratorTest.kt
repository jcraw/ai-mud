package com.jcraw.mud.reasoning

import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.world.TerrainType
import com.jcraw.sophia.llm.LLMClient
import com.jcraw.sophia.llm.OpenAIChoice
import com.jcraw.sophia.llm.OpenAIMessage
import com.jcraw.sophia.llm.OpenAIResponse
import com.jcraw.sophia.llm.OpenAIUsage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Tests for RoomDescriptionGenerator
 */
class RoomDescriptionGeneratorTest {

    @Test
    fun `should generate description using LLM`() = runBlocking {
        val mockLLM = MockLLMClient()
        val generator = RoomDescriptionGenerator(mockLLM)

        val space = SpacePropertiesComponent(
            name = "Test Room",
            description = "",
            terrainType = TerrainType.NORMAL
        )

        val description = generator.generateDescription(space, hintTraits = listOf("dark", "damp", "cold"))

        assertTrue(description.isNotBlank(), "Description should not be blank")
        assertEquals("You stand in a Test Room. The traits are: dark, damp, cold.", description)
    }

    @Test
    fun `should fallback to simple description on LLM failure`() = runBlocking {
        val failingLLM = FailingMockLLMClient()
        val generator = RoomDescriptionGenerator(failingLLM)

        val space = SpacePropertiesComponent(
            name = "Test Room",
            description = "",
            terrainType = TerrainType.NORMAL
        )

        val description = generator.generateDescription(space, hintTraits = listOf("dark", "damp", "cold"))

        assertEquals("dark. damp. cold.", description)
    }

    @Test
    fun `should handle empty traits list`() = runBlocking {
        val mockLLM = MockLLMClient()
        val generator = RoomDescriptionGenerator(mockLLM)

        val space = SpacePropertiesComponent(
            name = "Empty Room",
            description = "",
            terrainType = TerrainType.NORMAL
        )

        val description = generator.generateDescription(space)

        assertTrue(description.isNotBlank(), "Description should not be blank even with empty traits")
    }

    /**
     * Mock LLM client for testing
     */
    private class MockLLMClient : LLMClient {
        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            // Extract room name and traits from user context
            val roomNameMatch = Regex("Space: (.+)").find(userContext)
            val roomName = roomNameMatch?.groupValues?.get(1) ?: "Unknown"

            val traitsMatch = Regex("Traits: (.+)").find(userContext)
            val traits = traitsMatch?.groupValues?.get(1) ?: ""

            val content = "You stand in a $roomName. The traits are: $traits."

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
                usage = OpenAIUsage(
                    promptTokens = 10,
                    completionTokens = 20,
                    totalTokens = 30
                )
            )
        }

        override suspend fun createEmbedding(text: String, model: String): List<Double> {
            // Return a dummy embedding
            return List(1536) { 0.1 }
        }

        override fun close() {}
    }

    /**
     * Mock LLM client that always fails
     */
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
