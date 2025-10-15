package com.jcraw.mud.reasoning

import com.jcraw.mud.core.Difficulty
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SkillChallenge
import com.jcraw.mud.core.StatType
import com.jcraw.mud.core.Stats
import com.jcraw.sophia.llm.LLMClient
import com.jcraw.sophia.llm.OpenAIChoice
import com.jcraw.sophia.llm.OpenAIMessage
import com.jcraw.sophia.llm.OpenAIResponse
import com.jcraw.sophia.llm.OpenAIUsage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SocialInteractionTest {
    private val scriptedReply = "Keep your wits about you beyond the gate, traveler."
    private val dialogueStub = DialogueStubLLMClient(scriptedReply)
    private val generator = NPCInteractionGenerator(llmClient = dialogueStub)

    @Test
    fun `old guard responds with scripted LLM dialogue`() = runBlocking {
        println("1. Creating Old Guard NPC with social challenge scaffold")
        val npc = Entity.NPC(
            id = "old_guard_social",
            name = "Old Guard",
            description = "A weathered sentry keeping watch from a modest post.",
            isHostile = false,
            health = 32,
            maxHealth = 50,
            stats = Stats(
                strength = 12,
                dexterity = 9,
                constitution = 14,
                intelligence = 10,
                wisdom = 15,
                charisma = 13
            ),
            persuasionChallenge = SkillChallenge(
                statType = StatType.CHARISMA,
                difficulty = Difficulty.EASY,
                description = "Gently ask the guard for advice before heading deeper.",
                successDescription = "The Old Guard shares a quiet warning about hidden doors beyond the gate.",
                failureDescription = "The guard shrugs and keeps his secrets."
            )
        )

        println("2. Spawning player state in guard_hearth room")
        val player = PlayerState(
            id = "player-social",
            name = "Test Adventurer",
            currentRoomId = "guard_hearth"
        )

        println("3. Requesting dialogue from NPCInteractionGenerator (LLM stub)")
        val dialogue = generator.generateDialogue(npc, player)

        println("4. LLM returned -> \"$dialogue\"")
        assertEquals(scriptedReply, dialogue)
        assertTrue(dialogueStub.chatCompletionCalls > 0, "Expected LLM client to be invoked at least once")
    }

    private class DialogueStubLLMClient(private val dialogue: String) : LLMClient {
        var chatCompletionCalls: Int = 0
            private set

        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            chatCompletionCalls += 1
            return OpenAIResponse(
                id = "stub-response",
                `object` = "chat.completion",
                created = 0L,
                model = modelId,
                choices = listOf(
                    OpenAIChoice(
                        message = OpenAIMessage(role = "assistant", content = dialogue),
                        finishReason = "stop"
                    )
                ),
                usage = OpenAIUsage(
                    promptTokens = 10,
                    completionTokens = dialogue.length,
                    totalTokens = 10 + dialogue.length
                )
            )
        }

        override suspend fun createEmbedding(text: String, model: String): List<Double> {
            return listOf(0.0, 0.0, 0.0)
        }

        override fun close() {
            // No-op for tests
        }
    }
}
