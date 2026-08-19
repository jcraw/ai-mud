@file:Suppress("ForbiddenComment")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatHandlerPures
import com.jcraw.mud.reasoning.combat.FleeResult
import kotlinx.coroutines.runBlocking

/**
 * Flee fragment for [ClientMovementMoveHandlers].
 */
internal object ClientMovementFleeHandlers {

    fun handleFlee(game: EngineGameClient, direction: Direction, hostiles: List<String>) = runBlocking {
        game.emitEvent(GameEvent.Combat("⚠️  Hostile creatures block your path! You attempt to flee..."))
        val result = CombatHandlerPures.attemptFlee(
            game.attackResolver,
            game.worldState.player.id,
            hostiles,
            direction,
            game.worldState,
            game.skillManager
        )
        if (result == null) {
            game.emitEvent(
                GameEvent.System("Flee system unavailable. Allowing movement.", GameEvent.MessageLevel.WARNING)
            )
            ClientMovementMoveHandlers.performMove(game, direction)
            return@runBlocking
        }
        applyFleeResult(game, direction, result)
    }

    private fun applyFleeResult(game: EngineGameClient, direction: Direction, result: FleeResult) {
        when (result) {
            is FleeResult.Success -> {
                game.emitEvent(GameEvent.Combat("✅ Flee SUCCESS! You escape to safety."))
                ClientMovementMoveHandlers.performMove(game, direction)
            }
            is FleeResult.Failure -> onFleeFailure(game, result)
            is FleeResult.Error -> onFleeError(game, direction, result)
        }
    }

    private fun onFleeFailure(game: EngineGameClient, result: FleeResult.Failure) {
        game.emitEvent(GameEvent.Combat("❌ Flee FAILED! You are intercepted!"))
        narrateFreeAttacks(game, result)
        if (game.worldState.player.health <= 0) {
            game.emitEvent(GameEvent.Combat("💀 You have been slain!"))
            // TODO: Trigger player death/respawn logic
        }
    }

    private fun onFleeError(game: EngineGameClient, direction: Direction, result: FleeResult.Error) {
        game.emitEvent(
            GameEvent.System("Flee attempt failed: ${result.reason}", GameEvent.MessageLevel.ERROR)
        )
        game.emitEvent(GameEvent.System("Allowing movement as fallback.", GameEvent.MessageLevel.INFO))
        ClientMovementMoveHandlers.performMove(game, direction)
    }

    private fun narrateFreeAttacks(game: EngineGameClient, result: FleeResult.Failure) {
        result.freeAttacks.forEach { attack ->
            when (attack) {
                is AttackResult.Hit -> narrateHit(game, attack)
                is AttackResult.Miss -> narrateMiss(game, attack)
                else -> {}
            }
        }
    }

    private fun narrateHit(game: EngineGameClient, attack: AttackResult.Hit) {
        val attacker = game.worldState.getEntity(attack.attackerId)
        game.emitEvent(
            GameEvent.Combat(
                "The ${attacker?.name ?: "enemy"} strikes you for ${attack.damage} damage!",
                attack.damage
            )
        )
        game.worldState = CombatHandlerPures.applyFreeHitDamage(game.worldState, attack.damage)
    }

    private fun narrateMiss(game: EngineGameClient, attack: AttackResult.Miss) {
        val attacker = game.worldState.getEntity(attack.attackerId)
        game.emitEvent(
            GameEvent.Combat("The ${attacker?.name ?: "enemy"} swings at you but misses!")
        )
    }
}
