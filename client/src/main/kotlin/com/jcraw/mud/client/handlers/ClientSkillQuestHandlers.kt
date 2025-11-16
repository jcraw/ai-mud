package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.*

/**
 * Handles skills, quests, persistence, and meta-commands in the GUI client.
 */
object ClientSkillQuestHandlers {

    fun handleUseSkill(game: EngineGameClient, skill: String?, action: String) {
        // Determine skill name from explicit parameter or action
        val skillName = skill ?: inferSkillFromAction(action)
        if (skillName == null) {
            game.emitEvent(GameEvent.System("Could not determine which skill to use for: $action", GameEvent.MessageLevel.WARNING))
            return
        }

        // Check if skill exists
        val skillDef = com.jcraw.mud.reasoning.skill.SkillDefinitions.getSkill(skillName)
        if (skillDef == null) {
            game.emitEvent(GameEvent.System("Unknown skill: $skillName", GameEvent.MessageLevel.WARNING))
            return
        }

        // Perform skill check (default difficulty: Medium = 15)
        val difficulty = 15
        val checkResult = game.skillManager.checkSkill(
            entityId = game.worldState.player.id,
            skillName = skillName,
            difficulty = difficulty
        ).getOrElse { error ->
            game.emitEvent(GameEvent.System("Skill check failed: ${error.message}", GameEvent.MessageLevel.ERROR))
            return
        }

        // Grant XP based on success/failure (base 50 XP)
        val baseXp = 50L
        val xpEvents = game.skillManager.grantXp(
            entityId = game.worldState.player.id,
            skillName = skillName,
            baseXp = baseXp,
            success = checkResult.success
        ).getOrElse { error ->
            // Skill not unlocked
            val message = "You attempt to $action with $skillName, but the skill is not unlocked.\n" +
                "Try 'train $skillName with <npc>' or use it repeatedly to unlock it."
            game.emitEvent(GameEvent.System(message, GameEvent.MessageLevel.INFO))
            return
        }

        // Format output with roll details and XP
        val output = buildString {
            appendLine("You attempt to $action using $skillName:")
            appendLine()
            val total = checkResult.roll + checkResult.skillLevel
            appendLine("Roll: d20(${checkResult.roll}) + Level(${checkResult.skillLevel}) = $total vs DC $difficulty")
            appendLine(checkResult.narrative)
            appendLine()

            // XP and level-up messages
            xpEvents.forEach { event ->
                when (event) {
                    is com.jcraw.mud.core.SkillEvent.XpGained -> {
                        appendLine("+${event.xpAmount} XP to $skillName (${event.currentXp} total, level ${event.currentLevel})")
                    }
                    is com.jcraw.mud.core.SkillEvent.LevelUp -> {
                        appendLine()
                        appendLine("🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel}")
                        if (event.isAtPerkMilestone) {
                            appendLine("⚡ Milestone reached! Use 'choose perk for $skillName' to select a perk.")
                        }
                    }
                    else -> {}
                }
            }
        }

        game.emitEvent(GameEvent.Narrative(output))
    }

    /**
     * Infer skill name from action description
     * Maps common actions to skills
     */
    fun inferSkillFromAction(action: String): String? {
        val lower = action.lowercase()
        return when {
            lower.contains("fire") || lower.contains("fireball") || lower.contains("burn") -> "Fire Magic"
            lower.contains("water") || lower.contains("ice") || lower.contains("freeze") -> "Water Magic"
            lower.contains("earth") || lower.contains("stone") || lower.contains("rock") -> "Earth Magic"
            lower.contains("air") || lower.contains("wind") || lower.contains("lightning") -> "Air Magic"
            lower.contains("sneak") || lower.contains("hide") || lower.contains("stealth") -> "Stealth"
            lower.contains("pick") && lower.contains("lock") -> "Lockpicking"
            lower.contains("disarm") && lower.contains("trap") -> "Trap Disarm"
            lower.contains("set") && lower.contains("trap") -> "Trap Setting"
            lower.contains("backstab") || lower.contains("sneak attack") -> "Backstab"
            lower.contains("persuade") || lower.contains("negotiate") -> "Diplomacy"
            lower.contains("intimidate") || lower.contains("threaten") -> "Charisma"
            lower.contains("sword") -> "Sword Fighting"
            lower.contains("axe") -> "Axe Mastery"
            lower.contains("bow") || lower.contains("arrow") -> "Bow Accuracy"
            lower.contains("blacksmith") || lower.contains("forge") || lower.contains("craft") -> "Blacksmithing"
            else -> null
        }
    }

    fun handleTrainSkill(game: EngineGameClient, skill: String, method: String) {
        // Get entities in current space
        val entitiesInSpace = game.worldState.getEntitiesInSpace(game.worldState.player.currentRoomId)

        // Parse NPC name from method string (e.g., "with the knight" → "knight")
        val npcName = method.lowercase()
            .removePrefix("with ")
            .removePrefix("the ")
            .removePrefix("at ")
            .removePrefix("from ")
            .trim()

        if (npcName.isBlank()) {
            game.emitEvent(GameEvent.System("Train with whom? Use 'train <skill> with <npc>'.", GameEvent.MessageLevel.WARNING))
            return
        }

        // Find NPC in space
        val npc = entitiesInSpace.filterIsInstance<Entity.NPC>()
            .find {
                it.name.lowercase().contains(npcName) ||
                it.id.lowercase().contains(npcName)
            }

        if (npc == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name to train with.", GameEvent.MessageLevel.WARNING))
            return
        }

        // Attempt training via DispositionManager
        val trainingResult = game.dispositionManager.trainSkillWithNPC(
            game.worldState.player.id,
            npc,
            skill
        )

        trainingResult.onSuccess { message ->
            game.emitEvent(GameEvent.Narrative(message))

            // Update world state with any NPC changes (disposition) using space-based update
            game.worldState = game.worldState.replaceEntityInSpace(game.worldState.player.currentRoomId, npc.id, npc) ?: game.worldState
        }.onFailure { error ->
            game.emitEvent(GameEvent.System(error.message ?: "Training failed", GameEvent.MessageLevel.ERROR))
        }
    }

    fun handleChoosePerk(game: EngineGameClient, skillName: String, choice: Int) {
        // Get skill component to check skill state
        val component = game.skillManager.getSkillComponent(game.worldState.player.id)
        val skillState = component.getSkill(skillName)

        if (skillState == null) {
            game.emitEvent(GameEvent.System("You don't have the skill '$skillName'. Train it first!", GameEvent.MessageLevel.WARNING))
            return
        }

        // Get available perk choices at current level
        val availablePerks = game.perkSelector.getPerkChoices(skillName, skillState.level)

        if (availablePerks.isEmpty()) {
            game.emitEvent(GameEvent.System("No perk choices available for $skillName at level ${skillState.level}.", GameEvent.MessageLevel.INFO))
            return
        }

        // Validate choice (1-based index)
        if (choice < 1 || choice > availablePerks.size) {
            game.emitEvent(GameEvent.System("Invalid choice. Please choose a number between 1 and ${availablePerks.size}.", GameEvent.MessageLevel.WARNING))
            return
        }

        // Convert to 0-based index and get chosen perk
        val chosenPerk = availablePerks[choice - 1]

        // Attempt to select the perk
        val event = game.perkSelector.selectPerk(game.worldState.player.id, skillName, chosenPerk)

        if (event != null) {
            val message = com.jcraw.mud.action.SkillFormatter.formatPerkUnlocked(chosenPerk.name, skillName)
            game.emitEvent(GameEvent.Narrative(message))
        } else {
            game.emitEvent(GameEvent.System("Failed to unlock perk. You may not have a pending perk choice for this skill.", GameEvent.MessageLevel.ERROR))
        }
    }

    fun handleViewSkills(game: EngineGameClient) {
        val component = game.skillManager.getSkillComponent(game.worldState.player.id)
        val formattedSkillSheet = com.jcraw.mud.action.SkillFormatter.formatSkillSheet(component)
        game.emitEvent(GameEvent.Narrative(formattedSkillSheet))
    }

    fun handleSave(game: EngineGameClient, saveName: String) {
        val result = game.persistenceManager.saveGame(game.worldState, saveName)

        result.onSuccess {
            game.emitEvent(GameEvent.System("💾 Game saved as '$saveName'", GameEvent.MessageLevel.INFO))
        }.onFailure { error ->
            game.emitEvent(GameEvent.System("❌ Failed to save game: ${error.message}", GameEvent.MessageLevel.ERROR))
        }
    }

    fun handleLoad(game: EngineGameClient, saveName: String) {
        val result = game.persistenceManager.loadGame(saveName)

        result.onSuccess { loadedState ->
            game.worldState = loadedState
            game.emitEvent(GameEvent.System("📂 Game loaded from '$saveName'", GameEvent.MessageLevel.INFO))
            game.describeCurrentRoom()
        }.onFailure { error ->
            game.emitEvent(GameEvent.System("❌ Failed to load game: ${error.message}", GameEvent.MessageLevel.ERROR))

            val saves = game.persistenceManager.listSaves()
            if (saves.isNotEmpty()) {
                game.emitEvent(GameEvent.System("Available saves: ${saves.joinToString(", ")}", GameEvent.MessageLevel.INFO))
            } else {
                game.emitEvent(GameEvent.System("No saved games found.", GameEvent.MessageLevel.INFO))
            }
        }
    }

    fun handleHelp(game: EngineGameClient) {
        val helpText = """
            |Available Commands:
            |  Movement: north, south, east, west (or n, s, e, w)
            |  Actions: look [target], take <item>, drop <item>, talk <npc>
            |  Combat: attack <npc>
            |  Equipment: equip <item>, use <item>
            |  Skills: check <feature>, persuade <npc>, intimidate <npc>
            |  Quests: quests, accept <id>, claim <id>
            |  Meta: inventory/i, save [name], load [name], help, quit
        """.trimMargin()

        game.emitEvent(GameEvent.System(helpText, GameEvent.MessageLevel.INFO))
    }

    fun handleQuests(game: EngineGameClient) {
        val player = game.worldState.player

        val text = buildString {
            appendLine("\n═══════ QUEST LOG ═══════")
            appendLine("Experience: ${player.experiencePoints} | Gold: ${player.gold}")
            appendLine()

            if (player.activeQuests.isEmpty()) {
                appendLine("No active quests.")
            } else {
                appendLine("Active Quests:")
                player.activeQuests.forEachIndexed { index, quest ->
                    val statusIcon = when (quest.status) {
                        QuestStatus.ACTIVE -> if (quest.isComplete()) "✓" else "○"
                        QuestStatus.COMPLETED -> "✓"
                        QuestStatus.CLAIMED -> "★"
                        QuestStatus.FAILED -> "✗"
                    }
                    appendLine("\n${index + 1}. $statusIcon ${quest.title}")
                    appendLine("   ${quest.description}")
                    appendLine("   Progress: ${quest.getProgressSummary()}")

                    quest.objectives.forEach { obj ->
                        val checkmark = if (obj.isCompleted) "✓" else "○"
                        appendLine("     $checkmark ${obj.description}")
                    }

                    if (quest.status == QuestStatus.COMPLETED) {
                        appendLine("   ⚠ Ready to claim reward! Use 'claim ${quest.id}'")
                    }
                }
            }

            appendLine()
            if (game.worldState.availableQuests.isNotEmpty()) {
                appendLine("Available Quests (use 'accept <id>' to accept):")
                game.worldState.availableQuests.forEach { quest ->
                    appendLine("  - ${quest.id}: ${quest.title}")
                }
            }
            appendLine("═".repeat(26))
        }

        game.emitEvent(GameEvent.Quest(text))
    }

    fun handleAcceptQuest(game: EngineGameClient, questId: String?) {
        if (questId == null) {
            if (game.worldState.availableQuests.isEmpty()) {
                game.emitEvent(GameEvent.System("No quests available to accept.", GameEvent.MessageLevel.INFO))
            } else {
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
            return
        }

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
        val quest = game.worldState.player.getQuest(questId)
        if (quest == null) {
            game.emitEvent(GameEvent.System("You don't have a quest with ID '$questId'.", GameEvent.MessageLevel.WARNING))
            return
        }

        if (!quest.isComplete()) {
            game.emitEvent(GameEvent.System("Quest '${quest.title}' is not complete yet!\nProgress: ${quest.getProgressSummary()}", GameEvent.MessageLevel.WARNING))
            return
        }

        if (quest.status == QuestStatus.CLAIMED) {
            game.emitEvent(GameEvent.System("You've already claimed the reward for this quest!", GameEvent.MessageLevel.WARNING))
            return
        }

        game.worldState = game.worldState.updatePlayer(game.worldState.player.claimQuestReward(questId))

        val text = buildString {
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

        game.emitEvent(GameEvent.Quest(text, questId))

        // Update status
        game.emitEvent(GameEvent.StatusUpdate(
            hp = game.worldState.player.health,
            maxHp = game.worldState.player.maxHealth
        ))
    }

    /**
     * Handle interaction with objects (features, containers, harvestable resources)
     * Supports skill checks, tool requirements, and XP rewards for gathering
     */
    fun handleInteract(game: EngineGameClient, target: String) {
        // Use entity storage
        val spaceId = game.worldState.player.currentRoomId

        // Normalize target for matching
        val normalizedTarget = target.lowercase().replace("_", " ")

        // Find the feature
        val feature = game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.Feature>()
            .find { entity ->
                val normalizedName = entity.name.lowercase()
                val normalizedId = entity.id.lowercase().replace("_", " ")

                normalizedName.contains(normalizedTarget) ||
                normalizedId.contains(normalizedTarget) ||
                normalizedTarget.contains(normalizedName) ||
                normalizedTarget.contains(normalizedId) ||
                normalizedTarget.split(" ").all { word ->
                    normalizedName.contains(word) || normalizedId.contains(word)
                }
            }

        if (feature == null) {
            game.emitEvent(GameEvent.System("You don't see that here.", GameEvent.MessageLevel.WARNING))
            return
        }

        // Check if feature is harvestable (has lootTableId)
        if (feature.lootTableId == null) {
            game.emitEvent(GameEvent.System("There's nothing to harvest from that.", GameEvent.MessageLevel.INFO))
            return
        }

        // Check if already completed (harvested)
        if (feature.isCompleted) {
            game.emitEvent(GameEvent.System("This resource has already been harvested.", GameEvent.MessageLevel.WARNING))
            return
        }

        // Check tool requirement (if specified in properties)
        val requiredToolTag = feature.properties["required_tool_tag"]
        if (requiredToolTag != null) {
            // TODO: Check player's InventoryComponent for tool with matching tag
            // For now, assume player has the tool (will be implemented with InventoryComponent)
        }

        game.emitEvent(GameEvent.System("\nYou attempt to harvest ${feature.name}...", GameEvent.MessageLevel.INFO))

        // Perform skill check if specified
        var harvestSuccess = true
        var skillCheckResult: com.jcraw.mud.core.SkillCheckResult? = null

        if (feature.skillChallenge != null) {
            val challenge = feature.skillChallenge!!
            val result = game.skillCheckResolver.checkPlayer(
                game.worldState.player,
                challenge.statType,
                challenge.difficulty
            )
            skillCheckResult = result

            // Display roll details
            val rollText = buildString {
                appendLine("Rolling ${challenge.statType.name} check...")
                appendLine("d20 roll: ${result.roll} + modifier: ${result.modifier} = ${result.total} vs DC ${result.dc}")

                if (result.isCriticalSuccess) {
                    appendLine("🎲 CRITICAL SUCCESS! (Natural 20)")
                } else if (result.isCriticalFailure) {
                    appendLine("💀 CRITICAL FAILURE! (Natural 1)")
                }
            }
            game.emitEvent(GameEvent.System(rollText.trim(), GameEvent.MessageLevel.INFO))

            harvestSuccess = result.success

            if (!result.success) {
                game.emitEvent(GameEvent.System("❌ You failed to harvest the resource properly.", GameEvent.MessageLevel.WARNING))

                // Award 20% XP on failure
                val skillName = challenge.statType.name
                val baseXp = 25L
                val xpEvents = game.skillManager.grantXp(
                    entityId = game.worldState.player.id,
                    skillName = skillName,
                    baseXp = baseXp,
                    success = false
                ).getOrNull() ?: emptyList()

                xpEvents.forEach { event ->
                    when (event) {
                        is com.jcraw.mud.core.SkillEvent.XpGained -> {
                            game.emitEvent(GameEvent.System(
                                "+${event.xpAmount} XP to $skillName (${event.currentXp} total, level ${event.currentLevel})",
                                GameEvent.MessageLevel.INFO
                            ))
                        }
                        is com.jcraw.mud.core.SkillEvent.LevelUp -> {
                            game.emitEvent(GameEvent.System(
                                "🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel}",
                                GameEvent.MessageLevel.INFO
                            ))
                        }
                        else -> {}
                    }
                }

                return
            }

            game.emitEvent(GameEvent.System("✅ Success!", GameEvent.MessageLevel.INFO))
        }

        // Generate loot using LootGenerator
        val lootGenerator = com.jcraw.mud.reasoning.loot.LootGenerator(game.itemRepository)

        // Look up loot table from registry
        val lootTable = com.jcraw.mud.reasoning.loot.LootTableRegistry.getTable(feature.lootTableId!!)
        if (lootTable == null) {
            game.emitEvent(GameEvent.System("Error: Loot table not found for ${feature.lootTableId}", GameEvent.MessageLevel.WARNING))
            return
        }

        val instancesResult = lootGenerator.generateLoot(
            lootTable,
            com.jcraw.mud.reasoning.loot.LootSource.FEATURE
        )

        val instances = instancesResult.getOrNull() ?: emptyList()

        if (instances.isEmpty()) {
            game.emitEvent(GameEvent.System("You didn't find anything useful.", GameEvent.MessageLevel.INFO))
        } else {
            val harvestText = buildString {
                appendLine("\nYou harvested:")
                instances.forEach { instance ->
                    val templateResult = game.itemRepository.findTemplateById(instance.templateId)
                    val templateName = templateResult.getOrNull()?.name ?: "item"
                    appendLine("  - $templateName")

                    // Add to player inventory
                    val playerInv = game.worldState.player.inventoryComponent ?: com.jcraw.mud.core.InventoryComponent(
                        items = emptyList(),
                        equipped = emptyMap(),
                        gold = 0,
                        capacityWeight = 50.0
                    )

                    val updatedInv = playerInv.copy(items = playerInv.items + instance)
                    val updatedPlayer = game.worldState.player.copy(inventoryComponent = updatedInv)
                    game.worldState = game.worldState.updatePlayer(updatedPlayer)

                    // Track for quests
                    game.trackQuests(com.jcraw.mud.reasoning.QuestAction.CollectedItem(instance.id))
                }
            }
            game.emitEvent(GameEvent.System(harvestText.trim(), GameEvent.MessageLevel.INFO))
        }

        // Award XP for gathering skill based on feature's skill requirement
        if (feature.skillChallenge != null && skillCheckResult != null) {
            val skillName = feature.skillChallenge!!.statType.name
            val baseXp = 50L

            val xpEvents = game.skillManager.grantXp(
                entityId = game.worldState.player.id,
                skillName = skillName,
                baseXp = baseXp,
                success = true
            ).getOrNull() ?: emptyList()

            xpEvents.forEach { event ->
                when (event) {
                    is com.jcraw.mud.core.SkillEvent.XpGained -> {
                        game.emitEvent(GameEvent.System(
                            "+${event.xpAmount} XP to $skillName (${event.currentXp} total, level ${event.currentLevel})",
                            GameEvent.MessageLevel.INFO
                        ))
                    }
                    is com.jcraw.mud.core.SkillEvent.LevelUp -> {
                        game.emitEvent(GameEvent.System(
                            "🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel}",
                            GameEvent.MessageLevel.INFO
                        ))
                    }
                    else -> {}
                }
            }
        }

        // Mark feature as completed
        val updatedFeature = feature.copy(isCompleted = true)
        game.worldState = game.worldState.replaceEntityInSpace(spaceId, feature.id, updatedFeature) ?: game.worldState
    }

    /**
     * Handle crafting items from recipes
     */
    fun handleCraft(game: EngineGameClient, target: String) {
        val craftingManager = com.jcraw.mud.reasoning.crafting.CraftingManager(
            game.recipeRepository,
            game.itemRepository
        )

        // Find the recipe
        val recipeResult = craftingManager.findRecipe(target)
        if (recipeResult.isFailure) {
            game.emitEvent(GameEvent.System(
                "Failed to find recipe: ${recipeResult.exceptionOrNull()?.message}",
                GameEvent.MessageLevel.WARNING
            ))
            return
        }

        val recipe = recipeResult.getOrNull()
        if (recipe == null) {
            game.emitEvent(GameEvent.System(
                "No recipe found for '$target'.\nTip: Use 'craft' alone to see available recipes.",
                GameEvent.MessageLevel.WARNING
            ))
            return
        }

        // Get player's inventory and skill component
        val playerInventory = game.worldState.player.inventoryComponent
        if (playerInventory == null) {
            game.emitEvent(GameEvent.System("Inventory system not available.", GameEvent.MessageLevel.WARNING))
            return
        }

        // Get player's skill component from skill manager
        val skillComponent = game.skillManager.getSkillComponent(game.worldState.player.id)

        // Try to craft (note: craft() mutates inventory directly)
        val result = craftingManager.craft(skillComponent, playerInventory, recipe)

        when (result) {
            is com.jcraw.mud.reasoning.crafting.CraftingManager.CraftResult.Success -> {
                // craft() already mutated inventory, just update the player state
                val updatedPlayer = game.worldState.player.copy(inventoryComponent = playerInventory)
                game.worldState = game.worldState.updatePlayer(updatedPlayer)

                // Show crafting result
                game.emitEvent(GameEvent.System("✨ ${result.message}", GameEvent.MessageLevel.INFO))

                // Track for quests (use CollectedItem since crafted items count as collected)
                game.trackQuests(com.jcraw.mud.reasoning.QuestAction.CollectedItem(result.craftedItem.id))
            }
            is com.jcraw.mud.reasoning.crafting.CraftingManager.CraftResult.Failure -> {
                // craft() already mutated inventory (consumed some materials)
                val updatedPlayer = game.worldState.player.copy(inventoryComponent = playerInventory)
                game.worldState = game.worldState.updatePlayer(updatedPlayer)

                game.emitEvent(GameEvent.System(result.message, GameEvent.MessageLevel.WARNING))
            }
            is com.jcraw.mud.reasoning.crafting.CraftingManager.CraftResult.Invalid -> {
                game.emitEvent(GameEvent.System(result.message, GameEvent.MessageLevel.WARNING))
            }
        }
    }

    fun handleQuit(game: EngineGameClient) {
        game.emitEvent(GameEvent.System("Goodbye!", GameEvent.MessageLevel.INFO))
        game.running = false
    }
}
