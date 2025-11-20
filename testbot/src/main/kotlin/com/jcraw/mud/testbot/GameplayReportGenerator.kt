package com.jcraw.mud.testbot

import com.jcraw.sophia.llm.LLMClient
import kotlinx.serialization.Serializable

/**
 * Generates gameplay analysis reports using LLM.
 * Analyzes completed test runs and provides feedback on:
 * - Progression feedback (pacing, difficulty to level up)
 * - Combat balance (fight difficulty, damage tuning)
 * - Clarity of mechanics (feedback visibility, understanding)
 * - Overall enjoyment (fun factor, player experience)
 */
class GameplayReportGenerator(
    private val llmClient: LLMClient
) {
    /**
     * Generate a comprehensive gameplay report from a completed test.
     */
    suspend fun generateReport(report: TestReport): GameplayReport {
        val systemPrompt = buildSystemPrompt(report.scenario)
        val userContext = buildUserContext(report)

        val response = llmClient.chatCompletion(
            modelId = "gpt-4o-mini",
            systemPrompt = systemPrompt,
            userContext = userContext,
            maxTokens = 1000,
            temperature = 0.7  // Higher temperature for more creative analysis
        )

        val responseText = response.choices.firstOrNull()?.message?.content ?: ""
        return parseGameplayReport(responseText, report)
    }

    private fun buildSystemPrompt(scenario: TestScenario): String {
        return """
            You are an expert game designer analyzing a completed playthrough of an AI MUD game.
            Your task is to provide honest, insightful feedback on the gameplay experience.

            Scenario: ${scenario.name}
            Description: ${scenario.description}

            Analyze the gameplay and provide feedback in these four categories:

            1. PROGRESSION FEEDBACK (Score: X/10)
               - How easy/hard was it to make progress toward the goal?
               - Was the pacing good? Too slow? Too fast?
               - Were progression milestones satisfying?
               - Suggestions for improvement

            2. COMBAT BALANCE (Score: X/10)
               - Were fights too easy/hard/just right?
               - Was damage balanced appropriately?
               - Did combat feel fair and engaging?
               - Suggestions for improvement

            3. CLARITY OF MECHANICS (Score: X/10)
               - Was it clear how to achieve the goal?
               - Was feedback to the player clear and helpful?
               - Could mechanics be explained better?
               - Suggestions for improvement

            4. OVERALL ENJOYMENT (Score: X/10)
               - Would a real player enjoy this experience?
               - What worked well? What didn't?
               - Fun factor and engagement level
               - Suggestions for improvement

            Be specific, cite examples from the playthrough, and provide actionable recommendations.
            Use a direct, no-nonsense tone (Linus Torvalds style).
            Format your response as plain text with clear section headers.
        """.trimIndent()
    }

    private fun buildUserContext(report: TestReport): String {
        // Extract key metrics
        val progressionMetrics = buildProgressionMetrics(report)
        val combatMetrics = buildCombatMetrics(report)
        val feedbackSamples = buildFeedbackSamples(report)

        return """
            PLAYTHROUGH SUMMARY:
            - Scenario: ${report.scenario.name}
            - Duration: ${report.duration / 1000.0}s
            - Total Steps: ${report.totalSteps}
            - Pass Rate: ${if (report.totalSteps > 0) (report.passedSteps * 100 / report.totalSteps) else 0}%
            - Final Status: ${report.finalStatus}

            PROGRESSION METRICS:
            $progressionMetrics

            COMBAT METRICS:
            $combatMetrics

            FEEDBACK SAMPLES (last 10 actions):
            $feedbackSamples

            Analyze this playthrough and provide detailed feedback in the four categories.
        """.trimIndent()
    }

    private fun buildProgressionMetrics(report: TestReport): String {
        return when (report.scenario) {
            is TestScenario.SkillProgression -> {
                // Extract Dodge level progression from steps
                val skillLevels = report.steps.mapNotNull { step ->
                    val match = Regex("Dodge.*level\\s+(\\d+)", RegexOption.IGNORE_CASE)
                        .find(step.gmResponse)
                    match?.groupValues?.get(1)?.toIntOrNull()
                }

                val startLevel = skillLevels.firstOrNull() ?: 0
                val endLevel = skillLevels.lastOrNull() ?: 0
                val levelUps = skillLevels.zipWithNext().count { (prev, curr) -> curr > prev }
                val averageActionsPerLevel = if (levelUps > 0) report.totalSteps / levelUps else 0

                """
                - Starting Dodge Level: $startLevel
                - Ending Dodge Level: $endLevel
                - Total Level-Ups: $levelUps
                - Average Actions per Level: $averageActionsPerLevel
                - Goal Reached: ${if (endLevel >= 10) "YES ✅" else "NO ❌ (only $endLevel/10)"}
                """.trimIndent()
            }
            else -> {
                """
                - Rooms Visited: ${report.uniqueRoomsVisited}
                - NPCs Killed: ${report.npcsKilled}
                - Skill Checks Passed: ${report.skillChecksPassed}
                - Social Checks Passed: ${report.socialChecksPassed}
                """.trimIndent()
            }
        }
    }

    private fun buildCombatMetrics(report: TestReport): String {
        return """
            - Combat Rounds: ${report.combatRounds}
            - Damage Taken: ${report.damageTaken}
            - NPCs Killed: ${report.npcsKilled}
            - Player Died: ${if (report.playerDied) "YES" else "NO"}
        """.trimIndent()
    }

    private fun buildFeedbackSamples(report: TestReport): String {
        return report.steps.takeLast(10).joinToString("\n\n") { step ->
            val reasoningLine = if (step.reasoning != null) {
                "  [Reasoning] ${step.reasoning}\n"
            } else {
                ""
            }
            """
            $reasoningLine  [Player] ${step.playerInput}
              [GM] ${step.gmResponse.take(150)}${if (step.gmResponse.length > 150) "..." else ""}
            """.trimIndent()
        }
    }

    private fun parseGameplayReport(responseText: String, report: TestReport): GameplayReport {
        // Extract scores using regex
        val progressionScore = extractScore(responseText, "PROGRESSION FEEDBACK")
        val combatScore = extractScore(responseText, "COMBAT BALANCE")
        val clarityScore = extractScore(responseText, "CLARITY OF MECHANICS")
        val enjoymentScore = extractScore(responseText, "OVERALL ENJOYMENT")

        return GameplayReport(
            scenario = report.scenario,
            duration = report.duration,
            totalSteps = report.totalSteps,
            progressionScore = progressionScore,
            combatScore = combatScore,
            clarityScore = clarityScore,
            enjoymentScore = enjoymentScore,
            fullAnalysis = responseText
        )
    }

    private fun extractScore(text: String, sectionHeader: String): Int {
        val scorePattern = Regex("$sectionHeader.*?Score:\\s*(\\d+)/10", RegexOption.IGNORE_CASE)
        val match = scorePattern.find(text)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 5  // Default to 5 if not found
    }
}

/**
 * Structured gameplay report with scores and analysis.
 */
@Serializable
data class GameplayReport(
    val scenario: TestScenario,
    val duration: Long,
    val totalSteps: Int,
    val progressionScore: Int,
    val combatScore: Int,
    val clarityScore: Int,
    val enjoymentScore: Int,
    val fullAnalysis: String
) {
    fun formatForConsole(): String {
        val averageScore = (progressionScore + combatScore + clarityScore + enjoymentScore) / 4

        return """

            ${"=".repeat(60)}
            📊 GAMEPLAY REPORT
            ${"=".repeat(60)}
            Scenario: ${scenario.name}
            Duration: ${duration / 1000.0}s
            Total Steps: $totalSteps

            SCORES:
            - Progression Feedback: $progressionScore/10
            - Combat Balance: $combatScore/10
            - Clarity of Mechanics: $clarityScore/10
            - Overall Enjoyment: $enjoymentScore/10
            - AVERAGE: $averageScore/10

            ${"=".repeat(60)}

            $fullAnalysis

            ${"=".repeat(60)}
        """.trimIndent()
    }
}
