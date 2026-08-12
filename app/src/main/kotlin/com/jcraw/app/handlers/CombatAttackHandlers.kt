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
import com.jcraw.mud.reasoning.combat.AttackResult
import kotlinx.coroutines.runBlocking

/**
 * Attack orchestration for [CombatHandlers] facade (MUD-034k pure-move).
 */
internal object CombatAttackHandlers {

    fun handleAttack(game: MudGame, target: String?) {
        val prep = CombatAttackPrep.prepare(game, target) ?: return

        val attackResult = runBlocking {
            game.attackResolver.resolveAttack(
                attackerId = game.worldState.player.id,
                defenderId = prep.npc.id,
                action = "attack ${prep.npc.name} with ${prep.weaponName}",
                worldState = game.worldState,
                skillManager = game.skillManager,
                attackerEquipped = prep.attackerEquipped,
                defenderEquipped = prep.defenderEquipped,
                templates = prep.templates
            )
        }

        when (attackResult) {
            is AttackResult.Hit -> CombatAttackHit.apply(game, prep, attackResult)
            is AttackResult.Miss -> CombatAttackMiss.apply(game, prep.npc, prep.spaceId, attackResult)
            is AttackResult.Failure -> println("Attack failed: ${attackResult.reason}")
        }
    }
}
