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
import com.jcraw.mud.core.SkillChallenge
import com.jcraw.mud.core.SkillCheckResult

/**
 * Persuade / intimidate CHA checks for [SocialHandlers] facade (MUD-034l pure-move).
 */
internal object SocialDispositionHandlers {

    fun handlePersuade(game: MudGame, target: String) {
        val spaceId = game.worldState.player.currentRoomId
        val npc = SocialNpcResolve.findNpcByName(game, target)
        if (npc == null) {
            println("There's no one here by that name.")
            return
        }
        val challenge = npc.persuasionChallenge
        if (challenge == null) {
            println("${npc.name} doesn't seem interested in negotiating.")
            return
        }
        if (npc.hasBeenPersuaded) {
            println("You've already persuaded ${npc.name}.")
            return
        }
        runPersuade(game, spaceId, npc, challenge)
    }

    fun handleIntimidate(game: MudGame, target: String) {
        val spaceId = game.worldState.player.currentRoomId
        val npc = SocialNpcResolve.findNpcByName(game, target)
        if (npc == null) {
            println("There's no one here by that name.")
            return
        }
        val challenge = npc.intimidationChallenge
        if (challenge == null) {
            println("${npc.name} doesn't seem easily intimidated.")
            return
        }
        if (npc.hasBeenIntimidated) {
            println("${npc.name} is already frightened of you.")
            return
        }
        runIntimidate(game, spaceId, npc, challenge)
    }

    private fun runPersuade(
        game: MudGame,
        spaceId: String,
        npc: Entity.NPC,
        challenge: SkillChallenge
    ) {
        println("\n${challenge.description}")
        val result = rollPlayerCheck(game, challenge)
        displayCheckRoll(challenge, result)
        if (result.success) {
            println("\n✅ Success!")
            println(challenge.successDescription)
            val updatedNpc = npc.copy(hasBeenPersuaded = true)
            game.worldState = game.worldState.replaceEntityInSpace(spaceId, npc.id, updatedNpc) ?: game.worldState
        } else {
            println("\n❌ Failure!")
            println(challenge.failureDescription)
        }
    }

    private fun runIntimidate(
        game: MudGame,
        spaceId: String,
        npc: Entity.NPC,
        challenge: SkillChallenge
    ) {
        println("\n${challenge.description}")
        val result = rollPlayerCheck(game, challenge)
        displayCheckRoll(challenge, result)
        if (result.success) {
            println("\n✅ Success!")
            println(challenge.successDescription)
            val updatedNpc = npc.copy(hasBeenIntimidated = true)
            game.worldState = game.worldState.replaceEntityInSpace(spaceId, npc.id, updatedNpc) ?: game.worldState
        } else {
            println("\n❌ Failure!")
            println(challenge.failureDescription)
        }
    }

    private fun rollPlayerCheck(game: MudGame, challenge: SkillChallenge): SkillCheckResult {
        return game.skillCheckResolver.checkPlayer(
            game.worldState.player,
            challenge.statType,
            challenge.difficulty
        )
    }

    private fun displayCheckRoll(challenge: SkillChallenge, result: SkillCheckResult) {
        println("\nRolling ${challenge.statType.name} check...")
        println("d20 roll: ${result.roll} + modifier: ${result.modifier} = ${result.total} vs DC ${result.dc}")
        if (result.isCriticalSuccess) {
            println("\n🎲 CRITICAL SUCCESS! (Natural 20)")
        } else if (result.isCriticalFailure) {
            println("\n💀 CRITICAL FAILURE! (Natural 1)")
        }
    }
}
