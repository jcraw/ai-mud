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
                FOCUS: Test combat mechanics ONLY. Equipment testing is in the item interaction scenario.

                You start in throne room with Skeleton King (60 HP, hostile). You have weapon + armor equipped.

                Combat test steps (target: ~10-15 actions):
                1. Look around - Confirm NPC is present
                2. Attack named NPC - 'attack Skeleton King' to initiate combat
                3. Continue attacking - Use 'attack' repeatedly until combat ends
                4. Observe each round - Player attacks, NPC counterattacks

                Combat mechanics being tested:
                - Combat initiation with named NPC
                - Turn-based combat (player turn → NPC turn)
                - Damage calculation (weapon + STR modifier)
                - Armor defense reducing damage taken
                - Health decreasing each round
                - Victory condition (NPC dies at 0 HP, removed from room)
                - Defeat condition (player dies at 0 HP, game ends)

                DO NOT:
                - Try to equip/unequip items (item test handles this)
                - Search for items or move to other rooms (waste of steps)
                - Try to flee or talk during combat
                - Use consumables unless health critical (<20%)

                STRATEGY:
                - Use exact NPC name to start combat: "attack Skeleton King"
                - Just use "attack" once combat starts
                - Keep attacking until victory or defeat
                - This is a pure combat mechanics test - keep it simple!
            """.trimIndent()
            is TestScenario.SkillChecks -> """
                Focus on:
                - Finding interactive features
                - Attempting skill checks (check <feature>)
                - Testing different stat-based challenges
            """.trimIndent()
            is TestScenario.ItemInteraction -> """
                IMPORTANT: You are already in the Armory which has 4 items:
                - Rusty Iron Sword (weapon, +5 damage)
                - Sharp Steel Dagger (weapon, +3 damage)
                - Worn Leather Armor (armor, +2 defense)
                - Heavy Chainmail (armor, +4 defense)

                Test ALL item/inventory mechanics systematically:
                1. Look around - See what items are in the room
                2. Examine items - 'look <item>' to inspect 1-2 items before taking
                3. Take items - Pick up 2-3 different items (weapons, armor)
                4. Check inventory - Use 'inventory' to verify items were picked up
                5. Examine inventory items - 'look <item>' on items you're carrying
                6. Equip weapon - 'equip <weapon>' to equip a weapon
                7. Look at equipped weapon - Verify "(equipped)" tag appears
                8. Equip armor - 'equip <armor>' to equip armor
                9. Drop item - 'drop <item>' to drop something
                10. Look around - Verify dropped item appears in room
                11. Take item back - Pick up the dropped item again

                DO NOT:
                - Try to move to other rooms (stay in Armory)
                - Try to use compound commands ("look and take" - split them)
                - Retry failed commands (just move to next test)
                - Look for items that don't exist (only test the 4 items listed above)

                Target: ~12-15 actions covering all mechanics
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
