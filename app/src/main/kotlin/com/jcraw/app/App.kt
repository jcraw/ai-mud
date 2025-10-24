package com.jcraw.app

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.SampleDungeon
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.perception.IntentRecognizer
import com.jcraw.mud.reasoning.RoomDescriptionGenerator
import com.jcraw.mud.reasoning.SceneryDescriptionGenerator
import com.jcraw.mud.reasoning.NPCInteractionGenerator
import com.jcraw.mud.reasoning.CombatResolver
import com.jcraw.mud.reasoning.CombatNarrator
import com.jcraw.mud.reasoning.SkillCheckResolver
import com.jcraw.mud.reasoning.procedural.ProceduralDungeonBuilder
import com.jcraw.mud.reasoning.procedural.DungeonTheme
import com.jcraw.mud.reasoning.procedural.QuestGenerator
import com.jcraw.mud.reasoning.QuestTracker
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.PersistenceManager
import com.jcraw.mud.memory.social.SocialDatabase
import com.jcraw.mud.memory.social.SqliteSocialComponentRepository
import com.jcraw.mud.memory.social.SqliteSocialEventRepository
import com.jcraw.mud.reasoning.DispositionManager
import com.jcraw.mud.action.SkillFormatter
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.coroutines.runBlocking

/**
 * Main game application - console-based MUD interface
 */
fun main() {
    // Get OpenAI API key from environment or system property (from local.properties)
    val apiKey = System.getenv("OPENAI_API_KEY")
        ?: System.getProperty("openai.api.key")
        ?: loadApiKeyFromLocalProperties()

    println("=" * 60)
    println("  AI-Powered MUD - Alpha Version")
    println("=" * 60)

    // Ask user for game mode
    println("\nSelect game mode:")
    println("  1. Single-player mode")
    println("  2. Multi-user mode (local)")
    print("\nEnter choice (1-2) [default: 1]: ")

    val modeChoice = readLine()?.trim() ?: "1"
    val isMultiUser = modeChoice == "2"

    println()

    // Always use Sample Dungeon (same as test bot)
    println("Loading Sample Dungeon (handcrafted, 6 rooms)...")
    val worldState = SampleDungeon.createInitialWorldState()
    val dungeonTheme = DungeonTheme.CRYPT // Sample uses crypt theme

    // Generate initial quests
    val questGenerator = QuestGenerator()
    val initialQuests = questGenerator.generateQuestPool(worldState, dungeonTheme, count = 3)
    var worldStateWithQuests = worldState
    initialQuests.forEach { quest ->
        worldStateWithQuests = worldStateWithQuests.addAvailableQuest(quest)
    }

    println()

    // Initialize LLM components if API key is available
    val llmClient = if (!apiKey.isNullOrBlank()) {
        println("✅ Using LLM-powered descriptions, NPC dialogue, combat narration, and RAG memory\n")
        OpenAIClient(apiKey)
    } else {
        println("⚠️  OpenAI API key not found - using simple fallback mode")
        println("   Set OPENAI_API_KEY environment variable or openai.api.key in local.properties\n")
        null
    }

    if (isMultiUser) {
        // Multi-user mode
        val memoryManager = llmClient?.let { MemoryManager(it) }
        val descriptionGenerator = llmClient?.let { RoomDescriptionGenerator(it, memoryManager!!) }
        val npcInteractionGenerator = llmClient?.let { NPCInteractionGenerator(it, memoryManager!!) }
        val combatNarrator = llmClient?.let { CombatNarrator(it, memoryManager!!) }
        val skillCheckResolver = SkillCheckResolver()
        val combatResolver = CombatResolver()

        val multiUserGame = MultiUserGame(
            initialWorldState = worldStateWithQuests,
            descriptionGenerator = descriptionGenerator,
            npcInteractionGenerator = npcInteractionGenerator,
            combatNarrator = combatNarrator,
            memoryManager = memoryManager,
            combatResolver = combatResolver,
            skillCheckResolver = skillCheckResolver,
            llmClient = llmClient
        )

        multiUserGame.start()
    } else {
        // Single-player mode
        val game = if (llmClient != null) {
            val memoryManager = MemoryManager(llmClient)
            val descriptionGenerator = RoomDescriptionGenerator(llmClient, memoryManager)
            val npcInteractionGenerator = NPCInteractionGenerator(llmClient, memoryManager)
            val combatNarrator = CombatNarrator(llmClient, memoryManager)
            MudGame(
                initialWorldState = worldStateWithQuests,
                descriptionGenerator = descriptionGenerator,
                npcInteractionGenerator = npcInteractionGenerator,
                combatNarrator = combatNarrator,
                memoryManager = memoryManager,
                llmClient = llmClient
            )
        } else {
            MudGame(
                initialWorldState = worldStateWithQuests,
                descriptionGenerator = null,
                npcInteractionGenerator = null,
                combatNarrator = null,
                memoryManager = null,
                llmClient = null
            )
        }

        game.start()
    }
}

class MudGame(
    internal val initialWorldState: WorldState,
    private val descriptionGenerator: RoomDescriptionGenerator? = null,
    internal val npcInteractionGenerator: NPCInteractionGenerator? = null,
    internal val combatNarrator: CombatNarrator? = null,
    private val memoryManager: MemoryManager? = null,
    private val llmClient: OpenAIClient? = null
) {
    internal var worldState: WorldState = initialWorldState
    private var running = true
    internal val combatResolver = CombatResolver()
    internal val skillCheckResolver = SkillCheckResolver()
    private val persistenceManager = PersistenceManager()
    private val intentRecognizer = IntentRecognizer(llmClient)
    internal val sceneryGenerator = SceneryDescriptionGenerator(llmClient)
    internal var lastConversationNpcId: String? = null

    // Social system components
    private val socialDatabase = SocialDatabase("social.db")
    private val socialComponentRepo = SqliteSocialComponentRepository(socialDatabase)
    private val socialEventRepo = SqliteSocialEventRepository(socialDatabase)
    private val knowledgeRepo = com.jcraw.mud.memory.social.SqliteKnowledgeRepository(socialDatabase)
    private val dispositionManager = DispositionManager(socialComponentRepo, socialEventRepo)
    internal val emoteHandler = com.jcraw.mud.reasoning.EmoteHandler(dispositionManager)
    internal val npcKnowledgeManager = com.jcraw.mud.reasoning.NPCKnowledgeManager(knowledgeRepo, socialComponentRepo, llmClient)
    private val questTracker = QuestTracker(dispositionManager)

    // Skill system components
    private val skillDatabase = com.jcraw.mud.memory.skill.SkillDatabase("skills.db")
    private val skillRepo = com.jcraw.mud.memory.skill.SQLiteSkillRepository(skillDatabase)
    private val skillComponentRepo = com.jcraw.mud.memory.skill.SQLiteSkillComponentRepository(skillDatabase)
    private val skillManager = com.jcraw.mud.reasoning.skill.SkillManager(skillRepo, skillComponentRepo, memoryManager)
    private val perkSelector = com.jcraw.mud.reasoning.skill.PerkSelector(skillComponentRepo, memoryManager)

    fun start() {
        printWelcome()
        describeCurrentRoom()

        while (running) {
            print("\n> ")
            val input = readLine()?.trim() ?: continue

            if (input.isBlank()) continue

            val room = worldState.getCurrentRoom()
            val roomContext = room?.let { "${it.name}: ${it.traits.joinToString(", ")}" }
            val exitsWithNames = room?.let { buildExitsWithNames(it) }
            val intent = runBlocking {
                intentRecognizer.parseIntent(input, roomContext, exitsWithNames)
            }
            processIntent(intent)
        }

        println("\nThanks for playing!")
    }

    internal fun printWelcome() {
        println("\nWelcome, ${worldState.player.name}!")
        println("You have entered a dungeon with ${worldState.rooms.size} rooms to explore.")
        println("Type 'help' for available commands.\n")
    }

    internal fun describeCurrentRoom() {
        val room = worldState.getCurrentRoom() ?: return

        val npcs = room.entities.filterIsInstance<com.jcraw.mud.core.Entity.NPC>()
        if (lastConversationNpcId != null && npcs.none { it.id == lastConversationNpcId }) {
            lastConversationNpcId = null
        }

        // Show combat status if in combat
        if (worldState.player.isInCombat()) {
            val combat = worldState.player.activeCombat!!
            val npc = room.entities.filterIsInstance<Entity.NPC>()
                .find { it.id == combat.combatantNpcId }
            println("\n⚔️  IN COMBAT with ${npc?.name ?: "Unknown Enemy"}")
            println("Your Health: ${combat.playerHealth}/${worldState.player.maxHealth}")
            println("Enemy Health: ${combat.npcHealth}/${npc?.maxHealth ?: 100}")
            println()
            return
        }

        println("\n${room.name}")
        println("-" * room.name.length)
        println(generateRoomDescription(room))

        if (room.exits.isNotEmpty()) {
            println("\nExits: ${room.exits.keys.joinToString(", ") { direction -> direction.displayName }}")
        }

        if (room.entities.isNotEmpty()) {
            println("\nYou see:")
            room.entities.forEach { entity ->
                println("  - ${entity.name}")
            }
        }
    }

    internal fun generateRoomDescription(room: com.jcraw.mud.core.Room): String {
        return if (descriptionGenerator != null) {
            // Use LLM-powered description generation
            runBlocking {
                descriptionGenerator.generateDescription(room)
            }
        } else {
            // Fallback to simple trait concatenation
            room.traits.joinToString(". ") + "."
        }
    }

    private fun processIntent(intent: Intent) {
        when (intent) {
            is Intent.Move -> com.jcraw.app.handlers.MovementHandlers.handleMove(this, intent.direction)
            is Intent.Look -> com.jcraw.app.handlers.MovementHandlers.handleLook(this, intent.target)
            is Intent.Search -> com.jcraw.app.handlers.MovementHandlers.handleSearch(this, intent.target)
            is Intent.Interact -> handleInteract(intent.target)
            is Intent.Inventory -> com.jcraw.app.handlers.ItemHandlers.handleInventory(this)
            is Intent.Take -> com.jcraw.app.handlers.ItemHandlers.handleTake(this, intent.target)
            is Intent.TakeAll -> com.jcraw.app.handlers.ItemHandlers.handleTakeAll(this)
            is Intent.Drop -> com.jcraw.app.handlers.ItemHandlers.handleDrop(this, intent.target)
            is Intent.Give -> com.jcraw.app.handlers.ItemHandlers.handleGive(this, intent.itemTarget, intent.npcTarget)
            is Intent.Talk -> com.jcraw.app.handlers.SocialHandlers.handleTalk(this, intent.target)
            is Intent.Say -> com.jcraw.app.handlers.SocialHandlers.handleSay(this, intent.message, intent.npcTarget)
            is Intent.Attack -> com.jcraw.app.handlers.CombatHandlers.handleAttack(this, intent.target)
            is Intent.Equip -> com.jcraw.app.handlers.ItemHandlers.handleEquip(this, intent.target)
            is Intent.Use -> com.jcraw.app.handlers.ItemHandlers.handleUse(this, intent.target)
            is Intent.Check -> handleCheck(intent.target)
            is Intent.Persuade -> com.jcraw.app.handlers.SocialHandlers.handlePersuade(this, intent.target)
            is Intent.Intimidate -> com.jcraw.app.handlers.SocialHandlers.handleIntimidate(this, intent.target)
            is Intent.Emote -> com.jcraw.app.handlers.SocialHandlers.handleEmote(this, intent.emoteType, intent.target)
            is Intent.AskQuestion -> runBlocking { com.jcraw.app.handlers.SocialHandlers.handleAskQuestion(this@MudGame, intent.npcTarget, intent.topic) }
            is Intent.UseSkill -> handleUseSkill(intent.skill, intent.action)
            is Intent.TrainSkill -> handleTrainSkill(intent.skill, intent.method)
            is Intent.ChoosePerk -> handleChoosePerk(intent.skillName, intent.choice)
            is Intent.ViewSkills -> handleViewSkills()
            is Intent.Save -> handleSave(intent.saveName)
            is Intent.Load -> handleLoad(intent.saveName)
            is Intent.Quests -> handleQuests()
            is Intent.AcceptQuest -> handleAcceptQuest(intent.questId)
            is Intent.AbandonQuest -> handleAbandonQuest(intent.questId)
            is Intent.ClaimReward -> handleClaimReward(intent.questId)
            is Intent.Help -> handleHelp()
            is Intent.Quit -> handleQuit()
            is Intent.Invalid -> println(intent.message)
        }
    }

    internal fun trackQuests(action: QuestAction) {
        val (updatedPlayer, updatedWorld) = questTracker.updateQuestsAfterAction(
            worldState.player,
            worldState,
            action
        )

        // Check if any quest objectives were completed
        if (updatedPlayer != worldState.player) {
            updatedPlayer.activeQuests.forEach { quest ->
                val oldQuest = worldState.player.getQuest(quest.id)
                if (oldQuest != null) {
                    // Check for newly completed objectives
                    quest.objectives.zip(oldQuest.objectives).forEach { (newObj, oldObj) ->
                        if (newObj.isCompleted && !oldObj.isCompleted) {
                            println("\n✓ Quest objective completed: ${newObj.description}")
                        }
                    }

                    // Check if quest just completed
                    if (quest.status == com.jcraw.mud.core.QuestStatus.COMPLETED &&
                        oldQuest.status == com.jcraw.mud.core.QuestStatus.ACTIVE) {
                        println("\n🎉 Quest completed: ${quest.title}")
                        println("Use 'claim ${quest.id}' to collect your reward!")
                    }
                }
            }

            // Update both player and world state (world may have NPC disposition changes)
            worldState = updatedWorld.updatePlayer(updatedPlayer)
        }
    }

    /**
     * Build a map of exits with their destination room names for navigation parsing.
     */
    private fun buildExitsWithNames(room: com.jcraw.mud.core.Room): Map<Direction, String> {
        return room.exits.mapNotNull { (direction, roomId) ->
            val destRoom = worldState.rooms[roomId]
            if (destRoom != null) {
                direction to destRoom.name
            } else {
                null
            }
        }.toMap()
    }

    private fun handleInteract(target: String) {
        println("Interaction system not yet implemented. (Target: $target)")
    }

    private fun handleCheck(target: String) {
        val room = worldState.getCurrentRoom() ?: return

        // Normalize target for matching (replace underscores with spaces)
        val normalizedTarget = target.lowercase().replace("_", " ")

        // Find the feature in the room with flexible matching
        val feature = room.entities.filterIsInstance<Entity.Feature>()
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
        val result = skillCheckResolver.checkPlayer(
            worldState.player,
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
            worldState = worldState.replaceEntity(room.id, feature.id, updatedFeature) ?: worldState

            // Track skill check for quests
            trackQuests(QuestAction.UsedSkill(feature.id))
        } else {
            println("\n❌ Failure!")
            println(challenge.failureDescription)
        }
    }

    private fun handleUseSkill(skill: String?, action: String) {
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
        val checkResult = skillManager.checkSkill(
            entityId = worldState.player.id,
            skillName = skillName,
            difficulty = difficulty
        ).getOrElse { error ->
            println("\nSkill check failed: ${error.message}")
            return
        }

        // Grant XP based on success/failure (base 50 XP)
        val baseXp = 50L
        val xpEvents = skillManager.grantXp(
            entityId = worldState.player.id,
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

    private fun handleTrainSkill(skill: String, method: String) {
        val room = worldState.getCurrentRoom() ?: return

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

        // Find NPC in room
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find {
                it.name.lowercase().contains(npcName) ||
                it.id.lowercase().contains(npcName)
            }

        if (npc == null) {
            println("\nThere's no one here by that name to train with.")
            return
        }

        // Attempt training via DispositionManager
        val trainingResult = dispositionManager.trainSkillWithNPC(
            worldState.player.id,
            npc,
            skill
        )

        trainingResult.onSuccess { message ->
            println("\n$message")

            // Update world state with any NPC changes (disposition)
            worldState = worldState.replaceEntity(room.id, npc.id, npc) ?: worldState
        }.onFailure { error ->
            println("\n${error.message}")
        }
    }

    private fun handleChoosePerk(skillName: String, choice: Int) {
        // Get skill component to check skill state
        val component = skillManager.getSkillComponent(worldState.player.id)
        val skillState = component.getSkill(skillName)

        if (skillState == null) {
            println("\nYou don't have the skill '$skillName'. Train it first!")
            return
        }

        // Get available perk choices at current level
        val availablePerks = perkSelector.getPerkChoices(skillName, skillState.level)

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
        val event = perkSelector.selectPerk(worldState.player.id, skillName, chosenPerk)

        if (event != null) {
            val message = SkillFormatter.formatPerkUnlocked(chosenPerk.name, skillName)
            println("\n$message")
        } else {
            println("\nFailed to unlock perk. You may not have a pending perk choice for this skill.")
        }
    }

    private fun handleViewSkills() {
        val component = skillManager.getSkillComponent(worldState.player.id)
        println("\n" + SkillFormatter.formatSkillSheet(component))
    }

    private fun handleSave(saveName: String) {
        val result = persistenceManager.saveGame(worldState, saveName)

        result.onSuccess {
            println("💾 Game saved as '$saveName'")
        }.onFailure { error ->
            println("❌ Failed to save game: ${error.message}")
        }
    }

    private fun handleLoad(saveName: String) {
        val result = persistenceManager.loadGame(saveName)

        result.onSuccess { loadedState ->
            worldState = loadedState
            println("📂 Game loaded from '$saveName'")
            describeCurrentRoom()
        }.onFailure { error ->
            println("❌ Failed to load game: ${error.message}")

            val saves = persistenceManager.listSaves()
            if (saves.isNotEmpty()) {
                println("Available saves: ${saves.joinToString(", ")}")
            } else {
                println("No saved games found.")
            }
        }
    }

    private fun handleHelp() {
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
            |    persuade <npc>       - Attempt to persuade an NPC (CHA check)
            |    intimidate <npc>     - Attempt to intimidate an NPC (CHA check)
            |    interact <item>      - Interact with an object (not yet implemented)
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

    private fun handleQuests() {
        val player = worldState.player

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
        if (worldState.availableQuests.isNotEmpty()) {
            println("Available Quests (use 'accept <id>' to accept):")
            worldState.availableQuests.forEach { quest ->
                println("  - ${quest.id}: ${quest.title}")
            }
        }
        println("═" * 26)
    }

    private fun handleAcceptQuest(questId: String?) {
        if (questId == null) {
            // Show available quests
            if (worldState.availableQuests.isEmpty()) {
                println("No quests available to accept.")
            } else {
                println("\nAvailable Quests:")
                worldState.availableQuests.forEach { quest ->
                    println("  ${quest.id}: ${quest.title}")
                    println("    ${quest.description}")
                }
                println("\nUse 'accept <quest_id>' to accept a quest.")
            }
            return
        }

        val quest = worldState.getAvailableQuest(questId)
        if (quest == null) {
            println("No quest available with ID '$questId'.")
            return
        }

        if (worldState.player.hasQuest(questId)) {
            println("You already have this quest!")
            return
        }

        worldState = worldState
            .updatePlayer(worldState.player.addQuest(quest))
            .removeAvailableQuest(questId)

        println("\n📜 Quest Accepted: ${quest.title}")
        println("${quest.description}")
        println("\nObjectives:")
        quest.objectives.forEach { println("  ○ ${it.description}") }
    }

    private fun handleAbandonQuest(questId: String) {
        val quest = worldState.player.getQuest(questId)
        if (quest == null) {
            println("You don't have a quest with ID '$questId'.")
            return
        }

        println("Are you sure you want to abandon '${quest.title}'? (y/n)")
        val confirm = readLine()?.trim()?.lowercase()
        if (confirm == "y" || confirm == "yes") {
            worldState = worldState
                .updatePlayer(worldState.player.removeQuest(questId))
                .addAvailableQuest(quest)
            println("Quest abandoned.")
        }
    }

    private fun handleClaimReward(questId: String) {
        val quest = worldState.player.getQuest(questId)
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

        worldState = worldState.updatePlayer(worldState.player.claimQuestReward(questId))

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
        println("\nTotal Experience: ${worldState.player.experiencePoints}")
        println("Total Gold: ${worldState.player.gold}")
    }

    private fun handleQuit() {
        println("Are you sure you want to quit? (y/n)")
        val confirm = readLine()?.trim()?.lowercase()
        if (confirm == "y" || confirm == "yes") {
            running = false
        }
    }
}

/**
 * Multi-user game mode that runs GameServer and manages multiple player sessions
 */
class MultiUserGame(
    private val initialWorldState: WorldState,
    private val descriptionGenerator: RoomDescriptionGenerator?,
    private val npcInteractionGenerator: NPCInteractionGenerator?,
    private val combatNarrator: CombatNarrator?,
    private val memoryManager: MemoryManager?,
    private val combatResolver: CombatResolver,
    private val skillCheckResolver: SkillCheckResolver,
    private val llmClient: OpenAIClient?
) {
    private lateinit var gameServer: GameServer
    private val intentRecognizer = IntentRecognizer(llmClient)

    fun start() = runBlocking {
        // Create fallback components if needed
        val effectiveMemoryManager = memoryManager ?: createFallbackMemoryManager()
        val effectiveDescGenerator = descriptionGenerator ?: createFallbackDescriptionGenerator(effectiveMemoryManager)
        val effectiveNpcGenerator = npcInteractionGenerator ?: createFallbackNPCGenerator(effectiveMemoryManager)
        val effectiveCombatNarrator = combatNarrator ?: createFallbackCombatNarrator(effectiveMemoryManager)

        // Initialize game server with social system
        val socialDatabase = SocialDatabase("social.db")
        val sceneryGenerator = SceneryDescriptionGenerator(llmClient)
        gameServer = GameServer(
            worldState = initialWorldState,
            memoryManager = effectiveMemoryManager,
            roomDescriptionGenerator = effectiveDescGenerator,
            npcInteractionGenerator = effectiveNpcGenerator,
            combatResolver = combatResolver,
            combatNarrator = effectiveCombatNarrator,
            skillCheckResolver = skillCheckResolver,
            sceneryGenerator = sceneryGenerator,
            socialDatabase = socialDatabase
        )

        println("\n🎮 Multi-User Mode Enabled")
        println("=" * 60)
        println("This mode uses the multi-user server architecture.")
        println("Future versions will support network connections for true multi-player.")
        println("\nEnter your player name: ")

        val playerName = readLine()?.trim()?.ifBlank { "Adventurer" } ?: "Adventurer"

        println("\n🌟 Starting game for $playerName...")
        println("=" * 60)

        // Get starting room
        val startingRoom = initialWorldState.rooms.values.first()

        // Create player session
        val playerId = "player_main"
        val session = PlayerSession(
            playerId = playerId,
            playerName = playerName,
            input = System.`in`.bufferedReader(),
            output = System.out.writer().buffered().let { java.io.PrintWriter(it) }
        )

        gameServer.addPlayerSession(session, startingRoom.id)

        // Run session
        runPlayerSession(session)

        println("\n\n🎮 Game ended. Thanks for playing!")
    }

    private suspend fun runPlayerSession(session: PlayerSession) {
        var running = true

        // Welcome message
        session.sendMessage("\n" + "=" * 60)
        session.sendMessage("  Welcome, ${session.playerName}!")
        session.sendMessage("=" * 60)

        // Show initial room
        val initialRoom = gameServer.getWorldState().getCurrentRoom(session.playerId)
        if (initialRoom != null) {
            val description = descriptionGenerator?.generateDescription(initialRoom)
                ?: initialRoom.traits.joinToString(". ") + "."
            session.sendMessage("\n${initialRoom.name}")
            session.sendMessage("-" * initialRoom.name.length)
            session.sendMessage(description)
        }

        session.sendMessage("\nType 'help' for commands.\n")

        while (running) {
            // Process any pending events
            val events = session.processEvents()
            events.forEach { session.sendMessage(it) }

            // Show prompt
            session.sendMessage("\n[${session.playerName}] > ")

            // Read input
            val input = session.readLine() ?: break
            if (input.trim().isBlank()) continue

            // Parse intent using LLM
            val room = gameServer.getWorldState().getCurrentRoom(session.playerId)
            val roomContext = room?.let { "${it.name}: ${it.traits.joinToString(", ")}" }
            val exitsWithNames = room?.let { buildExitsWithNames(it, gameServer.getWorldState()) }
            val intent = intentRecognizer.parseIntent(input.trim(), roomContext, exitsWithNames)

            // Check for quit
            if (intent is Intent.Quit) {
                session.sendMessage("Goodbye, ${session.playerName}!")
                running = false
                gameServer.removePlayerSession(session.playerId)
                break
            }

            // Process intent through game server
            val response = gameServer.processIntent(session.playerId, intent)
            session.sendMessage(response)
        }
    }

    /**
     * Build a map of exits with their destination room names for navigation parsing.
     */
    private fun buildExitsWithNames(room: com.jcraw.mud.core.Room, worldState: WorldState): Map<Direction, String> {
        return room.exits.mapNotNull { (direction, roomId) ->
            val destRoom = worldState.rooms[roomId]
            if (destRoom != null) {
                direction to destRoom.name
            } else {
                null
            }
        }.toMap()
    }

    private fun createFallbackMemoryManager(): MemoryManager {
        // Create memory manager with null client (will use in-memory store only)
        return MemoryManager(null)
    }

    private fun createFallbackDescriptionGenerator(memoryManager: MemoryManager): RoomDescriptionGenerator {
        // Create a simple mock LLM client that always throws to trigger fallback logic
        val mockClient = object : com.jcraw.sophia.llm.LLMClient {
            override suspend fun chatCompletion(
                modelId: String,
                systemPrompt: String,
                userContext: String,
                maxTokens: Int,
                temperature: Double
            ): com.jcraw.sophia.llm.OpenAIResponse {
                throw UnsupportedOperationException("Mock client - fallback mode")
            }

            override suspend fun createEmbedding(text: String, model: String): List<Double> {
                return emptyList()
            }

            override fun close() {
                // No-op for mock
            }
        }
        return RoomDescriptionGenerator(mockClient, memoryManager)
    }

    private fun createFallbackNPCGenerator(memoryManager: MemoryManager): NPCInteractionGenerator {
        val mockClient = object : com.jcraw.sophia.llm.LLMClient {
            override suspend fun chatCompletion(
                modelId: String,
                systemPrompt: String,
                userContext: String,
                maxTokens: Int,
                temperature: Double
            ): com.jcraw.sophia.llm.OpenAIResponse {
                throw UnsupportedOperationException("Mock client - fallback mode")
            }

            override suspend fun createEmbedding(text: String, model: String): List<Double> {
                return emptyList()
            }

            override fun close() {
                // No-op for mock
            }
        }
        return NPCInteractionGenerator(mockClient, memoryManager)
    }

    private fun createFallbackCombatNarrator(memoryManager: MemoryManager): CombatNarrator {
        val mockClient = object : com.jcraw.sophia.llm.LLMClient {
            override suspend fun chatCompletion(
                modelId: String,
                systemPrompt: String,
                userContext: String,
                maxTokens: Int,
                temperature: Double
            ): com.jcraw.sophia.llm.OpenAIResponse {
                throw UnsupportedOperationException("Mock client - fallback mode")
            }

            override suspend fun createEmbedding(text: String, model: String): List<Double> {
                return emptyList()
            }

            override fun close() {
                // No-op for mock
            }
        }
        return CombatNarrator(mockClient, memoryManager)
    }
}

// String repetition helper
private operator fun String.times(n: Int): String = repeat(n)

/**
 * Load API key from local.properties file.
 * This allows the app to work when run directly from IDE.
 * Checks both current directory and project root.
 */
private fun loadApiKeyFromLocalProperties(): String? {
    // Try current directory first
    var localPropertiesFile = java.io.File("local.properties")

    // If not found, try project root (when run via Gradle from submodule)
    if (!localPropertiesFile.exists()) {
        localPropertiesFile = java.io.File("../local.properties")
    }

    // If still not found, try going up two levels (just in case)
    if (!localPropertiesFile.exists()) {
        localPropertiesFile = java.io.File("../../local.properties")
    }

    if (!localPropertiesFile.exists()) {
        return null
    }

    return try {
        localPropertiesFile.readLines()
            .firstOrNull { it.trim().startsWith("openai.api.key=") }
            ?.substringAfter("openai.api.key=")
            ?.trim()
    } catch (e: Exception) {
        null
    }
}
