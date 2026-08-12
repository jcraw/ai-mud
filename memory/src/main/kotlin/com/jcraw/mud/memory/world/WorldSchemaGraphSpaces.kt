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
 * graph_nodes + space_properties + space_entities DDL for [WorldDatabase] (MUD-034m).
 */
internal object WorldSchemaGraphSpaces {

    fun apply(stmt: Statement) {
        applyGraphNodes(stmt)
        applySpaceProperties(stmt)
        applySpacePropertyAlters(stmt)
        applySpaceEntities(stmt)
    }

    private fun applyGraphNodes(stmt: Statement) {
        stmt.execute(
            """
                CREATE TABLE IF NOT EXISTS graph_nodes (
                    id TEXT PRIMARY KEY,
                    chunk_id TEXT NOT NULL,
                    position_x INTEGER,
                    position_y INTEGER,
                    type TEXT NOT NULL,
                    neighbors TEXT NOT NULL,
                    FOREIGN KEY (chunk_id) REFERENCES world_chunks(id)
                )
            """.trimIndent()
        )
    }

    private fun applySpaceProperties(stmt: Statement) {
        stmt.execute(
            """
                CREATE TABLE IF NOT EXISTS space_properties (
                    chunk_id TEXT PRIMARY KEY,
                    name TEXT NOT NULL DEFAULT 'Unknown Location',
                    description TEXT NOT NULL,
                    exits TEXT NOT NULL,
                    brightness INTEGER NOT NULL,
                    terrain_type TEXT NOT NULL,
                    traps TEXT NOT NULL,
                    resources TEXT NOT NULL,
                    entities TEXT NOT NULL,
                    items_dropped TEXT NOT NULL,
                    state_flags TEXT NOT NULL,
                    is_safe_zone INTEGER NOT NULL DEFAULT 0,
                    is_treasure_room INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (chunk_id) REFERENCES world_chunks(id)
                )
            """.trimIndent()
        )
    }

    private fun applySpacePropertyAlters(stmt: Statement) {
        // Add name column for existing databases
        addColumnIfMissing(
            stmt,
            "ALTER TABLE space_properties ADD COLUMN name TEXT NOT NULL DEFAULT 'Unknown Location'"
        )
        addColumnIfMissing(
            stmt,
            "ALTER TABLE space_properties ADD COLUMN is_safe_zone INTEGER NOT NULL DEFAULT 0"
        )
        addColumnIfMissing(
            stmt,
            "ALTER TABLE space_properties ADD COLUMN is_treasure_room INTEGER NOT NULL DEFAULT 0"
        )
    }

    private fun applySpaceEntities(stmt: Statement) {
        stmt.execute(
            """
                CREATE TABLE IF NOT EXISTS space_entities (
                    id TEXT PRIMARY KEY,
                    entity_type TEXT NOT NULL,
                    entity_json TEXT NOT NULL
                )
            """.trimIndent()
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
