package com.jcraw.mud.reasoning.pickpocket

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import kotlin.random.Random
import kotlin.test.*

/**
 * Unit tests for PickpocketHandler
 * Tests pickpocketing mechanics, skill checks, disposition consequences, and wariness status
 */
class PickpocketHandlerTest {

    private lateinit var mockItemRepository: ItemRepository
    private lateinit var pickpocketHandler: PickpocketHandler

    // Test item templates
    private val daggerTemplate = ItemTemplate(
        id = "dagger_001",
        name = "Rusty Dagger",
        type = ItemType.WEAPON,
        tags = listOf("weapon", "sharp", "metal"),
        properties = mapOf("weight" to "1.0", "value" to "10"),
        rarity = Rarity.COMMON,
        description = "A rusty old dagger",
        equipSlot = EquipSlot.HANDS_MAIN
    )

    private val potionTemplate = ItemTemplate(
        id = "potion_health_001",
        name = "Health Potion",
        type = ItemType.CONSUMABLE,
        tags = listOf("consumable", "liquid", "healing"),
        properties = mapOf("weight" to "0.5", "value" to "25", "heal_amount" to "20"),
        rarity = Rarity.COMMON,
        description = "Restores 20 HP",
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
        description = "A stick of dynamite with a timed fuse",
        equipSlot = null
    )

    @BeforeTest
    fun setup() {
        // Create mock repository
        mockItemRepository = object : ItemRepository {
            private val templates = mapOf(
                "dagger_001" to daggerTemplate,
                "potion_health_001" to potionTemplate,
                "dynamite_001" to dynamiteTemplate
            )

            override fun save(template: ItemTemplate): Result<ItemTemplate> = Result.success(template)
            override fun saveAll(templates: List<ItemTemplate>): Result<List<ItemTemplate>> = Result.success(templates)
            override fun findById(id: String): Result<ItemTemplate?> = Result.success(templates[id])
            override fun findAll(): Result<List<ItemTemplate>> = Result.success(templates.values.toList())
            override fun delete(id: String): Result<Boolean> = Result.success(true)
            override fun findByType(type: ItemType): Result<List<ItemTemplate>> =
                Result.success(templates.values.filter { it.type == type })
            override fun findByRarity(rarity: Rarity): Result<List<ItemTemplate>> =
                Result.success(templates.values.filter { it.rarity == rarity })
            override fun findByTag(tag: String): Result<List<ItemTemplate>> =
                Result.success(templates.values.filter { tag in it.tags })
            override fun update(template: ItemTemplate): Result<ItemTemplate> = Result.success(template)
        }
    }

    @Test
    fun `stealFromNPC success - steal gold from NPC`() {
        // High roll to ensure success
        val fixedRandom = Random(12345) // Seed for consistent rolls
        pickpocketHandler = PickpocketHandler(mockItemRepository, fixedRandom)

        val playerInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val playerSkills = SkillComponent(
            skills = mapOf(
                "Stealth" to SkillState(level = 10, xp = 0, unlocked = true)
            )
        )

        val targetInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 100,
            capacityWeight = 50.0
        )

        val targetNpc = Entity.NPC(
            id = "npc_001",
            name = "Guard",
            description = "A town guard",
            stats = Stats(wisdom = 10), // Perception DC = 10
            components = mapOf(
                ComponentType.INVENTORY to targetInventory,
                ComponentType.SOCIAL to SocialComponent(personality = "stern", traits = emptyList())
            )
        )

        val result = pickpocketHandler.stealFromNPC(
            playerInventory = playerInventory,
            playerSkills = playerSkills,
            targetNpc = targetNpc,
            itemTarget = null, // Steal gold
            templates = emptyMap()
        )

        assertTrue(result is PickpocketHandler.PickpocketResult.Success)
        val success = result as PickpocketHandler.PickpocketResult.Success
        assertTrue(success.playerInventory.gold > playerInventory.gold, "Player should gain gold")
        assertTrue(success.targetInventory!!.gold < targetInventory.gold, "Target should lose gold")
        assertEquals("stole", success.action)
    }

    @Test
    fun `stealFromNPC success - steal specific item from NPC`() {
        val fixedRandom = Random(12345)
        pickpocketHandler = PickpocketHandler(mockItemRepository, fixedRandom)

        val daggerInstance = ItemInstance(
            id = "dagger_instance_001",
            templateId = "dagger_001",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val playerInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val playerSkills = SkillComponent(
            skills = mapOf(
                "Stealth" to SkillState(level = 12, xp = 0, unlocked = true)
            )
        )

        val targetInventory = InventoryComponent(
            items = listOf(daggerInstance),
            equipped = emptyMap(),
            gold = 50,
            capacityWeight = 50.0
        )

        val targetNpc = Entity.NPC(
            id = "npc_002",
            name = "Merchant",
            description = "A traveling merchant",
            stats = Stats(wisdom = 10),
            components = mapOf(
                ComponentType.INVENTORY to targetInventory
            )
        )

        val templates = mapOf("dagger_001" to daggerTemplate)

        val result = pickpocketHandler.stealFromNPC(
            playerInventory = playerInventory,
            playerSkills = playerSkills,
            targetNpc = targetNpc,
            itemTarget = "Rusty Dagger",
            templates = templates
        )

        assertTrue(result is PickpocketHandler.PickpocketResult.Success)
        val success = result as PickpocketHandler.PickpocketResult.Success
        assertEquals(1, success.playerInventory.items.size, "Player should have the stolen dagger")
        assertEquals(0, success.targetInventory!!.items.size, "Target should lose the dagger")
        assertEquals("Rusty Dagger", success.itemName)
    }

    @Test
    fun `placeItemOnNPC success - place item in NPC inventory`() {
        val fixedRandom = Random(12345)
        pickpocketHandler = PickpocketHandler(mockItemRepository, fixedRandom)

        val dynamiteInstance = ItemInstance(
            id = "dynamite_instance_001",
            templateId = "dynamite_001",
            quality = 5,
            charges = null,
            quantity = 1
        )

        val playerInventory = InventoryComponent(
            items = listOf(dynamiteInstance),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val playerSkills = SkillComponent(
            skills = mapOf(
                "Stealth" to SkillState(level = 15, xp = 0, unlocked = true)
            )
        )

        val targetInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 50,
            capacityWeight = 50.0
        )

        val targetNpc = Entity.NPC(
            id = "npc_003",
            name = "Goblin",
            description = "A hostile goblin",
            stats = Stats(wisdom = 8),
            components = mapOf(
                ComponentType.INVENTORY to targetInventory
            )
        )

        val templates = mapOf("dynamite_001" to dynamiteTemplate)

        val result = pickpocketHandler.placeItemOnNPC(
            playerInventory = playerInventory,
            playerSkills = playerSkills,
            targetNpc = targetNpc,
            instanceId = "dynamite_instance_001",
            templates = templates
        )

        assertTrue(result is PickpocketHandler.PickpocketResult.Success)
        val success = result as PickpocketHandler.PickpocketResult.Success
        assertEquals(0, success.playerInventory.items.size, "Player should lose the dynamite")
        assertEquals(1, success.targetInventory!!.items.size, "Target should gain the dynamite")
        assertEquals("placed", success.action)
        assertEquals("Dynamite", success.itemName)
    }

    @Test
    fun `stealFromNPC failure - caught and disposition penalty applied`() {
        // Low roll to ensure failure
        val fixedRandom = object : Random() {
            override fun nextInt(until: Int): Int = 1 // Always roll 1
            override fun nextInt(from: Int, until: Int): Int = 1 // Always roll minimum
            override fun nextBits(bitCount: Int): Int = 0
        }
        pickpocketHandler = PickpocketHandler(mockItemRepository, fixedRandom)

        val playerInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val playerSkills = SkillComponent(
            skills = mapOf(
                "Stealth" to SkillState(level = 2, xp = 0, unlocked = true)
            )
        )

        val targetInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 100,
            capacityWeight = 50.0
        )

        val targetSocial = SocialComponent(
            personality = "suspicious",
            traits = emptyList(),
            disposition = 50
        )

        val targetNpc = Entity.NPC(
            id = "npc_004",
            name = "Watchful Guard",
            description = "A very watchful guard",
            stats = Stats(wisdom = 16), // High perception
            components = mapOf(
                ComponentType.INVENTORY to targetInventory,
                ComponentType.SOCIAL to targetSocial
            )
        )

        val result = pickpocketHandler.stealFromNPC(
            playerInventory = playerInventory,
            playerSkills = playerSkills,
            targetNpc = targetNpc,
            itemTarget = null,
            templates = emptyMap()
        )

        assertTrue(result is PickpocketHandler.PickpocketResult.Caught)
        val caught = result as PickpocketHandler.PickpocketResult.Caught
        assertTrue(caught.dispositionDelta < 0, "Disposition should decrease")
        assertTrue(caught.dispositionDelta >= -50 && caught.dispositionDelta <= -20,
            "Disposition penalty should be between -20 and -50")
        assertTrue(caught.targetSocial.disposition < 50, "Disposition should be reduced")
    }

    @Test
    fun `stealFromNPC failure - wariness status applied to target`() {
        val fixedRandom = object : Random() {
            override fun nextInt(from: Int, until: Int): Int = 1
            override fun nextBits(bitCount: Int): Int = 0
        }
        pickpocketHandler = PickpocketHandler(mockItemRepository, fixedRandom)

        val playerInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val playerSkills = SkillComponent(
            skills = mapOf(
                "Stealth" to SkillState(level = 1, xp = 0, unlocked = true)
            )
        )

        val targetInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 100,
            capacityWeight = 50.0
        )

        val targetNpc = Entity.NPC(
            id = "npc_005",
            name = "Alert Guard",
            description = "An alert guard",
            stats = Stats(wisdom = 14),
            components = mapOf(
                ComponentType.INVENTORY to targetInventory,
                ComponentType.SOCIAL to SocialComponent(personality = "alert", traits = emptyList())
            )
        )

        val result = pickpocketHandler.stealFromNPC(
            playerInventory = playerInventory,
            playerSkills = playerSkills,
            targetNpc = targetNpc,
            itemTarget = null,
            templates = emptyMap()
        )

        assertTrue(result is PickpocketHandler.PickpocketResult.Caught)
        val caught = result as PickpocketHandler.PickpocketResult.Caught

        assertNotNull(caught.targetCombat, "Combat component should be created/updated")
        assertTrue(caught.targetCombat.statusEffects.any { it.type == StatusEffectType.WARINESS },
            "Wariness status should be applied")

        val warinessEffect = caught.targetCombat.statusEffects.first { it.type == StatusEffectType.WARINESS }
        assertEquals(20, warinessEffect.magnitude, "Wariness magnitude should be +20")
        assertEquals(10, warinessEffect.duration, "Wariness duration should be 10 turns")
    }

    @Test
    fun `high Stealth overcomes low Perception`() {
        val fixedRandom = Random(99999) // Decent roll
        pickpocketHandler = PickpocketHandler(mockItemRepository, fixedRandom)

        val playerInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val playerSkills = SkillComponent(
            skills = mapOf(
                "Stealth" to SkillState(level = 15, xp = 0, unlocked = true)
            )
        )

        val targetInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 100,
            capacityWeight = 50.0
        )

        val targetNpc = Entity.NPC(
            id = "npc_006",
            name = "Distracted Merchant",
            description = "A distracted merchant",
            stats = Stats(wisdom = 8), // Low wisdom = low perception
            components = mapOf(
                ComponentType.INVENTORY to targetInventory
            )
        )

        val result = pickpocketHandler.stealFromNPC(
            playerInventory = playerInventory,
            playerSkills = playerSkills,
            targetNpc = targetNpc,
            itemTarget = null,
            templates = emptyMap()
        )

        assertTrue(result is PickpocketHandler.PickpocketResult.Success,
            "High stealth should succeed against low perception")
    }

    @Test
    fun `wariness status increases difficulty on retry`() {
        val fixedRandom = Random(54321)
        pickpocketHandler = PickpocketHandler(mockItemRepository, fixedRandom)

        val playerInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val playerSkills = SkillComponent(
            skills = mapOf(
                "Stealth" to SkillState(level = 10, xp = 0, unlocked = true)
            )
        )

        val targetInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 100,
            capacityWeight = 50.0
        )

        // Create target with existing wariness status
        val warinessEffect = StatusEffect(
            type = StatusEffectType.WARINESS,
            magnitude = 20,
            duration = 5,
            source = "previous_pickpocket"
        )

        val targetCombat = CombatComponent(
            maxHp = 100,
            currentHp = 100,
            statusEffects = listOf(warinessEffect)
        )

        val targetNpc = Entity.NPC(
            id = "npc_007",
            name = "Wary Guard",
            description = "A guard who's been alerted before",
            stats = Stats(wisdom = 10),
            components = mapOf(
                ComponentType.INVENTORY to targetInventory,
                ComponentType.COMBAT to targetCombat
            )
        )

        val result = pickpocketHandler.stealFromNPC(
            playerInventory = playerInventory,
            playerSkills = playerSkills,
            targetNpc = targetNpc,
            itemTarget = null,
            templates = emptyMap()
        )

        // With +20 wariness bonus, DC should be harder
        // DC = 10 + wisdom modifier (0) + perception skill (0) + wariness (20) = 30
        // With Stealth 10, need to roll 20+ on d20, which is unlikely
        // This test validates that wariness makes subsequent attempts harder
        when (result) {
            is PickpocketHandler.PickpocketResult.Success -> {
                assertTrue(result.dc >= 30, "DC should include wariness bonus")
            }
            is PickpocketHandler.PickpocketResult.Caught -> {
                assertTrue(result.dc >= 30, "DC should include wariness bonus")
            }
            else -> fail("Unexpected result type")
        }
    }

    @Test
    fun `stealFromNPC failure - target has no inventory`() {
        pickpocketHandler = PickpocketHandler(mockItemRepository)

        val playerInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val playerSkills = SkillComponent(
            skills = mapOf(
                "Stealth" to SkillState(level = 10, xp = 0, unlocked = true)
            )
        )

        val targetNpc = Entity.NPC(
            id = "npc_008",
            name = "Ghost",
            description = "An incorporeal ghost",
            stats = Stats(),
            components = emptyMap() // No inventory
        )

        val result = pickpocketHandler.stealFromNPC(
            playerInventory = playerInventory,
            playerSkills = playerSkills,
            targetNpc = targetNpc,
            itemTarget = null,
            templates = emptyMap()
        )

        assertTrue(result is PickpocketHandler.PickpocketResult.Failure)
        val failure = result as PickpocketHandler.PickpocketResult.Failure
        assertEquals("Target has no inventory", failure.reason)
    }

    @Test
    fun `stealFromNPC failure - target has no gold`() {
        pickpocketHandler = PickpocketHandler(mockItemRepository)

        val playerInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val playerSkills = SkillComponent(
            skills = mapOf(
                "Stealth" to SkillState(level = 10, xp = 0, unlocked = true)
            )
        )

        val targetInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 0, // No gold
            capacityWeight = 50.0
        )

        val targetNpc = Entity.NPC(
            id = "npc_009",
            name = "Poor Beggar",
            description = "A penniless beggar",
            stats = Stats(),
            components = mapOf(
                ComponentType.INVENTORY to targetInventory
            )
        )

        val result = pickpocketHandler.stealFromNPC(
            playerInventory = playerInventory,
            playerSkills = playerSkills,
            targetNpc = targetNpc,
            itemTarget = null,
            templates = emptyMap()
        )

        assertTrue(result is PickpocketHandler.PickpocketResult.Failure)
        val failure = result as PickpocketHandler.PickpocketResult.Failure
        assertEquals("Target has no gold", failure.reason)
    }

    @Test
    fun `stealFromNPC failure - target doesn't have specified item`() {
        pickpocketHandler = PickpocketHandler(mockItemRepository)

        val playerInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val playerSkills = SkillComponent(
            skills = mapOf(
                "Stealth" to SkillState(level = 10, xp = 0, unlocked = true)
            )
        )

        val targetInventory = InventoryComponent(
            items = emptyList(), // Empty inventory
            equipped = emptyMap(),
            gold = 50,
            capacityWeight = 50.0
        )

        val targetNpc = Entity.NPC(
            id = "npc_010",
            name = "Guard",
            description = "A guard",
            stats = Stats(),
            components = mapOf(
                ComponentType.INVENTORY to targetInventory
            )
        )

        val result = pickpocketHandler.stealFromNPC(
            playerInventory = playerInventory,
            playerSkills = playerSkills,
            targetNpc = targetNpc,
            itemTarget = "Diamond Ring", // Item not in inventory
            templates = emptyMap()
        )

        assertTrue(result is PickpocketHandler.PickpocketResult.Failure)
        val failure = result as PickpocketHandler.PickpocketResult.Failure
        assertEquals("Target doesn't have that item", failure.reason)
    }

    @Test
    fun `placeItemOnNPC failure - player doesn't have item`() {
        pickpocketHandler = PickpocketHandler(mockItemRepository)

        val playerInventory = InventoryComponent(
            items = emptyList(), // Empty
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        val playerSkills = SkillComponent(
            skills = mapOf(
                "Stealth" to SkillState(level = 10, xp = 0, unlocked = true)
            )
        )

        val targetInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 50,
            capacityWeight = 50.0
        )

        val targetNpc = Entity.NPC(
            id = "npc_011",
            name = "Merchant",
            description = "A merchant",
            stats = Stats(),
            components = mapOf(
                ComponentType.INVENTORY to targetInventory
            )
        )

        val result = pickpocketHandler.placeItemOnNPC(
            playerInventory = playerInventory,
            playerSkills = playerSkills,
            targetNpc = targetNpc,
            instanceId = "nonexistent_item",
            templates = emptyMap()
        )

        assertTrue(result is PickpocketHandler.PickpocketResult.Failure)
        val failure = result as PickpocketHandler.PickpocketResult.Failure
        assertEquals("You don't have that item", failure.reason)
    }

    @Test
    fun `Agility skill can be used instead of Stealth`() {
        val fixedRandom = Random(12345)
        pickpocketHandler = PickpocketHandler(mockItemRepository, fixedRandom)

        val playerInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 10,
            capacityWeight = 50.0
        )

        // High Agility, no Stealth
        val playerSkills = SkillComponent(
            skills = mapOf(
                "Agility" to SkillState(level = 12, xp = 0, unlocked = true),
                "Stealth" to SkillState(level = 0, xp = 0, unlocked = false)
            )
        )

        val targetInventory = InventoryComponent(
            items = emptyList(),
            equipped = emptyMap(),
            gold = 100,
            capacityWeight = 50.0
        )

        val targetNpc = Entity.NPC(
            id = "npc_012",
            name = "Merchant",
            description = "A merchant",
            stats = Stats(wisdom = 10),
            components = mapOf(
                ComponentType.INVENTORY to targetInventory
            )
        )

        val result = pickpocketHandler.stealFromNPC(
            playerInventory = playerInventory,
            playerSkills = playerSkills,
            targetNpc = targetNpc,
            itemTarget = null,
            templates = emptyMap()
        )

        // Should use Agility (12) instead of Stealth (0)
        assertTrue(result is PickpocketHandler.PickpocketResult.Success ||
                   result is PickpocketHandler.PickpocketResult.Caught,
            "Should attempt pickpocket using Agility")
    }
}
