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

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.core.SocialEvent
import com.jcraw.mud.reasoning.EmoteApply
import com.jcraw.mud.reasoning.QuestAction
import kotlinx.coroutines.runBlocking

/**
 * Talk / say / emote / ask for [SocialHandlers] facade (MUD-034l pure-move).
 */
internal object SocialDialogueHandlers {

    fun handleTalk(game: MudGame, target: String) {
        val npc = SocialNpcResolve.findNpcByName(game, target)
        if (npc == null) {
            println("There's no one here by that name.")
            return
        }
        game.lastConversationNpcId = npc.id
        emitTalkDialogue(game, npc)
        game.trackQuests(QuestAction.TalkedToNPC(npc.id))
    }

    fun handleSay(game: MudGame, message: String, npcTarget: String?) {
        val utterance = message.trim()
        if (utterance.isEmpty()) {
            println("\nSay what?")
            return
        }
        val npc = SocialNpcResolve.resolveNpcTarget(game, npcTarget)
        if (npcTarget != null && npc == null) {
            println("\nThere's no one here by that name.")
            return
        }
        if (npc == null) {
            println("\nYou say: \"$utterance\"")
            game.lastConversationNpcId = null
            return
        }
        emitSayToNpc(game, npc, utterance)
    }

    fun handleEmote(game: MudGame, emoteType: String, target: String?) {
        if (target.isNullOrBlank()) {
            println("\nYou ${emoteType.lowercase()}.")
            return
        }
        val npc = SocialNpcResolve.findNpcByName(game, target)
        if (npc == null) {
            println("\nNo one by that name here.")
            return
        }
        applyEmote(game, npc, emoteType)
    }

    suspend fun handleAskQuestion(game: MudGame, npcTarget: String, topic: String) {
        val spaceId = game.worldState.player.currentRoomId
        val npc = SocialNpcResolve.findNpcByName(game, npcTarget)
        if (npc == null) {
            println("\nThere's no one here by that name.")
            return
        }
        game.lastConversationNpcId = npc.id
        applyAskKnowledge(game, spaceId, npc, topic)
    }

    private fun emitTalkDialogue(game: MudGame, npc: Entity.NPC) {
        if (game.npcInteractionGenerator != null) {
            println("\nYou speak to ${npc.name}...")
            val dialogue = runBlocking {
                game.npcInteractionGenerator.generateDialogue(npc, game.worldState.player)
            }
            println("\n${npc.name} says: \"$dialogue\"")
        } else {
            if (npc.isHostile) {
                println("\n${npc.name} glares at you menacingly and says nothing.")
            } else {
                println("\n${npc.name} nods at you in acknowledgment.")
            }
        }
    }

    private fun emitSayToNpc(game: MudGame, npc: Entity.NPC, utterance: String) {
        println("\nYou say to ${npc.name}: \"$utterance\"")
        game.lastConversationNpcId = npc.id
        if (SocialNpcResolve.isQuestion(utterance)) {
            val topic = utterance.trimEnd('?', ' ').ifBlank { utterance }
            runBlocking {
                handleAskQuestion(game, npc.name, topic)
            }
            game.trackQuests(QuestAction.TalkedToNPC(npc.id))
            return
        }
        emitSayReply(game, npc)
        game.trackQuests(QuestAction.TalkedToNPC(npc.id))
    }

    private fun emitSayReply(game: MudGame, npc: Entity.NPC) {
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
    }

    private fun applyEmote(game: MudGame, npc: Entity.NPC, emoteType: String) {
        val spaceId = game.worldState.player.currentRoomId
        when (val result = EmoteApply.apply(game.worldState, spaceId, npc, emoteType, game.emoteHandler)) {
            is EmoteApply.Result.Success -> {
                game.worldState = result.world
                println("\n${result.narrative}")
            }
            is EmoteApply.Result.Failure -> println("\n${result.message}")
        }
    }

    private suspend fun applyAskKnowledge(game: MudGame, spaceId: String, npc: Entity.NPC, topic: String) {
        val worldContext = buildQuestionContext(game, npc, topic)
        val knowledgeResult = game.npcKnowledgeManager.queryKnowledge(npc, topic, worldContext)
        var updatedNpc = knowledgeResult.npc
        val questionEvent = SocialEvent.QuestionAsked(
            topic = knowledgeResult.normalizedTopic,
            questionText = knowledgeResult.question,
            answerText = knowledgeResult.answer,
            description = "${game.worldState.player.name} asked ${npc.name} about \"${knowledgeResult.question}\""
        )
        updatedNpc = game.dispositionManager.applyEvent(updatedNpc, questionEvent)
        game.worldState = game.worldState.replaceEntityInSpace(spaceId, npc.id, updatedNpc) ?: game.worldState
        println("\n${npc.name} says: \"${knowledgeResult.answer}\"")
        game.trackQuests(QuestAction.TalkedToNPC(npc.id))
    }

    private fun buildQuestionContext(game: MudGame, npc: Entity.NPC, topic: String): String {
        val player = game.worldState.player
        val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
        val locationName = "Current Location"
        val locationTraits = emptyList<String>()
        return buildString {
            appendLine("Location: $locationName")
            if (locationTraits.isNotEmpty()) {
                appendLine("Location traits: ${locationTraits.joinToString()}")
            }
            appendLine("NPC name: ${npc.name}")
            appendLine("NPC description: ${npc.description}")
            appendSocialContext(this, social)
            appendLine("Player name: ${player.name}")
            appendLine("Player disposition towards NPC: ${npc.getDisposition()}")
            appendLine("Topic requested: $topic")
        }
    }

    private fun appendSocialContext(builder: StringBuilder, social: SocialComponent?) {
        if (social == null) return
        builder.appendLine("NPC personality: ${social.personality}")
        if (social.traits.isNotEmpty()) {
            builder.appendLine("NPC traits: ${social.traits.joinToString()}")
        }
        builder.appendLine("NPC disposition score: ${social.disposition}")
    }
}
