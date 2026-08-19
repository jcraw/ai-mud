package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.crafting.Recipe
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.crafting.CraftXp
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
        emitCraftXp(game, recipe.requiredSkill, CraftXp.successXp(recipe.difficulty), showMilestone = true)
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
        emitCraftXp(game, recipe.requiredSkill, CraftXp.failureXp(recipe.difficulty), showMilestone = false)
    }

    private fun emitCraftXp(
        game: EngineGameClient,
        skillName: String,
        baseXp: Long,
        showMilestone: Boolean
    ) {
        val xpEvents = game.skillManager.attemptSkillProgress(
            entityId = game.worldState.player.id,
            skillName = skillName,
            baseXp = baseXp,
            success = showMilestone
        ).getOrNull() ?: emptyList()
        xpEvents.forEach { event ->
            when (event) {
                is SkillEvent.XpGained -> emitXpGained(game, skillName, event)
                is SkillEvent.LevelUp -> emitLevelUp(game, skillName, event, showMilestone)
                else -> {}
            }
        }
    }

    private fun emitXpGained(game: EngineGameClient, skillName: String, event: SkillEvent.XpGained) {
        game.emitEvent(
            GameEvent.System(CraftXp.gainedLine(skillName, event), GameEvent.MessageLevel.INFO)
        )
    }

    private fun emitLevelUp(
        game: EngineGameClient,
        skillName: String,
        event: SkillEvent.LevelUp,
        showMilestone: Boolean
    ) {
        val method = if (event.oldLevel == 0) "(lucky progression)" else "(lucky level-up)"
        game.emitEvent(
            GameEvent.System(
                "🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel} $method",
                GameEvent.MessageLevel.INFO
            )
        )
        if (showMilestone && event.isAtPerkMilestone) {
            game.emitEvent(
                GameEvent.System(
                    "⚡ Milestone reached! Use 'choose perk for $skillName' to select a perk.",
                    GameEvent.MessageLevel.INFO
                )
            )
        }
    }
}
