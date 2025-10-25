package com.jcraw.mud.reasoning.combat

/**
 * Calculates action costs modified by the Speed skill.
 *
 * Provides a convenient API for converting action types to tick costs,
 * taking into account the entity's Speed skill level.
 */
object SpeedCalculator {

    /**
     * Supported action types for cost calculation.
     */
    enum class ActionType(val baseCost: Int) {
        MELEE_ATTACK(ActionCosts.MELEE_ATTACK),
        RANGED_ATTACK(ActionCosts.RANGED_ATTACK),
        SPELL_CAST(ActionCosts.SPELL_CAST),
        ITEM_USE(ActionCosts.ITEM_USE),
        MOVE(ActionCosts.MOVE),
        SOCIAL(ActionCosts.SOCIAL),
        DEFEND(ActionCosts.DEFEND),
        HIDE(ActionCosts.HIDE),
        FLEE(ActionCosts.FLEE);

        companion object {
            /**
             * Attempts to parse an action string to an ActionType.
             * Falls back to MELEE_ATTACK if no match found.
             */
            fun fromString(action: String): ActionType {
                return when (action.lowercase().trim()) {
                    "melee", "melee_attack", "attack" -> MELEE_ATTACK
                    "ranged", "ranged_attack", "shoot", "fire" -> RANGED_ATTACK
                    "spell", "spell_cast", "cast", "magic" -> SPELL_CAST
                    "item", "item_use", "use" -> ITEM_USE
                    "move", "movement", "walk", "go" -> MOVE
                    "social", "talk", "emote" -> SOCIAL
                    "defend", "block", "parry" -> DEFEND
                    "hide", "stealth", "sneak" -> HIDE
                    "flee", "run", "escape" -> FLEE
                    else -> MELEE_ATTACK // Default fallback
                }
            }
        }
    }

    /**
     * Calculates the action cost in ticks for a given action and speed level.
     *
     * @param actionType The type of action being performed
     * @param speedLevel The entity's Speed skill level (0+)
     * @return The actual cost in ticks (minimum 2)
     */
    fun calculateActionCost(actionType: ActionType, speedLevel: Int): Long {
        return ActionCosts.calculateCost(actionType.baseCost, speedLevel)
    }

    /**
     * Calculates the action cost in ticks for a given action string and speed level.
     *
     * @param action The action name as a string (e.g., "attack", "cast spell")
     * @param speedLevel The entity's Speed skill level (0+)
     * @return The actual cost in ticks (minimum 2)
     */
    fun calculateActionCost(action: String, speedLevel: Int): Long {
        val actionType = ActionType.fromString(action)
        return calculateActionCost(actionType, speedLevel)
    }

    /**
     * Calculates what the effective speed multiplier is at a given skill level.
     *
     * @param speedLevel The Speed skill level
     * @return The speed multiplier (e.g., 2.0 means actions take half as long)
     */
    fun getSpeedMultiplier(speedLevel: Int): Double {
        require(speedLevel >= 0) { "Speed level cannot be negative" }
        return 1.0 + (speedLevel / 10.0)
    }

    /**
     * Calculates how much faster one entity is compared to another.
     *
     * @param speedLevel1 First entity's speed level
     * @param speedLevel2 Second entity's speed level
     * @return Ratio of their speeds (>1 means first is faster)
     */
    fun getSpeedRatio(speedLevel1: Int, speedLevel2: Int): Double {
        val mult1 = getSpeedMultiplier(speedLevel1)
        val mult2 = getSpeedMultiplier(speedLevel2)
        return mult1 / mult2
    }
}
