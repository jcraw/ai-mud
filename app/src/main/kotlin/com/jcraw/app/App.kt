package com.jcraw.app

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.SampleDungeon
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.reasoning.RoomDescriptionGenerator
import com.jcraw.mud.reasoning.NPCInteractionGenerator
import com.jcraw.mud.reasoning.CombatResolver
import com.jcraw.mud.reasoning.CombatNarrator
import com.jcraw.mud.reasoning.SkillCheckResolver
import com.jcraw.mud.reasoning.procedural.ProceduralDungeonBuilder
import com.jcraw.mud.reasoning.procedural.DungeonTheme
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.PersistenceManager
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.coroutines.runBlocking

/**
 * Main game application - console-based MUD interface
 */
fun main() {
    // Get OpenAI API key from environment or system property (from local.properties)
    val apiKey = System.getenv("OPENAI_API_KEY")
        ?: System.getProperty("openai.api.key")

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

    // Ask user if they want sample or procedural dungeon
    println("Select dungeon type:")
    println("  1. Sample Dungeon (handcrafted, 6 rooms)")
    println("  2. Procedural Crypt (ancient tombs)")
    println("  3. Procedural Castle (ruined fortress)")
    println("  4. Procedural Cave (dark caverns)")
    println("  5. Procedural Temple (forgotten shrine)")
    print("\nEnter choice (1-5) [default: 1]: ")

    val choice = readLine()?.trim() ?: "1"

    // Generate world state based on choice
    val worldState = when (choice) {
        "2" -> {
            print("Number of rooms [default: 10]: ")
            val roomCount = readLine()?.toIntOrNull() ?: 10
            ProceduralDungeonBuilder.generateCrypt(roomCount)
        }
        "3" -> {
            print("Number of rooms [default: 10]: ")
            val roomCount = readLine()?.toIntOrNull() ?: 10
            ProceduralDungeonBuilder.generateCastle(roomCount)
        }
        "4" -> {
            print("Number of rooms [default: 10]: ")
            val roomCount = readLine()?.toIntOrNull() ?: 10
            ProceduralDungeonBuilder.generateCave(roomCount)
        }
        "5" -> {
            print("Number of rooms [default: 10]: ")
            val roomCount = readLine()?.toIntOrNull() ?: 10
            ProceduralDungeonBuilder.generateTemple(roomCount)
        }
        else -> {
            println("Using Sample Dungeon")
            SampleDungeon.createInitialWorldState()
        }
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
            initialWorldState = worldState,
            descriptionGenerator = descriptionGenerator,
            npcInteractionGenerator = npcInteractionGenerator,
            combatNarrator = combatNarrator,
            memoryManager = memoryManager,
            combatResolver = combatResolver,
            skillCheckResolver = skillCheckResolver
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
                initialWorldState = worldState,
                descriptionGenerator = descriptionGenerator,
                npcInteractionGenerator = npcInteractionGenerator,
                combatNarrator = combatNarrator,
                memoryManager = memoryManager
            )
        } else {
            MudGame(
                initialWorldState = worldState,
                descriptionGenerator = null,
                npcInteractionGenerator = null,
                combatNarrator = null,
                memoryManager = null
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
    private val memoryManager: MemoryManager? = null
) {
    private var worldState: WorldState = initialWorldState
    private var running = true
    private val combatResolver = CombatResolver()
    private val skillCheckResolver = SkillCheckResolver()
    private val persistenceManager = PersistenceManager()

    fun start() {
        printWelcome()
        describeCurrentRoom()

        while (running) {
            print("\n> ")
            val input = readLine()?.trim() ?: continue

            if (input.isBlank()) continue

            val intent = parseInput(input)
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

    private fun parseInput(input: String): Intent {
        val parts = input.lowercase().split(" ", limit = 2)
        val command = parts[0]
        val args = parts.getOrNull(1)

        return when (command) {
            "go", "move", "n", "s", "e", "w", "north", "south", "east", "west",
            "ne", "nw", "se", "sw", "northeast", "northwest", "southeast", "southwest",
            "u", "d", "up", "down" -> {
                val direction = when (command) {
                    "n", "north" -> Direction.NORTH
                    "s", "south" -> Direction.SOUTH
                    "e", "east" -> Direction.EAST
                    "w", "west" -> Direction.WEST
                    "ne", "northeast" -> Direction.NORTHEAST
                    "nw", "northwest" -> Direction.NORTHWEST
                    "se", "southeast" -> Direction.SOUTHEAST
                    "sw", "southwest" -> Direction.SOUTHWEST
                    "u", "up" -> Direction.UP
                    "d", "down" -> Direction.DOWN
                    "go", "move" -> Direction.fromString(args ?: "") ?: return Intent.Invalid("Go where?")
                    else -> return Intent.Invalid("Unknown direction")
                }
                Intent.Move(direction)
            }
            "look", "l" -> {
                Intent.Look(args)
            }
            "interact" -> {
                if (args.isNullOrBlank()) {
                    Intent.Invalid("Interact with what?")
                } else {
                    Intent.Interact(args)
                }
            }
            "take", "get", "pickup", "pick" -> {
                if (args.isNullOrBlank()) {
                    Intent.Invalid("Take what?")
                } else {
                    Intent.Take(args)
                }
            }
            "drop", "put" -> {
                if (args.isNullOrBlank()) {
                    Intent.Invalid("Drop what?")
                } else {
                    Intent.Drop(args)
                }
            }
            "talk", "speak", "chat" -> {
                if (args.isNullOrBlank()) {
                    Intent.Invalid("Talk to whom?")
                } else {
                    Intent.Talk(args)
                }
            }
            "attack", "kill", "fight", "hit" -> {
                // In combat, target is optional (attack current combatant)
                // Out of combat, target is required to initiate combat
                Intent.Attack(args)
            }
            "equip", "wield", "wear" -> {
                if (args.isNullOrBlank()) {
                    Intent.Invalid("Equip what?")
                } else {
                    Intent.Equip(args)
                }
            }
            "use", "consume", "drink", "eat" -> {
                if (args.isNullOrBlank()) {
                    Intent.Invalid("Use what?")
                } else {
                    Intent.Use(args)
                }
            }
            "check", "test", "attempt", "try" -> {
                if (args.isNullOrBlank()) {
                    Intent.Invalid("Check what?")
                } else {
                    Intent.Check(args)
                }
            }
            "persuade", "convince" -> {
                if (args.isNullOrBlank()) {
                    Intent.Invalid("Persuade whom?")
                } else {
                    Intent.Persuade(args)
                }
            }
            "intimidate", "threaten" -> {
                if (args.isNullOrBlank()) {
                    Intent.Invalid("Intimidate whom?")
                } else {
                    Intent.Intimidate(args)
                }
            }
            "save" -> Intent.Save(args ?: "quicksave")
            "load" -> Intent.Load(args ?: "quicksave")
            "inventory", "i" -> Intent.Inventory
            "help", "h", "?" -> Intent.Help
            "quit", "exit", "q" -> Intent.Quit
            else -> Intent.Invalid("Unknown command: $command. Type 'help' for available commands.")
        }
    }

    private fun processIntent(intent: Intent) {
        when (intent) {
            is Intent.Move -> handleMove(intent.direction)
            is Intent.Look -> handleLook(intent.target)
            is Intent.Interact -> handleInteract(intent.target)
            is Intent.Inventory -> handleInventory()
            is Intent.Take -> handleTake(intent.target)
            is Intent.Drop -> handleDrop(intent.target)
            is Intent.Talk -> handleTalk(intent.target)
            is Intent.Attack -> handleAttack(intent.target)
            is Intent.Equip -> handleEquip(intent.target)
            is Intent.Use -> handleUse(intent.target)
            is Intent.Check -> handleCheck(intent.target)
            is Intent.Persuade -> handlePersuade(intent.target)
            is Intent.Intimidate -> handleIntimidate(intent.target)
            is Intent.Save -> handleSave(intent.saveName)
            is Intent.Load -> handleLoad(intent.saveName)
            is Intent.Help -> handleHelp()
            is Intent.Quit -> handleQuit()
            is Intent.Invalid -> println(intent.message)
        }
    }

    private fun handleMove(direction: Direction) {
        val newState = worldState.movePlayer(direction)

        if (newState == null) {
            println("You can't go that way.")
            return
        }

        worldState = newState
        println("You move ${direction.displayName}.")
        describeCurrentRoom()
    }

    private fun handleLook(target: String?) {
        if (target == null) {
            // Look at room
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
                println("You don't see that here.")
            }
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
            println("You don't see that here.")
            return
        }

        if (!item.isPickupable) {
            println("You can't take that.")
            return
        }

        // Remove item from room and add to inventory
        val newState = worldState
            .removeEntityFromRoom(room.id, item.id)
            ?.updatePlayer(worldState.player.addToInventory(item))

        if (newState != null) {
            worldState = newState
            println("You take the ${item.name}.")
        } else {
            println("Something went wrong.")
        }
    }

    private fun handleDrop(target: String) {
        val room = worldState.getCurrentRoom() ?: return

        // Find the item in inventory
        val item = worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(target.lowercase()) ||
            invItem.id.lowercase().contains(target.lowercase())
        }

        if (item == null) {
            println("You don't have that.")
            return
        }

        // Remove from inventory and add to room
        val newState = worldState
            .updatePlayer(worldState.player.removeFromInventory(item.id))
            .addEntityToRoom(room.id, item)

        if (newState != null) {
            worldState = newState
            println("You drop the ${item.name}.")
        } else {
            println("Something went wrong.")
        }
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
            if (result.newCombatState != null) {
                worldState = worldState.updatePlayer(worldState.player.updateCombat(result.newCombatState))
                describeCurrentRoom()  // Show updated combat status
            } else {
                // Combat ended - save combat info before ending
                val endedCombat = worldState.player.activeCombat
                worldState = worldState.updatePlayer(worldState.player.endCombat())

                when {
                    result.npcDied -> {
                        println("\nVictory! The enemy has been defeated!")
                        // Remove NPC from room
                        if (endedCombat != null) {
                            worldState = worldState.removeEntityFromRoom(room.id, endedCombat.combatantNpcId) ?: worldState
                        }
                    }
                    result.playerDied -> {
                        println("\nYou have been defeated! Game over.")
                        running = false
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
                worldState = worldState.updatePlayer(worldState.player.useConsumable(item))
                val healedAmount = worldState.player.health - oldHealth

                if (healedAmount > 0) {
                    println("You consume the ${item.name} and restore $healedAmount HP.")
                    println("Current health: ${worldState.player.health}/${worldState.player.maxHealth}")
                } else {
                    println("You consume the ${item.name}, but you're already at full health.")
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

    private fun handleCheck(target: String) {
        val room = worldState.getCurrentRoom() ?: return

        // Find the feature in the room
        val feature = room.entities.filterIsInstance<Entity.Feature>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
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
            |    take/get <item>      - Pick up an item
            |    drop/put <item>      - Drop an item from inventory
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
            |  Meta:
            |    save [name]          - Save game (defaults to 'quicksave')
            |    load [name]          - Load game (defaults to 'quicksave')
            |    help, h, ?           - Show this help
            |    quit, exit, q        - Quit game
        """.trimMargin())
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
    private val skillCheckResolver: SkillCheckResolver
) {
    private lateinit var gameServer: GameServer

    fun start() = runBlocking {
        // Create fallback components if needed
        val effectiveMemoryManager = memoryManager ?: createFallbackMemoryManager()
        val effectiveDescGenerator = descriptionGenerator ?: createFallbackDescriptionGenerator(effectiveMemoryManager)
        val effectiveNpcGenerator = npcInteractionGenerator ?: createFallbackNPCGenerator(effectiveMemoryManager)
        val effectiveCombatNarrator = combatNarrator ?: createFallbackCombatNarrator(effectiveMemoryManager)

        // Initialize game server
        gameServer = GameServer(
            worldState = initialWorldState,
            memoryManager = effectiveMemoryManager,
            roomDescriptionGenerator = effectiveDescGenerator,
            npcInteractionGenerator = effectiveNpcGenerator,
            combatResolver = combatResolver,
            combatNarrator = effectiveCombatNarrator,
            skillCheckResolver = skillCheckResolver
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

            // Parse intent
            val intent = parseInput(input.trim())

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

    private fun parseInput(input: String): Intent {
        val parts = input.lowercase().split(" ", limit = 2)
        val command = parts[0]
        val args = parts.getOrNull(1)

        return when (command) {
            "go", "move", "n", "s", "e", "w", "north", "south", "east", "west",
            "ne", "nw", "se", "sw", "northeast", "northwest", "southeast", "southwest",
            "u", "d", "up", "down" -> {
                val direction = when (command) {
                    "n", "north" -> Direction.NORTH
                    "s", "south" -> Direction.SOUTH
                    "e", "east" -> Direction.EAST
                    "w", "west" -> Direction.WEST
                    "ne", "northeast" -> Direction.NORTHEAST
                    "nw", "northwest" -> Direction.NORTHWEST
                    "se", "southeast" -> Direction.SOUTHEAST
                    "sw", "southwest" -> Direction.SOUTHWEST
                    "u", "up" -> Direction.UP
                    "d", "down" -> Direction.DOWN
                    "go", "move" -> Direction.fromString(args ?: "") ?: return Intent.Invalid("Go where?")
                    else -> return Intent.Invalid("Unknown direction")
                }
                Intent.Move(direction)
            }
            "look", "l" -> Intent.Look(args)
            "interact" -> if (args.isNullOrBlank()) Intent.Invalid("Interact with what?") else Intent.Interact(args)
            "take", "get", "pickup", "pick" -> if (args.isNullOrBlank()) Intent.Invalid("Take what?") else Intent.Take(args)
            "drop", "put" -> if (args.isNullOrBlank()) Intent.Invalid("Drop what?") else Intent.Drop(args)
            "talk", "speak", "chat" -> if (args.isNullOrBlank()) Intent.Invalid("Talk to whom?") else Intent.Talk(args)
            "attack", "kill", "fight", "hit" -> Intent.Attack(args)
            "equip", "wield", "wear" -> if (args.isNullOrBlank()) Intent.Invalid("Equip what?") else Intent.Equip(args)
            "use", "consume", "drink", "eat" -> if (args.isNullOrBlank()) Intent.Invalid("Use what?") else Intent.Use(args)
            "check", "test", "attempt", "try" -> if (args.isNullOrBlank()) Intent.Invalid("Check what?") else Intent.Check(args)
            "persuade", "convince" -> if (args.isNullOrBlank()) Intent.Invalid("Persuade whom?") else Intent.Persuade(args)
            "intimidate", "threaten" -> if (args.isNullOrBlank()) Intent.Invalid("Intimidate whom?") else Intent.Intimidate(args)
            "inventory", "i" -> Intent.Inventory
            "help", "h", "?" -> Intent.Help
            "quit", "exit", "q" -> Intent.Quit
            else -> Intent.Invalid("Unknown command: $command. Type 'help' for available commands.")
        }
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
