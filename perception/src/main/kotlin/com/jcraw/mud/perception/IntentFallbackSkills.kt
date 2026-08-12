@file:Suppress(
    "ReturnCount",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "TooManyFunctions",
)

package com.jcraw.mud.perception

/**
 * Fallback parser arms: check/skills/quests/cast/pick/sneak/train/choose.
 * Pure extract (MUD-034c) — behavior unchanged (incl. dead "pick" arm body).
 */
internal object IntentFallbackSkills {

    fun parseCheck(args: String?): Intent =
        if (args.isNullOrBlank()) Intent.Invalid("Intent.Check what?") else Intent.Check(args)

    fun parseQuests(): Intent = Intent.Quests

    fun parseAccept(args: String?): Intent = Intent.AcceptQuest(args)

    fun parseAbandon(args: String?): Intent =
        if (args.isNullOrBlank()) Intent.Invalid("Abandon which quest?") else Intent.AbandonQuest(args)

    fun parseClaim(args: String?): Intent =
        if (args.isNullOrBlank()) Intent.Invalid("Claim reward for which quest?") else Intent.ClaimReward(args)

    fun parseCast(command: String, args: String?): Intent {
        // Magic skill usage: "cast fireball", "invoke shield"
        return if (args.isNullOrBlank()) {
            Intent.Invalid("Cast what?")
        } else {
            Intent.UseSkill(null, "$command $args")
        }
    }

    /**
     * Lockpicking arm body. Note: command "pick" is also listed under take earlier;
     * this arm is dead for "pick" but live for "lockpick" — order preserved in shell.
     */
    fun parsePick(command: String, args: String?): Intent {
        // Lockpicking: "pick lock", "lockpick door"
        return if (args.isNullOrBlank()) {
            Intent.Invalid("Pick what?")
        } else {
            Intent.UseSkill(null, "$command $args")
        }
    }

    fun parseSneak(command: String, args: String?): Intent {
        // Stealth usage: "sneak past guard", "hide in shadows"
        return Intent.UseSkill(null, if (args.isNullOrBlank()) command else "$command $args")
    }

    fun parseTrain(args: String?): Intent {
        if (args.isNullOrBlank()) {
            return Intent.Invalid("Train what skill?")
        }
        // Parse "train sword fighting with knight" or "practice magic with wizard"
        val parts = args.split(Regex("\\s+with\\s+|\\s+at\\s+"), limit = 2)
        return if (parts.size < 2) {
            Intent.Invalid("Train with whom or how?")
        } else {
            Intent.TrainSkill(parts[0].trim(), parts[1].trim())
        }
    }

    fun parseChoose(args: String?): Intent {
        if (args.isNullOrBlank()) {
            return Intent.Invalid("Choose what?")
        } else if (args.lowercase().contains("perk")) {
            return parsePerkChoice(args)
        } else {
            return Intent.Invalid("Choose what?")
        }
    }

    private fun parsePerkChoice(args: String): Intent {
        // Parse "choose perk 1 for sword fighting" or "select perk 2"
        val perkMatch = Regex("perk\\s+(\\d+)\\s+for\\s+(.+)", RegexOption.IGNORE_CASE).find(args)
            ?: Regex("perk\\s+(\\d+)", RegexOption.IGNORE_CASE).find(args)

        if (perkMatch == null) {
            return Intent.Invalid("Choose which perk (1 or 2)?")
        }
        val choice = perkMatch.groupValues[1].toIntOrNull() ?: 1
        val skillName = perkMatch.groupValues.getOrNull(2)?.trim() ?: ""
        return if (skillName.isEmpty()) {
            Intent.Invalid("Choose perk for which skill?")
        } else {
            Intent.ChoosePerk(skillName, choice)
        }
    }

    fun parseViewSkills(): Intent = Intent.ViewSkills
}
