package com.jcraw.app.integration

import com.jcraw.mud.core.*
import com.jcraw.mud.testbot.InMemoryGameEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for quest system
 *
 * Tests the full quest workflow including:
 * - Quest acceptance
 * - Objective auto-tracking (all 6 types)
 * - Quest completion
 * - Reward claiming
 */
class QuestIntegrationTest {

    @Test
    fun `player can accept a quest`() = runBlocking {
        val quest = createTestQuest()
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room"
        )

        val world = createTestWorld(player = player).copy(availableQuests = listOf(quest))
        val engine = InMemoryGameEngine(world)

        // Accept the quest
        val response = engine.processInput("accept ${quest.id}")
        assertTrue(response.contains("accept", ignoreCase = true) || response.contains("quest", ignoreCase = true))

        // Quest should move to active quests
        val worldState = engine.getWorldState()
        assertTrue(worldState.player.activeQuests.any { it.id == quest.id })
        assertTrue(worldState.availableQuests.none { it.id == quest.id })
    }

    @Test
    fun `kill objective auto-tracks when NPC is defeated`() = runBlocking {
        val npc = Entity.NPC(
            id = "goblin",
            name = "Goblin Scout",
            description = "A weak goblin",
            isHostile = true,
            health = 1,  // Dies in one hit
            maxHealth = 1
        )

        val quest = Quest(
            id = "kill_quest",
            title = "Goblin Slayer",
            description = "Defeat the goblin",
            objectives = listOf(
                QuestObjective.KillEnemy(
                    id = "obj1",
                    description = "Defeat Goblin Scout",
                    targetNpcId = "goblin",
                    targetName = "Goblin Scout"
                )
            ),
            reward = QuestReward(experiencePoints = 50)
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            activeQuests = listOf(quest)
        )

        val world = createTestWorld(player = player, npc = npc)
        val engine = InMemoryGameEngine(world)

        // Attack and defeat the goblin
        var defeated = false
        repeat(5) {
            val response = engine.processInput("attack goblin")
            if (response.contains("defeated", ignoreCase = true) || response.contains("dies", ignoreCase = true)) {
                defeated = true
                return@repeat
            }
        }

        assertTrue(defeated, "Goblin should be defeated")

        // Quest objective should be completed
        val activeQuest = engine.getWorldState().player.activeQuests.find { it.id == "kill_quest" }
        assertNotNull(activeQuest)
        assertTrue(activeQuest.objectives[0].isCompleted, "Kill objective should be completed")
        assertEquals(QuestStatus.COMPLETED, activeQuest.status, "Quest should auto-complete")
    }

    @Test
    fun `collect objective auto-tracks when item is picked up`() = runBlocking {
        val item = Entity.Item(
            id = "crystal",
            name = "Magic Crystal",
            description = "A glowing crystal",
            isPickupable = true,
            itemType = ItemType.MISC
        )

        val quest = Quest(
            id = "collect_quest",
            title = "Crystal Collector",
            description = "Find the magic crystal",
            objectives = listOf(
                QuestObjective.CollectItem(
                    id = "obj1",
                    description = "Collect Magic Crystal",
                    targetItemId = "crystal",
                    targetName = "Magic Crystal",
                    quantity = 1
                )
            ),
            reward = QuestReward(goldAmount = 25)
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            activeQuests = listOf(quest)
        )

        val world = createTestWorld(player = player, items = listOf(item))
        val engine = InMemoryGameEngine(world)

        // Take the crystal
        val response = engine.processInput("take crystal")
        assertTrue(response.contains("take", ignoreCase = true) || response.contains("pick", ignoreCase = true))

        // Quest objective should be completed
        val activeQuest = engine.getWorldState().player.activeQuests.find { it.id == "collect_quest" }
        assertNotNull(activeQuest)
        assertTrue(activeQuest.objectives[0].isCompleted, "Collect objective should be completed")
        assertEquals(QuestStatus.COMPLETED, activeQuest.status, "Quest should auto-complete")
    }

    @Test
    fun `explore objective auto-tracks when room is visited`() = runBlocking {
        val quest = Quest(
            id = "explore_quest",
            title = "Explorer",
            description = "Visit the northern chamber",
            objectives = listOf(
                QuestObjective.ExploreRoom(
                    id = "obj1",
                    description = "Explore Northern Chamber",
                    targetRoomId = "north_room",
                    targetRoomName = "Northern Chamber"
                )
            ),
            reward = QuestReward(experiencePoints = 30)
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            activeQuests = listOf(quest)
        )

        val world = createTestWorldWithExits(player = player)
        val engine = InMemoryGameEngine(world)

        // Move north
        val response = engine.processInput("north")
        assertTrue(response.contains("north", ignoreCase = true) || response.contains("move", ignoreCase = true))

        // Quest objective should be completed
        val activeQuest = engine.getWorldState().player.activeQuests.find { it.id == "explore_quest" }
        assertNotNull(activeQuest)
        assertTrue(activeQuest.objectives[0].isCompleted, "Explore objective should be completed")
        assertEquals(QuestStatus.COMPLETED, activeQuest.status, "Quest should auto-complete")
    }

    @Test
    fun `talk objective auto-tracks when NPC is conversed with`() = runBlocking {
        val npc = Entity.NPC(
            id = "merchant",
            name = "Village Merchant",
            description = "A friendly trader",
            isHostile = false,
            health = 50,
            maxHealth = 50
        )

        val quest = Quest(
            id = "talk_quest",
            title = "Messenger",
            description = "Speak with the merchant",
            objectives = listOf(
                QuestObjective.TalkToNpc(
                    id = "obj1",
                    description = "Talk to Village Merchant",
                    targetNpcId = "merchant",
                    targetName = "Village Merchant"
                )
            ),
            reward = QuestReward(goldAmount = 10)
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            activeQuests = listOf(quest)
        )

        val world = createTestWorld(player = player, npc = npc)
        val engine = InMemoryGameEngine(world)

        // Talk to the merchant
        val response = engine.processInput("talk merchant")
        assertTrue(response.contains("merchant", ignoreCase = true) || response.contains("says", ignoreCase = true))

        // Quest objective should be completed
        val activeQuest = engine.getWorldState().player.activeQuests.find { it.id == "talk_quest" }
        assertNotNull(activeQuest)
        assertTrue(activeQuest.objectives[0].isCompleted, "Talk objective should be completed")
        assertEquals(QuestStatus.COMPLETED, activeQuest.status, "Quest should auto-complete")
    }

    @Test
    fun `skill check objective auto-tracks when skill is successfully used`() = runBlocking {
        val feature = Entity.Feature(
            id = "lock",
            name = "Rusty Lock",
            description = "An old lock mechanism",
            skillChallenge = SkillChallenge(
                statType = StatType.DEXTERITY,
                difficulty = Difficulty.TRIVIAL,  // Very easy check
                description = "Pick the lock", successDescription = "You successfully pick the lock!",
                failureDescription = "The lock resists your attempts."
            )
        )

        val quest = Quest(
            id = "skill_quest",
            title = "Lockpicker",
            description = "Pick the rusty lock",
            objectives = listOf(
                QuestObjective.UseSkill(
                    id = "obj1",
                    description = "Pick the Rusty Lock",
                    skillType = StatType.DEXTERITY,
                    targetFeatureId = "lock",
                    targetName = "Rusty Lock"
                )
            ),
            reward = QuestReward(experiencePoints = 40)
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            activeQuests = listOf(quest),
            stats = Stats(dexterity = 20)  // High DEX for guaranteed success
        )

        val world = createTestWorld(player = player, features = listOf(feature))
        val engine = InMemoryGameEngine(world)

        // Attempt the skill check (may need multiple tries due to dice rolls)
        var succeeded = false
        repeat(10) {
            val response = engine.processInput("check lock")
            if (response.contains("succeed", ignoreCase = true) || response.contains("success", ignoreCase = true)) {
                succeeded = true
                return@repeat
            }
        }

        assertTrue(succeeded, "Skill check should succeed with high DEX")

        // Quest objective should be completed
        val activeQuest = engine.getWorldState().player.activeQuests.find { it.id == "skill_quest" }
        assertNotNull(activeQuest)
        assertTrue(activeQuest.objectives[0].isCompleted, "Skill check objective should be completed")
        assertEquals(QuestStatus.COMPLETED, activeQuest.status, "Quest should auto-complete")
    }

    @Test
    fun `deliver objective auto-tracks when item is delivered to NPC`() = runBlocking {
        val item = Entity.Item(
            id = "letter",
            name = "Sealed Letter",
            description = "An important message",
            isPickupable = true,
            itemType = ItemType.MISC
        )

        val npc = Entity.NPC(
            id = "captain",
            name = "Guard Captain",
            description = "The captain of the guard",
            isHostile = false,
            health = 100,
            maxHealth = 100
        )

        val quest = Quest(
            id = "deliver_quest",
            title = "Courier",
            description = "Deliver the letter to the captain",
            objectives = listOf(
                QuestObjective.DeliverItem(
                    id = "obj1",
                    description = "Deliver Sealed Letter to Guard Captain",
                    itemId = "letter",
                    itemName = "Sealed Letter",
                    targetNpcId = "captain",
                    targetNpcName = "Guard Captain"
                )
            ),
            reward = QuestReward(goldAmount = 50, experiencePoints = 25)
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            inventory = listOf(item),
            activeQuests = listOf(quest)
        )

        val world = createTestWorld(player = player, npc = npc)
        val engine = InMemoryGameEngine(world)

        // Give the letter to the captain
        val response = engine.processInput("give letter to captain")
        assertTrue(response.contains("give", ignoreCase = true) || response.contains("captain", ignoreCase = true))

        // Quest objective should be completed
        val activeQuest = engine.getWorldState().player.activeQuests.find { it.id == "deliver_quest" }
        assertNotNull(activeQuest)
        assertTrue(activeQuest.objectives[0].isCompleted, "Deliver objective should be completed")
        assertEquals(QuestStatus.COMPLETED, activeQuest.status, "Quest should auto-complete")
    }

    @Test
    fun `quest with multiple objectives completes when all are done`() = runBlocking {
        val npc = Entity.NPC(
            id = "rat",
            name = "Giant Rat",
            description = "A nasty vermin",
            isHostile = true,
            health = 1,
            maxHealth = 1
        )

        val item = Entity.Item(
            id = "cheese",
            name = "Moldy Cheese",
            description = "Gross but valuable",
            isPickupable = true,
            itemType = ItemType.MISC
        )

        val quest = Quest(
            id = "multi_quest",
            title = "Rat Problem",
            description = "Deal with the rat infestation",
            objectives = listOf(
                QuestObjective.KillEnemy(
                    id = "obj1",
                    description = "Kill Giant Rat",
                    targetNpcId = "rat",
                    targetName = "Giant Rat"
                ),
                QuestObjective.CollectItem(
                    id = "obj2",
                    description = "Collect Moldy Cheese",
                    targetItemId = "cheese",
                    targetName = "Moldy Cheese",
                    quantity = 1
                )
            ),
            reward = QuestReward(experiencePoints = 75, goldAmount = 30)
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            activeQuests = listOf(quest)
        )

        val world = createTestWorld(player = player, npc = npc, items = listOf(item))
        val engine = InMemoryGameEngine(world)

        // First objective not completed yet
        var activeQuest = engine.getWorldState().player.activeQuests.find { it.id == "multi_quest" }!!
        assertTrue(!activeQuest.objectives[0].isCompleted, "First objective should not be completed yet")
        assertTrue(!activeQuest.objectives[1].isCompleted, "Second objective should not be completed yet")
        assertEquals(QuestStatus.ACTIVE, activeQuest.status)

        // Complete first objective: kill the rat
        repeat(5) {
            val response = engine.processInput("attack rat")
            if (response.contains("defeated", ignoreCase = true)) return@repeat
        }

        activeQuest = engine.getWorldState().player.activeQuests.find { it.id == "multi_quest" }!!
        assertTrue(activeQuest.objectives[0].isCompleted, "First objective should be completed")
        assertTrue(!activeQuest.objectives[1].isCompleted, "Second objective should not be completed yet")
        assertEquals(QuestStatus.ACTIVE, activeQuest.status, "Quest should still be active")

        // Complete second objective: take the cheese
        engine.processInput("take cheese")

        activeQuest = engine.getWorldState().player.activeQuests.find { it.id == "multi_quest" }!!
        assertTrue(activeQuest.objectives[0].isCompleted, "First objective should still be completed")
        assertTrue(activeQuest.objectives[1].isCompleted, "Second objective should be completed")
        assertEquals(QuestStatus.COMPLETED, activeQuest.status, "Quest should auto-complete when all objectives done")
    }

    @Test
    fun `player can claim quest rewards`() = runBlocking {
        val completedQuest = Quest(
            id = "completed_quest",
            title = "Test Quest",
            description = "A completed quest",
            objectives = listOf(
                QuestObjective.ExploreRoom(
                    id = "obj1",
                    description = "Done",
                    targetRoomId = "test_room",
                    targetRoomName = "Test",
                    isCompleted = true
                )
            ),
            reward = QuestReward(
                experiencePoints = 100,
                goldAmount = 50
            ),
            status = QuestStatus.COMPLETED
        )

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            activeQuests = listOf(completedQuest),
            experiencePoints = 0,
            gold = 0
        )

        val world = createTestWorld(player = player)
        val engine = InMemoryGameEngine(world)

        // Claim the reward
        val response = engine.processInput("claim completed_quest")
        assertTrue(response.contains("claim", ignoreCase = true) || response.contains("reward", ignoreCase = true))

        // Player should receive rewards
        val playerState = engine.getWorldState().player
        assertTrue(playerState.experiencePoints >= 100, "Player should receive XP reward")
        assertTrue(playerState.gold >= 50, "Player should receive gold reward")

        // Quest should be marked as claimed
        val claimedQuest = playerState.activeQuests.find { it.id == "completed_quest" }
        if (claimedQuest != null) {
            assertEquals(QuestStatus.CLAIMED, claimedQuest.status, "Quest should be marked as claimed")
        }
    }

    @Test
    fun `player can view quest log`() = runBlocking {
        val activeQuest = Quest(
            id = "quest1",
            title = "Active Quest",
            description = "An ongoing quest",
            objectives = listOf(
                QuestObjective.KillEnemy(
                    id = "obj1",
                    description = "Kill something",
                    targetNpcId = "enemy",
                    targetName = "Enemy"
                )
            ),
            reward = QuestReward(experiencePoints = 50)
        )

        val availableQuest = createTestQuest()

        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            activeQuests = listOf(activeQuest)
        )

        val world = createTestWorld(player = player).copy(availableQuests = listOf(availableQuest))
        val engine = InMemoryGameEngine(world)

        // View quest log
        val response = engine.processInput("quests")

        // Should mention active and available quests
        assertTrue(response.contains("quest", ignoreCase = true))
        assertTrue(response.contains("Active Quest", ignoreCase = true) || response.contains("quest1", ignoreCase = true))
    }

    @Test
    fun `player can abandon active quest`() = runBlocking {
        val quest = createTestQuest()
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room",
            activeQuests = listOf(quest)
        )

        val world = createTestWorld(player = player)
        val engine = InMemoryGameEngine(world)

        // Abandon the quest
        val response = engine.processInput("abandon ${quest.id}")
        assertTrue(response.contains("abandon", ignoreCase = true) || response.contains("quest", ignoreCase = true))

        // Quest should be removed from active quests
        val playerState = engine.getWorldState().player
        assertTrue(playerState.activeQuests.none { it.id == quest.id })
    }

    // ========== Helper Functions ==========

    private fun createTestQuest(): Quest {
        return Quest(
            id = "test_quest",
            title = "Test Quest",
            description = "A simple test quest",
            objectives = listOf(
                QuestObjective.ExploreRoom(
                    id = "obj1",
                    description = "Explore somewhere",
                    targetRoomId = "somewhere",
                    targetRoomName = "Somewhere"
                )
            ),
            reward = QuestReward(experiencePoints = 10)
        )
    }

    private fun createTestWorld(
        player: PlayerState = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room"
        ),
        items: List<Entity.Item> = emptyList(),
        features: List<Entity.Feature> = emptyList(),
        npc: Entity.NPC? = null
    ): WorldState {
        val entities = mutableListOf<Entity>()
        entities.addAll(items)
        entities.addAll(features)
        if (npc != null) entities.add(npc)

        val room = Room(
            id = "test_room",
            name = "Test Room",
            traits = listOf("empty", "quiet"),
            entities = entities
        )

        return WorldState(
            rooms = mapOf("test_room" to room),
            players = mapOf(player.id to player)
        )
    }

    private fun createTestWorldWithExits(
        player: PlayerState = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room"
        )
    ): WorldState {
        val room1 = Room(
            id = "test_room",
            name = "Test Room",
            traits = listOf("empty", "quiet"),
            exits = mapOf(Direction.NORTH to "north_room")
        )

        val room2 = Room(
            id = "north_room",
            name = "Northern Chamber",
            traits = listOf("dark", "cold"),
            exits = mapOf(Direction.SOUTH to "test_room")
        )

        return WorldState(
            rooms = mapOf("test_room" to room1, "north_room" to room2),
            players = mapOf(player.id to player)
        )
    }
}
