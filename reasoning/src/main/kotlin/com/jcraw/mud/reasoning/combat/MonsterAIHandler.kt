package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import com.jcraw.sophia.llm.LLMClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Handles AI decision-making for NPCs in combat
 * Decision quality is modulated by intelligence and wisdom skills
 *
 * Design principles:
 * - Intelligence determines prompt complexity and tactical depth
 * - Wisdom determines decision consistency (via temperature)
 * - Personality influences action preferences
 * - Fallback rules ensure robustness if LLM fails
 */
class MonsterAIHandler(
    private val llmClient: LLMClient?
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Decide what action the NPC should take
     *
     * @param npcId ID of the NPC making the decision
     * @param worldState Current world state
     * @return AIDecision with chosen action
     */
    suspend fun decideAction(
        npcId: String,
        worldState: WorldState
    ): AIDecision {
        // Find the NPC entity
        val npc = findNPC(npcId, worldState)
            ?: return AIDecision.Error("NPC not found: $npcId")

        // Get components
        val skillComponent = npc.getComponent<SkillComponent>(ComponentType.SKILL)
        val combatComponent = npc.getComponent<CombatComponent>(ComponentType.COMBAT)
        val socialComponent = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)

        // If no combat component, NPC can't fight
        if (combatComponent == null) {
            return AIDecision.Error("NPC has no combat component")
        }

        // Get intelligence and wisdom levels (default to 0 if no skill component)
        val intelligenceLevel = skillComponent?.getEffectiveLevel("Intelligence") ?: 0
        val wisdomLevel = skillComponent?.getEffectiveLevel("Wisdom") ?: 0

        // Try LLM decision if available
        val decision = if (llmClient != null) {
            tryLLMDecision(
                npc = npc,
                combatComponent = combatComponent,
                socialComponent = socialComponent,
                intelligenceLevel = intelligenceLevel,
                wisdomLevel = wisdomLevel,
                worldState = worldState
            ) ?: fallbackDecision(
                npc = npc,
                combatComponent = combatComponent,
                socialComponent = socialComponent,
                worldState = worldState
            )
        } else {
            // Fallback to rule-based decision
            fallbackDecision(
                npc = npc,
                combatComponent = combatComponent,
                socialComponent = socialComponent,
                worldState = worldState
            )
        }

        // Apply personality modifications to decision
        return PersonalityAI.modifyDecision(decision, socialComponent, combatComponent)
    }

    /**
     * Use LLM to make tactical decision
     * Prompt complexity and temperature are modulated by int/wis
     */
    private suspend fun tryLLMDecision(
        npc: Entity.NPC,
        combatComponent: CombatComponent,
        socialComponent: SocialComponent?,
        intelligenceLevel: Int,
        wisdomLevel: Int,
        worldState: WorldState
    ): AIDecision? {
        val prompt = buildPrompt(
            npc = npc,
            combatComponent = combatComponent,
            socialComponent = socialComponent,
            intelligenceLevel = intelligenceLevel,
            worldState = worldState
        )

        val temperature = calculateTemperature(wisdomLevel)

        return try {
            val response = llmClient?.chatCompletion(
                modelId = "gpt-4o-mini",
                systemPrompt = prompt.system,
                userContext = prompt.user,
                maxTokens = 200,
                temperature = temperature
            ) ?: return null

            val content = response.choices.firstOrNull()?.message?.content?.trim()
                ?: return null

            parseDecision(content)
        } catch (e: Exception) {
            println("⚠️ Monster AI LLM call failed: ${e.message}")
            null
        }
    }

    /**
     * Build LLM prompt with complexity based on intelligence
     *
     * Low intelligence (0-20): Simple impulsive decisions
     * Medium intelligence (21-50): Tactical considerations
     * High intelligence (51+): Strategic optimization
     */
    private fun buildPrompt(
        npc: Entity.NPC,
        combatComponent: CombatComponent,
        socialComponent: SocialComponent?,
        intelligenceLevel: Int,
        worldState: WorldState
    ): AIPrompt {
        // Find space containing the NPC (V3)
        val spaceEntry = worldState.spaces.entries.find { (_, space) ->
            space.entities.contains(npc.id)
        }
        val spaceName = spaceEntry?.value?.name ?: "unknown location"

        val player = worldState.player
        val hpPercentage = (combatComponent.currentHp.toDouble() / combatComponent.maxHp * 100).toInt()
        val personality = socialComponent?.personality ?: "ordinary creature"
        val traits = socialComponent?.traits?.joinToString(", ") ?: "none"

        val systemPrompt = when {
            intelligenceLevel <= 20 -> buildLowIntelligencePrompt(personality)
            intelligenceLevel <= 50 -> buildMediumIntelligencePrompt(personality)
            else -> buildHighIntelligencePrompt(personality)
        }

        val userPrompt = """
            You are a ${npc.name} ($personality).
            Traits: $traits
            Your HP: ${combatComponent.currentHp}/${combatComponent.maxHp} ($hpPercentage%)

            Combat situation:
            - You are in: $spaceName
            - Enemy: ${player.name}

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

        return AIPrompt(systemPrompt, userPrompt)
    }

    private fun buildLowIntelligencePrompt(personality: String): String = """
        You are a $personality with limited intelligence.

        Make an impulsive decision based on:
        - If hurt badly (HP < 30%), try to flee or heal
        - Otherwise, attack aggressively

        Don't overthink it. Act on instinct.
    """.trimIndent()

    private fun buildMediumIntelligencePrompt(personality: String): String = """
        You are a $personality with average tactical thinking.

        Consider:
        - Your current HP level
        - Whether you have items to use
        - Basic tactical advantages

        Make a reasonable tactical choice.
    """.trimIndent()

    private fun buildHighIntelligencePrompt(personality: String): String = """
        You are a $personality with superior strategic intellect.

        Analyze:
        - HP levels and resource management
        - Tactical positioning and options
        - Enemy patterns and likely counter-moves
        - Long-term combat advantage

        Choose the optimal strategic action that maximizes your chance of victory.
        Consider both immediate gains and future positioning.
    """.trimIndent()

    /**
     * Calculate temperature based on wisdom
     *
     * Low wisdom (0-20): temp=1.2 (erratic, unpredictable)
     * Medium wisdom (21-50): temp=0.7 (balanced)
     * High wisdom (51+): temp=0.3 (consistent, reliable)
     */
    private fun calculateTemperature(wisdomLevel: Int): Double = when {
        wisdomLevel <= 20 -> 1.2
        wisdomLevel <= 50 -> 0.7
        else -> 0.3
    }

    /**
     * Parse LLM response into AIDecision
     */
    private fun parseDecision(content: String): AIDecision? {
        return try {
            // Extract JSON from response (handle markdown code blocks)
            val jsonContent = content
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val dto = json.decodeFromString<AIDecisionDto>(jsonContent)

            when (dto.action.uppercase()) {
                "ATTACK" -> AIDecision.Attack(
                    target = dto.target ?: "player",
                    reasoning = dto.reasoning
                )
                "DEFEND" -> AIDecision.Defend(reasoning = dto.reasoning)
                "USEITEM" -> AIDecision.UseItem(reasoning = dto.reasoning)
                "FLEE" -> AIDecision.Flee(reasoning = dto.reasoning)
                "WAIT" -> AIDecision.Wait(reasoning = dto.reasoning)
                else -> null
            }
        } catch (e: Exception) {
            println("⚠️ Failed to parse AI decision: ${e.message}")
            null
        }
    }

    /**
     * Fallback rule-based decision when LLM is unavailable or fails
     */
    private fun fallbackDecision(
        npc: Entity.NPC,
        combatComponent: CombatComponent,
        socialComponent: SocialComponent?,
        worldState: WorldState
    ): AIDecision {
        val hpPercentage = combatComponent.currentHp.toDouble() / combatComponent.maxHp
        val isCowardly = socialComponent?.traits?.contains("cowardly") == true

        return when {
            // Flee if HP critical or cowardly at 50% HP
            hpPercentage < 0.3 -> AIDecision.Flee("HP critical")
            hpPercentage < 0.5 && isCowardly -> AIDecision.Flee("Cowardly nature")

            // Try to use healing item if HP < 70%
            hpPercentage < 0.7 -> AIDecision.UseItem("Need healing")

            // Attack if HP good
            hpPercentage > 0.7 -> AIDecision.Attack(
                target = worldState.player.id,
                reasoning = "Healthy, aggressive stance"
            )

            // Default: Attack
            else -> AIDecision.Attack(
                target = worldState.player.id,
                reasoning = "Default attack"
            )
        }
    }

    /**
     * Find NPC entity by ID (V3: use global entity storage)
     */
    private fun findNPC(npcId: String, worldState: WorldState): Entity.NPC? {
        return worldState.getEntity(npcId) as? Entity.NPC
    }
}

/**
 * AI decision types that NPCs can make
 */
sealed class AIDecision {
    abstract val reasoning: String

    data class Attack(
        val target: String,
        override val reasoning: String
    ) : AIDecision()

    data class Defend(
        override val reasoning: String
    ) : AIDecision()

    data class UseItem(
        override val reasoning: String
    ) : AIDecision()

    data class Flee(
        override val reasoning: String
    ) : AIDecision()

    data class Wait(
        override val reasoning: String
    ) : AIDecision()

    data class Error(
        override val reasoning: String
    ) : AIDecision()
}

/**
 * Prompt structure for LLM
 */
private data class AIPrompt(
    val system: String,
    val user: String
)

/**
 * DTO for JSON deserialization of LLM response
 */
@Serializable
private data class AIDecisionDto(
    val action: String,
    val target: String? = null,
    val reasoning: String = ""
)
