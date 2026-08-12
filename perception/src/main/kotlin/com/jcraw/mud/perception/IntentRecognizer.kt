package com.jcraw.mud.perception

import com.jcraw.mud.core.Direction
import com.jcraw.sophia.llm.LLMClient

/**
 * LLM-powered intent recognizer that converts natural language input into structured Intent objects.
 *
 * This component uses an LLM to handle flexible, natural language parsing rather than rigid regex patterns.
 * Players can type commands in many different ways and the LLM will understand their intent.
 *
 * Implementation detail: direction / say / trade / LLM / fallback pure-moved to sibling objects
 * (MUD-034c) — public API and parseIntent pipeline order unchanged.
 */
class IntentRecognizer(
    private val llmClient: LLMClient?
) {
    /**
     * Parse natural language input into a structured Intent.
     *
     * If LLM is not available, falls back to simple pattern matching for basic commands.
     *
     * @param input The player's text input
     * @param roomContext Optional room context description
     * @param exitsWithNames Map of available exits to their destination room names (for navigation)
     */
    suspend fun parseIntent(
        input: String,
        roomContext: String? = null,
        exitsWithNames: Map<Direction, String>? = null
    ): Intent {
        // Fast path: Intent.Check if input is ONLY a cardinal direction BEFORE splitting (bypass LLM)
        // This ensures compound commands like "north and take sword" don't match the fast path
        val pureDirection = IntentDirectionParse.parseCardinalDirection(input)
        if (pureDirection != null) {
            return Intent.Move(pureDirection)
        }

        // Split compound commands (e.g., "take sword and equip it" → "take sword")
        val firstCommand = IntentDirectionParse.splitCompoundCommand(input)

        if (llmClient == null) {
            return IntentFallbackParse.parseFallback(firstCommand)
        }

        return try {
            IntentLlmParse.parseLLM(llmClient, firstCommand, roomContext, exitsWithNames)
        } catch (e: Exception) {
            // Fall back to simple parsing if LLM fails
            IntentFallbackParse.parseFallback(firstCommand)
        }
    }
}
