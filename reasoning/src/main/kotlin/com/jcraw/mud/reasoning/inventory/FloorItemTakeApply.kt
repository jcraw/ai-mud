package com.jcraw.mud.reasoning.inventory

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SpaceId
import com.jcraw.mud.core.WorldState
import java.util.UUID

/**
 * Pure apply for floor-item take → V2 [InventoryComponent].
 * Shared by console, GUI, and multi-user handlers so take writes match inventory reads.
 */
object FloorItemTakeApply {

    private const val QUALITY_MIN = 1
    private const val QUALITY_MAX = 10
    private const val DEFAULT_QUALITY = 5

    sealed class Result {
        data class Success(
            val world: WorldState,
            val itemName: String,
            val templateId: String,
            val instanceId: String
        ) : Result()

        data class Failure(val message: String) : Result()
    }

    /**
     * Take [floorItem] from [spaceId] into the player's V2 inventory.
     *
     * Resolves [ItemInstance] from entity properties (`templateId` / `instanceId` / quality /
     * quantity / charges) or name-matches against [templates]. Never writes V1-only inventory.
     */
    fun apply(
        world: WorldState,
        player: PlayerState,
        spaceId: SpaceId,
        floorItem: Entity.Item,
        templates: Map<String, ItemTemplate>
    ): Result {
        if (!floorItem.isPickupable) {
            return Result.Failure("That's part of the environment and can't be taken.")
        }

        val resolved = resolveInstance(floorItem, templates)
        val updatedPlayer = resolved?.let { (instance, template) ->
            val templateMap = templates + (template.id to template)
            player.addItemInstance(instance, templateMap)
        }

        return when {
            resolved == null -> Result.Failure(
                "You can't take the ${floorItem.name} — no matching item template."
            )
            updatedPlayer == null -> Result.Failure(
                "You can't carry that - you're already carrying too much weight."
            )
            else -> {
                val (instance, template) = resolved
                var newWorld = world
                    .removeEntityFromSpace(spaceId, floorItem.id)
                    .updatePlayer(updatedPlayer)
                val droppedId = floorItem.properties["instanceId"] ?: instance.id
                newWorld = newWorld.removeDroppedItem(spaceId, droppedId)
                Result.Success(
                    world = newWorld,
                    itemName = floorItem.name,
                    templateId = template.id,
                    instanceId = instance.id
                )
            }
        }
    }

    /**
     * Prefer properties from LootGenerator.toEntityItems; else name-match templates.
     */
    internal fun resolveInstance(
        floorItem: Entity.Item,
        templates: Map<String, ItemTemplate>
    ): Pair<ItemInstance, ItemTemplate>? {
        val template = resolveTemplate(floorItem, templates) ?: return null

        val instanceId = floorItem.properties["instanceId"]
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        val quality = floorItem.properties["quality"]?.toIntOrNull()
            ?.coerceIn(QUALITY_MIN, QUALITY_MAX)
            ?: DEFAULT_QUALITY
        val quantity = floorItem.properties["quantity"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val charges = floorItem.properties["charges"]?.toIntOrNull()?.coerceAtLeast(0)

        val instance = ItemInstance(
            id = instanceId,
            templateId = template.id,
            quality = quality,
            quantity = quantity,
            charges = charges
        )
        return instance to template
    }

    private fun resolveTemplate(
        floorItem: Entity.Item,
        templates: Map<String, ItemTemplate>
    ): ItemTemplate? {
        val templateIdProp = floorItem.properties["templateId"]
        val byId = templateIdProp?.let { templates[it] }

        // Strip LootGenerator quantity suffix " xN"
        val baseName = floorItem.name.replace(Regex("""\s+x\d+$"""), "").lowercase().trim()
        val exact = if (baseName.isNotEmpty()) {
            templates.values.find { it.name.lowercase() == baseName }
        } else {
            null
        }
        val fuzzy = if (baseName.isNotEmpty()) {
            templates.values.find {
                val n = it.name.lowercase()
                n.contains(baseName) || baseName.contains(n)
            }
        } else {
            null
        }

        return byId ?: exact ?: fuzzy
    }
}
