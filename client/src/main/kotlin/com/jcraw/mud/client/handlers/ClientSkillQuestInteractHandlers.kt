@file:Suppress("ReturnCount")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.reasoning.interact.FeatureMatch
import com.jcraw.mud.reasoning.interact.HarvestSupport

/**
 * Interact orchestrator for [ClientSkillQuestHandlers] facade.
 */
object ClientSkillQuestInteractHandlers {

    fun handleInteract(game: EngineGameClient, target: String) {
        val spaceId = game.worldState.player.currentRoomId
        val feature = findFeature(game, spaceId, target)
        if (feature == null) {
            game.emitEvent(GameEvent.System("You don't see that here.", GameEvent.MessageLevel.WARNING))
            return
        }
        if (FeatureMatch.isFountain(feature)) {
            ClientSkillQuestInteractFountain.handleFountainInteraction(game, feature)
            return
        }
        harvestOrWarn(game, spaceId, feature)
    }

    private fun harvestOrWarn(game: EngineGameClient, spaceId: String, feature: Entity.Feature) {
        val harvestError = HarvestSupport.validateHarvestTarget(feature)
        if (harvestError != null) {
            val level = if (feature.lootTableId == null) {
                GameEvent.MessageLevel.INFO
            } else {
                GameEvent.MessageLevel.WARNING
            }
            game.emitEvent(GameEvent.System(harvestError, level))
            return
        }
        if (!ClientSkillQuestInteractHarvest.hasRequiredTool(game, feature)) return
        game.emitEvent(
            GameEvent.System(
                "\nYou attempt to harvest ${feature.name}...",
                GameEvent.MessageLevel.INFO
            )
        )
        ClientSkillQuestInteractHarvest.performHarvest(game, spaceId, feature)
    }

    internal fun findFeature(
        game: EngineGameClient,
        spaceId: String,
        target: String
    ): Entity.Feature? = FeatureMatch.find(game.worldState.getEntitiesInSpace(spaceId), target)
}
