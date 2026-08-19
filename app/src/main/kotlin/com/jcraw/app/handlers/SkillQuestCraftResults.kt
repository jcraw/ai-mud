package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.crafting.Recipe
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.crafting.CraftXp
import com.jcraw.mud.reasoning.crafting.CraftingManager

/**
 * Craft success/failure result handlers (fragment of craft cluster).
 */
object SkillQuestCraftResults {

    fun onSuccess(
        game: MudGame,
        inventoryComponent: InventoryComponent,
        recipe: Recipe,
        result: CraftingManager.CraftResult.Success
    ) {
        println(result.message)
        val updatedInventory = inventoryComponent.addItem(result.craftedItem)
        val updatedPlayer = game.worldState.player.copy(inventoryComponent = updatedInventory)
        game.worldState = game.worldState.updatePlayer(updatedPlayer)
        game.trackQuests(QuestAction.CollectedItem(result.craftedItem.id))
        printCraftXp(game, recipe.requiredSkill, CraftXp.successXp(recipe.difficulty), success = true)
    }

    fun onFailure(
        game: MudGame,
        inventoryComponent: InventoryComponent,
        recipe: Recipe,
        result: CraftingManager.CraftResult.Failure
    ) {
        println(result.message)
        applyMaterialLoss(game, inventoryComponent, result)
        printCraftXp(game, recipe.requiredSkill, CraftXp.failureXp(recipe.difficulty), success = false)
    }

    private fun applyMaterialLoss(
        game: MudGame,
        inventoryComponent: InventoryComponent,
        result: CraftingManager.CraftResult.Failure
    ) {
        var updatedInventory: InventoryComponent = inventoryComponent
        result.inputsLost.forEach { (templateId, qty) ->
            val itemsToRemove = updatedInventory.items.filter { it.templateId == templateId }.take(qty)
            itemsToRemove.forEach { item ->
                updatedInventory = updatedInventory.removeItem(item.id) ?: updatedInventory
            }
        }
        val updatedPlayer = game.worldState.player.copy(inventoryComponent = updatedInventory)
        game.worldState = game.worldState.updatePlayer(updatedPlayer)
        printMaterialsLost(game, result)
    }

    private fun printMaterialsLost(game: MudGame, result: CraftingManager.CraftResult.Failure) {
        if (result.inputsLost.isEmpty()) return
        println("Materials lost:")
        result.inputsLost.forEach { (templateId, qty) ->
            val templateName = game.itemRepository.findTemplateById(templateId).getOrNull()?.name ?: templateId
            println("  - $qty x $templateName")
        }
    }

    private fun printCraftXp(game: MudGame, skillName: String, baseXp: Long, success: Boolean) {
        val xpEvents = game.skillManager.attemptSkillProgress(
            entityId = game.worldState.player.id,
            skillName = skillName,
            baseXp = baseXp,
            success = success
        ).getOrNull() ?: emptyList()
        xpEvents.forEach { event ->
            when (event) {
                is SkillEvent.XpGained -> println(CraftXp.gainedLine(skillName, event))
                is SkillEvent.LevelUp -> {
                    println("$skillName leveled up! ${event.oldLevel} -> ${event.newLevel}")
                    if (success && event.isAtPerkMilestone) {
                        println("Milestone reached! Use 'choose perk for $skillName' to select a perk.")
                    }
                }
                else -> {}
            }
        }
    }
}
