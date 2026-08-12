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
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter"
)

package com.jcraw.mud.memory.skill

import com.jcraw.mud.core.SkillState
import kotlinx.serialization.json.Json
import java.sql.ResultSet

/**
 * Shared row decode for [SQLiteSkillRepository] (MUD-034m pure-move).
 */
internal object SkillRepoMapping {

    fun skillStateFrom(rs: ResultSet, json: Json): SkillState = SkillState(
        level = rs.getInt("level"),
        xp = rs.getLong("xp"),
        unlocked = rs.getInt("unlocked") == 1,
        tags = json.decodeFromString(rs.getString("tags") ?: "[]"),
        perks = json.decodeFromString(rs.getString("perks") ?: "[]"),
        resourceType = rs.getString("resource_type"),
        tempBuffs = rs.getInt("temp_buffs")
    )
}
