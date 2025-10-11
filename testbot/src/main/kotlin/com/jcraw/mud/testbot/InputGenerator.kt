package com.jcraw.mud.testbot

import com.jcraw.sophia.llm.LLMClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Generates natural language player inputs using LLM.
 * Uses gpt-4o-mini for cost savings as per guidelines.
 */
class InputGenerator(
    private val llmClient: LLMClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    /**
     * Generate a player input for the given scenario and context.
     */
    suspend fun generateInput(
        scenario: TestScenario,
        recentHistory: List<TestStep>,
        currentContext: String
    ): GeneratedInput {
        val systemPrompt = buildSystemPrompt(scenario)
        val userContext = buildUserContext(scenario, recentHistory, currentContext)

        val response = llmClient.chatCompletion(
            modelId = "gpt-4o-mini",
            systemPrompt = systemPrompt,
            userContext = userContext,
            maxTokens = 200,
            temperature = 0.8
        )

        val responseText = response.choices.firstOrNull()?.message?.content ?: ""
        return parseResponse(responseText)
    }

    private fun buildSystemPrompt(scenario: TestScenario): String {
        return """
            You are an AI test bot playing a text-based MUD (Multi-User Dungeon) game.
            Your goal is to test the game engine by generating realistic player inputs.

            Scenario: ${scenario.name}
            Description: ${scenario.description}

            Generate a single player command that:
            1. Is appropriate for the current scenario
            2. Uses natural language (e.g., "look around", "attack skeleton", "go north")
            3. Tests game mechanics relevant to this scenario
            4. Varies from previous inputs to explore different code paths

            Respond with JSON in this format:
            {
                "input": "the player command",
                "intent": "what you're trying to test",
                "expected": "what you expect to happen"
            }
        """.trimIndent()
    }

    private fun buildUserContext(
        scenario: TestScenario,
        recentHistory: List<TestStep>,
        currentContext: String
    ): String {
        val historyText = if (recentHistory.isEmpty()) {
            "No previous actions yet."
        } else {
            recentHistory.takeLast(3).joinToString("\n") { step ->
                "Player: ${step.playerInput}\nGM: ${step.gmResponse.take(200)}"
            }
        }

        val scenarioGuidance = when (scenario) {
            is TestScenario.Exploration -> """
                Focus on:
                - Moving between rooms (n/s/e/w, north/south/east/west)
                - Looking at the environment and objects
                - Exploring different areas
            """.trimIndent()
            is TestScenario.Combat -> """
                Focus on:
                - Finding and attacking NPCs
                - Using equipped weapons
                - Continuing combat until victory or defeat
            """.trimIndent()
            is TestScenario.SkillChecks -> """
                Focus on:
                - Finding interactive features
                - Attempting skill checks (check <feature>)
                - Testing different stat-based challenges
            """.trimIndent()
            is TestScenario.ItemInteraction -> """
                Focus on:
                - Taking/picking up items
                - Dropping items
                - Equipping weapons and armor
                - Using consumables like potions
            """.trimIndent()
            is TestScenario.SocialInteraction -> """
                Focus on:
                - Talking to NPCs
                - Persuading NPCs
                - Intimidating NPCs
            """.trimIndent()
            is TestScenario.Exploratory -> """
                Try anything:
                - Random combinations
                - Edge cases
                - Invalid inputs
                - Ambiguous commands
            """.trimIndent()
            is TestScenario.FullPlaythrough -> """
                Play naturally:
                - Explore, fight, interact with NPCs
                - Collect items and equipment
                - Progress toward completing the dungeon
            """.trimIndent()
        }

        return """
            Current game state:
            $currentContext

            Recent history:
            $historyText

            Scenario guidance:
            $scenarioGuidance

            Generate the next player input.
        """.trimIndent()
    }

    private fun parseResponse(responseText: String): GeneratedInput {
        return try {
            // Try to extract JSON from response
            val jsonStart = responseText.indexOf('{')
            val jsonEnd = responseText.lastIndexOf('}') + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonText = responseText.substring(jsonStart, jsonEnd)
                json.decodeFromString<GeneratedInput>(jsonText)
            } else {
                // Fallback: treat entire response as input
                GeneratedInput(
                    input = responseText.trim(),
                    intent = "unknown",
                    expected = "unknown"
                )
            }
        } catch (e: Exception) {
            // Fallback on parse error
            GeneratedInput(
                input = responseText.trim(),
                intent = "parse_error",
                expected = "fallback"
            )
        }
    }
}

@Serializable
data class GeneratedInput(
    val input: String,
    val intent: String,
    val expected: String
)
