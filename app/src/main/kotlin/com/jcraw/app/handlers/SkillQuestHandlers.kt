@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame

/**
 * Thin facade for skill/quest/meta handlers.
 * Public handle* names preserved for MudGameEngine dispatch; bodies live in cluster extracts.
 */
object SkillQuestHandlers {

    fun handleInteract(game: MudGame, target: String) =
        SkillQuestInteractHandlers.handleInteract(game, target)

    fun handleCheck(game: MudGame, target: String) =
        SkillQuestCheckHandlers.handleCheck(game, target)

    fun handleUseSkill(game: MudGame, skill: String?, action: String) =
        SkillQuestSkillUseHandlers.handleUseSkill(game, skill, action)

    fun handleTrainSkill(game: MudGame, skill: String, method: String) =
        SkillQuestTrainHandlers.handleTrainSkill(game, skill, method)

    fun handleCraft(game: MudGame, target: String) =
        SkillQuestCraftHandlers.handleCraft(game, target)

    fun handleChoosePerk(game: MudGame, skillName: String, choice: Int) =
        SkillQuestTrainHandlers.handleChoosePerk(game, skillName, choice)

    fun handleViewSkills(game: MudGame) =
        SkillQuestTrainHandlers.handleViewSkills(game)

    fun handleSave(game: MudGame, saveName: String) =
        SkillQuestMetaHandlers.handleSave(game, saveName)

    fun handleLoad(game: MudGame, saveName: String) =
        SkillQuestMetaHandlers.handleLoad(game, saveName)

    fun handleHelp() =
        SkillQuestMetaHandlers.handleHelp()

    fun handleQuests(game: MudGame) =
        SkillQuestQuestHandlers.handleQuests(game)

    fun handleAcceptQuest(game: MudGame, questId: String?) =
        SkillQuestQuestHandlers.handleAcceptQuest(game, questId)

    fun handleAbandonQuest(game: MudGame, questId: String) =
        SkillQuestQuestHandlers.handleAbandonQuest(game, questId)

    fun handleClaimReward(game: MudGame, questId: String) =
        SkillQuestQuestHandlers.handleClaimReward(game, questId)

    fun handleQuit(game: MudGame) =
        SkillQuestMetaHandlers.handleQuit(game)
}
