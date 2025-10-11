package com.jcraw.mud.testbot

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.reasoning.*
import com.jcraw.sophia.llm.LLMClient

/**
 * In-memory game engine implementation for testing.
 * Processes intents without stdio, suitable for automated testing.
 */
class InMemoryGameEngine(
    private val initialWorldState: WorldState,
    private val descriptionGenerator: RoomDescriptionGenerator? = null,
    private val npcInteractionGenerator: NPCInteractionGenerator? = null,
    private val combatNarrator: CombatNarrator? = null,
    private val memoryManager: MemoryManager? = null
) : GameEngineInterface {

    private var worldState: WorldState = initialWorldState
    private var running = true
    private val combatResolver = CombatResolver()
    private val skillCheckResolver = SkillCheckResolver()

    override suspend fun processInput(input: String): String {
        if (!running) return "Game is not running."

        val intent = parseInput(input)
        return processIntent(intent)
    }

    override fun getWorldState(): WorldState = worldState

    override fun reset() {
        worldState = initialWorldState
        running = true
    }

    override fun isRunning(): Boolean = running

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
            else -> Intent.Invalid("Unknown command: $command")
        }
    }

    private suspend fun processIntent(intent: Intent): String {
        return when (intent) {
            is Intent.Move -> handleMove(intent.direction)
            is Intent.Look -> handleLook(intent.target)
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
            is Intent.Help -> handleHelp()
            is Intent.Quit -> handleQuit()
            is Intent.Invalid -> intent.message
            else -> "Command not supported in test mode: $intent"
        }
    }

    private suspend fun handleMove(direction: Direction): String {
        val newState = worldState.movePlayer(direction)
        return if (newState != null) {
            worldState = newState
            val room = worldState.getCurrentRoom()!!
            buildRoomDescription(room)
        } else {
            "You can't go that way."
        }
    }

    private suspend fun handleLook(target: String?): String {
        val room = worldState.getCurrentRoom() ?: return "You are nowhere."

        return if (target == null) {
            buildRoomDescription(room)
        } else {
            val entity = room.entities.find { e ->
                e.name.lowercase().contains(target.lowercase()) || e.id.lowercase().contains(target.lowercase())
            }
            entity?.description ?: "You don't see that here."
        }
    }

    private fun handleInventory(): String {
        val lines = mutableListOf<String>()
        lines.add("Inventory:")

        if (worldState.player.equippedWeapon != null) {
            lines.add("  Weapon: ${worldState.player.equippedWeapon!!.name} (+${worldState.player.equippedWeapon!!.damageBonus})")
        }
        if (worldState.player.equippedArmor != null) {
            lines.add("  Armor: ${worldState.player.equippedArmor!!.name} (+${worldState.player.equippedArmor!!.defenseBonus})")
        }

        if (worldState.player.inventory.isEmpty()) {
            lines.add("  Carrying: (nothing)")
        } else {
            lines.add("  Carrying: ${worldState.player.inventory.joinToString { it.name }}")
        }

        return lines.joinToString("\n")
    }

    private fun handleTake(target: String): String {
        val room = worldState.getCurrentRoom() ?: return "You are nowhere."
        val item = room.entities.filterIsInstance<Entity.Item>()
            .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

        return if (item != null && item.isPickupable) {
            val newState = worldState.removeEntityFromRoom(room.id, item.id)
                ?.updatePlayer(worldState.player.addToInventory(item))
            if (newState != null) {
                worldState = newState
                "You take the ${item.name}."
            } else {
                "Failed to take item."
            }
        } else {
            "You can't take that."
        }
    }

    private fun handleDrop(target: String): String {
        val room = worldState.getCurrentRoom() ?: return "You are nowhere."
        val item = worldState.player.inventory.find {
            it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase())
        }

        return if (item != null) {
            val newState = worldState.updatePlayer(worldState.player.removeFromInventory(item.id))
                .addEntityToRoom(room.id, item)
            if (newState != null) {
                worldState = newState
                "You drop the ${item.name}."
            } else {
                "Failed to drop item."
            }
        } else {
            "You don't have that."
        }
    }

    private suspend fun handleTalk(target: String): String {
        val room = worldState.getCurrentRoom() ?: return "You are nowhere."
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

        return if (npc != null) {
            if (npcInteractionGenerator != null) {
                val dialogue = npcInteractionGenerator.generateDialogue(npc, worldState.player)
                "${npc.name} says: \"$dialogue\""
            } else {
                "${npc.name} acknowledges you."
            }
        } else {
            "No one by that name here."
        }
    }

    private suspend fun handleAttack(target: String?): String {
        val room = worldState.getCurrentRoom() ?: return "You are nowhere."

        // In combat
        if (worldState.player.isInCombat()) {
            val result = combatResolver.executePlayerAttack(worldState)
            val narrative = result.narrative

            if (result.newCombatState != null) {
                worldState = worldState.updatePlayer(worldState.player.updateCombat(result.newCombatState))
            } else {
                worldState = worldState.updatePlayer(worldState.player.endCombat())
                if (result.npcDied) {
                    val combat = worldState.player.activeCombat
                    if (combat != null) {
                        worldState = worldState.removeEntityFromRoom(room.id, combat.combatantNpcId) ?: worldState
                    }
                }
                if (result.playerDied) {
                    running = false
                }
            }
            return narrative
        }

        // Start combat
        if (target.isNullOrBlank()) return "Attack whom?"

        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

        return if (npc != null) {
            val result = combatResolver.initiateCombat(worldState, npc.id)
            if (result != null) {
                worldState = worldState.updatePlayer(worldState.player.updateCombat(result.newCombatState!!))
                result.narrative
            } else {
                "Cannot attack that."
            }
        } else {
            "No one by that name here."
        }
    }

    private fun handleEquip(target: String): String {
        val item = worldState.player.inventory.find {
            it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase())
        }

        return if (item != null) {
            when (item.itemType) {
                ItemType.WEAPON -> {
                    worldState = worldState.updatePlayer(worldState.player.equipWeapon(item))
                    "You equip the ${item.name} (+${item.damageBonus} damage)."
                }
                ItemType.ARMOR -> {
                    worldState = worldState.updatePlayer(worldState.player.equipArmor(item))
                    "You equip the ${item.name} (+${item.defenseBonus} defense)."
                }
                else -> "You can't equip that."
            }
        } else {
            "You don't have that."
        }
    }

    private fun handleUse(target: String): String {
        val item = worldState.player.inventory.find {
            it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase())
        }

        return if (item != null && item.isUsable && item.itemType == ItemType.CONSUMABLE) {
            val oldHealth = worldState.player.health
            worldState = worldState.updatePlayer(worldState.player.useConsumable(item))
            val healedAmount = worldState.player.health - oldHealth
            if (healedAmount > 0) {
                "You use the ${item.name} and restore $healedAmount HP."
            } else {
                "You use the ${item.name}, but you're at full health."
            }
        } else {
            "You can't use that."
        }
    }

    private fun handleCheck(target: String): String {
        val room = worldState.getCurrentRoom() ?: return "You are nowhere."
        val feature = room.entities.filterIsInstance<Entity.Feature>()
            .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

        if (feature == null || !feature.isInteractable || feature.skillChallenge == null) {
            return "Nothing to check here."
        }

        if (feature.isCompleted) {
            return "You've already completed that."
        }

        val result = skillCheckResolver.checkPlayer(worldState.player, feature.skillChallenge!!.statType, feature.skillChallenge!!.difficulty)

        return if (result.success) {
            worldState = worldState.replaceEntity(room.id, feature.id, feature.copy(isCompleted = true)) ?: worldState
            "Success! ${feature.skillChallenge!!.successDescription}"
        } else {
            "Failed! ${feature.skillChallenge!!.failureDescription}"
        }
    }

    private fun handlePersuade(target: String): String {
        val room = worldState.getCurrentRoom() ?: return "You are nowhere."
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

        if (npc == null || npc.persuasionChallenge == null) {
            return "Cannot persuade that."
        }

        if (npc.hasBeenPersuaded) {
            return "Already persuaded."
        }

        val result = skillCheckResolver.checkPlayer(worldState.player, npc.persuasionChallenge!!.statType, npc.persuasionChallenge!!.difficulty)

        return if (result.success) {
            worldState = worldState.replaceEntity(room.id, npc.id, npc.copy(hasBeenPersuaded = true)) ?: worldState
            "Success! ${npc.persuasionChallenge!!.successDescription}"
        } else {
            "Failed! ${npc.persuasionChallenge!!.failureDescription}"
        }
    }

    private fun handleIntimidate(target: String): String {
        val room = worldState.getCurrentRoom() ?: return "You are nowhere."
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

        if (npc == null || npc.intimidationChallenge == null) {
            return "Cannot intimidate that."
        }

        if (npc.hasBeenIntimidated) {
            return "Already intimidated."
        }

        val result = skillCheckResolver.checkPlayer(worldState.player, npc.intimidationChallenge!!.statType, npc.intimidationChallenge!!.difficulty)

        return if (result.success) {
            worldState = worldState.replaceEntity(room.id, npc.id, npc.copy(hasBeenIntimidated = true)) ?: worldState
            "Success! ${npc.intimidationChallenge!!.successDescription}"
        } else {
            "Failed! ${npc.intimidationChallenge!!.failureDescription}"
        }
    }

    private fun handleHelp(): String = "Available commands: move, look, inventory, take, drop, talk, attack, equip, use, check, persuade, intimidate, quit"

    private fun handleQuit(): String {
        running = false
        return "Goodbye!"
    }

    private suspend fun buildRoomDescription(room: com.jcraw.mud.core.Room): String {
        val description = if (descriptionGenerator != null) {
            try {
                descriptionGenerator.generateDescription(room)
            } catch (e: Exception) {
                room.traits.joinToString(". ") + "."
            }
        } else {
            room.traits.joinToString(". ") + "."
        }

        val exits = if (room.exits.isNotEmpty()) {
            "\nExits: ${room.exits.keys.joinToString { it.displayName }}"
        } else ""

        val entities = if (room.entities.isNotEmpty()) {
            "\nYou see: ${room.entities.joinToString { it.name }}"
        } else ""

        return "${room.name}\n$description$exits$entities"
    }
}
