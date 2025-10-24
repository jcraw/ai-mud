package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.QuestAction
import kotlinx.coroutines.runBlocking

/**
 * Handles combat system in the GUI client.
 */
object ClientCombatHandlers {

    fun handleAttack(game: EngineGameClient, target: String?) {
        val room = game.worldState.getCurrentRoom() ?: return

        // If already in combat
        if (game.worldState.player.isInCombat()) {
            val result = game.combatResolver.executePlayerAttack(game.worldState)

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

            game.emitEvent(GameEvent.Combat(narrative, result.npcDamage))

            val combatState = result.newCombatState
            if (combatState != null) {
                // Sync player's actual health with combat state
                val updatedPlayer = game.worldState.player
                    .updateCombat(combatState)
                    .copy(health = combatState.playerHealth)
                game.worldState = game.worldState.updatePlayer(updatedPlayer)

                // Update status
                game.emitEvent(GameEvent.StatusUpdate(
                    hp = game.worldState.player.health,
                    maxHp = game.worldState.player.maxHealth
                ))

                game.describeCurrentRoom()
            } else {
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
                        game.emitEvent(GameEvent.Combat("\nVictory! The enemy has been defeated!"))
                        if (endedCombat != null) {
                            game.worldState = game.worldState.removeEntityFromRoom(room.id, endedCombat.combatantNpcId) ?: game.worldState

                            // Track NPC kill for quests
                            game.trackQuests(QuestAction.KilledNPC(endedCombat.combatantNpcId))
                        }
                    }
                    result.playerDied -> {
                        game.emitEvent(GameEvent.Combat("\nYou have been defeated! Game over."))
                        game.running = false
                    }
                    result.playerFled -> {
                        game.emitEvent(GameEvent.Narrative("\nYou have fled from combat."))
                    }
                }

                // Update status
                game.emitEvent(GameEvent.StatusUpdate(
                    hp = game.worldState.player.health,
                    maxHp = game.worldState.player.maxHealth
                ))
            }
            return
        }

        // Initiate combat
        if (target.isNullOrBlank()) {
            game.emitEvent(GameEvent.System("Attack whom?", GameEvent.MessageLevel.WARNING))
            return
        }

        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (npc == null) {
            game.emitEvent(GameEvent.System("You don't see anyone by that name to attack.", GameEvent.MessageLevel.WARNING))
            return
        }

        val result = game.combatResolver.initiateCombat(game.worldState, npc.id)
        if (result == null) {
            game.emitEvent(GameEvent.System("You cannot initiate combat with that target.", GameEvent.MessageLevel.ERROR))
            return
        }

        val narrative = if (game.combatNarrator != null) {
            runBlocking {
                game.combatNarrator.narrateCombatStart(game.worldState, npc)
            }
        } else {
            result.narrative
        }

        game.emitEvent(GameEvent.Combat(narrative))

        val combatState = result.newCombatState
        if (combatState != null) {
            // Sync player's actual health with combat state
            val updatedPlayer = game.worldState.player
                .updateCombat(combatState)
                .copy(health = combatState.playerHealth)
            game.worldState = game.worldState.updatePlayer(updatedPlayer)

            // Update status
            game.emitEvent(GameEvent.StatusUpdate(
                hp = game.worldState.player.health,
                maxHp = game.worldState.player.maxHealth
            ))

            game.describeCurrentRoom()
        }
    }
}
