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
    "TooGenericExceptionCaught"
)

package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.core.WorldState
import com.jcraw.sophia.llm.LLMClient

/** LLM decision attempt for [MonsterAIHandler] (MUD-034k pure-move). */
internal object MonsterAILlm {

    data class Ctx(
        val llmClient: LLMClient?,
        val npc: Entity.NPC,
        val combat: CombatComponent,
        val social: SocialComponent?,
        val intelligence: Int,
        val wisdom: Int,
        val worldState: WorldState
    )

    suspend fun tryDecision(ctx: Ctx): AIDecision? {
        val prompt = MonsterAIPrompts.buildPrompt(
            ctx.npc, ctx.combat, ctx.social, ctx.intelligence, ctx.worldState
        )
        val temperature = MonsterAIPrompts.calculateTemperature(ctx.wisdom)
        return callLlm(ctx.llmClient, prompt, temperature)
    }

    private suspend fun callLlm(
        llmClient: LLMClient?,
        prompt: AIPrompt,
        temperature: Double
    ): AIDecision? = try {
        val response = llmClient?.chatCompletion(
            modelId = "gpt-4o-mini",
            systemPrompt = prompt.system,
            userContext = prompt.user,
            maxTokens = 200,
            temperature = temperature
        ) ?: return null
        val content = response.choices.firstOrNull()?.message?.content?.trim() ?: return null
        MonsterAIParse.parseDecision(content)
    } catch (e: Exception) {
        println("⚠️ Monster AI LLM call failed: ${e.message}")
        null
    }
}
