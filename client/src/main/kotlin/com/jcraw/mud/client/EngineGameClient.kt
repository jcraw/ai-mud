package com.jcraw.mud.client

import com.jcraw.mud.core.*
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.perception.IntentRecognizer
import com.jcraw.mud.reasoning.*
import com.jcraw.mud.reasoning.procedural.ProceduralDungeonBuilder
import com.jcraw.mud.reasoning.procedural.DungeonTheme
import com.jcraw.mud.reasoning.procedural.QuestGenerator
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.PersistenceManager
import com.jcraw.sophia.llm.OpenAIClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking

/**
 * Real game client implementation that wraps the actual game engine.
 * Integrates with the existing MudGame logic but exposes it through the GameClient interface.
 */
class EngineGameClient(
    private val apiKey: String? = null,
    private val dungeonTheme: DungeonTheme = DungeonTheme.CRYPT,
    private val roomCount: Int = 10
) : GameClient {

    private val _events = MutableSharedFlow<GameEvent>(replay = 10)
    private var worldState: WorldState
    private var running = true

    // Engine components
    private val llmClient: OpenAIClient?
    private val descriptionGenerator: RoomDescriptionGenerator?
    private val npcInteractionGenerator: NPCInteractionGenerator?
    private val combatNarrator: CombatNarrator?
    private val memoryManager: MemoryManager?
    private val combatResolver: CombatResolver
    private val skillCheckResolver: SkillCheckResolver
    private val persistenceManager: PersistenceManager
    private val intentRecognizer: IntentRecognizer
    private val sceneryGenerator: SceneryDescriptionGenerator
    private val questGenerator: QuestGenerator

    init {
        // Initialize LLM components if API key available
        llmClient = if (!apiKey.isNullOrBlank()) {
            OpenAIClient(apiKey)
        } else {
            null
        }

        memoryManager = llmClient?.let { MemoryManager(it) }
        descriptionGenerator = llmClient?.let { RoomDescriptionGenerator(it, memoryManager!!) }
        npcInteractionGenerator = llmClient?.let { NPCInteractionGenerator(it, memoryManager!!) }
        combatNarrator = llmClient?.let { CombatNarrator(it, memoryManager!!) }

        combatResolver = CombatResolver()
        skillCheckResolver = SkillCheckResolver()
        persistenceManager = PersistenceManager()
        intentRecognizer = IntentRecognizer(llmClient)
        sceneryGenerator = SceneryDescriptionGenerator(llmClient)
        questGenerator = QuestGenerator()

        // Always use Sample Dungeon (same as test bot)
        val baseWorldState = SampleDungeon.createInitialWorldState()

        // Initialize player with default stats
        val playerState = PlayerState(
            id = "player_ui",
            name = "Adventurer",
            currentRoomId = baseWorldState.rooms.values.first().id,
            health = 40,
            maxHealth = 40,
            stats = Stats(
                strength = 10,      // Weak - below average
                dexterity = 8,      // Clumsy
                constitution = 10,  // Average
                intelligence = 9,   // Not bright
                wisdom = 8,         // Inexperienced
                charisma = 9        // Unimpressive
            )
        )

        worldState = baseWorldState.addPlayer(playerState)

        // Generate and add quests
        val initialQuests = questGenerator.generateQuestPool(worldState, dungeonTheme, count = 3)
        initialQuests.forEach { quest ->
            worldState = worldState.addAvailableQuest(quest)
        }

        // Send welcome message
        emitEvent(GameEvent.System("Welcome to the dungeon, ${playerState.name}!"))

        // Describe initial room
        describeCurrentRoom()
    }

    override suspend fun sendInput(text: String) {
        if (!running || text.isBlank()) return

        // Parse intent
        val room = worldState.getCurrentRoom()
        val roomContext = room?.let { "${it.name}: ${it.traits.joinToString(", ")}" }
        val intent = runBlocking {
            intentRecognizer.parseIntent(text, roomContext)
        }

        // Process intent
        processIntent(intent)
    }

    override fun observeEvents(): Flow<GameEvent> = _events.asSharedFlow()

    override fun getCurrentState(): PlayerState? = worldState.player

    override suspend fun close() {
        running = false
        llmClient?.close()
    }

    private fun emitEvent(event: GameEvent) {
        runBlocking {
            _events.emit(event)
        }
    }

    private fun describeCurrentRoom() {
        val room = worldState.getCurrentRoom() ?: return

        // Check if in combat
        if (worldState.player.isInCombat()) {
            val combat = worldState.player.activeCombat!!
            val npc = room.entities.filterIsInstance<Entity.NPC>()
                .find { it.id == combat.combatantNpcId }

            emitEvent(GameEvent.Combat(
                "⚔️ IN COMBAT with ${npc?.name ?: "Unknown Enemy"}\n" +
                "Your Health: ${combat.playerHealth}/${worldState.player.maxHealth}\n" +
                "Enemy Health: ${combat.npcHealth}/${npc?.maxHealth ?: 100}"
            ))
            return
        }

        // Generate room description
        val description = if (descriptionGenerator != null) {
            runBlocking { descriptionGenerator.generateDescription(room) }
        } else {
            room.traits.joinToString(". ") + "."
        }

        val narrativeText = buildString {
            appendLine("\n${room.name}")
            appendLine("-".repeat(room.name.length))
            appendLine(description)

            if (room.exits.isNotEmpty()) {
                // Debug: print exits map
                println("DEBUG: room.exits = ${room.exits}")
                println("DEBUG: room.exits.keys = ${room.exits.keys}")
                appendLine("\nExits: ${room.exits.keys.joinToString(", ") { it.displayName }}")
            }

            if (room.entities.isNotEmpty()) {
                appendLine("\nYou see:")
                room.entities.forEach { entity ->
                    appendLine("  - ${entity.name}")
                }
            }
        }

        emitEvent(GameEvent.Narrative(narrativeText))

        // Update status
        emitEvent(GameEvent.StatusUpdate(
            hp = worldState.player.health,
            maxHp = worldState.player.maxHealth,
            location = room.name
        ))
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
            is Intent.Talk -> handleTalk(intent.target)
            is Intent.Attack -> handleAttack(intent.target)
            is Intent.Equip -> handleEquip(intent.target)
            is Intent.Use -> handleUse(intent.target)
            is Intent.Check -> handleCheck(intent.target)
            is Intent.Persuade -> handlePersuade(intent.target)
            is Intent.Intimidate -> handleIntimidate(intent.target)
            is Intent.Save -> handleSave(intent.saveName)
            is Intent.Load -> handleLoad(intent.saveName)
            is Intent.Quests -> handleQuests()
            is Intent.AcceptQuest -> handleAcceptQuest(intent.questId)
            is Intent.AbandonQuest -> handleAbandonQuest(intent.questId)
            is Intent.ClaimReward -> handleClaimReward(intent.questId)
            is Intent.Help -> handleHelp()
            is Intent.Quit -> handleQuit()
            is Intent.Invalid -> emitEvent(GameEvent.System(intent.message, GameEvent.MessageLevel.WARNING))
        }
    }

    private fun handleMove(direction: Direction) {
        // Check if in combat - must flee first
        if (worldState.player.isInCombat()) {
            emitEvent(GameEvent.Narrative("\nYou attempt to flee from combat..."))

            val result = combatResolver.attemptFlee(worldState)
            emitEvent(GameEvent.Combat(result.narrative))

            if (result.playerFled) {
                // Flee successful - update state and move
                worldState = worldState.updatePlayer(worldState.player.endCombat())

                val newState = worldState.movePlayer(direction)
                if (newState == null) {
                    emitEvent(GameEvent.System("You can't go that way.", GameEvent.MessageLevel.WARNING))
                    return
                }

                worldState = newState
                emitEvent(GameEvent.Narrative("You move ${direction.displayName}."))

                // Update status
                emitEvent(GameEvent.StatusUpdate(
                    hp = worldState.player.health,
                    maxHp = worldState.player.maxHealth
                ))

                describeCurrentRoom()
            } else if (result.playerDied) {
                // Player died trying to flee
                running = false
                emitEvent(GameEvent.StatusUpdate(
                    hp = 0,
                    maxHp = worldState.player.maxHealth
                ))
            } else {
                // Failed to flee - update combat state and stay in place
                if (result.newCombatState != null) {
                    worldState = worldState.updatePlayer(worldState.player.updateCombat(result.newCombatState))
                }

                // Update status
                emitEvent(GameEvent.StatusUpdate(
                    hp = worldState.player.activeCombat?.playerHealth,
                    maxHp = worldState.player.maxHealth
                ))

                describeCurrentRoom()
            }
            return
        }

        // Normal movement (not in combat)
        val newState = worldState.movePlayer(direction)

        if (newState == null) {
            emitEvent(GameEvent.System("You can't go that way.", GameEvent.MessageLevel.WARNING))
            return
        }

        worldState = newState
        emitEvent(GameEvent.Narrative("You move ${direction.displayName}."))
        describeCurrentRoom()
    }

    private fun handleLook(target: String?) {
        if (target == null) {
            describeCurrentRoom()

            // Also list pickupable items on the ground
            val room = worldState.getCurrentRoom() ?: return
            val groundItems = room.entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }
            if (groundItems.isNotEmpty()) {
                val itemsList = buildString {
                    appendLine()
                    appendLine("Items on the ground:")
                    groundItems.forEach { item ->
                        appendLine("  - ${item.name}")
                    }
                }
                emitEvent(GameEvent.Narrative(itemsList))
            } else {
                emitEvent(GameEvent.Narrative("\nYou don't see any items here."))
            }
            return
        }

        val room = worldState.getCurrentRoom() ?: return

        // First check room entities
        val roomEntity = room.entities.find { e ->
            e.name.lowercase().contains(target.lowercase()) ||
            e.id.lowercase().contains(target.lowercase())
        }

        if (roomEntity != null) {
            emitEvent(GameEvent.Narrative(roomEntity.description))
            return
        }

        // Then check inventory (including equipped items)
        val inventoryItem = worldState.player.inventory.find { item ->
            item.name.lowercase().contains(target.lowercase()) ||
            item.id.lowercase().contains(target.lowercase())
        }

        if (inventoryItem != null) {
            emitEvent(GameEvent.Narrative(inventoryItem.description))
            return
        }

        // Check equipped weapon
        val equippedWeapon = worldState.player.equippedWeapon
        if (equippedWeapon != null &&
            (equippedWeapon.name.lowercase().contains(target.lowercase()) ||
             equippedWeapon.id.lowercase().contains(target.lowercase()))) {
            emitEvent(GameEvent.Narrative(equippedWeapon.description + " (equipped)"))
            return
        }

        // Check equipped armor
        val equippedArmor = worldState.player.equippedArmor
        if (equippedArmor != null &&
            (equippedArmor.name.lowercase().contains(target.lowercase()) ||
             equippedArmor.id.lowercase().contains(target.lowercase()))) {
            emitEvent(GameEvent.Narrative(equippedArmor.description + " (equipped)"))
            return
        }

        // Finally try scenery
        val roomDescription = if (descriptionGenerator != null) {
            runBlocking { descriptionGenerator.generateDescription(room) }
        } else {
            room.traits.joinToString(". ") + "."
        }

        val sceneryDescription = runBlocking {
            sceneryGenerator.describeScenery(target, room, roomDescription)
        }

        if (sceneryDescription != null) {
            emitEvent(GameEvent.Narrative(sceneryDescription))
        } else {
            emitEvent(GameEvent.System("You don't see that here.", GameEvent.MessageLevel.INFO))
        }
    }

    private fun handleSearch(target: String?) {
        val room = worldState.getCurrentRoom() ?: return

        val searchMessage = "You search the area carefully${if (target != null) ", focusing on the $target" else ""}..."

        // Perform a Wisdom (Perception) skill check to find hidden items
        val result = skillCheckResolver.checkPlayer(
            worldState.player,
            StatType.WISDOM,
            Difficulty.MEDIUM
        )

        val narrative = buildString {
            appendLine(searchMessage)
            appendLine()
            appendLine("Rolling Perception check...")
            appendLine("d20 roll: ${result.roll} + WIS modifier: ${result.modifier} = ${result.total} vs DC ${result.dc}")
            appendLine()

            if (result.isCriticalSuccess) {
                appendLine("🎲 CRITICAL SUCCESS! (Natural 20)")
            } else if (result.isCriticalFailure) {
                appendLine("💀 CRITICAL FAILURE! (Natural 1)")
            }

            if (result.success) {
                appendLine("✅ Success!")
                appendLine()

                // Find items in the room
                val hiddenItems = room.entities.filterIsInstance<Entity.Item>().filter { !it.isPickupable }
                val pickupableItems = room.entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }

                if (hiddenItems.isNotEmpty() || pickupableItems.isNotEmpty()) {
                    if (pickupableItems.isNotEmpty()) {
                        appendLine("You find the following items:")
                        pickupableItems.forEach { item ->
                            appendLine("  - ${item.name}: ${item.description}")
                        }
                    }
                    if (hiddenItems.isNotEmpty()) {
                        appendLine()
                        appendLine("You also notice some interesting features:")
                        hiddenItems.forEach { item ->
                            appendLine("  - ${item.name}: ${item.description}")
                        }
                    }
                } else {
                    appendLine("You don't find anything hidden here.")
                }
            } else {
                appendLine("❌ Failure!")
                appendLine("You don't find anything of interest.")
            }
        }

        emitEvent(GameEvent.Narrative(narrative))
    }

    private fun handleInteract(target: String) {
        emitEvent(GameEvent.System("Interaction system not yet implemented. (Target: $target)", GameEvent.MessageLevel.INFO))
    }

    private fun handleInventory() {
        val text = buildString {
            appendLine("Inventory:")
            appendLine()

            if (worldState.player.equippedWeapon != null) {
                appendLine("  Equipped Weapon: ${worldState.player.equippedWeapon!!.name} (+${worldState.player.equippedWeapon!!.damageBonus} damage)")
            } else {
                appendLine("  Equipped Weapon: (none)")
            }

            if (worldState.player.equippedArmor != null) {
                appendLine("  Equipped Armor: ${worldState.player.equippedArmor!!.name} (+${worldState.player.equippedArmor!!.defenseBonus} defense)")
            } else {
                appendLine("  Equipped Armor: (none)")
            }

            if (worldState.player.inventory.isEmpty()) {
                appendLine("  Carrying: (nothing)")
            } else {
                appendLine("  Carrying:")
                worldState.player.inventory.forEach { item ->
                    val extra = when (item.itemType) {
                        ItemType.WEAPON -> " [weapon, +${item.damageBonus} damage]"
                        ItemType.ARMOR -> " [armor, +${item.defenseBonus} defense]"
                        ItemType.CONSUMABLE -> " [heals ${item.healAmount} HP]"
                        else -> ""
                    }
                    appendLine("    - ${item.name}$extra")
                }
            }
        }

        emitEvent(GameEvent.Narrative(text))
    }

    private fun handleTake(target: String) {
        val room = worldState.getCurrentRoom() ?: return

        val item = room.entities.filterIsInstance<Entity.Item>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (item == null) {
            // Not an item - check if it's scenery (room trait or entity)
            val isScenery = room.traits.any { it.lowercase().contains(target.lowercase()) } ||
                           room.entities.any { it.name.lowercase().contains(target.lowercase()) }
            if (isScenery) {
                emitEvent(GameEvent.System("That's part of the environment and can't be taken.", GameEvent.MessageLevel.WARNING))
            } else {
                emitEvent(GameEvent.System("You don't see that here.", GameEvent.MessageLevel.WARNING))
            }
            return
        }

        if (!item.isPickupable) {
            emitEvent(GameEvent.System("That's part of the environment and can't be taken.", GameEvent.MessageLevel.WARNING))
            return
        }

        val newState = worldState
            .removeEntityFromRoom(room.id, item.id)
            ?.updatePlayer(worldState.player.addToInventory(item))

        if (newState != null) {
            worldState = newState
            emitEvent(GameEvent.Narrative("You take the ${item.name}."))
        } else {
            emitEvent(GameEvent.System("Something went wrong.", GameEvent.MessageLevel.ERROR))
        }
    }

    private fun handleTakeAll() {
        val room = worldState.getCurrentRoom() ?: return

        // Find all pickupable items in the room
        val items = room.entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }

        if (items.isEmpty()) {
            emitEvent(GameEvent.System("There are no items to take here.", GameEvent.MessageLevel.INFO))
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
                takenCount++
                emitEvent(GameEvent.Narrative("You take the ${item.name}."))
            }
        }

        worldState = currentState

        if (takenCount > 0) {
            emitEvent(GameEvent.Narrative("Picked up $takenCount ${if (takenCount == 1) "item" else "items"}."))
        }
    }

    private fun handleDrop(target: String) {
        val room = worldState.getCurrentRoom() ?: return

        val item = worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(target.lowercase()) ||
            invItem.id.lowercase().contains(target.lowercase())
        }

        if (item == null) {
            emitEvent(GameEvent.System("You don't have that.", GameEvent.MessageLevel.WARNING))
            return
        }

        val newState = worldState
            .updatePlayer(worldState.player.removeFromInventory(item.id))
            .addEntityToRoom(room.id, item)

        if (newState != null) {
            worldState = newState
            emitEvent(GameEvent.Narrative("You drop the ${item.name}."))
        } else {
            emitEvent(GameEvent.System("Something went wrong.", GameEvent.MessageLevel.ERROR))
        }
    }

    private fun handleTalk(target: String) {
        val room = worldState.getCurrentRoom() ?: return

        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (npc == null) {
            emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }

        if (npcInteractionGenerator != null) {
            emitEvent(GameEvent.Narrative("\nYou speak to ${npc.name}..."))
            val dialogue = runBlocking {
                npcInteractionGenerator.generateDialogue(npc, worldState.player)
            }
            emitEvent(GameEvent.Narrative("\n${npc.name} says: \"$dialogue\""))
        } else {
            if (npc.isHostile) {
                emitEvent(GameEvent.Narrative("\n${npc.name} glares at you menacingly and says nothing."))
            } else {
                emitEvent(GameEvent.Narrative("\n${npc.name} nods at you in acknowledgment."))
            }
        }
    }

    // Combat, equipment, skills, quests - implement similarly to MudGame
    // (Continuing in next part due to length...)

    private fun handleAttack(target: String?) {
        val room = worldState.getCurrentRoom() ?: return

        // If already in combat
        if (worldState.player.isInCombat()) {
            val result = combatResolver.executePlayerAttack(worldState)

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

            emitEvent(GameEvent.Combat(narrative, result.npcDamage))

            if (result.newCombatState != null) {
                worldState = worldState.updatePlayer(worldState.player.updateCombat(result.newCombatState))

                // Update status
                emitEvent(GameEvent.StatusUpdate(
                    hp = worldState.player.activeCombat?.playerHealth,
                    maxHp = worldState.player.maxHealth
                ))

                describeCurrentRoom()
            } else {
                val endedCombat = worldState.player.activeCombat
                worldState = worldState.updatePlayer(worldState.player.endCombat())

                when {
                    result.npcDied -> {
                        emitEvent(GameEvent.Combat("\nVictory! The enemy has been defeated!"))
                        if (endedCombat != null) {
                            worldState = worldState.removeEntityFromRoom(room.id, endedCombat.combatantNpcId) ?: worldState
                        }
                    }
                    result.playerDied -> {
                        emitEvent(GameEvent.Combat("\nYou have been defeated! Game over."))
                        running = false
                    }
                    result.playerFled -> {
                        emitEvent(GameEvent.Narrative("\nYou have fled from combat."))
                    }
                }

                // Update status
                emitEvent(GameEvent.StatusUpdate(
                    hp = worldState.player.health,
                    maxHp = worldState.player.maxHealth
                ))
            }
            return
        }

        // Initiate combat
        if (target.isNullOrBlank()) {
            emitEvent(GameEvent.System("Attack whom?", GameEvent.MessageLevel.WARNING))
            return
        }

        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (npc == null) {
            emitEvent(GameEvent.System("You don't see anyone by that name to attack.", GameEvent.MessageLevel.WARNING))
            return
        }

        val result = combatResolver.initiateCombat(worldState, npc.id)
        if (result == null) {
            emitEvent(GameEvent.System("You cannot initiate combat with that target.", GameEvent.MessageLevel.ERROR))
            return
        }

        val narrative = if (combatNarrator != null) {
            runBlocking {
                combatNarrator.narrateCombatStart(worldState, npc)
            }
        } else {
            result.narrative
        }

        emitEvent(GameEvent.Combat(narrative))

        if (result.newCombatState != null) {
            worldState = worldState.updatePlayer(worldState.player.updateCombat(result.newCombatState))

            // Update status
            emitEvent(GameEvent.StatusUpdate(
                hp = worldState.player.activeCombat?.playerHealth,
                maxHp = worldState.player.maxHealth
            ))

            describeCurrentRoom()
        }
    }

    private fun handleEquip(target: String) {
        val item = worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(target.lowercase()) ||
            invItem.id.lowercase().contains(target.lowercase())
        }

        if (item == null) {
            emitEvent(GameEvent.System("You don't have that in your inventory.", GameEvent.MessageLevel.WARNING))
            return
        }

        when (item.itemType) {
            ItemType.WEAPON -> {
                val oldWeapon = worldState.player.equippedWeapon
                worldState = worldState.updatePlayer(worldState.player.equipWeapon(item))

                if (oldWeapon != null) {
                    emitEvent(GameEvent.Narrative("You unequip the ${oldWeapon.name} and equip the ${item.name} (+${item.damageBonus} damage)."))
                } else {
                    emitEvent(GameEvent.Narrative("You equip the ${item.name} (+${item.damageBonus} damage)."))
                }
            }
            ItemType.ARMOR -> {
                val oldArmor = worldState.player.equippedArmor
                worldState = worldState.updatePlayer(worldState.player.equipArmor(item))

                if (oldArmor != null) {
                    emitEvent(GameEvent.Narrative("You unequip the ${oldArmor.name} and equip the ${item.name} (+${item.defenseBonus} defense)."))
                } else {
                    emitEvent(GameEvent.Narrative("You equip the ${item.name} (+${item.defenseBonus} defense)."))
                }
            }
            else -> {
                emitEvent(GameEvent.System("You can't equip that.", GameEvent.MessageLevel.WARNING))
            }
        }
    }

    private fun handleUse(target: String) {
        val item = worldState.player.inventory.find { invItem ->
            invItem.name.lowercase().contains(target.lowercase()) ||
            invItem.id.lowercase().contains(target.lowercase())
        }

        if (item == null) {
            emitEvent(GameEvent.System("You don't have that in your inventory.", GameEvent.MessageLevel.WARNING))
            return
        }

        if (!item.isUsable) {
            emitEvent(GameEvent.System("You can't use that.", GameEvent.MessageLevel.WARNING))
            return
        }

        when (item.itemType) {
            ItemType.CONSUMABLE -> {
                val oldHealth = worldState.player.health
                worldState = worldState.updatePlayer(worldState.player.useConsumable(item))
                val healedAmount = worldState.player.health - oldHealth

                if (healedAmount > 0) {
                    emitEvent(GameEvent.Narrative("You consume the ${item.name} and restore $healedAmount HP.\nCurrent health: ${worldState.player.health}/${worldState.player.maxHealth}"))

                    // Update status
                    emitEvent(GameEvent.StatusUpdate(
                        hp = worldState.player.health,
                        maxHp = worldState.player.maxHealth
                    ))
                } else {
                    emitEvent(GameEvent.Narrative("You consume the ${item.name}, but you're already at full health."))
                }
            }
            ItemType.WEAPON -> {
                emitEvent(GameEvent.System("Try 'equip ${item.name}' to equip this weapon.", GameEvent.MessageLevel.INFO))
            }
            else -> {
                emitEvent(GameEvent.System("You're not sure how to use that.", GameEvent.MessageLevel.INFO))
            }
        }
    }

    // Skill checks, persuade, intimidate - simplified for brevity
    private fun handleCheck(target: String) {
        emitEvent(GameEvent.System("Skill check system integrated - implement if needed", GameEvent.MessageLevel.INFO))
    }

    private fun handlePersuade(target: String) {
        emitEvent(GameEvent.System("Persuasion system integrated - implement if needed", GameEvent.MessageLevel.INFO))
    }

    private fun handleIntimidate(target: String) {
        emitEvent(GameEvent.System("Intimidation system integrated - implement if needed", GameEvent.MessageLevel.INFO))
    }

    private fun handleSave(saveName: String) {
        val result = persistenceManager.saveGame(worldState, saveName)

        result.onSuccess {
            emitEvent(GameEvent.System("💾 Game saved as '$saveName'", GameEvent.MessageLevel.INFO))
        }.onFailure { error ->
            emitEvent(GameEvent.System("❌ Failed to save game: ${error.message}", GameEvent.MessageLevel.ERROR))
        }
    }

    private fun handleLoad(saveName: String) {
        val result = persistenceManager.loadGame(saveName)

        result.onSuccess { loadedState ->
            worldState = loadedState
            emitEvent(GameEvent.System("📂 Game loaded from '$saveName'", GameEvent.MessageLevel.INFO))
            describeCurrentRoom()
        }.onFailure { error ->
            emitEvent(GameEvent.System("❌ Failed to load game: ${error.message}", GameEvent.MessageLevel.ERROR))

            val saves = persistenceManager.listSaves()
            if (saves.isNotEmpty()) {
                emitEvent(GameEvent.System("Available saves: ${saves.joinToString(", ")}", GameEvent.MessageLevel.INFO))
            } else {
                emitEvent(GameEvent.System("No saved games found.", GameEvent.MessageLevel.INFO))
            }
        }
    }

    private fun handleHelp() {
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

        emitEvent(GameEvent.System(helpText, GameEvent.MessageLevel.INFO))
    }

    private fun handleQuests() {
        val player = worldState.player

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
            if (worldState.availableQuests.isNotEmpty()) {
                appendLine("Available Quests (use 'accept <id>' to accept):")
                worldState.availableQuests.forEach { quest ->
                    appendLine("  - ${quest.id}: ${quest.title}")
                }
            }
            appendLine("═".repeat(26))
        }

        emitEvent(GameEvent.Quest(text))
    }

    private fun handleAcceptQuest(questId: String?) {
        if (questId == null) {
            if (worldState.availableQuests.isEmpty()) {
                emitEvent(GameEvent.System("No quests available to accept.", GameEvent.MessageLevel.INFO))
            } else {
                val text = buildString {
                    appendLine("\nAvailable Quests:")
                    worldState.availableQuests.forEach { quest ->
                        appendLine("  ${quest.id}: ${quest.title}")
                        appendLine("    ${quest.description}")
                    }
                    appendLine("\nUse 'accept <quest_id>' to accept a quest.")
                }
                emitEvent(GameEvent.Quest(text))
            }
            return
        }

        val quest = worldState.getAvailableQuest(questId)
        if (quest == null) {
            emitEvent(GameEvent.System("No quest available with ID '$questId'.", GameEvent.MessageLevel.WARNING))
            return
        }

        if (worldState.player.hasQuest(questId)) {
            emitEvent(GameEvent.System("You already have this quest!", GameEvent.MessageLevel.WARNING))
            return
        }

        worldState = worldState
            .updatePlayer(worldState.player.addQuest(quest))
            .removeAvailableQuest(questId)

        val text = buildString {
            appendLine("\n📜 Quest Accepted: ${quest.title}")
            appendLine(quest.description)
            appendLine("\nObjectives:")
            quest.objectives.forEach { appendLine("  ○ ${it.description}") }
        }

        emitEvent(GameEvent.Quest(text, questId))
    }

    private fun handleAbandonQuest(questId: String) {
        val quest = worldState.player.getQuest(questId)
        if (quest == null) {
            emitEvent(GameEvent.System("You don't have a quest with ID '$questId'.", GameEvent.MessageLevel.WARNING))
            return
        }

        worldState = worldState
            .updatePlayer(worldState.player.removeQuest(questId))
            .addAvailableQuest(quest)

        emitEvent(GameEvent.Quest("Quest '${quest.title}' abandoned.", questId))
    }

    private fun handleClaimReward(questId: String) {
        val quest = worldState.player.getQuest(questId)
        if (quest == null) {
            emitEvent(GameEvent.System("You don't have a quest with ID '$questId'.", GameEvent.MessageLevel.WARNING))
            return
        }

        if (!quest.isComplete()) {
            emitEvent(GameEvent.System("Quest '${quest.title}' is not complete yet!\nProgress: ${quest.getProgressSummary()}", GameEvent.MessageLevel.WARNING))
            return
        }

        if (quest.status == QuestStatus.CLAIMED) {
            emitEvent(GameEvent.System("You've already claimed the reward for this quest!", GameEvent.MessageLevel.WARNING))
            return
        }

        worldState = worldState.updatePlayer(worldState.player.claimQuestReward(questId))

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
            appendLine("\nTotal Experience: ${worldState.player.experiencePoints}")
            appendLine("Total Gold: ${worldState.player.gold}")
        }

        emitEvent(GameEvent.Quest(text, questId))

        // Update status
        emitEvent(GameEvent.StatusUpdate(
            hp = worldState.player.health,
            maxHp = worldState.player.maxHealth
        ))
    }

    private fun handleQuit() {
        emitEvent(GameEvent.System("Goodbye!", GameEvent.MessageLevel.INFO))
        running = false
    }
}
