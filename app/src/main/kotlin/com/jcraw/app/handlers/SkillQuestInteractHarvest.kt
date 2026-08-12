@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.SkillCheckResult
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.loot.LootGenerator
import com.jcraw.mud.reasoning.loot.LootSource
import com.jcraw.mud.reasoning.loot.LootTableRegistry

/**
 * Harvest validation, skill check, loot, and XP for interact cluster.
 */
object SkillQuestInteractHarvest {

    private sealed class CheckOutcome {
        data object NoChallenge : CheckOutcome()
        data object Failed : CheckOutcome()
        data class Passed(val result: SkillCheckResult) : CheckOutcome()
    }

    fun hasRequiredTool(game: MudGame, feature: Entity.Feature): Boolean {
        val requiredToolTag = feature.properties["required_tool_tag"] ?: return true
        val hasTool = game.worldState.player.inventoryComponent?.items?.any { instance ->
            val template = game.itemRepository.findTemplateById(instance.templateId).getOrNull()
            template?.tags?.contains(requiredToolTag) == true
        } ?: false
        if (!hasTool) {
            println("You need a ${requiredToolTag.replace("_", " ")} to harvest this.")
            return false
        }
        return true
    }

    fun performHarvest(game: MudGame, spaceId: String, feature: Entity.Feature) {
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

    private fun markHarvested(game: MudGame, spaceId: String, feature: Entity.Feature) {
        val updatedFeature = feature.copy(isCompleted = true)
        game.worldState = game.worldState.replaceEntityInSpace(spaceId, feature.id, updatedFeature)
            ?: game.worldState
    }

    private fun runHarvestSkillCheck(game: MudGame, feature: Entity.Feature): CheckOutcome {
        if (feature.skillChallenge == null) return CheckOutcome.NoChallenge
        val challenge = feature.skillChallenge!!
        val result = game.skillCheckResolver.checkPlayer(
            game.worldState.player,
            challenge.statType,
            challenge.difficulty
        )
        printRoll(challenge.statType.name, result)
        if (!result.success) {
            println("❌ You failed to harvest the resource properly.")
            awardFailedHarvestXp(game, challenge.statType.name)
            return CheckOutcome.Failed
        }
        println("✅ Success!")
        return CheckOutcome.Passed(result)
    }

    private fun printRoll(statName: String, result: SkillCheckResult) {
        println("Rolling $statName check...")
        println("d20 roll: ${result.roll} + modifier: ${result.modifier} = ${result.total} vs DC ${result.dc}")
        if (result.isCriticalSuccess) {
            println("🎲 CRITICAL SUCCESS! (Natural 20)")
        } else if (result.isCriticalFailure) {
            println("💀 CRITICAL FAILURE! (Natural 1)")
        }
    }

    private fun awardFailedHarvestXp(game: MudGame, skillName: String) {
        printXpEvents(
            skillName,
            game.skillManager.attemptSkillProgress(
                entityId = game.worldState.player.id,
                skillName = skillName,
                baseXp = 25L,
                success = false
            ).getOrNull() ?: emptyList(),
            showMilestone = false
        )
    }

    private fun grantHarvestLoot(game: MudGame, feature: Entity.Feature) {
        val lootTable = LootTableRegistry.getTable(feature.lootTableId!!)
        if (lootTable == null) {
            println("Error: Loot table not found for ${feature.lootTableId}")
            return
        }
        val instances = LootGenerator(game.itemRepository)
            .generateLoot(lootTable, LootSource.FEATURE)
            .getOrNull() ?: emptyList()
        if (instances.isEmpty()) {
            println("You didn't find anything useful.")
            return
        }
        addLootToInventory(game, instances)
    }

    private fun addLootToInventory(game: MudGame, instances: List<ItemInstance>) {
        println("\nYou harvested:")
        var updatedInventory = game.worldState.player.inventoryComponent
        instances.forEach { instance ->
            updatedInventory = tryAddInstance(game, updatedInventory, instance)
            game.trackQuests(QuestAction.CollectedItem(instance.id))
        }
        if (updatedInventory != game.worldState.player.inventoryComponent) {
            val updatedPlayer = game.worldState.player.copy(inventoryComponent = updatedInventory)
            game.worldState = game.worldState.updatePlayer(updatedPlayer)
        }
    }

    private fun tryAddInstance(
        game: MudGame,
        inventory: com.jcraw.mud.core.InventoryComponent,
        instance: ItemInstance
    ): com.jcraw.mud.core.InventoryComponent {
        val template = game.itemRepository.findTemplateById(instance.templateId).getOrNull()
        val templateName = template?.name ?: "item"
        if (template == null) {
            println("  - $templateName")
            return inventory
        }
        return addOrSkip(game, inventory, instance, template, templateName)
    }

    private fun addOrSkip(
        game: MudGame,
        inventory: com.jcraw.mud.core.InventoryComponent,
        instance: ItemInstance,
        template: com.jcraw.mud.core.ItemTemplate,
        templateName: String
    ): com.jcraw.mud.core.InventoryComponent {
        val templates = inventory.items.associate {
            it.templateId to game.itemRepository.findTemplateById(it.templateId).getOrNull()
        }
            .filterValues { it != null }
            .mapValues { it.value!! }
            .toMutableMap()
        templates[template.id] = template
        return if (inventory.canAdd(template, instance.quantity, templates)) {
            println("  - $templateName (added to inventory)")
            inventory.addItem(instance)
        } else {
            println("  - $templateName (too heavy to carry)")
            inventory
        }
    }

    private fun awardGatheringXp(
        game: MudGame,
        feature: Entity.Feature,
        skillCheckResult: SkillCheckResult
    ) {
        val skillName = feature.skillChallenge!!.statType.name
        printXpEvents(
            skillName,
            game.skillManager.attemptSkillProgress(
                entityId = game.worldState.player.id,
                skillName = skillName,
                baseXp = 50L,
                success = skillCheckResult.success
            ).getOrNull() ?: emptyList(),
            showMilestone = true
        )
    }

    private fun printXpEvents(
        skillName: String,
        xpEvents: List<SkillEvent>,
        showMilestone: Boolean
    ) {
        xpEvents.forEach { event ->
            when (event) {
                is SkillEvent.XpGained -> {
                    println("+${event.xpAmount} XP to $skillName (${event.currentXp} total, level ${event.currentLevel})")
                }
                is SkillEvent.LevelUp -> {
                    println("🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel}")
                    if (showMilestone && event.isAtPerkMilestone) {
                        println("⚡ Milestone reached! Use 'choose perk for $skillName' to select a perk.")
                    }
                }
                else -> {}
            }
        }
    }
}
