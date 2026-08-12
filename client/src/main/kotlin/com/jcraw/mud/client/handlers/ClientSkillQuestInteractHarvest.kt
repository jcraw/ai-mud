@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.SkillCheckResult
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.loot.LootGenerator
import com.jcraw.mud.reasoning.loot.LootSource
import com.jcraw.mud.reasoning.loot.LootTableRegistry

/**
 * Harvest validation, skill check, loot, and XP for client interact cluster.
 */
object ClientSkillQuestInteractHarvest {

    private sealed class CheckOutcome {
        data object NoChallenge : CheckOutcome()
        data object Failed : CheckOutcome()
        data class Passed(val result: SkillCheckResult) : CheckOutcome()
    }

    fun hasRequiredTool(game: EngineGameClient, feature: Entity.Feature): Boolean {
        val requiredToolTag = feature.properties["required_tool_tag"] ?: return true
        val hasTool = game.worldState.player.inventoryComponent?.items?.any { instance ->
            val template = game.itemRepository.findTemplateById(instance.templateId).getOrNull()
            template?.tags?.contains(requiredToolTag) == true
        } ?: false
        if (!hasTool) {
            game.emitEvent(GameEvent.System(
                "You need a ${requiredToolTag.replace("_", " ")} to harvest this.",
                GameEvent.MessageLevel.WARNING
            ))
            return false
        }
        return true
    }

    fun performHarvest(game: EngineGameClient, spaceId: String, feature: Entity.Feature) {
        when (val outcome = runHarvestSkillCheck(game, feature)) {
            is CheckOutcome.Failed -> return
            is CheckOutcome.NoChallenge -> {
                grantHarvestLoot(game, feature)
                markHarvested(game, spaceId, feature)
            }
            is CheckOutcome.Passed -> {
                grantHarvestLoot(game, feature)
                awardGatheringXp(game, feature, outcome.result)
                markHarvested(game, spaceId, feature)
            }
        }
    }

    private fun markHarvested(game: EngineGameClient, spaceId: String, feature: Entity.Feature) {
        val updatedFeature = feature.copy(isCompleted = true)
        game.worldState = game.worldState.replaceEntityInSpace(spaceId, feature.id, updatedFeature)
            ?: game.worldState
    }

    private fun runHarvestSkillCheck(
        game: EngineGameClient,
        feature: Entity.Feature
    ): CheckOutcome {
        if (feature.skillChallenge == null) return CheckOutcome.NoChallenge
        val challenge = feature.skillChallenge!!
        val result = game.skillCheckResolver.checkPlayer(
            game.worldState.player,
            challenge.statType,
            challenge.difficulty
        )
        emitRoll(game, challenge.statType.name, result)
        if (!result.success) {
            game.emitEvent(GameEvent.System(
                "❌ You failed to harvest the resource properly.",
                GameEvent.MessageLevel.WARNING
            ))
            awardFailedHarvestXp(game, challenge.statType.name)
            return CheckOutcome.Failed
        }
        game.emitEvent(GameEvent.System("✅ Success!", GameEvent.MessageLevel.INFO))
        return CheckOutcome.Passed(result)
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
                baseXp = 25L,
                success = false
            ).getOrNull() ?: emptyList()
        )
    }

    private fun grantHarvestLoot(game: EngineGameClient, feature: Entity.Feature) {
        val lootTable = LootTableRegistry.getTable(feature.lootTableId!!)
        if (lootTable == null) {
            game.emitEvent(GameEvent.System(
                "Error: Loot table not found for ${feature.lootTableId}",
                GameEvent.MessageLevel.WARNING
            ))
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
                baseXp = 50L,
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
                is SkillEvent.XpGained -> {
                    game.emitEvent(GameEvent.System(
                        "+${event.xpAmount} XP to $skillName (${event.currentXp} total, level ${event.currentLevel})",
                        GameEvent.MessageLevel.INFO
                    ))
                }
                is SkillEvent.LevelUp -> {
                    val method = if (event.oldLevel == 0) "(lucky progression)" else "(lucky level-up)"
                    game.emitEvent(GameEvent.System(
                        "🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel} $method",
                        GameEvent.MessageLevel.INFO
                    ))
                }
                else -> {}
            }
        }
    }
}
