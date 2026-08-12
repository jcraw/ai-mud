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
    "ForbiddenComment"
)

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Direction
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.FleeResolver
import com.jcraw.mud.reasoning.combat.FleeResult
import kotlinx.coroutines.runBlocking

/**
 * Flee fragment for [MovementMoveHandlers].
 */
internal object MovementFleeHandlers {

    fun handleFlee(game: MudGame, direction: Direction, hostiles: List<String>) = runBlocking {
        println("\n⚠️  Hostile creatures block your path! You attempt to flee...")
        val attackResolver = game.attackResolver
        if (attackResolver == null) {
            println("Flee system unavailable. Allowing movement.")
            MovementMoveHandlers.performMove(game, direction)
            return@runBlocking
        }
        val fleeResolver = FleeResolver(attackResolver)
        val result = fleeResolver.resolveFlee(
            fleeingEntityId = game.worldState.player.id,
            pursuers = hostiles,
            targetDirection = direction,
            worldState = game.worldState,
            skillManager = game.skillManager
        )
        processFleeSkillProgression(game, result)
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
                    game.worldState = game.worldState.updatePlayer(
                        game.worldState.player.copy(
                            health = game.worldState.player.health - attack.damage
                        )
                    )
                }
                is AttackResult.Miss -> {
                    val attacker = game.worldState.getEntity(attack.attackerId)
                    println("\nThe ${attacker?.name ?: "enemy"} swings at you but misses!")
                }
                else -> {}
            }
        }
    }

    private fun processFleeSkillProgression(game: MudGame, result: FleeResult) {
        val skillManager = game.skillManager
        if (result.escapeSkillUsed) {
            skillManager.attemptSkillProgress(
                entityId = result.fleeingEntityId,
                skillName = "Escape",
                baseXp = 10L,
                success = result is FleeResult.Success
            )
        }
        result.pursuitSkillsUsed.forEach { (pursuerId, pursuitLevel) ->
            if (pursuitLevel > 0) {
                skillManager.attemptSkillProgress(
                    entityId = pursuerId,
                    skillName = "Pursuit",
                    baseXp = 10L,
                    success = result is FleeResult.Failure
                )
            }
        }
    }
}
