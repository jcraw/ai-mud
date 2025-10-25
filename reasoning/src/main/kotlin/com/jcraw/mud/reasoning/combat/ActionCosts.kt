package com.jcraw.mud.reasoning.combat

/**
 * Action cost constants for the asynchronous turn-based combat system.
 *
 * Base costs represent the number of ticks an action takes at 0 speed.
 * Actual costs are modulated by the Speed skill using the formula:
 * `actualCost = baseCost / (1 + speedSkillLevel / 10).coerceAtLeast(2)`
 *
 * Minimum action cost is 2 ticks to prevent degenerate cases.
 */
object ActionCosts {
    /** Melee attack action (sword, axe, mace, etc.) */
    const val MELEE_ATTACK = 6

    /** Ranged attack action (bow, crossbow, thrown weapon) */
    const val RANGED_ATTACK = 5

    /** Spell casting action (requires mana) */
    const val SPELL_CAST = 8

    /** Item use action (potions, consumables) */
    const val ITEM_USE = 4

    /** Movement between rooms */
    const val MOVE = 10

    /** Social interactions (talk, emote, persuade) */
    const val SOCIAL = 3

    /** Defensive stance action */
    const val DEFEND = 4

    /** Hide/stealth attempt */
    const val HIDE = 5

    /** Flee attempt from combat */
    const val FLEE = 6

    /**
     * Calculates the actual action cost given base cost and speed level.
     *
     * @param baseCost The base cost in ticks (from constants above)
     * @param speedLevel The entity's Speed skill level
     * @return The actual cost in ticks, minimum 2
     */
    fun calculateCost(baseCost: Int, speedLevel: Int): Long {
        require(baseCost > 0) { "Base cost must be positive" }
        require(speedLevel >= 0) { "Speed level cannot be negative" }

        val speedModifier = 1.0 + (speedLevel / 10.0)
        val actualCost = (baseCost / speedModifier).toLong().coerceAtLeast(2)
        return actualCost
    }
}
