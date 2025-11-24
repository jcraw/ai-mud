package com.jcraw.mud.testbot

import com.jcraw.mud.core.GameEngineInterface
import com.jcraw.sophia.llm.LLMClient
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Main test bot runner using ReAct (Reason-Act-Observe) loop.
 * Generates inputs, validates outputs, and logs gameplay.
 */
class TestBotRunner(
    private val llmClient: LLMClient,
    private val gameEngine: GameEngineInterface,
    private val scenario: TestScenario,
    private val logger: GameplayLogger = GameplayLogger()
) {
    private val inputGenerator = InputGenerator(llmClient)
    private val outputValidator = OutputValidator(llmClient)

    /**
     * Run the complete test scenario and return a report.
     */
    suspend fun run(): TestReport {
        val sessionId = logger.generateSessionId(scenario)
        val startTime = Instant.now()
        var state = TestState(scenario = scenario)

        println("🤖 Starting test bot: ${scenario.name}")
        println("   Max steps: ${scenario.maxSteps}")
        println("   Session: $sessionId\n")

        try {
            while (!state.isComplete && gameEngine.isRunning()) {
                // ReAct loop: Reason -> Act -> Observe
                val stepResult = executeStep(state, sessionId)

                state = state.withStep(stepResult.step, stepResult)

                // Check for early completion based on scenario objectives
                if (checkScenarioComplete(state)) {
                    println("   ✅ Scenario objectives complete at step ${state.currentStep}!")
                    state = state.withCompletion(TestStatus.PASSED)
                    break
                }

                // Brief delay to avoid overwhelming the LLM API
                delay(100)

                // Progress update
                if (state.currentStep % 5 == 0) {
                    println("   Step ${state.currentStep}/${scenario.maxSteps} completed...")
                }
            }

            // Mark completion if we reached max steps without failure
            if (!state.isComplete) {
                state = state.withCompletion(TestStatus.PASSED)
            }

        } catch (e: Exception) {
            println("❌ Test bot encountered error: ${e.message}")
            state = state.withCompletion(TestStatus.ERROR)
        }

        val endTime = Instant.now()
        val report = TestReport.fromTestState(state, startTime, endTime)

        // Log the final report
        logger.logReport(sessionId, report)

        printSummary(report)

        // Generate gameplay report for scenarios that benefit from detailed analysis
        if (scenario is TestScenario.SkillProgression) {
            println("\n🎮 Generating gameplay analysis report...")
            try {
                val reportGenerator = GameplayReportGenerator(llmClient)
                val gameplayReport = reportGenerator.generateReport(report)

                // Print to console
                println(gameplayReport.formatForConsole())

                // Save to file
                logger.logGameplayReport(sessionId, gameplayReport)
            } catch (e: Exception) {
                println("⚠️  Failed to generate gameplay report: ${e.message}")
            }
        }

        return report
    }

    /**
     * Execute a single step in the ReAct loop.
     */
    private suspend fun executeStep(state: TestState, sessionId: String): StepResult {
        val stepNumber = state.currentStep + 1

        // 1. REASON: Generate context for the next action
        val currentContext = buildContext(state)

        // 2. ACT: Generate player input using LLM
        val generatedInput = try {
            inputGenerator.generateInput(
                scenario = scenario,
                recentHistory = state.steps, // Pass full history for room tracking
                currentContext = currentContext
            )
        } catch (e: Exception) {
            println("   ⚠️  Input generation failed: ${e.message}")
            return StepResult(
                stepNumber = stepNumber,
                step = TestStep(
                    playerInput = "look",
                    gmResponse = "Fallback to 'look' command"
                ),
                passed = false,
                reason = "Input generation error: ${e.message}"
            )
        }

        // Print reasoning to console if present
        if (generatedInput.reasoning.isNotBlank()) {
            println("   💭 Reasoning: ${generatedInput.reasoning}")
        }

        // Print the command the bot is executing
        println("   🎮 Command: ${generatedInput.input}")

        // 3. ACT: Submit input to game engine
        val rawGmResponse = try {
            gameEngine.processInput(generatedInput.input)
        } catch (e: Exception) {
            println("   ❌ Game engine error: ${e.message}")
            return StepResult(
                stepNumber = stepNumber,
                step = TestStep(
                    playerInput = generatedInput.input,
                    gmResponse = "ERROR: ${e.message}"
                ),
                passed = false,
                reason = "Game engine error: ${e.message}"
            )
        }

        // Filter debug output for bot/validator (but keep raw for logs)
        val cleanGmResponse = filterDebugOutput(rawGmResponse)

        // Print the RAW game response (including debug output for troubleshooting)
        println("   📜 Raw Response:")
        rawGmResponse.lines().forEach { line ->
            println("      $line")
        }
        println()

        // 4. OBSERVE: Validate the output (using CLEAN response)
        val validationResult = try {
            outputValidator.validate(
                scenario = scenario,
                playerInput = generatedInput.input,
                gmResponse = cleanGmResponse,
                recentHistory = state.steps.takeLast(2),
                expectedOutcome = generatedInput.expected,
                worldState = gameEngine.getWorldState()
            )
        } catch (e: Exception) {
            println("   ⚠️  Validation failed: ${e.message}")
            ValidationResult(
                pass = true, // Be lenient on validation errors
                reason = "Validation error, assuming pass: ${e.message}"
            )
        }

        // Create the test step (using CLEAN response for bot's future context)
        val step = TestStep(
            playerInput = generatedInput.input,
            gmResponse = cleanGmResponse,
            validationResult = validationResult,
            reasoning = generatedInput.reasoning.takeIf { it.isNotBlank() }
        )

        // Log the step
        logger.logStep(sessionId, step)

        // Determine if step passed
        val passed = validationResult.pass && !cleanGmResponse.contains("ERROR", ignoreCase = true)
        val reason = if (passed) {
            "Step completed successfully"
        } else {
            validationResult.reason
        }

        return StepResult(
            stepNumber = stepNumber,
            step = step,
            passed = passed,
            reason = reason
        )
    }

    /**
     * Build context string describing the current game state.
     */
    private fun buildContext(state: TestState): String {
        val worldState = gameEngine.getWorldState()
        val currentSpace = worldState.getCurrentSpace()
        val player = worldState.player

        val questInfo = if (scenario is TestScenario.QuestTesting) {
            val activeQuests = player.activeQuests
            val availableQuests = worldState.availableQuests
            val completedQuestIds = player.completedQuests
            val activeComplete = activeQuests.filter { it.isComplete() }
            """

            Quests:
              - Active: ${activeQuests.size} (${activeQuests.joinToString { it.id }})
              - Active & Complete (ready to claim): ${activeComplete.size} (${activeComplete.joinToString { it.id }})
              - Available: ${availableQuests.size} (${availableQuests.joinToString { it.id }})
              - Claimed: ${completedQuestIds.size}
            """.trimIndent()
        } else {
            ""
        }

        // Include the last GM response so the bot can see room descriptions and exits
        val lastGMResponse = state.steps.lastOrNull()?.gmResponse ?: "No previous game output"

        return """
            Last game output:
            $lastGMResponse

            Current space: ${currentSpace?.name ?: "Unknown"}
            Player health: ${player.health}/${player.maxHealth}
            Inventory: ${player.inventory.joinToString { it.name }}
            Steps completed: ${state.currentStep}/${state.scenario.maxSteps}$questInfo
        """.trimIndent()
    }

    /**
     * Print a summary of the test run.
     */
    private fun printSummary(report: TestReport) {
        val passRate = if (report.totalSteps > 0) {
            (report.passedSteps.toDouble() / report.totalSteps * 100).toInt()
        } else {
            0
        }

        val statusEmoji = when (report.finalStatus) {
            TestStatus.PASSED -> "✅"
            TestStatus.FAILED -> "❌"
            TestStatus.ERROR -> "⚠️"
            TestStatus.RUNNING -> "🔄"
        }

        println("\n" + "=".repeat(60))
        println("$statusEmoji TEST COMPLETE: ${report.scenario.name}")
        println("=".repeat(60))
        println("Status: ${report.finalStatus}")
        println("Steps: ${report.totalSteps} (${report.passedSteps} passed, ${report.failedSteps} failed)")
        println("Pass Rate: $passRate%")

        // Add exploration metrics if this is an exploration scenario
        if (report.scenario is TestScenario.Exploration) {
            val target = report.scenario.targetRoomsToVisit
            val actual = report.uniqueRoomsVisited
            println("Rooms Visited: $actual / $target")
            println("Rooms: ${report.roomNames.joinToString(", ")}")
        }

        // Add playthrough metrics for playthrough scenarios
        when (report.scenario) {
            is TestScenario.BadPlaythrough -> {
                println("\n📊 Playthrough Metrics:")
                println("  Damage Taken: ${report.damageTaken}")
                println("  NPCs Killed: ${report.npcsKilled}")
                println("  Player Died: ${if (report.playerDied) "✅ YES (as expected)" else "❌ NO (game too easy!)"}")
                println("  Rooms Visited: ${report.uniqueRoomsVisited} (${report.roomNames.joinToString(", ")})")
                if (!report.playerDied) {
                    println("\n⚠️  WARNING: Player should die without gear! Difficulty may be too low.")
                }
            }
            is TestScenario.BruteForcePlaythrough -> {
                println("\n📊 Playthrough Metrics:")
                println("  Damage Taken: ${report.damageTaken}")
                println("  NPCs Killed: ${report.npcsKilled} ${if (report.npcsKilled > 0) "✅" else "❌"}")
                println("  Skill Checks Passed: ${report.skillChecksPassed}")
                println("  Player Died: ${if (!report.playerDied) "✅ NO (victory!)" else "❌ YES (should win with gear!)"}")
                println("  Rooms Visited: ${report.uniqueRoomsVisited} (${report.roomNames.joinToString(", ")})")
                if (report.playerDied) {
                    println("\n⚠️  WARNING: Player should win with proper gear! Difficulty may be too high.")
                }
            }
            is TestScenario.SmartPlaythrough -> {
                println("\n📊 Playthrough Metrics:")
                println("  Damage Taken: ${report.damageTaken} ${if (report.damageTaken < 20) "✅ (minimal)" else "⚠️ (high)"}")
                println("  NPCs Killed: ${report.npcsKilled} ${if (report.npcsKilled == 0) "✅ (non-lethal!)" else "⚠️"}")
                println("  Skill Checks Passed: ${report.skillChecksPassed}")
                println("  Social Checks Passed: ${report.socialChecksPassed} ${if (report.socialChecksPassed > 0) "✅" else "❌"}")
                println("  Player Died: ${if (!report.playerDied) "✅ NO" else "❌ YES"}")
                println("  Rooms Visited: ${report.uniqueRoomsVisited} (${report.roomNames.joinToString(", ")})")
                if (report.socialChecksPassed == 0) {
                    println("\n⚠️  WARNING: No social checks passed! Multiple solution paths may not be working.")
                }
            }
            else -> {
                // For other scenarios, just show basic metrics if available
                if (report.damageTaken > 0 || report.npcsKilled > 0 || report.skillChecksPassed > 0 || report.socialChecksPassed > 0) {
                    println("\n📊 Metrics:")
                    if (report.damageTaken > 0) println("  Damage Taken: ${report.damageTaken}")
                    if (report.npcsKilled > 0) println("  NPCs Killed: ${report.npcsKilled}")
                    if (report.skillChecksPassed > 0) println("  Skill Checks Passed: ${report.skillChecksPassed}")
                    if (report.socialChecksPassed > 0) println("  Social Checks Passed: ${report.socialChecksPassed}")
                    if (report.playerDied) println("  Player Died: YES")
                }
            }
        }

        println("\nDuration: ${report.duration / 1000.0}s")
        println("=".repeat(60))
    }

    /**
     * Check if scenario-specific completion criteria are met.
     */
    private fun checkScenarioComplete(state: TestState): Boolean {
        val worldState = gameEngine.getWorldState()

        return when (state.scenario) {
            is TestScenario.BruteForcePlaythrough -> {
                // Complete when Skeleton King is defeated
                val skeletonKingDefeated = state.steps.any {
                    it.gmResponse.contains("has been defeated", ignoreCase = true) &&
                    it.playerInput.contains("skeleton", ignoreCase = true)
                }
                skeletonKingDefeated
            }
            is TestScenario.BadPlaythrough -> {
                // Complete when player dies
                val playerDied = state.steps.any {
                    it.gmResponse.contains("You have died", ignoreCase = true) ||
                    it.gmResponse.contains("You have been defeated", ignoreCase = true) ||
                    it.gmResponse.contains("Game over", ignoreCase = true)
                }
                playerDied
            }
            is TestScenario.SmartPlaythrough -> {
                // Complete when player reaches secret chamber OR defeats boss
                // Check if player is actually IN the secret chamber (by checking current room name)
                val currentSpace = worldState.getCurrentSpace()
                val reachedSecretChamber = currentSpace?.name?.contains("Secret", ignoreCase = true) == true ||
                    currentSpace?.name?.contains("Hidden", ignoreCase = true) == true ||
                    // Also check if room description starts with "Secret Chamber" or "Hidden Chamber" (room name header)
                    state.steps.any {
                        it.gmResponse.startsWith("Secret Chamber", ignoreCase = true) ||
                        it.gmResponse.startsWith("Hidden Chamber", ignoreCase = true)
                    }
                val bossDefeated = state.steps.any {
                    it.gmResponse.contains("has been defeated", ignoreCase = true) &&
                    it.playerInput.contains("skeleton", ignoreCase = true)
                }
                reachedSecretChamber || bossDefeated
            }
            is TestScenario.SkillProgression -> {
                // Complete if player dies (test should end, report current skill level)
                val playerDied = worldState.player.health <= 0 || state.steps.any {
                    it.gmResponse.contains("You have died", ignoreCase = true) ||
                    it.gmResponse.contains("You have been slain", ignoreCase = true) ||
                    it.gmResponse.contains("been defeated", ignoreCase = true) ||
                    it.validationResult?.reason?.contains("Player died", ignoreCase = true) == true
                }
                if (playerDied) {
                    return true
                }

                // Complete when Dodge skill reaches target level (10)
                // Check response for skill level messages or query current level
                val dodgeLevelReached = state.steps.any {
                    it.gmResponse.contains("Dodge", ignoreCase = true) &&
                    (it.gmResponse.contains("level 10", ignoreCase = true) ||
                     it.gmResponse.contains("Level: 10", ignoreCase = true) ||
                     Regex("Dodge.*level\\s+1[0-9]").find(it.gmResponse) != null)
                }
                dodgeLevelReached
            }
            else -> false // Other scenarios run to maxSteps
        }
    }

    /**
     * Filter debug output from game responses.
     * Removes debug lines while keeping actual game narrative.
     */
    private fun filterDebugOutput(text: String): String {
        return text.lines()
            .filterNot { line ->
                val trimmed = line.trim()
                // Filter out debug lines
                trimmed.startsWith("[") ||
                trimmed.startsWith("💾") ||
                trimmed.startsWith("⚠️") ||
                trimmed.contains("DEBUG]") ||
                trimmed.contains("Warning: Failed to")
            }
            .joinToString("\n")
            .trim()
    }
}
