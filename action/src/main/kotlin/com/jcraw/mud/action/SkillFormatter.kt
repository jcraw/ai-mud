package com.jcraw.mud.action

import com.jcraw.mud.core.Perk
import com.jcraw.mud.core.PerkType
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SkillState

/**
 * Formats skill-related output for player display
 * Handles skill sheets, level-up messages, and perk choice prompts
 */
object SkillFormatter {

    /**
     * Format complete skill sheet showing all skills, levels, XP, and perks
     *
     * Example output:
     * ```
     * === SKILLS ===
     *
     * Combat Skills:
     *   Sword Fighting [Level 15] (2,350 / 25,600 XP)
     *     Perks: Quick Strike, +15% Damage
     *   Shield Use [Level 8] (1,200 / 7,200 XP)
     *
     * Magic Skills:
     *   Fire Magic [Level 12] (5,000 / 16,900 XP)
     *     Perks: Fireball Volley
     * ```
     */
    fun formatSkillSheet(component: SkillComponent): String {
        val unlockedSkills = component.getUnlockedSkills()

        if (unlockedSkills.isEmpty()) {
            return "You have no unlocked skills yet. Use skills to unlock them!"
        }

        val sb = StringBuilder()
        sb.appendLine("=== SKILLS ===")
        sb.appendLine()

        // Group skills by primary tag
        val skillGroups = unlockedSkills.entries.groupBy { (_, skillState) ->
            skillState.tags.firstOrNull() ?: "other"
        }

        // Sort groups: stats first, then alphabetically
        val sortedGroups = skillGroups.entries.sortedBy { (tag, _) ->
            when (tag) {
                "stat" -> "0_stat"
                "combat" -> "1_combat"
                "magic" -> "2_magic"
                "rogue" -> "3_rogue"
                "resource" -> "4_resource"
                else -> "5_$tag"
            }
        }

        for ((tag, skills) in sortedGroups) {
            val groupName = when (tag) {
                "stat" -> "Core Stats"
                "combat" -> "Combat Skills"
                "magic" -> "Magic Skills"
                "rogue" -> "Rogue Skills"
                "resource" -> "Resources"
                "resistance" -> "Resistances"
                else -> tag.replaceFirstChar { it.uppercase() }
            }

            sb.appendLine("$groupName:")

            for ((skillName, skillState) in skills.sortedBy { it.key }) {
                sb.append("  $skillName [Level ${skillState.level}]")

                // Show buffs if present
                if (skillState.tempBuffs > 0) {
                    sb.append(" (+${skillState.tempBuffs} buff)")
                }

                // Show XP progress
                sb.append(" (${formatNumber(skillState.xp)} / ${formatNumber(skillState.xpToNext)} XP)")

                sb.appendLine()

                // Show perks if any
                if (skillState.perks.isNotEmpty()) {
                    val perkNames = skillState.perks.joinToString(", ") { it.name }
                    sb.appendLine("    Perks: $perkNames")
                }

                // Show pending perk choice
                if (skillState.hasPendingPerkChoice()) {
                    sb.appendLine("    ⚠ Perk choice available! Use 'choose perk' command.")
                }
            }

            sb.appendLine()
        }

        return sb.toString().trim()
    }

    /**
     * Format level-up message
     *
     * Example: "🎉 Sword Fighting leveled up to 15!"
     * Example with milestone: "🎉 Sword Fighting leveled up to 10! Choose a perk: 'choose perk for Sword Fighting'"
     */
    fun formatLevelUp(skillName: String, newLevel: Int, hasPerkChoice: Boolean = false): String {
        val sb = StringBuilder()
        sb.append("🎉 $skillName leveled up to $newLevel!")

        if (hasPerkChoice) {
            sb.append(" Choose a perk: 'choose perk for $skillName'")
        }

        return sb.toString()
    }

    /**
     * Format perk choice prompt
     *
     * Example:
     * ```
     * === Perk Choice: Sword Fighting (Level 10) ===
     *
     * Choose one of the following perks:
     *
     * [1] Quick Strike (ABILITY)
     *     Execute a rapid attack that strikes twice in one turn
     *
     * [2] +15% Damage (PASSIVE)
     *     All sword attacks deal 15% more damage
     *
     * Use: choose perk 1 for Sword Fighting
     * ```
     */
    fun formatPerkChoice(skillName: String, level: Int, perks: List<Perk>): String {
        require(perks.isNotEmpty()) { "Perk list cannot be empty" }

        val sb = StringBuilder()
        sb.appendLine("=== Perk Choice: $skillName (Level $level) ===")
        sb.appendLine()
        sb.appendLine("Choose one of the following perks:")
        sb.appendLine()

        perks.forEachIndexed { index, perk ->
            val perkNumber = index + 1
            val perkTypeLabel = when (perk.type) {
                PerkType.ABILITY -> "ABILITY"
                PerkType.PASSIVE -> "PASSIVE"
            }

            sb.appendLine("[$perkNumber] ${perk.name} ($perkTypeLabel)")
            sb.appendLine("    ${perk.description}")
            sb.appendLine()
        }

        sb.appendLine("Use: choose perk [1-${perks.size}] for $skillName")

        return sb.toString().trim()
    }

    /**
     * Format perk unlocked confirmation message
     *
     * Example: "✓ Perk unlocked: Quick Strike (Sword Fighting)"
     */
    fun formatPerkUnlocked(perkName: String, skillName: String): String {
        return "✓ Perk unlocked: $perkName ($skillName)"
    }

    /**
     * Format XP gained message
     *
     * Example: "Sword Fighting +50 XP (Level 15: 2,350 / 25,600)"
     */
    fun formatXpGained(skillName: String, xpGained: Long, currentXp: Long, xpToNext: Long, currentLevel: Int): String {
        return "$skillName +${formatNumber(xpGained)} XP (Level $currentLevel: ${formatNumber(currentXp)} / ${formatNumber(xpToNext)})"
    }

    /**
     * Format skill unlock message
     *
     * Example: "✓ Skill unlocked: Sword Fighting (via training)"
     */
    fun formatSkillUnlocked(skillName: String, method: String): String {
        return "✓ Skill unlocked: $skillName (via $method)"
    }

    /**
     * Format skill check result
     *
     * Example: "Sword Fighting check: Success! (rolled 15 + 10 = 25 vs DC 20, margin: +5)"
     * Example: "Stealth check: Failed. (rolled 8 + 3 = 11 vs DC 15, margin: -4)"
     */
    fun formatSkillCheck(
        skillName: String,
        success: Boolean,
        roll: Int,
        skillLevel: Int,
        difficulty: Int,
        margin: Int
    ): String {
        val total = roll + skillLevel
        val outcome = if (success) "Success!" else "Failed."
        return "$skillName check: $outcome (rolled $roll + $skillLevel = $total vs DC $difficulty, margin: ${if (margin >= 0) "+" else ""}$margin)"
    }

    /**
     * Format a simple skill sheet with just skill names and levels
     * Useful for compact displays in UI
     *
     * Example: "Sword Fighting 15, Shield Use 8, Fire Magic 12"
     */
    fun formatCompactSkillList(component: SkillComponent): String {
        val unlockedSkills = component.getUnlockedSkills()

        if (unlockedSkills.isEmpty()) {
            return "No skills unlocked"
        }

        return unlockedSkills.entries
            .sortedByDescending { (_, skillState) -> skillState.level }
            .joinToString(", ") { (skillName, skillState) ->
                "$skillName ${skillState.level}"
            }
    }

    /**
     * Format number with thousand separators
     * Example: 25600 -> "25,600"
     */
    private fun formatNumber(number: Long): String {
        return number.toString().reversed().chunked(3).joinToString(",").reversed()
    }
}
