package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Events that affect NPC disposition
 * Each event has a disposition delta that gets applied
 */
@Serializable
sealed class SocialEvent {
    abstract val dispositionDelta: Int
    abstract val description: String
    abstract val eventType: String
    open val metadata: Map<String, String> = emptyMap()

    @Serializable
    data class HelpProvided(
        override val dispositionDelta: Int = 20,
        override val description: String = "You helped this NPC"
    ) : SocialEvent() {
        override val eventType: String = "HELP_PROVIDED"
    }

    @Serializable
    data class HelpRefused(
        override val dispositionDelta: Int = -10,
        override val description: String = "You refused to help"
    ) : SocialEvent() {
        override val eventType: String = "HELP_REFUSED"
    }

    @Serializable
    data class Threatened(
        override val dispositionDelta: Int = -15,
        override val description: String = "You threatened this NPC"
    ) : SocialEvent() {
        override val eventType: String = "THREATENED"
    }

    @Serializable
    data class Intimidated(
        val success: Boolean,
        override val description: String = if (success) "You successfully intimidated this NPC" else "You failed to intimidate this NPC"
    ) : SocialEvent() {
        override val dispositionDelta: Int = if (success) -20 else -5
        override val eventType: String = "INTIMIDATED"
    }

    @Serializable
    data class Persuaded(
        val success: Boolean,
        override val description: String = if (success) "You successfully persuaded this NPC" else "You failed to persuade this NPC"
    ) : SocialEvent() {
        override val dispositionDelta: Int = if (success) 15 else -2
        override val eventType: String = "PERSUADED"
    }

    @Serializable
    data class GiftGiven(
        val itemValue: Int,
        override val description: String = "You gave a gift"
    ) : SocialEvent() {
        override val dispositionDelta: Int = (itemValue / 10).coerceIn(5, 30)
        override val eventType: String = "GIFT_GIVEN"
    }

    @Serializable
    data class EmotePerformed(
        val emoteType: EmoteType,
        override val description: String
    ) : SocialEvent() {
        override val dispositionDelta: Int = emoteType.dispositionDelta
        override val eventType: String = "EMOTE_PERFORMED"
    }

    @Serializable
    data class QuestCompleted(
        val questId: String,
        override val dispositionDelta: Int = 30,
        override val description: String = "You completed a quest for this NPC"
    ) : SocialEvent() {
        override val eventType: String = "QUEST_COMPLETED"
    }

    @Serializable
    data class QuestFailed(
        val questId: String,
        override val dispositionDelta: Int = -25,
        override val description: String = "You failed a quest for this NPC"
    ) : SocialEvent() {
        override val eventType: String = "QUEST_FAILED"
    }

    @Serializable
    data class AttackAttempted(
        override val dispositionDelta: Int = -100,
        override val description: String = "You attacked this NPC"
    ) : SocialEvent() {
        override val eventType: String = "ATTACK_ATTEMPTED"
    }

    @Serializable
    data class ConversationHeld(
        override val dispositionDelta: Int = 1,
        override val description: String = "You had a conversation"
    ) : SocialEvent() {
        override val eventType: String = "CONVERSATION_HELD"
    }

    @Serializable
    data class QuestionAsked(
        val topic: String,
        val questionText: String,
        val answerText: String,
        override val description: String,
        override val dispositionDelta: Int = 0
    ) : SocialEvent() {
        override val eventType: String = "QUESTION_ASKED"
        override val metadata: Map<String, String> = mapOf(
            "topic" to topic,
            "question" to questionText,
            "answer" to answerText
        )
    }

    @Serializable
    data class PersuasionAttempt(
        override val dispositionDelta: Int,
        override val description: String
    ) : SocialEvent() {
        override val eventType: String = "PERSUASION_ATTEMPT"
    }

    @Serializable
    data class IntimidationAttempt(
        override val dispositionDelta: Int,
        override val description: String
    ) : SocialEvent() {
        override val eventType: String = "INTIMIDATION_ATTEMPT"
    }
}

/**
 * Types of emotes players can perform
 */
@Serializable
enum class EmoteType(val dispositionDelta: Int, val keywords: List<String>) {
    BOW(5, listOf("bow", "curtsy", "kneel")),
    WAVE(2, listOf("wave", "greet", "hello")),
    NOD(1, listOf("nod", "agree")),
    SHAKE_HEAD(-1, listOf("shake head", "disagree")),
    LAUGH(3, listOf("laugh", "chuckle", "smile")),
    INSULT(-10, listOf("insult", "mock", "taunt")),
    THREATEN(-15, listOf("threaten", "menace", "glare"));

    companion object {
        fun fromKeyword(keyword: String): EmoteType? {
            return values().find { emote ->
                emote.keywords.any { it.equals(keyword, ignoreCase = true) }
            }
        }
    }
}
