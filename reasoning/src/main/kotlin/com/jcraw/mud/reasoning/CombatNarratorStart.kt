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
import com.jcraw.sophia.llm.LLMClient

/**
 * Combat start narration for [CombatNarrator] (MUD-034k pure-move).
 */
internal object CombatNarratorStart {

    suspend fun narrate(
        llmClient: LLMClient,
        worldState: WorldState,
        npc: Entity.NPC
    ): String {
        val space = worldState.getCurrentSpace() ?: return "Combat begins..."
        val weapon = playerWeapon(worldState)
        val userContext = userContext(space.name, space.terrainType.toString(), weapon, npc)
        return callOrFallback(llmClient, userContext, npc, weapon)
    }

    private fun playerWeapon(worldState: WorldState): String {
        val item = worldState.player.inventoryComponent.getEquipped(EquipSlot.HANDS_MAIN)
            ?: worldState.player.inventoryComponent.getEquipped(EquipSlot.HANDS_BOTH)
        return item?.templateId ?: "bare fists"
    }

    private fun userContext(
        spaceName: String,
        terrain: String,
        weapon: String,
        npc: Entity.NPC
    ): String = buildString {
        appendLine("Combat Starting:")
        appendLine("Location: $spaceName")
        appendLine("Atmosphere: $terrain")
        appendLine("Player weapon: $weapon")
        appendLine("Enemy: ${npc.name} - ${npc.description}")
        appendLine("Enemy disposition: ${if (npc.isHostile) "Hostile" else "Provoked"}")
        appendLine()
        appendLine("Narrate the moment combat begins in 1-2 sentences. Mention the player's actual weapon (or fists if unarmed).")
    }

    private suspend fun callOrFallback(
        llmClient: LLMClient,
        userContext: String,
        npc: Entity.NPC,
        weapon: String
    ): String = try {
        val response = llmClient.chatCompletion(
            modelId = "gpt-4o-mini",
            systemPrompt = systemPrompt(),
            userContext = userContext,
            maxTokens = 100,
            temperature = 0.8
        )
        response.choices.firstOrNull()?.message?.content?.trim() ?: fallback(npc, weapon)
    } catch (e: Exception) {
        fallback(npc, weapon)
    }

    private fun systemPrompt(): String = """
        You are a dungeon master narrating the start of a combat encounter.
        Create a tense, atmospheric description as combat begins.
        Keep it brief (1-2 sentences) but set the mood.
    """.trimIndent()

    fun fallback(npc: Entity.NPC, weapon: String): String {
        return if (npc.isHostile) {
            if (weapon == "bare fists") {
                "${npc.name} attacks! You raise your fists to defend yourself!"
            } else {
                "${npc.name} attacks! You ready your $weapon!"
            }
        } else {
            if (weapon == "bare fists") {
                "You engage ${npc.name} with your bare hands!"
            } else {
                "You engage ${npc.name} with your $weapon!"
            }
        }
    }
}
