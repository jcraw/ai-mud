package com.jcraw.mud.reasoning

import com.jcraw.mud.core.*
import kotlin.random.Random

/**
 * Resolves skill checks using d20 + stat modifier vs DC
 */
class SkillCheckResolver(private val random: Random = Random.Default) {

    /**
     * Perform a skill check for a player
     */
    fun checkPlayer(
        player: PlayerState,
        statType: StatType,
        difficulty: Difficulty
    ): SkillCheckResult {
        return performCheck(player.stats, statType, difficulty)
    }

    /**
     * Perform a skill check for an NPC
     */
    fun checkNPC(
        npc: Entity.NPC,
        statType: StatType,
        difficulty: Difficulty
    ): SkillCheckResult {
        return performCheck(npc.stats, statType, difficulty)
    }

    /**
     * Perform the actual d20 + modifier check
     */
    private fun performCheck(
        stats: Stats,
        statType: StatType,
        difficulty: Difficulty
    ): SkillCheckResult {
        val roll = rollD20()
        val modifier = getStatModifier(stats, statType)
        val total = roll + modifier
        val dc = difficulty.dc
        val success = total >= dc
        val margin = total - dc

        return SkillCheckResult(
            success = success,
            roll = roll,
            modifier = modifier,
            total = total,
            dc = dc,
            margin = margin,
            isCriticalSuccess = roll == 20,
            isCriticalFailure = roll == 1
        )
    }

    /**
     * Get the modifier for a specific stat
     */
    private fun getStatModifier(stats: Stats, statType: StatType): Int {
        return when (statType) {
            StatType.STRENGTH -> stats.strModifier()
            StatType.DEXTERITY -> stats.dexModifier()
            StatType.CONSTITUTION -> stats.conModifier()
            StatType.INTELLIGENCE -> stats.intModifier()
            StatType.WISDOM -> stats.wisModifier()
            StatType.CHARISMA -> stats.chaModifier()
        }
    }

    /**
     * Roll a d20 (1-20)
     */
    private fun rollD20(): Int = random.nextInt(1, 21)

    /**
     * Perform an opposed check (e.g., player STR vs NPC STR)
     */
    fun opposedCheck(
        player: PlayerState,
        npc: Entity.NPC,
        playerStat: StatType,
        npcStat: StatType
    ): Boolean {
        val playerRoll = rollD20() + getStatModifier(player.stats, playerStat)
        val npcRoll = rollD20() + getStatModifier(npc.stats, npcStat)
        return playerRoll >= npcRoll
    }
}
