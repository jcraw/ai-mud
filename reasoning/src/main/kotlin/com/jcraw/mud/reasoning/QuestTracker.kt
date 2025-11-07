package com.jcraw.mud.reasoning

import com.jcraw.mud.core.*

/**
 * Tracks quest progress and handles objective completion
 */
class QuestTracker(
    private val dispositionManager: DispositionManager? = null
) {
    /**
     * Update quest objectives based on player actions
     * Returns updated player state and world state (if NPCs changed)
     */
    fun updateQuestsAfterAction(
        playerState: PlayerState,
        worldState: WorldState,
        action: QuestAction
    ): Pair<PlayerState, WorldState> {
        var updatedPlayer = playerState
        var updatedWorld = worldState

        // Check each active quest for progress
        playerState.activeQuests.forEach { quest ->
            val updatedQuest = checkQuestProgress(quest, worldState, playerState, action)
            if (updatedQuest != quest) {
                updatedPlayer = updatedPlayer.updateQuest(updatedQuest)

                // Auto-complete quest if all objectives are done
                if (updatedQuest.isComplete() && updatedQuest.status == QuestStatus.ACTIVE) {
                    val completedQuest = updatedQuest.complete()
                    updatedPlayer = updatedPlayer.updateQuest(completedQuest)

                    // Apply disposition bonus to quest giver
                    updatedWorld = applyQuestCompletionDisposition(completedQuest, updatedWorld)
                }
            }
        }

        return updatedPlayer to updatedWorld
    }

    /**
     * Apply disposition bonus to quest giver when quest is completed
     */
    private fun applyQuestCompletionDisposition(quest: Quest, worldState: WorldState): WorldState {
        // Only apply if we have a disposition manager and a quest giver
        if (dispositionManager == null || quest.giver == null) {
            return worldState
        }

        // Find the quest giver NPC (V3: entities are stored globally)
        val giverId = quest.giver ?: return worldState // Already checked null above, but compiler needs this
        val questGiver = worldState.getEntity(giverId) as? Entity.NPC ?: return worldState

        // Apply quest completed event (+15 disposition)
        val event = SocialEvent.QuestCompleted(quest.title)
        val updatedNPC = dispositionManager.applyEvent(questGiver, event)

        // Update entity in global storage
        return worldState.updateEntity(updatedNPC)
    }

    /**
     * Check if any objectives were completed by this action
     */
    private fun checkQuestProgress(
        quest: Quest,
        worldState: WorldState,
        playerState: PlayerState,
        action: QuestAction
    ): Quest {
        var updatedQuest = quest

        quest.objectives.forEach { objective ->
            if (!objective.isCompleted) {
                val completedObjective = when (action) {
                    is QuestAction.KilledNPC -> checkKillObjective(objective, action.npcId)
                    is QuestAction.CollectedItem -> checkCollectObjective(objective, action.itemId, playerState)
                    is QuestAction.VisitedRoom -> checkExploreObjective(objective, action.roomId)
                    is QuestAction.TalkedToNPC -> checkTalkObjective(objective, action.npcId)
                    is QuestAction.UsedSkill -> checkSkillObjective(objective, action.featureId)
                    is QuestAction.DeliveredItem -> checkDeliverObjective(objective, action.itemId, action.npcId)
                }

                if (completedObjective != null) {
                    updatedQuest = updatedQuest.updateObjective(objective.id) { completedObjective }
                }
            }
        }

        return updatedQuest
    }

    private fun checkKillObjective(objective: QuestObjective, npcId: String): QuestObjective? {
        return if (objective is QuestObjective.KillEnemy && objective.targetNpcId == npcId) {
            objective.copy(isCompleted = true)
        } else null
    }

    private fun checkCollectObjective(
        objective: QuestObjective,
        itemId: String,
        playerState: PlayerState
    ): QuestObjective? {
        return if (objective is QuestObjective.CollectItem && objective.targetItemId == itemId) {
            // Check if player has the item in inventory
            val hasItem = playerState.inventory.any { it.id == itemId }
            if (hasItem) {
                objective.copy(currentQuantity = objective.quantity, isCompleted = true)
            } else null
        } else null
    }

    private fun checkExploreObjective(objective: QuestObjective, roomId: RoomId): QuestObjective? {
        return if (objective is QuestObjective.ExploreRoom && objective.targetRoomId == roomId) {
            objective.copy(isCompleted = true)
        } else null
    }

    private fun checkTalkObjective(objective: QuestObjective, npcId: String): QuestObjective? {
        return if (objective is QuestObjective.TalkToNpc && objective.targetNpcId == npcId) {
            objective.copy(isCompleted = true)
        } else null
    }

    private fun checkSkillObjective(objective: QuestObjective, featureId: String): QuestObjective? {
        return if (objective is QuestObjective.UseSkill && objective.targetFeatureId == featureId) {
            objective.copy(isCompleted = true)
        } else null
    }

    private fun checkDeliverObjective(
        objective: QuestObjective,
        itemId: String,
        npcId: String
    ): QuestObjective? {
        return if (objective is QuestObjective.DeliverItem &&
            objective.itemId == itemId &&
            objective.targetNpcId == npcId
        ) {
            objective.copy(isCompleted = true)
        } else null
    }
}

/**
 * Actions that can trigger quest objective completion
 */
sealed class QuestAction {
    data class KilledNPC(val npcId: String) : QuestAction()
    data class CollectedItem(val itemId: String) : QuestAction()
    data class VisitedRoom(val roomId: RoomId) : QuestAction()
    data class TalkedToNPC(val npcId: String) : QuestAction()
    data class UsedSkill(val featureId: String) : QuestAction()
    data class DeliveredItem(val itemId: String, val npcId: String) : QuestAction()
}
