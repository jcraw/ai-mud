package com.jcraw.mud.core.world

import com.jcraw.mud.core.WorldChunkComponent

/**
 * Context for LLM-driven world generation.
 *
 * Encapsulates all parameters needed to generate a world chunk with lore inheritance.
 * Direction hint enables spatial coherence (e.g., "north" chunks are colder if parent is snowy).
 *
 * @property seed Global world seed for deterministic generation hints
 * @property globalLore World-level lore (politics, factions, overall theme)
 * @property parentChunk Parent chunk for lore inheritance (null for WORLD level)
 * @property parentChunkId Parent chunk entity ID for hierarchical ID generation (null for WORLD level)
 * @property level Chunk level being generated (WORLD, REGION, ZONE, SUBZONE, SPACE)
 * @property direction Optional spatial hint relative to parent ("north", "down", etc.)
 */
data class GenerationContext(
    val seed: String,
    val globalLore: String,
    val parentChunk: WorldChunkComponent?,
    val parentChunkId: String?,
    val level: ChunkLevel,
    val direction: String? = null
)
