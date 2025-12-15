package com.jcraw.mud.testbot

import com.jcraw.mud.core.GameEngineInterface
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.DatabaseConfig
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.world.*
import com.jcraw.mud.memory.item.ItemDatabase
import com.jcraw.mud.memory.item.SQLiteItemRepository
import com.jcraw.mud.memory.item.ItemTemplateLoader
import com.jcraw.mud.memory.skill.SkillDatabase
import com.jcraw.mud.memory.skill.SQLiteSkillRepository
import com.jcraw.mud.memory.skill.SQLiteSkillComponentRepository
import com.jcraw.mud.perception.IntentRecognizer
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.reasoning.world.*
import com.jcraw.mud.reasoning.skill.SkillManager
import com.jcraw.sophia.llm.OpenAIClient

/**
 * Test game engine for testbot that uses V3 world generation.
 * Implements GameEngineInterface to work with TestBotRunner.
 *
 * This is a simplified game engine for automated testing.
 */
class V3TestGameEngine(
    private val ancientAbyssWorld: AncientAbyssWorld
) : GameEngineInterface {

    private var worldState: WorldState = ancientAbyssWorld.worldState
    private var running = true
    private val llmClient: OpenAIClient = ancientAbyssWorld.llmClient

    // Core components
    private val intentRecognizer = IntentRecognizer(llmClient)
    private val memoryManager = MemoryManager(llmClient)

    // Item system
    private val itemDatabase = ItemDatabase(DatabaseConfig.ITEMS_DB)
    private val itemRepository = SQLiteItemRepository(itemDatabase)

    init {
        ItemTemplateLoader.loadTemplatesFromResource(itemRepository)
    }

    // Skill system
    private val skillDatabase = SkillDatabase(DatabaseConfig.SKILLS_DB)
    private val skillRepo = SQLiteSkillRepository(skillDatabase)
    private val skillComponentRepo = SQLiteSkillComponentRepository(skillDatabase)
    private val skillManager = SkillManager(skillRepo, skillComponentRepo, memoryManager)

    // World system
    private val worldDatabase = ancientAbyssWorld.worldDatabase
    private val worldGenerator = ancientAbyssWorld.worldGenerator

    override suspend fun processInput(input: String): String {
        if (!running) return "Game has ended."

        val space = worldState.getCurrentSpace()
        val spaceContext = space?.let {
            val desc = if (it.description.isNotBlank()) it.description else "Unexplored area"
            "${it.name}: $desc"
        }
        val exitsWithNames = worldState.getCurrentGraphNode()?.let { buildExitsWithNames(it) }

        val intent = intentRecognizer.parseIntent(input, spaceContext, exitsWithNames)
        val result = processIntent(intent)

        // Advance game time by 1 tick
        worldState = worldState.advanceTime(1)

        return result
    }

    override fun getWorldState(): WorldState = worldState

    override fun reset() {
        worldState = ancientAbyssWorld.worldState
        running = true
    }

    override fun isRunning(): Boolean = running

    private fun buildExitsWithNames(node: com.jcraw.mud.core.GraphNodeComponent): Map<Direction, String> {
        val player = worldState.player
        return node.neighbors
            .filter { edge -> !edge.hidden || player.hasRevealedExit("${node.id}:${edge.targetId}") }
            .mapNotNull { edge ->
                val direction = Direction.fromString(edge.direction) ?: return@mapNotNull null
                val targetName = worldState.getSpace(edge.targetId)?.name ?: edge.targetId
                direction to targetName
            }
            .toMap()
    }

    private fun processIntent(intent: Intent): String {
        return when (intent) {
            is Intent.Move -> handleMove(intent.direction)
            is Intent.Look -> handleLook()
            is Intent.Inventory -> handleInventory()
            is Intent.Take -> handleTake(intent.target)
            is Intent.Drop -> handleDrop(intent.target)
            is Intent.Talk -> handleTalk(intent.target)
            is Intent.Attack -> handleAttack(intent.target)
            is Intent.Search -> handleSearch()
            is Intent.Quests -> handleViewQuests()
            is Intent.Rest -> handleRest()
            is Intent.Help -> handleHelp()
            is Intent.Quit -> handleQuit()
            is Intent.Invalid -> intent.message
            else -> "Command not supported in test mode."
        }
    }

    private fun handleMove(direction: Direction): String {
        val player = worldState.player
        val currentNode = worldState.getCurrentGraphNode()
            ?: return "You can't move - no navigation data."

        val playerSkills = skillManager.getSkillComponent(player.id)
        val edge = currentNode.neighbors.find { it.direction.equals(direction.name, ignoreCase = true) }

        if (edge == null || (edge.hidden && !player.hasRevealedExit("${currentNode.id}:${edge.targetId}"))) {
            return "You can't go $direction from here."
        }

        val newState = worldState.movePlayerV3(direction, playerSkills) ?: worldState
        if (newState == worldState) {
            return "You can't go $direction from here."
        }

        worldState = newState
        return buildLocationDescription()
    }

    private fun handleLook(): String {
        return buildLocationDescription()
    }

    private fun buildLocationDescription(): String {
        val space = worldState.getCurrentSpace() ?: return "You are in an unknown location."
        val node = worldState.getCurrentGraphNode()
        val player = worldState.player

        val sb = StringBuilder()
        sb.appendLine(space.name)
        sb.appendLine("-".repeat(space.name.length))
        sb.appendLine(space.description.ifBlank { "An unexplored area..." })

        if (node != null) {
            val visibleExits = node.neighbors.filter { e ->
                !e.hidden || player.hasRevealedExit("${node.id}:${e.targetId}")
            }
            if (visibleExits.isNotEmpty()) {
                val exitText = visibleExits.joinToString(", ") { e ->
                    val targetName = worldState.getSpace(e.targetId)?.name ?: e.targetId
                    "${e.direction} (${targetName})"
                }
                sb.appendLine("\nExits: $exitText")
            }
        }

        val entities = worldState.getEntitiesInSpace(player.currentRoomId)
        if (entities.isNotEmpty()) {
            sb.appendLine("\nYou see:")
            entities.forEach { entity ->
                sb.appendLine("  - ${entity.name}")
            }
        }

        return sb.toString().trim()
    }

    private fun handleInventory(): String {
        val inventory = worldState.player.inventoryComponent ?: return "You have no inventory."
        val sb = StringBuilder()
        sb.appendLine("Inventory:")
        if (inventory.items.isEmpty()) {
            sb.appendLine("  (empty)")
        } else {
            inventory.items.forEach { item ->
                val template = itemRepository.findTemplateById(item.templateId).getOrNull()
                sb.appendLine("  - ${template?.name ?: item.templateId}")
            }
        }
        sb.appendLine("\nGold: ${inventory.gold}")
        return sb.toString().trim()
    }

    private fun handleTake(target: String): String {
        val spaceId = worldState.player.currentRoomId
        val entities = worldState.getEntitiesInSpace(spaceId)
        val item = entities.filterIsInstance<Entity.Item>()
            .find { it.name.contains(target, ignoreCase = true) }
            ?: return "You don't see '$target' here."

        val inventory = worldState.player.inventoryComponent ?: return "You have no inventory."
        val newItem = ItemInstance(id = item.id, templateId = item.id)
        val newInventory = inventory.copy(items = inventory.items + newItem)
        val newPlayer = worldState.player.copy(inventoryComponent = newInventory)

        worldState = worldState.updatePlayer(newPlayer)
        worldState = worldState.removeEntityFromSpace(spaceId, item.id)

        return "You take the ${item.name}."
    }

    private fun handleDrop(target: String): String {
        val inventory = worldState.player.inventoryComponent ?: return "You have no inventory."
        val item = inventory.items.find {
            val template = itemRepository.findTemplateById(it.templateId).getOrNull()
            (template?.name ?: it.templateId).contains(target, ignoreCase = true)
        } ?: return "You don't have '$target'."

        val template = itemRepository.findTemplateById(item.templateId).getOrNull()
        val itemName = template?.name ?: item.templateId

        val newInventory = inventory.copy(items = inventory.items - item)
        worldState = worldState.updatePlayer(worldState.player.copy(inventoryComponent = newInventory))

        val droppedEntity = Entity.Item(id = item.id, name = itemName, description = "A dropped item.")
        worldState = worldState.addEntityToSpace(worldState.player.currentRoomId, droppedEntity)

        return "You drop the $itemName."
    }

    private fun handleTalk(target: String): String {
        val entities = worldState.getEntitiesInSpace(worldState.player.currentRoomId)
        val npc = entities.filterIsInstance<Entity.NPC>()
            .find { it.name.contains(target, ignoreCase = true) }
            ?: return "You don't see '$target' here."

        return "${npc.name} says: \"Hello, adventurer.\""
    }

    private fun handleAttack(target: String?): String {
        val spaceId = worldState.player.currentRoomId
        val space = worldState.getCurrentSpace()

        if (space?.isSafeZone == true) {
            return "This is a safe zone. Combat is not allowed here."
        }

        val entities = worldState.getEntitiesInSpace(spaceId)
        val npc = if (target != null) {
            entities.filterIsInstance<Entity.NPC>()
                .find { it.name.contains(target, ignoreCase = true) }
        } else {
            entities.filterIsInstance<Entity.NPC>().firstOrNull { it.isHostile }
        } ?: return "No target to attack."

        val combatComponent = npc.getComponent<CombatComponent>(ComponentType.COMBAT)
        if (combatComponent == null || combatComponent.currentHp <= 0) {
            return "${npc.name} is already dead."
        }

        // Simplified combat - just deal damage
        val damage = (5..15).random()
        val newHealth = (combatComponent.currentHp - damage).coerceAtLeast(0)
        val newCombatComponent = combatComponent.copy(currentHp = newHealth)
        val newNpc = npc.withComponent(newCombatComponent)

        worldState = worldState.replaceEntityInSpace(worldState.player.currentRoomId, npc.id, newNpc)

        val sb = StringBuilder()
        sb.appendLine("You attack ${npc.name} for $damage damage!")

        if (newHealth <= 0) {
            sb.appendLine("${npc.name} has been defeated!")
            worldState = worldState.removeEntityFromSpace(spaceId, npc.id)
        } else {
            sb.appendLine("${npc.name} has $newHealth health remaining.")
        }

        return sb.toString().trim()
    }

    private fun handleSearch(): String {
        val player = worldState.player
        val currentNode = worldState.getCurrentGraphNode() ?: return "Nothing to search here."

        val hiddenEdges = currentNode.neighbors.filter { edge ->
            edge.hidden && !player.hasRevealedExit("${currentNode.id}:${edge.targetId}")
        }

        if (hiddenEdges.isEmpty()) {
            return "You search the area but find nothing unusual."
        }

        val perceptionLevel = skillManager.getSkillComponent(player.id)?.getEffectiveLevel("Perception") ?: 0
        val roll = (1..20).random()
        val total = roll + perceptionLevel

        if (total >= 15) {
            val revealed = hiddenEdges.first()
            val exitKey = "${currentNode.id}:${revealed.targetId}"
            val newPlayer = player.revealExit(exitKey)
            worldState = worldState.updatePlayer(newPlayer)

            val targetName = worldState.getSpace(revealed.targetId)?.name ?: "an unknown area"
            return "You discover a hidden passage leading ${revealed.direction} to $targetName!"
        }

        return "You search carefully but find nothing unusual."
    }

    private fun handleViewQuests(): String {
        val player = worldState.player
        val sb = StringBuilder()

        sb.appendLine("Active Quests:")
        if (player.activeQuests.isEmpty()) {
            sb.appendLine("  (none)")
        } else {
            player.activeQuests.forEach { quest ->
                val status = if (quest.isComplete()) " [COMPLETE]" else ""
                sb.appendLine("  - ${quest.title}$status")
            }
        }

        sb.appendLine("\nAvailable Quests:")
        if (worldState.availableQuests.isEmpty()) {
            sb.appendLine("  (none)")
        } else {
            worldState.availableQuests.forEach { quest ->
                sb.appendLine("  - ${quest.title} (${quest.id})")
            }
        }

        return sb.toString().trim()
    }

    private fun handleRest(): String {
        val space = worldState.getCurrentSpace()
        if (space?.isSafeZone != true) {
            return "You can't rest here - it's not safe!"
        }

        val player = worldState.player
        val healAmount = (player.maxHealth * 0.25).toInt().coerceAtLeast(5)
        val newHealth = (player.health + healAmount).coerceAtMost(player.maxHealth)
        val actualHeal = newHealth - player.health

        worldState = worldState.updatePlayer(player.copy(health = newHealth))

        return if (actualHeal > 0) {
            "You rest and recover $actualHeal health. (${newHealth}/${player.maxHealth})"
        } else {
            "You rest but are already at full health."
        }
    }

    private fun handleHelp(): String {
        return """
Available commands:
  Movement: n/s/e/w, look
  Items: take, drop, inventory
  Combat: attack <target>
  Social: talk <npc>
  Skills: search
  Quests: quests
  Other: rest, help, quit
        """.trimIndent()
    }

    private fun handleQuit(): String {
        running = false
        return "Thanks for playing!"
    }
}
