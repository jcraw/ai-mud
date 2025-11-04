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
                // Fallback: check if response contains validation language
                val lowerText = responseText.lowercase()
                val hasValidationLanguage = listOf(
                    "looks", "good", "validated", "correct", "coherent", "pass",
                    "error", "fail", "invalid", "incorrect", "incoherent"
                ).any { lowerText.contains(it) }

                if (!hasValidationLanguage) {
                    // Malformed response - fail conservatively
                    return ValidationResult(
                        pass = false,
                        reason = "Failed to parse validation response: No JSON or validation keywords found",
                        details = mapOf("error" to "malformed_response")
                    )
                }

                // Check for error/fail indicators, but ignore negations
                val hasError = lowerText.contains("error") && !lowerText.contains("no error")
                val hasFail = lowerText.contains("fail") && !lowerText.contains("no fail")
                val pass = !hasError && !hasFail
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
