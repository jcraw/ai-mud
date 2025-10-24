package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.QuestAction
import kotlinx.coroutines.runBlocking

/**
 * Handles social interactions (talking, emotes, persuasion, intimidation) in the GUI client.
 */
object ClientSocialHandlers {

    fun handleTalk(game: EngineGameClient, target: String) {
        val room = game.worldState.getCurrentRoom() ?: return

        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (npc == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }

        game.lastConversationNpcId = npc.id

        if (game.npcInteractionGenerator != null) {
            game.emitEvent(GameEvent.Narrative("\nYou speak to ${npc.name}..."))
            val dialogue = runBlocking {
                game.npcInteractionGenerator.generateDialogue(npc, game.worldState.player)
            }
            game.emitEvent(GameEvent.Narrative("\n${npc.name} says: \"$dialogue\""))
        } else {
            if (npc.isHostile) {
                game.emitEvent(GameEvent.Narrative("\n${npc.name} glares at you menacingly and says nothing."))
            } else {
                game.emitEvent(GameEvent.Narrative("\n${npc.name} nods at you in acknowledgment."))
            }
        }

        // Track NPC conversation for quests
        game.trackQuests(QuestAction.TalkedToNPC(npc.id))
    }

    suspend fun handleSay(game: EngineGameClient, message: String, npcTarget: String?) {
        val utterance = message.trim()
        if (utterance.isEmpty()) {
            game.emitEvent(GameEvent.System("Say what?", GameEvent.MessageLevel.WARNING))
            return
        }

        val room = game.worldState.getCurrentRoom() ?: return

        val npc = resolveNpcTarget(game, room, npcTarget)
        if (npcTarget != null && npc == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }

        if (npc == null) {
            game.emitEvent(GameEvent.Narrative("You say: \"$utterance\""))
            game.lastConversationNpcId = null
            return
        }

        game.emitEvent(GameEvent.Narrative("You say to ${npc.name}: \"$utterance\""))
        game.lastConversationNpcId = npc.id

        if (isQuestion(utterance)) {
            val topic = utterance.trimEnd('?', ' ').ifBlank { utterance }
            handleAskQuestion(game, npc.name, topic)
            game.trackQuests(QuestAction.TalkedToNPC(npc.id))
            return
        }

        if (game.npcInteractionGenerator != null) {
            val reply = runCatching {
                game.npcInteractionGenerator?.generateDialogue(npc, game.worldState.player)
            }.getOrElse {
                println("Warning: NPC dialogue generation failed: ${it.message}")
                null
            }

            if (reply != null) {
                game.emitEvent(GameEvent.Narrative("${npc.name} says: \"$reply\""))
            }
        } else {
            val fallbackResponse = if (npc.isHostile) {
                "${npc.name} scowls and refuses to answer."
            } else {
                "${npc.name} listens quietly."
            }
            game.emitEvent(GameEvent.Narrative(fallbackResponse))
        }

        game.trackQuests(QuestAction.TalkedToNPC(npc.id))
    }

    fun handleEmote(game: EngineGameClient, emoteType: String, target: String?) {
        val room = game.worldState.getCurrentRoom() ?: return

        // If no target specified, perform general emote
        if (target.isNullOrBlank()) {
            game.emitEvent(GameEvent.Narrative("You ${emoteType.lowercase()}."))
            return
        }

        // Find target NPC
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

        if (npc == null) {
            game.emitEvent(GameEvent.System("No one by that name here.", GameEvent.MessageLevel.WARNING))
            return
        }

        // Parse emote keyword
        val emoteTypeEnum = game.emoteHandler.parseEmoteKeyword(emoteType)
        if (emoteTypeEnum == null) {
            game.emitEvent(GameEvent.System("Unknown emote: $emoteType", GameEvent.MessageLevel.WARNING))
            return
        }

        // Process emote
        val (narrative, updatedNpc) = game.emoteHandler.processEmote(npc, emoteTypeEnum, "You")

        // Update world state with updated NPC
        game.worldState = game.worldState.replaceEntity(room.id, npc.id, updatedNpc) ?: game.worldState

        game.emitEvent(GameEvent.Narrative(narrative))
    }

    suspend fun handleAskQuestion(game: EngineGameClient, npcTarget: String, topic: String) {
        val room = game.worldState.getCurrentRoom() ?: return

        // Find the NPC in the room
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(npcTarget.lowercase()) ||
                entity.id.lowercase().contains(npcTarget.lowercase())
            }

        if (npc == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }

        game.lastConversationNpcId = npc.id

        // Query knowledge
        val (answer, updatedNpc) = game.npcKnowledgeManager.queryKnowledge(npc, topic)

        // Update world state with updated NPC (may have new knowledge)
        game.worldState = game.worldState.replaceEntity(room.id, npc.id, updatedNpc) ?: game.worldState

        game.emitEvent(GameEvent.Narrative("${npc.name} says: \"$answer\""))
        game.trackQuests(QuestAction.TalkedToNPC(npc.id))
    }

    fun handleCheck(game: EngineGameClient, target: String) {
        game.emitEvent(GameEvent.System("Skill check system integrated - implement if needed", GameEvent.MessageLevel.INFO))
    }

    fun handlePersuade(game: EngineGameClient, target: String) {
        game.emitEvent(GameEvent.System("Persuasion system integrated - implement if needed", GameEvent.MessageLevel.INFO))
    }

    fun handleIntimidate(game: EngineGameClient, target: String) {
        game.emitEvent(GameEvent.System("Intimidation system integrated - implement if needed", GameEvent.MessageLevel.INFO))
    }

    // Helper functions

    fun resolveNpcTarget(game: EngineGameClient, room: Room, npcTarget: String?): Entity.NPC? {
        val candidates = room.entities.filterIsInstance<Entity.NPC>()
        if (npcTarget != null) {
            val lower = npcTarget.lowercase()
            val explicit = candidates.find {
                it.name.lowercase().contains(lower) || it.id.lowercase().contains(lower)
            }
            if (explicit != null) {
                return explicit
            }
        }

        val recentId = game.lastConversationNpcId
        if (recentId != null) {
            return candidates.find { it.id == recentId }
        }

        return null
    }

    fun isQuestion(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.endsWith("?")) return true

        val lower = trimmed.lowercase()
        val questionPrefixes = listOf(
            "who", "what", "where", "when", "why", "how",
            "can", "will", "is", "are", "am",
            "do", "does", "did",
            "should", "could", "would",
            "have", "has", "had",
            "tell me", "explain", "describe"
        )

        return questionPrefixes.any { lower.startsWith(it) }
    }
}
