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

import com.jcraw.mud.core.world.Condition
import com.jcraw.mud.core.world.ExitData

/**
 * Hidden exit ExitData factories (MUD-034g pure move).
 * One factory per exit so FN_E stays under 250.
 */
internal object HiddenExitPlacerExits {

    fun perceptionExit(surfaceWildernessId: String): ExitData {
        return ExitData(
            targetId = surfaceWildernessId,
            direction = "cracked wall",
            description = "A faint crack in the eastern wall reveals starlight beyond. Fresh air drifts through.",
            conditions = listOf(
                Condition.SkillCheck("Perception", 40)
            ),
            isHidden = true,
            hiddenDifficulty = 40
        )
    }

    fun lockpickExit(surfaceWildernessId: String): ExitData {
        return ExitData(
            targetId = surfaceWildernessId,
            direction = "hidden door",
            description = "A cleverly concealed door with a complex lock mechanism. It seems to lead upward.",
            conditions = listOf(
                Condition.SkillCheck("Lockpicking", 30)
            ),
            isHidden = true,
            hiddenDifficulty = 35
        )
    }

    fun strengthExit(surfaceWildernessId: String): ExitData {
        return ExitData(
            targetId = surfaceWildernessId,
            direction = "weak wall",
            description = "This section of wall looks structurally weak. With enough force, it might break.",
            conditions = listOf(
                Condition.SkillCheck("Strength", 50)
            ),
            isHidden = true,
            hiddenDifficulty = 45
        )
    }

    fun buildHiddenExits(surfaceWildernessId: String): List<ExitData> {
        return listOf(
            perceptionExit(surfaceWildernessId),
            lockpickExit(surfaceWildernessId),
            strengthExit(surfaceWildernessId)
        )
    }
}
