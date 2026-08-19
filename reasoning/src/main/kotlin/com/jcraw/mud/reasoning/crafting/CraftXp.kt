@file:Suppress("MagicNumber")

package com.jcraw.mud.reasoning.crafting

import com.jcraw.mud.core.SkillEvent

/**
 * Craft XP amounts + gained line. Shared by console and GUI (MUD-039).
 */
object CraftXp {

    fun successXp(difficulty: Int): Long = 50L + (difficulty * 5L)

    fun failureXp(difficulty: Int): Long = 10L + (difficulty * 1L)

    fun gainedLine(skillName: String, event: SkillEvent.XpGained): String =
        "+${event.xpAmount} XP to $skillName (${event.currentXp} total, level ${event.currentLevel})"
}
