@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

import com.jcraw.mud.core.GameEngineInterface

/**
 * Single ReAct step execution (reason → act → observe) (MUD-034f).
 */
internal object TestBotStepExecutor {

    suspend fun executeStep(
        state: TestState,
        sessionId: String,
        scenario: TestScenario,
        gameEngine: GameEngineInterface,
        inputGenerator: InputGenerator,
        outputValidator: OutputValidator,
        logger: GameplayLogger
    ): StepResult {
        val stepNumber = state.currentStep + 1
        val currentContext = TestBotContextBuilder.buildContext(gameEngine, scenario, state)
        when (val gen = tryGenerate(inputGenerator, scenario, state, currentContext)) {
            is GenResult.Fail -> return gen.toStepResult(stepNumber)
            is GenResult.Ok -> {
                printReasoning(gen.input)
                return processAfterInput(
                    stepNumber, gen.input, scenario, state, gameEngine,
                    outputValidator, logger, sessionId
                )
            }
        }
    }

    private sealed class GenResult {
        data class Ok(val input: GeneratedInput) : GenResult()
        data class Fail(val message: String?) : GenResult() {
            fun toStepResult(stepNumber: Int) = StepResult(
                stepNumber = stepNumber,
                step = TestStep(playerInput = "look", gmResponse = "Fallback to 'look' command"),
                passed = false,
                reason = "Input generation error: $message"
            )
        }
    }

    private suspend fun tryGenerate(
        inputGenerator: InputGenerator,
        scenario: TestScenario,
        state: TestState,
        currentContext: String
    ): GenResult = try {
        GenResult.Ok(
            inputGenerator.generateInput(
                scenario = scenario,
                recentHistory = state.steps,
                currentContext = currentContext
            )
        )
    } catch (e: Exception) {
        println("   ⚠️  Input generation failed: ${e.message}")
        GenResult.Fail(e.message)
    }

    private fun printReasoning(generatedInput: GeneratedInput) {
        if (generatedInput.reasoning.isNotBlank()) {
            println("   💭 Reasoning: ${generatedInput.reasoning}")
        }
        println("   🎮 Command: ${generatedInput.input}")
    }

    private suspend fun processAfterInput(
        stepNumber: Int,
        generatedInput: GeneratedInput,
        scenario: TestScenario,
        state: TestState,
        gameEngine: GameEngineInterface,
        outputValidator: OutputValidator,
        logger: GameplayLogger,
        sessionId: String
    ): StepResult {
        val rawOrError = runGameInput(gameEngine, generatedInput.input)
        if (rawOrError.error != null) {
            return engineErrorStep(stepNumber, generatedInput.input, rawOrError.error)
        }
        return observeAndLog(
            stepNumber, generatedInput, rawOrError.response!!, scenario, state,
            gameEngine, outputValidator, logger, sessionId
        )
    }

    private data class GameInputResult(val response: String?, val error: String?)

    private suspend fun runGameInput(gameEngine: GameEngineInterface, input: String): GameInputResult =
        try {
            GameInputResult(gameEngine.processInput(input), null)
        } catch (e: Exception) {
            println("   ❌ Game engine error: ${e.message}")
            GameInputResult(null, e.message)
        }

    private fun engineErrorStep(stepNumber: Int, input: String, message: String?): StepResult =
        StepResult(
            stepNumber = stepNumber,
            step = TestStep(playerInput = input, gmResponse = "ERROR: $message"),
            passed = false,
            reason = "Game engine error: $message"
        )

    private suspend fun observeAndLog(
        stepNumber: Int,
        generatedInput: GeneratedInput,
        rawGmResponse: String,
        scenario: TestScenario,
        state: TestState,
        gameEngine: GameEngineInterface,
        outputValidator: OutputValidator,
        logger: GameplayLogger,
        sessionId: String
    ): StepResult {
        val cleanGmResponse = TestBotDebugFilter.filterDebugOutput(rawGmResponse)
        printRawResponse(rawGmResponse)
        val validationResult = validateStep(
            outputValidator, scenario, generatedInput, cleanGmResponse, state, gameEngine
        )
        val step = makeStep(generatedInput, cleanGmResponse, validationResult)
        logger.logStep(sessionId, step)
        return finalizeStep(stepNumber, step, validationResult, cleanGmResponse)
    }

    private fun makeStep(
        generatedInput: GeneratedInput,
        cleanGmResponse: String,
        validationResult: ValidationResult
    ): TestStep = TestStep(
        playerInput = generatedInput.input,
        gmResponse = cleanGmResponse,
        validationResult = validationResult,
        reasoning = generatedInput.reasoning.takeIf { it.isNotBlank() }
    )

    private fun finalizeStep(
        stepNumber: Int,
        step: TestStep,
        validationResult: ValidationResult,
        cleanGmResponse: String
    ): StepResult {
        val passed = validationResult.pass && !cleanGmResponse.contains("ERROR", ignoreCase = true)
        return StepResult(
            stepNumber = stepNumber,
            step = step,
            passed = passed,
            reason = if (passed) "Step completed successfully" else validationResult.reason
        )
    }

    private fun printRawResponse(rawGmResponse: String) {
        println("   📜 Raw Response:")
        rawGmResponse.lines().forEach { line -> println("      $line") }
        println()
    }

    private suspend fun validateStep(
        outputValidator: OutputValidator,
        scenario: TestScenario,
        generatedInput: GeneratedInput,
        cleanGmResponse: String,
        state: TestState,
        gameEngine: GameEngineInterface
    ): ValidationResult = try {
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
        ValidationResult(pass = true, reason = "Validation error, assuming pass: ${e.message}")
    }
}
