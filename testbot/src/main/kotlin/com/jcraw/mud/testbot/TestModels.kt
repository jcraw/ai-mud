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
    val validationResult: ValidationResult? = null
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
        fun fromTestState(state: TestState, startTime: Instant, endTime: Instant): TestReport {
            val passedSteps = state.results.count { it.passed }
            val failedSteps = state.results.count { !it.passed }

            // Extract unique rooms for exploration scenario
            val roomNames = extractRoomsFromSteps(state.steps)

            // Calculate playthrough metrics
            val damageTaken = calculateDamageTaken(state.steps)
            val npcsKilled = countNPCsKilled(state.steps)
            val combatRounds = countCombatRounds(state.steps)
            val skillChecksPassed = countSkillChecksPassed(state.steps)
            val socialChecksPassed = countSocialChecksPassed(state.steps)
            val playerDied = checkPlayerDied(state.steps)

            return TestReport(
                scenario = state.scenario,
                totalSteps = state.steps.size,
                passedSteps = passedSteps,
                failedSteps = failedSteps,
                finalStatus = state.finalStatus,
                startTime = startTime.toString(),
                endTime = endTime.toString(),
                duration = endTime.toEpochMilli() - startTime.toEpochMilli(),
                steps = state.steps,
                results = state.results,
                uniqueRoomsVisited = roomNames.size,
                roomNames = roomNames.toList(),
                damageTaken = damageTaken,
                npcsKilled = npcsKilled,
                combatRounds = combatRounds,
                skillChecksPassed = skillChecksPassed,
                socialChecksPassed = socialChecksPassed,
                playerDied = playerDied
            )
        }

        /**
         * Extract unique room names from test steps.
         */
        private fun extractRoomsFromSteps(steps: List<TestStep>): Set<String> {
            val roomNames = mutableSetOf<String>()
            val roomPattern = Regex("^([A-Z][a-zA-Z\\s]+)\\n", RegexOption.MULTILINE)

            for (step in steps) {
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

        /**
         * Calculate total damage taken by player during playthrough.
         */
        private fun calculateDamageTaken(steps: List<TestStep>): Int {
            var totalDamage = 0
            val damagePattern = Regex("(?:retaliates|strikes|hits|deals).+?(\\d+)\\s+damage", RegexOption.IGNORE_CASE)

            for (step in steps) {
                // Look for NPC damage to player (retaliation)
                if (step.gmResponse.contains("retaliate", ignoreCase = true)) {
                    val match = damagePattern.find(step.gmResponse)
                    if (match != null) {
                        totalDamage += match.groupValues[1].toIntOrNull() ?: 0
                    }
                }
            }

            return totalDamage
        }

        /**
         * Count NPCs killed during playthrough.
         */
        private fun countNPCsKilled(steps: List<TestStep>): Int {
            var killCount = 0

            for (step in steps) {
                // Look for kill/defeat messages
                if (step.gmResponse.contains("has been defeated", ignoreCase = true) ||
                    step.gmResponse.contains("slain", ignoreCase = true) ||
                    step.gmResponse.contains("falls dead", ignoreCase = true)) {
                    killCount++
                }
            }

            return killCount
        }

        /**
         * Count combat rounds (attack actions) during playthrough.
         */
        private fun countCombatRounds(steps: List<TestStep>): Int {
            var roundCount = 0

            for (step in steps) {
                // Count attack commands and combat-related responses
                if (step.playerInput.contains("attack", ignoreCase = true) ||
                    step.playerInput.contains("fight", ignoreCase = true) ||
                    step.playerInput.contains("hit", ignoreCase = true) ||
                    step.playerInput.contains("kill", ignoreCase = true)) {
                    // Verify it was actually a combat action (look for damage or combat keywords)
                    if (step.gmResponse.contains("damage", ignoreCase = true) ||
                        step.gmResponse.contains("hit", ignoreCase = true) ||
                        step.gmResponse.contains("strike", ignoreCase = true) ||
                        step.gmResponse.contains("attack", ignoreCase = true) ||
                        step.gmResponse.contains("combat", ignoreCase = true) ||
                        step.gmResponse.contains("retaliate", ignoreCase = true)) {
                        roundCount++
                    }
                }
            }

            return roundCount
        }

        /**
         * Count successful skill checks during playthrough.
         */
        private fun countSkillChecksPassed(steps: List<TestStep>): Int {
            var checkCount = 0

            for (step in steps) {
                // Check if this was a "check" command that succeeded
                if (step.playerInput.startsWith("check", ignoreCase = true) &&
                    (step.gmResponse.contains("Success!", ignoreCase = true) ||
                     step.gmResponse.contains("succeed", ignoreCase = true))) {
                    checkCount++
                }
            }

            return checkCount
        }

        /**
         * Count successful social checks (persuade/intimidate) during playthrough.
         */
        private fun countSocialChecksPassed(steps: List<TestStep>): Int {
            var socialCount = 0

            for (step in steps) {
                // Check if this was a social command that succeeded
                if ((step.playerInput.contains("persuade", ignoreCase = true) ||
                     step.playerInput.contains("intimidate", ignoreCase = true)) &&
                    (step.gmResponse.contains("Success!", ignoreCase = true) ||
                     step.gmResponse.contains("succeed", ignoreCase = true))) {
                    socialCount++
                }
            }

            return socialCount
        }

        /**
         * Check if player died during playthrough.
         */
        private fun checkPlayerDied(steps: List<TestStep>): Boolean {
            for (step in steps) {
                if (step.gmResponse.contains("You have died", ignoreCase = true) ||
                    step.gmResponse.contains("You fall", ignoreCase = true) ||
                    step.gmResponse.contains("You are dead", ignoreCase = true) ||
                    step.gmResponse.contains("death", ignoreCase = true) &&
                    step.gmResponse.contains("you", ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }
}
