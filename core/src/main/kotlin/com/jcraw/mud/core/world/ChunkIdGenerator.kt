package com.jcraw.mud.core.world

import com.jcraw.mud.core.world.ChunkLevel
import java.util.UUID

/**
 * Generates human-readable, hierarchical IDs for world chunks.
 *
 * ID format: `{level}_{parentId}_{uuid}` (e.g., "SPACE_subzone123_a1b2c3d4")
 * Special case: WORLD level uses "WORLD_root"
 *
 * Readable IDs aid debugging and make DB queries easier (can filter by level prefix).
 */
object ChunkIdGenerator {
    /**
     * Generates a unique chunk ID with hierarchical structure.
     *
     * @param level The chunk level being generated
     * @param parentId Parent chunk ID (null for WORLD level)
     * @return Unique chunk ID with format: level_parent_uuid
     */
    fun generate(level: ChunkLevel, parentId: String?): String {
        return when (level) {
            ChunkLevel.WORLD -> "WORLD_root"
            else -> {
                val parent = parentId ?: "orphan"
                val uuid = UUID.randomUUID().toString().take(8)
                "${level.name}_${parent}_$uuid"
            }
        }
    }

    /**
     * Extracts chunk level from ID string.
     *
     * @param id Chunk ID to parse
     * @return ChunkLevel if parse successful, null if invalid format
     */
    fun parse(id: String): ChunkLevel? {
        val parts = id.split("_")
        if (parts.isEmpty()) return null

        val levelName = parts[0]
        return try {
            ChunkLevel.valueOf(levelName)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Extracts parent ID from chunk ID.
     *
     * @param id Chunk ID to parse
     * @return Parent ID if present, null for WORLD level or invalid format
     */
    fun extractParentId(id: String): String? {
        if (id == "WORLD_root") return null

        val parts = id.split("_")
        if (parts.size < 3) return null

        // Parent ID is everything between level and final UUID
        return parts.subList(1, parts.size - 1).joinToString("_")
    }
}
