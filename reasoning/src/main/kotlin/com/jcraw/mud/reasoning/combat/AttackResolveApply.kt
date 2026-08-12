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

import com.jcraw.mud.core.EquipSlot
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.reasoning.skill.SkillManager
import kotlin.random.Random

/** Params bag for attack resolve entry (MUD-034k). */
internal data class AttackResolveParams(
    val skillClassifier: SkillClassifier,
    val damageCalculator: DamageCalculator,
    val random: Random,
    val attackerId: String,
    val defenderId: String,
    val action: String,
    val worldState: WorldState,
    val skillManager: SkillManager,
    val attackerEquipped: Map<EquipSlot, ItemInstance>,
    val defenderEquipped: Map<EquipSlot, ItemInstance>,
    val templates: Map<String, ItemTemplate>
)

/**
 * Attack resolve pipeline apply body (MUD-034k pure-move).
 * Order preserved: load → classify → roll → miss | damage/hit.
 */
internal object AttackResolveApply {

    suspend fun resolve(p: AttackResolveParams): AttackResult {
        val loaded = when (val setup = AttackResolveSetup.load(
            p.attackerId, p.defenderId, p.worldState, p.skillManager
        )) {
            is AttackResolveSetup.Outcome.Fail -> return setup.result
            is AttackResolveSetup.Outcome.Ok -> setup.loaded
        }
        return afterLoad(p, loaded)
    }

    private suspend fun afterLoad(
        p: AttackResolveParams,
        loaded: AttackResolveSetup.Loaded
    ): AttackResult {
        val (skillWeights, classifyFail) = AttackResolveRolls.classifyOrFail(
            p.skillClassifier, p.action, loaded.attackerSkills
        )
        if (classifyFail != null) return classifyFail
        val rolls = AttackResolveRolls.compute(
            skillWeights, loaded.attackerSkills, loaded.defenderSkills, p.random
        )
        if (!rolls.isHit) {
            return AttackResolveRolls.missResult(p.attackerId, p.defenderId, rolls)
        }
        return AttackResolveHit.apply(toHitParams(p, loaded, rolls))
    }

    private fun toHitParams(
        p: AttackResolveParams,
        loaded: AttackResolveSetup.Loaded,
        rolls: AttackResolveRolls.RollOutcome
    ) = AttackHitParams(
        attackerId = p.attackerId,
        defenderId = p.defenderId,
        action = p.action,
        worldState = p.worldState,
        attackerSkills = loaded.attackerSkills,
        defenderSkills = loaded.defenderSkills,
        defenderCombat = loaded.defenderCombat,
        rolls = rolls,
        damageCalculator = p.damageCalculator,
        attackerEquipped = p.attackerEquipped,
        defenderEquipped = p.defenderEquipped,
        templates = p.templates
    )
}
