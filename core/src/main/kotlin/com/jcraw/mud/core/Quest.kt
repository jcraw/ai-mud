package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Quest system for dynamic objectives and rewards
 */

typealias QuestId = String

/**
 * Quest status tracking
 */
@Serializable
enum class QuestStatus {
    ACTIVE,      // Quest is in progress
    COMPLETED,   // All objectives met, reward not claimed
    CLAIMED,     // Reward has been claimed
    FAILED       // Quest failed (optional death/timeout)
}

/**
 * Types of quest objectives
 */
@Serializable
sealed class QuestObjective {
    abstract val id: String
    abstract val description: String
    abstract val isCompleted: Boolean

    @Serializable
    data class KillEnemy(
        override val id: String,
        override val description: String,
        val targetNpcId: String,
        val targetName: String,
        override val isCompleted: Boolean = false
    ) : QuestObjective()

    @Serializable
    data class CollectItem(
        override val id: String,
        override val description: String,
        val targetItemId: String,
        val targetName: String,
        val quantity: Int = 1,
        val currentQuantity: Int = 0,
        override val isCompleted: Boolean = false
    ) : QuestObjective()

    @Serializable
    data class ExploreRoom(
        override val id: String,
        override val description: String,
        val targetRoomId: RoomId,
        val targetRoomName: String,
        override val isCompleted: Boolean = false
    ) : QuestObjective()

    @Serializable
    data class TalkToNpc(
        override val id: String,
        override val description: String,
        val targetNpcId: String,
        val targetName: String,
        override val isCompleted: Boolean = false
    ) : QuestObjective()

    @Serializable
    data class UseSkill(
        override val id: String,
        override val description: String,
        val skillType: StatType,
        val targetFeatureId: String,
        val targetName: String,
        override val isCompleted: Boolean = false
    ) : QuestObjective()

    @Serializable
    data class DeliverItem(
        override val id: String,
        override val description: String,
        val itemId: String,
        val itemName: String,
        val targetNpcId: String,
        val targetNpcName: String,
        override val isCompleted: Boolean = false
    ) : QuestObjective()
}

/**
 * Quest rewards
 */
@Serializable
data class QuestReward(
    val experiencePoints: Int = 0,
    val goldAmount: Int = 0,
    val items: List<Entity.Item> = emptyList(),
    val description: String = ""
)

/**
 * A quest with objectives and rewards
 */
@Serializable
data class Quest(
    val id: QuestId,
    val title: String,
    val description: String,
    val giver: String? = null,  // NPC who gave the quest
    val objectives: List<QuestObjective>,
    val reward: QuestReward,
    val status: QuestStatus = QuestStatus.ACTIVE,
    val flavorText: String = ""  // Additional lore/context
) {
    /**
     * Check if all objectives are complete
     */
    fun isComplete(): Boolean = objectives.all { it.isCompleted }

    /**
     * Update a specific objective
     */
    fun updateObjective(objectiveId: String, updater: (QuestObjective) -> QuestObjective): Quest {
        val updatedObjectives = objectives.map {
            if (it.id == objectiveId) updater(it) else it
        }
        return copy(objectives = updatedObjectives)
    }

    /**
     * Mark quest as completed (all objectives done)
     */
    fun complete(): Quest = copy(status = QuestStatus.COMPLETED)

    /**
     * Mark reward as claimed
     */
    fun claim(): Quest = copy(status = QuestStatus.CLAIMED)

    /**
     * Mark quest as failed
     */
    fun fail(): Quest = copy(status = QuestStatus.FAILED)

    /**
     * Get progress summary (e.g., "2/3 objectives complete")
     */
    fun getProgressSummary(): String {
        val completed = objectives.count { it.isCompleted }
        val total = objectives.size
        return "$completed/$total objectives complete"
    }
}
