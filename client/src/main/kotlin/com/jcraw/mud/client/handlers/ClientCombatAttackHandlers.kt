@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "SwallowedException"
)

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.reasoning.combat.AttackResult
import kotlinx.coroutines.runBlocking

/**
 * Attack orchestration for [ClientCombatHandlers] facade (MUD-034k pure-move).
 */
internal object ClientCombatAttackHandlers {

    fun handleAttack(game: EngineGameClient, target: String?) {
        val prep = ClientCombatAttackPrep.prepare(game, target) ?: return
        val attackResult = resolve(game, prep)
        dispatch(game, prep, attackResult)
    }

    private fun resolve(
        game: EngineGameClient,
        prep: ClientCombatAttackPrep.Prepared
    ): AttackResult? {
        if (game.attackResolver == null || game.skillManager == null) return null
        return try {
            runBlocking {
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
        } catch (e: Exception) {
            null
        }
    }

    private fun dispatch(
        game: EngineGameClient,
        prep: ClientCombatAttackPrep.Prepared,
        attackResult: AttackResult?
    ) {
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
}
