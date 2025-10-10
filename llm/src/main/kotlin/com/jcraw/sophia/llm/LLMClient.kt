package com.jcraw.sophia.llm

interface LLMClient {
    suspend fun chatCompletion(
        modelId: String,
        systemPrompt: String,
        userContext: String,
        maxTokens: Int = 1000,
        temperature: Double = 0.7
    ): OpenAIResponse

    fun close()
}