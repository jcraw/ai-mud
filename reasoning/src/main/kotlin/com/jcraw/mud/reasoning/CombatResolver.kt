package com.jcraw.mud.reasoning

import com.jcraw.mud.core.*
import kotlin.random.Random

/**
 * Resolves combat mechanics including damage calculation, turn management,
 * and combat state transitions.
 */
class CombatResolver {

    /**
     * Initiates combat with an NPC.
     * Returns null if combat cannot be started (NPC not found, etc.)
     */
    fun initiateCombat(worldState: WorldState, player: PlayerState, targetNpcId: String): CombatResult? {
        val space = worldState.getCurrentSpace(player.id) ?: return null
        val npc = worldState.getEntitiesInSpace(player.currentRoomId).filterIsInstance<Entity.NPC>()
            .find { it.id == targetNpcId }
            ?: return null

        val newCombatState = CombatState(
            combatantNpcId = npc.id,
            playerHealth = player.health,
            npcHealth = npc.health,
            isPlayerTurn = true,
            turnCount = 0
        )

        return CombatResult(
            narrative = "You engage ${npc.name} in combat!",
            newCombatState = newCombatState,
            playerDied = false,
            npcDied = false,
            playerFled = false
        )
    }

    // Backward compatibility overload
    fun initiateCombat(worldState: WorldState, targetNpcId: String): CombatResult? {
        return initiateCombat(worldState, worldState.player, targetNpcId)
    }

    /**
     * Executes a player attack action during combat.
     *
     * DEPRECATED: This is legacy V1 code. Use AttackResolver for V2 combat system.
     * Kept for backward compatibility only.
     */
    @Deprecated("Use AttackResolver from Combat V2 system", ReplaceWith("AttackResolver.resolveAttack()"))
    fun executePlayerAttack(worldState: WorldState, player: PlayerState): CombatResult {
        // V2 uses emergent combat, no modal combat state
        return CombatResult(
            narrative = "Combat system V1 is deprecated. Use the new AttackResolver system.",
            newCombatState = null,
            playerDied = false,
            npcDied = false
        )
    }

    // Backward compatibility overload
    fun executePlayerAttack(worldState: WorldState): CombatResult {
        return executePlayerAttack(worldState, worldState.player)
    }

    /**
     * Attempts to flee from combat.
     *
     * DEPRECATED: This is legacy V1 code. Use CombatBehavior for V2 combat system.
     * Kept for backward compatibility only.
     */
    @Deprecated("Use CombatBehavior from Combat V2 system", ReplaceWith("CombatBehavior"))
    fun attemptFlee(worldState: WorldState, player: PlayerState): CombatResult {
        // V2 uses emergent combat with disposition-based fleeing
        return CombatResult(
            narrative = "Combat system V1 is deprecated. Movement now handles fleeing from hostile NPCs automatically.",
            newCombatState = null,
            playerDied = false,
            npcDied = false,
            playerFled = true
        )
    }

    // Backward compatibility overload
    fun attemptFlee(worldState: WorldState): CombatResult {
        return attemptFlee(worldState, worldState.player)
    }

    /**
     * Calculate damage dealt by player attack.
     * Base damage + STR modifier. V2 combat system should use AttackResolver.
     */
    private fun calculatePlayerDamage(player: PlayerState): Int {
        val baseDamage = Random.nextInt(5, 16)
        // V1 weapon bonus removed - use V2 AttackResolver for proper equipment bonuses
        val strModifier = player.stats.strModifier()
        return (baseDamage + strModifier).coerceAtLeast(1)
    }

    /**
     * Calculate damage dealt by NPC attack.
     * Base damage + STR modifier. V2 combat system should use AttackResolver.
     */
    private fun calculateNpcDamage(worldState: WorldState, player: PlayerState, npcId: String): Int {
        val space = worldState.getCurrentSpace(player.id) ?: return 0
        val npc = worldState.getEntitiesInSpace(player.currentRoomId).filterIsInstance<Entity.NPC>()
            .find { it.id == npcId }
            ?: return 0

        // Base damage 3-12 + STR modifier (V1 armor defense removed - use V2 AttackResolver)
        val baseDamage = Random.nextInt(3, 13)
        val strModifier = npc.stats.strModifier()
        return (baseDamage + strModifier).coerceAtLeast(1)
    }
}
