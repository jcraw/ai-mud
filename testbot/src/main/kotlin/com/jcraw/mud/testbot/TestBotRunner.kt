@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

import com.jcraw.mud.core.GameEngineInterface
import com.jcraw.sophia.llm.LLMClient
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Main test bot runner using ReAct (Reason-Act-Observe) loop.
 * Generates inputs, validates outputs, and logs gameplay.
 *
 * Step / context / summary / complete / debug bodies in extracts (MUD-034f).
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
                val stepResult = TestBotStepExecutor.executeStep(
                    state = state,
                    sessionId = sessionId,
                    scenario = scenario,
                    gameEngine = gameEngine,
                    inputGenerator = inputGenerator,
                    outputValidator = outputValidator,
                    logger = logger
                )

                state = state.withStep(stepResult.step, stepResult)

                // Check for early completion based on scenario objectives
                if (TestBotScenarioComplete.checkScenarioComplete(gameEngine, state)) {
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

        TestBotSummary.printSummary(report)

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
}
