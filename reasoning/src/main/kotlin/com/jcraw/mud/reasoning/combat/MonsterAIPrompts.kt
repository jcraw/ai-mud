@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "NestedBlockDepth",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.core.WorldState

/** Prompt structure for Monster AI LLM (MUD-034k). */
internal data class AIPrompt(val system: String, val user: String)

/**
 * LLM prompt builders for [MonsterAIHandler] (MUD-034k pure-move).
 */
internal object MonsterAIPrompts {

    fun buildPrompt(
        npc: Entity.NPC,
        combatComponent: CombatComponent,
        socialComponent: SocialComponent?,
        intelligenceLevel: Int,
        worldState: WorldState
    ): AIPrompt {
        val spaceName = spaceName(npc, worldState)
        val personality = socialComponent?.personality ?: "ordinary creature"
        val traits = socialComponent?.traits?.joinToString(", ") ?: "none"
        val system = systemForIntelligence(intelligenceLevel, personality)
        val user = userPrompt(npc, combatComponent, personality, traits, spaceName, worldState)
        return AIPrompt(system, user)
    }

    private fun spaceName(npc: Entity.NPC, worldState: WorldState): String {
        val spaceEntry = worldState.spaces.entries.find { (_, space) ->
            space.entities.contains(npc.id)
        }
        return spaceEntry?.value?.name ?: "unknown location"
    }

    private fun systemForIntelligence(intelligenceLevel: Int, personality: String): String =
        when {
            intelligenceLevel <= 20 -> lowIntelligence(personality)
            intelligenceLevel <= 50 -> mediumIntelligence(personality)
            else -> highIntelligence(personality)
        }

    private fun userPrompt(
        npc: Entity.NPC,
        combat: CombatComponent,
        personality: String,
        traits: String,
        spaceName: String,
        worldState: WorldState
    ): String {
        val hpPct = (combat.currentHp.toDouble() / combat.maxHp * 100).toInt()
        val header = """
            You are a ${npc.name} ($personality).
            Traits: $traits
            Your HP: ${combat.currentHp}/${combat.maxHp} ($hpPct%)

            Combat situation:
            - You are in: $spaceName
            - Enemy: ${worldState.player.name}
        """.trimIndent()
        return header + "\n\n" + actionsBlock()
    }

    private fun actionsBlock(): String = """
        Available actions:
        1. Attack - Attack the enemy
        2. Defend - Take defensive stance
        3. UseItem - Use a healing item (if you have one)
        4. Flee - Attempt to escape combat
        5. Wait - Do nothing this turn

        Choose one action and respond ONLY with a JSON object:
        {"action": "Attack", "target": "player", "reasoning": "brief explanation"}

        Valid action values: Attack, Defend, UseItem, Flee, Wait
    """.trimIndent()

    fun lowIntelligence(personality: String): String = """
        You are a $personality with limited intelligence.

        Make an impulsive decision based on:
        - If hurt badly (HP < 30%), try to flee or heal
        - Otherwise, attack aggressively

        Don't overthink it. Act on instinct.
    """.trimIndent()

    fun mediumIntelligence(personality: String): String = """
        You are a $personality with average tactical thinking.

        Consider:
        - Your current HP level
        - Whether you have items to use
        - Basic tactical advantages

        Make a reasonable tactical choice.
    """.trimIndent()

    fun highIntelligence(personality: String): String = """
        You are a $personality with superior strategic intellect.

        Analyze:
        - HP levels and resource management
        - Tactical positioning and options
        - Enemy patterns and likely counter-moves
        - Long-term combat advantage

        Choose the optimal strategic action that maximizes your chance of victory.
        Consider both immediate gains and future positioning.
    """.trimIndent()

    fun calculateTemperature(wisdomLevel: Int): Double = when {
        wisdomLevel <= 20 -> 1.2
        wisdomLevel <= 50 -> 0.7
        else -> 0.3
    }
}
