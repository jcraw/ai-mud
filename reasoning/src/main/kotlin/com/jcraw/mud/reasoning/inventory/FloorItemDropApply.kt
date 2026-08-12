package com.jcraw.mud.reasoning.inventory

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SpaceId
import com.jcraw.mud.core.WorldState

/**
 * Pure apply for inventory drop → V2 [InventoryComponent] remove + floor dual-write.
 * Shared by console, GUI, and multi-user handlers so drop writes match inventory reads / take reverse.
 *
 * Reverse of [FloorItemTakeApply]. Never writes V1-only inventory / equip fields on Success.
 */
object FloorItemDropApply {

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
     * Drop an inventory item matching [target] onto [spaceId].
     *
     * Resolve order: exact instance id → template name contains → templateId contains.
     * Success: [InventoryComponent.removeItem], floor [Entity.Item] with take-compatible props,
     * and append to space [itemsDropped].
     */
    fun apply(
        world: WorldState,
        player: PlayerState,
        spaceId: SpaceId,
        target: String,
        templates: Map<String, ItemTemplate>
    ): Result {
        val instance = resolveInstance(player, target, templates)
        val newInv = instance?.let { player.inventoryComponent.removeItem(it.id) }

        return if (instance == null || newInv == null) {
            Result.Failure("You don't have that.")
        } else {
            val template = templates[instance.templateId]
            val floorEntity = toFloorEntity(instance, template)

            var newWorld = world
                .updatePlayer(player.updateInventory(newInv))
                .addEntityToSpace(spaceId, floorEntity)

            val space = newWorld.getSpace(spaceId)
            if (space != null) {
                newWorld = newWorld.updateSpace(spaceId, space.addItem(instance))
            }

            Result.Success(
                world = newWorld,
                itemName = floorEntity.name,
                templateId = instance.templateId,
                instanceId = instance.id
            )
        }
    }

    /**
     * Prefer exact instance id; else template name contains target; else templateId contains.
     */
    internal fun resolveInstance(
        player: PlayerState,
        target: String,
        templates: Map<String, ItemTemplate>
    ): ItemInstance? {
        val query = target.lowercase().trim()
        val items = player.inventoryComponent.items
        if (query.isEmpty() || items.isEmpty()) return null

        return items.find { it.id.equals(query, ignoreCase = true) }
            ?: items.find { instance ->
                templates[instance.templateId]?.name?.lowercase()?.contains(query) == true
            }
            ?: items.find { it.templateId.lowercase().contains(query) }
    }

    /**
     * Stamp entity props matching [LootGenerator.toEntityItems] so take round-trips.
     */
    internal fun toFloorEntity(
        instance: ItemInstance,
        template: ItemTemplate?
    ): Entity.Item {
        val quantitySuffix = if (instance.quantity > 1) " x${instance.quantity}" else ""
        val baseName = template?.name ?: instance.templateId
        val name = baseName + quantitySuffix

        val multiplier = instance.getQualityMultiplier()
        val damage = ((template?.getPropertyInt("damage", 0) ?: 0) * multiplier).toInt()
        val defense = ((template?.getPropertyInt("defense", 0) ?: 0) * multiplier).toInt()
        val healing = template?.getPropertyInt("healing", 0) ?: 0
        val itemType = template?.type ?: ItemType.MISC

        val properties = buildMap {
            template?.properties?.let { putAll(it) }
            put("templateId", instance.templateId)
            put("instanceId", instance.id)
            put("quality", instance.quality.toString())
            put("quantity", instance.quantity.toString())
            instance.charges?.let { put("charges", it.toString()) }
        }

        return Entity.Item(
            id = "drop_${instance.id}",
            name = name,
            description = template?.description ?: "A dropped item.",
            isPickupable = true,
            isUsable = itemType == ItemType.CONSUMABLE,
            itemType = itemType,
            properties = properties,
            damageBonus = damage,
            defenseBonus = defense,
            healAmount = healing,
            isConsumable = itemType == ItemType.CONSUMABLE
        )
    }
}
