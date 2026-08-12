@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemInstance

/**
 * Inventory / take / drop handlers for V3 test engine (MUD-034f).
 */
internal object V3TestItemHandlers {

    fun handleInventory(state: V3TestEngineState): String {
        val inventory = state.worldState.player.inventoryComponent ?: return "You have no inventory."
        val sb = StringBuilder()
        sb.appendLine("Inventory:")
        if (inventory.items.isEmpty()) {
            sb.appendLine("  (empty)")
        } else {
            inventory.items.forEach { item ->
                val template = state.itemRepository.findTemplateById(item.templateId).getOrNull()
                sb.appendLine("  - ${template?.name ?: item.templateId}")
            }
        }
        sb.appendLine("\nGold: ${inventory.gold}")
        return sb.toString().trim()
    }

    fun handleTake(state: V3TestEngineState, target: String): String {
        val spaceId = state.worldState.player.currentRoomId
        val entities = state.worldState.getEntitiesInSpace(spaceId)
        val item = entities.filterIsInstance<Entity.Item>()
            .find { it.name.contains(target, ignoreCase = true) }
            ?: return "You don't see '$target' here."
        val inventory = state.worldState.player.inventoryComponent ?: return "You have no inventory."
        val newItem = ItemInstance(id = item.id, templateId = item.id)
        val newInventory = inventory.copy(items = inventory.items + newItem)
        val newPlayer = state.worldState.player.copy(inventoryComponent = newInventory)
        state.worldState = state.worldState.updatePlayer(newPlayer)
        state.worldState = state.worldState.removeEntityFromSpace(spaceId, item.id)
        return "You take the ${item.name}."
    }

    fun handleDrop(state: V3TestEngineState, target: String): String {
        val inventory = state.worldState.player.inventoryComponent ?: return "You have no inventory."
        val item = findInventoryItem(state, inventory.items, target)
            ?: return "You don't have '$target'."
        val itemName = itemName(state, item)
        removeFromInventory(state, inventory, item)
        dropEntity(state, item, itemName)
        return "You drop the $itemName."
    }

    private fun findInventoryItem(
        state: V3TestEngineState,
        items: List<ItemInstance>,
        target: String
    ): ItemInstance? = items.find {
        itemName(state, it).contains(target, ignoreCase = true)
    }

    private fun itemName(state: V3TestEngineState, item: ItemInstance): String {
        val template = state.itemRepository.findTemplateById(item.templateId).getOrNull()
        return template?.name ?: item.templateId
    }

    private fun removeFromInventory(
        state: V3TestEngineState,
        inventory: com.jcraw.mud.core.InventoryComponent,
        item: ItemInstance
    ) {
        val newInventory = inventory.copy(items = inventory.items - item)
        state.worldState = state.worldState.updatePlayer(
            state.worldState.player.copy(inventoryComponent = newInventory)
        )
    }

    private fun dropEntity(state: V3TestEngineState, item: ItemInstance, itemName: String) {
        val droppedEntity = Entity.Item(id = item.id, name = itemName, description = "A dropped item.")
        state.worldState = state.worldState.addEntityToSpace(
            state.worldState.player.currentRoomId,
            droppedEntity
        )
    }
}
