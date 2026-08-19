@file:Suppress("ReturnCount", "TooManyFunctions")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.SkillCheckResult
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.interact.HarvestSupport
import com.jcraw.mud.reasoning.loot.LootGenerator
import com.jcraw.mud.reasoning.loot.LootSource
import com.jcraw.mud.reasoning.loot.LootTableRegistry

/**
 * Harvest validation, skill check, loot, and XP for client interact cluster.
 */
object ClientSkillQuestInteractHarvest {

    fun hasRequiredTool(game: EngineGameClient, feature: Entity.Feature): Boolean {
        val requiredToolTag = feature.properties["required_tool_tag"] ?: return true
        val hasTool = HarvestSupport.hasRequiredTool(
            game.worldState.player.inventoryComponent?.items,
            requiredToolTag
        ) { id -> game.itemRepository.findTemplateById(id).getOrNull() }
        if (!hasTool) {
            game.emitEvent(
                GameEvent.System(
                    HarvestSupport.missingToolMessage(requiredToolTag),
                    GameEvent.MessageLevel.WARNING
                )
            )
            return false
        }
        return true
    }

    fun performHarvest(game: EngineGameClient, spaceId: String, feature: Entity.Feature) {
        val outcome = runHarvestSkillCheck(game, feature)
        if (outcome is HarvestSupport.CheckOutcome.Failed) return
        grantHarvestLoot(game, feature)
        if (outcome is HarvestSupport.CheckOutcome.Passed) {
            awardGatheringXp(game, feature, outcome.result)
        }
        markHarvested(game, spaceId, feature)
    }

    private fun markHarvested(game: EngineGameClient, spaceId: String, feature: Entity.Feature) {
        val updatedFeature = feature.copy(isCompleted = true)
        game.worldState = game.worldState.replaceEntityInSpace(spaceId, feature.id, updatedFeature)
            ?: game.worldState
    }

    private fun runHarvestSkillCheck(
        game: EngineGameClient,
        feature: Entity.Feature
    ): HarvestSupport.CheckOutcome {
        if (feature.skillChallenge == null) return HarvestSupport.CheckOutcome.NoChallenge
        val challenge = feature.skillChallenge!!
        val result = game.skillCheckResolver.checkPlayer(
            game.worldState.player,
            challenge.statType,
            challenge.difficulty
        )
        emitRoll(game, challenge.statType.name, result)
        return harvestCheckOutcome(game, challenge.statType.name, result)
    }

    private fun harvestCheckOutcome(
        game: EngineGameClient,
        skillName: String,
        result: SkillCheckResult
    ): HarvestSupport.CheckOutcome {
        if (!result.success) {
            game.emitEvent(
                GameEvent.System(
                    "❌ You failed to harvest the resource properly.",
                    GameEvent.MessageLevel.WARNING
                )
            )
            awardFailedHarvestXp(game, skillName)
            return HarvestSupport.CheckOutcome.Failed
        }
        game.emitEvent(GameEvent.System("✅ Success!", GameEvent.MessageLevel.INFO))
        return HarvestSupport.CheckOutcome.Passed(result)
    }

    private fun emitRoll(game: EngineGameClient, statName: String, result: SkillCheckResult) {
        val rollText = buildString {
            appendLine("Rolling $statName check...")
            appendLine("d20 roll: ${result.roll} + modifier: ${result.modifier} = ${result.total} vs DC ${result.dc}")
            if (result.isCriticalSuccess) {
                appendLine("🎲 CRITICAL SUCCESS! (Natural 20)")
            } else if (result.isCriticalFailure) {
                appendLine("💀 CRITICAL FAILURE! (Natural 1)")
            }
        }
        game.emitEvent(GameEvent.System(rollText.trim(), GameEvent.MessageLevel.INFO))
    }

    private fun awardFailedHarvestXp(game: EngineGameClient, skillName: String) {
        emitXpEvents(
            game,
            skillName,
            game.skillManager.attemptSkillProgress(
                entityId = game.worldState.player.id,
                skillName = skillName,
                baseXp = HarvestSupport.failXp(),
                success = false
            ).getOrNull() ?: emptyList()
        )
    }

    private fun grantHarvestLoot(game: EngineGameClient, feature: Entity.Feature) {
        val lootTable = LootTableRegistry.getTable(feature.lootTableId!!)
        if (lootTable == null) {
            game.emitEvent(
                GameEvent.System(
                    "Error: Loot table not found for ${feature.lootTableId}",
                    GameEvent.MessageLevel.WARNING
                )
            )
            return
        }
        val instances = LootGenerator(game.itemRepository)
            .generateLoot(lootTable, LootSource.FEATURE)
            .getOrNull() ?: emptyList()
        if (instances.isEmpty()) {
            game.emitEvent(GameEvent.System("You didn't find anything useful.", GameEvent.MessageLevel.INFO))
            return
        }
        addLootInstances(game, instances)
    }

    private fun addLootInstances(game: EngineGameClient, instances: List<ItemInstance>) {
        val lines = mutableListOf<String>()
        lines.add("\nYou harvested:")
        instances.forEach { instance ->
            val templateName = game.itemRepository.findTemplateById(instance.templateId)
                .getOrNull()?.name ?: "item"
            lines.add("  - $templateName")
            addInstanceToInventory(game, instance)
            game.trackQuests(QuestAction.CollectedItem(instance.id))
        }
        game.emitEvent(GameEvent.System(lines.joinToString("\n").trim(), GameEvent.MessageLevel.INFO))
    }

    private fun addInstanceToInventory(game: EngineGameClient, instance: ItemInstance) {
        val playerInv = game.worldState.player.inventoryComponent ?: InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 0,
            capacityWeight = 50.0
        )
        val updatedInv = playerInv.copy(items = playerInv.items + instance)
        val updatedPlayer = game.worldState.player.copy(inventoryComponent = updatedInv)
        game.worldState = game.worldState.updatePlayer(updatedPlayer)
    }

    private fun awardGatheringXp(
        game: EngineGameClient,
        feature: Entity.Feature,
        skillCheckResult: SkillCheckResult
    ) {
        val skillName = feature.skillChallenge!!.statType.name
        emitXpEvents(
            game,
            skillName,
            game.skillManager.attemptSkillProgress(
                entityId = game.worldState.player.id,
                skillName = skillName,
                baseXp = HarvestSupport.successXp(),
                success = skillCheckResult.success
            ).getOrNull() ?: emptyList()
        )
    }

    private fun emitXpEvents(
        game: EngineGameClient,
        skillName: String,
        xpEvents: List<SkillEvent>
    ) {
        xpEvents.forEach { event ->
            when (event) {
                is SkillEvent.XpGained -> emitXpGained(game, skillName, event)
                is SkillEvent.LevelUp -> emitLevelUp(game, skillName, event)
                else -> {}
            }
        }
    }

    private fun emitXpGained(game: EngineGameClient, skillName: String, event: SkillEvent.XpGained) {
        game.emitEvent(
            GameEvent.System(
                HarvestSupport.xpGainedLine(skillName, event.xpAmount, event.currentXp, event.currentLevel),
                GameEvent.MessageLevel.INFO
            )
        )
    }

    private fun emitLevelUp(game: EngineGameClient, skillName: String, event: SkillEvent.LevelUp) {
        val method = if (event.oldLevel == 0) "(lucky progression)" else "(lucky level-up)"
        game.emitEvent(
            GameEvent.System(
                "🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel} $method",
                GameEvent.MessageLevel.INFO
            )
        )
    }
}
