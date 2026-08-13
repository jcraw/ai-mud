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

package com.jcraw.mud.reasoning.treasureroom

import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.sophia.llm.LLMClient

/**
 * LLM complete + fallback for [TreasureRoomDescriptionGenerator] (MUD-034n).
 */
internal object TreasureRoomComplete {

    suspend fun complete(
        llmClient: LLMClient,
        systemPrompt: String,
        userContext: String,
        treasureRoom: TreasureRoomComponent,
        pedestalInfo: List<TreasureRoomHandler.PedestalInfo>,
        biomeTheme: TreasureRoomDescriptionGenerator.BiomeTheme
    ): String {
        return try {
            val text = requestContent(llmClient, systemPrompt, userContext)
            text ?: TreasureRoomFallbacks.generateFallbackDescription(
                treasureRoom,
                pedestalInfo,
                biomeTheme
            )
        } catch (e: Exception) {
            println("⚠️ Treasure room description generation failed: ${e.message}")
            TreasureRoomFallbacks.generateFallbackDescription(treasureRoom, pedestalInfo, biomeTheme)
        }
    }

    private suspend fun requestContent(
        llmClient: LLMClient,
        systemPrompt: String,
        userContext: String
    ): String? {
        val response = llmClient.chatCompletion(
            modelId = "gpt-4o-mini",
            systemPrompt = systemPrompt,
            userContext = userContext,
            maxTokens = 250,
            temperature = 0.8  // Higher temperature for atmospheric variety
        )
        return response.choices.firstOrNull()?.message?.content?.trim()
    }
}
