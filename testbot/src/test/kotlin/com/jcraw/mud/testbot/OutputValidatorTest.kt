package com.jcraw.mud.testbot

import com.jcraw.sophia.llm.LLMClient
import com.jcraw.sophia.llm.OpenAIResponse
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutputValidatorTest {

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
                usage = com.jcraw.sophia.llm.OpenAIUsage(25, 25, 50)
            )
        }

        override suspend fun createEmbedding(text: String, model: String): List<Double> {
            return emptyList()
        }

        override fun close() {}
    }

    @Test
    fun `OutputValidator parses passing validation correctly`() = runBlocking {
        val jsonResponse = """
            {
                "pass": true,
                "reason": "Output is coherent and follows game logic",
                "details": {
                    "coherence": "pass",
                    "consistency": "pass",
                    "mechanics": "pass"
                }
            }
        """.trimIndent()

        val mockClient = MockLLMClient(jsonResponse)
        val validator = OutputValidator(mockClient)

        val result = validator.validate(
            scenario = TestScenario.Exploration(),
            playerInput = "look",
            gmResponse = "You see a dark corridor.",
            recentHistory = emptyList()
        )

        assertTrue(result.pass)
        assertEquals("Output is coherent and follows game logic", result.reason)
        assertEquals("pass", result.details["coherence"])
    }

    @Test
    fun `OutputValidator parses failing validation correctly`() = runBlocking {
        val jsonResponse = """
            {
                "pass": false,
                "reason": "Output contains errors and is incoherent",
                "details": {
                    "coherence": "fail",
                    "consistency": "fail"
                }
            }
        """.trimIndent()

        val mockClient = MockLLMClient(jsonResponse)
        val validator = OutputValidator(mockClient)

        val result = validator.validate(
            scenario = TestScenario.Combat(),
            playerInput = "attack",
            gmResponse = "ERROR: NullPointerException",
            recentHistory = emptyList()
        )

        assertFalse(result.pass)
        assertEquals("Output contains errors and is incoherent", result.reason)
    }

    @Test
    fun `OutputValidator handles malformed response conservatively`() = runBlocking {
        val badResponse = "This is not valid JSON at all"

        val mockClient = MockLLMClient(badResponse)
        val validator = OutputValidator(mockClient)

        val result = validator.validate(
            scenario = TestScenario.Exploration(),
            playerInput = "look",
            gmResponse = "You see a room.",
            recentHistory = emptyList()
        )

        // Should fail conservatively on parse errors
        assertFalse(result.pass)
        assertTrue(result.reason.contains("Failed to parse"))
    }

    @Test
    fun `OutputValidator uses fallback logic for missing JSON`() = runBlocking {
        val textResponse = "Everything looks good, no errors detected."

        val mockClient = MockLLMClient(textResponse)
        val validator = OutputValidator(mockClient)

        val result = validator.validate(
            scenario = TestScenario.Exploration(),
            playerInput = "look",
            gmResponse = "You see a room.",
            recentHistory = emptyList()
        )

        // Fallback should pass if no "error" or "fail" keywords
        assertTrue(result.pass)
    }

    @Test
    fun `OutputValidator detects errors in text fallback`() = runBlocking {
        val textResponse = "This output has an error and should fail validation."

        val mockClient = MockLLMClient(textResponse)
        val validator = OutputValidator(mockClient)

        val result = validator.validate(
            scenario = TestScenario.Combat(),
            playerInput = "attack",
            gmResponse = "ERROR: Something broke",
            recentHistory = emptyList()
        )

        // Should fail because response contains "error"
        assertFalse(result.pass)
    }

    @Test
    fun `OutputValidator includes scenario-specific criteria`() = runBlocking {
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
                            message = com.jcraw.sophia.llm.OpenAIMessage("assistant", """{"pass": true, "reason": "OK"}"""),
                            finishReason = "stop"
                        )
                    ),
                    usage = com.jcraw.sophia.llm.OpenAIUsage(25, 25, 50)
                )
            }

            override suspend fun createEmbedding(text: String, model: String): List<Double> = emptyList()
            override fun close() {}
        }

        val validator = OutputValidator(mockClient)
        val scenario = TestScenario.Combat(name = "combat_validation")

        validator.validate(
            scenario = scenario,
            playerInput = "attack skeleton",
            gmResponse = "You swing your sword.",
            recentHistory = emptyList()
        )

        assert(capturedSystemPrompt.contains("combat_validation"))
        assert(capturedUserContext.contains("attack skeleton"))
        assert(capturedUserContext.contains("You swing your sword"))
    }
}
