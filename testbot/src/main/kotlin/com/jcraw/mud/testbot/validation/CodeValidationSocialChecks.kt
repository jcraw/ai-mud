@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot.validation

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.RoomView
import com.jcraw.mud.testbot.ValidationResult

/**
 * Persuade / intimidate social validation (MUD-034f).
 */
internal object CodeValidationSocialChecks {

    fun validatePersuade(
        playerInput: String,
        gmResponse: String,
        currentRoom: RoomView?
    ): ValidationResult? {
        val match = Regex("^(?:persuade|convince)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(playerInput.trim()) ?: return null
        val npcName = match.groupValues[1].trim()
        val npcInRoom = npcInRoom(currentRoom, npcName)
        checkResult(npcName, gmResponse, npcInRoom, "Persuasion")?.let { return it }
        return rejection(npcName, gmResponse, npcInRoom, "cannot persuade", "not in room or not persuadable")
    }

    fun validateIntimidate(
        playerInput: String,
        gmResponse: String,
        currentRoom: RoomView?
    ): ValidationResult? {
        val match = Regex("^(?:intimidate|threaten)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(playerInput.trim()) ?: return null
        val npcName = match.groupValues[1].trim()
        val npcInRoom = npcInRoom(currentRoom, npcName)
        checkResult(npcName, gmResponse, npcInRoom, "Intimidation", intimidateFailedExtra = true)
            ?.let { return it }
        val rejected = rejection(npcName, gmResponse, npcInRoom, "cannot intimidate", "not in room")
        if (rejected != null) return rejected
        // If NPC is in room but no Success/Failed marker, might still be valid
        if (!npcInRoom) return null
        return null
    }

    private fun npcInRoom(currentRoom: RoomView?, npcName: String): Boolean =
        currentRoom?.entities?.filterIsInstance<Entity.NPC>()?.any {
            it.name.lowercase().contains(npcName.lowercase()) ||
                npcName.lowercase().contains(it.name.lowercase())
        } ?: false

    private fun hasSuccess(gmResponse: String): Boolean =
        gmResponse.contains("Success!", ignoreCase = false) ||
            gmResponse.contains("✅ Success!", ignoreCase = false) ||
            gmResponse.contains("succeeds!", ignoreCase = true)

    private fun hasFailed(gmResponse: String, intimidateExtra: Boolean): Boolean {
        val base = gmResponse.contains("Failed!", ignoreCase = false) ||
            gmResponse.contains("❌ Failure!", ignoreCase = false) ||
            gmResponse.contains("fails!", ignoreCase = true)
        if (base) return true
        if (!intimidateExtra) return false
        return gmResponse.contains("fail", ignoreCase = true) &&
            !gmResponse.contains("failure", ignoreCase = true)
    }

    private fun checkResult(
        npcName: String,
        gmResponse: String,
        npcInRoom: Boolean,
        label: String,
        intimidateFailedExtra: Boolean = false
    ): ValidationResult? {
        if (hasSuccess(gmResponse)) {
            return ValidationResult(
                pass = true,
                reason = "[CODE] $label check succeeded on '$npcName' (dice roll won)",
                details = mapOf(
                    "validation_type" to "code",
                    "npc" to npcName,
                    "check_result" to "success",
                    "npc_in_room" to npcInRoom.toString()
                )
            )
        }
        if (hasFailed(gmResponse, intimidateFailedExtra)) {
            val reason = if (intimidateFailedExtra) {
                "[CODE] $label check failed on '$npcName' (dice roll lost, mechanics work. Flavor text about hostility/consequences is normal for failed intimidation)"
            } else {
                "[CODE] $label check failed on '$npcName' (dice roll lost, mechanics work)"
            }
            return ValidationResult(
                pass = true,
                reason = reason,
                details = mapOf(
                    "validation_type" to "code",
                    "npc" to npcName,
                    "check_result" to "failed",
                    "npc_in_room" to npcInRoom.toString()
                )
            )
        }
        return null
    }

    private fun rejection(
        npcName: String,
        gmResponse: String,
        npcInRoom: Boolean,
        cannotPhrase: String,
        reasonSuffix: String
    ): ValidationResult? {
        if (npcInRoom) return null
        val rejected = gmResponse.contains("no one here", ignoreCase = true) ||
            gmResponse.contains("don't see", ignoreCase = true) ||
            gmResponse.contains(cannotPhrase, ignoreCase = true)
        if (!rejected) return null
        return ValidationResult(
            pass = true,
            reason = "[CODE] Correctly rejected: '$npcName' $reasonSuffix",
            details = mapOf("validation_type" to "code", "npc" to npcName)
        )
    }
}
