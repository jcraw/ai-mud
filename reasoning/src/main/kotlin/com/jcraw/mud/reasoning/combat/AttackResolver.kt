package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.skill.SkillManager
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
     * @param skillManager SkillManager for accessing V2 skill components
     * @param attackerEquipped Attacker's equipped items (for weapon damage/bonuses)
     * @param defenderEquipped Defender's equipped items (for armor defense)
     * @param templates Map of item templates for property lookup
     * @return AttackResult with outcome and updated state
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
    ): AttackResult {
        // Get entities
        val attacker = worldState.findEntity(attackerId)
        val defender = worldState.findEntity(defenderId)

        if (attacker == null || defender == null) {
            return AttackResult.failure("Invalid attacker or defender")
        }

        // Get components (using SkillManager for players, component storage for NPCs)
        val attackerSkills = attacker.getComponent<SkillComponent>(ComponentType.SKILL, worldState, skillManager)
        val defenderSkills = defender.getComponent<SkillComponent>(ComponentType.SKILL, worldState, skillManager)
        val defenderCombat = defender.getComponent<CombatComponent>(ComponentType.COMBAT, worldState, skillManager)

        // Debug logging
        println("[COMBAT DEBUG] Attacker: ${attacker.name} (${attacker.javaClass.simpleName})")
        println("[COMBAT DEBUG]   - SkillComponent: ${if (attackerSkills != null) "FOUND" else "MISSING"}")
        if (attacker is Entity.NPC) {
            println("[COMBAT DEBUG]   - NPC components: ${attacker.components.keys}")
        }
        println("[COMBAT DEBUG] Defender: ${defender.name} (${defender.javaClass.simpleName})")
        println("[COMBAT DEBUG]   - CombatComponent: ${if (defenderCombat != null) "FOUND" else "MISSING"}")
        println("[COMBAT DEBUG]   - SkillComponent: ${if (defenderSkills != null) "FOUND" else "MISSING"}")
        if (defender is Entity.NPC) {
            println("[COMBAT DEBUG]   - NPC components: ${defender.components.keys}")
        }

        if (attackerSkills == null || defenderCombat == null) {
            return AttackResult.failure("Missing required components")
        }

        // 1. Get skill weights from classifier
        val skillWeights = skillClassifier.classifySkills(action, attackerSkills)

        if (skillWeights.isEmpty()) {
            // Should never happen with new SkillClassifier, but fallback to level 0 attack
            println("[COMBAT DEBUG] No skills classified (unexpected), using pure d20 roll")
            return AttackResult.failure("No applicable skills for this action")
        }

        // 2. Calculate attack modifier (weighted sum of skill levels)
        // getEffectiveLevel() returns 0 for unlocked skills, allowing level 0 usage
        val attackModifier = skillWeights.sumOf {
            val skillLevel = attackerSkills.getEffectiveLevel(it.skill)
            println("[COMBAT DEBUG]   - Using skill ${it.skill}: level=$skillLevel (weight=${it.weight})")
            skillLevel * it.weight
        }.toInt()
        println("[COMBAT DEBUG] Total attack modifier: $attackModifier")

        // 3. Roll attack: d20 + modifier
        val attackRoll = rollD20() + attackModifier

        // 4. Calculate defense: d20 + (Dodge*0.6 + Parry*0.4)
        val defenderSkillsUsed = mutableListOf<String>()
        val dodgeLevel: Int
        val parryLevel: Int

        val defenseModifier = if (defenderSkills != null) {
            dodgeLevel = defenderSkills.getEffectiveLevel("Dodge")
            parryLevel = defenderSkills.getEffectiveLevel("Parry")
            // Always track defensive skills (even at level 0) for XP progression
            // Level-0 skills can unlock via dual-path: lucky chance OR XP accumulation
            defenderSkillsUsed.add("Dodge")
            defenderSkillsUsed.add("Parry")
            (dodgeLevel * 0.6 + parryLevel * 0.4).toInt()
        } else {
            dodgeLevel = 0
            parryLevel = 0
            0
        }
        val defenseRoll = rollD20() + defenseModifier

        // 5. Determine hit
        val isHit = attackRoll > defenseRoll

        // 6. Calculate defense outcome
        val defenseOutcome = if (!isHit) {
            // Attack missed - which skill saved them?
            val dodgeContribution = dodgeLevel * 0.6
            val parryContribution = parryLevel * 0.4
            when {
                dodgeContribution > parryContribution * 1.5 -> DefenseOutcome.DODGED
                parryContribution > dodgeContribution * 1.5 -> DefenseOutcome.PARRIED
                dodgeContribution > 0 || parryContribution > 0 -> DefenseOutcome.BLOCKED
                else -> DefenseOutcome.DODGED // Pure luck dodge (no skills)
            }
        } else {
            DefenseOutcome.OVERWHELMED
        }

        if (!isHit) {
            // Attack missed
            return AttackResult.miss(
                attackerId = attackerId,
                defenderId = defenderId,
                attackRoll = attackRoll,
                defenseRoll = defenseRoll,
                attackerSkillsUsed = skillWeights.map { it.skill },
                defenderSkillsUsed = defenderSkillsUsed,
                defenseOutcome = defenseOutcome,
                wasDodged = defenseRoll > attackRoll
            )
        }

        // 7. Calculate damage
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

        val damageResult = damageCalculator.calculateDamage(
            damageContext,
            worldState,
            attackerEquipped,
            defenderEquipped,
            templates
        )

        // 8. Apply damage to defender's CombatComponent
        val updatedDefenderCombat = defenderCombat.applyDamage(
            damageResult.finalDamage,
            damageResult.damageType
        )

        // 9. Check for status effects (from weapon or ability)
        // V1: No status effects on basic attacks, will be added in later phases
        val statusEffects = emptyList<StatusEffect>()

        // 10. Return result
        return AttackResult.hit(
            attackerId = attackerId,
            defenderId = defenderId,
            damage = damageResult.finalDamage,
            damageType = damageResult.damageType,
            attackRoll = attackRoll,
            defenseRoll = defenseRoll,
            attackerSkillsUsed = skillWeights.map { it.skill },
            defenderSkillsUsed = defenderSkillsUsed,
            defenseOutcome = defenseOutcome,
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
    abstract val attackerSkillsUsed: List<String>
    abstract val defenderSkillsUsed: List<String>

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
        override val attackerSkillsUsed: List<String>,
        override val defenderSkillsUsed: List<String>,
        val defenseOutcome: DefenseOutcome,
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
        override val attackerSkillsUsed: List<String>,
        override val defenderSkillsUsed: List<String>,
        val defenseOutcome: DefenseOutcome,
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
        override val attackerSkillsUsed: List<String> = emptyList()
        override val defenderSkillsUsed: List<String> = emptyList()
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
            attackerSkillsUsed: List<String>,
            defenderSkillsUsed: List<String>,
            defenseOutcome: DefenseOutcome,
            updatedDefenderCombat: CombatComponent,
            statusEffects: List<StatusEffect> = emptyList(),
            wasKilled: Boolean = false
        ) = Hit(
            attackerId, defenderId, damage, damageType, attackRoll, defenseRoll,
            attackerSkillsUsed, defenderSkillsUsed, defenseOutcome,
            updatedDefenderCombat, statusEffects, wasKilled
        )

        fun miss(
            attackerId: String,
            defenderId: String,
            attackRoll: Int,
            defenseRoll: Int,
            attackerSkillsUsed: List<String>,
            defenderSkillsUsed: List<String>,
            defenseOutcome: DefenseOutcome,
            wasDodged: Boolean
        ) = Miss(attackerId, defenderId, attackRoll, defenseRoll,
                 attackerSkillsUsed, defenderSkillsUsed, defenseOutcome, wasDodged)

        fun failure(reason: String) = Failure(reason)
    }
}

/**
 * Defense outcome - how the defender's skills contributed to the result
 */
enum class DefenseOutcome {
    DODGED,      // Dodge skill was primary contributor
    PARRIED,     // Parry skill was primary contributor
    BLOCKED,     // Both contributed equally
    OVERWHELMED  // Defense attempted but attacker won
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
    val armorDefense: Int = 0,
    val variance: Int,
    val finalDamage: Int,
    val damageType: DamageType
)

/**
 * Helper to find entity by ID in world state
 */
private fun WorldState.findEntity(entityId: String): Entity? {
    if (player.id == entityId) {
        val equippedWeapon = player.inventoryComponent.getEquipped(EquipSlot.HANDS_MAIN)
            ?: player.inventoryComponent.getEquipped(EquipSlot.HANDS_BOTH)
        val equippedArmor = player.inventoryComponent.getEquipped(EquipSlot.CHEST)
        return Entity.Player(
            id = player.id,
            name = player.name,
            description = "Player character",
            playerId = player.id,
            health = player.health,
            maxHealth = player.maxHealth,
            equippedWeapon = equippedWeapon?.id,
            equippedArmor = equippedArmor?.id
        )
    }

    // Search global entity storage (V3)
    return entities[entityId]
}

/**
 * Helper to get component from entity
 * For players: Converts PlayerState data to components via SkillManager
 * For NPCs: Uses entity's component storage
 */
private inline fun <reified T : Component> Entity.getComponent(
    type: ComponentType,
    worldState: WorldState,
    skillManager: SkillManager
): T? {
    return when (this) {
        is Entity.Player -> {
            // Bridge V1 PlayerState to V2 components
            when (type) {
                ComponentType.SKILL -> {
                    // Fetch from SkillManager (V2)
                    skillManager.getSkillComponent(this.id) as? T
                }
                ComponentType.COMBAT -> {
                    // Create CombatComponent from PlayerState health
                    CombatComponent(
                        currentHp = worldState.player.health,
                        maxHp = worldState.player.maxHealth
                    ) as? T
                }
                else -> null
            }
        }
        is Entity.NPC -> {
            components[type] as? T
        }
        is Entity.Item -> null
        is Entity.Feature -> null
        is Entity.Corpse -> null
    }
}
