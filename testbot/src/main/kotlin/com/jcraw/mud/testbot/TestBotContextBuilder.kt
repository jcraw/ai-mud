@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

import com.jcraw.mud.core.GameEngineInterface
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.WorldState

/**
 * Builds current-status context for input generation (MUD-034f).
 */
internal object TestBotContextBuilder {

    fun buildContext(
        gameEngine: GameEngineInterface,
        scenario: TestScenario,
        state: TestState
    ): String {
        val worldState = gameEngine.getWorldState()
        val currentSpace = worldState.getCurrentSpace()
        val player = worldState.player
        val healthLine = healthLine(player)
        val questInfo = questInfoBlock(scenario, player, worldState)
        val lastGMResponse = state.steps.lastOrNull()?.gmResponse ?: "No previous game output"
        return """
            === CURRENT STATUS ===
            $healthLine
            Location: ${currentSpace?.name ?: "Unknown"}
            Inventory: ${player.inventory.joinToString { it.name }}
            Steps: ${state.currentStep}/${state.scenario.maxSteps}$questInfo

            === LAST GAME OUTPUT ===
            $lastGMResponse
        """.trimIndent()
    }

    private fun healthLine(player: PlayerState): String {
        val healthPercent = (player.health.toDouble() / player.maxHealth * 100).toInt()
        val healthWarning = when {
            healthPercent <= 25 -> "⚠️ CRITICAL - find healing immediately!"
            healthPercent <= 50 -> "⚠️ LOW - be careful in combat"
            else -> ""
        }
        return "Health: ${player.health}/${player.maxHealth} (${healthPercent}%) $healthWarning"
    }

    private fun questInfoBlock(
        scenario: TestScenario,
        player: PlayerState,
        worldState: WorldState
    ): String {
        if (scenario !is TestScenario.QuestTesting) return ""
        val activeQuests = player.activeQuests
        val availableQuests = worldState.availableQuests
        val completedQuestIds = player.completedQuests
        val activeComplete = activeQuests.filter { it.isComplete() }
        return """

            Quests:
              - Active: ${activeQuests.size} (${activeQuests.joinToString { it.id }})
              - Active & Complete (ready to claim): ${activeComplete.size} (${activeComplete.joinToString { it.id }})
              - Available: ${availableQuests.size} (${availableQuests.joinToString { it.id }})
              - Claimed: ${completedQuestIds.size}
        """.trimIndent()
    }
}
