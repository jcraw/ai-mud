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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class AIDecisionDto(
    val action: String,
    val target: String? = null,
    val reasoning: String = ""
)

/** Parse LLM response into AIDecision (MUD-034k pure-move). */
internal object MonsterAIParse {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseDecision(content: String): AIDecision? = try {
        fromDto(json.decodeFromString(stripMarkdown(content)))
    } catch (e: Exception) {
        println("⚠️ Failed to parse AI decision: ${e.message}")
        null
    }

    private fun stripMarkdown(content: String): String = content
        .trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    private fun fromDto(dto: AIDecisionDto): AIDecision? = when (dto.action.uppercase()) {
        "ATTACK" -> AIDecision.Attack(target = dto.target ?: "player", reasoning = dto.reasoning)
        "DEFEND" -> AIDecision.Defend(reasoning = dto.reasoning)
        "USEITEM" -> AIDecision.UseItem(reasoning = dto.reasoning)
        "FLEE" -> AIDecision.Flee(reasoning = dto.reasoning)
        "WAIT" -> AIDecision.Wait(reasoning = dto.reasoning)
        else -> null
    }
}
