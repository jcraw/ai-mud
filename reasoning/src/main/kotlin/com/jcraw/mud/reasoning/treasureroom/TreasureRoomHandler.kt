package com.jcraw.mud.reasoning.treasureroom

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository

/**
 * Handles treasure room interactions for Brogue-inspired item selection
 * Manages take/return/swap mechanics with pedestal locking
 *
 * Design:
 * - Taking item locks all other pedestals
 * - Returning item unlocks all pedestals
 * - Leaving room with item marks room as looted (handled by movement handlers)
 * - Validates inventory weight limits and treasure room state
 */
class TreasureRoomHandler(
    private val itemRepository: ItemRepository
) {

    /**
     * Result of treasure room interaction
     */
    sealed class TreasureRoomResult {
        /**
         * Interaction succeeded
         * @param treasureRoomComponent Updated treasure room component
         * @param playerInventory Updated player inventory
         * @param itemName Name of the item affected
         * @param action Description of what happened ("took", "returned")
         */
        data class Success(
            val treasureRoomComponent: TreasureRoomComponent,
            val playerInventory: InventoryComponent,
            val itemName: String,
            val action: String
        ) : TreasureRoomResult()

        /**
         * Interaction failed
         * @param reason Human-readable error message
         */
        data class Failure(val reason: String) : TreasureRoomResult()
    }

    /**
     * Take item from pedestal
     * Locks all other pedestals and adds item to inventory
     *
     * @param treasureRoom Current treasure room component
     * @param playerInventory Current player inventory
     * @param itemTemplateId Item template ID to take from pedestal
     * @param itemTemplates All item templates for weight/name lookups
     * @return TreasureRoomResult with updated states or failure reason
     */
    fun takeItemFromPedestal(
        treasureRoom: TreasureRoomComponent,
        playerInventory: InventoryComponent,
        itemTemplateId: String,
        itemTemplates: Map<String, ItemTemplate>
    ): TreasureRoomResult {
        // Validate room state
        if (treasureRoom.hasBeenLooted) {
            return TreasureRoomResult.Failure("The treasure room has been looted. Only empty altars remain.")
        }

        // Validate no item currently taken
        if (treasureRoom.currentlyTakenItem != null) {
            val currentItemTemplate = itemTemplates[treasureRoom.currentlyTakenItem]
            val currentItemName = currentItemTemplate?.name ?: treasureRoom.currentlyTakenItem
            return TreasureRoomResult.Failure(
                "You already hold the $currentItemName. Return it before taking another."
            )
        }

        // Validate item exists in treasure room
        val pedestal = treasureRoom.getPedestal(itemTemplateId)
            ?: return TreasureRoomResult.Failure("That item is not on any pedestal in this room.")

        // Validate pedestal state
        if (pedestal.state != PedestalState.AVAILABLE) {
            return TreasureRoomResult.Failure("That pedestal is ${pedestal.state.name.lowercase()}.")
        }

        // Get item template
        val templateResult = itemRepository.findTemplateById(itemTemplateId)
        if (templateResult.isFailure || templateResult.getOrNull() == null) {
            return TreasureRoomResult.Failure("Item template not found (corrupted treasure room state)")
        }
        val template = templateResult.getOrNull()!!

        // Check inventory weight capacity
        if (!playerInventory.canAdd(template, 1, itemTemplates)) {
            return TreasureRoomResult.Failure(
                "You can't carry the ${template.name} (weight limit exceeded)"
            )
        }

        // Create item instance
        val itemInstance = ItemInstance(
            id = java.util.UUID.randomUUID().toString(),
            templateId = itemTemplateId,
            quantity = 1,
            quality = 5 // Treasure room items are mid-tier quality (5/10)
        )

        // Update treasure room (lock other pedestals)
        val updatedTreasureRoom = try {
            treasureRoom.takeItem(itemTemplateId)
        } catch (e: IllegalArgumentException) {
            return TreasureRoomResult.Failure(e.message ?: "Cannot take item")
        }

        // Update player inventory
        val updatedInventory = playerInventory.addItem(itemInstance)

        return TreasureRoomResult.Success(
            treasureRoomComponent = updatedTreasureRoom,
            playerInventory = updatedInventory,
            itemName = template.name,
            action = "took"
        )
    }

    /**
     * Return item to pedestal
     * Unlocks all pedestals and removes item from inventory
     *
     * @param treasureRoom Current treasure room component
     * @param playerInventory Current player inventory
     * @param itemInstanceId Item instance ID to return (from player inventory)
     * @param itemTemplates All item templates for name lookups
     * @return TreasureRoomResult with updated states or failure reason
     */
    fun returnItemToPedestal(
        treasureRoom: TreasureRoomComponent,
        playerInventory: InventoryComponent,
        itemInstanceId: String,
        itemTemplates: Map<String, ItemTemplate>
    ): TreasureRoomResult {
        // Validate item in player inventory
        val item = playerInventory.getItem(itemInstanceId)
            ?: return TreasureRoomResult.Failure("You don't have that item.")

        // Validate item matches currentlyTakenItem
        if (treasureRoom.currentlyTakenItem != item.templateId) {
            val expectedTemplateId = treasureRoom.currentlyTakenItem
            if (expectedTemplateId == null) {
                return TreasureRoomResult.Failure("You haven't taken any treasure from this room.")
            } else {
                val expectedTemplate = itemTemplates[expectedTemplateId]
                val expectedName = expectedTemplate?.name ?: expectedTemplateId
                return TreasureRoomResult.Failure(
                    "You can only return the $expectedName to this treasure room."
                )
            }
        }

        // Get item template for name
        val template = itemTemplates[item.templateId]
            ?: return TreasureRoomResult.Failure("Item template not found")

        // Update treasure room (unlock pedestals)
        val updatedTreasureRoom = try {
            treasureRoom.returnItem(item.templateId)
        } catch (e: IllegalArgumentException) {
            return TreasureRoomResult.Failure(e.message ?: "Cannot return item")
        }

        // Update player inventory (remove item)
        val updatedInventory = playerInventory.removeItem(itemInstanceId)
            ?: return TreasureRoomResult.Failure("Failed to remove item from inventory")

        return TreasureRoomResult.Success(
            treasureRoomComponent = updatedTreasureRoom,
            playerInventory = updatedInventory,
            itemName = template.name,
            action = "returned"
        )
    }

    /**
     * Get pedestal examination info for display
     * Returns list of pedestal descriptions with item details and state
     *
     * @param treasureRoom Current treasure room component
     * @param itemTemplates All item templates for details
     * @return List of pedestal info strings
     */
    fun getPedestalInfo(
        treasureRoom: TreasureRoomComponent,
        itemTemplates: Map<String, ItemTemplate>
    ): List<PedestalInfo> {
        return treasureRoom.pedestals.map { pedestal ->
            val template = itemTemplates[pedestal.itemTemplateId]
            PedestalInfo(
                pedestalIndex = pedestal.pedestalIndex,
                itemName = template?.name ?: pedestal.itemTemplateId,
                itemDescription = template?.description ?: "A mysterious item",
                themeDescription = pedestal.themeDescription,
                state = pedestal.state,
                rarity = template?.rarity ?: Rarity.COMMON
            )
        }
    }

    /**
     * Information about a pedestal for display
     */
    data class PedestalInfo(
        val pedestalIndex: Int,
        val itemName: String,
        val itemDescription: String,
        val themeDescription: String,
        val state: PedestalState,
        val rarity: Rarity
    )
}
