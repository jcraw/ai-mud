@file:Suppress("ReturnCount")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity
import com.jcraw.mud.reasoning.interact.FeatureMatch
import com.jcraw.mud.reasoning.interact.HarvestSupport

/**
 * Interact orchestrator for [SkillQuestHandlers] facade.
 */
object SkillQuestInteractHandlers {

    fun handleInteract(game: MudGame, target: String) {
        val spaceId = game.worldState.player.currentRoomId
        val feature = findFeature(game, spaceId, target)
        if (feature == null) {
            println("You don't see that here.")
            return
        }
        if (FeatureMatch.isFountain(feature)) {
            SkillQuestInteractFountain.handleFountainInteraction(game, feature)
            return
        }
        val harvestError = HarvestSupport.validateHarvestTarget(feature)
        if (harvestError != null) {
            println(harvestError)
            return
        }
        if (!SkillQuestInteractHarvest.hasRequiredTool(game, feature)) return
        println("\nYou attempt to harvest ${feature.name}...")
        SkillQuestInteractHarvest.performHarvest(game, spaceId, feature)
    }

    internal fun findFeature(game: MudGame, spaceId: String, target: String): Entity.Feature? =
        FeatureMatch.find(game.worldState.getEntitiesInSpace(spaceId), target)
}
