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

package com.jcraw.mud.memory.world

import java.sql.Statement

/**
 * respawn_components + corpses DDL for [WorldDatabase] (MUD-034m pure-move).
 */
internal object WorldSchemaRespawnCorpse {

    fun apply(stmt: Statement) {
        applyRespawn(stmt)
        applyCorpses(stmt)
    }

    private fun applyRespawn(stmt: Statement) {
        stmt.execute(
            """
                CREATE TABLE IF NOT EXISTS respawn_components (
                    entity_id TEXT PRIMARY KEY,
                    space_id TEXT NOT NULL,
                    respawn_turns INTEGER NOT NULL,
                    last_killed INTEGER NOT NULL,
                    original_entity_id TEXT NOT NULL
                )
            """.trimIndent()
        )
    }

    private fun applyCorpses(stmt: Statement) {
        stmt.execute(
            """
                CREATE TABLE IF NOT EXISTS corpses (
                    id TEXT PRIMARY KEY,
                    player_id TEXT NOT NULL,
                    space_id TEXT NOT NULL,
                    inventory TEXT NOT NULL,
                    equipment TEXT NOT NULL,
                    gold INTEGER NOT NULL,
                    decay_timer INTEGER NOT NULL,
                    looted INTEGER NOT NULL
                )
            """.trimIndent()
        )
    }
}
