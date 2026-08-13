@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter"
)

package com.jcraw.mud.reasoning.procedural

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.Stats
import kotlin.random.Random

/**
 * Hostile / friendly / boss / room-roll entities for [NPCGenerator] (MUD-034n).
 */
internal object NPCGeneratorEntities {

    /**
     * Generate hostile NPC with random stats
     */
    fun generateHostileNPC(
        theme: DungeonTheme,
        random: Random,
        id: String,
        powerLevel: Int
    ): Entity.NPC {
        val name = "${theme.npcNamePrefixes.random(random)} ${NPCGeneratorTables.npcSuffixes.random(random)}"
        val stats = NPCGeneratorStats.generateStatsForPowerLevel(powerLevel, random)
        val (health, maxHealth) = NPCGeneratorStats.calculateHealthForPowerLevel(powerLevel, stats.constitution)
        val (persuasion, intimidation) = NPCGeneratorStats.createSocialChallenges(
            npcName = name,
            powerLevel = powerLevel,
            includePersuasion = powerLevel <= 2,
            includeIntimidation = true
        )
        return assembleNpc(
            id, name, "A menacing $name ready for battle", true,
            health, maxHealth, stats, persuasion, intimidation,
            NPCGeneratorSocial.generateHostile(theme, random)
        )
    }

    /**
     * Generate friendly NPC with random stats
     */
    fun generateFriendlyNPC(
        theme: DungeonTheme,
        random: Random,
        id: String,
        powerLevel: Int
    ): Entity.NPC {
        val name = "${friendlyPrefixes().random(random)} ${friendlySuffixes().random(random)}"
        val stats = NPCGeneratorStats.generateStatsForPowerLevel(powerLevel, random)
        val (health, maxHealth) = NPCGeneratorStats.calculateHealthForPowerLevel(powerLevel, stats.constitution)
        val (persuasion, intimidation) = NPCGeneratorStats.createSocialChallenges(
            npcName = name,
            powerLevel = powerLevel,
            includePersuasion = true,
            includeIntimidation = false
        )
        return assembleNpc(
            id, name, "A $name willing to help travelers", false,
            health, maxHealth, stats, persuasion, intimidation,
            NPCGeneratorSocial.generateFriendly(theme, random)
        )
    }

    private fun friendlyPrefixes() = listOf("Old", "Wise", "Friendly", "Helpful", "Kind")

    private fun friendlySuffixes() = listOf("Merchant", "Scholar", "Guide", "Traveler", "Hermit")

    /**
     * Generate boss NPC (powerful hostile)
     */
    fun generateBoss(
        theme: DungeonTheme,
        random: Random,
        id: String,
        bossName: String?
    ): Entity.NPC {
        val name = bossName ?: "${theme.displayName} Overlord"
        val stats = bossStats(random)
        val (health, maxHealth) = NPCGeneratorStats.calculateHealthForPowerLevel(4, stats.constitution)
        val (persuasion, intimidation) = NPCGeneratorStats.createSocialChallenges(
            npcName = name,
            powerLevel = 4,
            includePersuasion = true,
            includeIntimidation = true
        )
        return assembleNpc(
            id, name, "The fearsome $name, master of this domain", true,
            health, maxHealth, stats, persuasion, intimidation,
            NPCGeneratorSocial.generateBoss(theme, random)
        )
    }

    private fun bossStats(random: Random) = Stats(
        strength = random.nextInt(16, 20),
        dexterity = random.nextInt(14, 18),
        constitution = random.nextInt(14, 18),
        intelligence = random.nextInt(12, 16),
        wisdom = random.nextInt(14, 18),
        charisma = random.nextInt(8, 14)
    )

    private fun assembleNpc(
        id: String,
        name: String,
        description: String,
        isHostile: Boolean,
        health: Int,
        maxHealth: Int,
        stats: Stats,
        persuasion: com.jcraw.mud.core.SkillChallenge?,
        intimidation: com.jcraw.mud.core.SkillChallenge?,
        social: com.jcraw.mud.core.SocialComponent
    ): Entity.NPC {
        val npc = Entity.NPC(
            id = id,
            name = name,
            description = description,
            isHostile = isHostile,
            health = health,
            maxHealth = maxHealth,
            stats = stats,
            persuasionChallenge = persuasion,
            intimidationChallenge = intimidation
        )
        return npc.withComponent(social) as Entity.NPC
    }

    /**
     * Generate random NPC for a room
     * Returns null, one friendly, or one hostile NPC based on probability
     */
    fun generateRoomNPC(
        theme: DungeonTheme,
        random: Random,
        roomId: String,
        powerLevel: Int
    ): Entity.NPC? {
        val roll = random.nextInt(100)

        return when {
            roll < 60 -> null  // 60% no NPC
            roll < 80 -> generateHostileNPC(theme, random, "${roomId}_npc", powerLevel)  // 20% hostile
            else -> generateFriendlyNPC(theme, random, "${roomId}_npc", powerLevel)      // 20% friendly
        }
    }
}
