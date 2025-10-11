package com.jcraw.mud.testbot

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestModelsTest {

    @Test
    fun `TestState starts with empty results`() {
        val scenario = TestScenario.Exploration()
        val state = TestState(scenario)

        assertEquals(0, state.steps.size)
        assertEquals(0, state.results.size)
        assertEquals(0, state.currentStep)
        assertFalse(state.isComplete)
        assertEquals(TestStatus.RUNNING, state.finalStatus)
    }

    @Test
    fun `TestState adds step and updates correctly`() {
        val scenario = TestScenario.Exploration(maxSteps = 5)
        val state = TestState(scenario)

        val step = TestStep(
            playerInput = "look",
            gmResponse = "You see a room."
        )
        val result = StepResult(
            stepNumber = 1,
            step = step,
            passed = true,
            reason = "Success"
        )

        val newState = state.withStep(step, result)

        assertEquals(1, newState.steps.size)
        assertEquals(1, newState.results.size)
        assertEquals(1, newState.currentStep)
        assertFalse(newState.isComplete)
        assertEquals(TestStatus.RUNNING, newState.finalStatus)
    }

    @Test
    fun `TestState completes on failure`() {
        val scenario = TestScenario.Exploration(maxSteps = 10)
        val state = TestState(scenario)

        val step = TestStep(
            playerInput = "invalid",
            gmResponse = "Error"
        )
        val result = StepResult(
            stepNumber = 1,
            step = step,
            passed = false,
            reason = "Validation failed"
        )

        val newState = state.withStep(step, result)

        assertTrue(newState.isComplete)
        assertEquals(TestStatus.FAILED, newState.finalStatus)
    }

    @Test
    fun `TestState completes after max steps`() {
        val scenario = TestScenario.Exploration(maxSteps = 2)
        var state = TestState(scenario)

        // Step 1
        state = state.withStep(
            TestStep(playerInput = "look", gmResponse = "Room"),
            StepResult(1, TestStep(playerInput = "look", gmResponse = "Room"), true, "OK")
        )

        assertFalse(state.isComplete)

        // Step 2 (reaches max)
        state = state.withStep(
            TestStep(playerInput = "north", gmResponse = "Moved"),
            StepResult(2, TestStep(playerInput = "north", gmResponse = "Moved"), true, "OK")
        )

        assertTrue(state.isComplete)
        assertEquals(TestStatus.PASSED, state.finalStatus)
    }

    @Test
    fun `ValidationResult captures pass and reason`() {
        val result = ValidationResult(
            pass = true,
            reason = "Output is coherent",
            details = mapOf("coherence" to "pass")
        )

        assertTrue(result.pass)
        assertEquals("Output is coherent", result.reason)
        assertEquals("pass", result.details["coherence"])
    }

    @Test
    fun `TestReport calculates pass rate correctly`() {
        val scenario = TestScenario.Combat()
        val steps = listOf(
            TestStep(playerInput = "attack", gmResponse = "Hit!"),
            TestStep(playerInput = "attack", gmResponse = "Miss!"),
            TestStep(playerInput = "attack", gmResponse = "Hit!")
        )
        val results = listOf(
            StepResult(1, steps[0], true, "OK"),
            StepResult(2, steps[1], false, "Failed"),
            StepResult(3, steps[2], true, "OK")
        )

        val state = TestState(
            scenario = scenario,
            steps = steps,
            results = results,
            currentStep = 3,
            isComplete = true,
            finalStatus = TestStatus.PASSED
        )

        val report = TestReport.fromTestState(
            state = state,
            startTime = java.time.Instant.now(),
            endTime = java.time.Instant.now().plusMillis(5000)
        )

        assertEquals(3, report.totalSteps)
        assertEquals(2, report.passedSteps)
        assertEquals(1, report.failedSteps)
        assertEquals(TestStatus.PASSED, report.finalStatus)
    }
}
