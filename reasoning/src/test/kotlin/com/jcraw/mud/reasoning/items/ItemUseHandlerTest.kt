package com.jcraw.mud.reasoning.items

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import kotlin.test.*

/**
 * Unit tests for ItemUseHandler
 * Tests multipurpose item uses based on tags and actions
 */
class ItemUseHandlerTest {

    private lateinit var mockItemRepository: ItemRepository
    private lateinit var itemUseHandler: ItemUseHandler

    // Test item templates
    private val clayPotTemplate = ItemTemplate(
        id = "pot_clay_001",
        name = "Clay Pot",
        type = ItemType.MISC,
        tags = listOf("container", "blunt", "fragile"),
        properties = mapOf(
            "weight" to "3.0",
            "value" to "5",
            "capacity_bonus" to "5.0"
        ),
        rarity = Rarity.COMMON,
        description = "A simple clay pot",
        equipSlot = null
    )

    private val dynamiteTemplate = ItemTemplate(
        id = "dynamite_001",
        name = "Dynamite",
        type = ItemType.MISC,
        tags = listOf("explosive", "throwable", "timed"),
        properties = mapOf(
            "weight" to "2.0",
            "value" to "50",
            "explosion_damage" to "30",
            "explosion_timer" to "3"
        ),
        rarity = Rarity.UNCOMMON,
        description = "A stick of dynamite",
        equipSlot = null
    )

    private val ironSwordTemplate = ItemTemplate(
        id = "sword_iron_001",
        name = "Iron Sword",
        type = ItemType.WEAPON,
        tags = listOf("weapon", "sharp", "slashing", "metal"),
        properties = mapOf(
            "weight" to "4.0",
            "value" to "100",
            "damage" to "10"
        ),
        rarity = Rarity.COMMON,
        description = "A well-crafted iron sword",
        equipSlot = EquipSlot.HANDS_MAIN
    )

    private val torchTemplate = ItemTemplate(
        id = "torch_001",
        name = "Torch",
        type = ItemType.MISC,
        tags = listOf("flammable", "light_source", "throwable"),
        properties = mapOf(
            "weight" to "1.0",
            "value" to "2"
        ),
        rarity = Rarity.COMMON,
        description = "A wooden torch",
        equipSlot = null
    )

    private val glassVialTemplate = ItemTemplate(
        id = "vial_glass_001",
        name = "Glass Vial",
        type = ItemType.MISC,
        tags = listOf("fragile", "container", "liquid"),
        properties = mapOf(
            "weight" to "0.2",
            "value" to "3",
            "capacity_bonus" to "0.5"
        ),
        rarity = Rarity.COMMON,
        description = "A small glass vial",
        equipSlot = null
    )

    private val ropeTemplate = ItemTemplate(
        id = "rope_001",
        name = "Rope",
        type = ItemType.MISC,
        tags = listOf("climbable", "throwable"),
        properties = mapOf(
            "weight" to "2.0",
            "value" to "10"
        ),
        rarity = Rarity.COMMON,
        description = "A sturdy rope",
        equipSlot = null
    )

    private val backpackTemplate = ItemTemplate(
        id = "backpack_leather_001",
        name = "Leather Backpack",
        type = ItemType.CONTAINER,
        tags = listOf("container", "wearable"),
        properties = mapOf(
            "weight" to "3.0",
            "value" to "25",
            "capacity_bonus" to "20.0"
        ),
        rarity = Rarity.COMMON,
        description = "A sturdy leather backpack",
        equipSlot = EquipSlot.BACK
    )

    @BeforeTest
    fun setup() {
        // Create mock repository
        mockItemRepository = object : ItemRepository {
            private val templates = mapOf(
                "pot_clay_001" to clayPotTemplate,
                "dynamite_001" to dynamiteTemplate,
                "sword_iron_001" to ironSwordTemplate,
                "torch_001" to torchTemplate,
                "vial_glass_001" to glassVialTemplate,
                "rope_001" to ropeTemplate,
                "backpack_leather_001" to backpackTemplate
            )

            override fun findTemplateById(templateId: String): Result<ItemTemplate?> = Result.success(templates[templateId])
            override fun findAllTemplates(): Result<Map<String, ItemTemplate>> = Result.success(templates)
            override fun findTemplatesByType(type: ItemType): Result<List<ItemTemplate>> =
                Result.success(templates.values.filter { it.type == type })
            override fun findTemplatesByRarity(rarity: Rarity): Result<List<ItemTemplate>> =
                Result.success(templates.values.filter { it.rarity == rarity })
            override fun saveTemplate(template: ItemTemplate): Result<Unit> = Result.success(Unit)
            override fun saveTemplates(templates: List<ItemTemplate>): Result<Unit> = Result.success(Unit)
            override fun deleteTemplate(templateId: String): Result<Unit> = Result.success(Unit)
            override fun findInstanceById(instanceId: String): Result<ItemInstance?> = Result.success(null)
            override fun findInstancesByTemplate(templateId: String): Result<List<ItemInstance>> = Result.success(emptyList())
            override fun saveInstance(instance: ItemInstance): Result<Unit> = Result.success(Unit)
            override fun deleteInstance(instanceId: String): Result<Unit> = Result.success(Unit)
            override fun findAllInstances(): Result<Map<String, ItemInstance>> = Result.success(emptyMap())
        }

        itemUseHandler = ItemUseHandler(mockItemRepository)
    }

    @Test
    fun `pot with blunt tag can be used as improvised weapon`() {
        val potInstance = ItemInstance(
            id = "pot_instance_001",
            templateId = "pot_clay_001",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val result = itemUseHandler.useAsImprovisedWeapon(clayPotTemplate, potInstance)

        assertTrue(result is ItemUseHandler.ItemUseResult.ImprovisedWeapon)
        val weapon = result as ItemUseHandler.ItemUseResult.ImprovisedWeapon
        assertEquals("Clay Pot", weapon.itemName)
        // Weight 3.0 * 0.5 = 1.5, rounded to 1
        assertEquals(1, weapon.damageBonus)
        assertTrue("blunt" in weapon.itemTags)
    }

    @Test
    fun `sword with sharp tag can be used as improvised weapon`() {
        val swordInstance = ItemInstance(
            id = "sword_instance_001",
            templateId = "sword_iron_001",
            quality = 8,
            charges = null,
            quantity = 1
        )

        val result = itemUseHandler.useAsImprovisedWeapon(ironSwordTemplate, swordInstance)

        assertTrue(result is ItemUseHandler.ItemUseResult.ImprovisedWeapon)
        val weapon = result as ItemUseHandler.ItemUseResult.ImprovisedWeapon
        assertEquals("Iron Sword", weapon.itemName)
        // Weight 4.0 * 0.5 = 2.0
        assertEquals(2, weapon.damageBonus)
        assertTrue("sharp" in weapon.itemTags)
    }

    @Test
    fun `dynamite with explosive tag provides AoE damage`() {
        val dynamiteInstance = ItemInstance(
            id = "dynamite_instance_001",
            templateId = "dynamite_001",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val result = itemUseHandler.useAsExplosive(dynamiteTemplate, dynamiteInstance)

        assertTrue(result is ItemUseHandler.ItemUseResult.ExplosiveUse)
        val explosive = result as ItemUseHandler.ItemUseResult.ExplosiveUse
        assertEquals("Dynamite", explosive.itemName)
        assertEquals(30, explosive.aoeDamage)
        assertEquals(3, explosive.timer)
    }

    @Test
    fun `item without explosive tag cannot be used as explosive`() {
        val potInstance = ItemInstance(
            id = "pot_instance_002",
            templateId = "pot_clay_001",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val result = itemUseHandler.useAsExplosive(clayPotTemplate, potInstance)

        assertTrue(result is ItemUseHandler.ItemUseResult.Failure)
        val failure = result as ItemUseHandler.ItemUseResult.Failure
        assertEquals("This item isn't explosive", failure.reason)
    }

    @Test
    fun `pot with container tag provides capacity bonus`() {
        val potInstance = ItemInstance(
            id = "pot_instance_003",
            templateId = "pot_clay_001",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val result = itemUseHandler.useAsContainer(clayPotTemplate, potInstance)

        assertTrue(result is ItemUseHandler.ItemUseResult.ContainerUse)
        val container = result as ItemUseHandler.ItemUseResult.ContainerUse
        assertEquals("Clay Pot", container.itemName)
        assertEquals(5.0, container.capacityBonus)
    }

    @Test
    fun `backpack with container tag provides larger capacity bonus`() {
        val backpackInstance = ItemInstance(
            id = "backpack_instance_001",
            templateId = "backpack_leather_001",
            quality = 7,
            charges = null,
            quantity = 1
        )

        val result = itemUseHandler.useAsContainer(backpackTemplate, backpackInstance)

        assertTrue(result is ItemUseHandler.ItemUseResult.ContainerUse)
        val container = result as ItemUseHandler.ItemUseResult.ContainerUse
        assertEquals("Leather Backpack", container.itemName)
        assertEquals(20.0, container.capacityBonus)
    }

    @Test
    fun `item without container tag cannot be used as container`() {
        val dynamiteInstance = ItemInstance(
            id = "dynamite_instance_002",
            templateId = "dynamite_001",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val result = itemUseHandler.useAsContainer(dynamiteTemplate, dynamiteInstance)

        assertTrue(result is ItemUseHandler.ItemUseResult.Failure)
        val failure = result as ItemUseHandler.ItemUseResult.Failure
        assertEquals("This item isn't a container", failure.reason)
    }

    @Test
    fun `determineUse with bash action uses item as weapon`() {
        val potInstance = ItemInstance(
            id = "pot_instance_004",
            templateId = "pot_clay_001",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val inventory = InventoryComponent(
            items = listOf(potInstance),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val result = itemUseHandler.determineUse(
            instanceId = "pot_instance_004",
            action = "bash",
            inventory = inventory
        )

        assertTrue(result is ItemUseHandler.ItemUseResult.ImprovisedWeapon)
        val weapon = result as ItemUseHandler.ItemUseResult.ImprovisedWeapon
        assertEquals("Clay Pot", weapon.itemName)
    }

    @Test
    fun `determineUse with throw action on explosive uses as explosive`() {
        val dynamiteInstance = ItemInstance(
            id = "dynamite_instance_003",
            templateId = "dynamite_001",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val inventory = InventoryComponent(
            items = listOf(dynamiteInstance),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val result = itemUseHandler.determineUse(
            instanceId = "dynamite_instance_003",
            action = "throw",
            inventory = inventory
        )

        assertTrue(result is ItemUseHandler.ItemUseResult.ExplosiveUse)
        val explosive = result as ItemUseHandler.ItemUseResult.ExplosiveUse
        assertEquals("Dynamite", explosive.itemName)
    }

    @Test
    fun `determineUse with burn action on flammable item creates environmental effect`() {
        val torchInstance = ItemInstance(
            id = "torch_instance_001",
            templateId = "torch_001",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val inventory = InventoryComponent(
            items = listOf(torchInstance),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val result = itemUseHandler.determineUse(
            instanceId = "torch_instance_001",
            action = "burn",
            inventory = inventory
        )

        assertTrue(result is ItemUseHandler.ItemUseResult.EnvironmentalUse)
        val envUse = result as ItemUseHandler.ItemUseResult.EnvironmentalUse
        assertEquals("Torch", envUse.itemName)
        assertTrue(envUse.effect.contains("fire") || envUse.effect.contains("light"))
    }

    @Test
    fun `determineUse with break action on fragile item creates environmental effect`() {
        val vialInstance = ItemInstance(
            id = "vial_instance_001",
            templateId = "vial_glass_001",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val inventory = InventoryComponent(
            items = listOf(vialInstance),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val result = itemUseHandler.determineUse(
            instanceId = "vial_instance_001",
            action = "break",
            inventory = inventory
        )

        assertTrue(result is ItemUseHandler.ItemUseResult.EnvironmentalUse)
        val envUse = result as ItemUseHandler.ItemUseResult.EnvironmentalUse
        assertEquals("Glass Vial", envUse.itemName)
        assertTrue(envUse.effect.contains("shatter") || envUse.effect.contains("Shatter"))
    }

    @Test
    fun `determineUse with pour action on liquid item creates environmental effect`() {
        val vialInstance = ItemInstance(
            id = "vial_instance_002",
            templateId = "vial_glass_001",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val inventory = InventoryComponent(
            items = listOf(vialInstance),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val result = itemUseHandler.determineUse(
            instanceId = "vial_instance_002",
            action = "pour",
            inventory = inventory
        )

        assertTrue(result is ItemUseHandler.ItemUseResult.EnvironmentalUse)
        val envUse = result as ItemUseHandler.ItemUseResult.EnvironmentalUse
        assertEquals("Glass Vial", envUse.itemName)
        assertTrue(envUse.effect.contains("pour") || envUse.effect.contains("Pour"))
    }

    @Test
    fun `determineUse fails for action without matching tags`() {
        val swordInstance = ItemInstance(
            id = "sword_instance_002",
            templateId = "sword_iron_001",
            quality = 8,
            charges = null,
            quantity = 1
        )

        val inventory = InventoryComponent(
            items = listOf(swordInstance),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val result = itemUseHandler.determineUse(
            instanceId = "sword_instance_002",
            action = "eat", // Swords aren't edible!
            inventory = inventory
        )

        assertTrue(result is ItemUseHandler.ItemUseResult.Failure)
        val failure = result as ItemUseHandler.ItemUseResult.Failure
        assertTrue(failure.reason.contains("can't use"))
    }

    @Test
    fun `determineUse fails for nonexistent item`() {
        val inventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val result = itemUseHandler.determineUse(
            instanceId = "nonexistent_item",
            action = "bash",
            inventory = inventory
        )

        assertTrue(result is ItemUseHandler.ItemUseResult.Failure)
        val failure = result as ItemUseHandler.ItemUseResult.Failure
        assertEquals("You don't have that item", failure.reason)
    }

    @Test
    fun `getPossibleUses returns all uses for clay pot`() {
        val uses = itemUseHandler.getPossibleUses(clayPotTemplate)

        assertTrue(uses.isNotEmpty())
        assertTrue(uses.any { it.contains("Improvised weapon") })
        assertTrue(uses.any { it.contains("Container") })
        assertTrue(uses.any { it.contains("Break") })
    }

    @Test
    fun `getPossibleUses returns explosive use for dynamite`() {
        val uses = itemUseHandler.getPossibleUses(dynamiteTemplate)

        assertTrue(uses.isNotEmpty())
        assertTrue(uses.any { it.contains("Explosive") })
        assertTrue(uses.any { it.contains("Throw") })
    }

    @Test
    fun `getPossibleUses returns climbing use for rope`() {
        val uses = itemUseHandler.getPossibleUses(ropeTemplate)

        assertTrue(uses.isNotEmpty())
        assertTrue(uses.any { it.contains("climbing") || it.contains("Climbing") })
    }

    @Test
    fun `damage calculation scales with weight`() {
        // Heavy item should deal more damage
        val heavyPot = clayPotTemplate.copy(
            properties = clayPotTemplate.properties + ("weight" to "10.0")
        )

        val heavyInstance = ItemInstance(
            id = "heavy_pot_001",
            templateId = "pot_clay_001",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val result = itemUseHandler.useAsImprovisedWeapon(heavyPot, heavyInstance)

        assertTrue(result is ItemUseHandler.ItemUseResult.ImprovisedWeapon)
        val weapon = result as ItemUseHandler.ItemUseResult.ImprovisedWeapon
        // Weight 10.0 * 0.5 = 5
        assertEquals(5, weapon.damageBonus)
    }

    @Test
    fun `item without weapon tags cannot be used as improvised weapon`() {
        val backpackInstance = ItemInstance(
            id = "backpack_instance_002",
            templateId = "backpack_leather_001",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val result = itemUseHandler.useAsImprovisedWeapon(backpackTemplate, backpackInstance)

        assertTrue(result is ItemUseHandler.ItemUseResult.Failure)
        val failure = result as ItemUseHandler.ItemUseResult.Failure
        assertEquals("This item isn't suitable as a weapon", failure.reason)
    }
}
