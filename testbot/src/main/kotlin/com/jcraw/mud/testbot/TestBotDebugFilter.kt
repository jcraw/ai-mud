@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

/**
 * Filters debug lines from game responses for bot/validator context (MUD-034f).
 */
internal object TestBotDebugFilter {

    fun filterDebugOutput(text: String): String {
        return text.lines()
            .filterNot { line ->
                val trimmed = line.trim()
                // Filter out debug lines
                trimmed.startsWith("[") ||
                    trimmed.startsWith("💾") ||
                    trimmed.startsWith("⚠️") ||
                    trimmed.contains("DEBUG]") ||
                    trimmed.contains("Warning: Failed to")
            }
            .joinToString("\n")
            .trim()
    }
}
