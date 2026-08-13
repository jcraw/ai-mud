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
import com.jcraw.mud.core.QuestObjective
import com.jcraw.mud.core.QuestReward
import com.jcraw.mud.core.WorldState
import kotlin.random.Random

/**
 * Explore-space quest; fallback → fallback-gen (MUD-034n).
 */
internal object QuestExploreGen {

    /**
     * Generate a quest to explore a space
     */
    fun generate(worldState: WorldState, theme: DungeonTheme, random: Random): Quest {
        // V3: Use spaces instead of rooms
        val spaces = worldState.spaces.toList()
        val targetSpace = spaces.randomOrNull(random) ?: return QuestFallbackGen.generate(worldState, theme, random)
        val spaceId = targetSpace.first
        val space = targetSpace.second

        return build(spaceId, space.name, theme, random)
    }

    private fun build(spaceId: String, spaceName: String, theme: DungeonTheme, random: Random): Quest {
        val title = QuestGeneratorTitles.title(theme, random, "Explore the Unknown")
        val objective = QuestObjective.ExploreRoom(
            id = "obj_explore_$spaceId",
            description = "Explore the $spaceName",
            targetRoomId = spaceId,
            targetRoomName = spaceName
        )

        return Quest(
            id = "quest_${System.currentTimeMillis()}_${random.nextInt(1000)}",
            title = title,
            description = "Venture into the $spaceName and discover what lies within.",
            giver = null,
            objectives = listOf(objective),
            reward = QuestReward(
                experiencePoints = 50,
                goldAmount = 25,
                description = "Experience and gold reward"
            ),
            flavorText = flavor(theme)
        )
    }

    private fun flavor(theme: DungeonTheme): String = when (theme) {
        DungeonTheme.CRYPT -> "Map the forgotten chambers."
        DungeonTheme.CASTLE -> "Survey the ancient stronghold."
        DungeonTheme.CAVE -> "Chart the underground passages."
        DungeonTheme.TEMPLE -> "Discover the sacred mysteries."
    }
}
