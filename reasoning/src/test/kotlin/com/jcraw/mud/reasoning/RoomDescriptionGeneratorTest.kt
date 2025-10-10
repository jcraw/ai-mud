package com.jcraw.mud.reasoning

import com.jcraw.mud.core.Room
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

        val room = Room(
            id = "test",
            name = "Test Room",
            traits = listOf("dark", "damp", "cold")
        )

        val description = generator.generateDescription(room)

        assertTrue(description.isNotBlank(), "Description should not be blank")
        assertEquals("You stand in a Test Room. The traits are: dark, damp, cold.", description)
    }

    @Test
    fun `should fallback to simple description on LLM failure`() = runBlocking {
        val failingLLM = FailingMockLLMClient()
        val generator = RoomDescriptionGenerator(failingLLM)

        val room = Room(
            id = "test",
            name = "Test Room",
            traits = listOf("dark", "damp", "cold")
        )

        val description = generator.generateDescription(room)

        // Should use fallback
        assertEquals("dark. damp. cold.", description)
    }

    @Test
    fun `should handle empty traits list`() = runBlocking {
        val mockLLM = MockLLMClient()
        val generator = RoomDescriptionGenerator(mockLLM)

        val room = Room(
            id = "test",
            name = "Empty Room",
            traits = emptyList()
        )

        val description = generator.generateDescription(room)

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
            val roomNameMatch = Regex("Room: (.+)").find(userContext)
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

        override fun close() {}
    }
}
