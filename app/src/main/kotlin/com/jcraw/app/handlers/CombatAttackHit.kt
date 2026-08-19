package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatHandlerPures
import com.jcraw.mud.reasoning.combat.CombatHitApply
import com.jcraw.mud.reasoning.combat.DeathHandler

/**
 * Hit branch for console combat attack (MUD-039).
 */
internal object CombatAttackHit {

    fun apply(game: MudGame, prep: CombatHandlerPures.Prepared, attackResult: AttackResult.Hit) {
        val applied = CombatHitApply.apply(game.worldState, prep.spaceId, prep.npc, attackResult)
        if (applied is CombatHitApply.Result.Success) {
            game.worldState = applied.world
        }
        printHit(game, prep, attackResult)
        CombatSkillProgressHandlers.processSkillProgression(game, attackResult)
        if (attackResult.wasKilled) {
            handleDeath(game, prep.spaceId, prep.npc)
            return
        }
        game.worldState = CombatHandlerPures.maybeCounterAttack(
            game.worldState, prep.npc.id, prep.spaceId, game.turnQueue
        )
    }

    private fun printHit(game: MudGame, prep: CombatHandlerPures.Prepared, hit: AttackResult.Hit) {
        println(
            "\n${CombatHandlerPures.narrateHit(
                game.combatNarrator,
                game.worldState.player.equippedWeapon?.name,
                prep.playerInventory,
                prep.templates,
                prep.npc,
                hit
            )}"
        )
        println(
            CombatHandlerPures.healthDescriptor(
                hit.updatedDefenderCombat.currentHp,
                hit.updatedDefenderCombat.maxHp,
                prep.npc.name
            )
        )
    }

    private fun handleDeath(game: MudGame, spaceId: String, npc: Entity.NPC) {
        println("\nVictory! ${npc.name} has been defeated!")
        val deathResult = game.deathHandler.handleDeath(npc.id, game.worldState)
        game.worldState = when (deathResult) {
            is DeathHandler.DeathResult.NPCDeath -> deathResult.updatedWorld
            else -> game.worldState.removeEntityFromSpace(spaceId, npc.id) ?: game.worldState
        }
        game.respawnChecker?.markDeath(npc.id, game.worldState.gameTime)
        game.trackQuests(QuestAction.KilledNPC(npc.id))
    }
}
