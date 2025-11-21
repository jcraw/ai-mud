package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.skill.SkillManager
import kotlin.random.Random

/**
 * Resolves flee attempts using skill-based checks
 * Determines success/failure, triggers free attack on failure
 *
 * Design principles:
 * - Dual-skill formula: Agility*0.6 + Escape*0.4 (player) vs Pursuit (enemy)
 * - Success: Player moves to target direction, hostiles don't follow
 * - Failure: Enemy gets free attack (no defense roll), movement blocked
 * - Skill progression: Both Escape and Pursuit gain XP on use
 */
class FleeResolver(
    private val attackResolver: AttackResolver,
    private val random: Random = Random.Default
) {

    /**
     * Resolve a flee attempt from fleeing entity against pursuing entities
     *
     * @param fleeingEntityId ID of entity attempting to flee
     * @param pursuers List of entity IDs pursuing the fleeing entity
     * @param targetDirection Direction to flee towards
     * @param worldState Current world state
     * @param skillManager SkillManager for accessing skill components
     * @return FleeResult with outcome and any free attacks
     */
    suspend fun resolveFlee(
        fleeingEntityId: String,
        pursuers: List<String>,
        targetDirection: Direction,
        worldState: WorldState,
        skillManager: SkillManager
    ): FleeResult {
        // Get fleeing entity
        val fleeingEntity = worldState.findEntity(fleeingEntityId)
        if (fleeingEntity == null) {
            return FleeResult.error("Fleeing entity not found")
        }

        // Get fleeing entity's skills
        val fleeingSkills = fleeingEntity.getComponent<SkillComponent>(ComponentType.SKILL, worldState, skillManager)
        if (fleeingSkills == null) {
            return FleeResult.error("Fleeing entity has no skill component")
        }

        // If no pursuers, flee succeeds automatically
        if (pursuers.isEmpty()) {
            return FleeResult.success(
                fleeingEntityId = fleeingEntityId,
                targetDirection = targetDirection,
                fleeRoll = 0,
                pursuitRoll = 0,
                escapeSkillUsed = true,
                pursuitSkillsUsed = emptyMap(),
                freeAttacks = emptyList()
            )
        }

        // 1. Calculate flee modifier: Agility*0.6 + Escape*0.4
        val agilityLevel = fleeingSkills.getEffectiveLevel("Agility")
        val escapeLevel = fleeingSkills.getEffectiveLevel("Escape")
        val fleeModifier = (agilityLevel * 0.6 + escapeLevel * 0.4).toInt()

        println("[FLEE DEBUG] Fleeing entity: ${fleeingEntity.name}")
        println("[FLEE DEBUG]   - Agility level: $agilityLevel")
        println("[FLEE DEBUG]   - Escape level: $escapeLevel")
        println("[FLEE DEBUG]   - Flee modifier: $fleeModifier")

        // 2. Roll flee check: d20 + modifier
        val fleeRoll = rollD20() + fleeModifier
        println("[FLEE DEBUG]   - Flee roll: $fleeRoll")

        // 3. Calculate highest pursuit check from all pursuers
        var highestPursuitRoll = 0
        var bestPursuer: String? = null
        val pursuitSkillsUsed = mutableMapOf<String, Int>()

        for (pursuerId in pursuers) {
            val pursuer = worldState.findEntity(pursuerId)
            if (pursuer == null) {
                continue
            }

            val pursuerSkills = pursuer.getComponent<SkillComponent>(ComponentType.SKILL, worldState, skillManager)
            if (pursuerSkills == null) {
                continue
            }

            // Calculate pursuit modifier
            val pursuitLevel = pursuerSkills.getEffectiveLevel("Pursuit")
            val pursuitModifier = pursuitLevel
            val pursuitRoll = rollD20() + pursuitModifier

            println("[FLEE DEBUG] Pursuer: ${pursuer.name}")
            println("[FLEE DEBUG]   - Pursuit level: $pursuitLevel")
            println("[FLEE DEBUG]   - Pursuit roll: $pursuitRoll")

            pursuitSkillsUsed[pursuerId] = pursuitLevel

            if (pursuitRoll > highestPursuitRoll) {
                highestPursuitRoll = pursuitRoll
                bestPursuer = pursuerId
            }
        }

        // 4. Determine success
        val success = fleeRoll > highestPursuitRoll
        println("[FLEE DEBUG] Flee ${if (success) "SUCCESS" else "FAILURE"} (flee: $fleeRoll vs pursuit: $highestPursuitRoll)")

        // 5. Handle success (no free attacks)
        if (success) {
            return FleeResult.success(
                fleeingEntityId = fleeingEntityId,
                targetDirection = targetDirection,
                fleeRoll = fleeRoll,
                pursuitRoll = highestPursuitRoll,
                escapeSkillUsed = escapeLevel > 0,
                pursuitSkillsUsed = pursuitSkillsUsed,
                freeAttacks = emptyList()
            )
        }

        // 6. Handle failure (free attack from best pursuer, no defense roll)
        if (bestPursuer == null) {
            // Should not happen, but handle gracefully
            return FleeResult.failure(
                fleeingEntityId = fleeingEntityId,
                targetDirection = targetDirection,
                fleeRoll = fleeRoll,
                pursuitRoll = highestPursuitRoll,
                escapeSkillUsed = escapeLevel > 0,
                pursuitSkillsUsed = pursuitSkillsUsed,
                freeAttacks = emptyList(),
                interceptorId = null
            )
        }

        // 7. Execute free attack (no defense roll)
        val freeAttack = attackResolver.resolveAttack(
            attackerId = bestPursuer,
            defenderId = fleeingEntityId,
            action = "free attack during flee",
            worldState = worldState,
            skillManager = skillManager,
            attackerEquipped = emptyMap(), // TODO: Get equipped items if needed
            defenderEquipped = emptyMap(),
            templates = emptyMap()
        )

        return FleeResult.failure(
            fleeingEntityId = fleeingEntityId,
            targetDirection = targetDirection,
            fleeRoll = fleeRoll,
            pursuitRoll = highestPursuitRoll,
            escapeSkillUsed = escapeLevel > 0,
            pursuitSkillsUsed = pursuitSkillsUsed,
            freeAttacks = listOf(freeAttack),
            interceptorId = bestPursuer
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
 * Result of a flee resolution
 * Contains all information about what happened
 */
sealed class FleeResult {
    abstract val fleeingEntityId: String
    abstract val escapeSkillUsed: Boolean
    abstract val pursuitSkillsUsed: Map<String, Int> // Pursuer ID -> Pursuit skill level

    /**
     * Flee succeeded - entity escapes to target direction
     */
    data class Success(
        override val fleeingEntityId: String,
        val targetDirection: Direction,
        val fleeRoll: Int,
        val pursuitRoll: Int,
        override val escapeSkillUsed: Boolean,
        override val pursuitSkillsUsed: Map<String, Int>,
        val freeAttacks: List<AttackResult>
    ) : FleeResult() {
        val isSuccess = true
    }

    /**
     * Flee failed - entity is intercepted and takes free attack(s)
     */
    data class Failure(
        override val fleeingEntityId: String,
        val targetDirection: Direction,
        val fleeRoll: Int,
        val pursuitRoll: Int,
        override val escapeSkillUsed: Boolean,
        override val pursuitSkillsUsed: Map<String, Int>,
        val freeAttacks: List<AttackResult>,
        val interceptorId: String? // Entity that intercepted
    ) : FleeResult() {
        val isSuccess = false
    }

    /**
     * Flee failed due to error
     */
    data class Error(
        val reason: String
    ) : FleeResult() {
        override val fleeingEntityId: String = ""
        override val escapeSkillUsed: Boolean = false
        override val pursuitSkillsUsed: Map<String, Int> = emptyMap()
        val isSuccess = false
    }

    companion object {
        fun success(
            fleeingEntityId: String,
            targetDirection: Direction,
            fleeRoll: Int,
            pursuitRoll: Int,
            escapeSkillUsed: Boolean,
            pursuitSkillsUsed: Map<String, Int>,
            freeAttacks: List<AttackResult> = emptyList()
        ) = Success(
            fleeingEntityId, targetDirection, fleeRoll, pursuitRoll,
            escapeSkillUsed, pursuitSkillsUsed, freeAttacks
        )

        fun failure(
            fleeingEntityId: String,
            targetDirection: Direction,
            fleeRoll: Int,
            pursuitRoll: Int,
            escapeSkillUsed: Boolean,
            pursuitSkillsUsed: Map<String, Int>,
            freeAttacks: List<AttackResult>,
            interceptorId: String?
        ) = Failure(
            fleeingEntityId, targetDirection, fleeRoll, pursuitRoll,
            escapeSkillUsed, pursuitSkillsUsed, freeAttacks, interceptorId
        )

        fun error(reason: String) = Error(reason)
    }
}

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
