package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Status effect that can be applied to entities in combat
 * Handles buffs, debuffs, damage-over-time, and temporary modifiers
 */
@Serializable
data class StatusEffect(
    val type: StatusEffectType,
    val magnitude: Int, // Effect strength (e.g., damage per tick, % bonus)
    val duration: Int, // Ticks remaining
    val source: String // Entity ID that applied this effect
) {
    /**
     * Process one tick of this effect
     * Returns updated effect with decreased duration, or null if expired
     */
    fun tick(): StatusEffect? {
        val newDuration = duration - 1
        return if (newDuration <= 0) {
            null // Effect has expired
        } else {
            copy(duration = newDuration)
        }
    }

    /**
     * Check if this effect has expired
     */
    fun isExpired(): Boolean = duration <= 0
}

/**
 * Types of status effects
 * V1 includes 5 basic types, can be expanded in future versions
 */
@Serializable
enum class StatusEffectType {
    /** Damage over time (poison, bleeding, burning) */
    POISON_DOT,

    /** Temporary strength increase */
    STRENGTH_BOOST,

    /** Movement and action speed reduction */
    SLOW,

    /** Health restoration over time */
    REGENERATION,

    /** Temporary damage absorption shield */
    SHIELD,

    /** Hidden from enemy targeting (stealth) */
    HIDDEN,

    /** Defensive stance - reduces incoming damage */
    DEFENSIVE_STANCE
}
