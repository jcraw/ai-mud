package com.jcraw.mud.reasoning.procedural

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.Stats
import kotlin.random.Random

/**
 * Generates procedural NPCs with varied stats and personalities
 */
class NPCGenerator(
    private val theme: DungeonTheme,
    private val random: Random = Random.Default
) {

    private val npcSuffixes = listOf(
        "Warrior", "Mage", "Rogue", "Brute", "Sentinel",
        "Berserker", "Assassin", "Champion", "Warden", "Destroyer"
    )

    /**
     * Generate hostile NPC with random stats
     */
    fun generateHostileNPC(
        id: String,
        powerLevel: Int = 1  // 1 = weak, 2 = medium, 3 = strong, 4 = boss
    ): Entity.NPC {
        val prefix = theme.npcNamePrefixes.random(random)
        val suffix = npcSuffixes.random(random)
        val name = "$prefix $suffix"

        val stats = generateStatsForPowerLevel(powerLevel)
        val (health, maxHealth) = calculateHealthForPowerLevel(powerLevel, stats.constitution)

        return Entity.NPC(
            id = id,
            name = name,
            description = "A menacing $name ready for battle",
            isHostile = true,
            health = health,
            maxHealth = maxHealth,
            stats = stats
        )
    }

    /**
     * Generate friendly NPC with random stats
     */
    fun generateFriendlyNPC(
        id: String,
        powerLevel: Int = 1
    ): Entity.NPC {
        val friendlyPrefixes = listOf("Old", "Wise", "Friendly", "Helpful", "Kind")
        val friendlySuffixes = listOf("Merchant", "Scholar", "Guide", "Traveler", "Hermit")

        val prefix = friendlyPrefixes.random(random)
        val suffix = friendlySuffixes.random(random)
        val name = "$prefix $suffix"

        val stats = generateStatsForPowerLevel(powerLevel)
        val (health, maxHealth) = calculateHealthForPowerLevel(powerLevel, stats.constitution)

        return Entity.NPC(
            id = id,
            name = name,
            description = "A $name willing to help travelers",
            isHostile = false,
            health = health,
            maxHealth = maxHealth,
            stats = stats
        )
    }

    /**
     * Generate stats based on power level
     */
    private fun generateStatsForPowerLevel(powerLevel: Int): Stats {
        // Base stats increase with power level
        val baseStatMin = 8 + (powerLevel * 2)
        val baseStatMax = 12 + (powerLevel * 3)

        return Stats(
            strength = random.nextInt(baseStatMin, baseStatMax + 1),
            dexterity = random.nextInt(baseStatMin, baseStatMax + 1),
            constitution = random.nextInt(baseStatMin, baseStatMax + 1),
            intelligence = random.nextInt(baseStatMin, baseStatMax + 1),
            wisdom = random.nextInt(baseStatMin, baseStatMax + 1),
            charisma = random.nextInt(baseStatMin, baseStatMax + 1)
        )
    }

    /**
     * Calculate health based on power level and constitution
     */
    private fun calculateHealthForPowerLevel(powerLevel: Int, constitution: Int): Pair<Int, Int> {
        val conModifier = (constitution - 10) / 2
        val baseHealth = 20 + (powerLevel * 15)
        val healthBonus = conModifier * powerLevel * 2
        val maxHealth = baseHealth + healthBonus

        return Pair(maxHealth, maxHealth)
    }

    /**
     * Generate boss NPC (powerful hostile)
     */
    fun generateBoss(id: String, bossName: String? = null): Entity.NPC {
        val name = bossName ?: "${theme.displayName} Overlord"
        val stats = Stats(
            strength = random.nextInt(16, 20),
            dexterity = random.nextInt(14, 18),
            constitution = random.nextInt(14, 18),
            intelligence = random.nextInt(12, 16),
            wisdom = random.nextInt(14, 18),
            charisma = random.nextInt(8, 14)
        )

        val (health, maxHealth) = calculateHealthForPowerLevel(4, stats.constitution)

        return Entity.NPC(
            id = id,
            name = name,
            description = "The fearsome $name, master of this domain",
            isHostile = true,
            health = health,
            maxHealth = maxHealth,
            stats = stats
        )
    }

    /**
     * Generate random NPC for a room
     * Returns null, one friendly, or one hostile NPC based on probability
     */
    fun generateRoomNPC(roomId: String, powerLevel: Int = 1): Entity.NPC? {
        val roll = random.nextInt(100)

        return when {
            roll < 60 -> null  // 60% no NPC
            roll < 80 -> generateHostileNPC("${roomId}_npc", powerLevel)  // 20% hostile
            else -> generateFriendlyNPC("${roomId}_npc", powerLevel)      // 20% friendly
        }
    }
}
