@file:Suppress("NestedBlockDepth")

package com.jcraw.app

import com.jcraw.mud.core.QuestStatus
import com.jcraw.mud.reasoning.QuestAction

/**
 * Quest tracking and max-HP sync for [MudGame]. Pure extract.
 */
object MudGameQuestSupport {

    /**
     * Synchronize player max HP with current skill levels.
     * Updates max HP based on Vitality, Endurance, and Constitution skills.
     * Preserves current HP percentage when max HP changes.
     */
    fun syncPlayerMaxHp(game: MudGame) {
        val player = game.worldState.player
        val skillComponent = game.skillManager.getSkillComponent(player.id)
        val correctMaxHp = player.calculateMaxHp(skillComponent)

        if (player.maxHealth != correctMaxHp) {
            val updatedPlayer = player.updateMaxHp(correctMaxHp)
            game.worldState = game.worldState.updatePlayer(updatedPlayer)
            println("\n💪 Your maximum health has changed: ${player.maxHealth} → $correctMaxHp HP")
        }
    }

    /**
     * Track quest progress after player actions.
     */
    fun trackQuests(game: MudGame, action: QuestAction) {
        val (updatedPlayer, updatedWorld) = game.questTracker.updateQuestsAfterAction(
            game.worldState.player,
            game.worldState,
            action
        )

        // Check if any quest objectives were completed
        if (updatedPlayer != game.worldState.player) {
            emitQuestProgress(game, updatedPlayer)

            // Update both player and world state (world may have NPC disposition changes)
            game.worldState = updatedWorld.updatePlayer(updatedPlayer)
        }
    }

    private fun emitQuestProgress(game: MudGame, updatedPlayer: com.jcraw.mud.core.PlayerState) {
        updatedPlayer.activeQuests.forEach { quest ->
            val oldQuest = game.worldState.player.getQuest(quest.id)
            if (oldQuest != null) {
                // Check for newly completed objectives
                quest.objectives.zip(oldQuest.objectives).forEach { (newObj, oldObj) ->
                    if (newObj.isCompleted && !oldObj.isCompleted) {
                        println("\n✓ Quest objective completed: ${newObj.description}")
                    }
                }

                // Check if quest just completed
                if (quest.status == QuestStatus.COMPLETED &&
                    oldQuest.status == QuestStatus.ACTIVE
                ) {
                    println("\n🎉 Quest completed: ${quest.title}")
                    println("Use 'claim ${quest.id}' to collect your reward!")
                }
            }
        }
    }
}
