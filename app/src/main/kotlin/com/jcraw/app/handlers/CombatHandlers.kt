package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity
import com.jcraw.mud.reasoning.QuestAction
import kotlinx.coroutines.runBlocking

/**
 * Combat handlers - attack, damage resolution, victory/defeat
 */
object CombatHandlers {

    fun handleAttack(game: MudGame, target: String?) {
        val room = game.worldState.getCurrentRoom() ?: return

        // If already in combat
        if (game.worldState.player.isInCombat()) {
            val result = game.combatResolver.executePlayerAttack(game.worldState)

            // Generate narrative
            val narrative = if (game.combatNarrator != null && !result.playerDied && !result.npcDied) {
                val combat = game.worldState.player.activeCombat!!
                val npc = room.entities.filterIsInstance<Entity.NPC>()
                    .find { it.id == combat.combatantNpcId }

                if (npc != null) {
                    runBlocking {
                        game.combatNarrator.narrateCombatRound(
                            game.worldState, npc, result.playerDamage, result.npcDamage,
                            result.npcDied, result.playerDied
                        )
                    }
                } else {
                    result.narrative
                }
            } else {
                result.narrative
            }

            println("\n$narrative")

            // Update world state
            val combatState = result.newCombatState
            if (combatState != null) {
                // Sync player's actual health with combat state
                val updatedPlayer = game.worldState.player
                    .updateCombat(combatState)
                    .copy(health = combatState.playerHealth)
                game.worldState = game.worldState.updatePlayer(updatedPlayer)
                game.describeCurrentRoom()  // Show updated combat status
            } else {
                // Combat ended - save combat info before ending
                val endedCombat = game.worldState.player.activeCombat
                // Sync final health before ending combat
                val playerWithHealth = if (endedCombat != null) {
                    game.worldState.player.copy(health = endedCombat.playerHealth)
                } else {
                    game.worldState.player
                }
                game.worldState = game.worldState.updatePlayer(playerWithHealth.endCombat())

                when {
                    result.npcDied -> {
                        println("\nVictory! The enemy has been defeated!")
                        // Remove NPC from room
                        if (endedCombat != null) {
                            game.worldState = game.worldState.removeEntityFromRoom(room.id, endedCombat.combatantNpcId) ?: game.worldState

                            // Track NPC kill for quests
                            game.trackQuests(QuestAction.KilledNPC(endedCombat.combatantNpcId))
                        }
                    }
                    result.playerDied -> {
                        println("\nYou have been defeated! Game over.")
                        println("\nPress any key to play again...")
                        readLine()  // Wait for any input

                        // Restart the game
                        game.worldState = game.initialWorldState
                        println("\n" + "=" * 60)
                        println("  Restarting Adventure...")
                        println("=" * 60)
                        game.printWelcome()
                        game.describeCurrentRoom()
                    }
                    result.playerFled -> {
                        println("\nYou have fled from combat.")
                    }
                }
            }
            return
        }

        // Initiate combat with target
        if (target.isNullOrBlank()) {
            println("Attack whom?")
            return
        }

        // Find the NPC
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (npc == null) {
            println("You don't see anyone by that name to attack.")
            return
        }

        // Start combat
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

        if (result.newCombatState != null) {
            game.worldState = game.worldState.updatePlayer(game.worldState.player.updateCombat(result.newCombatState))
            game.describeCurrentRoom()  // Show combat status
        }
    }
}

// String repetition helper
private operator fun String.times(n: Int): String = repeat(n)
