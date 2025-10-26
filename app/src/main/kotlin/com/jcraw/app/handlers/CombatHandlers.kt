package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.combat.AttackResolver
import com.jcraw.mud.reasoning.combat.CombatBehavior
import com.jcraw.mud.reasoning.QuestAction
import kotlinx.coroutines.runBlocking

/**
 * Combat handlers - attack, damage resolution, victory/defeat
 *
 * Phase 4: Refactored to use emergent combat system
 * - No combat modes, combat emerges from hostile dispositions
 * - Uses AttackResolver for attack resolution
 * - Uses CombatBehavior for counter-attack triggers
 */
object CombatHandlers {

    /**
     * Handle player attack action
     *
     * @param game MudGame instance with world state and systems
     * @param target Target identifier (NPC name or ID)
     */
    fun handleAttack(game: MudGame, target: String?) {
        val room = game.worldState.getCurrentRoom() ?: return

        // Validate target
        if (target.isNullOrBlank()) {
            println("Attack whom?")
            return
        }

        // Find the target NPC
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (npc == null) {
            println("You don't see anyone by that name to attack.")
            return
        }

        // Resolve attack using AttackResolver (Phase 3)
        val attackResult = if (game.attackResolver != null && game.llmService != null) {
            try {
                runBlocking {
                    game.attackResolver.resolveAttack(
                        attackerId = game.worldState.player.id,
                        defenderId = npc.id,
                        action = "attack ${npc.name}",
                        worldState = game.worldState,
                        llmService = game.llmService
                    )
                }
            } catch (e: Exception) {
                // Fallback to simple attack if resolver fails
                println("Debug: AttackResolver failed: ${e.message}, using fallback")
                null
            }
        } else {
            null
        }

        if (attackResult != null) {
            // Apply damage to NPC if hit
            if (attackResult.hit && attackResult.damage > 0) {
                val npcCombat = npc.getComponent<CombatComponent>(ComponentType.COMBAT)
                if (npcCombat != null) {
                    val updatedNpcCombat = npcCombat.applyDamage(attackResult.damage, attackResult.damageType)
                    val updatedNpc = npc.withComponent(updatedNpcCombat) as Entity.NPC

                    // Update room with damaged NPC
                    val updatedRoom = room.removeEntity(npc.id).addEntity(updatedNpc)
                    game.worldState = game.worldState.updateRoom(updatedRoom)

                    // Check if NPC died
                    if (updatedNpcCombat.isDead()) {
                        println("\nVictory! ${npc.name} has been defeated!")
                        game.worldState = game.worldState.removeEntityFromRoom(room.id, npc.id) ?: game.worldState

                        // Track NPC kill for quests
                        game.trackQuests(QuestAction.KilledNPC(npc.id))
                        return
                    }
                }
            }

            // Display attack narrative
            println("\n${attackResult.narrative}")

            // Trigger counter-attack behavior (Phase 4)
            // This makes the NPC hostile and adds them to the turn queue
            if (game.turnQueue != null) {
                game.worldState = CombatBehavior.triggerCounterAttack(
                    npcId = npc.id,
                    roomId = room.id,
                    worldState = game.worldState,
                    turnQueue = game.turnQueue
                )
            }
        } else {
            // Fallback: Use old combat system for backward compatibility
            handleLegacyAttack(game, npc)
        }
    }

    /**
     * Legacy attack handler for backward compatibility
     * Uses the old CombatResolver system
     *
     * TODO: Remove this once all combat is migrated to V2
     */
    private fun handleLegacyAttack(game: MudGame, npc: Entity.NPC) {
        // Start combat using old system
        val result = game.combatResolver.initiateCombat(game.worldState, npc.id)
        if (result == null) {
            println("You cannot initiate combat with that target.")
            return
        }

        // Generate narrative for combat start
        val narrative = if (game.combatNarrator != null) {
            runBlocking {
                game.combatNarrator.narrateCombatStart(game.worldState, npc)
            }
        } else {
            result.narrative
        }

        println("\n$narrative")

        // Execute the attack
        val attackResult = game.combatResolver.executePlayerAttack(game.worldState)

        // Display combat narrative
        val combatNarrative = if (game.combatNarrator != null && !attackResult.playerDied && !attackResult.npcDied) {
            val room = game.worldState.getCurrentRoom()
            val combatNpc = room?.entities?.filterIsInstance<Entity.NPC>()?.find { it.id == npc.id }

            if (combatNpc != null) {
                runBlocking {
                    game.combatNarrator.narrateCombatRound(
                        game.worldState, combatNpc, attackResult.playerDamage, attackResult.npcDamage,
                        attackResult.npcDied, attackResult.playerDied
                    )
                }
            } else {
                attackResult.narrative
            }
        } else {
            attackResult.narrative
        }

        println("\n$combatNarrative")

        // Handle combat results
        when {
            attackResult.npcDied -> {
                println("\nVictory! The enemy has been defeated!")
                game.worldState = game.worldState.removeEntityFromRoom(game.worldState.getCurrentRoom()!!.id, npc.id) ?: game.worldState
                game.trackQuests(QuestAction.KilledNPC(npc.id))
            }
            attackResult.playerDied -> {
                // Use new permadeath system
                game.handlePlayerDeath()
            }
        }
    }
}
