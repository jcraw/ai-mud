@file:Suppress(
    "ReturnCount",
    "CyclomaticComplexMethod",
)

package com.jcraw.mud.perception

/**
 * Fallback parser arms: say/talk/attack/emote/ask/persuade/intimidate.
 * Pure extract (MUD-034c) — behavior unchanged.
 */
internal object IntentFallbackSocial {

    fun parseSay(args: String?): Intent = IntentSayParse.parseSay(args)

    fun parseTalk(args: String?): Intent =
        if (args.isNullOrBlank()) Intent.Invalid("Intent.Talk to whom?") else Intent.Talk(args)

    fun parseAttack(args: String?): Intent = Intent.Attack(args)

    fun parsePersuade(args: String?): Intent =
        if (args.isNullOrBlank()) Intent.Invalid("Intent.Persuade whom?") else Intent.Persuade(args)

    fun parseIntimidate(args: String?): Intent =
        if (args.isNullOrBlank()) Intent.Invalid("Intent.Intimidate whom?") else Intent.Intimidate(args)

    fun parseEmoteKeyword(command: String, args: String?): Intent {
        // Parse "smile at guard" or just "smile"
        val emoteType = command
        return if (args.isNullOrBlank()) {
            Intent.Emote(emoteType, null)
        } else {
            // Remove "at" if present
            val target = args.removePrefix("at ").trim()
            Intent.Emote(emoteType, target)
        }
    }

    fun parseEmoteCommand(args: String?): Intent {
        if (args.isNullOrBlank()) {
            return Intent.Invalid("What emotion do you want to express?")
        } else {
            // Parse "emote smile at guard" or "emote smile"
            val parts = args.split(Regex("\\s+at\\s+|\\s+"), limit = 2)
            val emoteType = parts[0].trim()
            val target = parts.getOrNull(1)?.trim()
            return Intent.Emote(emoteType, target)
        }
    }

    fun parseAsk(args: String?): Intent {
        if (args.isNullOrBlank()) {
            return Intent.Invalid("Ask whom?")
        } else {
            // Parse "ask guard about castle" or "ask guard castle"
            val parts = args.split(Regex("\\s+about\\s+|\\s+"), limit = 2)
            return if (parts.size < 2) {
                Intent.Invalid("Ask about what?")
            } else {
                Intent.AskQuestion(parts[0].trim(), parts[1].trim())
            }
        }
    }
}
