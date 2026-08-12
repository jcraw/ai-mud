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
    "ForbiddenComment"
)

package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.reasoning.skill.SkillManager
import kotlin.random.Random

/** Params for flee resolve (MUD-034k). */
internal data class FleeResolveParams(
    val attackResolver: AttackResolver,
    val random: Random,
    val fleeingEntityId: String,
    val pursuers: List<String>,
    val targetDirection: Direction,
    val worldState: WorldState,
    val skillManager: SkillManager
)

/**
 * Flee resolve pipeline apply body (MUD-034k pure-move).
 */
internal object FleeResolveApply {

    suspend fun resolve(p: FleeResolveParams): FleeResult {
        val fleeingEntity = CombatEntityLookup.findEntity(p.worldState, p.fleeingEntityId)
            ?: return FleeResult.error("Fleeing entity not found")
        val fleeingSkills = CombatEntityLookup.getComponent<SkillComponent>(
            fleeingEntity, ComponentType.SKILL, p.worldState, p.skillManager
        ) ?: return FleeResult.error("Fleeing entity has no skill component")
        if (p.pursuers.isEmpty()) {
            return autoSuccess(p)
        }
        return withPursuers(p, fleeingEntity.name, fleeingSkills)
    }

    private fun autoSuccess(p: FleeResolveParams): FleeResult = FleeResult.success(
        fleeingEntityId = p.fleeingEntityId,
        targetDirection = p.targetDirection,
        fleeRoll = 0,
        pursuitRoll = 0,
        escapeSkillUsed = true,
        pursuitSkillsUsed = emptyMap(),
        freeAttacks = emptyList()
    )

    private suspend fun withPursuers(
        p: FleeResolveParams,
        fleeingName: String,
        fleeingSkills: SkillComponent
    ): FleeResult {
        val escapeLevel = fleeingSkills.getEffectiveLevel("Escape")
        val fleeRoll = rollFlee(fleeingName, fleeingSkills, p.random)
        val pursuit = FleeResolvePursuit.evaluate(
            p.pursuers, p.worldState, p.skillManager, p.random
        )
        val success = fleeRoll > pursuit.highestPursuitRoll
        println("[FLEE DEBUG] Flee ${if (success) "SUCCESS" else "FAILURE"} (flee: $fleeRoll vs pursuit: ${pursuit.highestPursuitRoll})")
        if (success) {
            return successResult(p, fleeRoll, pursuit, escapeLevel)
        }
        return FleeResolveFreeAttack.onFailure(p, fleeRoll, pursuit, escapeLevel)
    }

    private fun rollFlee(name: String, skills: SkillComponent, random: Random): Int {
        val agility = skills.getEffectiveLevel("Agility")
        val escape = skills.getEffectiveLevel("Escape")
        val modifier = (agility * 0.6 + escape * 0.4).toInt()
        println("[FLEE DEBUG] Fleeing entity: $name")
        println("[FLEE DEBUG]   - Agility level: $agility")
        println("[FLEE DEBUG]   - Escape level: $escape")
        println("[FLEE DEBUG]   - Flee modifier: $modifier")
        val fleeRoll = AttackResolveRolls.rollD20(random) + modifier
        println("[FLEE DEBUG]   - Flee roll: $fleeRoll")
        return fleeRoll
    }

    private fun successResult(
        p: FleeResolveParams,
        fleeRoll: Int,
        pursuit: FleeResolvePursuit.Outcome,
        escapeLevel: Int
    ): FleeResult = FleeResult.success(
        fleeingEntityId = p.fleeingEntityId,
        targetDirection = p.targetDirection,
        fleeRoll = fleeRoll,
        pursuitRoll = pursuit.highestPursuitRoll,
        escapeSkillUsed = escapeLevel > 0,
        pursuitSkillsUsed = pursuit.pursuitSkillsUsed,
        freeAttacks = emptyList()
    )
}
