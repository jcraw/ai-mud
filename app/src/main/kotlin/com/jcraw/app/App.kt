package com.jcraw.app

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.SampleDungeon
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.reasoning.RoomDescriptionGenerator
import com.jcraw.mud.reasoning.NPCInteractionGenerator
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.coroutines.runBlocking

/**
 * Main game application - console-based MUD interface
 */
fun main() {
    // Get OpenAI API key from environment or system property (from local.properties)
    val apiKey = System.getenv("OPENAI_API_KEY")
        ?: System.getProperty("openai.api.key")

    val game = if (apiKey.isNullOrBlank()) {
        println("⚠️  OpenAI API key not found - using simple fallback mode")
        println("   Set OPENAI_API_KEY environment variable or openai.api.key in local.properties\n")
        MudGame(descriptionGenerator = null, npcInteractionGenerator = null)
    } else {
        println("✅ Using LLM-powered descriptions and NPC dialogue\n")
        val llmClient = OpenAIClient(apiKey)
        val descriptionGenerator = RoomDescriptionGenerator(llmClient)
        val npcInteractionGenerator = NPCInteractionGenerator(llmClient)
        MudGame(descriptionGenerator = descriptionGenerator, npcInteractionGenerator = npcInteractionGenerator)
    }

    game.start()
}

class MudGame(
    private val descriptionGenerator: RoomDescriptionGenerator? = null,
    private val npcInteractionGenerator: NPCInteractionGenerator? = null
) {
    private var worldState: WorldState = SampleDungeon.createInitialWorldState()
    private var running = true

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
        println("=" * 60)
        println("  AI-Powered MUD - Alpha Version")
        println("=" * 60)
        println("\nWelcome, ${worldState.player.name}!")
        println("Type 'help' for available commands.\n")
    }

    private fun describeCurrentRoom() {
        val room = worldState.getCurrentRoom() ?: return
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
            "interact", "use" -> {
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
        if (worldState.player.inventory.isEmpty()) {
            println("Your inventory is empty.")
        } else {
            println("Inventory:")
            worldState.player.inventory.forEach { item ->
                println("  - ${item.name}")
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

    private fun handleHelp() {
        println("""
            |Available Commands:
            |  Movement:
            |    go <direction>, n/s/e/w, north/south/east/west, etc.
            |
            |  Actions:
            |    look [target]    - Examine room or specific object
            |    take/get <item>  - Pick up an item
            |    drop/put <item>  - Drop an item from inventory
            |    talk/speak <npc> - Talk to an NPC
            |    interact <item>  - Interact with an object (not yet implemented)
            |    inventory, i     - View your inventory
            |
            |  Meta:
            |    help, h, ?       - Show this help
            |    quit, exit, q    - Quit game
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

// String repetition helper
private operator fun String.times(n: Int): String = repeat(n)
