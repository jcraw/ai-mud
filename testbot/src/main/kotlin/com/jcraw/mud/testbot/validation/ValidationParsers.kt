package com.jcraw.mud.testbot.validation

import com.jcraw.mud.testbot.ValidationResult
import kotlinx.serialization.json.Json

/**
 * Parse LLM responses and extract validation results.
 */
object ValidationParsers {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseValidation(responseText: String): ValidationResult {
        return try {
            // Try to extract JSON from response
            val jsonStart = responseText.indexOf('{')
            val jsonEnd = responseText.lastIndexOf('}') + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonText = responseText.substring(jsonStart, jsonEnd)
                json.decodeFromString<ValidationResult>(jsonText)
            } else {
                // Fallback: assume pass if no errors mentioned
                val pass = !responseText.contains("error", ignoreCase = true) &&
                        !responseText.contains("fail", ignoreCase = true)
                ValidationResult(
                    pass = pass,
                    reason = "Fallback validation: $responseText",
                    details = emptyMap()
                )
            }
        } catch (e: Exception) {
            // On parse error, be conservative and fail
            ValidationResult(
                pass = false,
                reason = "Failed to parse validation response: ${e.message}",
                details = mapOf("error" to "parse_failure")
            )
        }
    }
}
