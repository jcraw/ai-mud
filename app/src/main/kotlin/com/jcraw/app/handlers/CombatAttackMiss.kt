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

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatBehavior

/**
 * Miss branch for console combat attack (MUD-034k pure-move).
 */
internal object CombatAttackMiss {

    fun apply(
        game: MudGame,
        npc: Entity.NPC,
        spaceId: String,
        attackResult: AttackResult.Miss
    ) {
        val narrative = if (attackResult.wasDodged) {
            "${npc.name} dodges your attack!"
        } else {
            "You miss ${npc.name}!"
        }
        println("\n$narrative")

        CombatSkillProgressHandlers.processSkillProgression(game, attackResult)

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
