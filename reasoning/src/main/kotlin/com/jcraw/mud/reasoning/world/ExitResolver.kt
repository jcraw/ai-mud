package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.world.ExitData
import com.jcraw.mud.core.world.Condition
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.sophia.llm.LLMClient
import kotlin.math.min

/**
 * Result of exit resolution
 */
sealed class ResolveResult {
    /**
     * Exit successfully resolved
     * @param exit The resolved exit data
     * @param targetId The ID of the target space
     */
    data class Success(val exit: ExitData, val targetId: String) : ResolveResult()

    /**
     * Exit could not be resolved or player cannot use it
     * @param reason Human-readable explanation of why resolution failed
     */
    data class Failure(val reason: String) : ResolveResult()

    /**
     * Multiple possible exits matched, player needs to clarify
     * @param suggestions List of possible exits and their descriptions
     */
    data class Ambiguous(val suggestions: Map<String, String>) : ResolveResult()
}

/**
 * Resolves player exit intents using a three-phase approach:
 * 1. Exact match for cardinal directions (fast)
 * 2. Fuzzy match for minor typos
 * 3. LLM parsing for natural language
 *
 * Also handles exit visibility (hidden exits require Perception checks)
 * and exit conditions (skill/item requirements).
 */
class ExitResolver(
    private val llmClient: LLMClient
) {
    companion object {
        // Cardinal directions for exact matching
        private val CARDINAL_DIRECTIONS = setOf(
            "n", "north",
            "s", "south",
            "e", "east",
            "w", "west",
            "ne", "northeast",
            "nw", "northwest",
            "se", "southeast",
            "sw", "southwest",
            "up", "u",
            "down", "d"
        )
    }

    /**
     * Resolves a player's exit intent through three phases of matching.
     *
     * @param exitIntent The direction or description the player entered
     * @param currentSpace The space properties containing available exits
     * @param playerState Current player state (for visibility and condition checks)
     * @return ResolveResult indicating success, failure, or ambiguity
     */
    suspend fun resolve(
        exitIntent: String,
        currentSpace: SpacePropertiesComponent,
        playerState: PlayerState
    ): ResolveResult {
        val visibleExits = getVisibleExits(currentSpace, playerState)

        if (visibleExits.isEmpty()) {
            return ResolveResult.Failure("You don't see any obvious exits from here.")
        }

        // Phase 1: Exact match for cardinal directions
        val exactMatch = phaseOneExactMatch(exitIntent, visibleExits)
        if (exactMatch != null) {
            return checkConditions(exactMatch, playerState)
        }

        // Phase 2: Fuzzy match for typos
        val fuzzyMatch = phaseTwoFuzzyMatch(exitIntent, visibleExits)
        if (fuzzyMatch != null) {
            return checkConditions(fuzzyMatch, playerState)
        }

        // Phase 3: LLM parsing for natural language
        val llmMatch = phaseThreeLLMParse(exitIntent, visibleExits)
        return when (llmMatch) {
            is LLMMatchResult.Match -> checkConditions(llmMatch.exit, playerState)
            is LLMMatchResult.Unclear -> ResolveResult.Ambiguous(
                visibleExits.associate { exit ->
                    exit.direction to exit.description
                }
            )
            is LLMMatchResult.NoMatch -> ResolveResult.Failure(
                "I'm not sure which way you want to go. Available exits: ${visibleExits.joinToString { it.direction }}"
            )
        }
    }

    /**
     * Phase 1: Exact case-insensitive match for cardinal directions
     */
    private fun phaseOneExactMatch(intent: String, exits: List<ExitData>): ExitData? {
        val normalizedIntent = intent.trim().lowercase()
        return exits.firstOrNull { exit ->
            exit.direction.lowercase() == normalizedIntent &&
                    normalizedIntent in CARDINAL_DIRECTIONS
        }
    }

    /**
     * Phase 2: Fuzzy match using Levenshtein distance for typos
     */
    private fun phaseTwoFuzzyMatch(intent: String, exits: List<ExitData>): ExitData? {
        val normalizedIntent = intent.trim().lowercase()
        val matches = exits.filter { exit ->
            levenshteinDistance(normalizedIntent, exit.direction.lowercase()) <= 2
        }

        // Only return match if unambiguous
        return if (matches.size == 1) matches.first() else null
    }

    /**
     * Phase 3: LLM-based natural language parsing
     */
    private suspend fun phaseThreeLLMParse(intent: String, exits: List<ExitData>): LLMMatchResult {
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

        val response = try {
            llmClient.chatCompletion(
                modelId = "gpt-4o-mini",
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = 50,
                temperature = 0.3
            ).choices.firstOrNull()?.message?.content
        } catch (e: Exception) {
            null
        }

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
     * Checks if the player meets all conditions to use an exit
     */
    private fun checkConditions(exit: ExitData, playerState: PlayerState): ResolveResult {
        val unmetConditions = exit.conditions.filterNot { it.meetsCondition(playerState) }

        return if (unmetConditions.isEmpty()) {
            ResolveResult.Success(exit, exit.targetId)
        } else {
            val conditionDescriptions = unmetConditions.joinToString(", ") { condition ->
                when (condition) {
                    is Condition.SkillCheck -> "${condition.skill} ${condition.difficulty}+"
                    is Condition.ItemRequired -> "item: ${condition.itemTag}"
                }
            }
            ResolveResult.Failure(
                "You cannot go ${exit.direction}. Required: $conditionDescriptions"
            )
        }
    }

    /**
     * Filters exits by visibility based on player's Perception skill.
     * Hidden exits require a passive Perception check.
     *
     * @param space The space containing exits
     * @param playerState Current player state
     * @return List of exits the player can see
     */
    fun getVisibleExits(space: SpacePropertiesComponent, playerState: PlayerState): List<ExitData> {
        val perceptionModifier = playerState.stats.wisdom / 2 - 5
        val perceptionSkill = playerState.getSkillLevel("Perception")
        val passivePerception = 10 + perceptionModifier + perceptionSkill

        return space.exits.filter { exit ->
            !exit.isHidden || passivePerception >= (exit.hiddenDifficulty ?: 10)
        }
    }

    /**
     * Generates a description of an exit, including condition hints if not met.
     *
     * @param exit The exit to describe
     * @param player Current player state
     * @return Human-readable description with condition hints
     */
    fun describeExit(exit: ExitData, player: PlayerState): String {
        val baseDescription = "${exit.direction}: ${exit.description}"

        val unmetConditions = exit.conditions.filterNot { it.meetsCondition(player) }
        if (unmetConditions.isEmpty()) {
            return baseDescription
        }

        val conditionHints = unmetConditions.joinToString(", ") { condition ->
            when (condition) {
                is Condition.SkillCheck ->
                    "requires ${condition.skill} ${condition.difficulty}+"
                is Condition.ItemRequired ->
                    "requires ${condition.itemTag}"
            }
        }

        return "$baseDescription ($conditionHints)"
    }

    /**
     * Calculates Levenshtein distance between two strings
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        if (len1 == 0) return len2
        if (len2 == 0) return len1

        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[len1][len2]
    }
}

/**
 * Internal result type for LLM matching
 */
private sealed class LLMMatchResult {
    data class Match(val exit: ExitData) : LLMMatchResult()
    data object Unclear : LLMMatchResult()
    data object NoMatch : LLMMatchResult()
}
