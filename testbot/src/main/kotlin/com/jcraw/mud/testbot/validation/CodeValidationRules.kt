package com.jcraw.mud.testbot.validation

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.RoomView
import com.jcraw.mud.testbot.TestStep
import com.jcraw.mud.testbot.ValidationResult

/**
 * Code-based deterministic validation logic.
 * Returns ValidationResult if we can definitively validate, null otherwise.
 */
object CodeValidationRules {
    fun validate(
        playerInput: String,
        gmResponse: String,
        recentHistory: List<TestStep>,
        worldState: WorldState?
    ): ValidationResult? {
        if (worldState == null) return null

        // Extract previous room ID from history
        val previousRoomId = if (recentHistory.isNotEmpty()) {
            worldState.player.currentRoomId // This is the room AFTER the last action
        } else {
            null
        }

        // Get current room ID after this action
        val currentRoomId = worldState.player.currentRoomId
        val currentRoom = worldState.getCurrentRoomView()

        // Track inventory state from history
        val inventoryTracker = trackInventoryFromHistory(recentHistory)

        // Try each validation type
        validateItemInteraction(playerInput, gmResponse, inventoryTracker, currentRoom, worldState)?.let { return it }
        validateMovement(playerInput, gmResponse, recentHistory, currentRoom, worldState)?.let { return it }
        validateCombat(playerInput, gmResponse, currentRoom, worldState)?.let { return it }
        validateSocialInteraction(playerInput, gmResponse, currentRoom, worldState)?.let { return it }

        // No definitive validation
        return null
    }

    private fun validateItemInteraction(
        playerInput: String,
        gmResponse: String,
        inventoryTracker: Set<String>,
        currentRoom: RoomView?,
        worldState: WorldState
    ): ValidationResult? {
        // Parse "take/get/pickup" commands
        val takeMatch = Regex(
            "(?:take|get|pickup)\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(playerInput)

        if (takeMatch != null) {
            val itemName = takeMatch.groupValues[1].trim()
            val normalizedItemName = itemName.lowercase()

            // Check if response indicates success
            if (gmResponse.contains("You take", ignoreCase = true) ||
                gmResponse.contains("You pick up", ignoreCase = true)) {
                // Check if item was already in inventory
                if (inventoryTracker.contains(normalizedItemName)) {
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
                // Success - item taken
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] Item '$itemName' successfully taken",
                    details = mapOf("validation_type" to "code", "item" to itemName)
                )
            }

            // Check if response indicates failure
            if (gmResponse.contains("can't take", ignoreCase = true) ||
                gmResponse.contains("don't see", ignoreCase = true)) {

                // Check if item is in current room entities
                val itemInRoom = currentRoom?.entities?.any {
                    it.name.lowercase().contains(normalizedItemName) ||
                    normalizedItemName.contains(it.name.lowercase())
                } ?: false

                // Check if item is already in inventory
                val itemInInventory = inventoryTracker.contains(normalizedItemName)

                if (itemInInventory) {
                    // Correct behavior - can't take what you already have
                    return ValidationResult(
                        pass = true,
                        reason = "[CODE] Correctly rejected: '$itemName' already in inventory",
                        details = mapOf(
                            "validation_type" to "code",
                            "item" to itemName,
                            "inventory" to inventoryTracker.joinToString(", ")
                        )
                    )
                }

                if (itemInRoom) {
                    // Bug - item is in room but can't be taken
                    return ValidationResult(
                        pass = false,
                        reason = "[CODE] Bug: '$itemName' exists in room but was rejected",
                        details = mapOf(
                            "validation_type" to "code",
                            "item" to itemName,
                            "room_entities" to (currentRoom?.entities?.joinToString(", ") { it.name } ?: "")
                        )
                    )
                }

                // Correct behavior - item doesn't exist and rejection is appropriate
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] Correctly rejected: '$itemName' not available",
                    details = mapOf("validation_type" to "code", "item" to itemName)
                )
            }
        }

        // Parse "look/examine" commands
        val lookMatch = Regex(
            "(?:look|examine|inspect)(?:\\s+(?:at\\s+)?(.+))?",
            RegexOption.IGNORE_CASE
        ).find(playerInput)

        if (lookMatch != null && lookMatch.groupValues[1].isNotEmpty()) {
            val itemName = lookMatch.groupValues[1].trim()

            // Any descriptive text (non-error) = valid description
            if (!gmResponse.contains("error", ignoreCase = true) &&
                !gmResponse.contains("crash", ignoreCase = true) &&
                !gmResponse.contains("exception", ignoreCase = true) &&
                gmResponse.isNotBlank() &&
                gmResponse.length > 5) { // At least a minimal description

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
        }

        // Parse "equip/wield/wear" commands
        val equipMatch = Regex(
            "(?:equip|wield|wear)\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(playerInput)

        if (equipMatch != null) {
            val itemName = equipMatch.groupValues[1].trim()

            if (gmResponse.contains("You equip", ignoreCase = true) ||
                gmResponse.contains("You wield", ignoreCase = true) ||
                gmResponse.contains("You wear", ignoreCase = true)) {

                return ValidationResult(
                    pass = true,
                    reason = "[CODE] Item '$itemName' successfully equipped",
                    details = mapOf("validation_type" to "code", "item" to itemName)
                )
            }
        }

        // Parse "drop" commands
        val dropMatch = Regex(
            "(?:drop)\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(playerInput)

        if (dropMatch != null) {
            val itemName = dropMatch.groupValues[1].trim()

            if (gmResponse.contains("You drop", ignoreCase = true)) {
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] Item '$itemName' successfully dropped",
                    details = mapOf("validation_type" to "code", "item" to itemName)
                )
            }
        }

        // Parse "inventory" commands
        val inventoryMatch = Regex(
            "^(inventory|inv|i)$",
            RegexOption.IGNORE_CASE
        ).find(playerInput)

        if (inventoryMatch != null) {
            // Any non-error response with "Inventory" or "Carrying" = valid
            if ((gmResponse.contains("Inventory", ignoreCase = true) ||
                 gmResponse.contains("Carrying", ignoreCase = true)) &&
                !gmResponse.contains("error", ignoreCase = true) &&
                !gmResponse.contains("crash", ignoreCase = true) &&
                !gmResponse.contains("exception", ignoreCase = true)) {

                return ValidationResult(
                    pass = true,
                    reason = "[CODE] Inventory listing displayed",
                    details = mapOf(
                        "validation_type" to "code",
                        "tracked_inventory" to inventoryTracker.joinToString(", ")
                    )
                )
            }
        }

        return null
    }

    private fun validateMovement(
        playerInput: String,
        gmResponse: String,
        recentHistory: List<TestStep>,
        currentRoom: RoomView?,
        worldState: WorldState
    ): ValidationResult? {
        val currentRoomId = worldState.player.currentRoomId

        // Parse movement command - support all directions including diagonals
        val movementMatch = Regex(
            "(?:go|move|^)\\s*(north|south|east|west|northeast|northwest|southeast|southwest|up|down|n|s|e|w|ne|nw|se|sw|u|d)(?:\\s|$)",
            RegexOption.IGNORE_CASE
        ).find(playerInput)

        if (movementMatch != null) {
            val directionStr = movementMatch.groupValues[1].lowercase()
            val direction = when (directionStr) {
                "north", "n" -> Direction.NORTH
                "south", "s" -> Direction.SOUTH
                "east", "e" -> Direction.EAST
                "west", "w" -> Direction.WEST
                "northeast", "ne" -> Direction.NORTHEAST
                "northwest", "nw" -> Direction.NORTHWEST
                "southeast", "se" -> Direction.SOUTHEAST
                "southwest", "sw" -> Direction.SOUTHWEST
                "up", "u" -> Direction.UP
                "down", "d" -> Direction.DOWN
                else -> null
            }

            if (direction != null && currentRoom != null) {
                // Check if the direction was valid in the previous room
                val hadValidExit = recentHistory.lastOrNull()?.let { lastStep ->
                    // Extract exits from last response
                    val exitsMatch = Regex("Exits: ([\\w, ]+)").find(lastStep.gmResponse)
                    exitsMatch?.groupValues?.get(1)?.split(", ")?.any { exit ->
                        exit.equals(direction.displayName, ignoreCase = true) ||
                        exit.equals(directionStr, ignoreCase = true)
                    } ?: false
                } ?: true // If no history, assume valid

                // Case 1: "You can't go that way" should only happen for invalid exits
                if (gmResponse.contains("can't go that way", ignoreCase = true)) {
                    return if (hadValidExit) {
                        // Exit existed but got rejection = BUG
                        ValidationResult(
                            pass = false,
                            reason = "[CODE] Invalid rejection: exit $direction existed but game rejected movement",
                            details = mapOf(
                                "validation_type" to "code",
                                "coherence" to "fail",
                                "consistency" to "fail",
                                "mechanics" to "fail"
                            )
                        )
                    } else {
                        // No exit existed, rejection is correct
                        ValidationResult(
                            pass = true,
                            reason = "[CODE] Correctly rejected invalid direction: $direction",
                            details = mapOf(
                                "validation_type" to "code",
                                "coherence" to "pass",
                                "consistency" to "pass",
                                "mechanics" to "pass"
                            )
                        )
                    }
                }

                // Case 2: Room description with header = successful movement
                val roomHeaderMatch = Regex("^([A-Z][a-zA-Z\\s]+)\\n").find(gmResponse)
                if (roomHeaderMatch != null) {
                    // Got a room description = movement succeeded
                    return ValidationResult(
                        pass = true,
                        reason = "[CODE] Movement succeeded: entered room '${roomHeaderMatch.groupValues[1].trim()}'",
                        details = mapOf(
                            "validation_type" to "code",
                            "coherence" to "pass",
                            "consistency" to "pass",
                            "mechanics" to "pass",
                            "room_name" to roomHeaderMatch.groupValues[1].trim(),
                            "room_id" to currentRoomId
                        )
                    )
                }
            }
        }

        return null
    }

    private fun validateCombat(
        playerInput: String,
        gmResponse: String,
        currentRoom: RoomView?,
        worldState: WorldState
    ): ValidationResult? {
        // Parse "attack" commands (including "continue attacking" variants)
        val attackMatch = Regex(
            "(?:continue\\s+)?(?:attack(?:ing)?|fight(?:ing)?|kill(?:ing)?|hit(?:ting)?)(?:\\s+(.+))?",
            RegexOption.IGNORE_CASE
        ).find(playerInput)

        if (attackMatch != null) {
            val targetName = attackMatch.groupValues[1].trim()

            // "attack" with no target - check for appropriate response
            if (targetName.isBlank()) {
                if (gmResponse.contains("Attack whom", ignoreCase = true)) {
                    return ValidationResult(
                        pass = true,
                        reason = "[CODE] Correctly prompted for target",
                        details = mapOf("validation_type" to "code", "combat" to "no_target")
                    )
                }
            }

            // Combat V2: No modal combat state, combat is emergent
            val inCombatNow = false

            // Check if NPC exists in room NOW (after action)
            val npcStillInRoom = currentRoom?.entities?.filterIsInstance<com.jcraw.mud.core.Entity.NPC>()?.any {
                it.name.lowercase().contains(targetName.lowercase()) ||
                targetName.lowercase().contains(it.name.lowercase())
            } ?: false

            // Check for victory marker (hardcoded in CombatResolver)
            if (gmResponse.contains("has been defeated", ignoreCase = true)) {
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] NPC defeated - victory",
                    details = mapOf("validation_type" to "code", "combat" to "victory")
                )
            }

            if (inCombatNow) {
                // Still in combat after attack = combat progressing normally
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] Combat ongoing",
                    details = mapOf("validation_type" to "code", "combat" to "ongoing")
                )
            } else {
                // Not in combat after attack command
                // Either: combat just started, NPC not found, or missing target

                // Check if NPC exists in room - this tells us if it's a bug
                val npcInRoom = if (targetName.isNotBlank()) {
                    currentRoom?.entities?.filterIsInstance<com.jcraw.mud.core.Entity.NPC>()?.any {
                        it.name.lowercase().contains(targetName.lowercase()) ||
                        targetName.lowercase().contains(it.name.lowercase())
                    } ?: false
                } else {
                    false
                }

                // If NPC is in room but we're not in combat, that's a bug (should have started combat)
                if (npcInRoom) {
                    return ValidationResult(
                        pass = false,
                        reason = "[CODE] Bug: NPC '$targetName' in room but combat didn't start",
                        details = mapOf(
                            "validation_type" to "code",
                            "combat" to "failed_to_initiate",
                            "npcs_in_room" to (currentRoom?.entities?.filterIsInstance<com.jcraw.mud.core.Entity.NPC>()
                                ?.joinToString { it.name } ?: "")
                        )
                    )
                }

                // NPC not in room = correct rejection or missing target
                // Check world state to see if NPC was removed (means it was killed)
                if (!npcStillInRoom && targetName.isNotBlank()) {
                    return ValidationResult(
                        pass = true,
                        reason = "[CODE] NPC not in room - correctly rejected (likely killed previously)",
                        details = mapOf("validation_type" to "code", "combat" to "npc_not_present")
                    )
                }

                // Let LLM validate other cases
                return null
            }
        }

        // Parse "use" commands (consumables in combat)
        val useMatch = Regex(
            "(?:use|consume|drink|eat)\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(playerInput)

        if (useMatch != null) {
            val itemName = useMatch.groupValues[1].trim()

            if (gmResponse.contains("restore", ignoreCase = true) &&
                (gmResponse.contains("HP", ignoreCase = true) || gmResponse.contains("health", ignoreCase = true))) {
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] Successfully used consumable '$itemName'",
                    details = mapOf("validation_type" to "code", "item" to itemName)
                )
            }

            if (gmResponse.contains("full health", ignoreCase = true)) {
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] Correctly noted already at full health",
                    details = mapOf("validation_type" to "code", "item" to itemName)
                )
            }
        }

        return null
    }

    private fun validateSocialInteraction(
        playerInput: String,
        gmResponse: String,
        currentRoom: RoomView?,
        worldState: WorldState
    ): ValidationResult? {
        // Parse "talk/speak" commands (conversation, NOT a skill check)
        val talkPattern = Regex("^(?:talk|speak)(?:\\s+(?:to|with))?\\s+(.+)$", RegexOption.IGNORE_CASE)
        val talkMatch = talkPattern.find(playerInput.trim())

        if (talkMatch != null) {
            val npcName = talkMatch.groupValues[1].trim()

            // Check if NPC is in room using game state
            val npcInRoom = currentRoom?.entities?.filterIsInstance<com.jcraw.mud.core.Entity.NPC>()?.any {
                it.name.lowercase().contains(npcName.lowercase()) ||
                npcName.lowercase().contains(it.name.lowercase())
            } ?: false

            // "talk to" is NOT a skill check - it just generates dialogue
            // PASS if response contains NPC dialogue (check for multiple patterns)
            val hasDialogue = gmResponse.contains("says:", ignoreCase = true) ||
                gmResponse.contains("says \"", ignoreCase = true) ||
                gmResponse.contains("says, \"", ignoreCase = true) ||
                gmResponse.contains("replies:", ignoreCase = true) ||
                gmResponse.contains("replies, \"", ignoreCase = true) ||
                gmResponse.matches(Regex(".*\\b${Regex.escape(npcName)}\\b.*:", RegexOption.IGNORE_CASE))

            if (hasDialogue && !gmResponse.contains("error", ignoreCase = true)) {
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] NPC dialogue generated for '$npcName' (talk is conversation, not a check)",
                    details = mapOf(
                        "validation_type" to "code",
                        "npc" to npcName,
                        "interaction_type" to "conversation",
                        "npc_in_room" to npcInRoom.toString()
                    )
                )
            }

            // Correct rejection if NPC not in room
            if (!npcInRoom && (gmResponse.contains("no one here", ignoreCase = true) ||
                gmResponse.contains("don't see", ignoreCase = true))) {
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] Correctly rejected: '$npcName' not in room",
                    details = mapOf("validation_type" to "code", "npc" to npcName)
                )
            }

            // If NPC is in room and we got non-error response, assume it's dialogue
            if (npcInRoom && gmResponse.isNotBlank() && !gmResponse.contains("error", ignoreCase = true)) {
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] NPC '$npcName' in room, got non-error response (assuming valid dialogue)",
                    details = mapOf(
                        "validation_type" to "code",
                        "npc" to npcName,
                        "interaction_type" to "conversation_fallback"
                    )
                )
            }
        }

        // Parse "persuade" commands (CHA skill check)
        val persuadePattern = Regex("^(?:persuade|convince)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val persuadeMatch = persuadePattern.find(playerInput.trim())

        if (persuadeMatch != null) {
            val npcName = persuadeMatch.groupValues[1].trim()

            // Check if NPC is in room using game state
            val npcInRoom = currentRoom?.entities?.filterIsInstance<com.jcraw.mud.core.Entity.NPC>()?.any {
                it.name.lowercase().contains(npcName.lowercase()) ||
                npcName.lowercase().contains(it.name.lowercase())
            } ?: false

            // Check if skill check triggered (Success or Failed indicates dice roll happened)
            val hasSuccess = gmResponse.contains("Success!", ignoreCase = false) ||
                gmResponse.contains("✅ Success!", ignoreCase = false) ||
                gmResponse.contains("succeeds!", ignoreCase = true)

            val hasFailed = gmResponse.contains("Failed!", ignoreCase = false) ||
                gmResponse.contains("❌ Failure!", ignoreCase = false) ||
                gmResponse.contains("fails!", ignoreCase = true)

            if (hasSuccess) {
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] Persuasion check succeeded on '$npcName' (dice roll won)",
                    details = mapOf(
                        "validation_type" to "code",
                        "npc" to npcName,
                        "check_result" to "success",
                        "npc_in_room" to npcInRoom.toString()
                    )
                )
            }

            if (hasFailed) {
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] Persuasion check failed on '$npcName' (dice roll lost, mechanics work)",
                    details = mapOf(
                        "validation_type" to "code",
                        "npc" to npcName,
                        "check_result" to "failed",
                        "npc_in_room" to npcInRoom.toString()
                    )
                )
            }

            // "Cannot persuade" when NPC not in room = correct
            if (!npcInRoom && (gmResponse.contains("no one here", ignoreCase = true) ||
                gmResponse.contains("don't see", ignoreCase = true) ||
                gmResponse.contains("cannot persuade", ignoreCase = true))) {
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] Correctly rejected: '$npcName' not in room or not persuadable",
                    details = mapOf("validation_type" to "code", "npc" to npcName)
                )
            }
        }

        // Parse "intimidate/threaten" commands (CHA skill check)
        val intimidatePattern = Regex("^(?:intimidate|threaten)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val intimidateMatch = intimidatePattern.find(playerInput.trim())

        if (intimidateMatch != null) {
            val npcName = intimidateMatch.groupValues[1].trim()

            // Check if NPC is in room using game state
            val npcInRoom = currentRoom?.entities?.filterIsInstance<com.jcraw.mud.core.Entity.NPC>()?.any {
                it.name.lowercase().contains(npcName.lowercase()) ||
                npcName.lowercase().contains(it.name.lowercase())
            } ?: false

            // Check if skill check triggered (Success or Failed indicates dice roll happened)
            // Both SUCCESS and FAILURE are valid outcomes - the mechanic is what we're testing!
            val hasSuccess = gmResponse.contains("Success!", ignoreCase = false) ||
                gmResponse.contains("✅ Success!", ignoreCase = false) ||
                gmResponse.contains("succeeds!", ignoreCase = true)

            val hasFailed = gmResponse.contains("Failed!", ignoreCase = false) ||
                gmResponse.contains("❌ Failure!", ignoreCase = false) ||
                gmResponse.contains("fails!", ignoreCase = true) ||
                (gmResponse.contains("fail", ignoreCase = true) && !gmResponse.contains("failure", ignoreCase = true))

            if (hasSuccess) {
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] Intimidation check succeeded on '$npcName' (dice roll won)",
                    details = mapOf(
                        "validation_type" to "code",
                        "npc" to npcName,
                        "check_result" to "success",
                        "npc_in_room" to npcInRoom.toString()
                    )
                )
            }

            if (hasFailed) {
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] Intimidation check failed on '$npcName' (dice roll lost, mechanics work. Flavor text about hostility/consequences is normal for failed intimidation)",
                    details = mapOf(
                        "validation_type" to "code",
                        "npc" to npcName,
                        "check_result" to "failed",
                        "npc_in_room" to npcInRoom.toString()
                    )
                )
            }

            // "Cannot intimidate" when NPC not in room = correct rejection
            if (!npcInRoom && (gmResponse.contains("no one here", ignoreCase = true) ||
                gmResponse.contains("don't see", ignoreCase = true) ||
                gmResponse.contains("cannot intimidate", ignoreCase = true))) {
                return ValidationResult(
                    pass = true,
                    reason = "[CODE] Correctly rejected: '$npcName' not in room",
                    details = mapOf("validation_type" to "code", "npc" to npcName)
                )
            }

            // If NPC is in room but no Success/Failed marker, might still be valid
            // (e.g., NPC doesn't have intimidation challenge set)
            if (!npcInRoom) {
                // Unclear - let LLM decide
                return null
            }
        }

        return null
    }

    /**
     * Track inventory state by analyzing history for take/drop commands.
     * Returns set of lowercase item names currently in inventory.
     */
    internal fun trackInventoryFromHistory(history: List<TestStep>): Set<String> {
        val inventory = mutableSetOf<String>()

        for (step in history) {
            val input = step.playerInput
            val response = step.gmResponse

            // Check for successful "take" commands
            val takeMatch = Regex(
                "(?:take|get|pickup)\\s+(.+)",
                RegexOption.IGNORE_CASE
            ).find(input)

            if (takeMatch != null &&
                (response.contains("You take", ignoreCase = true) ||
                 response.contains("You pick up", ignoreCase = true))) {
                val itemName = takeMatch.groupValues[1].trim().lowercase()
                inventory.add(itemName)
            }

            // Check for successful "drop" commands
            val dropMatch = Regex(
                "(?:drop)\\s+(.+)",
                RegexOption.IGNORE_CASE
            ).find(input)

            if (dropMatch != null && response.contains("You drop", ignoreCase = true)) {
                val itemName = dropMatch.groupValues[1].trim().lowercase()
                inventory.remove(itemName)
            }
        }

        return inventory
    }
}
