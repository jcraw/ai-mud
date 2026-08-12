@file:Suppress(
    "MaxLineLength",
    "MagicNumber",
)

package com.jcraw.mud.perception

/**
 * LLM JSON map: save/load/help/quit/invalid.
 * Pure extract (MUD-034c) — keys and outcomes unchanged.
 */
internal object IntentLlmJsonMapMeta {

    fun mapMeta(
        intentType: String,
        target: String?,
        originalInput: String
    ): Intent? {
        return when (intentType) {
            "save" -> Intent.Save(target ?: "quicksave")
            "load" -> Intent.Load(target ?: "quicksave")
            "help" -> Intent.Help
            "quit" -> Intent.Quit
            "invalid" -> Intent.Invalid("Unknown command: ${originalInput.take(50)}. Type 'help' for available commands.")
            else -> null
        }
    }
}
