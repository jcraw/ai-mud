@file:Suppress("TooGenericExceptionCaught", "SwallowedException", "ReturnCount")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatHandlerPures

/**
 * Attack orchestration for [ClientCombatHandlers] facade (MUD-039).
 */
internal object ClientCombatAttackHandlers {

    fun handleAttack(game: EngineGameClient, target: String?) {
        val prep = ClientCombatAttackPrep.prepare(game, target) ?: return
        val attackResult = resolve(game, prep)
        when (attackResult) {
            is AttackResult.Hit -> ClientCombatAttackHit.apply(game, prep, attackResult)
            is AttackResult.Miss -> ClientCombatAttackMiss.apply(
                game, prep.npc, prep.spaceId, attackResult
            )
            is AttackResult.Failure -> game.emitEvent(
                GameEvent.System(
                    "Attack failed: ${attackResult.reason}",
                    GameEvent.MessageLevel.WARNING
                )
            )
            null -> game.emitEvent(
                GameEvent.System(
                    "Combat system not available (requires API key)",
                    GameEvent.MessageLevel.WARNING
                )
            )
        }
    }

    private fun resolve(
        game: EngineGameClient,
        prep: CombatHandlerPures.Prepared
    ): AttackResult? {
        val resolver = game.attackResolver ?: return null
        val skillManager = game.skillManager ?: return null
        return try {
            CombatHandlerPures.resolvePlayerAttack(
                resolver,
                game.worldState.player.id,
                prep,
                game.worldState,
                skillManager
            )
        } catch (e: Exception) {
            null
        }
    }
}
