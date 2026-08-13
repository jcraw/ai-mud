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
 * Talk-to-NPC quest; fallback → explore (MUD-034n).
 */
internal object QuestTalkGen {

    /**
     * Generate a quest to talk to an NPC
     */
    fun generate(worldState: WorldState, theme: DungeonTheme, random: Random): Quest {
        // V3: Get all NPCs from global entity storage
        val friendlyNpcs = worldState.entities.values
            .filterIsInstance<Entity.NPC>()
            .filter { !it.isHostile }

        val targetNpc = friendlyNpcs.randomOrNull(random) ?: run {
            // Fallback if no friendly NPCs
            return QuestExploreGen.generate(worldState, theme, random)
        }

        return build(targetNpc, random)
    }

    private fun build(targetNpc: Entity.NPC, random: Random): Quest {
        val title = "Seek Wisdom from ${targetNpc.name}"
        val objective = QuestObjective.TalkToNpc(
            id = "obj_talk_${targetNpc.id}",
            description = "Speak with ${targetNpc.name}",
            targetNpcId = targetNpc.id,
            targetName = targetNpc.name
        )

        return Quest(
            id = "quest_${System.currentTimeMillis()}_${random.nextInt(1000)}",
            title = title,
            description = "${targetNpc.name} may have valuable information to share.",
            giver = null,
            objectives = listOf(objective),
            reward = QuestReward(
                experiencePoints = 30,
                goldAmount = 0,
                description = "Experience reward"
            ),
            flavorText = "Knowledge is its own reward."
        )
    }
}
