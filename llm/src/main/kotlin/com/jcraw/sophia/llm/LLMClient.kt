package com.jcraw.sophia.llm

interface LLMClient {
    suspend fun chatCompletion(
        modelId: String,
        systemPrompt: String,
        userContext: String,
        maxTokens: Int = 1000,
        temperature: Double = 0.7
    ): OpenAIResponse

    suspend fun createEmbedding(
        text: String,
        model: String = "text-embedding-3-small"
    ): List<Double>

    fun close()
}