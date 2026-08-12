@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot.validation

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.RoomView
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.testbot.ValidationResult

/**
 * Combat code validation (MUD-034f).
 */
internal object CodeValidationCombat {

    fun validate(
        playerInput: String,
        gmResponse: String,
        currentRoom: RoomView?,
        worldState: WorldState
    ): ValidationResult? {
        validateAttack(playerInput, gmResponse, currentRoom)?.let { return it }
        validateUseConsumable(playerInput, gmResponse)?.let { return it }
        return null
    }

    private fun validateAttack(
        playerInput: String,
        gmResponse: String,
        currentRoom: RoomView?
    ): ValidationResult? {
        val attackMatch = attackRegex.find(playerInput) ?: return null
        val targetName = attackMatch.groupValues[1].trim()
        noTargetResult(targetName, gmResponse)?.let { return it }
        victoryResult(gmResponse)?.let { return it }
        return attackPresenceResult(targetName, gmResponse, currentRoom)
    }

    private val attackRegex = Regex(
        "(?:continue\\s+)?(?:attack(?:ing)?|fight(?:ing)?|kill(?:ing)?|hit(?:ting)?)(?:\\s+(.+))?",
        RegexOption.IGNORE_CASE
    )

    private fun noTargetResult(targetName: String, gmResponse: String): ValidationResult? {
        if (targetName.isNotBlank()) return null
        if (!gmResponse.contains("Attack whom", ignoreCase = true)) return null
        return ValidationResult(
            pass = true,
            reason = "[CODE] Correctly prompted for target",
            details = mapOf("validation_type" to "code", "combat" to "no_target")
        )
    }

    private fun victoryResult(gmResponse: String): ValidationResult? {
        if (!gmResponse.contains("has been defeated", ignoreCase = true)) return null
        return ValidationResult(
            pass = true,
            reason = "[CODE] NPC defeated - victory",
            details = mapOf("validation_type" to "code", "combat" to "victory")
        )
    }

    private fun attackPresenceResult(
        targetName: String,
        gmResponse: String,
        currentRoom: RoomView?
    ): ValidationResult? {
        val hasCombatMessages = hasCombatMessages(gmResponse)
        val npcPresent = npcInRoom(currentRoom, targetName)
        if (npcPresent && hasCombatMessages) return ongoingCombatResult()
        if (npcPresent && !hasCombatMessages) return failedInitiateResult(targetName, currentRoom)
        if (!npcPresent && targetName.isNotBlank()) return npcMissingResult()
        return null
    }

    private fun ongoingCombatResult() = ValidationResult(
        pass = true,
        reason = "[CODE] Combat ongoing - attack/dodge/damage messages present",
        details = mapOf("validation_type" to "code", "combat" to "ongoing")
    )

    private fun failedInitiateResult(targetName: String, currentRoom: RoomView?) = ValidationResult(
        pass = false,
        reason = "[CODE] Bug: NPC '$targetName' in room but combat didn't start",
        details = mapOf(
            "validation_type" to "code",
            "combat" to "failed_to_initiate",
            "npcs_in_room" to (currentRoom?.entities?.filterIsInstance<Entity.NPC>()
                ?.joinToString { it.name } ?: "")
        )
    )

    private fun npcMissingResult() = ValidationResult(
        pass = true,
        reason = "[CODE] NPC not in room - correctly rejected (likely killed previously)",
        details = mapOf("validation_type" to "code", "combat" to "npc_not_present")
    )

    private fun hasCombatMessages(gmResponse: String): Boolean =
        gmResponse.contains("attack", ignoreCase = true) ||
            gmResponse.contains("dodge", ignoreCase = true) ||
            gmResponse.contains("damage", ignoreCase = true) ||
            gmResponse.contains("HP:", ignoreCase = false) ||
            gmResponse.contains("hit", ignoreCase = true) ||
            gmResponse.contains("miss", ignoreCase = true)

    private fun npcInRoom(currentRoom: RoomView?, targetName: String): Boolean {
        if (targetName.isBlank()) return false
        return currentRoom?.entities?.filterIsInstance<Entity.NPC>()?.any {
            it.name.lowercase().contains(targetName.lowercase()) ||
                targetName.lowercase().contains(it.name.lowercase())
        } ?: false
    }

    private fun validateUseConsumable(playerInput: String, gmResponse: String): ValidationResult? {
        val useMatch = Regex("(?:use|consume|drink|eat)\\s+(.+)", RegexOption.IGNORE_CASE)
            .find(playerInput) ?: return null
        val itemName = useMatch.groupValues[1].trim()
        restoreResult(itemName, gmResponse)?.let { return it }
        fullHealthResult(itemName, gmResponse)?.let { return it }
        return null
    }

    private fun restoreResult(itemName: String, gmResponse: String): ValidationResult? {
        val restores = gmResponse.contains("restore", ignoreCase = true) &&
            (gmResponse.contains("HP", ignoreCase = true) || gmResponse.contains("health", ignoreCase = true))
        if (!restores) return null
        return ValidationResult(
            pass = true,
            reason = "[CODE] Successfully used consumable '$itemName'",
            details = mapOf("validation_type" to "code", "item" to itemName)
        )
    }

    private fun fullHealthResult(itemName: String, gmResponse: String): ValidationResult? {
        if (!gmResponse.contains("full health", ignoreCase = true)) return null
        return ValidationResult(
            pass = true,
            reason = "[CODE] Correctly noted already at full health",
            details = mapOf("validation_type" to "code", "item" to itemName)
        )
    }
}
