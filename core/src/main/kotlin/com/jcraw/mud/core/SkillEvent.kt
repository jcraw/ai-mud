package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Events related to skill progression
 * Used for tracking skill usage history and triggering UI updates
 */
@Serializable
sealed class SkillEvent {
    abstract val entityId: String
    abstract val skillName: String
    abstract val eventType: String
    abstract val timestamp: Long

    /**
     * Skill was unlocked via attempt, observation, training, or prerequisite
     */
    @Serializable
    data class SkillUnlocked(
        override val entityId: String,
        override val skillName: String,
        val unlockMethod: String, // "attempt", "observation", "training", "prerequisite"
        override val timestamp: Long = System.currentTimeMillis()
    ) : SkillEvent() {
        override val eventType: String = "SKILL_UNLOCKED"
    }

    /**
     * Entity gained XP in a skill
     */
    @Serializable
    data class XpGained(
        override val entityId: String,
        override val skillName: String,
        val xpAmount: Long,
        val currentXp: Long,
        val currentLevel: Int,
        val success: Boolean, // Whether the action that granted XP was successful
        override val timestamp: Long = System.currentTimeMillis()
    ) : SkillEvent() {
        override val eventType: String = "XP_GAINED"
    }

    /**
     * Entity leveled up a skill
     */
    @Serializable
    data class LevelUp(
        override val entityId: String,
        override val skillName: String,
        val oldLevel: Int,
        val newLevel: Int,
        val isAtPerkMilestone: Boolean, // True if this level-up triggers a perk choice (every 10 levels)
        override val timestamp: Long = System.currentTimeMillis()
    ) : SkillEvent() {
        override val eventType: String = "LEVEL_UP"
    }

    /**
     * Entity chose and unlocked a perk for a skill
     */
    @Serializable
    data class PerkUnlocked(
        override val entityId: String,
        override val skillName: String,
        val perk: Perk,
        val skillLevel: Int, // The level at which the perk was unlocked
        override val timestamp: Long = System.currentTimeMillis()
    ) : SkillEvent() {
        override val eventType: String = "PERK_UNLOCKED"
    }

    /**
     * Entity attempted a skill check
     */
    @Serializable
    data class SkillCheckAttempt(
        override val entityId: String,
        override val skillName: String,
        val difficulty: Int,
        val roll: Int,
        val skillLevel: Int,
        val success: Boolean,
        val margin: Int, // How much they succeeded/failed by (positive = success margin)
        val isOpposed: Boolean = false, // True if this was an opposed check
        val opposingSkill: String? = null,
        override val timestamp: Long = System.currentTimeMillis()
    ) : SkillEvent() {
        override val eventType: String = "SKILL_CHECK_ATTEMPT"
    }
}
