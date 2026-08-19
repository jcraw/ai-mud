package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatHandlerPures

/**
 * Miss branch for client combat attack (MUD-039).
 */
internal object ClientCombatAttackMiss {

    fun apply(
        game: EngineGameClient,
        npc: Entity.NPC,
        spaceId: String,
        attackResult: AttackResult.Miss
    ) {
        game.emitEvent(
            GameEvent.Combat(CombatHandlerPures.missNarrative(npc.name, attackResult.wasDodged))
        )
        ClientCombatSkillProgressHandlers.processSkillProgression(game, attackResult)
        game.worldState = CombatHandlerPures.maybeCounterAttack(
            game.worldState, npc.id, spaceId, game.turnQueue
        )
    }
}
