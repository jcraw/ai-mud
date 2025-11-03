package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.client.SpaceEntitySupport
import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.QuestAction
import kotlinx.coroutines.runBlocking

/**
 * Handles social interactions (talking, emotes, persuasion, intimidation) in the GUI client.
 */
object ClientSocialHandlers {

    fun handleTalk(game: EngineGameClient, target: String) {
        val room = game.worldState.getCurrentRoom()

        if (room != null) {
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

            game.trackQuests(QuestAction.TalkedToNPC(npc.id))
            return
        }

        val space = game.currentSpace()
        if (space == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }

        val resolved = resolveSpaceNpc(game, space, target)
        if (resolved == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }

        val (entityId, npcCandidate) = resolved
        val persistedNpc = game.loadEntity(entityId) as? Entity.NPC
        val targetNpc = persistedNpc ?: npcCandidate

        game.lastConversationNpcId = entityId

        if (game.npcInteractionGenerator != null) {
            game.emitEvent(GameEvent.Narrative("\nYou speak to ${targetNpc.name}..."))
            val dialogue = runBlocking {
                game.npcInteractionGenerator.generateDialogue(targetNpc, game.worldState.player)
            }
            game.emitEvent(GameEvent.Narrative("\n${targetNpc.name} says: \"$dialogue\""))
        } else {
            val fallback = persistedNpc?.description ?: npcCandidate.description
            game.emitEvent(GameEvent.Narrative("\n${targetNpc.name} greets you: \"$fallback\""))
        }

        game.trackQuests(QuestAction.TalkedToNPC(entityId))
    }

    suspend fun handleSay(game: EngineGameClient, message: String, npcTarget: String?) {
        val utterance = message.trim()
        if (utterance.isEmpty()) {
            game.emitEvent(GameEvent.System("Say what?", GameEvent.MessageLevel.WARNING))
            return
        }

        val room = game.worldState.getCurrentRoom()
        val space = if (room == null) game.currentSpace() else null

        if (room == null && space == null) {
            game.emitEvent(GameEvent.Narrative("You say: \"$utterance\""))
            return
        }

        if (room != null) {
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
            return
        }

        val resolved = resolveSpaceNpc(game, space!!, npcTarget)
        if (npcTarget != null && resolved == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }

        if (resolved == null) {
            game.emitEvent(GameEvent.Narrative("You say: \"$utterance\""))
            game.lastConversationNpcId = null
            return
        }

        val (entityId, npcCandidate) = resolved
        val npc = game.loadEntity(entityId) as? Entity.NPC ?: npcCandidate
        game.emitEvent(GameEvent.Narrative("You say to ${npc.name}: \"$utterance\""))
        game.lastConversationNpcId = entityId

        if (isQuestion(utterance)) {
            val topic = utterance.trimEnd('?', ' ').ifBlank { utterance }
            handleAskQuestion(game, npc.name, topic)
            game.trackQuests(QuestAction.TalkedToNPC(entityId))
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

        game.trackQuests(QuestAction.TalkedToNPC(entityId))
    }
    fun handleEmote(game: EngineGameClient, emoteType: String, target: String?) {
        val room = game.worldState.getCurrentRoom()
        val space = if (room == null) game.currentSpace() else null

        if (room == null && space == null) {
            return
        }

        if (target.isNullOrBlank()) {
            game.emitEvent(GameEvent.Narrative("You ${emoteType.lowercase()}."))
            return
        }

        if (room != null) {
            val npc = room.entities.filterIsInstance<Entity.NPC>()
                .find { it.name.lowercase().contains(target.lowercase()) || it.id.lowercase().contains(target.lowercase()) }

            if (npc == null) {
                game.emitEvent(GameEvent.System("No one by that name here.", GameEvent.MessageLevel.WARNING))
                return
            }

            val emoteTypeEnum = game.emoteHandler.parseEmoteKeyword(emoteType)
            if (emoteTypeEnum == null) {
                game.emitEvent(GameEvent.System("Unknown emote: $emoteType", GameEvent.MessageLevel.WARNING))
                return
            }

            val (narrative, updatedNpc) = game.emoteHandler.processEmote(npc, emoteTypeEnum, "You")
            game.worldState = game.worldState.replaceEntity(room.id, npc.id, updatedNpc) ?: game.worldState
            game.emitEvent(GameEvent.Narrative(narrative))
            return
        }

        val resolved = resolveSpaceNpc(game, space!!, target)
        if (resolved == null) {
            game.emitEvent(GameEvent.System("No one by that name here.", GameEvent.MessageLevel.WARNING))
            return
        }

        val (entityId, npcCandidate) = resolved
        val npc = game.loadEntity(entityId) as? Entity.NPC ?: npcCandidate
        val emoteTypeEnum = game.emoteHandler.parseEmoteKeyword(emoteType)
        if (emoteTypeEnum == null) {
            game.emitEvent(GameEvent.System("Unknown emote: $emoteType", GameEvent.MessageLevel.WARNING))
            return
        }

        val (narrative, updatedNpc) = game.emoteHandler.processEmote(npc, emoteTypeEnum, "You")
        // Persist updated state if NPC exists in repository
        game.spaceEntityRepository.save(updatedNpc).onFailure {
            println("Warning: failed to persist NPC state: ${it.message}")
        }

        game.emitEvent(GameEvent.Narrative(narrative))
    }

    suspend fun handleAskQuestion(game: EngineGameClient, npcTarget: String, topic: String) {
        val room = game.worldState.getCurrentRoom()
        val space = if (room == null) game.currentSpace() else null

        if (room == null && space == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }

        if (room != null) {
            val npc = room.entities.filterIsInstance<Entity.NPC>()
                .find {
                    it.name.lowercase().contains(npcTarget.lowercase()) ||
                    it.id.lowercase().contains(npcTarget.lowercase())
                }

            if (npc == null) {
                game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
                return
            }

            game.lastConversationNpcId = npc.id

            merchantResponse(game, npc, topic)?.let { reply ->
                game.emitEvent(GameEvent.Narrative("${npc.name} says: \"$reply\""))
                game.trackQuests(QuestAction.TalkedToNPC(npc.id))
                return
            }

            val worldContext = buildRoomQuestionContext(game, room, npc, topic)
            val knowledgeResult = game.npcKnowledgeManager.queryKnowledge(npc, topic, worldContext)
            var updatedNpc = knowledgeResult.npc

            val questionEvent = SocialEvent.QuestionAsked(
                topic = knowledgeResult.normalizedTopic,
                questionText = knowledgeResult.question,
                answerText = knowledgeResult.answer,
                description = "${game.worldState.player.name} asked ${npc.name} about \"${knowledgeResult.question}\""
            )
            updatedNpc = game.dispositionManager.applyEvent(updatedNpc, questionEvent)

            game.worldState = game.worldState.replaceEntity(room.id, npc.id, updatedNpc) ?: game.worldState
            game.emitEvent(GameEvent.Narrative("${updatedNpc.name} says: \"${knowledgeResult.answer}\""))
            game.trackQuests(QuestAction.TalkedToNPC(npc.id))
            return
        }

        val resolved = resolveSpaceNpc(game, space!!, npcTarget)
        if (resolved == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }

        val (entityId, npcCandidate) = resolved
        val npc = game.loadEntity(entityId) as? Entity.NPC ?: npcCandidate
        game.lastConversationNpcId = entityId

        merchantResponse(game, npc, topic)?.let { reply ->
            game.emitEvent(GameEvent.Narrative("${npc.name} says: \"$reply\""))
            game.trackQuests(QuestAction.TalkedToNPC(entityId))
            return
        }

        val worldContext = buildSpaceQuestionContext(game, space, npc, topic)
        val knowledgeResult = game.npcKnowledgeManager.queryKnowledge(npc, topic, worldContext)
        var updatedNpc = knowledgeResult.npc

        val questionEvent = SocialEvent.QuestionAsked(
            topic = knowledgeResult.normalizedTopic,
            questionText = knowledgeResult.question,
            answerText = knowledgeResult.answer,
            description = "${game.worldState.player.name} asked ${npc.name} about \"${knowledgeResult.question}\""
        )
        updatedNpc = game.dispositionManager.applyEvent(updatedNpc, questionEvent)

        game.spaceEntityRepository.save(updatedNpc).onFailure {
            println("Warning: failed to persist NPC knowledge update: ${it.message}")
        }

        game.emitEvent(GameEvent.Narrative("${updatedNpc.name} says: \"${knowledgeResult.answer}\""))
        game.trackQuests(QuestAction.TalkedToNPC(entityId))
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

    private fun buildRoomQuestionContext(
        game: EngineGameClient,
        room: Room,
        npc: Entity.NPC,
        topic: String
    ): String {
        val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
        return buildString {
            appendLine("Location: ${room.name}")
            if (room.traits.isNotEmpty()) {
                appendLine("Location traits: ${room.traits.joinToString()}")
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
            appendLine("Player name: ${game.worldState.player.name}")
            appendLine("Topic requested: $topic")
        }
    }

    private fun buildSpaceQuestionContext(
        game: EngineGameClient,
        space: SpacePropertiesComponent,
        npc: Entity.NPC,
        topic: String
    ): String {
        val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
        return buildString {
            appendLine("Space description: ${space.description}")
            appendLine("NPC name: ${npc.name}")
            appendLine("NPC description: ${npc.description}")
            if (social != null) {
                appendLine("NPC personality: ${social.personality}")
                if (social.traits.isNotEmpty()) {
                    appendLine("NPC traits: ${social.traits.joinToString()}")
                }
                appendLine("NPC disposition score: ${social.disposition}")
            }
            appendLine("Player name: ${game.worldState.player.name}")
            appendLine("Topic requested: $topic")
        }
    }

    private fun merchantResponse(game: EngineGameClient, npc: Entity.NPC, topic: String): String? {
        val trading = npc.getComponent<TradingComponent>(ComponentType.TRADING) ?: return null
        val lowerTopic = topic.lowercase()
        val keywords = listOf("sell", "stock", "wares", "goods", "buy", "inventory", "offer", "shop")
        if (keywords.none { lowerTopic.contains(it) }) {
            return null
        }

        if (trading.stock.isEmpty()) {
            return "I'm afraid my shelves are empty right now."
        }

        val disposition = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)?.disposition ?: 0
        val entries = trading.stock
            .sortedBy { it.templateId }
            .take(5)
            .map { instance ->
                val template = game.getItemTemplate(instance.templateId)
                val price = trading.calculateBuyPrice(template, instance, disposition)
                val quantityText = if (instance.quantity > 1) " (x${instance.quantity})" else ""
                "${template.name}$quantityText for $price gold"
            }

        val moreSuffix = if (trading.stock.size > entries.size) {
            " Ask if you'd like to see the rest."
        } else {
            ""
        }

        return "I'm selling ${entries.joinToString(", ")}.$moreSuffix"
    }

    private fun resolveSpaceNpc(
        game: EngineGameClient,
        space: SpacePropertiesComponent,
        npcTarget: String?
    ): Pair<String, Entity.NPC>? {
        if (space.entities.isEmpty()) return null

        val candidates = space.entities.map { id ->
            val persisted = game.loadEntity(id) as? Entity.NPC
            val npc = persisted ?: SpaceEntitySupport.createNpcStub(SpaceEntitySupport.getStub(id))
            id to npc
        }

        val lower = npcTarget?.lowercase()
        if (lower != null) {
            candidates.firstOrNull { (_, npc) ->
                npc.name.lowercase().contains(lower) || npc.id.lowercase().contains(lower)
            }?.let { return it }
        }

        val recent = game.lastConversationNpcId
        if (recent != null) {
            candidates.firstOrNull { it.first == recent }?.let { return it }
        }

        return candidates.firstOrNull()
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
