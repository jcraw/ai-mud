package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.combat.AttackResolver
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatBehavior
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.town.SafeZoneValidator
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

        // Check if current space is a safe zone (blocks combat)
        val currentSpaceId = game.worldState.player.currentRoomId
        val currentSpace = game.spacePropertiesRepository.findByChunkId(currentSpaceId).getOrNull()

        if (currentSpace != null && SafeZoneValidator.isSafeZone(currentSpace)) {
            println(SafeZoneValidator.getCombatBlockedMessage(target ?: "unknown"))
            return
        }

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
        val attackResult = if (game.attackResolver != null) {
            try {
                runBlocking {
                    game.attackResolver.resolveAttack(
                        attackerId = game.worldState.player.id,
                        defenderId = npc.id,
                        action = "attack ${npc.name}",
                        worldState = game.worldState
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

        when (attackResult) {
            is AttackResult.Hit -> {
                // Apply damage from AttackResolver - damage already applied to component
                val updatedNpc = npc.withComponent(attackResult.updatedDefenderCombat) as Entity.NPC

                // Update room with damaged NPC
                val updatedRoom = room.removeEntity(npc.id).addEntity(updatedNpc)
                game.worldState = game.worldState.updateRoom(updatedRoom)

                // Generate and display attack narrative
                val weapon = game.worldState.player.equippedWeapon?.name ?: "bare fists"
                val narrative = if (game.combatNarrator != null) {
                    runBlocking {
                        game.combatNarrator.narrateAction(
                            weapon = weapon,
                            damage = attackResult.damage,
                            maxHp = npc.maxHealth,
                            isHit = true,
                            isCritical = false,
                            isDeath = attackResult.wasKilled,
                            isSpell = false,
                            targetName = npc.name
                        )
                    }
                } else {
                    "You hit ${npc.name} for ${attackResult.damage} damage!"
                }
                println("\n$narrative")

                // Check if NPC died
                if (attackResult.wasKilled) {
                    println("\nVictory! ${npc.name} has been defeated!")
                    game.worldState = game.worldState.removeEntityFromRoom(room.id, npc.id) ?: game.worldState

                    // Mark entity death for respawn system
                    game.respawnChecker?.markDeath(npc.id, game.worldState.gameTime)

                    // Track NPC kill for quests
                    game.trackQuests(QuestAction.KilledNPC(npc.id))
                    return
                }

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
            }
            is AttackResult.Miss -> {
                // Generate and display miss narrative
                val narrative = if (attackResult.wasDodged) {
                    "${npc.name} dodges your attack!"
                } else {
                    "You miss ${npc.name}!"
                }
                println("\n$narrative")

                // Trigger counter-attack even on miss
                if (game.turnQueue != null) {
                    game.worldState = CombatBehavior.triggerCounterAttack(
                        npcId = npc.id,
                        roomId = room.id,
                        worldState = game.worldState,
                        turnQueue = game.turnQueue
                    )
                }
            }
            is AttackResult.Failure -> {
                println("Attack failed: ${attackResult.reason}")
            }
            null -> {
                // Fallback: Use old combat system for backward compatibility
                handleLegacyAttack(game, npc)
            }
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

                // Mark entity death for respawn system
                game.respawnChecker?.markDeath(npc.id, game.worldState.gameTime)

                game.trackQuests(QuestAction.KilledNPC(npc.id))
            }
            attackResult.playerDied -> {
                // Use new permadeath system
                game.handlePlayerDeath()
            }
        }
    }
}
