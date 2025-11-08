package com.jcraw.mud.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for the quest system data models
 */
class QuestSystemTest {
    @Test
    fun `quest should track objective completion`() {
        val objective = QuestObjective.KillEnemy(
            id = "obj1",
            description = "Kill the goblin",
            targetNpcId = "goblin_1",
            targetName = "Goblin",
            isCompleted = false
        )

        val quest = Quest(
            id = "quest1",
            title = "Goblin Slayer",
            description = "Defeat the goblin terrorizing the village",
            objectives = listOf(objective),
            reward = QuestReward(experiencePoints = 100, goldAmount = 50)
        )

        assertFalse(quest.isComplete(), "Quest should not be complete initially")
        assertEquals(QuestStatus.ACTIVE, quest.status)

        // Complete the objective
        val completedObjective = objective.copy(isCompleted = true)
        val updatedQuest = quest.updateObjective(objective.id) { completedObjective }

        assertTrue(updatedQuest.isComplete(), "Quest should be complete when all objectives are done")
        assertEquals("1/1 objectives complete", updatedQuest.getProgressSummary())
    }

    @Test
    fun `quest should support multiple objectives`() {
        val obj1 = QuestObjective.KillEnemy(
            id = "obj1",
            description = "Kill 3 goblins",
            targetNpcId = "goblin",
            targetName = "Goblin",
            isCompleted = false
        )

        val obj2 = QuestObjective.CollectItem(
            id = "obj2",
            description = "Collect goblin ears",
            targetItemId = "goblin_ear",
            targetName = "Goblin Ear",
            quantity = 3,
            currentQuantity = 0,
            isCompleted = false
        )

        val quest = Quest(
            id = "quest1",
            title = "Goblin Hunter",
            description = "Hunt goblins and collect their ears",
            objectives = listOf(obj1, obj2),
            reward = QuestReward(experiencePoints = 200, goldAmount = 100)
        )

        assertEquals("0/2 objectives complete", quest.getProgressSummary())

        // Complete first objective
        val updatedQuest1 = quest.updateObjective(obj1.id) { obj1.copy(isCompleted = true) }
        assertEquals("1/2 objectives complete", updatedQuest1.getProgressSummary())
        assertFalse(updatedQuest1.isComplete())

        // Complete second objective
        val updatedQuest2 = updatedQuest1.updateObjective(obj2.id) { obj2.copy(isCompleted = true) }
        assertEquals("2/2 objectives complete", updatedQuest2.getProgressSummary())
        assertTrue(updatedQuest2.isComplete())
    }

    @Test
    fun `quest lifecycle should progress through status updates`() {
        val quest = Quest(
            id = "quest1",
            title = "Test Quest",
            description = "A test quest",
            objectives = listOf(
                QuestObjective.ExploreRoom(
                    id = "obj1",
                    description = "Explore the dungeon",
                    targetRoomId = "room1",
                    targetRoomName = "Dungeon",
                    isCompleted = true
                )
            ),
            reward = QuestReward(experiencePoints = 50)
        )

        assertEquals(QuestStatus.ACTIVE, quest.status)

        val completedQuest = quest.complete()
        assertEquals(QuestStatus.COMPLETED, completedQuest.status)

        val claimedQuest = completedQuest.claim()
        assertEquals(QuestStatus.CLAIMED, claimedQuest.status)
    }

    @Test
    fun `player should be able to manage quests`() {
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "room1"
        )

        val quest = Quest(
            id = "quest1",
            title = "Test Quest",
            description = "A test",
            objectives = listOf(
                QuestObjective.ExploreRoom(
                    id = "obj1",
                    description = "Explore",
                    targetRoomId = "room1",
                    targetRoomName = "Room",
                    isCompleted = false
                )
            ),
            reward = QuestReward(experiencePoints = 100, goldAmount = 50)
        )

        // Add quest
        val playerWithQuest = player.addQuest(quest)
        assertTrue(playerWithQuest.hasQuest("quest1"))
        assertEquals(1, playerWithQuest.activeQuests.size)

        // Remove quest
        val playerWithoutQuest = playerWithQuest.removeQuest("quest1")
        assertFalse(playerWithoutQuest.hasQuest("quest1"))
        assertEquals(0, playerWithoutQuest.activeQuests.size)
    }

    @Test
    fun `player should be able to claim quest rewards`() {
        val player = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "room1",
            experiencePoints = 0,
            gold = 0
        )

        val rewardItem = Entity.Item(
            id = "reward_sword",
            name = "Reward Sword",
            description = "A shiny sword",
            damageBonus = 10
        )

        val quest = Quest(
            id = "quest1",
            title = "Test Quest",
            description = "A test",
            objectives = listOf(
                QuestObjective.ExploreRoom(
                    id = "obj1",
                    description = "Explore",
                    targetRoomId = "room1",
                    targetRoomName = "Room",
                    isCompleted = true  // Already complete
                )
            ),
            reward = QuestReward(
                experiencePoints = 100,
                goldAmount = 50,
                items = listOf(rewardItem)
            )
        )

        val playerWithQuest = player.addQuest(quest)

        // Claim reward
        val playerWithReward = playerWithQuest.claimQuestReward("quest1")
        assertEquals(100, playerWithReward.experiencePoints)
        assertEquals(50, playerWithReward.gold)
        assertEquals(1, playerWithReward.inventory.size)
        assertEquals("Reward Sword", playerWithReward.inventory[0].name)
        assertEquals(QuestStatus.CLAIMED, playerWithReward.getQuest("quest1")?.status)
    }

    @Test
    fun `world state should manage available quests`() {
        val worldState = WorldState(
            players = mapOf()
        )

        val quest = Quest(
            id = "quest1",
            title = "Test Quest",
            description = "A test",
            objectives = emptyList(),
            reward = QuestReward(experiencePoints = 50)
        )

        // Add quest
        val updatedWorld = worldState.addAvailableQuest(quest)
        assertEquals(1, updatedWorld.availableQuests.size)
        assertEquals(quest, updatedWorld.getAvailableQuest("quest1"))

        // Remove quest
        val worldWithoutQuest = updatedWorld.removeAvailableQuest("quest1")
        assertEquals(0, worldWithoutQuest.availableQuests.size)
        assertEquals(null, worldWithoutQuest.getAvailableQuest("quest1"))
    }
}
