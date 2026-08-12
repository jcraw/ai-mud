@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.Quest
import com.jcraw.mud.core.QuestStatus

/**
 * Quest list/accept/abandon/claim handlers for [ClientSkillQuestHandlers] facade.
 * Pure extract.
 */
object ClientSkillQuestQuestHandlers {

    fun handleQuests(game: EngineGameClient) {
        val player = game.worldState.player
        val text = buildString {
            appendLine("\n═══════ QUEST LOG ═══════")
            appendLine("Experience: ${player.experiencePoints} | Gold: ${player.gold}")
            appendLine()
            appendActiveQuests(this, player.activeQuests)
            appendLine()
            appendAvailableQuests(this, game)
            appendLine("═".repeat(26))
        }
        game.emitEvent(GameEvent.Quest(text))
    }

    private fun appendActiveQuests(sb: StringBuilder, activeQuests: List<Quest>) {
        if (activeQuests.isEmpty()) {
            sb.appendLine("No active quests.")
            return
        }
        sb.appendLine("Active Quests:")
        activeQuests.forEachIndexed { index, quest ->
            appendOneActiveQuest(sb, index, quest)
        }
    }

    private fun appendOneActiveQuest(sb: StringBuilder, index: Int, quest: Quest) {
        val statusIcon = questStatusIcon(quest)
        sb.appendLine("\n${index + 1}. $statusIcon ${quest.title}")
        sb.appendLine("   ${quest.description}")
        sb.appendLine("   Progress: ${quest.getProgressSummary()}")
        quest.objectives.forEach { obj ->
            val checkmark = if (obj.isCompleted) "✓" else "○"
            sb.appendLine("     $checkmark ${obj.description}")
        }
        if (quest.status == QuestStatus.COMPLETED) {
            sb.appendLine("   ⚠ Ready to claim reward! Use 'claim ${quest.id}'")
        }
    }

    private fun questStatusIcon(quest: Quest): String = when (quest.status) {
        QuestStatus.ACTIVE -> if (quest.isComplete()) "✓" else "○"
        QuestStatus.COMPLETED -> "✓"
        QuestStatus.CLAIMED -> "★"
        QuestStatus.FAILED -> "✗"
    }

    private fun appendAvailableQuests(sb: StringBuilder, game: EngineGameClient) {
        if (game.worldState.availableQuests.isEmpty()) return
        sb.appendLine("Available Quests (use 'accept <id>' to accept):")
        game.worldState.availableQuests.forEach { quest ->
            sb.appendLine("  - ${quest.id}: ${quest.title}")
        }
    }

    fun handleAcceptQuest(game: EngineGameClient, questId: String?) {
        if (questId == null) {
            listAcceptableQuests(game)
            return
        }
        acceptQuestById(game, questId)
    }

    private fun listAcceptableQuests(game: EngineGameClient) {
        if (game.worldState.availableQuests.isEmpty()) {
            game.emitEvent(GameEvent.System("No quests available to accept.", GameEvent.MessageLevel.INFO))
            return
        }
        val text = buildString {
            appendLine("\nAvailable Quests:")
            game.worldState.availableQuests.forEach { quest ->
                appendLine("  ${quest.id}: ${quest.title}")
                appendLine("    ${quest.description}")
            }
            appendLine("\nUse 'accept <quest_id>' to accept a quest.")
        }
        game.emitEvent(GameEvent.Quest(text))
    }

    private fun acceptQuestById(game: EngineGameClient, questId: String) {
        val quest = game.worldState.getAvailableQuest(questId)
        if (quest == null) {
            game.emitEvent(GameEvent.System("No quest available with ID '$questId'.", GameEvent.MessageLevel.WARNING))
            return
        }
        if (game.worldState.player.hasQuest(questId)) {
            game.emitEvent(GameEvent.System("You already have this quest!", GameEvent.MessageLevel.WARNING))
            return
        }
        game.worldState = game.worldState
            .updatePlayer(game.worldState.player.addQuest(quest))
            .removeAvailableQuest(questId)
        val text = buildString {
            appendLine("\n📜 Quest Accepted: ${quest.title}")
            appendLine(quest.description)
            appendLine("\nObjectives:")
            quest.objectives.forEach { appendLine("  ○ ${it.description}") }
        }
        game.emitEvent(GameEvent.Quest(text, questId))
    }

    fun handleAbandonQuest(game: EngineGameClient, questId: String) {
        val quest = game.worldState.player.getQuest(questId)
        if (quest == null) {
            game.emitEvent(GameEvent.System("You don't have a quest with ID '$questId'.", GameEvent.MessageLevel.WARNING))
            return
        }
        game.worldState = game.worldState
            .updatePlayer(game.worldState.player.removeQuest(questId))
            .addAvailableQuest(quest)
        game.emitEvent(GameEvent.Quest("Quest '${quest.title}' abandoned.", questId))
    }

    fun handleClaimReward(game: EngineGameClient, questId: String) {
        val quest = game.worldState.player.getQuest(questId) ?: run {
            game.emitEvent(GameEvent.System("You don't have a quest with ID '$questId'.", GameEvent.MessageLevel.WARNING))
            return
        }
        if (!quest.isComplete()) {
            game.emitEvent(GameEvent.System(
                "Quest '${quest.title}' is not complete yet!\nProgress: ${quest.getProgressSummary()}",
                GameEvent.MessageLevel.WARNING
            ))
            return
        }
        if (quest.status == QuestStatus.CLAIMED) {
            game.emitEvent(GameEvent.System(
                "You've already claimed the reward for this quest!",
                GameEvent.MessageLevel.WARNING
            ))
            return
        }
        game.worldState = game.worldState.updatePlayer(game.worldState.player.claimQuestReward(questId))
        emitClaimRewards(game, quest, questId)
    }

    private fun emitClaimRewards(game: EngineGameClient, quest: Quest, questId: String) {
        game.emitEvent(GameEvent.Quest(formatClaimText(game, quest), questId))
        game.emitEvent(GameEvent.StatusUpdate(
            hp = game.worldState.player.health,
            maxHp = game.worldState.player.maxHealth
        ))
    }

    private fun formatClaimText(game: EngineGameClient, quest: Quest): String = buildString {
        appendLine("\n🎉 Quest Completed: ${quest.title}")
        appendLine("\nRewards:")
        if (quest.reward.experiencePoints > 0) {
            appendLine("  +${quest.reward.experiencePoints} Experience")
        }
        if (quest.reward.goldAmount > 0) {
            appendLine("  +${quest.reward.goldAmount} Gold")
        }
        if (quest.reward.items.isNotEmpty()) {
            appendLine("  Items:")
            quest.reward.items.forEach { appendLine("    - ${it.name}") }
        }
        appendLine("\nTotal Experience: ${game.worldState.player.experiencePoints}")
        appendLine("Total Gold: ${game.worldState.player.gold}")
    }
}
