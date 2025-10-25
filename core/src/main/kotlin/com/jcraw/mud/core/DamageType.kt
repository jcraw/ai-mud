package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Types of damage that can be dealt in combat
 * Used for resistance calculations and status effect application
 *
 * Resistance reduction formula: resistSkillLevel / 2%
 * Example: Fire Resistance skill at level 20 reduces fire damage by 10%
 */
@Serializable
enum class DamageType {
    /** Physical damage from melee/ranged weapons */
    PHYSICAL,

    /** Fire damage from spells, burning weapons, environmental hazards */
    FIRE,

    /** Cold/Ice damage from frost spells and frozen weapons */
    COLD,

    /** Poison damage from toxic weapons and venomous creatures */
    POISON,

    /** Lightning damage from electrical spells and storms */
    LIGHTNING,

    /** Arcane/magical damage that bypasses physical armor */
    MAGIC
}
