@file:Suppress(
    "ReturnCount",
    "CyclomaticComplexMethod",
    "TooManyFunctions",
    "UnusedParameter",
)

package com.jcraw.mud.perception

/**
 * Fallback parser shell: tokenize + ordered domain dispatch.
 * Pure extract (MUD-034c) — first-match order preserved (incl. dead "pick" arm).
 */
internal object IntentFallbackParse {

    /**
     * Fallback parser using simple pattern matching when LLM is not available.
     * This is a simplified version that handles basic commands.
     */
    fun parseFallback(input: String): Intent {
        val parts = input.lowercase().trim().split(Regex("\\s+"), limit = 2)
        val command = parts[0]
        val args = parts.getOrNull(1)

        // Domain order matches original when (command) arm order
        return tryNav(command, args)
            ?: tryEarlyItems(command, args)
            ?: trySocialCore(command, args)
            ?: tryMidItems(command, args)
            ?: trySocialChecks(command, args)
            ?: tryMetaEarly(command, args)
            ?: tryQuestsEmotes(command, args)
            ?: trySkills(command, args)
            ?: tryLateItems(command, args)
            ?: tryTrade(command, args)
            ?: tryMetaLate(command, args)
            ?: IntentFallbackMeta.parseUnknown(command)
    }

    private fun tryNav(command: String, args: String?): Intent? = when (command) {
        "go", "move", "n", "s", "e", "w", "north", "south", "east", "west",
        "ne", "nw", "se", "sw", "northeast", "northwest", "southeast", "southwest",
        "u", "d", "up", "down" -> IntentFallbackNav.parseMoveCommand(command, args)
        "look", "l", "examine", "inspect" -> IntentFallbackNav.parseLook(args)
        "search" -> IntentFallbackNav.parseSearch(args)
        "interact" -> IntentFallbackNav.parseInteract(args)
        else -> null
    }

    private fun tryEarlyItems(command: String, args: String?): Intent? = when (command) {
        "take", "get", "pickup", "pick" -> IntentFallbackItems.parseTake(args)
        "drop", "put" -> IntentFallbackItems.parseDrop(args)
        "give", "deliver" -> IntentFallbackItems.parseGive(args)
        else -> null
    }

    private fun trySocialCore(command: String, args: String?): Intent? = when (command) {
        "say", "tell" -> IntentFallbackSocial.parseSay(args)
        "talk", "speak", "chat" -> IntentFallbackSocial.parseTalk(args)
        "attack", "kill", "fight", "hit" -> IntentFallbackSocial.parseAttack(args)
        else -> null
    }

    private fun tryMidItems(command: String, args: String?): Intent? = when (command) {
        "equip", "wield", "wear" -> IntentFallbackItems.parseEquip(args)
        "use", "consume", "eat" -> IntentFallbackItems.parseUse(args)
        "drink" -> IntentFallbackItems.parseDrink(args)
        else -> null
    }

    private fun trySocialChecks(command: String, args: String?): Intent? = when (command) {
        "check", "test", "attempt", "try" -> IntentFallbackSkills.parseCheck(args)
        "persuade", "convince" -> IntentFallbackSocial.parsePersuade(args)
        "intimidate", "threaten" -> IntentFallbackSocial.parseIntimidate(args)
        else -> null
    }

    private fun tryMetaEarly(command: String, args: String?): Intent? = when (command) {
        "save" -> IntentFallbackMeta.parseSave(args)
        "load" -> IntentFallbackMeta.parseLoad(args)
        else -> null
    }

    private fun tryQuestsEmotes(command: String, args: String?): Intent? = when (command) {
        "quests", "quest", "journal", "j" -> IntentFallbackSkills.parseQuests()
        "accept" -> IntentFallbackSkills.parseAccept(args)
        "abandon" -> IntentFallbackSkills.parseAbandon(args)
        "claim" -> IntentFallbackSkills.parseClaim(args)
        "smile", "wave", "nod", "shrug", "laugh", "cry", "bow" ->
            IntentFallbackSocial.parseEmoteKeyword(command, args)
        "emote" -> IntentFallbackSocial.parseEmoteCommand(args)
        "ask" -> IntentFallbackSocial.parseAsk(args)
        else -> null
    }

    private fun trySkills(command: String, args: String?): Intent? = when (command) {
        "cast", "invoke", "channel" -> IntentFallbackSkills.parseCast(command, args)
        // Dead for "pick" (already matched under take); live for "lockpick"
        "pick", "lockpick" -> IntentFallbackSkills.parsePick(command, args)
        "sneak", "stealth", "hide" -> IntentFallbackSkills.parseSneak(command, args)
        "train", "practice" -> IntentFallbackSkills.parseTrain(args)
        "choose", "select" -> IntentFallbackSkills.parseChoose(args)
        else -> null
    }

    private fun tryLateItems(command: String, args: String?): Intent? = when (command) {
        "treasure" -> IntentFallbackItems.parseTreasure(args)
        "return", "putback", "replace" -> IntentFallbackItems.parseReturn(args)
        "skills", "abilities", "sheet" -> IntentFallbackSkills.parseViewSkills()
        "inventory", "i", "equipment", "gear", "eq" -> IntentFallbackItems.parseInventory()
        else -> null
    }

    private fun tryTrade(command: String, args: String?): Intent? = when (command) {
        "buy", "purchase" -> IntentTradeParse.parseTradeCommand(
            args, action = "buy", missingItemMessage = "Buy what?",
            merchantPrepositions = listOf("from", "with")
        )
        "sell" -> IntentTradeParse.parseTradeCommand(
            args, action = "sell", missingItemMessage = "Sell what?",
            merchantPrepositions = listOf("to", "with")
        )
        "list" -> IntentTradeParse.parseListStock(args)
        "show" -> {
            if (args != null && args.contains("stock", ignoreCase = true)) {
                IntentTradeParse.parseListStock(args)
            } else {
                Intent.Invalid("Show what?")
            }
        }
        else -> null
    }

    private fun tryMetaLate(command: String, @Suppress("UNUSED_PARAMETER") args: String?): Intent? = when (command) {
        "help", "h", "?" -> IntentFallbackMeta.parseHelp()
        "quit", "exit", "q" -> IntentFallbackMeta.parseQuit()
        else -> null
    }
}
