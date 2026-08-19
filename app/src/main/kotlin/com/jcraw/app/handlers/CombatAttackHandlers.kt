package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatHandlerPures

/**
 * Attack orchestration for [CombatHandlers] facade (MUD-039).
 */
internal object CombatAttackHandlers {

    fun handleAttack(game: MudGame, target: String?) {
        val prep = CombatAttackPrep.prepare(game, target) ?: return
        val attackResult = CombatHandlerPures.resolvePlayerAttack(
            game.attackResolver,
            game.worldState.player.id,
            prep,
            game.worldState,
            game.skillManager
        )
        when (attackResult) {
            is AttackResult.Hit -> CombatAttackHit.apply(game, prep, attackResult)
            is AttackResult.Miss -> CombatAttackMiss.apply(game, prep.npc, prep.spaceId, attackResult)
            is AttackResult.Failure -> println("Attack failed: ${attackResult.reason}")
        }
    }
}
