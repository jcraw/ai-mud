package com.jcraw.mud.testbot

import com.jcraw.sophia.llm.LLMClient
import kotlinx.serialization.json.Json

/**
 * Validates game engine outputs using LLM.
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
        expectedOutcome: String? = null
    ): ValidationResult {
        val systemPrompt = buildSystemPrompt(scenario)
        val userContext = buildUserContext(
            scenario,
            playerInput,
            gmResponse,
            recentHistory,
            expectedOutcome
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

    private fun buildSystemPrompt(scenario: TestScenario): String {
        return """
            You are a QA validator for a text-based MUD (Multi-User Dungeon) game engine.
            Your job is to verify that the game responds correctly and coherently to player inputs.

            Scenario: ${scenario.name}
            Description: ${scenario.description}

            Validation criteria:
            1. Response is coherent and makes sense given the input
            2. Response follows MUD conventions (room descriptions, combat mechanics, etc.)
            3. Response maintains consistency with previous history
            4. No obvious errors, crashes, or nonsensical text
            5. Response advances the game state appropriately

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
        expectedOutcome: String?
    ): String {
        val historyText = if (recentHistory.isEmpty()) {
            "No previous history."
        } else {
            recentHistory.takeLast(2).joinToString("\n") { step ->
                "Player: ${step.playerInput}\nGM: ${step.gmResponse.take(150)}"
            }
        }

        val scenarioCriteria = when (scenario) {
            is TestScenario.Exploration -> """
                Check that:
                - Room descriptions are vivid and detailed
                - Movement commands properly transition between rooms
                - Look commands provide appropriate information
                - Descriptions vary but remain consistent
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
