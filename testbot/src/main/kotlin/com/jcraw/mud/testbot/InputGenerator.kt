@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

import com.jcraw.sophia.llm.LLMClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Generates natural language player inputs using LLM.
 * Uses gpt-4o-mini for cost savings as per guidelines.
 *
 * Scenario guidance packs live in InputGuidance* extracts (MUD-034f).
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

            IMPORTANT: You are ALREADY in the game. Look at the "Current game state" to see where you are.
            Do NOT try to "enter" or "start" the game - you're already playing!

            CRITICAL RULES TO AVOID REDUNDANCY:
            1. Read "Actions taken so far" list carefully - do NOT repeat actions
            2. Follow the "Remaining" objectives list - test ONLY what hasn't been completed
            3. Each action should test something NEW - no duplicate tests
            4. The test objectives are MANDATORY, not optional suggestions
            5. Move systematically through the test plan - don't jump around randomly

            Generate a single player command that:
            1. Tests the NEXT uncompleted objective from the scenario guidance
            2. Uses valid game commands (look, go/move/n/s/e/w, take, attack, talk, equip, use, check, etc.)
            3. Has NOT been done before (check "Actions taken so far" list)
            4. Makes progress toward completing ALL mandatory test cases

            Respond with JSON in this format:
            {
                "reasoning": "your thought process before choosing this action (2-3 sentences explaining why this action makes sense given the current situation and goal)",
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
        // Extract all actions taken so far
        val actionsTaken = recentHistory.map { it.playerInput.lowercase() }

        val historyText = if (recentHistory.isEmpty()) {
            "No previous actions yet."
        } else {
            // Show ALL actions taken (condensed) + last 10 detailed
            val allActions = "Actions taken so far (${recentHistory.size}): ${actionsTaken.joinToString(", ")}"
            val recentDetailed = recentHistory.takeLast(10).joinToString("\n") { step ->
                "Player: ${step.playerInput}\nGM: ${step.gmResponse.take(1200)}"
            }
            "$allActions\n\nRecent details:\n$recentDetailed"
        }

        // Track unique rooms visited for exploration scenario
        val roomsVisited = if (scenario is TestScenario.Exploration) {
            extractRoomsFromHistory(recentHistory)
        } else {
            emptySet()
        }

        val scenarioGuidance = InputGuidanceRouter.guidanceFor(
            scenario = scenario,
            actionsTaken = actionsTaken,
            recentHistory = recentHistory,
            roomsVisited = roomsVisited,
            currentContext = currentContext
        )

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

    /**
     * Extract unique room names from test history.
     * Looks for room name headers in GM responses.
     */
    private fun extractRoomsFromHistory(history: List<TestStep>): Set<String> {
        val roomNames = mutableSetOf<String>()
        val roomPattern = Regex("^([A-Z][a-zA-Z\\s]+)\\n", RegexOption.MULTILINE)

        for (step in history) {
            // Look for room name at start of GM response
            val match = roomPattern.find(step.gmResponse)
            if (match != null) {
                val roomName = match.groupValues[1].trim()
                // Filter out common non-room patterns
                if (roomName.length > 3 && !roomName.startsWith("You ") && !roomName.startsWith("The ")) {
                    roomNames.add(roomName)
                }
            }
        }

        return roomNames
    }
}

@Serializable
data class GeneratedInput(
    val input: String,
    val intent: String,
    val expected: String,
    val reasoning: String = ""  // Bot's thought process before action
)
