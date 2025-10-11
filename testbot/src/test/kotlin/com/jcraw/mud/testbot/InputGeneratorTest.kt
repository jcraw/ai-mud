package com.jcraw.mud.testbot

import com.jcraw.sophia.llm.LLMClient
import com.jcraw.sophia.llm.OpenAIResponse
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InputGeneratorTest {

    private class MockLLMClient(private val response: String) : LLMClient {
        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): com.jcraw.sophia.llm.OpenAIResponse {
            return com.jcraw.sophia.llm.OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = System.currentTimeMillis() / 1000,
                model = modelId,
                choices = listOf(
                    com.jcraw.sophia.llm.OpenAIChoice(
                        message = com.jcraw.sophia.llm.OpenAIMessage("assistant", response),
                        finishReason = "stop"
                    )
                ),
                usage = com.jcraw.sophia.llm.OpenAIUsage(
                    promptTokens = 25,
                    completionTokens = 25,
                    totalTokens = 50
                )
            )
        }

        override suspend fun createEmbedding(text: String, model: String): List<Double> {
            return emptyList()
        }

        override fun close() {}
    }

    @Test
    fun `InputGenerator parses JSON response correctly`() = runBlocking {
        val jsonResponse = """
            {
                "input": "look around",
                "intent": "explore room",
                "expected": "room description"
            }
        """.trimIndent()

        val mockClient = MockLLMClient(jsonResponse)
        val generator = InputGenerator(mockClient)

        val result = generator.generateInput(
            scenario = TestScenario.Exploration(),
            recentHistory = emptyList(),
            currentContext = "Test room"
        )

        assertEquals("look around", result.input)
        assertEquals("explore room", result.intent)
        assertEquals("room description", result.expected)
    }

    @Test
    fun `InputGenerator handles malformed response gracefully`() = runBlocking {
        val badResponse = "Just some random text without JSON"

        val mockClient = MockLLMClient(badResponse)
        val generator = InputGenerator(mockClient)

        val result = generator.generateInput(
            scenario = TestScenario.Exploration(),
            recentHistory = emptyList(),
            currentContext = "Test room"
        )

        assertNotNull(result)
        assertEquals(badResponse.trim(), result.input)
    }

    @Test
    fun `InputGenerator handles partial JSON in response`() = runBlocking {
        val partialResponse = """
            Here's what I suggest:
            {
                "input": "go north",
                "intent": "move",
                "expected": "movement"
            }
            That should work!
        """.trimIndent()

        val mockClient = MockLLMClient(partialResponse)
        val generator = InputGenerator(mockClient)

        val result = generator.generateInput(
            scenario = TestScenario.Exploration(),
            recentHistory = emptyList(),
            currentContext = "Test room"
        )

        assertEquals("go north", result.input)
    }

    @Test
    fun `InputGenerator includes scenario context`() = runBlocking {
        var capturedSystemPrompt = ""
        var capturedUserContext = ""

        val mockClient = object : LLMClient {
            override suspend fun chatCompletion(
                modelId: String,
                systemPrompt: String,
                userContext: String,
                maxTokens: Int,
                temperature: Double
            ): com.jcraw.sophia.llm.OpenAIResponse {
                capturedSystemPrompt = systemPrompt
                capturedUserContext = userContext
                return com.jcraw.sophia.llm.OpenAIResponse(
                    id = "test-id",
                    `object` = "chat.completion",
                    created = System.currentTimeMillis() / 1000,
                    model = modelId,
                    choices = listOf(
                        com.jcraw.sophia.llm.OpenAIChoice(
                            message = com.jcraw.sophia.llm.OpenAIMessage("assistant", """{"input": "test", "intent": "test", "expected": "test"}"""),
                            finishReason = "stop"
                        )
                    ),
                    usage = com.jcraw.sophia.llm.OpenAIUsage(25, 25, 50)
                )
            }

            override suspend fun createEmbedding(text: String, model: String): List<Double> = emptyList()
            override fun close() {}
        }

        val generator = InputGenerator(mockClient)
        val scenario = TestScenario.Combat(name = "combat_test")

        generator.generateInput(
            scenario = scenario,
            recentHistory = emptyList(),
            currentContext = "In battle"
        )

        assert(capturedSystemPrompt.contains("combat_test"))
        assert(capturedUserContext.contains("In battle"))
    }
}
