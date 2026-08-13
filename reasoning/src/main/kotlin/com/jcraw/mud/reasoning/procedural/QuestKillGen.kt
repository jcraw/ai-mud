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
import com.jcraw.mud.core.Quest
import com.jcraw.mud.core.QuestObjective
import com.jcraw.mud.core.QuestReward
import com.jcraw.mud.core.WorldState
import kotlin.random.Random

/**
 * Kill-enemy quest; fallback → collect (MUD-034n).
 */
internal object QuestKillGen {

    /**
     * Generate a quest to kill an enemy
     */
    fun generate(worldState: WorldState, theme: DungeonTheme, random: Random): Quest {
        // V3: Get all NPCs from global entity storage
        val hostileNpcs = worldState.entities.values
            .filterIsInstance<Entity.NPC>()
            .filter { it.isHostile }

        val targetNpc = hostileNpcs.randomOrNull(random) ?: run {
            // Fallback if no hostile NPCs
            return QuestCollectGen.generate(worldState, theme, random)
        }

        return build(targetNpc, theme, random)
    }

    private fun build(targetNpc: Entity.NPC, theme: DungeonTheme, random: Random): Quest {
        val title = QuestGeneratorTitles.title(theme, random, "Defeat the Enemy")
        val objective = QuestObjective.KillEnemy(
            id = "obj_kill_${targetNpc.id}",
            description = "Defeat ${targetNpc.name}",
            targetNpcId = targetNpc.id,
            targetName = targetNpc.name
        )

        return Quest(
            id = "quest_${System.currentTimeMillis()}_${random.nextInt(1000)}",
            title = title,
            description = "The ${targetNpc.name} poses a threat to all who enter this place. Defeat them to make the area safer.",
            giver = null,
            objectives = listOf(objective),
            reward = QuestReward(
                experiencePoints = 100,
                goldAmount = 50,
                description = "Experience and gold reward"
            ),
            flavorText = flavor(theme)
        )
    }

    private fun flavor(theme: DungeonTheme): String = when (theme) {
        DungeonTheme.CRYPT -> "The undead must be put to rest."
        DungeonTheme.CASTLE -> "Restore order to these halls."
        DungeonTheme.CAVE -> "Make the caverns safe for travelers."
        DungeonTheme.TEMPLE -> "Purify this sacred place."
    }
}
