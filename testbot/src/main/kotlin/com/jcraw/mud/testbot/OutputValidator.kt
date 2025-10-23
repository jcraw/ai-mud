package com.jcraw.mud.testbot

import com.jcraw.mud.core.WorldState
import com.jcraw.mud.testbot.validation.CodeValidationRules
import com.jcraw.mud.testbot.validation.ValidationParsers
import com.jcraw.mud.testbot.validation.ValidationPrompts
import com.jcraw.sophia.llm.LLMClient

/**
 * Validates game engine outputs using hybrid approach:
 * 1. Code-based validation (fast, deterministic) for mechanical correctness
 * 2. LLM validation (slower, subjective) for narrative quality
 * Uses gpt-4o-mini for cost savings as per guidelines.
 */
class OutputValidator(
    private val llmClient: LLMClient
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
        val codeValidation = CodeValidationRules.validate(
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
        val systemPrompt = ValidationPrompts.buildSystemPrompt(scenario)
        val userContext = ValidationPrompts.buildUserContext(
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
        return ValidationParsers.parseValidation(responseText)
    }
}
