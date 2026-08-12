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

/**
 * Resolves attack actions using multi-skill checks.
 * Apply body lives in AttackResolve* extracts (MUD-034k pure-move).
 */
class AttackResolver(
    private val skillClassifier: SkillClassifier,
    private val damageCalculator: DamageCalculator = DamageCalculator(),
    private val random: Random = Random.Default
) {

    /**
     * Resolve an attack from attacker against defender
     */
    suspend fun resolveAttack(
        attackerId: String,
        defenderId: String,
        action: String,
        worldState: WorldState,
        skillManager: SkillManager,
        attackerEquipped: Map<EquipSlot, ItemInstance> = emptyMap(),
        defenderEquipped: Map<EquipSlot, ItemInstance> = emptyMap(),
        templates: Map<String, ItemTemplate> = emptyMap()
    ): AttackResult = AttackResolveApply.resolve(
        AttackResolveParams(
            skillClassifier = skillClassifier,
            damageCalculator = damageCalculator,
            random = random,
            attackerId = attackerId,
            defenderId = defenderId,
            action = action,
            worldState = worldState,
            skillManager = skillManager,
            attackerEquipped = attackerEquipped,
            defenderEquipped = defenderEquipped,
            templates = templates
        )
    )
}
