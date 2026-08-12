@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SkillCheckResult
import com.jcraw.mud.core.SkillChallenge
import com.jcraw.mud.reasoning.QuestAction

/**
 * Skill-check-on-feature handler (app-only; client Check routes via social).
 * Pure extract from [SkillQuestHandlers].
 */
object SkillQuestCheckHandlers {

    fun handleCheck(game: MudGame, target: String) {
        val spaceId = game.worldState.player.currentRoomId
        val feature = findFeature(game, spaceId, target)
        if (feature == null) {
            println("You don't see that here.")
            return
        }
        val challenge = feature.skillChallenge
        if (!feature.isInteractable || challenge == null) {
            println("There's nothing to check about that.")
            return
        }
        if (feature.isCompleted) {
            println("You've already successfully interacted with that.")
            return
        }
        println("\n${challenge.description}")
        val result = game.skillCheckResolver.checkPlayer(
            game.worldState.player,
            challenge.statType,
            challenge.difficulty
        )
        printCheckRoll(challenge, result)
        applyCheckOutcome(game, spaceId, feature, challenge, result)
    }

    private fun printCheckRoll(challenge: SkillChallenge, result: SkillCheckResult) {
        println("\nRolling ${challenge.statType.name} check...")
        println("d20 roll: ${result.roll} + modifier: ${result.modifier} = ${result.total} vs DC ${result.dc}")
        if (result.isCriticalSuccess) {
            println("\n🎲 CRITICAL SUCCESS! (Natural 20)")
        } else if (result.isCriticalFailure) {
            println("\n💀 CRITICAL FAILURE! (Natural 1)")
        }
    }

    private fun applyCheckOutcome(
        game: MudGame,
        spaceId: String,
        feature: Entity.Feature,
        challenge: SkillChallenge,
        result: SkillCheckResult
    ) {
        if (result.success) {
            println("\n✅ Success!")
            println(challenge.successDescription)
            val updatedFeature = feature.copy(isCompleted = true)
            game.worldState = game.worldState.replaceEntityInSpace(spaceId, feature.id, updatedFeature)
                ?: game.worldState
            game.trackQuests(QuestAction.UsedSkill(feature.id))
        } else {
            println("\n❌ Failure!")
            println(challenge.failureDescription)
        }
    }

    private fun findFeature(game: MudGame, spaceId: String, target: String): Entity.Feature? {
        val normalizedTarget = target.lowercase().replace("_", " ")
        return game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.Feature>()
            .find { matchesFeature(it, normalizedTarget) }
    }

    private fun matchesFeature(entity: Entity.Feature, normalizedTarget: String): Boolean {
        val normalizedName = entity.name.lowercase()
        val normalizedId = entity.id.lowercase().replace("_", " ")
        return normalizedName.contains(normalizedTarget) ||
            normalizedId.contains(normalizedTarget) ||
            normalizedTarget.contains(normalizedName) ||
            normalizedTarget.contains(normalizedId) ||
            normalizedTarget.split(" ").all { word ->
                normalizedName.contains(word) || normalizedId.contains(word)
            }
    }
}
