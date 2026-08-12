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
 * treasure_rooms + pedestals + indices DDL for [WorldDatabase] (MUD-034m).
 */
internal object WorldSchemaTreasure {

    fun apply(stmt: Statement) {
        applyTreasureRooms(stmt)
        applyPedestals(stmt)
        applyIndices(stmt)
    }

    private fun applyTreasureRooms(stmt: Statement) {
        // Treasure rooms table (Brogue-style treasure selection)
        // Note: No FK constraint on space_id to allow independent repository testing
        // Application logic ensures space exists before creating treasure room
        stmt.execute(
            """
                CREATE TABLE IF NOT EXISTS treasure_rooms (
                    space_id TEXT PRIMARY KEY,
                    room_type TEXT NOT NULL,
                    biome_theme TEXT NOT NULL,
                    currently_taken_item TEXT,
                    has_been_looted INTEGER NOT NULL
                )
            """.trimIndent()
        )
    }

    private fun applyPedestals(stmt: Statement) {
        stmt.execute(
            """
                CREATE TABLE IF NOT EXISTS pedestals (
                    id TEXT PRIMARY KEY,
                    treasure_room_id TEXT NOT NULL,
                    item_template_id TEXT NOT NULL,
                    state TEXT NOT NULL,
                    pedestal_index INTEGER NOT NULL,
                    theme_description TEXT NOT NULL,
                    FOREIGN KEY (treasure_room_id) REFERENCES treasure_rooms(space_id)
                )
            """.trimIndent()
        )
    }

    private fun applyIndices(stmt: Statement) {
        // Create indices for common queries
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_chunks_parent ON world_chunks(parent_id)")
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_chunks_level ON world_chunks(level)")
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_graph_nodes_chunk ON graph_nodes(chunk_id)")
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_space_chunk ON space_properties(chunk_id)")
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_respawn_space ON respawn_components(space_id)")
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_corpse_space ON corpses(space_id)")
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_corpse_player ON corpses(player_id)")
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_pedestals_room ON pedestals(treasure_room_id)")
    }
}
