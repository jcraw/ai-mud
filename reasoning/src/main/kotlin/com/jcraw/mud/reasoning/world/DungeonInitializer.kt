package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.repository.WorldSeedRepository
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.GenerationContext

/**
 * Initializes the V2 MVP deep dungeon structure.
 *
 * Creates a predictable starting hierarchy:
 * - WORLD: "Ancient Abyss Dungeon"
 * - REGIONS: Upper Depths (1-10), Mid Depths (11-50), Lower Depths (51-100+)
 * - Pre-generates first ZONE/SUBZONE/SPACE for immediate player start
 */
class DungeonInitializer(
    private val worldGenerator: WorldGenerator,
    private val worldSeedRepo: WorldSeedRepository,
    private val chunkRepo: WorldChunkRepository,
    private val spaceRepo: SpacePropertiesRepository
) {
    /**
     * Initializes a new deep dungeon world.
     *
     * @param seed World seed for generation (used for lore consistency)
     * @return Result with starting space ID
     */
    suspend fun initializeDeepDungeon(seed: String): Result<String> {
        val globalLore = """
            The Ancient Abyss is a vast vertical dungeon complex, plunging deep beneath a forgotten kingdom.
            Once a grand fortress, it has been corrupted by dark magic and now hosts countless monsters and treasures.
            Adventurers descend level by level, seeking glory and riches in the ever-deepening darkness.
            The deeper you go, the more dangerous and rewarding the challenges become.
        """.trimIndent()

        // Save world seed
        worldSeedRepo.save(seed, globalLore).getOrElse { return Result.failure(it) }

        // Generate WORLD level
        val worldContext = GenerationContext(
            seed = seed,
            globalLore = globalLore,
            parentChunk = null,
            level = ChunkLevel.WORLD
        )
        val (worldChunk, worldId) = worldGenerator.generateChunk(worldContext).getOrElse { return Result.failure(it) }
        chunkRepo.save(worldChunk, worldId).getOrElse { return Result.failure(it) }

        // Generate 3 REGION levels (Upper, Mid, Lower Depths)
        val regions = listOf(
            RegionSpec("Upper Depths", "floors 1-10", 5),
            RegionSpec("Mid Depths", "floors 11-50", 12),
            RegionSpec("Lower Depths", "floors 51-100+", 18)
        )

        val regionIds = mutableListOf<String>()
        for (regionSpec in regions) {
            val regionContext = GenerationContext(
                seed = seed,
                globalLore = globalLore,
                parentChunk = worldChunk.copy(
                    lore = "${worldChunk.lore}\n\nRegion: ${regionSpec.name} (${regionSpec.description})"
                ),
                level = ChunkLevel.REGION,
                direction = "down"
            )
            val (regionChunk, regionId) = worldGenerator.generateChunk(regionContext).getOrElse { return Result.failure(it) }

            // Override difficulty for region spec
            val adjustedRegion = regionChunk.copy(difficultyLevel = regionSpec.difficulty)
            chunkRepo.save(adjustedRegion, regionId).getOrElse { return Result.failure(it) }
            regionIds.add(regionId)
        }

        // Update world chunk with children
        val updatedWorld = worldChunk.copy(children = regionIds)
        chunkRepo.save(updatedWorld, worldId).getOrElse { return Result.failure(it) }

        // Pre-generate starting location (first region -> zone -> subzone -> space)
        val startingSpaceId = generateStartingLocation(
            seed,
            globalLore,
            regionIds.first()
        ).getOrElse { return Result.failure(it) }

        return Result.success(startingSpaceId)
    }

    /**
     * Generates the starting location (ZONE → SUBZONE → SPACE) for player spawn.
     */
    private suspend fun generateStartingLocation(
        seed: String,
        globalLore: String,
        regionId: String
    ): Result<String> {
        // Load region
        val regionChunk = chunkRepo.findById(regionId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Region not found: $regionId"))

        // Generate starting ZONE
        val zoneContext = GenerationContext(
            seed = seed,
            globalLore = globalLore,
            parentChunk = regionChunk,
            level = ChunkLevel.ZONE,
            direction = "entrance"
        )
        val (zoneChunk, zoneId) = worldGenerator.generateChunk(zoneContext).getOrElse { return Result.failure(it) }
        chunkRepo.save(zoneChunk, zoneId).getOrElse { return Result.failure(it) }

        // Update region with first child
        val updatedRegion = regionChunk.copy(children = listOf(zoneId))
        chunkRepo.save(updatedRegion, regionId).getOrElse { return Result.failure(it) }

        // Generate starting SUBZONE
        val subzoneContext = GenerationContext(
            seed = seed,
            globalLore = globalLore,
            parentChunk = zoneChunk,
            level = ChunkLevel.SUBZONE,
            direction = "entrance hall"
        )
        val (subzoneChunk, subzoneId) = worldGenerator.generateChunk(subzoneContext).getOrElse { return Result.failure(it) }
        chunkRepo.save(subzoneChunk, subzoneId).getOrElse { return Result.failure(it) }

        // Update zone with first child
        val updatedZone = zoneChunk.copy(children = listOf(subzoneId))
        chunkRepo.save(updatedZone, zoneId).getOrElse { return Result.failure(it) }

        // Generate starting SPACE
        val (space, spaceId) = worldGenerator.generateSpace(subzoneChunk).getOrElse { return Result.failure(it) }
        spaceRepo.save(space, spaceId).getOrElse { return Result.failure(it) }

        // Update subzone with first child
        val updatedSubzone = subzoneChunk.copy(children = listOf(spaceId))
        chunkRepo.save(updatedSubzone, subzoneId).getOrElse { return Result.failure(it) }

        return Result.success(spaceId)
    }

    private data class RegionSpec(
        val name: String,
        val description: String,
        val difficulty: Int
    )
}
