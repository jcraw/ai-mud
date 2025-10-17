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
    private val initialWorldState: WorldState,
    private val descriptionGenerator: RoomDescriptionGenerator? = null,
    private val npcInteractionGenerator: NPCInteractionGenerator? = null,
    private val combatNarrator: CombatNarrator? = null,
    private val memoryManager: MemoryManager? = null,
    private val llmClient: OpenAIClient? = null
) {
    private var worldState: WorldState = initialWorldState
    private var running = true
    private val combatResolver = CombatResolver()
    private val skillCheckResolver = SkillCheckResolver()
    private val persistenceManager = PersistenceManager()
    private val intentRecognizer = IntentRecognizer(llmClient)
    private val sceneryGenerator = SceneryDescriptionGenerator(llmClient)

    // Social system components
    private val socialDatabase = SocialDatabase("social.db")
    private val socialComponentRepo = SqliteSocialComponentRepository(socialDatabase)
    private val socialEventRepo = SqliteSocialEventRepository(socialDatabase)
    private val dispositionManager = DispositionManager(socialComponentRepo, socialEventRepo)
    private val questTracker = QuestTracker(dispositionManager)

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

    private fun printWelcome() {
        println("\nWelcome, ${worldState.player.name}!")
        println("You have entered a dungeon with ${worldState.rooms.size} rooms to explore.")
        println("Type 'help' for available commands.\n")
    }

    private fun describeCurrentRoom() {
        val room = worldState.getCurrentRoom() ?: return

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

    private fun generateRoomDescription(room: com.jcraw.mud.core.Room): String {
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
            is Intent.Move -> handleMove(intent.direction)
            is Intent.Look -> handleLook(intent.target)
            is Intent.Search -> handleSearch(intent.target)
            is Intent.Interact -> handleInteract(intent.target)
            is Intent.Inventory -> handleInventory()
            is Intent.Take -> handleTake(intent.target)
            is Intent.TakeAll -> handleTakeAll()
            is Intent.Drop -> handleDrop(intent.target)
            is Intent.Give -> handleGive(intent.itemTarget, intent.npcTarget)
            is Intent.Talk -> handleTalk(intent.target)
            is Intent.Attack -> handleAttack(intent.target)
            is Intent.Equip -> handleEquip(intent.target)
            is Intent.Use -> handleUse(intent.target)
            is Intent.Check -> handleCheck(intent.target)
            is Intent.Persuade -> handlePersuade(intent.target)
            is Intent.Intimidate -> handleIntimidate(intent.target)
            is Intent.Emote -> handleEmote(intent.emoteType, intent.target)
            is Intent.AskQuestion -> handleAskQuestion(intent.npcTarget, intent.topic)
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

    private fun trackQuests(action: QuestAction) {
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

    private fun handleMove(direction: Direction) {
        // Check if in combat - must flee first
        if (worldState.player.isInCombat()) {
            println("\nYou attempt to flee from combat...")

            val result = combatResolver.attemptFlee(worldState)
            println(result.narrative)

            if (result.playerFled) {
                // Flee successful - update state and move
                worldState = worldState.updatePlayer(worldState.player.endCombat())

                val newState = worldState.movePlayer(direction)
                if (newState == null) {
                    println("You can't go that way.")
                    return
                }

                worldState = newState
                println("You move ${direction.displayName}.")

                // Track room exploration for quests
                val room = worldState.getCurrentRoom()
                if (room != null) {
                    trackQuests(QuestAction.VisitedRoom(room.id))
                }

                describeCurrentRoom()
            } else if (result.playerDied) {
                // Player died trying to flee
                println("\nYou have been defeated! Game over.")
                println("\nPress any key to play again...")
                readLine()  // Wait for any input

                // Restart the game
                worldState = initialWorldState
                println("\n" + "=" * 60)
                println("  Restarting Adventure...")
                println("=" * 60)
                printWelcome()
                describeCurrentRoom()
            } else {
                // Failed to flee - update combat state and stay in place
                val combatState = result.newCombatState
                if (combatState != null) {
                    // Sync player's actual health with combat state
                    val updatedPlayer = worldState.player
                        .updateCombat(combatState)
                        .copy(health = combatState.playerHealth)
                    worldState = worldState.updatePlayer(updatedPlayer)
                }
                describeCurrentRoom()
            }
            return
        }

        // Normal movement (not in combat)
        val newState = worldState.movePlayer(direction)

        if (newState == null) {
            println("You can't go that way.")
            return
        }

        worldState = newState
        println("You move ${direction.displayName}.")

        // Track room exploration for quests
        val room = worldState.getCurrentRoom()
        if (room != null) {
            trackQuests(QuestAction.VisitedRoom(room.id))
        }

        describeCurrentRoom()
    }

    private fun handleLook(target: String?) {
        if (target == null) {
            // Look at room - describeCurrentRoom already shows all entities including items
            describeCurrentRoom()
        } else {
            // Look at specific entity
            val room = worldState.getCurrentRoom() ?: return
            val entity = room.entities.find { e ->
                e.name.lowercase().contains(target.lowercase()) ||
                e.id.lowercase().contains(target.lowercase())
            }

            if (entity != null) {
                println(entity.description)
            } else {
                // Try to describe scenery (non-entity objects like walls, floor, etc.)
                val roomDescription = generateRoomDescription(room)
                val sceneryDescription = runBlocking {
                    sceneryGenerator.describeScenery(target, room, roomDescription)
                }

                if (sceneryDescription != null) {
                    println(sceneryDescription)
                } else {
                    println("You don't see that here.")
                }
            }
        }
    }

    private fun handleSearch(target: String?) {
        val room = worldState.getCurrentRoom() ?: return

        println("\nYou search the area carefully${if (target != null) ", focusing on the $target" else ""}...")

        // Perform a Wisdom (Perception) skill check to find hidden items
        val result = skillCheckResolver.checkPlayer(
            worldState.player,
            com.jcraw.mud.core.StatType.WISDOM,
            com.jcraw.mud.core.Difficulty.MEDIUM  // DC 15 for finding hidden items
        )

        // Display roll details
        println("\nRolling Perception check...")
        println("d20 roll: ${result.roll} + WIS modifier: ${result.modifier} = ${result.total} vs DC ${result.dc}")

        if (result.isCriticalSuccess) {
            println("\n🎲 CRITICAL SUCCESS! (Natural 20)")
        } else if (result.isCriticalFailure) {
            println("\n💀 CRITICAL FAILURE! (Natural 1)")
        }

        if (result.success) {
            println("\n✅ Success!")

            // Find hidden items in the room
            val hiddenItems = room.entities.filterIsInstance<Entity.Item>().filter { !it.isPickupable }
            val pickupableItems = room.entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }

            if (hiddenItems.isNotEmpty() || pickupableItems.isNotEmpty()) {
                if (pickupableItems.isNotEmpty()) {
                    println("You find the following items:")
                    pickupableItems.forEach { item ->
                        println("  - ${item.name}: ${item.description}")
                    }
                }
                if (hiddenItems.isNotEmpty()) {
                    println("\nYou also notice some interesting features:")
                    hiddenItems.forEach { item ->
                        println("  - ${item.name}: ${item.description}")
                    }
                }
            } else {
                println("You don't find anything hidden here.")
            }
        } else {
            println("\n❌ Failure!")
            println("You don't find anything of interest.")
        }
    }

    private fun handleInteract(target: String) {
        println("Interaction system not yet implemented. (Target: $target)")
    }

    private fun handleInventory() {
        println("Inventory:")

        // Show equipped items
        if (worldState.player.equippedWeapon != null) {
            println("  Equipped Weapon: ${worldState.player.equippedWeapon!!.name} (+${worldState.player.equippedWeapon!!.damageBonus} damage)")
        } else {
            println("  Equipped Weapon: (none)")
        }

        if (worldState.player.equippedArmor != null) {
            println("  Equipped Armor: ${worldState.player.equippedArmor!!.name} (+${worldState.player.equippedArmor!!.defenseBonus} defense)")
        } else {
            println("  Equipped Armor: (none)")
        }

        // Show inventory items
        if (worldState.player.inventory.isEmpty()) {
            println("  Carrying: (nothing)")
        } else {
            println("  Carrying:")
            worldState.player.inventory.forEach { item ->
                val extra = when (item.itemType) {
                    ItemType.WEAPON -> " [weapon, +${item.damageBonus} damage]"
                    ItemType.ARMOR -> " [armor, +${item.defenseBonus} defense]"
                    ItemType.CONSUMABLE -> " [heals ${item.healAmount} HP]"
                    else -> ""
                }
                println("    - ${item.name}$extra")
            }
        }
    }

    private fun handleTake(target: String) {
        val room = worldState.getCurrentRoom() ?: return

        // Find the item in the room
        val item = room.entities.filterIsInstance<com.jcraw.mud.core.Entity.Item>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (item == null) {
            // Not an item - check if it's scenery (room trait or entity)
            val isScenery = room.traits.any { it.lowercase().contains(target.lowercase()) } ||
                           room.entities.any { it.name.lowercase().contains(target.lowercase()) }
            if (isScenery) {
                println("That's part of the environment and can't be taken.")
            } else {
                println("You don't see that here.")
            }
            return
        }

        if (!item.isPickupable) {
            println("That's part of the environment and can't be taken.")
            return
        }

        // Remove item from room and add to inventory
        val newState = worldState
            .removeEntityFromRoom(room.id, item.id)
            ?.updatePlayer(worldState.player.addToInventory(item))

        if (newState != null) {
            worldState = newState
            println("You take the ${item.name}.")

            // Track item collection for quests
            trackQuests(QuestAction.CollectedItem(item.id))
        } else {
            println("Something went wrong.")
        }
    }

    private fun handleTakeAll() {
        val room = worldState.getCurrentRoom() ?: return

        // Find all pickupable items in the room
        val items = room.entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }

        if (items.isEmpty()) {
            println("There are no items to take here.")
            return
        }

        var takenCount = 0
        var currentState = worldState

        items.forEach { item ->
            val newState = currentState
                .removeEntityFromRoom(room.id, item.id)
                ?.updatePlayer(currentState.player.addToInventory(item))

            if (newState != null) {
                currentState = newState
                println("You take the ${item.name}.")
                takenCount++
            }
        }

        worldState = currentState

        if (takenCount > 0) {
            println("\nYou took $takenCount item${if (takenCount > 1) "s" else ""}.")

            // Track item collection for quests
            items.forEach { item ->
                trackQuests(QuestAction.CollectedItem(item.id))
            }
        }
    }

    private fun handleDrop(target: String) {
        val room = worldState.getCurrentRoom() ?: return

        // Find the item in inventory
        var item = worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(target.lowercase()) ||
            invItem.id.lowercase().contains(target.lowercase())
        }

        // Check if item is equipped weapon
        var isEquippedWeapon = false
        if (item == null && worldState.player.equippedWeapon != null) {
            if (worldState.player.equippedWeapon!!.name.lowercase().contains(target.lowercase()) ||
                worldState.player.equippedWeapon!!.id.lowercase().contains(target.lowercase())) {
                item = worldState.player.equippedWeapon
                isEquippedWeapon = true
            }
        }

        // Check if item is equipped armor
        var isEquippedArmor = false
        if (item == null && worldState.player.equippedArmor != null) {
            if (worldState.player.equippedArmor!!.name.lowercase().contains(target.lowercase()) ||
                worldState.player.equippedArmor!!.id.lowercase().contains(target.lowercase())) {
                item = worldState.player.equippedArmor
                isEquippedArmor = true
            }
        }

        if (item == null) {
            println("You don't have that.")
            return
        }

        // Unequip if needed and add to room
        val updatedPlayer = when {
            isEquippedWeapon -> worldState.player.copy(equippedWeapon = null)
            isEquippedArmor -> worldState.player.copy(equippedArmor = null)
            else -> worldState.player.removeFromInventory(item.id)
        }

        val newState = worldState
            .updatePlayer(updatedPlayer)
            .addEntityToRoom(room.id, item)

        if (newState != null) {
            worldState = newState
            println("You drop the ${item.name}.")
        } else {
            println("Something went wrong.")
        }
    }

    private fun handleGive(itemTarget: String, npcTarget: String) {
        val room = worldState.getCurrentRoom() ?: return

        // Find the item in inventory
        val item = worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(itemTarget.lowercase()) ||
            invItem.id.lowercase().contains(itemTarget.lowercase())
        }

        if (item == null) {
            println("You don't have that item.")
            return
        }

        // Find the NPC in the room
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(npcTarget.lowercase()) ||
                entity.id.lowercase().contains(npcTarget.lowercase())
            }

        if (npc == null) {
            println("There's no one here by that name.")
            return
        }

        // Remove item from inventory
        val updatedPlayer = worldState.player.removeFromInventory(item.id)
        worldState = worldState.updatePlayer(updatedPlayer)

        println("You give the ${item.name} to ${npc.name}.")

        // Track delivery for quests
        trackQuests(QuestAction.DeliveredItem(item.id, npc.id))
    }

    private fun handleTalk(target: String) {
        val room = worldState.getCurrentRoom() ?: return

        // Find the NPC in the room
        val npc = room.entities.filterIsInstance<com.jcraw.mud.core.Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (npc == null) {
            println("There's no one here by that name.")
            return
        }

        // Generate dialogue
        if (npcInteractionGenerator != null) {
            println("\nYou speak to ${npc.name}...")
            val dialogue = runBlocking {
                npcInteractionGenerator.generateDialogue(npc, worldState.player)
            }
            println("\n${npc.name} says: \"$dialogue\"")
        } else {
            // Fallback dialogue without LLM
            if (npc.isHostile) {
                println("\n${npc.name} glares at you menacingly and says nothing.")
            } else {
                println("\n${npc.name} nods at you in acknowledgment.")
            }
        }

        // Track NPC conversation for quests
        trackQuests(QuestAction.TalkedToNPC(npc.id))
    }

    private fun handleAttack(target: String?) {
        val room = worldState.getCurrentRoom() ?: return

        // If already in combat
        if (worldState.player.isInCombat()) {
            val result = combatResolver.executePlayerAttack(worldState)

            // Generate narrative
            val narrative = if (combatNarrator != null && !result.playerDied && !result.npcDied) {
                val combat = worldState.player.activeCombat!!
                val npc = room.entities.filterIsInstance<Entity.NPC>()
                    .find { it.id == combat.combatantNpcId }

                if (npc != null) {
                    runBlocking {
                        combatNarrator.narrateCombatRound(
                            worldState, npc, result.playerDamage, result.npcDamage,
                            result.npcDied, result.playerDied
                        )
                    }
                } else {
                    result.narrative
                }
            } else {
                result.narrative
            }

            println("\n$narrative")

            // Update world state
            val combatState = result.newCombatState
            if (combatState != null) {
                // Sync player's actual health with combat state
                val updatedPlayer = worldState.player
                    .updateCombat(combatState)
                    .copy(health = combatState.playerHealth)
                worldState = worldState.updatePlayer(updatedPlayer)
                describeCurrentRoom()  // Show updated combat status
            } else {
                // Combat ended - save combat info before ending
                val endedCombat = worldState.player.activeCombat
                // Sync final health before ending combat
                val playerWithHealth = if (endedCombat != null) {
                    worldState.player.copy(health = endedCombat.playerHealth)
                } else {
                    worldState.player
                }
                worldState = worldState.updatePlayer(playerWithHealth.endCombat())

                when {
                    result.npcDied -> {
                        println("\nVictory! The enemy has been defeated!")
                        // Remove NPC from room
                        if (endedCombat != null) {
                            worldState = worldState.removeEntityFromRoom(room.id, endedCombat.combatantNpcId) ?: worldState

                            // Track NPC kill for quests
                            trackQuests(QuestAction.KilledNPC(endedCombat.combatantNpcId))
                        }
                    }
                    result.playerDied -> {
                        println("\nYou have been defeated! Game over.")
                        println("\nPress any key to play again...")
                        readLine()  // Wait for any input

                        // Restart the game
                        worldState = initialWorldState
                        println("\n" + "=" * 60)
                        println("  Restarting Adventure...")
                        println("=" * 60)
                        printWelcome()
                        describeCurrentRoom()
                    }
                    result.playerFled -> {
                        println("\nYou have fled from combat.")
                    }
                }
            }
            return
        }

        // Initiate combat with target
        if (target.isNullOrBlank()) {
            println("Attack whom?")
            return
        }

        // Find the NPC
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (npc == null) {
            println("You don't see anyone by that name to attack.")
            return
        }

        // Start combat
        val result = combatResolver.initiateCombat(worldState, npc.id)
        if (result == null) {
            println("You cannot initiate combat with that target.")
            return
        }

        // Generate narrative for combat start
        val narrative = if (combatNarrator != null) {
            runBlocking {
                combatNarrator.narrateCombatStart(worldState, npc)
            }
        } else {
            result.narrative
        }

        println("\n$narrative")

        if (result.newCombatState != null) {
            worldState = worldState.updatePlayer(worldState.player.updateCombat(result.newCombatState))
            describeCurrentRoom()  // Show combat status
        }
    }

    private fun handleEquip(target: String) {
        // Find the item in inventory
        val item = worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(target.lowercase()) ||
            invItem.id.lowercase().contains(target.lowercase())
        }

        if (item == null) {
            println("You don't have that in your inventory.")
            return
        }

        when (item.itemType) {
            ItemType.WEAPON -> {
                val oldWeapon = worldState.player.equippedWeapon
                worldState = worldState.updatePlayer(worldState.player.equipWeapon(item))

                if (oldWeapon != null) {
                    println("You unequip the ${oldWeapon.name} and equip the ${item.name} (+${item.damageBonus} damage).")
                } else {
                    println("You equip the ${item.name} (+${item.damageBonus} damage).")
                }
            }
            ItemType.ARMOR -> {
                val oldArmor = worldState.player.equippedArmor
                worldState = worldState.updatePlayer(worldState.player.equipArmor(item))

                if (oldArmor != null) {
                    println("You unequip the ${oldArmor.name} and equip the ${item.name} (+${item.defenseBonus} defense).")
                } else {
                    println("You equip the ${item.name} (+${item.defenseBonus} defense).")
                }
            }
            else -> {
                println("You can't equip that.")
            }
        }
    }

    private fun handleUse(target: String) {
        // Find the item in inventory
        val item = worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(target.lowercase()) ||
            invItem.id.lowercase().contains(target.lowercase())
        }

        if (item == null) {
            println("You don't have that in your inventory.")
            return
        }

        if (!item.isUsable) {
            println("You can't use that.")
            return
        }

        when (item.itemType) {
            ItemType.CONSUMABLE -> {
                val oldHealth = worldState.player.health
                val inCombat = worldState.player.isInCombat()

                // Consume the item and heal
                worldState = worldState.updatePlayer(worldState.player.useConsumable(item))
                val healedAmount = worldState.player.health - oldHealth

                if (healedAmount > 0) {
                    println("\nYou consume the ${item.name} and restore $healedAmount HP.")
                    println("Current health: ${worldState.player.health}/${worldState.player.maxHealth}")
                } else {
                    println("\nYou consume the ${item.name}, but you're already at full health.")
                }

                // If in combat, the NPC gets a free attack (using an item consumes your turn)
                if (inCombat) {
                    val combat = worldState.player.activeCombat!!
                    val room = worldState.getCurrentRoom() ?: return
                    val npc = room.entities.filterIsInstance<Entity.NPC>()
                        .find { it.id == combat.combatantNpcId }

                    if (npc != null) {
                        // Calculate NPC damage
                        val npcDamage = calculateNpcDamage(npc)
                        val afterNpcAttack = combat.applyNpcDamage(npcDamage)

                        println("\nThe enemy strikes back for $npcDamage damage while you drink!")

                        // Check if player died
                        if (afterNpcAttack.playerHealth <= 0) {
                            worldState = worldState.updatePlayer(worldState.player.endCombat())
                            println("\nYou have been defeated! Game over.")
                            println("\nPress any key to play again...")
                            readLine()  // Wait for any input

                            // Restart the game
                            worldState = initialWorldState
                            println("\n" + "=" * 60)
                            println("  Restarting Adventure...")
                            println("=" * 60)
                            printWelcome()
                            describeCurrentRoom()
                        } else {
                            // Update combat state with new health
                            val updatedPlayer = worldState.player
                                .updateCombat(afterNpcAttack)
                                .copy(health = afterNpcAttack.playerHealth)
                            worldState = worldState.updatePlayer(updatedPlayer)
                            describeCurrentRoom()  // Show updated combat status
                        }
                    }
                }
            }
            ItemType.WEAPON -> {
                println("Try 'equip ${item.name}' to equip this weapon.")
            }
            else -> {
                println("You're not sure how to use that.")
            }
        }
    }

    /**
     * Calculate damage dealt by NPC attack (helper for potion use during combat).
     * Base damage + STR modifier - player armor defense.
     */
    private fun calculateNpcDamage(npc: Entity.NPC): Int {
        // Base damage 3-12 + STR modifier - armor defense
        val baseDamage = kotlin.random.Random.nextInt(3, 13)
        val strModifier = npc.stats.strModifier()
        val armorDefense = worldState.player.getArmorDefenseBonus()
        return (baseDamage + strModifier - armorDefense).coerceAtLeast(1)
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

    private fun handlePersuade(target: String) {
        val room = worldState.getCurrentRoom() ?: return

        // Find the NPC in the room
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (npc == null) {
            println("There's no one here by that name.")
            return
        }

        val challenge = npc.persuasionChallenge
        if (challenge == null) {
            println("${npc.name} doesn't seem interested in negotiating.")
            return
        }

        if (npc.hasBeenPersuaded) {
            println("You've already persuaded ${npc.name}.")
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

            // Mark NPC as persuaded
            val updatedNpc = npc.copy(hasBeenPersuaded = true)
            worldState = worldState.replaceEntity(room.id, npc.id, updatedNpc) ?: worldState
        } else {
            println("\n❌ Failure!")
            println(challenge.failureDescription)
        }
    }

    private fun handleIntimidate(target: String) {
        val room = worldState.getCurrentRoom() ?: return

        // Find the NPC in the room
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (npc == null) {
            println("There's no one here by that name.")
            return
        }

        val challenge = npc.intimidationChallenge
        if (challenge == null) {
            println("${npc.name} doesn't seem easily intimidated.")
            return
        }

        if (npc.hasBeenIntimidated) {
            println("${npc.name} is already frightened of you.")
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

            // Mark NPC as intimidated
            val updatedNpc = npc.copy(hasBeenIntimidated = true)
            worldState = worldState.replaceEntity(room.id, npc.id, updatedNpc) ?: worldState
        } else {
            println("\n❌ Failure!")
            println(challenge.failureDescription)
        }
    }

    private fun handleEmote(emoteType: String, target: String?) {
        // Emotes are not yet fully integrated in console mode
        // This is a placeholder for future social system integration
        if (target.isNullOrBlank()) {
            println("\nYou ${emoteType.lowercase()}.")
        } else {
            println("\nYou ${emoteType.lowercase()} at $target.")
        }
    }

    private fun handleAskQuestion(npcTarget: String, topic: String) {
        val room = worldState.getCurrentRoom() ?: return

        // Find the NPC in the room
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(npcTarget.lowercase()) ||
                entity.id.lowercase().contains(npcTarget.lowercase())
            }

        if (npc == null) {
            println("There's no one here by that name.")
            return
        }

        // Knowledge queries not yet fully integrated in console mode
        // This is a placeholder for future social system integration
        println("\n${npc.name} doesn't seem to know anything about that.")
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
