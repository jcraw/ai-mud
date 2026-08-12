@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot.validation

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.RoomView
import com.jcraw.mud.testbot.ValidationResult

/**
 * Talk-command social validation (MUD-034f).
 */
internal object CodeValidationSocialTalk {

    fun validate(playerInput: String, gmResponse: String, currentRoom: RoomView?): ValidationResult? {
        val talkMatch = Regex("^(?:talk|speak)(?:\\s+(?:to|with))?\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(playerInput.trim()) ?: return null
        val npcName = talkMatch.groupValues[1].trim()
        val npcInRoom = npcInRoom(currentRoom, npcName)
        dialogueResult(npcName, gmResponse, npcInRoom)?.let { return it }
        rejectionResult(npcName, gmResponse, npcInRoom)?.let { return it }
        fallbackResult(npcName, gmResponse, npcInRoom)?.let { return it }
        return null
    }

    private fun npcInRoom(currentRoom: RoomView?, npcName: String): Boolean =
        currentRoom?.entities?.filterIsInstance<Entity.NPC>()?.any {
            it.name.lowercase().contains(npcName.lowercase()) ||
                npcName.lowercase().contains(it.name.lowercase())
        } ?: false

    private fun hasDialogue(gmResponse: String, npcName: String): Boolean =
        gmResponse.contains("says:", ignoreCase = true) ||
            gmResponse.contains("says \"", ignoreCase = true) ||
            gmResponse.contains("says, \"", ignoreCase = true) ||
            gmResponse.contains("replies:", ignoreCase = true) ||
            gmResponse.contains("replies, \"", ignoreCase = true) ||
            gmResponse.matches(Regex(".*\\b${Regex.escape(npcName)}\\b.*:", RegexOption.IGNORE_CASE))

    private fun dialogueResult(
        npcName: String,
        gmResponse: String,
        npcInRoom: Boolean
    ): ValidationResult? {
        if (!hasDialogue(gmResponse, npcName) || gmResponse.contains("error", ignoreCase = true)) {
            return null
        }
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

    private fun rejectionResult(
        npcName: String,
        gmResponse: String,
        npcInRoom: Boolean
    ): ValidationResult? {
        if (npcInRoom) return null
        val rejected = gmResponse.contains("no one here", ignoreCase = true) ||
            gmResponse.contains("don't see", ignoreCase = true)
        if (!rejected) return null
        return ValidationResult(
            pass = true,
            reason = "[CODE] Correctly rejected: '$npcName' not in room",
            details = mapOf("validation_type" to "code", "npc" to npcName)
        )
    }

    private fun fallbackResult(
        npcName: String,
        gmResponse: String,
        npcInRoom: Boolean
    ): ValidationResult? {
        if (!npcInRoom || gmResponse.isBlank() || gmResponse.contains("error", ignoreCase = true)) {
            return null
        }
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
