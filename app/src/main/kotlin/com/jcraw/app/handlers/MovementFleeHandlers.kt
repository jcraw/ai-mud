@file:Suppress("ForbiddenComment")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Direction
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatHandlerPures
import com.jcraw.mud.reasoning.combat.FleeResult
import kotlinx.coroutines.runBlocking

/**
 * Flee fragment for [MovementMoveHandlers].
 */
internal object MovementFleeHandlers {

    fun handleFlee(game: MudGame, direction: Direction, hostiles: List<String>) = runBlocking {
        println("\n⚠️  Hostile creatures block your path! You attempt to flee...")
        val result = CombatHandlerPures.attemptFlee(
            game.attackResolver,
            game.worldState.player.id,
            hostiles,
            direction,
            game.worldState,
            game.skillManager
        )
        if (result == null) {
            println("Flee system unavailable. Allowing movement.")
            MovementMoveHandlers.performMove(game, direction)
            return@runBlocking
        }
        applyFleeResult(game, direction, result)
    }

    private fun applyFleeResult(game: MudGame, direction: Direction, result: FleeResult) {
        when (result) {
            is FleeResult.Success -> {
                println("\n✅ Flee SUCCESS! You escape to safety.")
                MovementMoveHandlers.performMove(game, direction)
            }
            is FleeResult.Failure -> {
                println("\n❌ Flee FAILED! You are intercepted!")
                narrateFreeAttacks(game, result)
                if (game.worldState.player.health <= 0) {
                    println("\n💀 You have been slain!")
                    // TODO: Trigger player death/respawn logic
                }
            }
            is FleeResult.Error -> {
                println("\nFlee attempt failed: ${result.reason}")
                println("Allowing movement as fallback.")
                MovementMoveHandlers.performMove(game, direction)
            }
        }
    }

    private fun narrateFreeAttacks(game: MudGame, result: FleeResult.Failure) {
        result.freeAttacks.forEach { attack ->
            when (attack) {
                is AttackResult.Hit -> {
                    val attacker = game.worldState.getEntity(attack.attackerId)
                    println("\nThe ${attacker?.name ?: "enemy"} strikes you for ${attack.damage} damage!")
                    game.worldState = CombatHandlerPures.applyFreeHitDamage(game.worldState, attack.damage)
                }
                is AttackResult.Miss -> {
                    val attacker = game.worldState.getEntity(attack.attackerId)
                    println("\nThe ${attacker?.name ?: "enemy"} swings at you but misses!")
                }
                else -> {}
            }
        }
    }
}
