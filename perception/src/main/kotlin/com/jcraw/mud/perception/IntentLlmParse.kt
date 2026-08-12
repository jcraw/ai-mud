@file:Suppress(
    "ReturnCount",
    "CyclomaticComplexMethod",
    "LongMethod",
    "TooGenericExceptionCaught",
)

package com.jcraw.mud.perception

import com.jcraw.mud.core.Direction
import com.jcraw.sophia.llm.LLMClient

/**
 * LLM chat orchestration + JSON field extract + domain dispatch for [IntentRecognizer].
 * Pure extract (MUD-034c) — model/params/keys/when-order unchanged.
 */
internal object IntentLlmParse {

    // Manual JSON field patterns (verbatim from pre-split IntentRecognizer)
    private val INTENT_TYPE_RE = Regex(""""intent"\s*:\s*"([^"]+)"""")
    private val TARGET_RE = Regex(""""target"\s*:\s*(?:"([^"]*)"|null)""")
    private val NPC_TARGET_RE = Regex(""""npc_target"\s*:\s*(?:"([^"]*)"|null)""")
    private val SKILL_NAME_RE = Regex(""""skill_name"\s*:\s*(?:"([^"]*)"|null)""")
    private val ACTION_RE = Regex(""""action"\s*:\s*"([^"]+)"""")
    private val MERCHANT_RE = Regex(""""merchant_target"\s*:\s*(?:"([^"]*)"|null)""")
    private val QUANTITY_RE = Regex(""""quantity"\s*:\s*(-?\d+)""")
    private val PERK_RE = Regex(""""perk_choice"\s*:\s*(\d+)""")

    suspend fun parseLLM(
        llmClient: LLMClient,
        input: String,
        roomContext: String?,
        exitsWithNames: Map<Direction, String>?
    ): Intent {
        val systemPrompt = IntentLlmPromptBuild.buildSystemPrompt()
        val userPrompt = IntentLlmPromptBuild.buildUserPrompt(input, roomContext, exitsWithNames)

        val response = llmClient.chatCompletion(
            modelId = "gpt-4o-mini",  // Cost-effective model for parsing
            systemPrompt = systemPrompt,
            userContext = userPrompt,
            maxTokens = 500,
            temperature = 0.0  // Low temperature for consistent parsing
        )

        val responseText = response.choices.firstOrNull()?.message?.content ?: ""
        return parseIntentFromResponse(responseText, input)
    }

    fun parseIntentFromResponse(responseText: String, originalInput: String): Intent {
        try {
            val jsonText = extractJsonObject(responseText)
                ?: return Intent.Invalid("Could not parse response")
            val fields = extractJsonFields(jsonText)
                ?: return Intent.Invalid("Unknown command")
            return dispatchIntent(fields, originalInput)
        } catch (e: Exception) {
            return Intent.Invalid("Failed to parse command: ${e.message}")
        }
    }

    private fun extractJsonObject(responseText: String): String? {
        val jsonStart = responseText.indexOf('{')
        val jsonEnd = responseText.lastIndexOf('}')
        if (jsonStart == -1 || jsonEnd == -1) return null
        return responseText.substring(jsonStart, jsonEnd + 1)
    }

    private data class JsonFields(
        val intentType: String,
        val target: String?,
        val npcTarget: String?,
        val skillName: String?,
        val tradeAction: String?,
        val merchantTarget: String?,
        val quantity: Int?,
        val perkChoice: Int?
    )

    private fun extractJsonFields(jsonText: String): JsonFields? {
        val intentType = matchString(jsonText, INTENT_TYPE_RE) ?: return null
        return JsonFields(
            intentType = intentType,
            target = matchOptionalString(jsonText, TARGET_RE),
            npcTarget = matchOptionalString(jsonText, NPC_TARGET_RE),
            skillName = matchOptionalString(jsonText, SKILL_NAME_RE),
            tradeAction = matchString(jsonText, ACTION_RE),
            merchantTarget = matchOptionalString(jsonText, MERCHANT_RE),
            quantity = matchInt(jsonText, QUANTITY_RE),
            perkChoice = matchInt(jsonText, PERK_RE)
        )
    }

    private fun matchString(jsonText: String, re: Regex): String? =
        re.find(jsonText)?.groupValues?.get(1)

    private fun matchOptionalString(jsonText: String, re: Regex): String? =
        re.find(jsonText)?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }

    private fun matchInt(jsonText: String, re: Regex): Int? =
        re.find(jsonText)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun dispatchIntent(fields: JsonFields, originalInput: String): Intent {
        val lower = fields.intentType.lowercase()
        return IntentLlmJsonMapNav.mapNav(lower, fields.target)
            ?: IntentLlmJsonMapItems.mapItems(
                lower, fields.target, fields.npcTarget,
                fields.tradeAction, fields.merchantTarget, fields.quantity
            )
            ?: IntentLlmJsonMapSocial.mapSocial(lower, fields.target, fields.npcTarget)
            ?: IntentLlmJsonMapSkills.mapSkills(
                lower, fields.target, fields.npcTarget, fields.skillName, fields.perkChoice
            )
            ?: IntentLlmJsonMapMeta.mapMeta(lower, fields.target, originalInput)
            ?: Intent.Invalid("Unknown command: ${fields.intentType}. Type 'help' for available commands.")
    }
}
