@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.Perk

/**
 * Train / perk / view-skills handlers for [ClientSkillQuestHandlers] facade.
 * Pure extract.
 */
object ClientSkillQuestTrainHandlers {

    fun handleTrainSkill(game: EngineGameClient, skill: String, method: String) {
        val entitiesInSpace = game.worldState.getEntitiesInSpace(game.worldState.player.currentRoomId)
        val npcName = parseNpcName(method)
        if (npcName.isBlank()) {
            game.emitEvent(GameEvent.System(
                "Train with whom? Use 'train <skill> with <npc>'.",
                GameEvent.MessageLevel.WARNING
            ))
            return
        }
        val npc = findNpc(entitiesInSpace, npcName)
        if (npc == null) {
            game.emitEvent(GameEvent.System(
                "There's no one here by that name to train with.",
                GameEvent.MessageLevel.WARNING
            ))
            return
        }
        runTraining(game, npc, skill)
    }

    private fun parseNpcName(method: String): String =
        method.lowercase()
            .removePrefix("with ")
            .removePrefix("the ")
            .removePrefix("at ")
            .removePrefix("from ")
            .trim()

    private fun findNpc(entities: List<Entity>, npcName: String): Entity.NPC? =
        entities.filterIsInstance<Entity.NPC>()
            .find {
                it.name.lowercase().contains(npcName) ||
                    it.id.lowercase().contains(npcName)
            }

    private fun runTraining(game: EngineGameClient, npc: Entity.NPC, skill: String) {
        val trainingResult = game.dispositionManager.trainSkillWithNPC(
            game.worldState.player.id,
            npc,
            skill
        )
        trainingResult.onSuccess { message ->
            game.emitEvent(GameEvent.Narrative(message))
            game.worldState = game.worldState.replaceEntityInSpace(
                game.worldState.player.currentRoomId, npc.id, npc
            ) ?: game.worldState
        }.onFailure { error ->
            game.emitEvent(GameEvent.System(error.message ?: "Training failed", GameEvent.MessageLevel.ERROR))
        }
    }

    fun handleChoosePerk(game: EngineGameClient, skillName: String, choice: Int) {
        val component = game.skillManager.getSkillComponent(game.worldState.player.id)
        val skillState = component.getSkill(skillName)
        if (skillState == null) {
            game.emitEvent(GameEvent.System(
                "You don't have the skill '$skillName'. Train it first!",
                GameEvent.MessageLevel.WARNING
            ))
            return
        }
        val availablePerks = game.perkSelector.getPerkChoices(skillName, skillState.level)
        if (availablePerks.isEmpty()) {
            game.emitEvent(GameEvent.System(
                "No perk choices available for $skillName at level ${skillState.level}.",
                GameEvent.MessageLevel.INFO
            ))
            return
        }
        selectPerkChoice(game, skillName, choice, availablePerks)
    }

    private fun selectPerkChoice(
        game: EngineGameClient,
        skillName: String,
        choice: Int,
        availablePerks: List<Perk>
    ) {
        if (choice < 1 || choice > availablePerks.size) {
            game.emitEvent(GameEvent.System(
                "Invalid choice. Please choose a number between 1 and ${availablePerks.size}.",
                GameEvent.MessageLevel.WARNING
            ))
            return
        }
        applyPerkSelection(game, skillName, availablePerks[choice - 1])
    }

    private fun applyPerkSelection(game: EngineGameClient, skillName: String, chosenPerk: Perk) {
        val event = game.perkSelector.selectPerk(game.worldState.player.id, skillName, chosenPerk)
        if (event != null) {
            val message = com.jcraw.mud.action.SkillFormatter.formatPerkUnlocked(chosenPerk.name, skillName)
            game.emitEvent(GameEvent.Narrative(message))
        } else {
            game.emitEvent(GameEvent.System(
                "Failed to unlock perk. You may not have a pending perk choice for this skill.",
                GameEvent.MessageLevel.ERROR
            ))
        }
    }

    fun handleViewSkills(game: EngineGameClient) {
        val component = game.skillManager.getSkillComponent(game.worldState.player.id)
        val formattedSkillSheet = com.jcraw.mud.action.SkillFormatter.formatSkillSheet(component)
        game.emitEvent(GameEvent.Narrative(formattedSkillSheet))
    }
}
