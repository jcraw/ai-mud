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

package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.reasoning.skill.SkillManager
import kotlin.random.Random

/**
 * Pursuit roll loop for [FleeResolveApply] (MUD-034k).
 */
internal object FleeResolvePursuit {

    data class Outcome(
        val highestPursuitRoll: Int,
        val bestPursuer: String?,
        val pursuitSkillsUsed: Map<String, Int>
    )

    fun evaluate(
        pursuers: List<String>,
        worldState: WorldState,
        skillManager: SkillManager,
        random: Random
    ): Outcome {
        var highest = 0
        var best: String? = null
        val used = mutableMapOf<String, Int>()
        for (pursuerId in pursuers) {
            val roll = rollOne(pursuerId, worldState, skillManager, random, used) ?: continue
            if (roll > highest) {
                highest = roll
                best = pursuerId
            }
        }
        return Outcome(highest, best, used)
    }

    private fun rollOne(
        pursuerId: String,
        worldState: WorldState,
        skillManager: SkillManager,
        random: Random,
        used: MutableMap<String, Int>
    ): Int? {
        val pursuer = CombatEntityLookup.findEntity(worldState, pursuerId) ?: return null
        val skills = CombatEntityLookup.getComponent<SkillComponent>(
            pursuer, ComponentType.SKILL, worldState, skillManager
        ) ?: return null
        val pursuitLevel = skills.getEffectiveLevel("Pursuit")
        val pursuitRoll = AttackResolveRolls.rollD20(random) + pursuitLevel
        println("[FLEE DEBUG] Pursuer: ${pursuer.name}")
        println("[FLEE DEBUG]   - Pursuit level: $pursuitLevel")
        println("[FLEE DEBUG]   - Pursuit roll: $pursuitRoll")
        used[pursuerId] = pursuitLevel
        return pursuitRoll
    }
}
