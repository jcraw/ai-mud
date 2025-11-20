package com.jcraw.mud.testbot

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.SpaceId
import com.jcraw.mud.core.SpacePropertiesComponent
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
 *
 * **SkillManager Requirement**: For SkillProgression test scenarios,
 * skillManager must be provided to enable skill leveling mechanics (Dodge 0→10).
 * Without skillManager, skill-related commands will not function.
 */
class InMemoryGameEngine(
    private val initialWorldState: WorldState,
    private val descriptionGenerator: RoomDescriptionGenerator? = null,
    private val npcInteractionGenerator: NPCInteractionGenerator? = null,
    private val combatNarrator: CombatNarrator? = null,
    private val memoryManager: MemoryManager? = null,
    private val llmClient: LLMClient? = null,
    private val emoteHandler: EmoteHandler? = null,
    private val knowledgeManager: NPCKnowledgeManager? = null,
    private val dispositionManager: DispositionManager? = null,
    private val skillManager: com.jcraw.mud.reasoning.skill.SkillManager? = null
) : GameEngineInterface {

    private var worldState: WorldState = initialWorldState
    private var running = true
    private val combatResolver = CombatResolver()
    private val skillCheckResolver = SkillCheckResolver()
    private val intentRecognizer = IntentRecognizer(llmClient)
    private val sceneryGenerator = SceneryDescriptionGenerator(llmClient)
    private val questTracker = QuestTracker(dispositionManager)
    private var lastConversationNpcId: String? = null

    override suspend fun processInput(input: String): String {
        if (!running) return "Game is not running."

        val space = worldState.getCurrentSpace()
        val roomContext = space?.let { "${it.name}: ${it.description}" }
        val exitsWithNames = space?.let { buildExitsWithNames(it) }
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
            is Intent.Say -> handleSay(intent.message, intent.npcTarget)
            is Intent.Attack -> handleAttack(intent.target)
            is Intent.Equip -> handleEquip(intent.target)
            is Intent.Use -> handleUse(intent.target)
            is Intent.Check -> handleCheck(intent.target)
            is Intent.Persuade -> handlePersuade(intent.target)
            is Intent.Intimidate -> handleIntimidate(intent.target)
            is Intent.Emote -> handleEmote(intent.emoteType, intent.target)
            is Intent.AskQuestion -> handleAskQuestion(intent.npcTarget, intent.topic)
            is Intent.UseSkill -> handleUseSkill(intent.skill, intent.action)
            is Intent.TrainSkill -> handleTrainSkill(intent.skill, intent.method)
            is Intent.ChoosePerk -> handleChoosePerk(intent.skillName, intent.choice)
            is Intent.ViewSkills -> handleViewSkills()
            is Intent.Help -> handleHelp()
            is Intent.Quit -> handleQuit()
            is Intent.Invalid -> intent.message
            else -> "Command not supported in test mode: $intent"
        }
    }

    private suspend fun handleMove(direction: Direction): String {
        // Movement is always allowed in Combat System V2
        val playerSkills = skillManager?.getSkillComponent(worldState.player.id) ?: com.jcraw.mud.core.SkillComponent()
        val newState = worldState.movePlayerV3(direction, playerSkills)
        return if (newState != null) {
            worldState = newState
            val space = worldState.getCurrentSpace()!!
            val spaceId = worldState.player.currentRoomId
            val questNotifications = trackQuests(QuestAction.VisitedRoom(spaceId))
            buildRoomDescription(space, spaceId) + questNotifications
        } else {
            "You can't go that way."
        }
    }

    private suspend fun handleLook(target: String?): String {
        val space = worldState.getCurrentSpace() ?: return "You are nowhere."
        val spaceId = worldState.player.currentRoomId

        if (target == null) {
            return buildRoomDescription(space, spaceId)
        }

        // First check space entities
        val roomEntity = worldState.getEntitiesInSpace(spaceId).find { e ->
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

        // Finally try scenery (note: SceneryDescriptionGenerator needs V3 update, using description for now)
        val roomDescription = buildRoomDescription(space, spaceId)
        val sceneryDescription = space.description.takeIf { it.contains(target, ignoreCase = true) }
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
        val space = worldState.getCurrentSpace() ?: return "You are nowhere."
        val spaceId = worldState.player.currentRoomId
        val item = worldState.getEntitiesInSpace(spaceId).filterIsInstance<Entity.Item>()
            .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

        return if (item != null && item.isPickupable) {
            val newState = worldState.removeEntityFromSpace(spaceId, item.id)
                ?.updatePlayer(worldState.player.addToInventory(item))
            if (newState != null) {
                worldState = newState
                val questNotifications = trackQuests(QuestAction.CollectedItem(item.id))
                "You take the ${item.name}." + questNotifications
            } else {
                "Failed to take item."
            }
        } else {
            "You can't take that."
        }
    }

    private fun handleTakeAll(): String {
        val space = worldState.getCurrentSpace() ?: return "You are nowhere."
        val spaceId = worldState.player.currentRoomId
        val items = worldState.getEntitiesInSpace(spaceId).filterIsInstance<Entity.Item>().filter { it.isPickupable }

        return if (items.isEmpty()) {
            "There are no items to take here."
        } else {
            var currentState = worldState
            val takenItems = mutableListOf<String>()

            items.forEach { item ->
                val newState = currentState.removeEntityFromSpace(spaceId, item.id)
                    ?.updatePlayer(currentState.player.addToInventory(item))
                if (newState != null) {
                    currentState = newState
                    takenItems.add(item.name)
                }
            }

            worldState = currentState

            // Track quest progress for all taken items
            val questNotifications = items.map { item -> trackQuests(QuestAction.CollectedItem(item.id)) }.joinToString("")

            buildString {
                takenItems.forEach { append("You take the $it.\n") }
                append("You took ${takenItems.size} item${if (takenItems.size > 1) "s" else ""}.")
                append(questNotifications)
            }
        }
    }

    private fun handleDrop(target: String): String {
        val space = worldState.getCurrentSpace() ?: return "You are nowhere."
        val spaceId = worldState.player.currentRoomId

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

            val newState = worldState.updatePlayer(updatedPlayer).addEntityToSpace(spaceId, item)
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
        val space = worldState.getCurrentSpace() ?: return "You are nowhere."
        val spaceId = worldState.player.currentRoomId
        val npc = worldState.getEntitiesInSpace(spaceId).filterIsInstance<Entity.NPC>()
            .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

        return if (npc != null) {
            lastConversationNpcId = npc.id
            val questNotifications = trackQuests(QuestAction.TalkedToNPC(npc.id))
            if (npcInteractionGenerator != null) {
                val dialogue = npcInteractionGenerator.generateDialogue(npc, worldState.player)
                "${npc.name} says: \"$dialogue\"" + questNotifications
            } else {
                "${npc.name} acknowledges you." + questNotifications
            }
        } else {
            "No one by that name here."
        }
    }

    private suspend fun handleSay(message: String, npcTarget: String?): String {
        val utterance = message.trim()
        if (utterance.isEmpty()) {
            return "Say what?"
        }

        val space = worldState.getCurrentSpace() ?: return "You are nowhere."
        val spaceId = worldState.player.currentRoomId
        val npc = resolveNpcTarget(spaceId, npcTarget)

        if (npcTarget != null && npc == null) {
            return "No one by that name here."
        }

        if (npc == null) {
            lastConversationNpcId = null
            return "You say: \"$utterance\""
        }

        lastConversationNpcId = npc.id
        val introduction = "You say to ${npc.name}: \"$utterance\""

        if (isQuestion(utterance)) {
            val topic = utterance.trimEnd('?', ' ').ifBlank { utterance }
            val reply = handleAskQuestion(npc.name, topic)
            val questNotifications = trackQuests(QuestAction.TalkedToNPC(npc.id))
            return listOf(introduction, reply + questNotifications).joinToString("\n")
        }

        val response = if (npcInteractionGenerator != null) {
            val dialogue = npcInteractionGenerator.generateDialogue(npc, worldState.player)
            "${npc.name} says: \"$dialogue\""
        } else {
            if (npc.isHostile) {
                "${npc.name} scowls and refuses to answer."
            } else {
                "${npc.name} listens quietly."
            }
        }

        val questNotifications = trackQuests(QuestAction.TalkedToNPC(npc.id))
        return listOf(introduction, response + questNotifications).joinToString("\n")
    }

    private fun resolveNpcTarget(spaceId: SpaceId, npcTarget: String?): Entity.NPC? {
        val npcs = worldState.getEntitiesInSpace(spaceId).filterIsInstance<Entity.NPC>()
        if (npcTarget != null) {
            val lower = npcTarget.lowercase()
            val explicit = npcs.find {
                it.name.lowercase().contains(lower) || it.id.lowercase().contains(lower)
            }
            if (explicit != null) {
                return explicit
            }
        }

        val recent = lastConversationNpcId
        if (recent != null) {
            return npcs.find { it.id == recent }
        }

        return null
    }

    private fun isQuestion(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.endsWith("?")) return true

        val lower = trimmed.lowercase()
        val prefixes = listOf(
            "who", "what", "where", "when", "why", "how",
            "can", "will", "is", "are", "am",
            "do", "does", "did",
            "should", "could", "would",
            "have", "has", "had",
            "tell me", "explain", "describe"
        )

        return prefixes.any { lower.startsWith(it) }
    }

    private suspend fun handleAttack(target: String?): String {
        val space = worldState.getCurrentSpace() ?: return "You are nowhere."
        val spaceId = worldState.player.currentRoomId

        // Validate target
        if (target.isNullOrBlank()) return "Attack whom?"

        // Find the target NPC
        val npc = worldState.getEntitiesInSpace(spaceId).filterIsInstance<Entity.NPC>()
            .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

        if (npc == null) {
            return "No one by that name here."
        }

        // Calculate player damage: base + weapon + STR modifier
        val playerBaseDamage = kotlin.random.Random.nextInt(5, 16)
        val weaponBonus = worldState.player.getWeaponDamageBonus()
        val strModifier = worldState.player.stats.strModifier()
        val playerDamage = (playerBaseDamage + weaponBonus + strModifier).coerceAtLeast(1)

        // Calculate NPC damage: base + STR modifier - armor defense
        val npcBaseDamage = kotlin.random.Random.nextInt(3, 13)
        val npcStrModifier = npc.stats.strModifier()
        val armorDefense = worldState.player.getArmorDefenseBonus()
        val npcDamage = (npcBaseDamage + npcStrModifier - armorDefense).coerceAtLeast(1)

        // Apply damage to NPC
        val npcHealth = npc.health - playerDamage
        val npcDied = npcHealth <= 0

        // Generate narrative
        val weapon = worldState.player.equippedWeapon?.name ?: "bare fists"
        val attackNarrative = "You attack ${npc.name} with $weapon for $playerDamage damage!"

        // Check if NPC died
        if (npcDied) {
            worldState = worldState.removeEntityFromSpace(spaceId, npc.id)
            val questNotifications = trackQuests(QuestAction.KilledNPC(npc.id))
            return "$attackNarrative\nVictory! ${npc.name} has been defeated!" + questNotifications
        }

        // NPC counter-attacks
        val counterNarrative = "\n${npc.name} strikes back for $npcDamage damage!"

        // Apply damage to player
        val updatedPlayer = worldState.player.takeDamage(npcDamage)
        worldState = worldState.updatePlayer(updatedPlayer)

        // Check if player died
        if (updatedPlayer.isDead()) {
            running = false
            return "$attackNarrative$counterNarrative\nYou have been defeated! Game over."
        }

        return "$attackNarrative$counterNarrative"
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
        val space = worldState.getCurrentSpace() ?: return "You are nowhere."
        val spaceId = worldState.player.currentRoomId
        val feature = worldState.getEntitiesInSpace(spaceId).filterIsInstance<Entity.Feature>()
            .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

        if (feature == null || !feature.isInteractable || feature.skillChallenge == null) {
            return "Nothing to check here."
        }

        if (feature.isCompleted) {
            return "You've already completed that."
        }

        val result = skillCheckResolver.checkPlayer(worldState.player, feature.skillChallenge!!.statType, feature.skillChallenge!!.difficulty)

        return if (result.success) {
            worldState = worldState.replaceEntityInSpace(spaceId, feature.id, feature.copy(isCompleted = true))
            val questNotifications = trackQuests(QuestAction.UsedSkill(feature.id))
            "Success! ${feature.skillChallenge!!.successDescription}" + questNotifications
        } else {
            "Failed! ${feature.skillChallenge!!.failureDescription}"
        }
    }

    private fun handlePersuade(target: String): String {
        val space = worldState.getCurrentSpace() ?: return "You are nowhere."
        val spaceId = worldState.player.currentRoomId
        val npc = worldState.getEntitiesInSpace(spaceId).filterIsInstance<Entity.NPC>()
            .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

        if (npc == null || npc.persuasionChallenge == null) {
            return "Cannot persuade that."
        }

        if (npc.hasBeenPersuaded) {
            return "Already persuaded."
        }

        val result = skillCheckResolver.checkPlayer(worldState.player, npc.persuasionChallenge!!.statType, npc.persuasionChallenge!!.difficulty)

        return if (result.success) {
            worldState = worldState.replaceEntityInSpace(spaceId, npc.id, npc.copy(hasBeenPersuaded = true))
            "Success! ${npc.persuasionChallenge!!.successDescription}"
        } else {
            "Failed! ${npc.persuasionChallenge!!.failureDescription}"
        }
    }

    private fun handleIntimidate(target: String): String {
        val space = worldState.getCurrentSpace() ?: return "You are nowhere."
        val spaceId = worldState.player.currentRoomId
        val npc = worldState.getEntitiesInSpace(spaceId).filterIsInstance<Entity.NPC>()
            .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

        if (npc == null || npc.intimidationChallenge == null) {
            return "Cannot intimidate that."
        }

        if (npc.hasBeenIntimidated) {
            return "Already intimidated."
        }

        val result = skillCheckResolver.checkPlayer(worldState.player, npc.intimidationChallenge!!.statType, npc.intimidationChallenge!!.difficulty)

        return if (result.success) {
            worldState = worldState.replaceEntityInSpace(spaceId, npc.id, npc.copy(hasBeenIntimidated = true))
            "Success! ${npc.intimidationChallenge!!.successDescription}"
        } else {
            "Failed! ${npc.intimidationChallenge!!.failureDescription}"
        }
    }

    private fun handleEmote(emoteType: String, target: String?): String {
        // Emotes require emoteHandler
        if (emoteHandler == null) {
            return "Emotes are not available in this mode."
        }

        val space = worldState.getCurrentSpace() ?: return "You are nowhere."
        val spaceId = worldState.player.currentRoomId

        // If no target specified, perform general emote
        if (target.isNullOrBlank()) {
            return "You ${emoteType.lowercase()}."
        }

        // Find target NPC
        val npc = worldState.getEntitiesInSpace(spaceId).filterIsInstance<Entity.NPC>()
            .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

        if (npc == null) {
            return "No one by that name here."
        }

        // Parse emote keyword
        val emoteTypeEnum = emoteHandler.parseEmoteKeyword(emoteType)
        if (emoteTypeEnum == null) {
            return "Unknown emote: $emoteType"
        }

        // Process emote
        val (narrative, updatedNpc) = emoteHandler.processEmote(npc, emoteTypeEnum, "You")

        // Update world state with updated NPC
        worldState = worldState.replaceEntityInSpace(spaceId, npc.id, updatedNpc)

        return narrative
    }

    private suspend fun handleAskQuestion(npcTarget: String, topic: String): String {
        // Questions require knowledgeManager
        if (knowledgeManager == null) {
            return "Knowledge queries are not available in this mode."
        }

        val space = worldState.getCurrentSpace() ?: return "You are nowhere."
        val spaceId = worldState.player.currentRoomId

        // Find target NPC
        val npc = worldState.getEntitiesInSpace(spaceId).filterIsInstance<Entity.NPC>()
            .find { it.name.lowercase().contains(npcTarget.lowercase()) || it.id.lowercase().contains(npcTarget.lowercase()) }

        if (npc == null) {
            return "No one by that name here."
        }

        lastConversationNpcId = npc.id

        val worldContext = buildQuestionContext(space, npc, topic)
        val knowledgeResult = knowledgeManager.queryKnowledge(npc, topic, worldContext)
        var updatedNpc = knowledgeResult.npc

        val questionEvent = com.jcraw.mud.core.SocialEvent.QuestionAsked(
            topic = knowledgeResult.normalizedTopic,
            questionText = knowledgeResult.question,
            answerText = knowledgeResult.answer,
            description = "${worldState.player.name} asked ${npc.name} about \"${knowledgeResult.question}\""
        )
        updatedNpc = dispositionManager?.applyEvent(updatedNpc, questionEvent) ?: updatedNpc

        // Update world state with updated NPC (may have new knowledge)
        worldState = worldState.replaceEntityInSpace(spaceId, npc.id, updatedNpc)

        val questNotifications = trackQuests(QuestAction.TalkedToNPC(npc.id))
        return "${npc.name} says: \"${knowledgeResult.answer}\"" + questNotifications
    }

    private fun buildQuestionContext(space: SpacePropertiesComponent, npc: Entity.NPC, topic: String): String {
        val social = npc.getComponent<com.jcraw.mud.core.SocialComponent>(com.jcraw.mud.core.ComponentType.SOCIAL)
        return buildString {
            appendLine("Location: ${space.name}")
            if (space.description.isNotEmpty()) {
                appendLine("Location description: ${space.description}")
            }
            appendLine("NPC name: ${npc.name}")
            appendLine("NPC description: ${npc.description}")
            if (social != null) {
                appendLine("NPC personality: ${social.personality}")
                if (social.traits.isNotEmpty()) {
                    appendLine("NPC traits: ${social.traits.joinToString()}")
                }
                appendLine("NPC disposition score: ${social.disposition}")
            }
            appendLine("Player name: ${worldState.player.name}")
            appendLine("Topic requested: $topic")
        }
    }

    private fun handleUseSkill(skill: String?, action: String): String {
        if (skillManager == null) {
            return "Skills are not available in this mode."
        }

        // Determine skill name from explicit parameter or action
        val skillName = skill ?: inferSkillFromAction(action)
        if (skillName == null) {
            return "Could not determine which skill to use for: $action"
        }

        // Check if skill exists
        val skillDef = com.jcraw.mud.reasoning.skill.SkillDefinitions.getSkill(skillName)
        if (skillDef == null) {
            return "Unknown skill: $skillName"
        }

        // Perform skill check (default difficulty: Medium = 15)
        val difficulty = 15
        val checkResult = skillManager.checkSkill(
            entityId = worldState.player.id,
            skillName = skillName,
            difficulty = difficulty
        ).getOrElse { error ->
            return "Skill check failed: ${error.message}"
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
            return "You attempt to $action with $skillName, but the skill is not unlocked.\nTry 'train $skillName with <npc>' or use it repeatedly to unlock it."
        }

        // Format output with roll details and XP
        val output = buildString {
            appendLine("You attempt to $action using $skillName:")
            appendLine()
            val total = checkResult.roll + checkResult.skillLevel
            appendLine("Roll: d20(${checkResult.roll}) + Level(${checkResult.skillLevel}) = $total vs DC $difficulty")
            appendLine(checkResult.narrative)
            appendLine()

            // XP and level-up messages
            xpEvents.forEach { event ->
                when (event) {
                    is com.jcraw.mud.core.SkillEvent.XpGained -> {
                        appendLine("+${event.xpAmount} XP to $skillName (${event.currentXp} total, level ${event.currentLevel})")
                    }
                    is com.jcraw.mud.core.SkillEvent.LevelUp -> {
                        appendLine()
                        appendLine("🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel}")
                        if (event.isAtPerkMilestone) {
                            appendLine("⚡ Milestone reached! Use 'choose perk for $skillName' to select a perk.")
                        }
                    }
                    else -> {}
                }
            }
        }

        return output
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

    private fun handleTrainSkill(skill: String, method: String): String {
        if (skillManager == null || dispositionManager == null) {
            return "Skills are not available in this mode."
        }

        val space = worldState.getCurrentSpace() ?: return "You are nowhere."
        val spaceId = worldState.player.currentRoomId

        // Parse NPC name from method string (e.g., "with the knight" → "knight")
        val npcName = method.lowercase()
            .removePrefix("with ")
            .removePrefix("the ")
            .removePrefix("at ")
            .removePrefix("from ")
            .trim()

        if (npcName.isBlank()) {
            return "Train with whom? Use 'train <skill> with <npc>'."
        }

        // Find NPC in space
        val npc = worldState.getEntitiesInSpace(spaceId).filterIsInstance<Entity.NPC>()
            .find {
                it.name.lowercase().contains(npcName) ||
                it.id.lowercase().contains(npcName)
            }

        if (npc == null) {
            return "There's no one here by that name to train with."
        }

        // Attempt training via DispositionManager
        val trainingResult = dispositionManager.trainSkillWithNPC(
            worldState.player.id,
            npc,
            skill
        )

        return trainingResult.fold(
            onSuccess = { message ->
                // Update world state with any NPC changes (disposition)
                worldState = worldState.replaceEntityInSpace(spaceId, npc.id, npc)
                message
            },
            onFailure = { error ->
                error.message ?: "Training failed"
            }
        )
    }

    private fun handleChoosePerk(skillName: String, choice: Int): String {
        if (skillManager == null) {
            return "Skills are not available in this mode."
        }

        // Get skill component to check skill state
        val component = skillManager.getSkillComponent(worldState.player.id)
        val skillState = component.getSkill(skillName)

        if (skillState == null) {
            return "You don't have the skill '$skillName'. Train it first!"
        }

        // Get available perk choices at current level
        val perkSelector = com.jcraw.mud.reasoning.skill.PerkSelector(
            skillManager.getSkillComponentRepository(),
            memoryManager
        )
        val availablePerks = perkSelector.getPerkChoices(skillName, skillState.level)

        if (availablePerks.isEmpty()) {
            return "No perk choices available for $skillName at level ${skillState.level}."
        }

        // Validate choice (1-based index)
        if (choice < 1 || choice > availablePerks.size) {
            return "Invalid choice. Please choose a number between 1 and ${availablePerks.size}."
        }

        // Convert to 0-based index and get chosen perk
        val chosenPerk = availablePerks[choice - 1]

        // Attempt to select the perk
        val event = perkSelector.selectPerk(worldState.player.id, skillName, chosenPerk)

        return if (event != null) {
            com.jcraw.mud.action.SkillFormatter.formatPerkUnlocked(chosenPerk.name, skillName)
        } else {
            "Failed to unlock perk. You may not have a pending perk choice for this skill."
        }
    }

    private fun handleViewSkills(): String {
        if (skillManager == null) {
            return "Skills are not available in this mode."
        }

        val component = skillManager.getSkillComponent(worldState.player.id)
        return com.jcraw.mud.action.SkillFormatter.formatSkillSheet(component)
    }

    private fun handleHelp(): String = "Available commands: move, look, inventory, take, drop, talk, attack, equip, use, check, persuade, intimidate, emote, ask, skills, quit"

    private fun handleQuit(): String {
        running = false
        return "Goodbye!"
    }

    private suspend fun buildRoomDescription(space: SpacePropertiesComponent, spaceId: SpaceId): String {
        val npcs = worldState.getEntitiesInSpace(spaceId).filterIsInstance<Entity.NPC>()
        if (lastConversationNpcId != null && npcs.none { it.id == lastConversationNpcId }) {
            lastConversationNpcId = null
        }

        val description = if (descriptionGenerator != null && space.description.isEmpty()) {
            try {
                // Note: RoomDescriptionGenerator needs V3 update, using space description for now
                space.description.ifEmpty { "An unremarkable location." }
            } catch (e: Exception) {
                space.description.ifEmpty { "An unremarkable location." }
            }
        } else {
            space.description.ifEmpty { "An unremarkable location." }
        }

        val exits = if (space.exits.isNotEmpty()) {
            "\nExits: ${space.exits.joinToString { it.direction }}"
        } else ""

        val entities = worldState.getEntitiesInSpace(spaceId)
        val entitiesStr = if (entities.isNotEmpty()) {
            "\nYou see: ${entities.joinToString { it.name }}"
        } else ""

        return "${space.name}\n$description$exits$entitiesStr"
    }

    /**
     * Build a map of exits with their destination space names for navigation parsing (V3).
     * Uses graph node edges to find connected spaces.
     */
    private fun buildExitsWithNames(space: SpacePropertiesComponent): Map<Direction, String> {
        return space.exits.mapNotNull { exitData ->
            val destSpace = worldState.getSpace(exitData.targetId)
            if (destSpace != null) {
                // Convert exit direction string to Direction enum
                val direction = Direction.entries.find { it.displayName.equals(exitData.direction, ignoreCase = true) }
                if (direction != null) {
                    direction to destSpace.name
                } else {
                    null
                }
            } else {
                null
            }
        }.toMap()
    }

    /**
     * Track quest progress after player actions.
     * Returns quest notifications as a string.
     */
    private fun trackQuests(action: QuestAction): String {
        val (updatedPlayer, updatedWorld) = questTracker.updateQuestsAfterAction(
            worldState.player,
            worldState,
            action
        )

        val notifications = mutableListOf<String>()

        // Check if any quest objectives were completed
        if (updatedPlayer != worldState.player) {
            updatedPlayer.activeQuests.forEach { quest ->
                val oldQuest = worldState.player.getQuest(quest.id)
                if (oldQuest != null) {
                    // Check for newly completed objectives
                    quest.objectives.zip(oldQuest.objectives).forEach { (newObj, oldObj) ->
                        if (newObj.isCompleted && !oldObj.isCompleted) {
                            notifications.add("\n✓ Quest objective completed: ${newObj.description}")
                        }
                    }

                    // Check if quest just completed
                    if (quest.status == com.jcraw.mud.core.QuestStatus.COMPLETED &&
                        oldQuest.status == com.jcraw.mud.core.QuestStatus.ACTIVE) {
                        notifications.add("\n🎉 Quest completed: ${quest.title}")
                    }
                }
            }

            // Update both player and world state (world may have NPC disposition changes)
            worldState = updatedWorld.updatePlayer(updatedPlayer)
        }

        return notifications.joinToString("")
    }
}
