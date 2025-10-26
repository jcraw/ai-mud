package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import kotlin.random.Random

/**
 * Resolves attack actions using multi-skill checks
 * Determines hit/miss, calculates damage, applies to combat component
 *
 * Design principles:
 * - Multi-skill resolution: Uses SkillClassifier to determine relevant skills
 * - Weighted modifiers: Skills contribute based on their weight
 * - Defender has counter-check: Dodge/Parry skills affect defense
 * - Deterministic when seeded: Accepts Random instance for testing
 */
class AttackResolver(
    private val skillClassifier: SkillClassifier,
    private val damageCalculator: DamageCalculator = DamageCalculator(),
    private val random: Random = Random.Default
) {

    /**
     * Resolve an attack from attacker against defender
     *
     * @param attackerId ID of attacking entity
     * @param defenderId ID of defending entity
     * @param action Description of the attack action (for skill classification)
     * @param worldState Current world state
     * @return AttackResult with outcome and updated state
     */
    suspend fun resolveAttack(
        attackerId: String,
        defenderId: String,
        action: String,
        worldState: WorldState
    ): AttackResult {
        // Get entities
        val attacker = worldState.findEntity(attackerId)
        val defender = worldState.findEntity(defenderId)

        if (attacker == null || defender == null) {
            return AttackResult.failure("Invalid attacker or defender")
        }

        // Get components
        val attackerSkills = attacker.getComponent<SkillComponent>(ComponentType.SKILL)
        val defenderSkills = defender.getComponent<SkillComponent>(ComponentType.SKILL)
        val defenderCombat = defender.getComponent<CombatComponent>(ComponentType.COMBAT)

        if (attackerSkills == null || defenderCombat == null) {
            return AttackResult.failure("Missing required components")
        }

        // 1. Get skill weights from classifier
        val skillWeights = skillClassifier.classifySkills(action, attackerSkills)

        if (skillWeights.isEmpty()) {
            return AttackResult.failure("No applicable skills for this action")
        }

        // 2. Calculate attack modifier (weighted sum of skill levels)
        val attackModifier = skillWeights.sumOf {
            attackerSkills.getEffectiveLevel(it.skill) * it.weight
        }.toInt()

        // 3. Roll attack: d20 + modifier
        val attackRoll = rollD20() + attackModifier

        // 4. Calculate defense: d20 + (Dodge*0.6 + Parry*0.4)
        val defenseModifier = if (defenderSkills != null) {
            val dodgeLevel = defenderSkills.getEffectiveLevel("Dodge")
            val parryLevel = defenderSkills.getEffectiveLevel("Parry")
            (dodgeLevel * 0.6 + parryLevel * 0.4).toInt()
        } else {
            0
        }
        val defenseRoll = rollD20() + defenseModifier

        // 5. Determine hit
        val isHit = attackRoll > defenseRoll

        if (!isHit) {
            // Attack missed
            return AttackResult.miss(
                attackerId = attackerId,
                defenderId = defenderId,
                attackRoll = attackRoll,
                defenseRoll = defenseRoll,
                skillsUsed = skillWeights.map { it.skill },
                wasDodged = defenseRoll > attackRoll
            )
        }

        // 6. Calculate damage
        val damageContext = DamageContext(
            attackerId = attackerId,
            defenderId = defenderId,
            action = action,
            skillWeights = skillWeights,
            attackerSkills = attackerSkills,
            defenderSkills = defenderSkills,
            attackRoll = attackRoll,
            defenseRoll = defenseRoll
        )

        val damageResult = damageCalculator.calculateDamage(damageContext, worldState)

        // 7. Apply damage to defender's CombatComponent
        val updatedDefenderCombat = defenderCombat.applyDamage(
            damageResult.finalDamage,
            damageResult.damageType
        )

        // 8. Check for status effects (from weapon or ability)
        // V1: No status effects on basic attacks, will be added in later phases
        val statusEffects = emptyList<StatusEffect>()

        // 9. Return result
        return AttackResult.hit(
            attackerId = attackerId,
            defenderId = defenderId,
            damage = damageResult.finalDamage,
            damageType = damageResult.damageType,
            attackRoll = attackRoll,
            defenseRoll = defenseRoll,
            skillsUsed = skillWeights.map { it.skill },
            updatedDefenderCombat = updatedDefenderCombat,
            statusEffects = statusEffects,
            wasKilled = updatedDefenderCombat.isDead()
        )
    }

    /**
     * Roll a d20 (1-20)
     */
    private fun rollD20(): Int {
        return random.nextInt(1, 21)
    }
}

/**
 * Result of an attack resolution
 * Contains all information about what happened
 */
sealed class AttackResult {
    abstract val attackerId: String
    abstract val defenderId: String
    abstract val skillsUsed: List<String>

    /**
     * Attack hit successfully
     */
    data class Hit(
        override val attackerId: String,
        override val defenderId: String,
        val damage: Int,
        val damageType: DamageType,
        val attackRoll: Int,
        val defenseRoll: Int,
        override val skillsUsed: List<String>,
        val updatedDefenderCombat: CombatComponent,
        val statusEffects: List<StatusEffect>,
        val wasKilled: Boolean
    ) : AttackResult() {
        val isSuccess = true
    }

    /**
     * Attack missed or was dodged
     */
    data class Miss(
        override val attackerId: String,
        override val defenderId: String,
        val attackRoll: Int,
        val defenseRoll: Int,
        override val skillsUsed: List<String>,
        val wasDodged: Boolean
    ) : AttackResult() {
        val isSuccess = false
    }

    /**
     * Attack failed due to error
     */
    data class Failure(
        val reason: String
    ) : AttackResult() {
        override val attackerId: String = ""
        override val defenderId: String = ""
        override val skillsUsed: List<String> = emptyList()
        val isSuccess = false
    }

    companion object {
        fun hit(
            attackerId: String,
            defenderId: String,
            damage: Int,
            damageType: DamageType,
            attackRoll: Int,
            defenseRoll: Int,
            skillsUsed: List<String>,
            updatedDefenderCombat: CombatComponent,
            statusEffects: List<StatusEffect> = emptyList(),
            wasKilled: Boolean = false
        ) = Hit(
            attackerId, defenderId, damage, damageType, attackRoll, defenseRoll,
            skillsUsed, updatedDefenderCombat, statusEffects, wasKilled
        )

        fun miss(
            attackerId: String,
            defenderId: String,
            attackRoll: Int,
            defenseRoll: Int,
            skillsUsed: List<String>,
            wasDodged: Boolean
        ) = Miss(attackerId, defenderId, attackRoll, defenseRoll, skillsUsed, wasDodged)

        fun failure(reason: String) = Failure(reason)
    }
}

/**
 * Context for damage calculation
 * Encapsulates all information needed to calculate damage
 */
data class DamageContext(
    val attackerId: String,
    val defenderId: String,
    val action: String,
    val skillWeights: List<SkillWeight>,
    val attackerSkills: SkillComponent,
    val defenderSkills: SkillComponent?,
    val attackRoll: Int,
    val defenseRoll: Int
)

/**
 * Result of damage calculation
 */
data class DamageResult(
    val baseDamage: Int,
    val skillModifier: Int,
    val itemBonus: Int,
    val resistanceReduction: Int,
    val variance: Int,
    val finalDamage: Int,
    val damageType: DamageType
)

/**
 * Helper to find entity by ID in world state
 */
private fun WorldState.findEntity(entityId: String): Entity? {
    if (player.id == entityId) {
        return Entity.Player(
            id = player.id,
            name = player.name,
            description = "Player character",
            playerId = player.id,
            health = player.health,
            maxHealth = player.maxHealth,
            equippedWeapon = player.equippedWeapon?.id,
            equippedArmor = player.equippedArmor?.id
        )
    }

    // Search all rooms for the entity
    return rooms.values.flatMap { it.entities }.find { it.id == entityId }
}

/**
 * Helper to get component from entity
 * This is a temporary helper until Entity has proper component support
 */
private inline fun <reified T : Component> Entity.getComponent(type: ComponentType): T? {
    return when (this) {
        is Entity.Player -> {
            // Components come from PlayerState in WorldState
            // This is a limitation of current architecture
            // For now, return null and handle in caller
            null
        }
        is Entity.NPC -> {
            components[type] as? T
        }
        is Entity.Item -> null
        is Entity.Feature -> null
        is Entity.Corpse -> null
    }
}
