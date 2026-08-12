@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot.validation

import com.jcraw.mud.core.RoomView
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.testbot.ValidationResult

/**
 * Item interaction code validation (MUD-034f).
 */
internal object CodeValidationItem {

    fun validate(
        playerInput: String,
        gmResponse: String,
        inventoryTracker: Set<String>,
        currentRoom: RoomView?,
        worldState: WorldState
    ): ValidationResult? {
        validateTake(playerInput, gmResponse, inventoryTracker, currentRoom)?.let { return it }
        validateLookItem(playerInput, gmResponse)?.let { return it }
        validateEquip(playerInput, gmResponse)?.let { return it }
        validateDrop(playerInput, gmResponse)?.let { return it }
        validateInventoryCmd(playerInput, gmResponse, inventoryTracker)?.let { return it }
        return null
    }

    private fun validateTake(
        playerInput: String,
        gmResponse: String,
        inventoryTracker: Set<String>,
        currentRoom: RoomView?
    ): ValidationResult? {
        val takeMatch = Regex("(?:take|get|pickup)\\s+(.+)", RegexOption.IGNORE_CASE)
            .find(playerInput) ?: return null
        val itemName = takeMatch.groupValues[1].trim()
        val normalized = itemName.lowercase()
        if (isTakeSuccess(gmResponse)) {
            return takeSuccessResult(itemName, normalized, inventoryTracker)
        }
        if (isTakeRejection(gmResponse)) {
            return validateTakeRejection(itemName, normalized, inventoryTracker, currentRoom)
        }
        return null
    }

    private fun isTakeSuccess(gmResponse: String): Boolean =
        gmResponse.contains("You take", ignoreCase = true) ||
            gmResponse.contains("You pick up", ignoreCase = true)

    private fun isTakeRejection(gmResponse: String): Boolean =
        gmResponse.contains("can't take", ignoreCase = true) ||
            gmResponse.contains("don't see", ignoreCase = true)

    private fun takeSuccessResult(
        itemName: String,
        normalized: String,
        inventoryTracker: Set<String>
    ): ValidationResult {
        if (inventoryTracker.contains(normalized)) {
            return ValidationResult(
                pass = false,
                reason = "[CODE] Bug: Item '$itemName' taken but was already in inventory",
                details = mapOf(
                    "validation_type" to "code",
                    "item" to itemName,
                    "inventory" to inventoryTracker.joinToString(", ")
                )
            )
        }
        return ValidationResult(
            pass = true,
            reason = "[CODE] Item '$itemName' successfully taken",
            details = mapOf("validation_type" to "code", "item" to itemName)
        )
    }

    private fun validateTakeRejection(
        itemName: String,
        normalized: String,
        inventoryTracker: Set<String>,
        currentRoom: RoomView?
    ): ValidationResult {
        if (inventoryTracker.contains(normalized)) {
            return alreadyInInventoryResult(itemName, inventoryTracker)
        }
        if (itemInRoom(currentRoom, normalized)) {
            return roomRejectBugResult(itemName, currentRoom)
        }
        return notAvailableResult(itemName)
    }

    private fun alreadyInInventoryResult(itemName: String, inventoryTracker: Set<String>) =
        ValidationResult(
            pass = true,
            reason = "[CODE] Correctly rejected: '$itemName' already in inventory",
            details = mapOf(
                "validation_type" to "code",
                "item" to itemName,
                "inventory" to inventoryTracker.joinToString(", ")
            )
        )

    private fun itemInRoom(currentRoom: RoomView?, normalized: String): Boolean =
        currentRoom?.entities?.any {
            it.name.lowercase().contains(normalized) || normalized.contains(it.name.lowercase())
        } ?: false

    private fun roomRejectBugResult(itemName: String, currentRoom: RoomView?) = ValidationResult(
        pass = false,
        reason = "[CODE] Bug: '$itemName' exists in room but was rejected",
        details = mapOf(
            "validation_type" to "code",
            "item" to itemName,
            "room_entities" to (currentRoom?.entities?.joinToString(", ") { it.name } ?: "")
        )
    )

    private fun notAvailableResult(itemName: String) = ValidationResult(
        pass = true,
        reason = "[CODE] Correctly rejected: '$itemName' not available",
        details = mapOf("validation_type" to "code", "item" to itemName)
    )

    private fun validateLookItem(playerInput: String, gmResponse: String): ValidationResult? {
        val lookMatch = Regex(
            "(?:look|examine|inspect)(?:\\s+(?:at\\s+)?(.+))?",
            RegexOption.IGNORE_CASE
        ).find(playerInput) ?: return null
        if (lookMatch.groupValues[1].isEmpty()) return null
        val itemName = lookMatch.groupValues[1].trim()
        if (!isNonErrorDescription(gmResponse)) return null
        return ValidationResult(
            pass = true,
            reason = "[CODE] Description provided for '$itemName'",
            details = mapOf(
                "validation_type" to "code",
                "item" to itemName,
                "description_length" to gmResponse.length.toString()
            )
        )
    }

    private fun isNonErrorDescription(gmResponse: String): Boolean =
        !gmResponse.contains("error", ignoreCase = true) &&
            !gmResponse.contains("crash", ignoreCase = true) &&
            !gmResponse.contains("exception", ignoreCase = true) &&
            gmResponse.isNotBlank() &&
            gmResponse.length > 5

    private fun validateEquip(playerInput: String, gmResponse: String): ValidationResult? {
        val equipMatch = Regex("(?:equip|wield|wear)\\s+(.+)", RegexOption.IGNORE_CASE)
            .find(playerInput) ?: return null
        val itemName = equipMatch.groupValues[1].trim()
        if (!isEquipSuccess(gmResponse)) return null
        return ValidationResult(
            pass = true,
            reason = "[CODE] Item '$itemName' successfully equipped",
            details = mapOf("validation_type" to "code", "item" to itemName)
        )
    }

    private fun isEquipSuccess(gmResponse: String): Boolean =
        gmResponse.contains("You equip", ignoreCase = true) ||
            gmResponse.contains("You wield", ignoreCase = true) ||
            gmResponse.contains("You wear", ignoreCase = true)

    private fun validateDrop(playerInput: String, gmResponse: String): ValidationResult? {
        val dropMatch = Regex("(?:drop)\\s+(.+)", RegexOption.IGNORE_CASE)
            .find(playerInput) ?: return null
        val itemName = dropMatch.groupValues[1].trim()
        if (!gmResponse.contains("You drop", ignoreCase = true)) return null
        return ValidationResult(
            pass = true,
            reason = "[CODE] Item '$itemName' successfully dropped",
            details = mapOf("validation_type" to "code", "item" to itemName)
        )
    }

    private fun validateInventoryCmd(
        playerInput: String,
        gmResponse: String,
        inventoryTracker: Set<String>
    ): ValidationResult? {
        if (Regex("^(inventory|inv|i)$", RegexOption.IGNORE_CASE).find(playerInput) == null) {
            return null
        }
        if (!isInventoryListing(gmResponse)) return null
        return ValidationResult(
            pass = true,
            reason = "[CODE] Inventory listing displayed",
            details = mapOf(
                "validation_type" to "code",
                "tracked_inventory" to inventoryTracker.joinToString(", ")
            )
        )
    }

    private fun isInventoryListing(gmResponse: String): Boolean {
        val hasHeader = gmResponse.contains("Inventory", ignoreCase = true) ||
            gmResponse.contains("Carrying", ignoreCase = true)
        val noError = !gmResponse.contains("error", ignoreCase = true) &&
            !gmResponse.contains("crash", ignoreCase = true) &&
            !gmResponse.contains("exception", ignoreCase = true)
        return hasHeader && noError
    }
}
