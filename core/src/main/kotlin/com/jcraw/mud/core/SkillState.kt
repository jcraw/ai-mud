package com.jcraw.mud.core

import kotlinx.serialization.Serializable
import kotlin.math.pow

/**
 * Immutable skill state for a single skill
 * Handles XP progression, leveling, perks, and resource pools
 */
@Serializable
data class SkillState(
    val level: Int = 0,
    val xp: Long = 0L,
    val unlocked: Boolean = false,
    val tags: List<String> = emptyList(), // e.g., ["combat", "weapon", "melee"]
    val perks: List<Perk> = emptyList(), // Perks earned at milestone levels
    val resourceType: String? = null, // e.g., "mana", "chi" for resource pool skills
    val tempBuffs: Int = 0 // Temporary level buffs (e.g., from training, observation)
) {
    /**
     * Calculate XP required to reach next level
     * Formula: if (level <= 100) 100 * level^2 else 100 * level^2 * (level / 100)^1.5
     * - Early game: Linear-ish growth (L1→L10 takes ~5,000 XP)
     * - Mid game: Quadratic growth (L50→L60 takes ~185,000 XP)
     * - Late game: Super-exponential (L100→L200 takes millions of XP)
     */
    fun calculateXpToNext(forLevel: Int): Long {
        val nextLevel = forLevel + 1
        return if (nextLevel <= 100) {
            100L * nextLevel * nextLevel
        } else {
            val base = 100L * nextLevel * nextLevel
            val scalingFactor = (nextLevel.toDouble() / 100.0).pow(1.5)
            (base * scalingFactor).toLong()
        }
    }

    /**
     * Get XP required to reach next level for current level
     */
    val xpToNext: Long
        get() = calculateXpToNext(level)

    /**
     * Add XP and level up if threshold crossed
     * Returns updated SkillState with potentially multiple level-ups
     */
    fun addXp(amount: Long): SkillState {
        require(amount >= 0) { "XP amount must be non-negative" }

        var currentXp = xp + amount
        var currentLevel = level

        // Handle multiple level-ups in one XP grant
        while (currentXp >= calculateXpToNext(currentLevel)) {
            currentXp -= calculateXpToNext(currentLevel)
            currentLevel++
        }

        return copy(
            xp = currentXp,
            level = currentLevel
        )
    }

    /**
     * Unlock this skill (sets unlocked = true)
     */
    fun unlock(): SkillState {
        return copy(unlocked = true)
    }

    /**
     * Get effective level (base level + temporary buffs)
     */
    fun getEffectiveLevel(): Int {
        return level + tempBuffs
    }

    /**
     * Apply temporary buff (e.g., from training, observation)
     * Buffs should be cleared periodically (e.g., on rest)
     */
    fun applyBuff(buffAmount: Int): SkillState {
        return copy(tempBuffs = tempBuffs + buffAmount)
    }

    /**
     * Clear all temporary buffs
     */
    fun clearBuffs(): SkillState {
        return copy(tempBuffs = 0)
    }

    /**
     * Add perk to skill
     */
    fun addPerk(perk: Perk): SkillState {
        return copy(perks = perks + perk)
    }

    /**
     * Check if skill has reached a perk milestone (every 10 levels)
     */
    fun isAtPerkMilestone(): Boolean {
        return level % 10 == 0 && level > 0
    }

    /**
     * Get total perk milestones earned (one per 10 levels)
     */
    fun getPerkMilestonesEarned(): Int {
        return level / 10
    }

    /**
     * Check if there's a pending perk choice
     * (reached milestone but hasn't chosen perk yet)
     */
    fun hasPendingPerkChoice(): Boolean {
        return getPerkMilestonesEarned() > perks.size
    }
}

/**
 * Perk granted at skill milestone levels (10, 20, 30, etc.)
 */
@Serializable
data class Perk(
    val name: String,
    val description: String,
    val type: PerkType,
    val effectData: Map<String, String> = emptyMap() // Flexible effect storage (e.g., {"damageBonus": "15"})
)

/**
 * Perk type discriminator
 */
@Serializable
enum class PerkType {
    ABILITY,  // Active ability (e.g., "Quick Strike", "Fireball Volley")
    PASSIVE   // Passive effect (e.g., "+15% Damage", "+10% Parry Chance")
}
