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
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.reasoning.QuestAction

/**
 * Loot-all body for [ItemLootHandlers].
 */
internal object ItemLootAll {

    fun lootAll(game: MudGame, spaceId: String, corpse: Entity.Corpse) {
        val lootedInstanceIds = mutableListOf<String>()
        val templateMap = loadTemplates(game, corpse)
        val itemResult = lootItems(game, corpse, templateMap, lootedInstanceIds)
        val goldAmount = corpse.goldAmount
        val goldResult = lootGoldFromCorpse(
            corpse, goldAmount, itemResult.player, itemResult.corpse, lootedInstanceIds
        )
        commitLootState(game, spaceId, corpse.id, goldResult.first, goldResult.second, lootedInstanceIds)
        printLootSummary(corpse.name, itemResult.lootedCount, goldAmount, itemResult.failedCount)
    }

    private fun loadTemplates(game: MudGame, corpse: Entity.Corpse): Map<String, ItemTemplate> {
        val templateMap = mutableMapOf<String, ItemTemplate>()
        corpse.contents.forEach { instance ->
            val templateResult = game.itemRepository.findTemplateById(instance.templateId)
            templateResult.getOrNull()?.let { template ->
                templateMap[template.id] = template
            }
        }
        return templateMap
    }

    private data class ItemLootResult(
        val lootedCount: Int,
        val failedCount: Int,
        val player: PlayerState,
        val corpse: Entity.Corpse
    )

    private fun lootItems(
        game: MudGame,
        corpse: Entity.Corpse,
        templateMap: Map<String, ItemTemplate>,
        lootedInstanceIds: MutableList<String>
    ): ItemLootResult {
        var lootedCount = 0
        var failedCount = 0
        var currentPlayer = game.worldState.player
        var currentCorpse: Entity.Corpse = corpse
        corpse.contents.forEach { instance ->
            when (val step = tryLootOne(game, instance, templateMap, currentPlayer, currentCorpse)) {
                is OneLoot.Ok -> {
                    currentPlayer = step.player
                    currentCorpse = step.corpse
                    lootedCount++
                    lootedInstanceIds.add(instance.id)
                }
                is OneLoot.Fail -> failedCount++
                is OneLoot.Skip -> failedCount++
            }
        }
        return ItemLootResult(lootedCount, failedCount, currentPlayer, currentCorpse)
    }

    private sealed class OneLoot {
        data class Ok(val player: PlayerState, val corpse: Entity.Corpse) : OneLoot()
        data object Fail : OneLoot()
        data object Skip : OneLoot()
    }

    private fun tryLootOne(
        game: MudGame,
        instance: ItemInstance,
        templateMap: Map<String, ItemTemplate>,
        player: PlayerState,
        corpse: Entity.Corpse
    ): OneLoot {
        val template = templateMap[instance.templateId] ?: return OneLoot.Skip
        val updatedPlayer = player.addItemInstance(instance, templateMap)
        if (updatedPlayer == null) {
            println("You can't carry the ${template.name} - too heavy.")
            return OneLoot.Fail
        }
        println("You take the ${template.name}.")
        game.trackQuests(QuestAction.CollectedItem(instance.id))
        return OneLoot.Ok(updatedPlayer, corpse.removeItem(instance.id))
    }

    private fun lootGoldFromCorpse(
        corpse: Entity.Corpse,
        goldAmount: Int,
        player: PlayerState,
        currentCorpse: Entity.Corpse,
        lootedInstanceIds: MutableList<String>
    ): Pair<PlayerState, Entity.Corpse> {
        if (goldAmount <= 0) return player to currentCorpse
        println("You take $goldAmount gold.")
        var updatedCorpse = currentCorpse.removeGold(goldAmount)
        val goldInstance = corpse.contents.find { it.templateId == GOLD_TEMPLATE_ID }
        if (goldInstance != null) {
            updatedCorpse = updatedCorpse.removeItem(goldInstance.id)
            lootedInstanceIds.add(goldInstance.id)
        }
        return player.addGoldV2(goldAmount) to updatedCorpse
    }

    private fun commitLootState(
        game: MudGame,
        spaceId: String,
        corpseId: String,
        player: PlayerState,
        currentCorpse: Entity.Corpse,
        lootedInstanceIds: List<String>
    ) {
        val newState = game.worldState
            .updatePlayer(player)
            .replaceEntityInSpace(spaceId, corpseId, currentCorpse)
        var finalState = newState
        lootedInstanceIds.forEach { instanceId ->
            finalState = finalState.removeDroppedItem(
                spaceId = spaceId,
                instanceId = instanceId,
                removeEntity = true,
                updateCorpses = false
            )
        }
        game.worldState = finalState
    }

    private fun printLootSummary(
        corpseName: String,
        lootedCount: Int,
        goldAmount: Int,
        failedCount: Int
    ) {
        val summary = buildString {
            append("\nYou looted ")
            if (lootedCount > 0) {
                append("$lootedCount item${if (lootedCount > 1) "s" else ""}")
            }
            if (lootedCount > 0 && goldAmount > 0) {
                append(" and ")
            }
            if (goldAmount > 0) {
                append("$goldAmount gold")
            }
            append(" from $corpseName.")
            if (failedCount > 0) {
                append(" ($failedCount item${if (failedCount > 1) "s" else ""} left due to weight)")
            }
        }
        println(summary)
    }
}
