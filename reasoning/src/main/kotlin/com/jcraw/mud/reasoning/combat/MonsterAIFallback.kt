@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "NestedBlockDepth",
    "TooManyFunctions",
    "UnusedParameter"
)

package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.core.WorldState

/** Rule-based fallback when LLM is unavailable or fails (MUD-034k). */
internal object MonsterAIFallback {

    fun decide(
        npc: Entity.NPC,
        combatComponent: CombatComponent,
        socialComponent: SocialComponent?,
        worldState: WorldState
    ): AIDecision {
        // npc retained for call-site parity with original fallbackDecision signature
        val hp = combatComponent.currentHp.toDouble() / combatComponent.maxHp
        val cowardly = socialComponent?.traits?.contains("cowardly") == true
        return byHp(hp, cowardly, worldState.player.id)
    }

    private fun byHp(hp: Double, cowardly: Boolean, playerId: String): AIDecision = when {
        hp < 0.3 -> AIDecision.Flee("HP critical")
        hp < 0.5 && cowardly -> AIDecision.Flee("Cowardly nature")
        hp < 0.7 -> AIDecision.UseItem("Need healing")
        hp > 0.7 -> AIDecision.Attack(target = playerId, reasoning = "Healthy, aggressive stance")
        else -> AIDecision.Attack(target = playerId, reasoning = "Default attack")
    }
}
