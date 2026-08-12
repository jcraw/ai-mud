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

import java.sql.SQLException
import java.sql.Statement

/**
 * world_seed + world_chunks DDL for [WorldDatabase] (MUD-034m pure-move).
 */
internal object WorldSchemaSeedChunks {

    fun apply(stmt: Statement) {
        applySeed(stmt)
        applyChunks(stmt)
    }

    private fun applySeed(stmt: Statement) {
        stmt.execute(
            """
                CREATE TABLE IF NOT EXISTS world_seed (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    seed_string TEXT NOT NULL,
                    global_lore TEXT NOT NULL,
                    starting_space_id TEXT
                )
            """.trimIndent()
        )

        addColumnIfMissing(stmt, "ALTER TABLE world_seed ADD COLUMN starting_space_id TEXT")
    }

    private fun applyChunks(stmt: Statement) {
        stmt.execute(
            """
                CREATE TABLE IF NOT EXISTS world_chunks (
                    id TEXT PRIMARY KEY,
                    level TEXT NOT NULL,
                    parent_id TEXT,
                    children TEXT NOT NULL,
                    lore TEXT NOT NULL,
                    biome_theme TEXT NOT NULL,
                    size_estimate INTEGER NOT NULL,
                    mob_density REAL NOT NULL,
                    difficulty_level INTEGER NOT NULL,
                    adjacency TEXT NOT NULL DEFAULT '{}',
                    FOREIGN KEY (parent_id) REFERENCES world_chunks(id)
                )
            """.trimIndent()
        )

        addColumnIfMissing(
            stmt,
            "ALTER TABLE world_chunks ADD COLUMN adjacency TEXT NOT NULL DEFAULT '{}'"
        )
    }

    private fun addColumnIfMissing(stmt: Statement, sql: String) {
        try {
            stmt.execute(sql)
        } catch (e: SQLException) {
            if (!e.message.orEmpty().contains("duplicate column name")) {
                throw e
            }
        }
    }
}
