@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.crafting.Recipe
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.crafting.CraftingManager

/**
 * Client craft success/failure result handlers (fragment of craft cluster).
 */
object ClientSkillQuestCraftResults {

    fun onSuccess(
        game: EngineGameClient,
        playerInventory: InventoryComponent,
        recipe: Recipe,
        result: CraftingManager.CraftResult.Success
    ) {
        val updatedPlayer = game.worldState.player.copy(inventoryComponent = playerInventory)
        game.worldState = game.worldState.updatePlayer(updatedPlayer)
        game.emitEvent(GameEvent.System("✨ ${result.message}", GameEvent.MessageLevel.INFO))
        game.trackQuests(QuestAction.CollectedItem(result.craftedItem.id))
        val baseXp = 50L + (recipe.difficulty * 5L)
        emitCraftXp(game, recipe.requiredSkill, baseXp, success = true, showMilestone = true)
    }

    fun onFailure(
        game: EngineGameClient,
        playerInventory: InventoryComponent,
        recipe: Recipe,
        result: CraftingManager.CraftResult.Failure
    ) {
        val updatedPlayer = game.worldState.player.copy(inventoryComponent = playerInventory)
        game.worldState = game.worldState.updatePlayer(updatedPlayer)
        game.emitEvent(GameEvent.System(result.message, GameEvent.MessageLevel.WARNING))
        val baseXp = 10L + (recipe.difficulty * 1L)
        emitCraftXp(game, recipe.requiredSkill, baseXp, success = false, showMilestone = false)
    }

    private fun emitCraftXp(
        game: EngineGameClient,
        skillName: String,
        baseXp: Long,
        success: Boolean,
        showMilestone: Boolean
    ) {
        val xpEvents = game.skillManager.attemptSkillProgress(
            entityId = game.worldState.player.id,
            skillName = skillName,
            baseXp = baseXp,
            success = success
        ).getOrNull() ?: emptyList()
        emitXpLines(game, skillName, xpEvents, showMilestone)
    }

    private fun emitXpLines(
        game: EngineGameClient,
        skillName: String,
        xpEvents: List<SkillEvent>,
        showMilestone: Boolean
    ) {
        xpEvents.forEach { event ->
            when (event) {
                is SkillEvent.XpGained -> emitXpGained(game, skillName, event)
                is SkillEvent.LevelUp -> emitLevelUp(game, skillName, event, showMilestone)
                else -> {}
            }
        }
    }

    private fun emitXpGained(game: EngineGameClient, skillName: String, event: SkillEvent.XpGained) {
        game.emitEvent(GameEvent.System(
            "+${event.xpAmount} XP to $skillName (${event.currentXp} total, level ${event.currentLevel})",
            GameEvent.MessageLevel.INFO
        ))
    }

    private fun emitLevelUp(
        game: EngineGameClient,
        skillName: String,
        event: SkillEvent.LevelUp,
        showMilestone: Boolean
    ) {
        val method = if (event.oldLevel == 0) "(lucky progression)" else "(lucky level-up)"
        game.emitEvent(GameEvent.System(
            "🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel} $method",
            GameEvent.MessageLevel.INFO
        ))
        if (showMilestone && event.isAtPerkMilestone) {
            game.emitEvent(GameEvent.System(
                "⚡ Milestone reached! Use 'choose perk for $skillName' to select a perk.",
                GameEvent.MessageLevel.INFO
            ))
        }
    }
}
