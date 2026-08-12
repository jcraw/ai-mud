@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent

/**
 * Interact orchestrator for [ClientSkillQuestHandlers] facade.
 * Pure-move of host body (GUI dispatch still uses ClientMovementHandlers for Interact).
 */
object ClientSkillQuestInteractHandlers {

    fun handleInteract(game: EngineGameClient, target: String) {
        val spaceId = game.worldState.player.currentRoomId
        val feature = findFeature(game, spaceId, target)
        if (feature == null) {
            game.emitEvent(GameEvent.System("You don't see that here.", GameEvent.MessageLevel.WARNING))
            return
        }
        if (isFountain(feature)) {
            ClientSkillQuestInteractFountain.handleFountainInteraction(game, feature)
            return
        }
        if (!validateHarvestTarget(game, feature)) return
        if (!ClientSkillQuestInteractHarvest.hasRequiredTool(game, feature)) return
        game.emitEvent(GameEvent.System(
            "\nYou attempt to harvest ${feature.name}...",
            GameEvent.MessageLevel.INFO
        ))
        ClientSkillQuestInteractHarvest.performHarvest(game, spaceId, feature)
    }

    private fun isFountain(feature: Entity.Feature): Boolean =
        feature.properties["interaction_type"] == "fountain" &&
            feature.properties["heals_hp"] == "true"

    private fun validateHarvestTarget(game: EngineGameClient, feature: Entity.Feature): Boolean {
        if (feature.lootTableId == null) {
            game.emitEvent(GameEvent.System(
                "There's nothing to harvest from that.",
                GameEvent.MessageLevel.INFO
            ))
            return false
        }
        if (feature.isCompleted) {
            game.emitEvent(GameEvent.System(
                "This resource has already been harvested.",
                GameEvent.MessageLevel.WARNING
            ))
            return false
        }
        return true
    }

    internal fun findFeature(
        game: EngineGameClient,
        spaceId: String,
        target: String
    ): Entity.Feature? {
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
