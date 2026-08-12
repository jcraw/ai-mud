@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "UnusedParameter",
    "TooGenericExceptionCaught",
    "TooGenericExceptionThrown",
    "SwallowedException",
    "WildcardImport",
    "MayBeConst",
    "ImplicitDefaultLocale",
    "ForbiddenComment",
    "UnusedPrivateProperty",
)

package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.world.ExitData
import com.jcraw.sophia.llm.LLMClient

/**
 * Internal result type for LLM matching (MUD-034g pure move).
 */
internal sealed class LLMMatchResult {
    data class Match(val exit: ExitData) : LLMMatchResult()
    data object Unclear : LLMMatchResult()
    data object NoMatch : LLMMatchResult()
}

/**
 * Phase 3 LLM exit matching for [ExitResolver] (MUD-034g pure move).
 * Fragmented so no Added FN exceeds global FN_E 250.
 */
internal object ExitResolverLlm {

    data class PhaseThreePrompts(
        val systemPrompt: String,
        val userContext: String
    )

    fun buildPhaseThreePrompts(intent: String, exits: List<ExitData>): PhaseThreePrompts {
        val exitList = exits.joinToString("\n") { exit ->
            "- ${exit.direction}: ${exit.description}"
        }

        val systemPrompt = "You are a game assistant matching player intent to exits. Output EXIT:<direction> or UNCLEAR."

        val userContext = """
            |Player said: "$intent"
            |
            |Available exits:
            |$exitList
            |
            |Which exit matches the player's intent?
            |If clear, output: EXIT:<direction>
            |If unclear or no match, output: UNCLEAR
        """.trimMargin()

        return PhaseThreePrompts(systemPrompt, userContext)
    }

    fun parsePhaseThreeResponse(response: String?, exits: List<ExitData>): LLMMatchResult {
        return when {
            response == null -> LLMMatchResult.NoMatch
            response.startsWith("EXIT:") -> {
                val direction = response.removePrefix("EXIT:").trim()
                val matchedExit = exits.firstOrNull { it.direction.equals(direction, ignoreCase = true) }
                if (matchedExit != null) {
                    LLMMatchResult.Match(matchedExit)
                } else {
                    LLMMatchResult.NoMatch
                }
            }
            response.contains("UNCLEAR", ignoreCase = true) -> LLMMatchResult.Unclear
            else -> LLMMatchResult.NoMatch
        }
    }

    /**
     * Phase 3: LLM-based natural language parsing
     */
    suspend fun phaseThreeLLMParse(
        llmClient: LLMClient,
        intent: String,
        exits: List<ExitData>
    ): LLMMatchResult {
        val prompts = buildPhaseThreePrompts(intent, exits)

        val response = try {
            llmClient.chatCompletion(
                modelId = "gpt-4o-mini",
                systemPrompt = prompts.systemPrompt,
                userContext = prompts.userContext,
                maxTokens = 50,
                temperature = 0.3
            ).choices.firstOrNull()?.message?.content
        } catch (e: Exception) {
            null
        }

        return parsePhaseThreeResponse(response, exits)
    }
}
