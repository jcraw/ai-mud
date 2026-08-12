@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

import com.jcraw.mud.core.GameEngineInterface
import com.jcraw.mud.core.WorldState

/**
 * Scenario-specific early completion checks (MUD-034f).
 */
internal object TestBotScenarioComplete {

    fun checkScenarioComplete(gameEngine: GameEngineInterface, state: TestState): Boolean {
        val worldState = gameEngine.getWorldState()
        return when (state.scenario) {
            is TestScenario.BruteForcePlaythrough -> bruteForceComplete(state)
            is TestScenario.BadPlaythrough -> badPlaythroughComplete(state)
            is TestScenario.SmartPlaythrough -> smartPlaythroughComplete(state, worldState)
            is TestScenario.SkillProgression -> skillProgressionComplete(state)
            else -> false
        }
    }

    private fun bruteForceComplete(state: TestState): Boolean =
        state.steps.any {
            it.gmResponse.contains("has been defeated", ignoreCase = true) &&
                it.playerInput.contains("skeleton", ignoreCase = true)
        }

    private fun badPlaythroughComplete(state: TestState): Boolean =
        state.steps.any {
            it.gmResponse.contains("You have died", ignoreCase = true) ||
                it.gmResponse.contains("You have been defeated", ignoreCase = true) ||
                it.gmResponse.contains("Game over", ignoreCase = true)
        }

    private fun smartPlaythroughComplete(state: TestState, worldState: WorldState): Boolean {
        val reachedSecret = reachedSecretChamber(state, worldState)
        val bossDefeated = state.steps.any {
            it.gmResponse.contains("has been defeated", ignoreCase = true) &&
                it.playerInput.contains("skeleton", ignoreCase = true)
        }
        return reachedSecret || bossDefeated
    }

    private fun reachedSecretChamber(state: TestState, worldState: WorldState): Boolean {
        val currentSpace = worldState.getCurrentSpace()
        if (currentSpace?.name?.contains("Secret", ignoreCase = true) == true) return true
        if (currentSpace?.name?.contains("Hidden", ignoreCase = true) == true) return true
        return state.steps.any {
            it.gmResponse.startsWith("Secret Chamber", ignoreCase = true) ||
                it.gmResponse.startsWith("Hidden Chamber", ignoreCase = true)
        }
    }

    private fun skillProgressionComplete(state: TestState): Boolean {
        val targetLevel = (state.scenario as TestScenario.SkillProgression).targetLevel
        return state.steps.any { step ->
            val match = Regex("Dodge leveled up!.*?(\\d+)\\s*→\\s*(\\d+)", RegexOption.IGNORE_CASE)
                .find(step.gmResponse)
            val newLevel = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
            newLevel >= targetLevel
        }
    }
}
