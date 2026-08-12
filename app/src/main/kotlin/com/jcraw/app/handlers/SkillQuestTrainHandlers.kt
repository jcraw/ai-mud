@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.action.SkillFormatter
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.Perk

/**
 * Train / perk / view-skills handlers for [SkillQuestHandlers] facade.
 * Pure extract.
 */
object SkillQuestTrainHandlers {

    fun handleTrainSkill(game: MudGame, skill: String, method: String) {
        val spaceId = game.worldState.player.currentRoomId
        val npcName = parseNpcName(method)
        if (npcName.isBlank()) {
            println("\nTrain with whom? Use 'train <skill> with <npc>'.")
            return
        }
        val npc = findNpc(game, spaceId, npcName)
        if (npc == null) {
            println("\nThere's no one here by that name to train with.")
            return
        }
        runTraining(game, spaceId, npc, skill)
    }

    private fun parseNpcName(method: String): String =
        method.lowercase()
            .removePrefix("with ")
            .removePrefix("the ")
            .removePrefix("at ")
            .removePrefix("from ")
            .trim()

    private fun findNpc(game: MudGame, spaceId: String, npcName: String): Entity.NPC? =
        game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.NPC>()
            .find {
                it.name.lowercase().contains(npcName) ||
                    it.id.lowercase().contains(npcName)
            }

    private fun runTraining(game: MudGame, spaceId: String, npc: Entity.NPC, skill: String) {
        val trainingResult = game.dispositionManager.trainSkillWithNPC(
            game.worldState.player.id,
            npc,
            skill
        )
        trainingResult.onSuccess { message ->
            println("\n$message")
            game.worldState = game.worldState.replaceEntityInSpace(spaceId, npc.id, npc) ?: game.worldState
        }.onFailure { error ->
            println("\n${error.message}")
        }
    }

    fun handleChoosePerk(game: MudGame, skillName: String, choice: Int) {
        val component = game.skillManager.getSkillComponent(game.worldState.player.id)
        val skillState = component.getSkill(skillName)
        if (skillState == null) {
            println("\nYou don't have the skill '$skillName'. Train it first!")
            return
        }
        val availablePerks = game.perkSelector.getPerkChoices(skillName, skillState.level)
        if (availablePerks.isEmpty()) {
            println("\nNo perk choices available for $skillName at level ${skillState.level}.")
            return
        }
        selectPerkChoice(game, skillName, choice, availablePerks)
    }

    private fun selectPerkChoice(
        game: MudGame,
        skillName: String,
        choice: Int,
        availablePerks: List<Perk>
    ) {
        if (choice < 1 || choice > availablePerks.size) {
            println("\nInvalid choice. Please choose a number between 1 and ${availablePerks.size}.")
            return
        }
        val chosenPerk = availablePerks[choice - 1]
        val event = game.perkSelector.selectPerk(game.worldState.player.id, skillName, chosenPerk)
        if (event != null) {
            val message = SkillFormatter.formatPerkUnlocked(chosenPerk.name, skillName)
            println("\n$message")
        } else {
            println("\nFailed to unlock perk. You may not have a pending perk choice for this skill.")
        }
    }

    fun handleViewSkills(game: MudGame) {
        val component = game.skillManager.getSkillComponent(game.worldState.player.id)
        println("\n" + SkillFormatter.formatSkillSheet(component))
    }
}
