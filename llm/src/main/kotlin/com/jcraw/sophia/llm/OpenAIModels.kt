package com.jcraw.sophia.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class OpenAIMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    @SerialName("max_completion_tokens") val maxTokens: Int = 1000,
    val temperature: Double = 0.7
)

@Serializable
data class OpenAIChoice(
    val message: OpenAIMessage,
    @SerialName("finish_reason") val finishReason: String
)

@Serializable
data class OpenAIUsage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)

@Serializable
data class OpenAIResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage
)

@Serializable
data class OpenAIEmbeddingRequest(
    val model: String,
    val input: String
)

@Serializable
data class OpenAIEmbeddingData(
    val embedding: List<Double>,
    val index: Int
)

@Serializable
data class OpenAIEmbeddingResponse(
    val `object`: String,
    val data: List<OpenAIEmbeddingData>,
    val model: String,
    val usage: OpenAIUsage
)