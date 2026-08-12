@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot.validation

import com.jcraw.mud.core.RoomView
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.testbot.ValidationResult

/**
 * Social interaction code validation router (MUD-034f).
 */
internal object CodeValidationSocial {

    fun validate(
        playerInput: String,
        gmResponse: String,
        currentRoom: RoomView?,
        worldState: WorldState
    ): ValidationResult? {
        CodeValidationSocialTalk.validate(playerInput, gmResponse, currentRoom)?.let { return it }
        CodeValidationSocialChecks.validatePersuade(playerInput, gmResponse, currentRoom)?.let { return it }
        CodeValidationSocialChecks.validateIntimidate(playerInput, gmResponse, currentRoom)?.let { return it }
        return null
    }
}
