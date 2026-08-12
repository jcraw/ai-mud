@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "NestedBlockDepth",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning

import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.combat.CombatContext
import com.jcraw.mud.memory.combat.NarrationMatcher
import com.jcraw.sophia.llm.LLMClient

/** Params for single-action narration (MUD-034k). */
internal data class ActionNarrateParams(
    val llmClient: LLMClient,
    val memoryManager: MemoryManager?,
    val narrationMatcher: NarrationMatcher?,
    val weapon: String,
    val damage: Int,
    val maxHp: Int,
    val isHit: Boolean,
    val isCritical: Boolean,
    val isDeath: Boolean,
    val isSpell: Boolean,
    val targetName: String
)

/**
 * Single-action combat narration (cache + live + fallback) for [CombatNarrator] (MUD-034k).
 */
internal object CombatNarratorAction {

    suspend fun narrate(p: ActionNarrateParams): String {
        val context = buildContext(p)
        val cached = p.narrationMatcher?.findNarration(context)
        if (cached != null) return cached
        return CombatNarratorActionLive.generate(p)
    }

    private fun buildContext(p: ActionNarrateParams): CombatContext {
        val damageTier = when {
            p.isDeath -> "lethal"
            p.isCritical -> "critical"
            p.damage == 0 -> "none"
            else -> p.narrationMatcher?.determineDamageTier(p.damage, p.maxHp) ?: "medium"
        }
        val outcome = when {
            p.isDeath -> "death"
            p.isCritical -> "critical"
            p.isHit -> "hit"
            else -> "miss"
        }
        val scenario = when {
            p.isDeath -> "death_blow"
            p.isCritical -> "critical_hit"
            else -> p.narrationMatcher?.determineScenario(p.weapon, p.isHit, p.isSpell) ?: "melee_hit"
        }
        return CombatContext(scenario, p.weapon, damageTier, outcome)
    }

    fun fallback(weapon: String, damage: Int, isHit: Boolean, isDeath: Boolean): String = when {
        isDeath -> "Your $weapon delivers the killing blow!"
        isHit -> "Your $weapon strikes for $damage damage!"
        else -> "Your $weapon attack misses!"
    }
}
