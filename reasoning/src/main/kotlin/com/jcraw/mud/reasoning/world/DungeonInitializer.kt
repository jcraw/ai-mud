package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.repository.WorldSeedRepository
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.GenerationContext

/**
 * Result data for Ancient Abyss initialization
 */
data class AncientAbyssData(
    val worldId: String,
    val townSpaceId: String,
    val regions: Map<String, String> // region name -> region ID
)

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
    private val spaceRepo: SpacePropertiesRepository,
    private val townGenerator: TownGenerator,
    private val bossGenerator: BossGenerator,
    private val hiddenExitPlacer: HiddenExitPlacer
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
            parentChunkId = null,
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
                parentChunkId = worldId,
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
            parentChunkId = regionId,
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
            parentChunkId = zoneId,
            level = ChunkLevel.SUBZONE,
            direction = "entrance hall"
        )
        val (subzoneChunk, subzoneId) = worldGenerator.generateChunk(subzoneContext).getOrElse { return Result.failure(it) }
        chunkRepo.save(subzoneChunk, subzoneId).getOrElse { return Result.failure(it) }

        // Update zone with first child
        val updatedZone = zoneChunk.copy(children = listOf(subzoneId))
        chunkRepo.save(updatedZone, zoneId).getOrElse { return Result.failure(it) }

        // Generate starting SPACE
        val (space, spaceId) = worldGenerator.generateSpace(subzoneChunk, subzoneId).getOrElse { return Result.failure(it) }
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

    /**
     * Initialize the Ancient Abyss Dungeon with fixed 4-region structure
     * Pre-generates Upper/Mid/Lower Depths, lazy-generates Abyssal Core
     * Includes town safe zone, boss lair, and hidden exit to open world
     *
     * @param seed World seed for generation consistency
     * @return Result with Ancient Abyss initialization data
     */
    suspend fun initializeAncientAbyss(seed: String = "dark fantasy DnD"): Result<AncientAbyssData> {
        val globalLore = """
            The Ancient Abyss is a vast vertical dungeon complex, plunging deep beneath a forgotten kingdom.
            Once a grand fortress, it has been corrupted by dark magic and now hosts countless monsters and treasures.
            Adventurers descend level by level, seeking glory and riches in the ever-deepening darkness.
            At its heart lies the Abyssal Lord, an ancient demon who guards the legendary Abyss Heart.
        """.trimIndent()

        // Save world seed
        worldSeedRepo.save(seed, globalLore).getOrElse { return Result.failure(it) }

        // Generate WORLD level
        val worldContext = GenerationContext(
            seed = seed,
            globalLore = globalLore,
            parentChunk = null,
            parentChunkId = null,
            level = ChunkLevel.WORLD
        )
        val (worldChunk, worldId) = worldGenerator.generateChunk(worldContext)
            .getOrElse { return Result.failure(it) }
        chunkRepo.save(worldChunk, worldId).getOrElse { return Result.failure(it) }

        // Generate 4 REGION levels (Upper, Mid, Lower Depths, Abyssal Core)
        val regions = listOf(
            RegionSpec("Upper Depths", "floors 1-10", 5),
            RegionSpec("Mid Depths", "floors 10-30", 15),
            RegionSpec("Lower Depths", "floors 30-60", 40),
            RegionSpec("Abyssal Core", "floors 60+", 70)
        )

        val regionMap = mutableMapOf<String, String>()

        for ((index, regionSpec) in regions.withIndex()) {
            val regionContext = GenerationContext(
                seed = seed,
                globalLore = globalLore,
                parentChunk = worldChunk.copy(
                    lore = "${worldChunk.lore}\n\nRegion: ${regionSpec.name} (${regionSpec.description})"
                ),
                parentChunkId = worldId,
                level = ChunkLevel.REGION,
                direction = "down"
            )
            val (regionChunk, regionId) = worldGenerator.generateChunk(regionContext)
                .getOrElse { return Result.failure(it) }

            // Override difficulty for region spec
            val adjustedRegion = regionChunk.copy(difficultyLevel = regionSpec.difficulty)
            chunkRepo.save(adjustedRegion, regionId).getOrElse { return Result.failure(it) }
            regionMap[regionSpec.name] = regionId

            // Update world chunk with region child
            val updatedWorld = chunkRepo.findById(worldId).getOrElse { return Result.failure(it) }
                ?: return Result.failure(Exception("World not found during region generation"))
            chunkRepo.save(updatedWorld.addChild(regionId), worldId)
                .getOrElse { return Result.failure(it) }
        }

        // Generate town in Upper Depths (Zone 1, Subzone 1)
        val townSpaceId = generateTownInUpperDepths(
            seed,
            globalLore,
            regionMap["Upper Depths"] ?: return Result.failure(Exception("Upper Depths region not found"))
        ).getOrElse { return Result.failure(it) }

        // Generate boss lair in Abyssal Core
        generateBossLairInAbyssalCore(
            seed,
            globalLore,
            regionMap["Abyssal Core"] ?: return Result.failure(Exception("Abyssal Core region not found"))
        ).getOrElse { return Result.failure(it) }

        // Place hidden exit in Mid Depths
        hiddenExitPlacer.placeHiddenExit(
            regionMap["Mid Depths"] ?: return Result.failure(Exception("Mid Depths region not found")),
            seed,
            globalLore
        ).getOrElse { return Result.failure(it) }

        return Result.success(
            AncientAbyssData(
                worldId = worldId,
                townSpaceId = townSpaceId,
                regions = regionMap
            )
        )
    }

    /**
     * Generate town in Upper Depths region (Zone 1, Subzone 1)
     * Town is the player's starting location and safe zone
     *
     * @param seed World seed
     * @param globalLore World lore
     * @param upperDepthsId Upper Depths region ID
     * @return Result with town space ID
     */
    private suspend fun generateTownInUpperDepths(
        seed: String,
        globalLore: String,
        upperDepthsId: String
    ): Result<String> {
        // Load Upper Depths region
        val upperDepthsRegion = chunkRepo.findById(upperDepthsId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Upper Depths region not found"))

        // Generate Zone 1
        val zoneContext = GenerationContext(
            seed = seed,
            globalLore = globalLore,
            parentChunk = upperDepthsRegion,
            parentChunkId = upperDepthsId,
            level = ChunkLevel.ZONE,
            direction = "town entrance"
        )
        val (zoneChunk, zoneId) = worldGenerator.generateChunk(zoneContext)
            .getOrElse { return Result.failure(it) }
        chunkRepo.save(zoneChunk, zoneId).getOrElse { return Result.failure(it) }

        // Update region with zone
        val updatedRegion = upperDepthsRegion.copy(children = listOf(zoneId))
        chunkRepo.save(updatedRegion, upperDepthsId).getOrElse { return Result.failure(it) }

        // Generate town subzone using TownGenerator
        val (townSubzoneId, townSpaceId) = townGenerator.generateTownSubzone(
            zoneChunk,
            zoneId,
            seed,
            globalLore
        ).getOrElse { return Result.failure(it) }

        // Update zone with town subzone
        val updatedZone = zoneChunk.copy(children = listOf(townSubzoneId))
        chunkRepo.save(updatedZone, zoneId).getOrElse { return Result.failure(it) }

        return Result.success(townSpaceId)
    }

    /**
     * Generate boss lair in Abyssal Core region
     * Boss lair contains the Abyssal Lord and Abyss Heart
     *
     * @param seed World seed
     * @param globalLore World lore
     * @param abyssalCoreId Abyssal Core region ID
     * @return Result with boss lair space ID
     */
    private suspend fun generateBossLairInAbyssalCore(
        seed: String,
        globalLore: String,
        abyssalCoreId: String
    ): Result<String> {
        // Load Abyssal Core region
        val abyssalCoreRegion = chunkRepo.findById(abyssalCoreId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Abyssal Core region not found"))

        // Generate final zone
        val zoneContext = GenerationContext(
            seed = seed,
            globalLore = globalLore,
            parentChunk = abyssalCoreRegion,
            parentChunkId = abyssalCoreId,
            level = ChunkLevel.ZONE,
            direction = "abyssal depths"
        )
        val (zoneChunk, zoneId) = worldGenerator.generateChunk(zoneContext)
            .getOrElse { return Result.failure(it) }
        chunkRepo.save(zoneChunk, zoneId).getOrElse { return Result.failure(it) }

        // Update region with zone
        val updatedRegion = abyssalCoreRegion.copy(children = listOf(zoneId))
        chunkRepo.save(updatedRegion, abyssalCoreId).getOrElse { return Result.failure(it) }

        // Generate boss lair subzone
        val subzoneContext = GenerationContext(
            seed = seed,
            globalLore = globalLore,
            parentChunk = zoneChunk,
            parentChunkId = zoneId,
            level = ChunkLevel.SUBZONE,
            direction = "throne room"
        )
        val (subzoneChunk, subzoneId) = worldGenerator.generateChunk(subzoneContext)
            .getOrElse { return Result.failure(it) }
        chunkRepo.save(subzoneChunk, subzoneId).getOrElse { return Result.failure(it) }

        // Update zone with subzone
        val updatedZone = zoneChunk.copy(children = listOf(subzoneId))
        chunkRepo.save(updatedZone, zoneId).getOrElse { return Result.failure(it) }

        // Generate boss space using BossGenerator
        val bossSpaceId = bossGenerator.generateAbyssalLordSpace(subzoneChunk)
            .getOrElse { return Result.failure(it) }

        // Update subzone with boss space
        val updatedSubzone = subzoneChunk.copy(children = listOf(bossSpaceId))
        chunkRepo.save(updatedSubzone, subzoneId).getOrElse { return Result.failure(it) }

        return Result.success(bossSpaceId)
    }
}
