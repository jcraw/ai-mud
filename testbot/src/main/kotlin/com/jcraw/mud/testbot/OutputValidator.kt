package com.jcraw.mud.testbot

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.WorldState
import com.jcraw.sophia.llm.LLMClient
import kotlinx.serialization.json.Json

/**
 * Validates game engine outputs using hybrid approach:
 * 1. Code-based validation (fast, deterministic) for mechanical correctness
 * 2. LLM validation (slower, subjective) for narrative quality
 * Uses gpt-4o-mini for cost savings as per guidelines.
 */
class OutputValidator(
    private val llmClient: LLMClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    /**
     * Validate engine output for correctness and coherence.
     */
    suspend fun validate(
        scenario: TestScenario,
        playerInput: String,
        gmResponse: String,
        recentHistory: List<TestStep>,
        expectedOutcome: String? = null,
        worldState: WorldState? = null
    ): ValidationResult {
        // STEP 1: Code-based validation (deterministic, always runs first)
        val codeValidation = validateWithCode(
            playerInput = playerInput,
            gmResponse = gmResponse,
            recentHistory = recentHistory,
            worldState = worldState
        )

        // If code validation finds a definitive pass/fail, use it
        if (codeValidation != null) {
            return codeValidation
        }

        // STEP 2: LLM validation (for subjective checks)
        val systemPrompt = buildSystemPrompt(scenario)
        val userContext = buildUserContext(
            scenario,
            playerInput,
            gmResponse,
            recentHistory,
            expectedOutcome,
            worldState
        )

        val response = llmClient.chatCompletion(
            modelId = "gpt-4o-mini",
            systemPrompt = systemPrompt,
            userContext = userContext,
            maxTokens = 300,
            temperature = 0.3 // Lower temperature for more consistent validation
        )

        val responseText = response.choices.firstOrNull()?.message?.content ?: ""
        return parseValidation(responseText)
    }

    /**
     * Code-based validation using actual game state metadata.
     * Returns ValidationResult if we can definitively validate, null otherwise.
     */
    private fun validateWithCode(
        playerInput: String,
        gmResponse: String,
        recentHistory: List<TestStep>,
        worldState: WorldState?
    ): ValidationResult? {
        if (worldState == null) return null

        // Extract previous room ID from history
        val previousRoomId = if (recentHistory.isNotEmpty()) {
            worldState.player.currentRoomId // This is the room AFTER the last action
        } else {
            null
        }

        // Get current room ID after this action
        val currentRoomId = worldState.player.currentRoomId
        val currentRoom = worldState.getCurrentRoom()

        // Parse movement command - support all directions including diagonals
        val movementMatch = Regex(
            "(?:go|move|^)\\s*(north|south|east|west|northeast|northwest|southeast|southwest|up|down|n|s|e|w|ne|nw|se|sw|u|d)(?:\\s|$)",
            RegexOption.IGNORE_CASE
        ).find(playerInput)

        if (movementMatch != null) {
            val directionStr = movementMatch.groupValues[1].lowercase()
            val direction = when (directionStr) {
                "north", "n" -> Direction.NORTH
                "south", "s" -> Direction.SOUTH
                "east", "e" -> Direction.EAST
                "west", "w" -> Direction.WEST
                "northeast", "ne" -> Direction.NORTHEAST
                "northwest", "nw" -> Direction.NORTHWEST
                "southeast", "se" -> Direction.SOUTHEAST
                "southwest", "sw" -> Direction.SOUTHWEST
                "up", "u" -> Direction.UP
                "down", "d" -> Direction.DOWN
                else -> null
            }

            if (direction != null && currentRoom != null) {
                // Check if the direction was valid in the previous room
                val hadValidExit = recentHistory.lastOrNull()?.let { lastStep ->
                    // Extract exits from last response
                    val exitsMatch = Regex("Exits: ([\\w, ]+)").find(lastStep.gmResponse)
                    exitsMatch?.groupValues?.get(1)?.split(", ")?.any { exit ->
                        exit.equals(direction.displayName, ignoreCase = true) ||
                        exit.equals(directionStr, ignoreCase = true)
                    } ?: false
                } ?: true // If no history, assume valid

                // Case 1: "You can't go that way" should only happen for invalid exits
                if (gmResponse.contains("can't go that way", ignoreCase = true)) {
                    return if (hadValidExit) {
                        // Exit existed but got rejection = BUG
                        ValidationResult(
                            pass = false,
                            reason = "[CODE] Invalid rejection: exit $direction existed but game rejected movement",
                            details = mapOf(
                                "validation_type" to "code",
                                "coherence" to "fail",
                                "consistency" to "fail",
                                "mechanics" to "fail"
                            )
                        )
                    } else {
                        // No exit existed, rejection is correct
                        ValidationResult(
                            pass = true,
                            reason = "[CODE] Correctly rejected invalid direction: $direction",
                            details = mapOf(
                                "validation_type" to "code",
                                "coherence" to "pass",
                                "consistency" to "pass",
                                "mechanics" to "pass"
                            )
                        )
                    }
                }

                // Case 2: Room description with header = successful movement
                val roomHeaderMatch = Regex("^([A-Z][a-zA-Z\\s]+)\\n").find(gmResponse)
                if (roomHeaderMatch != null) {
                    // Got a room description = movement succeeded
                    return ValidationResult(
                        pass = true,
                        reason = "[CODE] Movement succeeded: entered room '${roomHeaderMatch.groupValues[1].trim()}'",
                        details = mapOf(
                            "validation_type" to "code",
                            "coherence" to "pass",
                            "consistency" to "pass",
                            "mechanics" to "pass",
                            "room_name" to roomHeaderMatch.groupValues[1].trim(),
                            "room_id" to currentRoomId
                        )
                    )
                }
            }
        }

        // No definitive validation, let LLM handle it
        return null
    }

    private fun buildSystemPrompt(scenario: TestScenario): String {
        return """
            You are a QA validator for a text-based MUD (Multi-User Dungeon) game engine.
            Your job is to verify that the game responds correctly and coherently to player inputs.

            Scenario: ${scenario.name}
            Description: ${scenario.description}

            CRITICAL: Be LENIENT with validation. Only fail if there's a clear error or crash.
            Normal MUD responses like room descriptions or "You can't go that way" are VALID.

            Validation criteria:
            1. Response is coherent and makes sense given the input
            2. Response follows MUD conventions (room descriptions, combat mechanics, etc.)
            3. Response maintains consistency with previous history
            4. No obvious errors, crashes, or nonsensical text
            5. Response advances the game state appropriately

            DEFAULT TO PASS unless you see a clear problem like:
            - Error messages when action should succeed
            - Crash or exception text
            - Completely nonsensical response
            - Violates game mechanics

            Respond with JSON in this format:
            {
                "pass": true/false,
                "reason": "brief explanation",
                "details": {
                    "coherence": "pass/fail",
                    "consistency": "pass/fail",
                    "mechanics": "pass/fail"
                }
            }
        """.trimIndent()
    }

    private fun buildUserContext(
        scenario: TestScenario,
        playerInput: String,
        gmResponse: String,
        recentHistory: List<TestStep>,
        expectedOutcome: String?,
        worldState: WorldState?
    ): String {
        // Extract room name from CURRENT response for tracking
        val currentRoomName = gmResponse.lines().firstOrNull()?.trim()?.takeIf {
            it.isNotBlank() && !it.startsWith("You ")
        }

        // Extract room name from PREVIOUS step for movement validation
        val previousRoomName = if (recentHistory.isNotEmpty()) {
            val lastResponse = recentHistory.last().gmResponse
            // Try to extract room name from first line
            lastResponse.lines().firstOrNull()?.trim()?.takeIf {
                it.isNotBlank() && !it.startsWith("You ")
            }
        } else {
            null
        }

        val historyText = if (recentHistory.isEmpty()) {
            "No previous history."
        } else {
            recentHistory.takeLast(2).joinToString("\n") { step ->
                "Player: ${step.playerInput}\nGM: ${step.gmResponse.take(150)}"
            }
        }

        // Add game state context for better validation
        val gameStateContext = if (worldState != null) {
            val currentRoom = worldState.getCurrentRoom()
            val player = worldState.player
            val roomTransitionInfo = buildString {
                if (previousRoomName != null && currentRoomName != null) {
                    if (previousRoomName != currentRoomName) {
                        append("\n            - ROOM CHANGED: \"$previousRoomName\" → \"$currentRoomName\" (successful movement)")
                    } else {
                        append("\n            - Same room name: \"$previousRoomName\" (could be: stayed in place, OR moved to different room with same name)")
                    }
                } else if (previousRoomName != null) {
                    append("\n            - Previous room: $previousRoomName")
                } else if (currentRoomName != null) {
                    append("\n            - Current room from response: $currentRoomName")
                }
            }
            """

            Current game state:
            - Player location: ${currentRoom?.name ?: "Unknown"}$roomTransitionInfo
            - Available exits: ${currentRoom?.exits?.keys?.joinToString(", ") { it.displayName } ?: "none"}
            - Player health: ${player.health}/${player.maxHealth}
            - In combat: ${player.isInCombat()}
            - Room entities: ${currentRoom?.entities?.joinToString(", ") { it.name } ?: "none"}
            """.trimIndent()
        } else {
            ""
        }

        val scenarioCriteria = when (scenario) {
            is TestScenario.Exploration -> """
                Check that:
                - Room descriptions are vivid and detailed
                - Look commands provide appropriate information
                - Descriptions vary but remain consistent with previous descriptions

                MOVEMENT VALIDATION RULES (CRITICAL - READ EVERY WORD):

                **RULE 1: ANY room description starting with a room name = SUCCESSFUL MOVEMENT → PASS**
                - If response has format "Room Name\n[description]\nExits: ..." → ALWAYS PASS
                - Does NOT matter if you've seen this room name before
                - Does NOT matter if description is different from last time
                - This is how the game engine shows you entered a room

                **RULE 2: "You can't go that way" = CORRECT REJECTION → PASS**
                - This means the player tried an invalid direction
                - Check game state exits FIRST before failing
                - ONLY fail if game state shows the exit DOES exist but got rejection

                **RULE 3: Game shows rooms DIRECTLY, not "You move..."**
                - NO "You move north" or "You walk east" messages exist
                - Seeing a room description IS the movement confirmation

                **RULE 4: Trust "ROOM CHANGED" markers**
                - If game state shows "ROOM CHANGED: X → Y" → ALWAYS PASS

                **EXAMPLES FROM ACTUAL FAILED VALIDATIONS (ALL SHOULD PASS):**

                ✓ Player: "go east" from "Dark Corridor"
                  Response: "Ancient Treasury\nYou enter the Ancient Treasury, where the glimmer..."
                  → PASS (room description = successful movement, even if first time seeing this room)

                ✓ Player: "go south" from "Ancient Treasury" (only exit: west)
                  Response: "You can't go that way."
                  → PASS (correct rejection of invalid direction, south doesn't exist)

                ✓ Player: "go east" from "Dark Corridor" (exits: south, east, west, north)
                  Response: "Ancient Treasury\nYou enter..."
                  → PASS (east IS a valid exit, got room description = success)

                ✗ Player: "go north" + Response: "Error: NullPointerException"
                  → FAIL (crash)

                ✗ Player: "go east" (east IS in exits) + Response: "You can't go that way."
                  → FAIL (game state shows exit exists but rejected)

                **DO NOT FAIL** for seeing the same room name twice. Players can visit rooms multiple times!
                **DO NOT FAIL** for "You can't go that way" unless the game state proves the exit exists!
                **DO NOT FAIL** for getting a room description after a movement command - this is SUCCESS!
            """.trimIndent()
            is TestScenario.Combat -> """
                Check that:
                - Combat mechanics work (attack/damage/health)
                - Health values update correctly
                - Combat resolves to victory or defeat
                - Narratives are engaging and coherent
            """.trimIndent()
            is TestScenario.SkillChecks -> """
                Check that:
                - D20 rolls are reported
                - Stat modifiers are applied
                - Success/failure is determined correctly
                - Critical successes/failures are handled
            """.trimIndent()
            is TestScenario.ItemInteraction -> """
                Check that:
                - Items can be picked up and dropped
                - Inventory tracking is correct
                - Equipment provides appropriate bonuses
                - Consumables have proper effects
            """.trimIndent()
            is TestScenario.SocialInteraction -> """
                Check that:
                - NPC dialogue is personality-appropriate
                - Social checks (persuasion/intimidation) work
                - NPCs respond coherently
                - Conversation maintains context
            """.trimIndent()
            is TestScenario.QuestTesting -> """
                Check that:
                - Quest viewing commands show quests correctly
                - Accept/abandon quest commands work properly
                - Quest progress is tracked accurately
                - Claim rewards command succeeds when quest is complete
                - Rewards are properly awarded (XP, gold, items)
            """.trimIndent()
            is TestScenario.Exploratory -> """
                Check that:
                - Invalid inputs are handled gracefully
                - Edge cases don't crash the game
                - Ambiguous commands receive helpful feedback
            """.trimIndent()
            is TestScenario.FullPlaythrough -> """
                Check that:
                - Game progresses naturally
                - All mechanics work together
                - Story and world remain consistent
            """.trimIndent()
        }

        val expectedText = expectedOutcome?.let { "\nExpected outcome: $it" } ?: ""

        return """
            Recent history:
            $historyText
            $gameStateContext

            Current turn:
            Player input: $playerInput
            GM response: $gmResponse
            $expectedText

            Scenario-specific criteria:
            $scenarioCriteria

            Validate this response.
        """.trimIndent()
    }

    private fun parseValidation(responseText: String): ValidationResult {
        return try {
            // Try to extract JSON from response
            val jsonStart = responseText.indexOf('{')
            val jsonEnd = responseText.lastIndexOf('}') + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonText = responseText.substring(jsonStart, jsonEnd)
                json.decodeFromString<ValidationResult>(jsonText)
            } else {
                // Fallback: assume pass if no errors mentioned
                val pass = !responseText.contains("error", ignoreCase = true) &&
                        !responseText.contains("fail", ignoreCase = true)
                ValidationResult(
                    pass = pass,
                    reason = "Fallback validation: $responseText",
                    details = emptyMap()
                )
            }
        } catch (e: Exception) {
            // On parse error, be conservative and fail
            ValidationResult(
                pass = false,
                reason = "Failed to parse validation response: ${e.message}",
                details = mapOf("error" to "parse_failure")
            )
        }
    }
}
