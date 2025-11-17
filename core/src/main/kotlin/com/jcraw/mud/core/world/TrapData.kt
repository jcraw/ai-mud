package com.jcraw.mud.core.world

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SkillComponent
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Result of trap interaction
 */
sealed class TrapResult {
    data class Success(val avoided: Boolean, val damage: Int) : TrapResult()
    data class Failure(val reason: String) : TrapResult()
}

/**
 * Trap data with difficulty and state tracking
 * Triggered flag persists state across game sessions
 */
@Serializable
data class TrapData(
    val id: String,
    val type: String,
    val difficulty: Int,
    val triggered: Boolean = false,
    val description: String = ""
) {
    /**
     * Attempt to avoid or disarm trap with skill check
     * D20 + Perception vs difficulty DC
     * @param playerSkills Player's V2 skill component for Perception check
     */
    fun roll(playerSkills: SkillComponent): TrapResult {
        if (triggered) {
            return TrapResult.Failure("Trap already triggered")
        }

        // Use V2 skill system
        val perceptionLevel = playerSkills.getEffectiveLevel("Perception")

        // D20 + skill level vs DC
        val roll = Random.nextInt(1, 21)
        val total = roll + perceptionLevel
        val avoided = total >= difficulty

        // Calculate damage if trap triggers
        val damage = if (avoided) 0 else calculateDamage()

        return TrapResult.Success(avoided = avoided, damage = damage)
    }

    /**
     * Calculate trap damage based on difficulty
     */
    private fun calculateDamage(): Int {
        // Base damage scales with difficulty
        val baseDamage = difficulty / 2
        val variance = Random.nextInt(1, difficulty + 1)
        return baseDamage + variance
    }

    /**
     * Mark trap as triggered
     */
    fun trigger(): TrapData = copy(triggered = true)
}
