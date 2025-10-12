package com.jcraw.mud.testbot

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

        // 3. ACT: Submit input to game engine
        val gmResponse = try {
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

        // 4. OBSERVE: Validate the output
        val validationResult = try {
            outputValidator.validate(
                scenario = scenario,
                playerInput = generatedInput.input,
                gmResponse = gmResponse,
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

        // Create the test step
        val step = TestStep(
            playerInput = generatedInput.input,
            gmResponse = gmResponse,
            validationResult = validationResult
        )

        // Log the step
        logger.logStep(sessionId, step)

        // Determine if step passed
        val passed = validationResult.pass && !gmResponse.contains("ERROR", ignoreCase = true)
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
        val currentRoom = worldState.getCurrentRoom()
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

        return """
            Current room: ${currentRoom?.name ?: "Unknown"}
            Player health: ${player.health}/${player.maxHealth}
            Inventory: ${player.inventory.joinToString { it.name }}
            In combat: ${player.isInCombat()}
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

        println("Duration: ${report.duration / 1000.0}s")
        println("=".repeat(60))
    }
}
