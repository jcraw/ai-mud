package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.SocialComponent

/**
 * Modulates AI behavior based on personality traits
 * Provides personality-specific thresholds and action preferences
 *
 * Design principles:
 * - Personality traits override default tactical decisions
 * - Traits create distinct, recognizable behavior patterns
 * - Multiple traits can combine for complex behaviors
 */
object PersonalityAI {

    /**
     * Check if NPC should flee based on personality and HP
     *
     * Default flee threshold: HP < 30%
     * Cowardly: HP < 50%
     * Brave/Aggressive: Never flee (or only at HP < 10%)
     *
     * @return true if should flee, false otherwise
     */
    fun shouldFlee(
        socialComponent: SocialComponent?,
        combatComponent: CombatComponent
    ): Boolean {
        val hpPercentage = combatComponent.currentHp.toDouble() / combatComponent.maxHp
        val traits = socialComponent?.traits ?: emptyList()

        return when {
            // Aggressive/brave never flee unless near death
            hasAggressiveTrait(traits) -> hpPercentage < 0.1
            hasBraveTrait(traits) -> hpPercentage < 0.1

            // Cowardly flees earlier
            hasCowardlyTrait(traits) -> hpPercentage < 0.5

            // Defensive flees at moderate HP
            hasDefensiveTrait(traits) -> hpPercentage < 0.35

            // Default threshold
            else -> hpPercentage < 0.3
        }
    }

    /**
     * Check if NPC should prefer defensive action
     *
     * Defensive personality prefers Defend over Attack when HP < 70%
     */
    fun prefersDefense(
        socialComponent: SocialComponent?,
        combatComponent: CombatComponent
    ): Boolean {
        val hpPercentage = combatComponent.currentHp.toDouble() / combatComponent.maxHp
        val traits = socialComponent?.traits ?: emptyList()

        return hasDefensiveTrait(traits) && hpPercentage < 0.7
    }

    /**
     * Check if NPC should always attack regardless of situation
     *
     * Aggressive/reckless personalities never wait or defend
     */
    fun isAggressiveOnly(socialComponent: SocialComponent?): Boolean {
        val traits = socialComponent?.traits ?: emptyList()
        return hasAggressiveTrait(traits) || hasRecklessTrait(traits)
    }

    /**
     * Modify AI decision based on personality
     * Can override LLM decision to enforce personality constraints
     *
     * @param decision The original AI decision
     * @param socialComponent NPC's social component
     * @param combatComponent NPC's combat component
     * @return Modified decision (or original if no modification needed)
     */
    fun modifyDecision(
        decision: AIDecision,
        socialComponent: SocialComponent?,
        combatComponent: CombatComponent
    ): AIDecision {
        val traits = socialComponent?.traits ?: emptyList()
        val hpPercentage = combatComponent.currentHp.toDouble() / combatComponent.maxHp

        // Aggressive NPCs should never flee or wait
        if (isAggressiveOnly(socialComponent)) {
            return when (decision) {
                is AIDecision.Flee -> AIDecision.Attack(
                    target = "player",
                    reasoning = "Aggressive nature prevents fleeing"
                )
                is AIDecision.Wait -> AIDecision.Attack(
                    target = "player",
                    reasoning = "Aggressive nature demands action"
                )
                is AIDecision.Defend -> AIDecision.Attack(
                    target = "player",
                    reasoning = "Aggressive nature prefers offense"
                )
                else -> decision
            }
        }

        // Cowardly NPCs should flee earlier
        if (hasCowardlyTrait(traits) && hpPercentage < 0.5) {
            return AIDecision.Flee("Cowardly nature - fleeing at ${(hpPercentage * 100).toInt()}% HP")
        }

        // Defensive NPCs should defend when injured
        if (prefersDefense(socialComponent, combatComponent)) {
            return when (decision) {
                is AIDecision.Attack -> AIDecision.Defend(
                    reasoning = "Defensive nature - protecting self at ${(hpPercentage * 100).toInt()}% HP"
                )
                else -> decision
            }
        }

        // Greedy NPCs should avoid fleeing if they have loot
        if (hasGreedyTrait(traits) && hpPercentage > 0.2) {
            return when (decision) {
                is AIDecision.Flee -> AIDecision.Attack(
                    target = "player",
                    reasoning = "Greedy nature - won't abandon potential loot"
                )
                else -> decision
            }
        }

        // Honorable NPCs won't attack defenseless opponents
        // (This would need additional context about enemy state)
        // For now, just return original decision

        return decision
    }

    /**
     * Get action preference weight based on personality
     * Used to bias decision-making toward preferred actions
     *
     * @return Map of action type to preference weight (1.0 = normal, >1.0 = preferred, <1.0 = avoided)
     */
    fun getActionPreferences(socialComponent: SocialComponent?): Map<String, Double> {
        val traits = socialComponent?.traits ?: emptyList()
        val preferences = mutableMapOf<String, Double>()

        when {
            hasAggressiveTrait(traits) -> {
                preferences["Attack"] = 2.0
                preferences["Defend"] = 0.3
                preferences["Flee"] = 0.1
                preferences["Wait"] = 0.2
            }
            hasDefensiveTrait(traits) -> {
                preferences["Attack"] = 0.7
                preferences["Defend"] = 1.8
                preferences["Flee"] = 1.2
            }
            hasCowardlyTrait(traits) -> {
                preferences["Attack"] = 0.6
                preferences["Defend"] = 1.3
                preferences["Flee"] = 2.0
            }
            hasBraveTrait(traits) -> {
                preferences["Attack"] = 1.5
                preferences["Defend"] = 0.8
                preferences["Flee"] = 0.2
            }
            else -> {
                // Default: no preference
                preferences["Attack"] = 1.0
                preferences["Defend"] = 1.0
                preferences["Flee"] = 1.0
                preferences["UseItem"] = 1.0
                preferences["Wait"] = 1.0
            }
        }

        return preferences
    }

    // Trait detection helpers

    private fun hasAggressiveTrait(traits: List<String>): Boolean {
        return traits.any { it.equals("aggressive", ignoreCase = true) }
    }

    private fun hasBraveTrait(traits: List<String>): Boolean {
        return traits.any { it.equals("brave", ignoreCase = true) || it.equals("courageous", ignoreCase = true) }
    }

    private fun hasCowardlyTrait(traits: List<String>): Boolean {
        return traits.any { it.equals("cowardly", ignoreCase = true) || it.equals("fearful", ignoreCase = true) }
    }

    private fun hasDefensiveTrait(traits: List<String>): Boolean {
        return traits.any { it.equals("defensive", ignoreCase = true) || it.equals("cautious", ignoreCase = true) }
    }

    private fun hasRecklessTrait(traits: List<String>): Boolean {
        return traits.any { it.equals("reckless", ignoreCase = true) }
    }

    private fun hasGreedyTrait(traits: List<String>): Boolean {
        return traits.any { it.equals("greedy", ignoreCase = true) }
    }

    private fun hasHonorableTrait(traits: List<String>): Boolean {
        return traits.any { it.equals("honorable", ignoreCase = true) }
    }

    /**
     * Get personality-specific description for combat behavior
     * Used in narration to explain why NPC chose this action
     */
    fun getActionFlavor(
        decision: AIDecision,
        socialComponent: SocialComponent?
    ): String {
        val personality = socialComponent?.personality ?: return ""
        val traits = socialComponent.traits

        return when (decision) {
            is AIDecision.Attack -> when {
                hasAggressiveTrait(traits) -> "charges aggressively"
                hasBraveTrait(traits) -> "bravely attacks"
                hasRecklessTrait(traits) -> "recklessly lunges"
                else -> "attacks"
            }
            is AIDecision.Defend -> when {
                hasDefensiveTrait(traits) -> "carefully guards"
                hasCowardlyTrait(traits) -> "fearfully raises defenses"
                else -> "defends"
            }
            is AIDecision.Flee -> when {
                hasCowardlyTrait(traits) -> "flees in terror"
                hasDefensiveTrait(traits) -> "tactically retreats"
                else -> "attempts to escape"
            }
            is AIDecision.UseItem -> "uses an item"
            is AIDecision.Wait -> "hesitates"
            is AIDecision.Error -> ""
        }
    }
}
