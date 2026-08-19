package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatHandlerPures
import com.jcraw.mud.reasoning.combat.CombatHitApply
import com.jcraw.mud.reasoning.combat.DeathHandler

/**
 * Hit branch for client combat attack (MUD-039).
 */
internal object ClientCombatAttackHit {

    fun apply(
        game: EngineGameClient,
        prep: CombatHandlerPures.Prepared,
        attackResult: AttackResult.Hit
    ) {
        val applied = CombatHitApply.apply(game.worldState, prep.spaceId, prep.npc, attackResult)
        if (applied is CombatHitApply.Result.Success) {
            game.worldState = applied.world
        }
        emitHit(game, prep, attackResult)
        ClientCombatSkillProgressHandlers.processSkillProgression(game, attackResult)
        if (attackResult.wasKilled) {
            handleDeath(game, prep.spaceId, prep.npc)
            return
        }
        game.worldState = CombatHandlerPures.maybeCounterAttack(
            game.worldState, prep.npc.id, prep.spaceId, game.turnQueue
        )
    }

    private fun emitHit(
        game: EngineGameClient,
        prep: CombatHandlerPures.Prepared,
        hit: AttackResult.Hit
    ) {
        game.emitEvent(
            GameEvent.Combat(
                CombatHandlerPures.narrateHit(
                    game.combatNarrator,
                    game.worldState.player.equippedWeapon?.name,
                    prep.playerInventory,
                    prep.templates,
                    prep.npc,
                    hit
                )
            )
        )
        game.emitEvent(
            GameEvent.Combat(
                CombatHandlerPures.healthDescriptor(
                    hit.updatedDefenderCombat.currentHp,
                    hit.updatedDefenderCombat.maxHp,
                    prep.npc.name
                )
            )
        )
    }

    private fun handleDeath(game: EngineGameClient, spaceId: String, npc: Entity.NPC) {
        game.emitEvent(GameEvent.Combat("\nVictory! ${npc.name} has been defeated!"))
        val deathResult = game.deathHandler.handleDeath(npc.id, game.worldState)
        game.worldState = when (deathResult) {
            is DeathHandler.DeathResult.NPCDeath -> deathResult.updatedWorld
            else -> game.worldState.removeEntityFromSpace(spaceId, npc.id) ?: game.worldState
        }
        game.trackQuests(QuestAction.KilledNPC(npc.id))
    }
}
