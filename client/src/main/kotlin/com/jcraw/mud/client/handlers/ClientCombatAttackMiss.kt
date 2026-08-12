@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList"
)

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatBehavior

/**
 * Miss branch for client combat attack (MUD-034k pure-move).
 */
internal object ClientCombatAttackMiss {

    fun apply(
        game: EngineGameClient,
        npc: Entity.NPC,
        spaceId: String,
        attackResult: AttackResult.Miss
    ) {
        val narrative = if (attackResult.wasDodged) {
            "${npc.name} dodges your attack!"
        } else {
            "You miss ${npc.name}!"
        }
        game.emitEvent(GameEvent.Combat(narrative))

        ClientCombatSkillProgressHandlers.processSkillProgression(game, attackResult)

        if (game.turnQueue != null) {
            game.worldState = CombatBehavior.triggerCounterAttack(
                npcId = npc.id,
                spaceId = spaceId,
                worldState = game.worldState,
                turnQueue = game.turnQueue
            )
        }
    }
}
