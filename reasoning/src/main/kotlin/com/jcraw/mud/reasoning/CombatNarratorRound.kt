@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "NestedBlockDepth",
    "TooManyFunctions",
    "TooGenericExceptionCaught",
    "SwallowedException"
)

package com.jcraw.mud.reasoning

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.EquipSlot
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.sophia.llm.LLMClient

/** Params for round narration (MUD-034k). */
internal data class RoundNarrateParams(
    val llmClient: LLMClient,
    val memoryManager: MemoryManager?,
    val worldState: WorldState,
    val npc: Entity.NPC,
    val playerDamage: Int,
    val npcDamage: Int,
    val npcDied: Boolean,
    val playerDied: Boolean
)

/**
 * Combat round narration for [CombatNarrator] (MUD-034k pure-move).
 */
internal object CombatNarratorRound {

    suspend fun narrate(p: RoundNarrateParams): String {
        val space = p.worldState.getCurrentSpace() ?: return "You fight in darkness..."
        val memories = p.memoryManager?.recall("combat with ${p.npc.name}", k = 2) ?: emptyList()
        val userContext = CombatNarratorRoundPrompt.build(
            p.worldState, space.name, space.terrainType.toString(), p.npc,
            p.playerDamage, p.npcDamage, p.npcDied, p.playerDied, memories
        )
        return callOrFallback(p, userContext)
    }

    private suspend fun callOrFallback(p: RoundNarrateParams, userContext: String): String = try {
        val response = p.llmClient.chatCompletion(
            modelId = "gpt-4o-mini",
            systemPrompt = CombatNarratorRoundPrompt.system(),
            userContext = userContext,
            maxTokens = 80,
            temperature = 0.8
        )
        val narrative = response.choices.firstOrNull()?.message?.content?.trim()
            ?: fallback(p.npc.name, p.playerDamage, p.npcDamage, p.npcDied, p.playerDied)
        p.memoryManager?.remember(
            "Combat with ${p.npc.name}: $narrative",
            mapOf("type" to "combat", "npc" to p.npc.name)
        )
        narrative
    } catch (e: Exception) {
        fallback(p.npc.name, p.playerDamage, p.npcDamage, p.npcDied, p.playerDied)
    }

    fun fallback(
        npcName: String,
        playerDamage: Int,
        npcDamage: Int,
        npcDied: Boolean,
        playerDied: Boolean
    ): String = buildString {
        append("You strike $npcName for $playerDamage damage!")
        when {
            npcDied -> append(" $npcName falls defeated!")
            playerDied -> {
                appendLine()
                append("$npcName's counter-attack for $npcDamage damage strikes you down!")
            }
            else -> {
                appendLine()
                append("$npcName retaliates for $npcDamage damage!")
            }
        }
    }
}
