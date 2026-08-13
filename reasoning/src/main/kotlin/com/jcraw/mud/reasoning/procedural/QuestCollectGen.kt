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
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.Quest
import com.jcraw.mud.core.QuestObjective
import com.jcraw.mud.core.QuestReward
import com.jcraw.mud.core.WorldState
import kotlin.random.Random

/**
 * Collect-item quest; fallback → explore (MUD-034n).
 */
internal object QuestCollectGen {

    /**
     * Generate a quest to collect items
     */
    fun generate(worldState: WorldState, theme: DungeonTheme, random: Random): Quest {
        // V3: Get all items from global entity storage
        val items = worldState.entities.values
            .filterIsInstance<Entity.Item>()
            .filter { it.isPickupable && it.itemType != ItemType.CONSUMABLE }

        val targetItem = items.randomOrNull(random) ?: run {
            // Fallback if no collectible items
            return QuestExploreGen.generate(worldState, theme, random)
        }

        return build(targetItem, theme, random)
    }

    private fun build(targetItem: Entity.Item, theme: DungeonTheme, random: Random): Quest {
        return Quest(
            id = "quest_${System.currentTimeMillis()}_${random.nextInt(1000)}",
            title = QuestGeneratorTitles.title(theme, random, "Collect the Treasure"),
            description = "Find and retrieve the ${targetItem.name}. It holds great value.",
            giver = null,
            objectives = listOf(collectObjective(targetItem)),
            reward = QuestReward(experiencePoints = 75, goldAmount = 100, description = "Experience and gold reward"),
            flavorText = flavor(theme)
        )
    }

    private fun collectObjective(targetItem: Entity.Item) = QuestObjective.CollectItem(
        id = "obj_collect_${targetItem.id}",
        description = "Collect ${targetItem.name}",
        targetItemId = targetItem.id,
        targetName = targetItem.name,
        quantity = 1,
        currentQuantity = 0
    )

    private fun flavor(theme: DungeonTheme): String = when (theme) {
        DungeonTheme.CRYPT -> "Ancient treasures lie within these tombs."
        DungeonTheme.CASTLE -> "The royal vault holds many secrets."
        DungeonTheme.CAVE -> "Precious minerals await discovery."
        DungeonTheme.TEMPLE -> "Sacred relics must be recovered."
    }
}
