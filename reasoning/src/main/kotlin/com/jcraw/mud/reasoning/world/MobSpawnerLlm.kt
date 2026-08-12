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
    "UnusedParameter",
    "TooGenericExceptionCaught",
    "TooGenericExceptionThrown",
    "SwallowedException",
    "WildcardImport",
    "MayBeConst",
    "ImplicitDefaultLocale",
    "ForbiddenComment",
    "UnusedPrivateProperty",
)

package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.*
import com.jcraw.sophia.llm.LLMClient
import kotlinx.serialization.json.Json

/**
 * LLM-backed entity spawn for [MobSpawner] (MUD-034g pure move).
 */
internal object MobSpawnerLlm {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseMobDataList(content: String): List<MobData> {
        val jsonContent = MobSpawnerLlmPrompts.stripMarkdownCodeBlocks(content)
        return json.decodeFromString(jsonContent)
    }

    suspend fun callLlm(
        llmClient: LLMClient,
        userContext: String
    ): String {
        val response = llmClient.chatCompletion(
            modelId = "gpt-4o-mini",
            systemPrompt = MobSpawnerLlmPrompts.SYSTEM_PROMPT,
            userContext = userContext,
            maxTokens = 2000,
            temperature = 0.8
        )
        return response.choices.firstOrNull()?.message?.content?.trim()
            ?: throw Exception("LLM returned empty response")
    }

    /**
     * Spawn entities using LLM for variety and theme-appropriate content.
     */
    suspend fun spawnEntitiesWithLLM(
        llmClient: LLMClient,
        theme: String,
        count: Int,
        difficulty: Int
    ): List<Entity.NPC> {
        val profile = ThemeRegistry.getProfileSemantic(theme)
            ?: ThemeRegistry.getDefaultProfile()
        val userContext = MobSpawnerLlmPrompts.buildUserContext(
            theme, count, difficulty, profile.mobArchetypes
        )
        return try {
            val content = callLlm(llmClient, userContext)
            parseMobDataList(content).map { mobData ->
                MobSpawnerLlmConvert.mobDataToNpc(mobData, theme, difficulty)
            }
        } catch (e: Exception) {
            MobSpawnerFallback.spawnEntitiesFallback(theme, count, difficulty)
        }
    }
}
