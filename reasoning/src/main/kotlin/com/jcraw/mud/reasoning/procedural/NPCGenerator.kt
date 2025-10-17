package com.jcraw.mud.reasoning.procedural

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.Stats
import com.jcraw.mud.core.SkillChallenge
import com.jcraw.mud.core.StatType
import com.jcraw.mud.core.Difficulty
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.core.ComponentType
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

    // Personality templates based on dungeon theme
    private val hostilePersonalities = mapOf(
        DungeonTheme.CRYPT to listOf(
            "undead revenant consumed by hatred",
            "cursed guardian bound to eternal service",
            "mindless skeleton driven by dark magic",
            "vengeful spirit seeking living souls"
        ),
        DungeonTheme.CASTLE to listOf(
            "battle-hardened knight sworn to defend",
            "ruthless mercenary seeking glory",
            "proud champion of the realm",
            "fanatical zealot defending their lord"
        ),
        DungeonTheme.CAVE to listOf(
            "savage beast defending its territory",
            "primitive warrior protecting the tribe",
            "territorial predator hunting intruders",
            "feral creature driven by instinct"
        ),
        DungeonTheme.TEMPLE to listOf(
            "zealous cultist devoted to dark gods",
            "corrupted priest spreading heresy",
            "fanatic guardian protecting sacred grounds",
            "possessed warrior controlled by divine will"
        )
    )

    private val friendlyPersonalities = mapOf(
        DungeonTheme.CRYPT to listOf(
            "ancient spirit offering cryptic wisdom",
            "melancholic ghost seeking redemption",
            "scholarly lich pursuing knowledge",
            "wandering soul longing for peace"
        ),
        DungeonTheme.CASTLE to listOf(
            "noble courtier with courtly manners",
            "wise advisor offering counsel",
            "friendly merchant seeking profit",
            "helpful servant maintaining the halls"
        ),
        DungeonTheme.CAVE to listOf(
            "curious explorer seeking adventure",
            "hermit sage living in solitude",
            "friendly dwarf mining for ore",
            "peaceful druid tending to nature"
        ),
        DungeonTheme.TEMPLE to listOf(
            "devout priest offering blessings",
            "humble monk seeking enlightenment",
            "compassionate healer aiding travelers",
            "wise oracle sharing prophecies"
        )
    )

    // Trait pools for variety
    private val hostileTraits = listOf(
        "aggressive", "ruthless", "merciless", "cunning",
        "fearless", "brutal", "savage", "vengeful"
    )

    private val friendlyTraits = listOf(
        "helpful", "wise", "patient", "curious",
        "generous", "humble", "honorable", "compassionate"
    )

    private val neutralTraits = listOf(
        "cautious", "observant", "pragmatic", "stoic",
        "mysterious", "reserved", "calculating", "diplomatic"
    )

    /**
     * Generate SocialComponent for hostile NPC
     * Hostile NPCs start with negative disposition and aggressive traits
     */
    private fun generateHostileSocialComponent(): SocialComponent {
        val personality = hostilePersonalities[theme]?.random(random)
            ?: "hostile warrior"

        // Select 1-3 traits
        val traitCount = random.nextInt(1, 4)
        val traits = (hostileTraits + neutralTraits)
            .shuffled(random)
            .take(traitCount)

        // Hostile NPCs start with unfriendly to hostile disposition
        val disposition = random.nextInt(-75, -25)

        return SocialComponent(
            disposition = disposition,
            personality = personality,
            traits = traits
        )
    }

    /**
     * Generate SocialComponent for friendly NPC
     * Friendly NPCs start with positive disposition and helpful traits
     */
    private fun generateFriendlySocialComponent(): SocialComponent {
        val personality = friendlyPersonalities[theme]?.random(random)
            ?: "friendly traveler"

        // Select 1-3 traits
        val traitCount = random.nextInt(1, 4)
        val traits = (friendlyTraits + neutralTraits)
            .shuffled(random)
            .take(traitCount)

        // Friendly NPCs start with friendly disposition
        val disposition = random.nextInt(25, 60)

        return SocialComponent(
            disposition = disposition,
            personality = personality,
            traits = traits
        )
    }

    /**
     * Generate SocialComponent for boss NPC
     * Bosses start with very hostile disposition and intimidating traits
     */
    private fun generateBossSocialComponent(): SocialComponent {
        val personality = hostilePersonalities[theme]?.firstOrNull()
            ?: "powerful overlord"

        // Bosses get more traits (2-4)
        val traitCount = random.nextInt(2, 5)
        val traits = (hostileTraits + listOf("powerful", "commanding", "intimidating"))
            .shuffled(random)
            .take(traitCount)

        // Bosses start very hostile
        val disposition = random.nextInt(-100, -75)

        return SocialComponent(
            disposition = disposition,
            personality = personality,
            traits = traits
        )
    }

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

        // Hostile NPCs can be intimidated (and sometimes persuaded at lower levels)
        val (persuasion, intimidation) = createSocialChallenges(
            npcName = name,
            powerLevel = powerLevel,
            includePersuasion = powerLevel <= 2,  // Weaker enemies can be persuaded
            includeIntimidation = true            // All hostile enemies can be intimidated
        )

        val npc = Entity.NPC(
            id = id,
            name = name,
            description = "A menacing $name ready for battle",
            isHostile = true,
            health = health,
            maxHealth = maxHealth,
            stats = stats,
            persuasionChallenge = persuasion,
            intimidationChallenge = intimidation
        )

        // Attach SocialComponent
        val socialComponent = generateHostileSocialComponent()
        return npc.withComponent(socialComponent) as Entity.NPC
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

        // Friendly NPCs can be persuaded
        val (persuasion, intimidation) = createSocialChallenges(
            npcName = name,
            powerLevel = powerLevel,
            includePersuasion = true,   // Friendly NPCs can be persuaded
            includeIntimidation = false // Don't intimidate friendly NPCs
        )

        val npc = Entity.NPC(
            id = id,
            name = name,
            description = "A $name willing to help travelers",
            isHostile = false,
            health = health,
            maxHealth = maxHealth,
            stats = stats,
            persuasionChallenge = persuasion,
            intimidationChallenge = intimidation
        )

        // Attach SocialComponent
        val socialComponent = generateFriendlySocialComponent()
        return npc.withComponent(socialComponent) as Entity.NPC
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
     * Create social challenges based on power level
     * Returns pair of (persuasionChallenge, intimidationChallenge)
     */
    private fun createSocialChallenges(
        npcName: String,
        powerLevel: Int,
        includePersuasion: Boolean,
        includeIntimidation: Boolean
    ): Pair<SkillChallenge?, SkillChallenge?> {
        // Difficulty scales with power level
        val difficulty = when (powerLevel) {
            1 -> Difficulty.EASY          // DC 10
            2 -> Difficulty.MEDIUM        // DC 15
            3 -> Difficulty.HARD          // DC 20
            else -> Difficulty.VERY_HARD  // DC 25 for bosses
        }

        val persuasion = if (includePersuasion) {
            SkillChallenge(
                statType = StatType.CHARISMA,
                difficulty = difficulty,
                description = "Attempt to persuade $npcName through charm and reason",
                successDescription = "Your words strike a chord. $npcName is persuaded.",
                failureDescription = "Your attempt at persuasion falls flat. $npcName is unmoved."
            )
        } else null

        val intimidation = if (includeIntimidation) {
            SkillChallenge(
                statType = StatType.CHARISMA,
                difficulty = difficulty,
                description = "Attempt to intimidate $npcName through force of will",
                successDescription = "Your intimidating presence works. $npcName backs down.",
                failureDescription = "Your attempt at intimidation fails. $npcName is unimpressed."
            )
        } else null

        return Pair(persuasion, intimidation)
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

        // Bosses have BOTH social challenges for multiple solution paths
        val (persuasion, intimidation) = createSocialChallenges(
            npcName = name,
            powerLevel = 4,  // Boss level
            includePersuasion = true,   // Can be persuaded (very hard)
            includeIntimidation = true  // Can be intimidated (very hard)
        )

        val npc = Entity.NPC(
            id = id,
            name = name,
            description = "The fearsome $name, master of this domain",
            isHostile = true,
            health = health,
            maxHealth = maxHealth,
            stats = stats,
            persuasionChallenge = persuasion,
            intimidationChallenge = intimidation
        )

        // Attach SocialComponent
        val socialComponent = generateBossSocialComponent()
        return npc.withComponent(socialComponent) as Entity.NPC
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
