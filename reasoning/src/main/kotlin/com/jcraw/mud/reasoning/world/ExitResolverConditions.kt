@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "UnusedParameter",
    "TooGenericExceptionCaught",
    "TooGenericExceptionThrown",
    "SwallowedException",
    "WildcardImport",
    "MayBeConst",
    "ImplicitDefaultLocale",
    "ForbiddenComment",
    "UnusedPrivateProperty",
)

package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.world.Condition
import com.jcraw.mud.core.world.ExitData

/**
 * Exit condition checks for [ExitResolver] (MUD-034g pure move).
 */
internal object ExitResolverConditions {

    /**
     * Checks if the player meets all conditions to use an exit
     */
    fun checkConditions(
        exit: ExitData,
        playerState: PlayerState,
        playerSkills: SkillComponent
    ): ResolveResult {
        val unmetConditions = exit.conditions.filterNot { it.meetsCondition(playerState, playerSkills) }

        return if (unmetConditions.isEmpty()) {
            ResolveResult.Success(exit, exit.targetId)
        } else {
            val conditionDescriptions = unmetConditions.joinToString(", ") { condition ->
                when (condition) {
                    is Condition.SkillCheck -> "${condition.skill} ${condition.difficulty}+"
                    is Condition.ItemRequired -> "item: ${condition.itemTag}"
                }
            }
            ResolveResult.Failure(
                "You cannot go ${exit.direction}. Required: $conditionDescriptions"
            )
        }
    }
}
