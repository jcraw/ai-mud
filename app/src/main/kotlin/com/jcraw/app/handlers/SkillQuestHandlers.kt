package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.app.times
import com.jcraw.mud.core.Entity
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.action.SkillFormatter

/**
 * Handlers for skill system, quest management, persistence, and meta-commands.
 * Includes skill checks, training, perks, quest tracking, save/load, help, and quit.
 */
object SkillQuestHandlers {
    /**
     * Handle interaction with objects (features, containers, harvestable resources)
     * Supports skill checks, tool requirements, and XP rewards for gathering
     */
    fun handleInteract(game: MudGame, target: String) {
        // V3: Use entity storage
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
            println("You don't see that here.")
            return
        }

        // Check if feature is harvestable (has lootTableId)
        if (feature.lootTableId == null) {
            println("There's nothing to harvest from that.")
            return
        }

        // Check if already completed (harvested)
        if (feature.isCompleted) {
            println("This resource has already been harvested.")
            return
        }

        // Check tool requirement (if specified in properties)
        val requiredToolTag = feature.properties["required_tool_tag"]
        if (requiredToolTag != null) {
            // TODO: Check player's InventoryComponent for tool with matching tag
            // For now, assume player has the tool (will be implemented with InventoryComponent)
            // val hasTool = player.getComponent<InventoryComponent>()?.items
            //     ?.any { item -> itemTemplate.tags.contains(requiredToolTag) } ?: false
            // if (!hasTool) {
            //     println("You need a ${requiredToolTag.replace("_", " ")} to harvest this.")
            //     return
            // }
        }

        println("\nYou attempt to harvest ${feature.name}...")

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
            println("Rolling ${challenge.statType.name} check...")
            println("d20 roll: ${result.roll} + modifier: ${result.modifier} = ${result.total} vs DC ${result.dc}")

            if (result.isCriticalSuccess) {
                println("🎲 CRITICAL SUCCESS! (Natural 20)")
            } else if (result.isCriticalFailure) {
                println("💀 CRITICAL FAILURE! (Natural 1)")
            }

            harvestSuccess = result.success

            if (!result.success) {
                println("❌ You failed to harvest the resource properly.")

                // Attempt skill progression on failure (dual-path: lucky chance OR XP)
                val skillName = challenge.statType.name
                val baseXp = 25L // Lower than success
                val xpEvents = game.skillManager.attemptSkillProgress(
                    entityId = game.worldState.player.id,
                    skillName = skillName,
                    baseXp = baseXp,
                    success = false
                ).getOrNull() ?: emptyList()

                xpEvents.forEach { event ->
                    when (event) {
                        is com.jcraw.mud.core.SkillEvent.XpGained -> {
                            println("+${event.xpAmount} XP to $skillName (${event.currentXp} total, level ${event.currentLevel})")
                        }
                        is com.jcraw.mud.core.SkillEvent.LevelUp -> {
                            println("🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel}")
                        }
                        else -> {}
                    }
                }

                return
            }

            println("✅ Success!")
        }

        // Generate loot using LootGenerator
        val lootGenerator = com.jcraw.mud.reasoning.loot.LootGenerator(game.itemRepository)

        // Look up loot table from registry
        val lootTable = com.jcraw.mud.reasoning.loot.LootTableRegistry.getTable(feature.lootTableId!!)
        if (lootTable == null) {
            println("Error: Loot table not found for ${feature.lootTableId}")
            return
        }

        val instancesResult = lootGenerator.generateLoot(
            lootTable,
            com.jcraw.mud.reasoning.loot.LootSource.FEATURE
        )

        val instances = instancesResult.getOrNull() ?: emptyList()

        if (instances.isEmpty()) {
            println("You didn't find anything useful.")
        } else {
            println("\nYou harvested:")
            instances.forEach { instance ->
                val templateResult = game.itemRepository.findTemplateById(instance.templateId)
                val templateName = templateResult.getOrNull()?.name ?: "item"
                println("  - $templateName")

                // TODO: Add items to player inventory when InventoryComponent is integrated
                // For now, just track for quests
                game.trackQuests(QuestAction.CollectedItem(instance.id))
            }
        }

        // Attempt skill progression for gathering (dual-path: lucky chance OR XP)
        if (feature.skillChallenge != null && skillCheckResult != null) {
            val skillName = feature.skillChallenge!!.statType.name
            val baseXp = 50L // Full XP for successful harvest
            val xpEvents = game.skillManager.attemptSkillProgress(
                entityId = game.worldState.player.id,
                skillName = skillName,
                baseXp = baseXp,
                success = skillCheckResult.success
            ).getOrNull() ?: emptyList()

            xpEvents.forEach { event ->
                when (event) {
                    is com.jcraw.mud.core.SkillEvent.XpGained -> {
                        println("+${event.xpAmount} XP to $skillName (${event.currentXp} total, level ${event.currentLevel})")
                    }
                    is com.jcraw.mud.core.SkillEvent.LevelUp -> {
                        println("🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel}")
                        if (event.isAtPerkMilestone) {
                            println("⚡ Milestone reached! Use 'choose perk for $skillName' to select a perk.")
                        }
                    }
                    else -> {}
                }
            }
        }

        // Mark feature as completed (harvested)
        val updatedFeature = feature.copy(isCompleted = true)
        game.worldState = game.worldState.replaceEntityInSpace(spaceId, feature.id, updatedFeature) ?: game.worldState
    }

    /**
     * Handle skill check on interactive features (D&D-style d20 rolls)
     */
    fun handleCheck(game: MudGame, target: String) {
        // V3: Use entity storage
        val spaceId = game.worldState.player.currentRoomId

        // Normalize target for matching (replace underscores with spaces)
        val normalizedTarget = target.lowercase().replace("_", " ")

        // Find the feature with flexible matching
        val feature = game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.Feature>()
            .find { entity ->
                val normalizedName = entity.name.lowercase()
                val normalizedId = entity.id.lowercase().replace("_", " ")

                // Check if target matches name or ID (with underscore normalization)
                normalizedName.contains(normalizedTarget) ||
                normalizedId.contains(normalizedTarget) ||
                normalizedTarget.contains(normalizedName) ||
                normalizedTarget.contains(normalizedId) ||
                // Also check if all words in target appear in name/id (any order)
                normalizedTarget.split(" ").all { word ->
                    normalizedName.contains(word) || normalizedId.contains(word)
                }
            }

        if (feature == null) {
            println("You don't see that here.")
            return
        }

        val challenge = feature.skillChallenge
        if (!feature.isInteractable || challenge == null) {
            println("There's nothing to check about that.")
            return
        }

        if (feature.isCompleted) {
            println("You've already successfully interacted with that.")
            return
        }

        println("\n${challenge.description}")

        // Perform the skill check
        val result = game.skillCheckResolver.checkPlayer(
            game.worldState.player,
            challenge.statType,
            challenge.difficulty
        )

        // Display roll details
        println("\nRolling ${challenge.statType.name} check...")
        println("d20 roll: ${result.roll} + modifier: ${result.modifier} = ${result.total} vs DC ${result.dc}")

        // Display result
        if (result.isCriticalSuccess) {
            println("\n🎲 CRITICAL SUCCESS! (Natural 20)")
        } else if (result.isCriticalFailure) {
            println("\n💀 CRITICAL FAILURE! (Natural 1)")
        }

        if (result.success) {
            println("\n✅ Success!")
            println(challenge.successDescription)

            // Mark feature as completed
            val updatedFeature = feature.copy(isCompleted = true)
            game.worldState = game.worldState.replaceEntityInSpace(spaceId, feature.id, updatedFeature) ?: game.worldState

            // Track skill check for quests
            game.trackQuests(QuestAction.UsedSkill(feature.id))
        } else {
            println("\n❌ Failure!")
            println(challenge.failureDescription)
        }
    }

    /**
     * Handle using a skill with the V2 skill system
     */
    fun handleUseSkill(game: MudGame, skill: String?, action: String) {
        // Determine skill name from explicit parameter or action
        val skillName = skill ?: inferSkillFromAction(action)
        if (skillName == null) {
            println("\nCould not determine which skill to use for: $action")
            return
        }

        // Check if skill exists
        val skillDef = com.jcraw.mud.reasoning.skill.SkillDefinitions.getSkill(skillName)
        if (skillDef == null) {
            println("\nUnknown skill: $skillName")
            return
        }

        // Perform skill check (default difficulty: Medium = 15)
        val difficulty = 15
        val checkResult = game.skillManager.checkSkill(
            entityId = game.worldState.player.id,
            skillName = skillName,
            difficulty = difficulty
        ).getOrElse { error ->
            println("\nSkill check failed: ${error.message}")
            return
        }

        // Attempt skill progression (dual-path: lucky chance OR XP)
        val baseXp = 50L
        val xpEvents = game.skillManager.attemptSkillProgress(
            entityId = game.worldState.player.id,
            skillName = skillName,
            baseXp = baseXp,
            success = checkResult.success
        ).getOrElse { error ->
            // Skill not unlocked
            println("\nYou attempt to $action with $skillName, but the skill is not unlocked.")
            println("Try 'train $skillName with <npc>' or use it repeatedly to unlock it.")
            return
        }

        // Format output with roll details and XP
        println("\nYou attempt to $action using $skillName:")
        println()
        val total = checkResult.roll + checkResult.skillLevel
        println("Roll: d20(${checkResult.roll}) + Level(${checkResult.skillLevel}) = $total vs DC $difficulty")
        println(checkResult.narrative)
        println()

        // XP and level-up messages
        xpEvents.forEach { event ->
            when (event) {
                is com.jcraw.mud.core.SkillEvent.XpGained -> {
                    println("+${event.xpAmount} XP to $skillName (${event.currentXp} total, level ${event.currentLevel})")
                }
                is com.jcraw.mud.core.SkillEvent.LevelUp -> {
                    println()
                    println("🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel}")
                    if (event.isAtPerkMilestone) {
                        println("⚡ Milestone reached! Use 'choose perk for $skillName' to select a perk.")
                    }
                }
                else -> {}
            }
        }
    }

    /**
     * Handle training a skill with an NPC
     */
    fun handleTrainSkill(game: MudGame, skill: String, method: String) {
        // V3: Use entity storage
        val spaceId = game.worldState.player.currentRoomId

        // Parse NPC name from method string (e.g., "with the knight" → "knight")
        val npcName = method.lowercase()
            .removePrefix("with ")
            .removePrefix("the ")
            .removePrefix("at ")
            .removePrefix("from ")
            .trim()

        if (npcName.isBlank()) {
            println("\nTrain with whom? Use 'train <skill> with <npc>'.")
            return
        }

        // Find NPC
        val npc = game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.NPC>()
            .find {
                it.name.lowercase().contains(npcName) ||
                it.id.lowercase().contains(npcName)
            }

        if (npc == null) {
            println("\nThere's no one here by that name to train with.")
            return
        }

        // Attempt training via DispositionManager
        val trainingResult = game.dispositionManager.trainSkillWithNPC(
            game.worldState.player.id,
            npc,
            skill
        )

        trainingResult.onSuccess { message ->
            println("\n$message")

            // Update world state with any NPC changes (disposition)
            game.worldState = game.worldState.replaceEntityInSpace(spaceId, npc.id, npc) ?: game.worldState
        }.onFailure { error ->
            println("\n${error.message}")
        }
    }

    /**
     * Handle crafting an item using a recipe
     */
    fun handleCraft(game: MudGame, target: String) {
        val craftingManager = com.jcraw.mud.reasoning.crafting.CraftingManager(
            game.recipeRepository,
            game.itemRepository
        )

        // Find the recipe
        val recipeResult = craftingManager.findRecipe(target)
        if (recipeResult.isFailure) {
            println("Failed to find recipe: ${recipeResult.exceptionOrNull()?.message}")
            return
        }

        val recipe = recipeResult.getOrNull()
        if (recipe == null) {
            println("No recipe found for '$target'.")
            println("Tip: Use 'craft' alone to see available recipes.")
            return
        }

        println("\nAttempting to craft: ${recipe.name}")
        println("Required skill: ${recipe.requiredSkill} (level ${recipe.minSkillLevel})")
        println("Difficulty: DC ${recipe.difficulty}")
        println()

        // TODO: Get SkillComponent and InventoryComponent for player when fully integrated
        println("❌ Crafting requires InventoryComponent integration (coming soon!)")

        /* TODO: Uncomment when InventoryComponent is integrated
        val result = craftingManager.craft(skillComponent, inventoryComponent, recipe)

        when (result) {
            is com.jcraw.mud.reasoning.crafting.CraftingManager.CraftResult.Success -> {
                println("✅ ${result.message}")

                // TODO: Add crafted item to player inventory when InventoryComponent is integrated
                // For now, just track for quests
                game.trackQuests(QuestAction.CollectedItem(result.craftedItem.id))

                // Attempt skill progression for crafting (dual-path: lucky chance OR XP)
                val baseXp = 50L + (recipe.difficulty * 5L) // Scale with difficulty
                val xpEvents = game.skillManager.attemptSkillProgress(
                    entityId = game.worldState.player.id,
                    skillName = recipe.requiredSkill,
                    baseXp = baseXp,
                    success = true
                ).getOrNull() ?: emptyList()

                xpEvents.forEach { event ->
                    when (event) {
                        is com.jcraw.mud.core.SkillEvent.XpGained -> {
                            println("+${event.xpAmount} XP to ${recipe.requiredSkill} (${event.currentXp} total, level ${event.currentLevel})")
                        }
                        is com.jcraw.mud.core.SkillEvent.LevelUp -> {
                            println("🎉 ${recipe.requiredSkill} leveled up! ${event.oldLevel} → ${event.newLevel}")
                            if (event.isAtPerkMilestone) {
                                println("⚡ Milestone reached! Use 'choose perk for ${recipe.requiredSkill}' to select a perk.")
                            }
                        }
                        else -> {}
                    }
                }
            }
            is com.jcraw.mud.reasoning.crafting.CraftingManager.CraftResult.Failure -> {
                println("❌ ${result.message}")
                if (result.inputsLost.isNotEmpty()) {
                    println("Materials lost:")
                    result.inputsLost.forEach { (templateId, qty) ->
                        val templateResult = game.itemRepository.findTemplateById(templateId)
                        val templateName = templateResult.getOrNull()?.name ?: templateId
                        println("  - $qty x $templateName")
                    }
                }

                // Attempt skill progression on failure (dual-path: lucky chance OR XP)
                val baseXp = 10L + (recipe.difficulty * 1L) // Lower than success
                val xpEvents = game.skillManager.attemptSkillProgress(
                    entityId = game.worldState.player.id,
                    skillName = recipe.requiredSkill,
                    baseXp = baseXp,
                    success = false
                ).getOrNull() ?: emptyList()

                xpEvents.forEach { event ->
                    when (event) {
                        is com.jcraw.mud.core.SkillEvent.XpGained -> {
                            println("+${event.xpAmount} XP to ${recipe.requiredSkill} (${event.currentXp} total, level ${event.currentLevel})")
                        }
                        is com.jcraw.mud.core.SkillEvent.LevelUp -> {
                            println("🎉 ${recipe.requiredSkill} leveled up! ${event.oldLevel} → ${event.newLevel}")
                        }
                        else -> {}
                    }
                }
            }
            is com.jcraw.mud.reasoning.crafting.CraftingManager.CraftResult.Invalid -> {
                println("❌ ${result.message}")
            }
        }
        */
    }

    /**
     * Handle choosing a perk for a skill at milestone levels
     */
    fun handleChoosePerk(game: MudGame, skillName: String, choice: Int) {
        // Get skill component to check skill state
        val component = game.skillManager.getSkillComponent(game.worldState.player.id)
        val skillState = component.getSkill(skillName)

        if (skillState == null) {
            println("\nYou don't have the skill '$skillName'. Train it first!")
            return
        }

        // Get available perk choices at current level
        val availablePerks = game.perkSelector.getPerkChoices(skillName, skillState.level)

        if (availablePerks.isEmpty()) {
            println("\nNo perk choices available for $skillName at level ${skillState.level}.")
            return
        }

        // Validate choice (1-based index)
        if (choice < 1 || choice > availablePerks.size) {
            println("\nInvalid choice. Please choose a number between 1 and ${availablePerks.size}.")
            return
        }

        // Convert to 0-based index and get chosen perk
        val chosenPerk = availablePerks[choice - 1]

        // Attempt to select the perk
        val event = game.perkSelector.selectPerk(game.worldState.player.id, skillName, chosenPerk)

        if (event != null) {
            val message = SkillFormatter.formatPerkUnlocked(chosenPerk.name, skillName)
            println("\n$message")
        } else {
            println("\nFailed to unlock perk. You may not have a pending perk choice for this skill.")
        }
    }

    /**
     * Handle viewing player's skill sheet
     */
    fun handleViewSkills(game: MudGame) {
        val component = game.skillManager.getSkillComponent(game.worldState.player.id)
        println("\n" + SkillFormatter.formatSkillSheet(component))
    }

    /**
     * Handle saving game state to disk
     */
    fun handleSave(game: MudGame, saveName: String) {
        val result = game.persistenceManager.saveGame(game.worldState, saveName)

        result.onSuccess {
            println("💾 Game saved as '$saveName'")
        }.onFailure { error ->
            println("❌ Failed to save game: ${error.message}")
        }
    }

    /**
     * Handle loading game state from disk
     */
    fun handleLoad(game: MudGame, saveName: String) {
        val result = game.persistenceManager.loadGame(saveName)

        result.onSuccess { loadedState ->
            game.worldState = loadedState
            println("📂 Game loaded from '$saveName'")
            game.describeCurrentRoom()
        }.onFailure { error ->
            println("❌ Failed to load game: ${error.message}")

            val saves = game.persistenceManager.listSaves()
            if (saves.isNotEmpty()) {
                println("Available saves: ${saves.joinToString(", ")}")
            } else {
                println("No saved games found.")
            }
        }
    }

    /**
     * Handle displaying help text
     */
    fun handleHelp() {
        println("""
            |Available Commands:
            |  Movement:
            |    go <direction>, n/s/e/w, north/south/east/west, etc.
            |
            |  Actions:
            |    look [target]        - Examine room or specific object
            |    search [target]      - Search for hidden items (skill check)
            |    take/get <item>      - Pick up an item
            |    drop/put <item>      - Drop an item from inventory
            |    give <item> to <npc> - Give an item to an NPC
            |    talk/speak <npc>     - Talk to an NPC
            |    attack/fight <npc>   - Attack an NPC or continue combat
            |    equip/wield <item>   - Equip a weapon or armor from inventory
            |    use/consume <item>   - Use a consumable item (potion, etc.)
            |    check/test <feature> - Attempt a skill check on an interactive feature
            |    interact/harvest/gather <resource> - Harvest resources (ore, herbs, etc.)
            |    craft <recipe>       - Craft an item using a recipe
            |    persuade <npc>       - Attempt to persuade an NPC (CHA check)
            |    intimidate <npc>     - Attempt to intimidate an NPC (CHA check)
            |    inventory, i         - View your inventory and equipped items
            |
            |  Quests:
            |    quests, quest, journal, j - View quest log and available quests
            |    accept <quest_id>    - Accept an available quest
            |    abandon <quest_id>   - Abandon an active quest
            |    claim <quest_id>     - Claim reward for a completed quest
            |
            |  Meta:
            |    save [name]          - Save game (defaults to 'quicksave')
            |    load [name]          - Load game (defaults to 'quicksave')
            |    help, h, ?           - Show this help
            |    quit, exit, q        - Quit game
        """.trimMargin())
    }

    /**
     * Handle displaying quest log
     */
    fun handleQuests(game: MudGame) {
        val player = game.worldState.player

        println("\n═══════ QUEST LOG ═══════")
        println("Experience: ${player.experiencePoints} | Gold: ${player.gold}")
        println()

        if (player.activeQuests.isEmpty()) {
            println("No active quests.")
        } else {
            println("Active Quests:")
            player.activeQuests.forEachIndexed { index, quest ->
                val statusIcon = when (quest.status) {
                    com.jcraw.mud.core.QuestStatus.ACTIVE -> if (quest.isComplete()) "✓" else "○"
                    com.jcraw.mud.core.QuestStatus.COMPLETED -> "✓"
                    com.jcraw.mud.core.QuestStatus.CLAIMED -> "★"
                    com.jcraw.mud.core.QuestStatus.FAILED -> "✗"
                }
                println("\n${index + 1}. $statusIcon ${quest.title}")
                println("   ${quest.description}")
                println("   Progress: ${quest.getProgressSummary()}")

                quest.objectives.forEach { obj ->
                    val checkmark = if (obj.isCompleted) "✓" else "○"
                    println("     $checkmark ${obj.description}")
                }

                if (quest.status == com.jcraw.mud.core.QuestStatus.COMPLETED) {
                    println("   ⚠ Ready to claim reward! Use 'claim ${quest.id}'")
                }
            }
        }

        println()
        if (game.worldState.availableQuests.isNotEmpty()) {
            println("Available Quests (use 'accept <id>' to accept):")
            game.worldState.availableQuests.forEach { quest ->
                println("  - ${quest.id}: ${quest.title}")
            }
        }
        println("═" * 26)
    }

    /**
     * Handle accepting an available quest
     */
    fun handleAcceptQuest(game: MudGame, questId: String?) {
        if (questId == null) {
            // Show available quests
            if (game.worldState.availableQuests.isEmpty()) {
                println("No quests available to accept.")
            } else {
                println("\nAvailable Quests:")
                game.worldState.availableQuests.forEach { quest ->
                    println("  ${quest.id}: ${quest.title}")
                    println("    ${quest.description}")
                }
                println("\nUse 'accept <quest_id>' to accept a quest.")
            }
            return
        }

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

    /**
     * Handle abandoning an active quest
     */
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

    /**
     * Handle claiming reward for completed quest
     */
    fun handleClaimReward(game: MudGame, questId: String) {
        val quest = game.worldState.player.getQuest(questId)
        if (quest == null) {
            println("You don't have a quest with ID '$questId'.")
            return
        }

        if (!quest.isComplete()) {
            println("Quest '${quest.title}' is not complete yet!")
            println("Progress: ${quest.getProgressSummary()}")
            return
        }

        if (quest.status == com.jcraw.mud.core.QuestStatus.CLAIMED) {
            println("You've already claimed the reward for this quest!")
            return
        }

        game.worldState = game.worldState.updatePlayer(game.worldState.player.claimQuestReward(questId))

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

    /**
     * Handle quit command
     */
    fun handleQuit(game: MudGame) {
        println("Are you sure you want to quit? (y/n)")
        val confirm = readLine()?.trim()?.lowercase()
        if (confirm == "y" || confirm == "yes") {
            game.running = false
        }
    }

    // ========== Helper Functions ==========

    /**
     * Infer skill name from action description
     * Maps common actions to skills
     */
    private fun inferSkillFromAction(action: String): String? {
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
}
