@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.mud.client.handlers

/**
 * Action→skill name inference for client skill-use cluster.
 * Pure extract from [ClientSkillQuestHandlers]. Split to keep FN/cyclo under global E.
 */
object ClientSkillQuestSkillInfer {

    fun inferSkillFromAction(action: String): String? {
        val lower = action.lowercase()
        return inferMagic(lower)
            ?: inferRogue(lower)
            ?: inferSocial(lower)
            ?: inferCombat(lower)
            ?: inferCraft(lower)
    }

    private fun inferMagic(lower: String): String? = when {
        lower.contains("fire") || lower.contains("fireball") || lower.contains("burn") -> "Fire Magic"
        lower.contains("water") || lower.contains("ice") || lower.contains("freeze") -> "Water Magic"
        lower.contains("earth") || lower.contains("stone") || lower.contains("rock") -> "Earth Magic"
        lower.contains("air") || lower.contains("wind") || lower.contains("lightning") -> "Air Magic"
        else -> null
    }

    private fun inferRogue(lower: String): String? = when {
        lower.contains("sneak") || lower.contains("hide") || lower.contains("stealth") -> "Stealth"
        lower.contains("pick") && lower.contains("lock") -> "Lockpicking"
        lower.contains("disarm") && lower.contains("trap") -> "Trap Disarm"
        lower.contains("set") && lower.contains("trap") -> "Trap Setting"
        lower.contains("backstab") || lower.contains("sneak attack") -> "Backstab"
        else -> null
    }

    private fun inferSocial(lower: String): String? = when {
        lower.contains("persuade") || lower.contains("negotiate") -> "Diplomacy"
        lower.contains("intimidate") || lower.contains("threaten") -> "Charisma"
        else -> null
    }

    private fun inferCombat(lower: String): String? = when {
        lower.contains("sword") -> "Sword Fighting"
        lower.contains("axe") -> "Axe Mastery"
        lower.contains("bow") || lower.contains("arrow") -> "Bow Accuracy"
        else -> null
    }

    private fun inferCraft(lower: String): String? = when {
        lower.contains("blacksmith") || lower.contains("forge") || lower.contains("craft") -> "Blacksmithing"
        else -> null
    }
}
