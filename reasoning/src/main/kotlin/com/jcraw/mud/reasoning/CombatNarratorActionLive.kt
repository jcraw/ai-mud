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

import com.jcraw.mud.memory.MemoryManager

/**
 * Live LLM action narration + cache store for [CombatNarratorAction] (MUD-034k).
 */
internal object CombatNarratorActionLive {

    suspend fun generate(p: ActionNarrateParams): String = try {
        val response = p.llmClient.chatCompletion(
            modelId = "gpt-4o-mini",
            systemPrompt = systemPrompt(),
            userContext = userContext(p),
            maxTokens = 30,
            temperature = 0.8
        )
        val narrative = response.choices.firstOrNull()?.message?.content?.trim()
            ?: CombatNarratorAction.fallback(p.weapon, p.damage, p.isHit, p.isDeath)
        storeInCache(p.memoryManager, p.weapon, p.isHit, p.isCritical, p.isDeath, narrative)
        narrative
    } catch (e: Exception) {
        CombatNarratorAction.fallback(p.weapon, p.damage, p.isHit, p.isDeath)
    }

    private fun systemPrompt(): String = """
        You are a dungeon master narrating combat.
        Create a vivid, brief description (under 15 words) of this combat action.
        Focus on the visceral action, not numbers.
    """.trimIndent()

    private fun userContext(p: ActionNarrateParams): String = when {
        p.isDeath -> "Describe the killing blow with ${p.weapon} against ${p.targetName}."
        p.isCritical -> "Describe a devastating critical hit with ${p.weapon}."
        p.isHit -> "Describe a successful strike with ${p.weapon} for ${p.damage} damage."
        else -> "Describe a missed attack with ${p.weapon}."
    }

    private suspend fun storeInCache(
        memoryManager: MemoryManager?,
        weapon: String,
        isHit: Boolean,
        isCritical: Boolean,
        isDeath: Boolean,
        narrative: String
    ) {
        val scenario = cacheScenario(weapon, isHit, isCritical, isDeath)
        val outcome = when {
            isDeath -> "death"
            isCritical -> "critical"
            isHit -> "hit"
            else -> "miss"
        }
        memoryManager?.remember(
            narrative,
            mapOf(
                "type" to "combat_narration",
                "scenario" to scenario,
                "weapon" to weapon,
                "outcome" to outcome
            )
        )
    }

    private fun cacheScenario(
        weapon: String,
        isHit: Boolean,
        isCritical: Boolean,
        isDeath: Boolean
    ): String = when {
        isDeath -> "death_blow"
        isCritical -> "critical_hit"
        isHit -> if (isRanged(weapon)) "ranged_hit" else "melee_hit"
        else -> if (isRanged(weapon)) "ranged_miss" else "melee_miss"
    }

    private fun isRanged(weapon: String): Boolean =
        weapon.contains("bow") || weapon.contains("arrow")
}
