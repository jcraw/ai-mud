@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient

/**
 * Thin facade for client skill/quest/meta handlers.
 * Public handle* names preserved for EngineGameClient dispatch; bodies live in cluster extracts.
 *
 * Note: Intent.Craft remains stubbed on EngineGameClient (handleCraft body pure-moved only).
 * Intent.Interact dispatches via ClientMovementHandlers (out of family); interact body retained here.
 */
object ClientSkillQuestHandlers {

    fun handleUseSkill(game: EngineGameClient, skill: String?, action: String) =
        ClientSkillQuestSkillUseHandlers.handleUseSkill(game, skill, action)

    fun inferSkillFromAction(action: String): String? =
        ClientSkillQuestSkillInfer.inferSkillFromAction(action)

    fun handleTrainSkill(game: EngineGameClient, skill: String, method: String) =
        ClientSkillQuestTrainHandlers.handleTrainSkill(game, skill, method)

    fun handleChoosePerk(game: EngineGameClient, skillName: String, choice: Int) =
        ClientSkillQuestTrainHandlers.handleChoosePerk(game, skillName, choice)

    fun handleViewSkills(game: EngineGameClient) =
        ClientSkillQuestTrainHandlers.handleViewSkills(game)

    fun handleSave(game: EngineGameClient, saveName: String) =
        ClientSkillQuestMetaHandlers.handleSave(game, saveName)

    fun handleLoad(game: EngineGameClient, saveName: String) =
        ClientSkillQuestMetaHandlers.handleLoad(game, saveName)

    fun handleHelp(game: EngineGameClient) =
        ClientSkillQuestMetaHandlers.handleHelp(game)

    fun handleQuests(game: EngineGameClient) =
        ClientSkillQuestQuestHandlers.handleQuests(game)

    fun handleAcceptQuest(game: EngineGameClient, questId: String?) =
        ClientSkillQuestQuestHandlers.handleAcceptQuest(game, questId)

    fun handleAbandonQuest(game: EngineGameClient, questId: String) =
        ClientSkillQuestQuestHandlers.handleAbandonQuest(game, questId)

    fun handleClaimReward(game: EngineGameClient, questId: String) =
        ClientSkillQuestQuestHandlers.handleClaimReward(game, questId)

    fun handleInteract(game: EngineGameClient, target: String) =
        ClientSkillQuestInteractHandlers.handleInteract(game, target)

    fun handleCraft(game: EngineGameClient, target: String) =
        ClientSkillQuestCraftHandlers.handleCraft(game, target)

    fun handleQuit(game: EngineGameClient) =
        ClientSkillQuestMetaHandlers.handleQuit(game)
}
