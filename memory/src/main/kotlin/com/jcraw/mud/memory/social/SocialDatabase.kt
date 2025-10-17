package com.jcraw.mud.memory.social

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * SQLite database for social system persistence
 * Manages schema creation and connection lifecycle
 */
class SocialDatabase(
    private val dbPath: String = "social.db"
) {
    private var connection: Connection? = null

    /**
     * Get or create database connection
     */
    fun getConnection(): Connection {
        if (connection == null || connection?.isClosed == true) {
            connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
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
            // Knowledge entries table
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS knowledge_entries (
                    id TEXT PRIMARY KEY,
                    npc_id TEXT NOT NULL,
                    content TEXT NOT NULL,
                    category TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    is_canon INTEGER DEFAULT 1,
                    tags TEXT DEFAULT ''
                )
                """.trimIndent()
            )

            // Social event history table
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS social_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    npc_id TEXT NOT NULL,
                    event_type TEXT NOT NULL,
                    disposition_delta INTEGER NOT NULL,
                    description TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    metadata TEXT
                )
                """.trimIndent()
            )

            // Social component state table
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS social_components (
                    npc_id TEXT PRIMARY KEY,
                    disposition INTEGER NOT NULL,
                    personality TEXT NOT NULL,
                    traits TEXT NOT NULL,
                    conversation_count INTEGER NOT NULL,
                    last_interaction_time INTEGER NOT NULL
                )
                """.trimIndent()
            )

            // Create indices for common queries
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_knowledge_npc ON knowledge_entries(npc_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_npc ON social_events(npc_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_timestamp ON social_events(timestamp)")
        }
    }

    /**
     * Clear all data from database (for testing)
     */
    fun clearAll() {
        val conn = getConnection()
        conn.createStatement().use { stmt ->
            stmt.execute("DELETE FROM knowledge_entries")
            stmt.execute("DELETE FROM social_events")
            stmt.execute("DELETE FROM social_components")
        }
    }
}
