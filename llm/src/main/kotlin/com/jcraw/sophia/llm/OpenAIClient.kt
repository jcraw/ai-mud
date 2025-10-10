package com.jcraw.sophia.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class OpenAIClient(private val apiKey: String) : LLMClient {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000L  // 2 minutes
            connectTimeoutMillis = 30_000L   // 30 seconds
            socketTimeoutMillis = 120_000L   // 2 minutes
        }
    }

    override suspend fun chatCompletion(
        modelId: String,
        systemPrompt: String,
        userContext: String,
        maxTokens: Int,
        temperature: Double
    ): OpenAIResponse {
        val actualTemperature = if (modelId.startsWith("gpt-5")) 1.0 else temperature
        println("🌐 OpenAI API call starting...")
        println("   Model: $modelId")
        println("   Max tokens: $maxTokens")
        println("   Temperature: $actualTemperature${if (actualTemperature != temperature) " (forced to 1.0 for GPT-5)" else ""}")
        println("   System prompt: ${systemPrompt.take(100)}...")
        println("   User context: ${userContext.take(100)}...")

        val messages = listOf(
            OpenAIMessage("system", systemPrompt),
            OpenAIMessage("user", userContext)
        )

        val request = OpenAIRequest(
            model = modelId,
            messages = messages,
            maxTokens = maxTokens,
            temperature = actualTemperature
        )

        try {
            val httpResponse = client.post("https://api.openai.com/v1/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            println("📡 HTTP Status: ${httpResponse.status}")

            if (httpResponse.status.value !in 200..299) {
                val errorBody = httpResponse.bodyAsText()
                println("❌ Error Response Body: $errorBody")
                throw RuntimeException("OpenAI API error: ${httpResponse.status} - $errorBody")
            }

            val response = httpResponse.body<OpenAIResponse>()

            println("🔥 LLM API: $modelId | tokens=${response.usage.totalTokens} (${response.usage.promptTokens}+${response.usage.completionTokens})")
            println("✅ OpenAI API call successful, received ${response.choices.size} choices")

            // Debug the response content when empty
            if (response.choices.any { it.message.content.isNullOrBlank() }) {
                response.choices.forEachIndexed { index, choice ->
                    println("   Choice $index content: '${choice.message.content}' (length: ${choice.message.content?.length ?: 0})")
                    println("   Choice $index finish_reason: ${choice.finishReason}")
                }
            }

            return response
        } catch (e: Exception) {
            println("❌ OpenAI API call failed: ${e.message}")
            println("   Exception type: ${e::class.simpleName}")
            e.printStackTrace()
            throw e
        }
    }

    override fun close() {
        client.close()
    }
}