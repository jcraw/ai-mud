@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Represents a single step (turn) in a test session.
 */
@Serializable
data class TestStep(
    val timestamp: String = Instant.now().toString(),
    val playerInput: String,
    val gmResponse: String,
    val validationResult: ValidationResult? = null,
    val reasoning: String? = null  // Bot's thought process before action
)

/**
 * Result of LLM validation on engine output.
 */
@Serializable
data class ValidationResult(
    val pass: Boolean,
    val reason: String,
    val details: Map<String, String> = emptyMap()
)

/**
 * Result of a complete test scenario run.
 */
@Serializable
data class StepResult(
    val stepNumber: Int,
    val step: TestStep,
    val passed: Boolean,
    val reason: String
)

/**
 * Immutable test state tracking scenario progress.
 */
@Serializable
data class TestState(
    val scenario: TestScenario,
    val steps: List<TestStep> = emptyList(),
    val results: List<StepResult> = emptyList(),
    val currentStep: Int = 0,
    val isComplete: Boolean = false,
    val finalStatus: TestStatus = TestStatus.RUNNING
) {
    fun withStep(step: TestStep, result: StepResult): TestState {
        val newSteps = steps + step
        val newResults = results + result
        val newStepNumber = currentStep + 1

        // Only complete when we reach max steps (allow recovery from failures)
        val complete = newStepNumber >= scenario.maxSteps

        // Determine final status based on overall pass rate
        val passedCount = newResults.count { it.passed }
        val totalCount = newResults.size
        val passRate = if (totalCount > 0) passedCount.toDouble() / totalCount else 0.0

        return copy(
            steps = newSteps,
            results = newResults,
            currentStep = newStepNumber,
            isComplete = complete,
            finalStatus = when {
                !complete -> TestStatus.RUNNING
                passRate >= 0.8 -> TestStatus.PASSED  // 80% pass rate = success
                passRate >= 0.5 -> TestStatus.FAILED  // 50-80% = partial failure
                else -> TestStatus.FAILED             // <50% = failure
            }
        )
    }

    fun withCompletion(status: TestStatus): TestState {
        return copy(
            isComplete = true,
            finalStatus = status
        )
    }
}

/**
 * Overall status of a test run.
 */
@Serializable
enum class TestStatus {
    RUNNING,
    PASSED,
    FAILED,
    ERROR
}

/**
 * Summary report of a test session.
 * Metrics / factory bodies live in [TestReportMetrics] / [TestReportFactory] (MUD-034f).
 */
@Serializable
data class TestReport(
    val scenario: TestScenario,
    val totalSteps: Int,
    val passedSteps: Int,
    val failedSteps: Int,
    val finalStatus: TestStatus,
    val startTime: String,
    val endTime: String,
    val duration: Long, // milliseconds
    val steps: List<TestStep>,
    val results: List<StepResult>,
    val uniqueRoomsVisited: Int = 0,
    val roomNames: List<String> = emptyList(),
    // Playthrough metrics
    val damageTaken: Int = 0,
    val npcsKilled: Int = 0,
    val combatRounds: Int = 0,
    val skillChecksPassed: Int = 0,
    val socialChecksPassed: Int = 0,
    val playerDied: Boolean = false
) {
    companion object {
        fun fromTestState(state: TestState, startTime: Instant, endTime: Instant): TestReport =
            TestReportFactory.fromTestState(state, startTime, endTime)
    }
}
