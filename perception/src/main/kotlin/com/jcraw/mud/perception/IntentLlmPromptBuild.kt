@file:Suppress(
    "MaxLineLength",
)

package com.jcraw.mud.perception

import com.jcraw.mud.core.Direction

/**
 * LLM prompt builders for [IntentRecognizer].
 * Pure extract (MUD-034c) — concat-only system prompt; user prompt unchanged.
 */
internal object IntentLlmPromptBuild {

    fun buildSystemPrompt(): String {
        return (
            IntentLlmPromptFragments.SYSTEM_PROMPT_PART_0 +
            IntentLlmPromptFragments.SYSTEM_PROMPT_PART_1 +
            IntentLlmPromptFragments.SYSTEM_PROMPT_PART_2 +
            IntentLlmPromptFragments.SYSTEM_PROMPT_PART_3 +
            IntentLlmPromptFragments.SYSTEM_PROMPT_PART_4 +
            IntentLlmPromptFragments.SYSTEM_PROMPT_PART_5 +
            IntentLlmPromptFragments.SYSTEM_PROMPT_PART_6 +
            IntentLlmPromptFragments.SYSTEM_PROMPT_PART_7 +
            IntentLlmPromptFragments.SYSTEM_PROMPT_PART_8 +
            IntentLlmPromptFragments.SYSTEM_PROMPT_PART_9 +
            IntentLlmPromptFragments.SYSTEM_PROMPT_PART_10
        ).trimIndent()
    }

    fun buildUserPrompt(
        input: String,
        roomContext: String?,
        exitsWithNames: Map<Direction, String>?
    ): String {
        val parts = mutableListOf<String>()

        if (roomContext != null) {
            parts.add("Current room context: $roomContext")
        }

        if (!exitsWithNames.isNullOrEmpty()) {
            val exitsText = exitsWithNames.entries.joinToString("\n") { (dir, name) ->
                "  ${dir.displayName} -> $name"
            }
            parts.add("Available exits:\n$exitsText")
        }

        parts.add("Player input: \"$input\"")
        parts.add("Parse the player's intent.")

        return parts.joinToString("\n\n")
    }
}
