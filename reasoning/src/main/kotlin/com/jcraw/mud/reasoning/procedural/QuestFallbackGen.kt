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
 * Safe fallback scout quest when world lacks populated spaces (MUD-034n).
 */
internal object QuestFallbackGen {

    /**
     * Generate a safe fallback quest when the world lacks populated spaces (e.g., new repository-backed worlds)
     */
    fun generate(worldState: WorldState, theme: DungeonTheme, random: Random): Quest {
        val currentSpaceId = worldState.players.values.firstOrNull()?.currentRoomId ?: "unknown_space"
        val spaceName = worldState.getSpace(currentSpaceId)?.name ?: "Unknown Space"
        return build(currentSpaceId, spaceName, theme, random)
    }

    private fun build(spaceId: String, spaceName: String, theme: DungeonTheme, random: Random): Quest {
        val objective = QuestObjective.ExploreRoom(
            id = "obj_explore_$spaceId",
            description = "Scout your immediate surroundings.",
            targetRoomId = spaceId,
            targetRoomName = spaceName
        )
        return Quest(
            id = questId(random),
            title = QuestGeneratorTitles.title(theme, random, "Scout the Abyss"),
            description = "Before delving deeper, get your bearings and assess the area around you.",
            giver = null,
            objectives = listOf(objective),
            reward = QuestReward(experiencePoints = 20, goldAmount = 15, description = "Basic scouting reward"),
            flavorText = "Even seasoned adventurers start by understanding where they stand."
        )
    }

    private fun questId(random: Random) = "quest_${System.currentTimeMillis()}_${random.nextInt(1000)}"
}
