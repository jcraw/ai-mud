@file:Suppress(
    "ReturnCount",
    "CyclomaticComplexMethod",
)

package com.jcraw.mud.perception

/**
 * Fallback parser arms: take/drop/give/equip/use/drink/treasure/return/inventory.
 * Pure extract (MUD-034c) — behavior unchanged.
 */
internal object IntentFallbackItems {

    fun parseTake(args: String?): Intent {
        if (args.isNullOrBlank()) {
            return Intent.Invalid("Intent.Take what?")
        } else if (args.lowercase() == "all" || args.lowercase() == "everything") {
            return Intent.TakeAll
        } else if (args.startsWith("treasure ", ignoreCase = true)) {
            val itemName = args.substring("treasure ".length).trim()
            return if (itemName.isBlank()) {
                Intent.Invalid("Intent.Take which treasure?")
            } else {
                Intent.TakeTreasure(itemName)
            }
        } else {
            return Intent.Take(args)
        }
    }

    fun parseDrop(args: String?): Intent =
        if (args.isNullOrBlank()) Intent.Invalid("Intent.Drop what?") else Intent.Drop(args)

    fun parseGive(args: String?): Intent {
        if (args.isNullOrBlank()) {
            return Intent.Invalid("Intent.Give what?")
        } else {
            // Parse "give [item] to [npc]" or "give [item] [npc]"
            val parts = args.split(Regex("\\s+to\\s+|\\s+"), limit = 2)
            return if (parts.size < 2) {
                Intent.Invalid("Intent.Give to whom?")
            } else {
                Intent.Give(parts[0].trim(), parts[1].trim())
            }
        }
    }

    fun parseEquip(args: String?): Intent =
        if (args.isNullOrBlank()) Intent.Invalid("Intent.Equip what?") else Intent.Equip(args)

    fun parseUse(args: String?): Intent =
        if (args.isNullOrBlank()) Intent.Invalid("Intent.Use what?") else Intent.Use(args)

    fun parseDrink(args: String?): Intent {
        // "drink from fountain" -> Intent.Interact with fountain
        // "drink potion" -> Intent.Use consumable
        return if (args?.contains("from", ignoreCase = true) == true ||
            args?.contains("fountain", ignoreCase = true) == true) {
            val target = args.replace("from ", "", ignoreCase = true).trim()
            Intent.Interact(target)
        } else {
            if (args.isNullOrBlank()) Intent.Invalid("Drink what?") else Intent.Use(args)
        }
    }

    fun parseTreasure(args: String?): Intent {
        // Handle "treasure" as a command prefix
        return if (args.isNullOrBlank()) {
            Intent.Invalid("What do you want to do with the treasure?")
        } else {
            Intent.Invalid("Try 'take treasure <item>' or 'return treasure <item>'")
        }
    }

    fun parseReturn(args: String?): Intent {
        // Handle return treasure command
        return if (args.isNullOrBlank()) {
            Intent.Invalid("Return what?")
        } else if (args.startsWith("treasure ", ignoreCase = true)) {
            val itemName = args.substring("treasure ".length).trim()
            if (itemName.isBlank()) {
                Intent.Invalid("Return which treasure?")
            } else {
                Intent.ReturnTreasure(itemName)
            }
        } else {
            Intent.ReturnTreasure(args)
        }
    }

    fun parseInventory(): Intent = Intent.Inventory
}
