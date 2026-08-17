@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList"
)

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.SocialEvent
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.reasoning.EmoteApply
import com.jcraw.mud.reasoning.QuestAction
import kotlinx.coroutines.runBlocking

/**
 * Talk / say / emote / ask for [ClientSocialHandlers] facade (MUD-034l pure-move).
 */
internal object ClientSocialDialogueHandlers {

    fun handleTalk(game: EngineGameClient, target: String) {
        val space = game.currentSpace()
        if (space == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }
        val resolved = ClientSocialNpcResolve.resolveSpaceNpc(game, space, target)
        if (resolved == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }
        emitTalkDialogue(game, resolved)
    }

    suspend fun handleSay(game: EngineGameClient, message: String, npcTarget: String?) {
        val utterance = message.trim()
        if (utterance.isEmpty()) {
            game.emitEvent(GameEvent.System("Say what?", GameEvent.MessageLevel.WARNING))
            return
        }
        val space = game.currentSpace()
        if (space == null) {
            game.emitEvent(GameEvent.Narrative("You say: \"$utterance\""))
            return
        }
        continueSayInSpace(game, space, utterance, npcTarget)
    }

    fun handleEmote(game: EngineGameClient, emoteType: String, target: String?) {
        val space = game.currentSpace()
        if (space == null) {
            return
        }
        if (target.isNullOrBlank()) {
            game.emitEvent(GameEvent.Narrative("You ${emoteType.lowercase()}."))
            return
        }
        val resolved = ClientSocialNpcResolve.resolveSpaceNpc(game, space, target)
        if (resolved == null) {
            game.emitEvent(GameEvent.System("No one by that name here.", GameEvent.MessageLevel.WARNING))
            return
        }
        applyEmote(game, resolved, emoteType)
    }

    suspend fun handleAskQuestion(game: EngineGameClient, npcTarget: String, topic: String) {
        val space = game.currentSpace()
        if (space == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }
        val resolved = ClientSocialNpcResolve.resolveSpaceNpc(game, space, npcTarget)
        if (resolved == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }
        applyAskKnowledge(game, space, resolved, topic)
    }

    private fun emitTalkDialogue(game: EngineGameClient, resolved: Pair<String, Entity.NPC>) {
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

    private suspend fun continueSayInSpace(
        game: EngineGameClient,
        space: SpacePropertiesComponent,
        utterance: String,
        npcTarget: String?
    ) {
        val resolved = ClientSocialNpcResolve.resolveSpaceNpc(game, space, npcTarget)
        if (npcTarget != null && resolved == null) {
            game.emitEvent(GameEvent.System("There's no one here by that name.", GameEvent.MessageLevel.WARNING))
            return
        }
        if (resolved == null) {
            game.emitEvent(GameEvent.Narrative("You say: \"$utterance\""))
            game.lastConversationNpcId = null
            return
        }
        emitSayToNpc(game, resolved, utterance)
    }

    private suspend fun emitSayToNpc(
        game: EngineGameClient,
        resolved: Pair<String, Entity.NPC>,
        utterance: String
    ) {
        val (entityId, npcCandidate) = resolved
        val npc = game.loadEntity(entityId) as? Entity.NPC ?: npcCandidate
        game.emitEvent(GameEvent.Narrative("You say to ${npc.name}: \"$utterance\""))
        game.lastConversationNpcId = entityId
        if (ClientSocialHandlers.isQuestion(utterance)) {
            val topic = utterance.trimEnd('?', ' ').ifBlank { utterance }
            handleAskQuestion(game, npc.name, topic)
            game.trackQuests(QuestAction.TalkedToNPC(entityId))
            return
        }
        emitSayReply(game, npc)
        game.trackQuests(QuestAction.TalkedToNPC(entityId))
    }

    private suspend fun emitSayReply(game: EngineGameClient, npc: Entity.NPC) {
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
    }

    private fun applyEmote(
        game: EngineGameClient,
        resolved: Pair<String, Entity.NPC>,
        emoteType: String
    ) {
        val (entityId, npcCandidate) = resolved
        val npc = game.loadEntity(entityId) as? Entity.NPC ?: npcCandidate
        val spaceId = game.worldState.player.currentRoomId
        when (val result = EmoteApply.apply(game.worldState, spaceId, npc, emoteType, game.emoteHandler)) {
            is EmoteApply.Result.Success -> persistEmoteSuccess(game, result)
            is EmoteApply.Result.Failure -> game.emitEvent(
                GameEvent.System(result.message, GameEvent.MessageLevel.WARNING)
            )
        }
    }

    private fun persistEmoteSuccess(game: EngineGameClient, result: EmoteApply.Result.Success) {
        game.worldState = result.world
        val updated = result.world.getEntity(result.npcId) as? Entity.NPC
        if (updated != null) {
            game.spaceEntityRepository.save(updated).onFailure {
                println("Warning: failed to persist NPC state: ${it.message}")
            }
        }
        game.emitEvent(GameEvent.Narrative(result.narrative))
    }

    private suspend fun applyAskKnowledge(
        game: EngineGameClient,
        space: SpacePropertiesComponent,
        resolved: Pair<String, Entity.NPC>,
        topic: String
    ) {
        val (entityId, npcCandidate) = resolved
        val npc = game.loadEntity(entityId) as? Entity.NPC ?: npcCandidate
        game.lastConversationNpcId = entityId
        ClientSocialNpcResolve.merchantResponse(game, npc, topic)?.let { reply ->
            game.emitEvent(GameEvent.Narrative("${npc.name} says: \"$reply\""))
            game.trackQuests(QuestAction.TalkedToNPC(entityId))
            return
        }
        persistAskAndEmit(game, space, npc, entityId, topic)
    }

    private suspend fun persistAskAndEmit(
        game: EngineGameClient,
        space: SpacePropertiesComponent,
        npc: Entity.NPC,
        entityId: String,
        topic: String
    ) {
        val worldContext = ClientSocialNpcResolve.buildSpaceQuestionContext(game, space, npc, topic)
        val knowledgeResult = game.npcKnowledgeManager.queryKnowledge(npc, topic, worldContext)
        val updatedNpc = applyAskEvent(game, npc, knowledgeResult)
        game.spaceEntityRepository.save(updatedNpc).onFailure {
            println("Warning: failed to persist NPC knowledge update: ${it.message}")
        }
        game.emitEvent(GameEvent.Narrative("${updatedNpc.name} says: \"${knowledgeResult.answer}\""))
        game.trackQuests(QuestAction.TalkedToNPC(entityId))
    }

    private fun applyAskEvent(
        game: EngineGameClient,
        npc: Entity.NPC,
        knowledgeResult: com.jcraw.mud.reasoning.NPCKnowledgeManager.KnowledgeResult
    ): Entity.NPC {
        val questionEvent = SocialEvent.QuestionAsked(
            topic = knowledgeResult.normalizedTopic,
            questionText = knowledgeResult.question,
            answerText = knowledgeResult.answer,
            description = "${game.worldState.player.name} asked ${npc.name} about \"${knowledgeResult.question}\""
        )
        return game.dispositionManager.applyEvent(knowledgeResult.npc, questionEvent)
    }
}
