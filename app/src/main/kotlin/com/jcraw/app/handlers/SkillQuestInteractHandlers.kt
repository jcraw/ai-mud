@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity

/**
 * Interact orchestrator for [SkillQuestHandlers] facade.
 * Fountain + harvest fragments hold the heavy bodies.
 */
object SkillQuestInteractHandlers {

    fun handleInteract(game: MudGame, target: String) {
        val spaceId = game.worldState.player.currentRoomId
        val feature = findFeature(game, spaceId, target)
        if (feature == null) {
            println("You don't see that here.")
            return
        }
        if (isFountain(feature)) {
            SkillQuestInteractFountain.handleFountainInteraction(game, feature)
            return
        }
        if (!validateHarvestTarget(feature)) return
        if (!SkillQuestInteractHarvest.hasRequiredTool(game, feature)) return
        println("\nYou attempt to harvest ${feature.name}...")
        SkillQuestInteractHarvest.performHarvest(game, spaceId, feature)
    }

    private fun isFountain(feature: Entity.Feature): Boolean =
        feature.properties["interaction_type"] == "fountain" &&
            feature.properties["heals_hp"] == "true"

    private fun validateHarvestTarget(feature: Entity.Feature): Boolean {
        if (feature.lootTableId == null) {
            println("There's nothing to harvest from that.")
            return false
        }
        if (feature.isCompleted) {
            println("This resource has already been harvested.")
            return false
        }
        return true
    }

    internal fun findFeature(game: MudGame, spaceId: String, target: String): Entity.Feature? {
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
