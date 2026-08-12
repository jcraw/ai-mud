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

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SkillComponent
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
 *
 * Thin facade — match/LLM/conditions extracted (MUD-034g).
 */
class ExitResolver(
    private val llmClient: LLMClient
) {
    /**
     * Resolves a player's exit intent through three phases of matching.
     */
    suspend fun resolve(
        exitIntent: String,
        currentSpace: SpacePropertiesComponent,
        playerState: PlayerState,
        playerSkills: SkillComponent
    ): ResolveResult {
        val visibleExits = getVisibleExits(currentSpace, playerState, playerSkills)
        if (visibleExits.isEmpty()) {
            return ResolveResult.Failure("You don't see any obvious exits from here.")
        }
        return resolveAgainstVisible(exitIntent, visibleExits, playerState, playerSkills)
    }

    private suspend fun resolveAgainstVisible(
        exitIntent: String,
        visibleExits: List<ExitData>,
        playerState: PlayerState,
        playerSkills: SkillComponent
    ): ResolveResult {
        // Phase 1: Exact match for cardinal directions
        val exactMatch = ExitResolverMatch.phaseOneExactMatch(exitIntent, visibleExits)
        if (exactMatch != null) {
            return ExitResolverConditions.checkConditions(exactMatch, playerState, playerSkills)
        }

        // Phase 2: Fuzzy match for typos
        val fuzzyMatch = ExitResolverMatch.phaseTwoFuzzyMatch(
            exitIntent, visibleExits, ::levenshteinDistance
        )
        if (fuzzyMatch != null) {
            return ExitResolverConditions.checkConditions(fuzzyMatch, playerState, playerSkills)
        }

        // Phase 3: LLM parsing for natural language
        return resolvePhaseThree(exitIntent, visibleExits, playerState, playerSkills)
    }

    private suspend fun resolvePhaseThree(
        exitIntent: String,
        visibleExits: List<ExitData>,
        playerState: PlayerState,
        playerSkills: SkillComponent
    ): ResolveResult {
        val llmMatch = ExitResolverLlm.phaseThreeLLMParse(llmClient, exitIntent, visibleExits)
        return when (llmMatch) {
            is LLMMatchResult.Match -> ExitResolverConditions.checkConditions(
                llmMatch.exit, playerState, playerSkills
            )
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
     * Filters exits by visibility based on player's Perception skill.
     * Hidden exits require a passive Perception check.
     */
    fun getVisibleExits(
        space: SpacePropertiesComponent,
        playerState: PlayerState,
        playerSkills: SkillComponent
    ): List<ExitData> {
        val perceptionModifier = playerState.stats.wisdom / 2 - 5
        // Use V2 skill system
        val perceptionSkill = playerSkills.getEffectiveLevel("Perception")
        val passivePerception = 10 + perceptionModifier + perceptionSkill

        return space.exits.filter { exit ->
            !exit.isHidden || passivePerception >= (exit.hiddenDifficulty ?: 10)
        }
    }

    /**
     * Generates a description of an exit, including condition hints if not met.
     */
    fun describeExit(exit: ExitData, player: PlayerState, playerSkills: SkillComponent): String {
        val baseDescription = "${exit.direction}: ${exit.description}"

        val unmetConditions = exit.conditions.filterNot { it.meetsCondition(player, playerSkills) }
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
     * Calculates Levenshtein distance between two strings (residual on host).
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
