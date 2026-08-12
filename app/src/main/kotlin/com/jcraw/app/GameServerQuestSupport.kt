@file:Suppress("FunctionOnlyReturningConstant", "UnusedParameter")

package com.jcraw.app

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.QuestStatus
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.reasoning.QuestAction

/**
 * Quest tracking and format helpers for [GameServer]. Pure extract.
 */
object GameServerQuestSupport {

    /**
     * Track quest progress and return notification messages
     * Also returns updated world state (quest giver NPCs may have disposition changes)
     */
    fun trackQuests(
        server: GameServer,
        playerState: PlayerState,
        action: QuestAction
    ): QuestTrackingResult {
        val (updatedPlayer, updatedWorld) = server.questTracker.updateQuestsAfterAction(
            playerState,
            server.worldState,
            action
        )

        // Check if any quest objectives were completed
        val notifications = buildQuestNotifications(playerState, updatedPlayer)

        return QuestTrackingResult(updatedPlayer, updatedWorld, notifications)
    }

    private fun buildQuestNotifications(
        playerState: PlayerState,
        updatedPlayer: PlayerState
    ): String {
        return buildString {
            if (updatedPlayer == playerState) return@buildString
            updatedPlayer.activeQuests.forEach { quest ->
                val oldQuest = playerState.getQuest(quest.id) ?: return@forEach
                appendObjectiveCompletions(quest, oldQuest)
                appendQuestCompleted(quest, oldQuest)
            }
        }
    }

    private fun StringBuilder.appendObjectiveCompletions(
        quest: com.jcraw.mud.core.Quest,
        oldQuest: com.jcraw.mud.core.Quest
    ) {
        quest.objectives.zip(oldQuest.objectives).forEach { (newObj, oldObj) ->
            if (newObj.isCompleted && !oldObj.isCompleted) {
                appendLine("\n✓ Quest objective completed: ${newObj.description}")
            }
        }
    }

    private fun StringBuilder.appendQuestCompleted(
        quest: com.jcraw.mud.core.Quest,
        oldQuest: com.jcraw.mud.core.Quest
    ) {
        if (quest.status == QuestStatus.COMPLETED && oldQuest.status == QuestStatus.ACTIVE) {
            appendLine("\n🎉 Quest completed: ${quest.title}")
            appendLine("Use 'claim ${quest.id}' to collect your reward!")
        }
    }

    fun formatQuests(playerState: PlayerState): String {
        return "Quest system coming soon to multi-user mode!"
    }
}
