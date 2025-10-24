package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.Entity
import com.jcraw.mud.reasoning.QuestAction
import kotlinx.coroutines.runBlocking

/**
 * Social interaction handlers for NPC dialogue, emotes, persuasion, and intimidation.
 * Manages conversation state, NPC knowledge queries, and disposition changes.
 */
object SocialHandlers {
    /**
     * Handle talking to an NPC - initiates conversation and generates dialogue
     */
    fun handleTalk(game: MudGame, target: String) {
        val room = game.worldState.getCurrentRoom() ?: return

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

        game.lastConversationNpcId = npc.id

        // Generate dialogue
        if (game.npcInteractionGenerator != null) {
            println("\nYou speak to ${npc.name}...")
            val dialogue = runBlocking {
                game.npcInteractionGenerator.generateDialogue(npc, game.worldState.player)
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
        game.trackQuests(QuestAction.TalkedToNPC(npc.id))
    }

    /**
     * Handle saying something - player speaks to NPCs or to the room
     */
    fun handleSay(game: MudGame, message: String, npcTarget: String?) {
        val utterance = message.trim()
        if (utterance.isEmpty()) {
            println("\nSay what?")
            return
        }

        val room = game.worldState.getCurrentRoom() ?: return
        val npc = resolveNpcTarget(game, room, npcTarget)

        if (npcTarget != null && npc == null) {
            println("\nThere's no one here by that name.")
            return
        }

        if (npc == null) {
            println("\nYou say: \"$utterance\"")
            game.lastConversationNpcId = null
            return
        }

        println("\nYou say to ${npc.name}: \"$utterance\"")
        game.lastConversationNpcId = npc.id

        if (isQuestion(utterance)) {
            val topic = utterance.trimEnd('?', ' ').ifBlank { utterance }
            runBlocking {
                handleAskQuestion(game, npc.name, topic)
            }
            game.trackQuests(QuestAction.TalkedToNPC(npc.id))
            return
        }

        if (game.npcInteractionGenerator != null) {
            val reply = runCatching {
                runBlocking {
                    game.npcInteractionGenerator?.generateDialogue(npc, game.worldState.player)
                }
            }.getOrElse {
                println("⚠️  NPC dialogue generation failed: ${it.message}")
                null
            }

            if (reply != null) {
                println("\n${npc.name} says: \"$reply\"")
            }
        } else {
            if (npc.isHostile) {
                println("\n${npc.name} scowls and refuses to answer.")
            } else {
                println("\n${npc.name} listens quietly.")
            }
        }

        game.trackQuests(QuestAction.TalkedToNPC(npc.id))
    }

    /**
     * Handle emote actions - player performs social gestures toward NPCs
     */
    fun handleEmote(game: MudGame, emoteType: String, target: String?) {
        val room = game.worldState.getCurrentRoom() ?: return

        // If no target specified, perform general emote
        if (target.isNullOrBlank()) {
            println("\nYou ${emoteType.lowercase()}.")
            return
        }

        // Find target NPC
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

        if (npc == null) {
            println("\nNo one by that name here.")
            return
        }

        // Parse emote keyword
        val emoteTypeEnum = game.emoteHandler.parseEmoteKeyword(emoteType)
        if (emoteTypeEnum == null) {
            println("\nUnknown emote: $emoteType")
            return
        }

        // Process emote
        val (narrative, updatedNpc) = game.emoteHandler.processEmote(npc, emoteTypeEnum, "You")

        // Update world state with updated NPC
        game.worldState = game.worldState.replaceEntity(room.id, npc.id, updatedNpc) ?: game.worldState

        println("\n$narrative")
    }

    /**
     * Handle asking NPCs questions - queries NPC knowledge base
     */
    suspend fun handleAskQuestion(game: MudGame, npcTarget: String, topic: String) {
        val room = game.worldState.getCurrentRoom() ?: return

        // Find the NPC in the room
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(npcTarget.lowercase()) ||
                entity.id.lowercase().contains(npcTarget.lowercase())
            }

        if (npc == null) {
            println("\nThere's no one here by that name.")
            return
        }

        game.lastConversationNpcId = npc.id

        // Query knowledge
        val (answer, updatedNpc) = game.npcKnowledgeManager.queryKnowledge(npc, topic)

        // Update world state with updated NPC (may have new knowledge)
        game.worldState = game.worldState.replaceEntity(room.id, npc.id, updatedNpc) ?: game.worldState

        println("\n${npc.name} says: \"$answer\"")
        game.trackQuests(QuestAction.TalkedToNPC(npc.id))
    }

    /**
     * Handle persuasion attempts - CHA-based skill check to change NPC behavior
     */
    fun handlePersuade(game: MudGame, target: String) {
        val room = game.worldState.getCurrentRoom() ?: return

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
        val result = game.skillCheckResolver.checkPlayer(
            game.worldState.player,
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
            game.worldState = game.worldState.replaceEntity(room.id, npc.id, updatedNpc) ?: game.worldState
        } else {
            println("\n❌ Failure!")
            println(challenge.failureDescription)
        }
    }

    /**
     * Handle intimidation attempts - CHA-based skill check to frighten NPCs
     */
    fun handleIntimidate(game: MudGame, target: String) {
        val room = game.worldState.getCurrentRoom() ?: return

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
        val result = game.skillCheckResolver.checkPlayer(
            game.worldState.player,
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
            game.worldState = game.worldState.replaceEntity(room.id, npc.id, updatedNpc) ?: game.worldState
        } else {
            println("\n❌ Failure!")
            println(challenge.failureDescription)
        }
    }

    // ========== Helper Functions ==========

    /**
     * Resolve NPC target from explicit name or recent conversation
     */
    private fun resolveNpcTarget(game: MudGame, room: com.jcraw.mud.core.Room, npcTarget: String?): Entity.NPC? {
        val npcs = room.entities.filterIsInstance<Entity.NPC>()
        if (npcTarget != null) {
            val lower = npcTarget.lowercase()
            val explicit = npcs.find {
                it.name.lowercase().contains(lower) || it.id.lowercase().contains(lower)
            }
            if (explicit != null) {
                return explicit
            }
        }

        val recent = game.lastConversationNpcId
        if (recent != null) {
            return npcs.find { it.id == recent }
        }

        return null
    }

    /**
     * Detect if utterance is a question based on punctuation or question words
     */
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
}
