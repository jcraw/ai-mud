package com.jcraw.mud.perception

/**
 * Fallback parser arms: save/load/help/quit + unknown.
 * Pure extract (MUD-034c) — behavior unchanged.
 */
internal object IntentFallbackMeta {

    fun parseSave(args: String?): Intent = Intent.Save(args ?: "quicksave")

    fun parseLoad(args: String?): Intent = Intent.Load(args ?: "quicksave")

    fun parseHelp(): Intent = Intent.Help

    fun parseQuit(): Intent = Intent.Quit

    fun parseUnknown(command: String): Intent =
        Intent.Invalid("Unknown command: $command. Type 'help' for available commands.")
}
