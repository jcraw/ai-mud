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

package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.config.GameConfig
import kotlin.math.floor
import kotlin.math.sqrt

/** Lucky progression chance: floor(base / sqrt(targetLevel + 1)) (MUD-034j). */
internal object SkillManagerLuckyChance {
    fun calculate(targetLevel: Int): Int {
        val baseChance = GameConfig.baseLuckyChance.toDouble()
        return floor(baseChance / sqrt(targetLevel + 1.0)).toInt()
    }
}
