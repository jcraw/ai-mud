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
    "UnusedParameter",
    "TooGenericExceptionCaught",
    "TooGenericExceptionThrown",
    "SwallowedException",
    "WildcardImport",
    "MayBeConst",
    "ImplicitDefaultLocale",
    "ForbiddenComment",
    "UnusedPrivateProperty",
)

package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.repository.WorldSeedRepository
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.GenerationContext
import com.jcraw.mud.core.repository.TreasureRoomRepository
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomPlacer

/**
 * Initializes the V2 MVP deep dungeon structure.
 *
 * Thin facade with residual stage orchestrators (MUD-034g).
 * Helpers live in DungeonInitializer* extracts (Added FN ≤250).
 */
class DungeonInitializer(
    private val worldGenerator: WorldGenerator,
    private val worldSeedRepo: WorldSeedRepository,
    private val chunkRepo: WorldChunkRepository,
    private val spaceRepo: SpacePropertiesRepository,
    private val townGenerator: TownGenerator,
    private val bossGenerator: BossGenerator,
    private val hiddenExitPlacer: HiddenExitPlacer,
    private val graphNodeRepo: com.jcraw.mud.core.repository.GraphNodeRepository,
    private val treasureRoomRepository: TreasureRoomRepository
): DungeonInitializerContract {

    private val treasureRoomPlacer = TreasureRoomPlacer()

    override suspend fun initializeDeepDungeon(seed: String): Result<String> {
        val globalLore = """
            The Ancient Abyss is a vast vertical dungeon complex, plunging deep beneath a forgotten kingdom.
            Once a grand fortress, it has been corrupted by dark magic and now hosts countless monsters and treasures.
            Adventurers descend level by level, seeking glory and riches in the ever-deepening darkness.
            The deeper you go, the more dangerous and rewarding the challenges become.
        """.trimIndent()

        worldSeedRepo.save(seed, globalLore, null).getOrElse { return Result.failure(it) }

        val worldContext = GenerationContext(
            seed = seed, globalLore = globalLore, parentChunk = null,
            parentChunkId = null, level = ChunkLevel.WORLD
        )
        val (worldChunk, worldId) = worldGenerator.generateChunk(worldContext).getOrElse { return Result.failure(it) }
        chunkRepo.save(worldChunk, worldId).getOrElse { return Result.failure(it) }

        val regions = listOf(
            RegionSpec("Training Grounds", "entrance level", 1, "training grounds"),
            RegionSpec("Upper Depths", "floors 1-10", 5),
            RegionSpec("Mid Depths", "floors 11-50", 12),
            RegionSpec("Lower Depths", "floors 51-100+", 18)
        )

        val regionIds = mutableListOf<String>()
        for (regionSpec in regions) {
            val regionContext = GenerationContext(
                seed = seed, globalLore = globalLore,
                parentChunk = worldChunk.copy(
                    lore = "${worldChunk.lore}\n\nRegion: ${regionSpec.name} (${regionSpec.description})"
                ),
                parentChunkId = worldId, level = ChunkLevel.REGION,
                direction = "down", biomeTheme = regionSpec.theme
            )
            val (regionChunk, regionId) = worldGenerator.generateChunk(regionContext)
                .getOrElse { return Result.failure(it) }
            chunkRepo.save(regionChunk.copy(difficultyLevel = regionSpec.difficulty), regionId)
                .getOrElse { return Result.failure(it) }
            regionIds.add(regionId)
        }

        chunkRepo.save(worldChunk.copy(children = regionIds), worldId).getOrElse { return Result.failure(it) }

        val startingSpaceId = DungeonInitializerStart.generateStartingLocation(
            worldGenerator, chunkRepo, spaceRepo, seed, globalLore, regionIds.first()
        ).getOrElse { return Result.failure(it) }

        worldSeedRepo.save(seed, globalLore, startingSpaceId).getOrElse { return Result.failure(it) }
        return Result.success(startingSpaceId)
    }

    suspend fun initializeAncientAbyss(seed: String = "dark fantasy DnD"): Result<AncientAbyssData> {
        val globalLore = """
            The Ancient Abyss is a vast vertical dungeon complex, plunging deep beneath a forgotten kingdom.
            Once a grand fortress, it has been corrupted by dark magic and now hosts countless monsters and treasures.
            Adventurers descend level by level, seeking glory and riches in the ever-deepening darkness.
            At its heart lies the Abyssal Lord, an ancient demon who guards the legendary Abyss Heart.
        """.trimIndent()

        worldSeedRepo.save(seed, globalLore, null).getOrElse { return Result.failure(it) }

        val worldContext = GenerationContext(
            seed = seed, globalLore = globalLore, parentChunk = null,
            parentChunkId = null, level = ChunkLevel.WORLD
        )
        val (worldChunk, worldId) = worldGenerator.generateChunk(worldContext)
            .getOrElse { return Result.failure(it) }
        chunkRepo.save(worldChunk, worldId).getOrElse { return Result.failure(it) }

        val regions = listOf(
            RegionSpec("Training Grounds", "entrance level", 1, "training grounds"),
            RegionSpec("Upper Depths", "floors 1-10", 5),
            RegionSpec("Mid Depths", "floors 10-30", 15),
            RegionSpec("Lower Depths", "floors 30-60", 40),
            RegionSpec("Abyssal Core", "floors 60+", 70)
        )

        val regionMap = mutableMapOf<String, String>()
        for ((_, regionSpec) in regions.withIndex()) {
            val regionContext = GenerationContext(
                seed = seed, globalLore = globalLore,
                parentChunk = worldChunk.copy(
                    lore = "${worldChunk.lore}\n\nRegion: ${regionSpec.name} (${regionSpec.description})"
                ),
                parentChunkId = worldId, level = ChunkLevel.REGION,
                direction = "down", biomeTheme = regionSpec.theme
            )
            val (regionChunk, regionId) = worldGenerator.generateChunk(regionContext)
                .getOrElse { return Result.failure(it) }
            chunkRepo.save(regionChunk.copy(difficultyLevel = regionSpec.difficulty), regionId)
                .getOrElse { return Result.failure(it) }
            regionMap[regionSpec.name] = regionId

            val updatedWorld = chunkRepo.findById(worldId).getOrElse { return Result.failure(it) }
                ?: return Result.failure(Exception("World not found during region generation"))
            chunkRepo.save(updatedWorld.addChild(regionId), worldId)
                .getOrElse { return Result.failure(it) }
        }

        val townSpaceId = generateTownInUpperDepths(
            seed, globalLore,
            regionMap["Training Grounds"] ?: return Result.failure(Exception("Training Grounds region not found"))
        ).getOrElse { return Result.failure(it) }

        generateBossLairInAbyssalCore(
            seed, globalLore,
            regionMap["Abyssal Core"] ?: return Result.failure(Exception("Abyssal Core region not found"))
        ).getOrElse { return Result.failure(it) }

        hiddenExitPlacer.placeHiddenExit(
            regionMap["Mid Depths"] ?: return Result.failure(Exception("Mid Depths region not found")),
            seed, globalLore
        ).getOrElse { return Result.failure(it) }

        worldSeedRepo.save(seed, globalLore, townSpaceId).getOrElse { return Result.failure(it) }

        return Result.success(AncientAbyssData(worldId, townSpaceId, regionMap))
    }

    private suspend fun generateTownInUpperDepths(
        seed: String,
        globalLore: String,
        upperDepthsId: String
    ): Result<String> {
        val upperDepthsRegion = chunkRepo.findById(upperDepthsId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Upper Depths region not found"))

        val (zoneChunk, zoneId) = DungeonInitializerTown.generateTownZone(
            worldGenerator, chunkRepo, seed, globalLore, upperDepthsId, upperDepthsRegion
        ).getOrElse { return Result.failure(it) }

        val (townSubzoneId, townSpaceId) = townGenerator.generateTownSubzone(
            zoneChunk, zoneId, seed, globalLore
        ).getOrElse { return Result.failure(it) }

        val combatResult = generateFirstCombatSubzone(seed, globalLore, zoneChunk, zoneId)
            .getOrElse { return Result.failure(it) }

        linkTownToDungeon(
            townSpaceId, townSubzoneId, combatResult.entranceSpaceId, combatResult.subzoneId
        ).getOrElse { return Result.failure(it) }

        chunkRepo.save(
            zoneChunk.copy(children = listOf(townSubzoneId, combatResult.subzoneId)), zoneId
        ).getOrElse { return Result.failure(it) }

        return Result.success(townSpaceId)
    }

    private suspend fun generateFirstCombatSubzone(
        seed: String,
        globalLore: String,
        zoneChunk: WorldChunkComponent,
        zoneId: String
    ): Result<CombatSubzoneResult> {
        val subzoneContext = DungeonInitializerCombat.combatSubzoneContext(
            seed, globalLore, zoneChunk, zoneId
        )
        val chunkResult = worldGenerator.generateChunk(subzoneContext)
            .getOrElse { return Result.failure(it) }
        val subzoneChunk = chunkResult.chunk
        val subzoneId = chunkResult.chunkId
        chunkRepo.save(subzoneChunk, subzoneId).getOrElse { return Result.failure(it) }

        if (chunkResult.graphNodes.isEmpty()) {
            return DungeonInitializerCombat.generateFallbackCombatSpace(
                worldGenerator, chunkRepo, spaceRepo, graphNodeRepo, subzoneChunk, subzoneId
            )
        }
        return DungeonInitializerCombat.materializeGraphCombatSpaces(
            worldGenerator, chunkRepo, spaceRepo, graphNodeRepo,
            subzoneChunk, subzoneId, chunkResult.graphNodes
        )
    }

    private fun linkTownToDungeon(
        townSpaceId: String,
        townSubzoneId: String,
        combatEntranceId: String,
        combatSubzoneId: String
    ): Result<Unit> {
        val townSpace = spaceRepo.findByChunkId(townSpaceId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Town space not found: $townSpaceId"))
        val combatSpace = spaceRepo.findByChunkId(combatEntranceId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Combat space not found: $combatEntranceId"))

        val (updatedTown, updatedCombat) = DungeonInitializerLink.applySpaceExits(
            townSpace, combatSpace, townSpaceId, combatEntranceId
        )
        spaceRepo.save(updatedTown, townSpaceId).getOrElse { return Result.failure(it) }
        spaceRepo.save(updatedCombat, combatEntranceId).getOrElse { return Result.failure(it) }

        DungeonInitializerLink.upsertTownGraphNode(
            graphNodeRepo, townSpaceId, townSubzoneId, combatEntranceId
        ).getOrElse { return Result.failure(it) }
        return DungeonInitializerLink.updateCombatGraphNode(
            graphNodeRepo, townSpaceId, combatEntranceId
        )
    }

    private suspend fun generateBossLairInAbyssalCore(
        seed: String,
        globalLore: String,
        abyssalCoreId: String
    ): Result<String> {
        val abyssalCoreRegion = chunkRepo.findById(abyssalCoreId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Abyssal Core region not found"))

        val (zoneChunk, zoneId) = DungeonInitializerBoss.generateBossZone(
            worldGenerator, chunkRepo, seed, globalLore, abyssalCoreId, abyssalCoreRegion
        ).getOrElse { return Result.failure(it) }

        val (subzoneChunk, subzoneId) = DungeonInitializerBoss.generateBossSubzone(
            worldGenerator, chunkRepo, seed, globalLore, zoneId, zoneChunk
        ).getOrElse { return Result.failure(it) }

        val bossSpaceId = bossGenerator.generateAbyssalLordSpace(subzoneChunk)
            .getOrElse { return Result.failure(it) }

        chunkRepo.save(subzoneChunk.copy(children = listOf(bossSpaceId)), subzoneId)
            .getOrElse { return Result.failure(it) }
        return Result.success(bossSpaceId)
    }
}
