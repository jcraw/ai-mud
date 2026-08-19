package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatHandlerPures

/**
 * Miss branch for console combat attack (MUD-039).
 */
internal object CombatAttackMiss {

    fun apply(
        game: MudGame,
        npc: Entity.NPC,
        spaceId: String,
        attackResult: AttackResult.Miss
    ) {
        println("\n${CombatHandlerPures.missNarrative(npc.name, attackResult.wasDodged)}")
        CombatSkillProgressHandlers.processSkillProgression(game, attackResult)
        game.worldState = CombatHandlerPures.maybeCounterAttack(
            game.worldState, npc.id, spaceId, game.turnQueue
        )
    }
}
