@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "NestedBlockDepth",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.Direction

/**
 * Result of a flee resolution
 * Contains all information about what happened
 */
sealed class FleeResult {
    abstract val fleeingEntityId: String
    abstract val escapeSkillUsed: Boolean
    abstract val pursuitSkillsUsed: Map<String, Int> // Pursuer ID -> Pursuit skill level

    /**
     * Flee succeeded - entity escapes to target direction
     */
    data class Success(
        override val fleeingEntityId: String,
        val targetDirection: Direction,
        val fleeRoll: Int,
        val pursuitRoll: Int,
        override val escapeSkillUsed: Boolean,
        override val pursuitSkillsUsed: Map<String, Int>,
        val freeAttacks: List<AttackResult>
    ) : FleeResult() {
        val isSuccess = true
    }

    /**
     * Flee failed - entity is intercepted and takes free attack(s)
     */
    data class Failure(
        override val fleeingEntityId: String,
        val targetDirection: Direction,
        val fleeRoll: Int,
        val pursuitRoll: Int,
        override val escapeSkillUsed: Boolean,
        override val pursuitSkillsUsed: Map<String, Int>,
        val freeAttacks: List<AttackResult>,
        val interceptorId: String? // Entity that intercepted
    ) : FleeResult() {
        val isSuccess = false
    }

    /**
     * Flee failed due to error
     */
    data class Error(
        val reason: String
    ) : FleeResult() {
        override val fleeingEntityId: String = ""
        override val escapeSkillUsed: Boolean = false
        override val pursuitSkillsUsed: Map<String, Int> = emptyMap()
        val isSuccess = false
    }

    companion object {
        fun success(
            fleeingEntityId: String,
            targetDirection: Direction,
            fleeRoll: Int,
            pursuitRoll: Int,
            escapeSkillUsed: Boolean,
            pursuitSkillsUsed: Map<String, Int>,
            freeAttacks: List<AttackResult> = emptyList()
        ) = Success(
            fleeingEntityId, targetDirection, fleeRoll, pursuitRoll,
            escapeSkillUsed, pursuitSkillsUsed, freeAttacks
        )

        fun failure(
            fleeingEntityId: String,
            targetDirection: Direction,
            fleeRoll: Int,
            pursuitRoll: Int,
            escapeSkillUsed: Boolean,
            pursuitSkillsUsed: Map<String, Int>,
            freeAttacks: List<AttackResult>,
            interceptorId: String?
        ) = Failure(
            fleeingEntityId, targetDirection, fleeRoll, pursuitRoll,
            escapeSkillUsed, pursuitSkillsUsed, freeAttacks, interceptorId
        )

        fun error(reason: String) = Error(reason)
    }
}
