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

            IMPORTANT: You are ALREADY in the game. Look at the "Current game state" to see where you are.
            Do NOT try to "enter" or "start" the game - you're already playing!

            Generate a single player command that:
            1. Is appropriate for the current scenario and game state
            2. Uses valid game commands (look, go/move/n/s/e/w, take, attack, talk, equip, use, check, etc.)
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

        // Track unique rooms visited for exploration scenario
        val roomsVisited = if (scenario is TestScenario.Exploration) {
            extractRoomsFromHistory(recentHistory)
        } else {
            emptySet()
        }

        val scenarioGuidance = when (scenario) {
            is TestScenario.Exploration -> {
                val roomsVisitedText = if (roomsVisited.isNotEmpty()) {
                    "Rooms visited so far: ${roomsVisited.joinToString(", ")}"
                } else {
                    "No rooms visited yet."
                }
                """
                Test ALL exploration mechanics efficiently:
                1. Room navigation - Move to new rooms (target: 5 different rooms)
                2. Look commands - Use 'look' to get room descriptions and 'look <object>' to examine items/NPCs
                3. Description variability - Revisit rooms occasionally to test that descriptions change

                Efficient strategy:
                - Visit new rooms using ALL directional movements (n/s/e/w/ne/nw/se/sw/up/down)
                - Test FULL direction names (north, northwest) AND abbreviations (n, nw)
                - Look at 1-2 objects/NPCs per room
                - Revisit 1-2 rooms to test description variability
                - Don't spend more than 2-3 actions per room

                IMPORTANT: Test diagonal directions (northeast, northwest, southeast, southwest)!

                $roomsVisitedText
            """.trimIndent()
            }
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
                Test ALL item/inventory mechanics efficiently:
                1. Examine items in room - Use 'look <item>' to inspect items before taking
                2. Take items - Pick up items with 'take <item>'
                3. Check inventory - Use 'inventory' or 'i' to verify item state
                4. Examine inventory items - Use 'look <item>' on items you're carrying (NEW!)
                5. Equip items - Equip weapons/armor with 'equip <item>' (automatically unequips old)
                6. Examine equipped items - Use 'look <weapon/armor>' to see equipped item descriptions
                7. Use consumables - Use potions with 'use <item>' (test healing)
                8. Drop items - Drop items with 'drop <item>'

                Efficient strategy (minimize steps):
                - Look at 1-2 items in room before taking
                - Take 2-3 different item types (weapon, armor, consumable)
                - Check inventory after taking
                - Look at 1 item in inventory to verify examination works
                - Equip weapon, then look at it to see "(equipped)" tag
                - Equip armor
                - Use consumable if damaged
                - Drop 1 item, verify it appears in room
                - Target: ~12-15 actions total
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
                Play naturally to complete the dungeon:
                - Start by looking around and exploring (look, n/s/e/w)
                - Find and collect items, equip weapons/armor
                - Fight NPCs when encountered
                - Talk to friendly NPCs for information
                - Work toward reaching the end of the dungeon
            """.trimIndent()
            is TestScenario.QuestTesting -> """
                Focus on:
                - Viewing quests (quests, journal, j)
                - Accepting available quests (accept <quest_id>)
                - Completing quest objectives (kill NPCs, collect items, explore rooms, talk to NPCs, use skills)
                - Claiming completed quest rewards (claim <quest_id>)
                - Testing abandon functionality (abandon <quest_id>)
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
    val expected: String
)
