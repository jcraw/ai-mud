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
import kotlin.random.Random

/**
 * Generates procedural NPCs with varied stats and personalities
 *
 * Thin facade — bodies in NPCGenerator* extracts (MUD-034n).
 */
class NPCGenerator(
    private val theme: DungeonTheme,
    private val random: Random = Random.Default
) {

    /**
     * Generate hostile NPC with random stats
     */
    fun generateHostileNPC(
        id: String,
        powerLevel: Int = 1  // 1 = weak, 2 = medium, 3 = strong, 4 = boss
    ): Entity.NPC = NPCGeneratorEntities.generateHostileNPC(theme, random, id, powerLevel)

    /**
     * Generate friendly NPC with random stats
     */
    fun generateFriendlyNPC(
        id: String,
        powerLevel: Int = 1
    ): Entity.NPC = NPCGeneratorEntities.generateFriendlyNPC(theme, random, id, powerLevel)

    /**
     * Generate boss NPC (powerful hostile)
     */
    fun generateBoss(id: String, bossName: String? = null): Entity.NPC =
        NPCGeneratorEntities.generateBoss(theme, random, id, bossName)

    /**
     * Generate random NPC for a room
     * Returns null, one friendly, or one hostile NPC based on probability
     */
    fun generateRoomNPC(roomId: String, powerLevel: Int = 1): Entity.NPC? =
        NPCGeneratorEntities.generateRoomNPC(theme, random, roomId, powerLevel)
}
