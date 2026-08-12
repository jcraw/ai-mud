package com.jcraw.mud.reasoning.combat

/**
 * AI decision types that NPCs can make
 */
sealed class AIDecision {
    abstract val reasoning: String

    data class Attack(
        val target: String,
        override val reasoning: String
    ) : AIDecision()

    data class Defend(
        override val reasoning: String
    ) : AIDecision()

    data class UseItem(
        override val reasoning: String
    ) : AIDecision()

    data class Flee(
        override val reasoning: String
    ) : AIDecision()

    data class Wait(
        override val reasoning: String
    ) : AIDecision()

    data class Error(
        override val reasoning: String
    ) : AIDecision()
}
