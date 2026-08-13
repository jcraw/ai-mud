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

import com.jcraw.mud.core.Quest
import com.jcraw.mud.core.WorldState
import kotlin.random.Random

/**
 * Generates procedural quests based on dungeon state
 *
 * Thin facade — bodies in Quest*Gen extracts (MUD-034n).
 * Fallback chain: kill→collect→explore; talk/skill→explore; empty→fallback.
 */
class QuestGenerator(private val seed: Long? = null) {
    private val random = seed?.let { Random(it) } ?: Random.Default

    /**
     * Generate a quest for a given world state
     */
    fun generateQuest(
        worldState: WorldState,
        theme: DungeonTheme
    ): Quest {
        if (worldState.spaces.isEmpty()) {
            return QuestFallbackGen.generate(worldState, theme, random)
        }

        val questType = random.nextInt(5) // 5 quest types

        return when (questType) {
            0 -> QuestKillGen.generate(worldState, theme, random)
            1 -> QuestCollectGen.generate(worldState, theme, random)
            2 -> QuestExploreGen.generate(worldState, theme, random)
            3 -> QuestTalkGen.generate(worldState, theme, random)
            4 -> QuestSkillGen.generate(worldState, theme, random)
            else -> QuestKillGen.generate(worldState, theme, random)
        }
    }

    /**
     * Generate multiple quests for a dungeon
     */
    fun generateQuestPool(
        worldState: WorldState,
        theme: DungeonTheme,
        count: Int = 3
    ): List<Quest> {
        val quests = mutableListOf<Quest>()
        repeat(count) {
            quests.add(generateQuest(worldState, theme))
        }
        return quests
    }
}
