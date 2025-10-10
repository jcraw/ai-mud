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
    fun initiateCombat(worldState: WorldState, targetNpcId: String): CombatResult? {
        val room = worldState.getCurrentRoom() ?: return null
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { it.id == targetNpcId }
            ?: return null

        val newWorldState = worldState.startCombat(npc.id, npc.health)

        return CombatResult(
            narrative = "You engage ${npc.name} in combat!",
            newCombatState = newWorldState.activeCombat,
            playerDied = false,
            npcDied = false,
            playerFled = false
        )
    }

    /**
     * Executes a player attack action during combat.
     */
    fun executePlayerAttack(worldState: WorldState): CombatResult {
        val combat = worldState.activeCombat
            ?: return CombatResult(
                narrative = "You are not in combat.",
                newCombatState = null,
                playerDied = false,
                npcDied = false
            )

        if (!combat.isPlayerTurn) {
            return CombatResult(
                narrative = "It's not your turn!",
                newCombatState = combat,
                playerDied = false,
                npcDied = false
            )
        }

        // Calculate damage (simple random for now)
        val damage = calculatePlayerDamage(worldState.player)
        val updatedCombat = combat.applyPlayerDamage(damage)

        // Check if NPC died
        if (updatedCombat.npcHealth <= 0) {
            return CombatResult(
                narrative = "You strike for $damage damage!",
                newCombatState = null,  // Combat ends
                playerDied = false,
                npcDied = true,
                playerDamage = damage,
                npcDamage = 0
            )
        }

        // NPC's turn to attack
        val npcDamage = calculateNpcDamage(worldState, combat.combatantNpcId)
        val afterNpcAttack = updatedCombat.applyNpcDamage(npcDamage).nextTurn()

        // Check if player died
        if (afterNpcAttack.playerHealth <= 0) {
            return CombatResult(
                narrative = "You strike for $damage damage! The enemy retaliates for $npcDamage damage!",
                newCombatState = null,  // Combat ends
                playerDied = true,
                npcDied = false,
                playerDamage = damage,
                npcDamage = npcDamage
            )
        }

        return CombatResult(
            narrative = "You strike for $damage damage! The enemy retaliates for $npcDamage damage!",
            newCombatState = afterNpcAttack,
            playerDied = false,
            npcDied = false,
            playerDamage = damage,
            npcDamage = npcDamage
        )
    }

    /**
     * Attempts to flee from combat.
     */
    fun attemptFlee(worldState: WorldState): CombatResult {
        val combat = worldState.activeCombat
            ?: return CombatResult(
                narrative = "You are not in combat.",
                newCombatState = null,
                playerDied = false,
                npcDied = false
            )

        // Simple flee chance - 50%
        val fleeSuccessful = Random.nextBoolean()

        if (fleeSuccessful) {
            return CombatResult(
                narrative = "You successfully flee from combat!",
                newCombatState = null,  // Combat ends
                playerDied = false,
                npcDied = false,
                playerFled = true
            )
        } else {
            // Failed flee, NPC gets free attack
            val npcDamage = calculateNpcDamage(worldState, combat.combatantNpcId)
            val afterNpcAttack = combat.applyNpcDamage(npcDamage).nextTurn()

            if (afterNpcAttack.playerHealth <= 0) {
                return CombatResult(
                    narrative = "You fail to escape! The enemy strikes you for $npcDamage damage!",
                    newCombatState = null,
                    playerDied = true,
                    npcDied = false
                )
            }

            return CombatResult(
                narrative = "You fail to escape! The enemy strikes you for $npcDamage damage!",
                newCombatState = afterNpcAttack,
                playerDied = false,
                npcDied = false
            )
        }
    }

    /**
     * Calculate damage dealt by player attack.
     * Base damage + weapon bonus.
     */
    private fun calculatePlayerDamage(player: PlayerState): Int {
        val baseDamage = Random.nextInt(5, 16)
        val weaponBonus = player.getWeaponDamageBonus()
        return baseDamage + weaponBonus
    }

    /**
     * Calculate damage dealt by NPC attack.
     */
    private fun calculateNpcDamage(worldState: WorldState, npcId: String): Int {
        val room = worldState.getCurrentRoom() ?: return 0
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { it.id == npcId }
            ?: return 0

        // Base damage 3-12, could vary by NPC type
        return Random.nextInt(3, 13)
    }
}
