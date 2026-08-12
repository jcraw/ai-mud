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

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.repository.SpaceEntityRepository
import com.jcraw.mud.core.repository.TreasureRoomRepository
import com.jcraw.mud.core.repository.GraphNodeRepository
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.GenerationContext
import com.jcraw.mud.core.world.NodeType
import com.jcraw.mud.core.world.EdgeData
import com.jcraw.mud.core.world.ExitData
import com.jcraw.mud.reasoning.treasureroom.TreasureRoomPlacer

/**
 * Generates town safe zones with merchants and NPCs
 * Towns provide:
 * - Safe zone (no combat, no traps, no mob spawns)
 * - Merchant NPCs with TradingComponent
 * - Rest area for HP/Mana regen
 *
 * Thin facade — merchants extracted (MUD-034g).
 */
class TownGenerator(
    private val worldGenerator: WorldGenerator,
    private val chunkRepo: WorldChunkRepository,
    private val spaceRepo: SpacePropertiesRepository,
    private val entityRepo: SpaceEntityRepository,
    private val treasureRoomRepo: TreasureRoomRepository,
    private val graphNodeRepo: GraphNodeRepository
) {
    private val treasureRoomPlacer = TreasureRoomPlacer()
    /**
     * Generate a town subzone within a parent zone
     * Returns (subzoneId, firstSpaceId) for the generated town
     * Now includes a treasure room adjacent to the town square
     *
     * @param parentZone Parent zone chunk to attach town to
     * @param seed World seed for generation consistency
     * @param globalLore World lore for context
     * @return Result with pair of (subzoneId, townSpaceId)
     */
    suspend fun generateTownSubzone(
        parentZone: WorldChunkComponent,
        parentZoneId: String,
        seed: String,
        globalLore: String
    ): Result<Pair<String, String>> {
        // Generate town SUBZONE
        val subzoneContext = GenerationContext(
            seed = seed,
            globalLore = globalLore,
            parentChunk = parentZone,
            parentChunkId = parentZoneId,
            level = ChunkLevel.SUBZONE,
            direction = "town entrance"
        )

        val (subzoneChunk, subzoneId) = worldGenerator.generateChunk(subzoneContext)
            .getOrElse { return Result.failure(it) }

        // Override mob density to 0 for town (no spawns)
        val townSubzone = subzoneChunk.copy(
            mobDensity = 0.0,
            biomeTheme = "town",
            lore = "A safe haven within the dungeon depths. Merchants hawk their wares, " +
                   "adventurers rest, and torches flicker against the encroaching darkness."
        )
        chunkRepo.save(townSubzone, subzoneId).getOrElse { return Result.failure(it) }

        // Generate town square SPACE
        val (townSpace, townSpaceId) = worldGenerator.generateSpace(townSubzone, subzoneId)
            .getOrElse { return Result.failure(it) }

        // Generate treasure room SPACE
        val (treasureSpace, treasureSpaceId) = worldGenerator.generateSpace(townSubzone, subzoneId)
            .getOrElse { return Result.failure(it) }

        // Create treasure room with "town" biome (ancient_abyss theme)
        val treasureComponent = treasureRoomPlacer.createStarterTreasureRoomComponent("ancient_abyss")

        // Configure treasure room space
        val configuredTreasureSpace = treasureSpace.copy(
            name = "Vault of Beginnings",
            description = "An ancient vault with five ornate pedestals, each displaying a legendary treasure. " +
                         "The air shimmers with protective magic. You may claim one item, but choose wisely - " +
                         "your choice will shape your journey through the Abyss.",
            isSafeZone = true,
            isTreasureRoom = true,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList()
        )

        // Link town square to treasure room (east/west)
        val townToTreasure = ExitData(
            targetId = treasureSpaceId,
            direction = "east",
            description = "A reinforced door leads to an ancient vault. Strange symbols glow faintly on its surface.",
            conditions = emptyList(),
            isHidden = false
        )

        val treasureToTown = ExitData(
            targetId = townSpaceId,
            direction = "west",
            description = "The door leads back to the bustling town square.",
            conditions = emptyList(),
            isHidden = false
        )

        // Update spaces with exits
        val townWithExit = townSpace.copy(isSafeZone = true).addExit(townToTreasure)
        val treasureWithExit = configuredTreasureSpace.addExit(treasureToTown)

        // Populate town space with merchants
        val populatedSpace = populateTownSpace(townWithExit).getOrElse { return Result.failure(it) }

        // Save both spaces
        spaceRepo.save(populatedSpace, townSpaceId).getOrElse { return Result.failure(it) }
        spaceRepo.save(treasureWithExit, treasureSpaceId).getOrElse { return Result.failure(it) }

        // Save treasure room component
        treasureRoomRepo.save(treasureComponent, treasureSpaceId)
            .getOrElse { return Result.failure(it) }

        // Create graph nodes for both spaces
        val townNode = GraphNodeComponent(
            id = townSpaceId,
            position = null,
            type = NodeType.Hub,
            neighbors = listOf(EdgeData(treasureSpaceId, "east", false)),
            chunkId = subzoneId
        )

        val treasureNode = GraphNodeComponent(
            id = treasureSpaceId,
            position = null,
            type = NodeType.TreasureRoom,
            neighbors = listOf(EdgeData(townSpaceId, "west", false)),
            chunkId = subzoneId
        )

        graphNodeRepo.save(townNode).getOrElse { return Result.failure(it) }
        graphNodeRepo.save(treasureNode).getOrElse { return Result.failure(it) }

        // Update subzone with both children
        val updatedSubzone = townSubzone.copy(children = listOf(townSpaceId, treasureSpaceId))
        chunkRepo.save(updatedSubzone, subzoneId).getOrElse { return Result.failure(it) }

        return Result.success(subzoneId to townSpaceId)
    }

    /**
     * Populate town space with merchant NPCs
     * Creates 3-5 merchants with TradingComponent and SocialComponent
     *
     * @param spaceProps Base space properties to populate
     * @return Updated space with merchants
     */
    fun populateTownSpace(spaceProps: SpacePropertiesComponent): Result<SpacePropertiesComponent> {
        val merchants = TownGeneratorMerchants.createTownMerchants()

        // Add merchant IDs to space entities list
        val merchantIds = merchants.map { it.id }

        merchants.forEach { merchant ->
            entityRepo.save(merchant).getOrElse { return Result.failure(it) }
        }

        // Create healing fountain
        val fountain = Entity.Feature(
            id = "feature_town_fountain",
            name = "Font of Renewal",
            description = "An ancient stone fountain rises from the center of the square. " +
                "Luminescent water spills from a carved serpent's mouth into a basin etched with healing runes. " +
                "Weary adventurers often pause here to restore their strength.",
            isInteractable = true,
            properties = mapOf(
                "interaction_type" to "fountain",
                "heals_hp" to "true"
            ),
            skillChallenge = null,
            isCompleted = false,
            lootTableId = null
        )
        entityRepo.save(fountain).getOrElse { return Result.failure(it) }

        // Update space description for town (mentions fountain)
        val townDescription = """
            You stand in the Town, a safe haven carved from the dungeon depths.
            At its center, the Font of Renewal glows softly, its healing waters a beacon for weary adventurers.
            Torches flicker on stone walls, casting dancing shadows. Merchants display their wares on
            rickety stalls, calling out to passing adventurers. The air is warm and smells of bread,
            metal, and brewing potions. This is a place of respite - no danger reaches here.
        """.trimIndent()
        val populated = spaceProps
            .copy(
                description = townDescription,
                entities = spaceProps.entities + merchantIds + fountain.id,
                isSafeZone = true,
                traps = emptyList() // No traps in town
            )

        return Result.success(populated)
    }
}
