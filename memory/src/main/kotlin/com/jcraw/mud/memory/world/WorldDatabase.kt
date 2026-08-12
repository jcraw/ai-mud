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

import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite database for world generation system persistence
 * Manages schema creation and connection lifecycle
 *
 * Database schema:
 * - world_seed: Global seed and lore for world generation (singleton)
 * - world_chunks: Hierarchical world chunks (WORLD/REGION/ZONE/SUBZONE/SPACE levels)
 * - graph_nodes: V3 graph topology for pre-generated navigation structure
 * - space_properties: Detailed space properties (descriptions, exits, content)
 * - respawn_components: Mob respawn timers and regeneration state
 * - corpses: Player death corpses with inventory/equipment (Dark Souls-style)
 */
class WorldDatabase(
    private val dbPath: String = "world.db"
) {
    private var connection: Connection? = null

    /**
     * Get or create database connection
     */
    fun getConnection(): Connection {
        if (connection == null || connection?.isClosed == true) {
            connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
            connection!!.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
            initializeSchema()
        }
        return connection!!
    }

    /**
     * Close database connection
     */
    fun close() {
        connection?.close()
        connection = null
    }

    /**
     * Initialize database schema if tables don't exist
     */
    private fun initializeSchema() {
        val conn = connection ?: return

        conn.createStatement().use { stmt ->
            WorldSchemaSeedChunks.apply(stmt)
            WorldSchemaGraphSpaces.apply(stmt)
            WorldSchemaRespawnCorpse.apply(stmt)
            WorldSchemaTreasure.apply(stmt)
        }
    }

    /**
     * Clear all data from database (for testing/no backward compatibility)
     */
    fun clearAll() {
        val conn = getConnection()
        conn.createStatement().use { stmt ->
            stmt.execute("DELETE FROM pedestals")
            stmt.execute("DELETE FROM treasure_rooms")
            stmt.execute("DELETE FROM corpses")
            stmt.execute("DELETE FROM respawn_components")
            stmt.execute("DELETE FROM space_entities")
            stmt.execute("DELETE FROM space_properties")
            stmt.execute("DELETE FROM graph_nodes")
            stmt.execute("DELETE FROM world_chunks")
            stmt.execute("DELETE FROM world_seed")
        }
    }
}
