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
import java.util.UUID

/**
 * Generates town safe zones with merchants and NPCs
 * Towns provide:
 * - Safe zone (no combat, no traps, no mob spawns)
 * - Merchant NPCs with TradingComponent
 * - Rest area for HP/Mana regen
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
        val merchants = createTownMerchants()

        // Add merchant IDs to space entities list
        val merchantIds = merchants.map { it.id }

        merchants.forEach { merchant ->
            entityRepo.save(merchant).getOrElse { return Result.failure(it) }
        }

        // Update space description for town
        val townDescription = """
            You stand in the Town, a safe haven carved from the dungeon depths.
            Torches flicker on stone walls, casting dancing shadows. Merchants display their wares on
            rickety stalls, calling out to passing adventurers. The air is warm and smells of bread,
            metal, and brewing potions. Weary travelers rest on benches, sharing tales of the depths below.
            This is a place of respite - no danger reaches here.
        """.trimIndent()
        val populated = spaceProps
            .copy(
                description = townDescription,
                entities = spaceProps.entities + merchantIds,
                isSafeZone = true,
                traps = emptyList() // No traps in town
            )

        return Result.success(populated)
    }

    /**
     * Create predefined merchant NPCs for town
     * Returns list of 3-5 merchants with stock and trading abilities
     */
    private fun createTownMerchants(): List<Entity.NPC> {
        return listOf(
            createPotionsMerchant(),
            createArmorMerchant(),
            createBlacksmith(),
            createGeneralStore()
        )
    }

    /**
     * Create potions merchant NPC
     * Sells healing potions, mana potions, and consumables
     */
    private fun createPotionsMerchant(): Entity.NPC {
        val stock = listOf(
            // Health potions (COMMON)
            createItemInstance("health_potion_minor", quality = 5, quantity = 10),
            createItemInstance("health_potion_moderate", quality = 5, quantity = 5),
            // Mana potions (UNCOMMON)
            createItemInstance("mana_potion_minor", quality = 5, quantity = 8),
            createItemInstance("mana_potion_moderate", quality = 5, quantity = 3)
        )

        val tradingComponent = TradingComponent(
            merchantGold = 500,
            stock = stock,
            buyAnything = false, // Only buys potions back
            priceModBase = 1.0
        )

        val socialComponent = SocialComponent(
            disposition = 0, // NEUTRAL initially
            personality = "friendly alchemist",
            traits = listOf("helpful", "knowledgeable", "patient")
        )

        return Entity.NPC(
            id = "npc_town_potions_merchant",
            name = "Alara the Alchemist",
            description = "A middle-aged woman with stained robes and kind eyes. Her stall overflows with colorful vials.",
            isHostile = false,
            health = 50,
            maxHealth = 50,
            stats = Stats(intelligence = 14, wisdom = 12),
            components = mapOf(
                ComponentType.TRADING to tradingComponent,
                ComponentType.SOCIAL to socialComponent
            )
        )
    }

    /**
     * Create armor merchant NPC
     * Sells various armor pieces for defense
     */
    private fun createArmorMerchant(): Entity.NPC {
        val stock = listOf(
            // Light armor (COMMON)
            createItemInstance("leather_helmet", quality = 5, quantity = 3),
            createItemInstance("leather_chest", quality = 5, quantity = 3),
            // Medium armor (UNCOMMON)
            createItemInstance("chainmail_chest", quality = 5, quantity = 2),
            createItemInstance("chainmail_legs", quality = 5, quantity = 2)
        )

        val tradingComponent = TradingComponent(
            merchantGold = 1000,
            stock = stock,
            buyAnything = true, // Buys armor and weapons
            priceModBase = 1.2 // 20% markup
        )

        val socialComponent = SocialComponent(
            disposition = 0,
            personality = "gruff merchant",
            traits = listOf("practical", "honest", "businesslike")
        )

        return Entity.NPC(
            id = "npc_town_armor_merchant",
            name = "Thoren Ironfist",
            description = "A stocky dwarf with a thick beard. He examines each piece of armor with a critical eye.",
            isHostile = false,
            health = 80,
            maxHealth = 80,
            stats = Stats(strength = 16, constitution = 15),
            components = mapOf(
                ComponentType.TRADING to tradingComponent,
                ComponentType.SOCIAL to socialComponent
            )
        )
    }

    /**
     * Create blacksmith NPC
     * Sells weapons and can repair (future feature)
     */
    private fun createBlacksmith(): Entity.NPC {
        val stock = listOf(
            // Basic weapons (COMMON)
            createItemInstance("iron_sword", quality = 5, quantity = 4),
            createItemInstance("iron_axe", quality = 5, quantity = 3),
            createItemInstance("wooden_bow", quality = 5, quantity = 2),
            // Better weapons (UNCOMMON)
            createItemInstance("steel_sword", quality = 6, quantity = 1)
        )

        val tradingComponent = TradingComponent(
            merchantGold = 800,
            stock = stock,
            buyAnything = true,
            priceModBase = 1.1
        )

        val socialComponent = SocialComponent(
            disposition = 0,
            personality = "master craftsman",
            traits = listOf("proud", "skilled", "direct")
        )

        return Entity.NPC(
            id = "npc_town_blacksmith",
            name = "Gareth the Smith",
            description = "A muscular human covered in soot and sweat. The clang of his hammer echoes through the town.",
            isHostile = false,
            health = 100,
            maxHealth = 100,
            stats = Stats(strength = 18, constitution = 16, dexterity = 12),
            components = mapOf(
                ComponentType.TRADING to tradingComponent,
                ComponentType.SOCIAL to socialComponent
            )
        )
    }

    /**
     * Create general store NPC
     * Sells tools, torches, rope, and misc items
     */
    private fun createGeneralStore(): Entity.NPC {
        val stock = listOf(
            // Tools and supplies (COMMON)
            createItemInstance("torch", quality = 5, quantity = 20),
            createItemInstance("rope_50ft", quality = 5, quantity = 5),
            createItemInstance("lockpick_set", quality = 5, quantity = 3),
            createItemInstance("rations", quality = 5, quantity = 15)
        )

        val tradingComponent = TradingComponent(
            merchantGold = 300,
            stock = stock,
            buyAnything = true,
            priceModBase = 1.0
        )

        val socialComponent = SocialComponent(
            disposition = 0,
            personality = "chatty shopkeeper",
            traits = listOf("friendly", "curious", "gossipy")
        )

        return Entity.NPC(
            id = "npc_town_general_store",
            name = "Mira Goodbarrel",
            description = "A cheerful halfling woman who always has a smile and a story to share.",
            isHostile = false,
            health = 40,
            maxHealth = 40,
            stats = Stats(charisma = 16, wisdom = 13),
            components = mapOf(
                ComponentType.TRADING to tradingComponent,
                ComponentType.SOCIAL to socialComponent
            )
        )
    }

    /**
     * Helper to create ItemInstance with basic properties
     * Template IDs are placeholders - actual templates should exist in DB
     */
    private fun createItemInstance(
        templateId: String,
        quality: Int = 5,
        quantity: Int = 1
    ): ItemInstance {
        return ItemInstance(
            id = UUID.randomUUID().toString(),
            templateId = templateId,
            quality = quality,
            quantity = quantity
        )
    }
}
