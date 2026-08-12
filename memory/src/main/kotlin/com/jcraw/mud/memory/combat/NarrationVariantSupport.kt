@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter"
)

package com.jcraw.mud.memory.combat

import com.jcraw.mud.memory.MemoryManager
import com.jcraw.sophia.llm.LLMClient

/**
 * Single-narrative + fallback for [NarrationVariantGenerator] (MUD-034m).
 */
internal object NarrationVariantSupport {

    suspend fun rememberVariant(
        llmClient: LLMClient,
        memoryManager: MemoryManager,
        scenario: String,
        weapon: String,
        damageTier: String,
        variantNumber: Int,
        tags: Map<String, String>
    ) {
        val narrative = generateSingleNarrative(llmClient, scenario, weapon, damageTier, variantNumber)
        memoryManager.remember(narrative, tags)
    }

    /**
     * Generates a single narration variant using the LLM.
     */
    suspend fun generateSingleNarrative(
        llmClient: LLMClient,
        scenario: String,
        weapon: String,
        damageTier: String,
        variantNumber: Int
    ): String {
        val systemPrompt = NarrationPromptBuilder.systemPrompt()
        val userContext = NarrationPromptBuilder.userContext(scenario, weapon, damageTier, variantNumber)
        return NarrationChatCall.complete(llmClient, systemPrompt, userContext, scenario, weapon)
    }

    /**
     * Provides fallback narratives if LLM fails.
     */
    fun getFallbackNarrative(scenario: String, weapon: String): String {
        return when (scenario) {
            "melee hit" -> "Your $weapon strikes true!"
            "melee miss" -> "Your $weapon swing goes wide!"
            "ranged hit" -> "Your $weapon finds its mark!"
            "ranged miss" -> "Your $weapon misses the target!"
            "spell cast" -> "Your $weapon erupts with power!"
            "critical hit" -> "A devastating blow with your $weapon!"
            "status effect" -> "The $weapon effect takes hold!"
            "death blow" -> "Your $weapon delivers the killing blow!"
            else -> "The attack continues."
        }
    }
}

/**
 * Prompt strings for [NarrationVariantSupport] (FN split vs chat).
 */
internal object NarrationPromptBuilder {

    fun systemPrompt(): String = """
            You are a dungeon master creating vivid, atmospheric combat descriptions.
            Generate a SINGLE SHORT sentence (under 15 words) describing the combat action.
            Be evocative and varied in your descriptions.
            Focus on visceral details - the clash, the impact, the movement.
            DO NOT include damage numbers or outcomes, just describe the action itself.
    """.trimIndent()

    fun userContext(
        scenario: String,
        weapon: String,
        damageTier: String,
        variantNumber: Int
    ): String = buildString {
        appendLine("Scenario: $scenario")
        appendLine("Weapon/Method: $weapon")
        appendLine("Damage tier: $damageTier")
        appendLine("Variant: #$variantNumber (make this DIFFERENT from other variants)")
        appendLine()
        when (scenario) {
            "melee hit" -> appendLine("Describe a successful melee attack with the $weapon. Focus on the strike connecting.")
            "melee miss" -> appendLine("Describe a missed melee attack with the $weapon. Show the enemy dodging or parrying.")
            "ranged hit" -> appendLine("Describe a successful ranged attack with the $weapon. Show the projectile striking true.")
            "ranged miss" -> appendLine("Describe a missed ranged attack with the $weapon. Show the projectile missing.")
            "spell cast" -> appendLine("Describe casting and landing the $weapon. Show magical energy manifesting.")
            "critical hit" -> appendLine("Describe a devastating critical hit with the $weapon. Extra dramatic!")
            "status effect" -> appendLine("Describe the $weapon effect taking hold of the target.")
            "death blow" -> appendLine("Describe the final, killing strike with the $weapon.")
        }
    }
}

/**
 * LLM chat call for [NarrationVariantSupport] (FN split vs prompt).
 */
internal object NarrationChatCall {

    suspend fun complete(
        llmClient: LLMClient,
        systemPrompt: String,
        userContext: String,
        scenario: String,
        weapon: String
    ): String {
        return try {
            val response = llmClient.chatCompletion(
                modelId = "gpt-4o-mini",
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = 30,
                temperature = 1.0 // High variance for diversity
            )
            response.choices.firstOrNull()?.message?.content?.trim()
                ?: NarrationVariantSupport.getFallbackNarrative(scenario, weapon)
        } catch (e: Exception) {
            NarrationVariantSupport.getFallbackNarrative(scenario, weapon)
        }
    }
}
