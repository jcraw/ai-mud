package com.jcraw.app

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.SampleDungeon
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.reasoning.RoomDescriptionGenerator
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
        println("⚠️  OpenAI API key not found - using simple trait-based descriptions")
        println("   Set OPENAI_API_KEY environment variable or openai.api.key in local.properties\n")
        MudGame(descriptionGenerator = null)
    } else {
        println("✅ Using LLM-powered room descriptions\n")
        val llmClient = OpenAIClient(apiKey)
        val descriptionGenerator = RoomDescriptionGenerator(llmClient)
        MudGame(descriptionGenerator = descriptionGenerator)
    }

    game.start()
}

class MudGame(private val descriptionGenerator: RoomDescriptionGenerator? = null) {
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

    private fun handleHelp() {
        println("""
            |Available Commands:
            |  Movement:
            |    go <direction>, n/s/e/w, north/south/east/west, etc.
            |
            |  Actions:
            |    look [target]    - Examine room or specific object
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
