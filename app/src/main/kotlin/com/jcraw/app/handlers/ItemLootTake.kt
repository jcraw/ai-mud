@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList"
)

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GOLD_TEMPLATE_ID
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.reasoning.QuestAction

/**
 * Single-item and gold loot from corpse.
 */
internal object ItemLootTake {

    fun lootGold(game: MudGame, spaceId: String, corpse: Entity.Corpse) {
        if (corpse.goldAmount <= 0) {
            println("There's no gold in the corpse.")
            return
        }
        val goldAmount = corpse.goldAmount
        val goldInstance = corpse.contents.find { it.templateId == GOLD_TEMPLATE_ID }
        val updatedCorpse = stripGoldFromCorpse(corpse, goldAmount, goldInstance)
        val updatedPlayer = game.worldState.player.addGoldV2(goldAmount)
        commitGoldLoot(game, spaceId, corpse, updatedCorpse, updatedPlayer, goldInstance, goldAmount)
    }

    private fun stripGoldFromCorpse(
        corpse: Entity.Corpse,
        goldAmount: Int,
        goldInstance: ItemInstance?
    ): Entity.Corpse {
        var updated = corpse.removeGold(goldAmount)
        if (goldInstance != null) {
            updated = updated.removeItem(goldInstance.id)
        }
        return updated
    }

    private fun commitGoldLoot(
        game: MudGame,
        spaceId: String,
        corpse: Entity.Corpse,
        updatedCorpse: Entity.Corpse,
        updatedPlayer: com.jcraw.mud.core.PlayerState,
        goldInstance: ItemInstance?,
        goldAmount: Int
    ) {
        val newState = game.worldState
            .updatePlayer(updatedPlayer)
            .replaceEntityInSpace(spaceId, corpse.id, updatedCorpse)
        if (newState == null) {
            println("Something went wrong.")
            return
        }
        var stateWithLoot = newState
        if (goldInstance != null) {
            stateWithLoot = stateWithLoot.removeDroppedItem(
                spaceId = spaceId,
                instanceId = goldInstance.id,
                removeEntity = true,
                updateCorpses = false
            )
        }
        game.worldState = stateWithLoot
        println("You take $goldAmount gold from ${corpse.name}.")
    }

    fun lootItem(game: MudGame, spaceId: String, corpse: Entity.Corpse, itemTarget: String) {
        val matchingItem = findMatchingContent(game, corpse, itemTarget)
        if (matchingItem == null) {
            println("That item isn't in the corpse.")
            return
        }
        val template = game.itemRepository.findTemplateById(matchingItem.templateId).getOrNull()
        if (template == null) {
            println("Something went wrong.")
            return
        }
        val templates = mapOf(template.id to template)
        val updatedPlayer = game.worldState.player.addItemInstance(matchingItem, templates)
        if (updatedPlayer == null) {
            println("You can't carry that - you're already carrying too much weight.")
            return
        }
        commitItemLoot(game, spaceId, corpse, matchingItem, updatedPlayer, template.name)
    }

    private fun commitItemLoot(
        game: MudGame,
        spaceId: String,
        corpse: Entity.Corpse,
        matchingItem: ItemInstance,
        updatedPlayer: com.jcraw.mud.core.PlayerState,
        templateName: String
    ) {
        val updatedCorpse = corpse.removeItem(matchingItem.id)
        val newState = game.worldState
            .updatePlayer(updatedPlayer)
            .replaceEntityInSpace(spaceId, corpse.id, updatedCorpse)
        if (newState == null) {
            println("Something went wrong.")
            return
        }
        game.worldState = newState.removeDroppedItem(
            spaceId = spaceId,
            instanceId = matchingItem.id,
            removeEntity = true,
            updateCorpses = false
        )
        println("You take the $templateName from ${corpse.name}.")
        game.trackQuests(QuestAction.CollectedItem(matchingItem.id))
    }

    private fun findMatchingContent(
        game: MudGame,
        corpse: Entity.Corpse,
        itemTarget: String
    ): ItemInstance? =
        corpse.contents.find { instance ->
            val templateResult = game.itemRepository.findTemplateById(instance.templateId)
            templateResult.getOrNull()?.let { template ->
                template.name.lowercase().contains(itemTarget.lowercase()) ||
                    instance.id.lowercase().contains(itemTarget.lowercase())
            } ?: false
        }
}
