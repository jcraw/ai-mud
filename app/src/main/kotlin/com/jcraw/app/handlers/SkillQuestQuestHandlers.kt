@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.app.times
import com.jcraw.mud.core.Quest
import com.jcraw.mud.core.QuestStatus

/**
 * Quest list/accept/abandon/claim handlers for [SkillQuestHandlers] facade.
 * Pure extract.
 */
object SkillQuestQuestHandlers {

    fun handleQuests(game: MudGame) {
        val player = game.worldState.player
        println("\n═══════ QUEST LOG ═══════")
        println("Experience: ${player.experiencePoints} | Gold: ${player.gold}")
        println()
        printActiveQuests(player.activeQuests)
        println()
        printAvailableQuests(game)
        println("═" * 26)
    }

    private fun printActiveQuests(activeQuests: List<Quest>) {
        if (activeQuests.isEmpty()) {
            println("No active quests.")
            return
        }
        println("Active Quests:")
        activeQuests.forEachIndexed { index, quest ->
            printOneActiveQuest(index, quest)
        }
    }

    private fun printOneActiveQuest(index: Int, quest: Quest) {
        val statusIcon = questStatusIcon(quest)
        println("\n${index + 1}. $statusIcon ${quest.title}")
        println("   ${quest.description}")
        println("   Progress: ${quest.getProgressSummary()}")
        quest.objectives.forEach { obj ->
            val checkmark = if (obj.isCompleted) "✓" else "○"
            println("     $checkmark ${obj.description}")
        }
        if (quest.status == QuestStatus.COMPLETED) {
            println("   ⚠ Ready to claim reward! Use 'claim ${quest.id}'")
        }
    }

    private fun questStatusIcon(quest: Quest): String = when (quest.status) {
        QuestStatus.ACTIVE -> if (quest.isComplete()) "✓" else "○"
        QuestStatus.COMPLETED -> "✓"
        QuestStatus.CLAIMED -> "★"
        QuestStatus.FAILED -> "✗"
    }

    private fun printAvailableQuests(game: MudGame) {
        if (game.worldState.availableQuests.isEmpty()) return
        println("Available Quests (use 'accept <id>' to accept):")
        game.worldState.availableQuests.forEach { quest ->
            println("  - ${quest.id}: ${quest.title}")
        }
    }

    fun handleAcceptQuest(game: MudGame, questId: String?) {
        if (questId == null) {
            listAcceptableQuests(game)
            return
        }
        acceptQuestById(game, questId)
    }

    private fun listAcceptableQuests(game: MudGame) {
        if (game.worldState.availableQuests.isEmpty()) {
            println("No quests available to accept.")
            return
        }
        println("\nAvailable Quests:")
        game.worldState.availableQuests.forEach { quest ->
            println("  ${quest.id}: ${quest.title}")
            println("    ${quest.description}")
        }
        println("\nUse 'accept <quest_id>' to accept a quest.")
    }

    private fun acceptQuestById(game: MudGame, questId: String) {
        val quest = game.worldState.getAvailableQuest(questId)
        if (quest == null) {
            println("No quest available with ID '$questId'.")
            return
        }
        if (game.worldState.player.hasQuest(questId)) {
            println("You already have this quest!")
            return
        }
        game.worldState = game.worldState
            .updatePlayer(game.worldState.player.addQuest(quest))
            .removeAvailableQuest(questId)
        println("\n📜 Quest Accepted: ${quest.title}")
        println("${quest.description}")
        println("\nObjectives:")
        quest.objectives.forEach { println("  ○ ${it.description}") }
    }

    fun handleAbandonQuest(game: MudGame, questId: String) {
        val quest = game.worldState.player.getQuest(questId)
        if (quest == null) {
            println("You don't have a quest with ID '$questId'.")
            return
        }
        println("Are you sure you want to abandon '${quest.title}'? (y/n)")
        val confirm = readLine()?.trim()?.lowercase()
        if (confirm == "y" || confirm == "yes") {
            game.worldState = game.worldState
                .updatePlayer(game.worldState.player.removeQuest(questId))
                .addAvailableQuest(quest)
            println("Quest abandoned.")
        }
    }

    fun handleClaimReward(game: MudGame, questId: String) {
        val quest = game.worldState.player.getQuest(questId) ?: run {
            println("You don't have a quest with ID '$questId'.")
            return
        }
        if (!quest.isComplete()) {
            println("Quest '${quest.title}' is not complete yet!")
            println("Progress: ${quest.getProgressSummary()}")
            return
        }
        if (quest.status == QuestStatus.CLAIMED) {
            println("You've already claimed the reward for this quest!")
            return
        }
        game.worldState = game.worldState.updatePlayer(game.worldState.player.claimQuestReward(questId))
        printClaimRewards(game, quest)
    }

    private fun printClaimRewards(game: MudGame, quest: Quest) {
        println("\n🎉 Quest Completed: ${quest.title}")
        println("\nRewards:")
        if (quest.reward.experiencePoints > 0) {
            println("  +${quest.reward.experiencePoints} Experience")
        }
        if (quest.reward.goldAmount > 0) {
            println("  +${quest.reward.goldAmount} Gold")
        }
        if (quest.reward.items.isNotEmpty()) {
            println("  Items:")
            quest.reward.items.forEach { println("    - ${it.name}") }
        }
        println("\nTotal Experience: ${game.worldState.player.experiencePoints}")
        println("\nTotal Gold: ${game.worldState.player.gold}")
    }
}
