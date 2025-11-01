package com.jcraw.mud.reasoning.boss

import com.jcraw.mud.core.*

/**
 * Enhanced combat AI and mechanics for boss fights
 * Provides dynamic boss behavior and summoning mechanics
 */
class BossCombatEnhancements {
    /**
     * Generate AI prompt for boss combat
     * Returns LLM prompt that can be used by combat handlers
     *
     * @param boss The boss NPC
     * @param playerHp Current player HP
     * @param playerName Player name for narrative
     * @return LLM prompt string
     */
    fun getBossAIPrompt(boss: Entity.NPC, playerHp: Int, playerName: String): String {
        if (!boss.bossDesignation.isValid()) {
            // Not a boss, return standard NPC prompt
            return generateStandardNPCPrompt(boss, playerHp, playerName)
        }

        return when (boss.bossDesignation.victoryFlag) {
            "abyssal_lord_defeated" -> generateAbyssalLordPrompt(boss, playerHp, playerName)
            else -> generateGenericBossPrompt(boss, playerHp, playerName)
        }
    }

    /**
     * Generate Abyssal Lord specific AI prompt
     */
    private fun generateAbyssalLordPrompt(boss: Entity.NPC, playerHp: Int, playerName: String): String {
        val hpPercent = (boss.health.toDouble() / boss.maxHealth.toDouble()) * 100

        return """
            You are the Abyssal Lord, an ancient demon of immense power who has ruled these depths for millennia.

            Current situation:
            - Your HP: ${boss.health}/${boss.maxHealth} (${hpPercent.toInt()}%)
            - Enemy: $playerName (HP: $playerHp)

            Your capabilities:
            - Fire Magic: Devastating ranged attacks
            - Dark Sorcery: Debilitating curses and shadow magic
            - Demonic Strength: Crushing melee attacks
            - Ancient Tactics: Centuries of combat experience

            Combat strategy:
            - Above 50% HP: Use ranged fire magic to maintain distance, demonstrate your superiority
            - Below 50% HP: Become desperate and aggressive, summon minions, use dark sorcery
            - Below 25% HP: Go berserk with melee attacks, accept mortality but fight to the bitter end

            Personality:
            - Arrogant and powerful, but not foolish
            - Strategic and calculating
            - Ruthless but not wasteful
            - Ancient wisdom tempered with demonic rage

            Generate a brief (2-3 sentences) combat action description that fits this strategy and personality.
            Be dramatic but concise. Focus on what action you take this turn.
        """.trimIndent()
    }

    /**
     * Generate generic boss AI prompt for other bosses
     */
    private fun generateGenericBossPrompt(boss: Entity.NPC, playerHp: Int, playerName: String): String {
        val hpPercent = (boss.health.toDouble() / boss.maxHealth.toDouble()) * 100

        return """
            You are ${boss.bossDesignation.bossTitle}, a powerful boss entity.

            Current situation:
            - Your HP: ${boss.health}/${boss.maxHealth} (${hpPercent.toInt()}%)
            - Enemy: $playerName (HP: $playerHp)

            As a boss, you should:
            - Fight strategically based on your HP
            - Use your full capabilities
            - Demonstrate why you are a legendary threat

            Generate a brief (2-3 sentences) combat action description.
        """.trimIndent()
    }

    /**
     * Generate standard NPC prompt (fallback)
     */
    private fun generateStandardNPCPrompt(npc: Entity.NPC, playerHp: Int, playerName: String): String {
        return """
            You are ${npc.name}.
            Current HP: ${npc.health}/${npc.maxHealth}
            Fighting: $playerName (HP: $playerHp)

            Generate a brief combat action (1-2 sentences).
        """.trimIndent()
    }

    /**
     * Check if boss should summon minions
     * Used to trigger summoning mechanic during combat
     *
     * @param boss The boss NPC
     * @param hasSummoned Whether boss has already summoned this fight
     * @return true if boss should summon now
     */
    fun shouldSummon(boss: Entity.NPC, hasSummoned: Boolean): Boolean {
        if (!boss.bossDesignation.isValid()) return false
        if (hasSummoned) return false // Only summon once per fight

        // Summon at 50% HP threshold
        val hpPercent = (boss.health.toDouble() / boss.maxHealth.toDouble()) * 100
        return hpPercent <= 50.0
    }

    /**
     * Generate summoned minions for boss fight
     * Returns list of NPC entities to add to combat
     *
     * @param boss The boss doing the summoning
     * @param difficulty Base difficulty for scaling minion strength
     * @return Result containing list of summoned NPCs
     */
    fun summonMinions(boss: Entity.NPC, difficulty: Int): Result<List<Entity.NPC>> {
        return try {
            if (!boss.bossDesignation.isValid()) {
                return Result.success(emptyList())
            }

            val minions = when (boss.bossDesignation.victoryFlag) {
                "abyssal_lord_defeated" -> summonAbyssalMinions(difficulty)
                else -> summonGenericMinions(boss, difficulty)
            }

            Result.success(minions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Summon Abyssal Lord's demonic minions
     */
    private fun summonAbyssalMinions(difficulty: Int): List<Entity.NPC> {
        // Summon 2-3 lesser demons
        val count = (2..3).random()
        return (1..count).map { index ->
            Entity.NPC(
                id = "abyssal_minion_$index",
                name = "Lesser Demon",
                description = "A twisted demonic creature summoned from the abyss, wreathed in shadow and flame.",
                isHostile = true,
                health = 50 + (difficulty * 2),
                maxHealth = 50 + (difficulty * 2),
                stats = Stats(
                    strength = 15 + (difficulty / 5),
                    dexterity = 12 + (difficulty / 5),
                    constitution = 14 + (difficulty / 5),
                    intelligence = 8,
                    wisdom = 8,
                    charisma = 6
                ),
                goldDrop = 10 + (difficulty * 2)
            )
        }
    }

    /**
     * Summon generic boss minions
     */
    private fun summonGenericMinions(boss: Entity.NPC, difficulty: Int): List<Entity.NPC> {
        // Summon 2 weak minions
        return (1..2).map { index ->
            Entity.NPC(
                id = "${boss.id}_minion_$index",
                name = "${boss.name}'s Minion",
                description = "A lesser creature serving ${boss.name}.",
                isHostile = true,
                health = 30 + difficulty,
                maxHealth = 30 + difficulty,
                stats = Stats(
                    strength = 10 + (difficulty / 5),
                    dexterity = 10 + (difficulty / 5),
                    constitution = 10 + (difficulty / 5)
                ),
                goldDrop = 5 + difficulty
            )
        }
    }

    companion object {
        /**
         * Summon triggered narration
         */
        fun getSummonNarration(boss: Entity.NPC, minionCount: Int): String {
            return when (boss.bossDesignation.victoryFlag) {
                "abyssal_lord_defeated" -> {
                    """
                    |The Abyssal Lord roars in fury as his strength wanes!
                    |Dark portals tear open in reality. ${minionCount} Lesser Demons emerge, wreathed in hellfire!
                    |"You dare wound me, mortal? Face the legions of the Abyss!"
                    """.trimMargin()
                }
                else -> {
                    "${boss.name} calls for aid! ${minionCount} minions arrive to assist their master!"
                }
            }
        }
    }
}
