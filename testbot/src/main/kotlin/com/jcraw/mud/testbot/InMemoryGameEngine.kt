package com.jcraw.mud.testbot

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.perception.IntentRecognizer
import com.jcraw.mud.reasoning.*
import com.jcraw.sophia.llm.LLMClient
import kotlinx.coroutines.runBlocking

/**
 * In-memory game engine implementation for testing.
 * Processes intents without stdio, suitable for automated testing.
 */
class InMemoryGameEngine(
    private val initialWorldState: WorldState,
    private val descriptionGenerator: RoomDescriptionGenerator? = null,
    private val npcInteractionGenerator: NPCInteractionGenerator? = null,
    private val combatNarrator: CombatNarrator? = null,
    private val memoryManager: MemoryManager? = null,
    private val llmClient: LLMClient? = null
) : GameEngineInterface {

    private var worldState: WorldState = initialWorldState
    private var running = true
    private val combatResolver = CombatResolver()
    private val skillCheckResolver = SkillCheckResolver()
    private val intentRecognizer = IntentRecognizer(llmClient)
    private val sceneryGenerator = SceneryDescriptionGenerator(llmClient)

    override suspend fun processInput(input: String): String {
        if (!running) return "Game is not running."

        val room = worldState.getCurrentRoom()
        val roomContext = room?.let { "${it.name}: ${it.traits.joinToString(", ")}" }
        val exitsWithNames = room?.let { buildExitsWithNames(it) }
        val intent = intentRecognizer.parseIntent(input, roomContext, exitsWithNames)
        return processIntent(intent)
    }

    override fun getWorldState(): WorldState = worldState

    override fun reset() {
        worldState = initialWorldState
        running = true
    }

    override fun isRunning(): Boolean = running

    private suspend fun processIntent(intent: Intent): String {
        return when (intent) {
            is Intent.Move -> handleMove(intent.direction)
            is Intent.Look -> handleLook(intent.target)
            is Intent.Inventory -> handleInventory()
            is Intent.Take -> handleTake(intent.target)
            is Intent.TakeAll -> handleTakeAll()
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
        // Check if in combat - must flee first
        if (worldState.player.isInCombat()) {
            val result = combatResolver.attemptFlee(worldState)

            return if (result.playerFled) {
                // Flee successful - update state and move
                worldState = worldState.updatePlayer(worldState.player.endCombat())

                val newState = worldState.movePlayer(direction)
                if (newState != null) {
                    worldState = newState
                    val room = worldState.getCurrentRoom()!!
                    "${result.narrative}\nYou move ${direction.displayName}.\n${buildRoomDescription(room)}"
                } else {
                    "${result.narrative}\nYou can't go that way."
                }
            } else if (result.playerDied) {
                // Player died trying to flee
                running = false
                result.narrative
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
                result.narrative
            }
        }

        // Normal movement (not in combat)
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

        if (target == null) {
            return buildRoomDescription(room)
        }

        // First check room entities
        val roomEntity = room.entities.find { e ->
            e.name.lowercase().contains(target.lowercase()) || e.id.lowercase().contains(target.lowercase())
        }

        if (roomEntity != null) {
            return roomEntity.description
        }

        // Then check inventory
        val inventoryItem = worldState.player.inventory.find { item ->
            item.name.lowercase().contains(target.lowercase()) || item.id.lowercase().contains(target.lowercase())
        }

        if (inventoryItem != null) {
            return inventoryItem.description
        }

        // Check equipped weapon
        val equippedWeapon = worldState.player.equippedWeapon
        if (equippedWeapon != null &&
            (equippedWeapon.name.lowercase().contains(target.lowercase()) ||
             equippedWeapon.id.lowercase().contains(target.lowercase()))) {
            return equippedWeapon.description + " (equipped)"
        }

        // Check equipped armor
        val equippedArmor = worldState.player.equippedArmor
        if (equippedArmor != null &&
            (equippedArmor.name.lowercase().contains(target.lowercase()) ||
             equippedArmor.id.lowercase().contains(target.lowercase()))) {
            return equippedArmor.description + " (equipped)"
        }

        // Finally try scenery
        val roomDescription = buildRoomDescription(room)
        val sceneryDescription = sceneryGenerator.describeScenery(target, room, roomDescription)
        return sceneryDescription ?: "You don't see that here."
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

    private fun handleTakeAll(): String {
        val room = worldState.getCurrentRoom() ?: return "You are nowhere."
        val items = room.entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }

        return if (items.isEmpty()) {
            "There are no items to take here."
        } else {
            var currentState = worldState
            val takenItems = mutableListOf<String>()

            items.forEach { item ->
                val newState = currentState.removeEntityFromRoom(room.id, item.id)
                    ?.updatePlayer(currentState.player.addToInventory(item))
                if (newState != null) {
                    currentState = newState
                    takenItems.add(item.name)
                }
            }

            worldState = currentState
            buildString {
                takenItems.forEach { append("You take the $it.\n") }
                append("You took ${takenItems.size} item${if (takenItems.size > 1) "s" else ""}.")
            }
        }
    }

    private fun handleDrop(target: String): String {
        val room = worldState.getCurrentRoom() ?: return "You are nowhere."

        // Check inventory first
        var item = worldState.player.inventory.find {
            it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase())
        }
        var isEquippedWeapon = false
        var isEquippedArmor = false

        // Check equipped weapon
        if (item == null && worldState.player.equippedWeapon != null) {
            val weapon = worldState.player.equippedWeapon!!
            if (weapon.name.lowercase().contains(target.lowercase()) || weapon.id.lowercase().contains(target.lowercase())) {
                item = weapon
                isEquippedWeapon = true
            }
        }

        // Check equipped armor
        if (item == null && worldState.player.equippedArmor != null) {
            val armor = worldState.player.equippedArmor!!
            if (armor.name.lowercase().contains(target.lowercase()) || armor.id.lowercase().contains(target.lowercase())) {
                item = armor
                isEquippedArmor = true
            }
        }

        return if (item != null) {
            val updatedPlayer = when {
                isEquippedWeapon -> worldState.player.copy(equippedWeapon = null)
                isEquippedArmor -> worldState.player.copy(equippedArmor = null)
                else -> worldState.player.removeFromInventory(item.id)
            }

            val newState = worldState.updatePlayer(updatedPlayer).addEntityToRoom(room.id, item)
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

            val combatState = result.newCombatState
            if (combatState != null) {
                // Sync player's actual health with combat state
                val updatedPlayer = worldState.player
                    .updateCombat(combatState)
                    .copy(health = combatState.playerHealth)
                worldState = worldState.updatePlayer(updatedPlayer)
            } else {
                // Combat ended - save combat info BEFORE ending
                val endedCombat = worldState.player.activeCombat
                // Sync final health before ending combat
                val playerWithHealth = if (endedCombat != null) {
                    worldState.player.copy(health = endedCombat.playerHealth)
                } else {
                    worldState.player
                }
                worldState = worldState.updatePlayer(playerWithHealth.endCombat())

                if (result.npcDied && endedCombat != null) {
                    worldState = worldState.removeEntityFromRoom(room.id, endedCombat.combatantNpcId) ?: worldState
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
}
