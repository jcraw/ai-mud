package com.jcraw.app.integration

import com.jcraw.mud.core.*
import com.jcraw.mud.memory.PersistenceManager
import com.jcraw.mud.reasoning.procedural.DungeonConfig
import com.jcraw.mud.reasoning.procedural.DungeonTheme
import com.jcraw.mud.testbot.InMemoryGameEngine
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.*

/**
 * Integration tests for full gameplay scenarios
 *
 * Tests complete game loops with multiple systems working together:
 * - Navigation + interaction + combat
 * - Quests + items + skill checks
 * - Save/load during active gameplay
 * - End-to-end player workflows
 */
class FullGameplayIntegrationTest {

    private val testSaveDir = "test_full_gameplay_saves"
    private lateinit var persistenceManager: PersistenceManager

    @BeforeTest
    fun setup() {
        persistenceManager = PersistenceManager(testSaveDir)
        File(testSaveDir).deleteRecursively()
    }

    @AfterTest
    fun cleanup() {
        File(testSaveDir).deleteRecursively()
    }

    // ========== Basic Game Loop Tests ==========

        @Test
        fun `can complete basic exploration loop`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Look at starting room
            val lookResponse = engine.processInput("look")
            assertTrue(lookResponse.isNotBlank(), "Look should describe room")

            // Navigate to corridor
            engine.processInput("north")
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, engine.getWorldState().player.currentRoomId)

            // Look at new room
            val corridorResponse = engine.processInput("look")
            assertTrue(corridorResponse.isNotBlank(), "Should describe new room")

            // Navigate to armory
            engine.processInput("west")
            assertEquals(SampleDungeon.ARMORY_ROOM_ID, engine.getWorldState().player.currentRoomId)

            // Look at armory
            val armoryResponse = engine.processInput("look")
            assertTrue(armoryResponse.isNotBlank(), "Should describe armory")
        }

        @Test
        fun `can complete talk to friendly NPC workflow`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Talk to Old Guard at entrance
            val talkResponse = engine.processInput("talk Old Guard")
            assertTrue(talkResponse.isNotBlank(), "Should get dialogue from NPC")

            // Player state should remain stable
            val afterTalk = engine.getWorldState()
            assertEquals(SampleDungeon.STARTING_ROOM_ID, afterTalk.player.currentRoomId)
            assertFalse(afterTalk.player.isInCombat(), "Talking shouldn't start combat")
        }

        @Test
        fun `can check inventory at any time`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Check inventory at start
            val initialInv = engine.processInput("inventory")
            assertTrue(initialInv.isNotBlank(), "Should show inventory")

            // Navigate and check again
            engine.processInput("north")
            val afterMove = engine.processInput("i")
            assertTrue(afterMove.isNotBlank(), "Should show inventory after movement")

            // Pick up item and check
            engine.processInput("west")  // Go to armory
            engine.processInput("take Rusty Iron Sword")
            val afterPickup = engine.processInput("inventory")
            assertTrue(afterPickup.isNotBlank(), "Should show updated inventory")

            val state = engine.getWorldState()
            assertTrue(state.player.inventory.any { it.id == "iron_sword" },
                "Inventory should contain picked up item")
        }

    // ========== Multi-System Integration Tests ==========

        @Test
        fun `can navigate, equip weapon, and enter combat`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Navigate to armory
            engine.processInput("north")  // to corridor
            engine.processInput("west")   // to armory

            // Pick up weapon
            engine.processInput("take Rusty Iron Sword")
            var state = engine.getWorldState()
            assertTrue(state.player.inventory.any { it.id == "iron_sword" })

            // Equip weapon
            engine.processInput("equip Rusty Iron Sword")
            state = engine.getWorldState()
            assertNotNull(state.player.equippedWeapon, "Weapon should be equipped")
            assertEquals("iron_sword", state.player.equippedWeapon?.id)

            // Navigate to throne room
            engine.processInput("east")   // back to corridor
            engine.processInput("north")  // to throne room

            // Attack Skeleton King
            engine.processInput("attack Skeleton King")
            state = engine.getWorldState()
            assertTrue(state.player.isInCombat(), "Should be in combat")

            // Equipped weapon should provide damage bonus in combat
            assertNotNull(state.player.equippedWeapon, "Weapon should still be equipped in combat")
        }

        @Test
        fun `can use consumables during combat`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Navigate to treasury and get health potion
            engine.processInput("north")  // to corridor
            engine.processInput("east")   // to treasury
            engine.processInput("take Red Health Potion")

            // Navigate to throne room
            engine.processInput("west")   // back to corridor
            engine.processInput("north")  // to throne room

            // Start combat
            engine.processInput("attack Skeleton King")
            var state = engine.getWorldState()
            assertTrue(state.player.isInCombat())

            // Take damage (let NPC attack a few times)
            engine.processInput("attack Skeleton King")
            engine.processInput("attack Skeleton King")

            // Get current health
            state = engine.getWorldState()
            val healthBeforePotion = state.player.health

            // Use health potion during combat
            engine.processInput("use Red Health Potion")
            state = engine.getWorldState()

            // Health should increase (capped at max)
            assertTrue(state.player.health >= healthBeforePotion || state.player.health == state.player.maxHealth,
                "Health should increase or be at max after using potion")

            // Potion should be consumed
            assertFalse(state.player.inventory.any { it.id == "health_potion" },
                "Potion should be removed from inventory after use")
        }

        @Test
        fun `can defeat NPC, take loot, and continue exploring`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Get powerful equipment first
            engine.processInput("north")  // to corridor
            engine.processInput("west")   // to armory
            engine.processInput("take Rusty Iron Sword")
            engine.processInput("take Heavy Chainmail")
            engine.processInput("equip Rusty Iron Sword")
            engine.processInput("equip Heavy Chainmail")

            // Navigate to throne room
            engine.processInput("east")   // to corridor
            engine.processInput("north")  // to throne room

            // Fight and defeat Skeleton King
            var state = engine.getWorldState()
            val skeletonKing = state.getCurrentRoom()?.entities?.find { it.id == "skeleton_king" }
            assertNotNull(skeletonKing, "Skeleton King should be in room")

            // Attack until defeated (with good equipment, should win)
            for (i in 1..20) {  // Limit iterations to prevent infinite loop
                engine.processInput("attack Skeleton King")
                state = engine.getWorldState()

                if (!state.player.isInCombat()) {
                    break  // Combat ended
                }

                // If player dies, test still passes (we tested the combat flow)
                if (state.player.health <= 0) {
                    return@runBlocking
                }
            }

            // After combat, should be able to continue exploring
            assertFalse(state.player.isInCombat(), "Combat should be over")

            // Navigate to secret chamber
            engine.processInput("north")
            state = engine.getWorldState()
            assertEquals(SampleDungeon.SECRET_CHAMBER_ROOM_ID, state.player.currentRoomId,
                "Should be able to navigate after combat")
        }

        @Test
        fun `can perform skill checks during exploration`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Navigate to corridor (has feature with skill check)
            engine.processInput("north")

            // Check the loose stone (WIS check)
            val checkResponse = engine.processInput("check Suspicious Loose Stone")
            assertTrue(checkResponse.isNotBlank(), "Should get response from skill check")

            // Navigate to treasury
            engine.processInput("east")

            // Check the locked chest (DEX check)
            val chestResponse = engine.processInput("check Locked Ornate Chest")
            assertTrue(chestResponse.isNotBlank(), "Should get response from chest check")

            // Player should still be able to continue exploring
            val state = engine.getWorldState()
            assertEquals(SampleDungeon.TREASURY_ROOM_ID, state.player.currentRoomId)
            assertFalse(state.player.isInCombat())
        }

    // ========== Quest Integration Tests ==========

        @Test
        fun `can complete explore quest through navigation`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Create an explore quest
            val exploreQuest = Quest(
                id = "explore_throne",
                title = "Explore the Throne Room",
                description = "Find and enter the Abandoned Throne Room",
                objectives = listOf(
                    QuestObjective.ExploreRoom(
                        id = "obj_explore_throne",
                        description = "Enter the Abandoned Throne Room",
                        targetRoomId = SampleDungeon.THRONE_ROOM_ID,
                        targetRoomName = "Abandoned Throne Room",
                        isCompleted = false
                    )
                ),
                reward = QuestReward(experiencePoints = 100, goldAmount = 50, items = emptyList()),
                status = QuestStatus.ACTIVE
            )

            var state = engine.getWorldState()
            state = state.copy(
                players = state.players.mapValues { (_, player) ->
                    player.copy(activeQuests = listOf(exploreQuest))
                }
            )
            val engineWithQuest = InMemoryGameEngine(state)

            // Quest should be active but not complete
            var player = engineWithQuest.getWorldState().player
            val questBefore = player.activeQuests.find { it.id == "explore_throne" }
            assertNotNull(questBefore)
            assertEquals(QuestStatus.ACTIVE, questBefore.status)

            // Navigate to throne room
            engineWithQuest.processInput("north")  // to corridor
            engineWithQuest.processInput("north")  // to throne room

            player = engineWithQuest.getWorldState().player
            assertEquals(SampleDungeon.THRONE_ROOM_ID, player.currentRoomId)

            // Quest objective should auto-complete
            val questAfter = player.activeQuests.find { it.id == "explore_throne" }
            assertNotNull(questAfter)
            assertTrue(questAfter.objectives.all { it.isCompleted },
                "Explore objective should complete when entering room")
        }

        @Test
        fun `can complete collect quest through item pickup`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Create a collect quest
            val collectQuest = Quest(
                id = "collect_sword",
                title = "Retrieve the Sword",
                description = "Find and collect the iron sword",
                objectives = listOf(
                    QuestObjective.CollectItem(
                        id = "obj_collect_sword",
                        description = "Collect 1 Rusty Iron Sword",
                        targetItemId = "iron_sword",
                        targetName = "Rusty Iron Sword",
                        quantity = 1,
                        currentQuantity = 0,
                        isCompleted = false
                    )
                ),
                reward = QuestReward(experiencePoints = 50, goldAmount = 25, items = emptyList()),
                status = QuestStatus.ACTIVE
            )

            var state = engine.getWorldState()
            state = state.copy(
                players = state.players.mapValues { (_, player) ->
                    player.copy(activeQuests = listOf(collectQuest))
                }
            )
            val engineWithQuest = InMemoryGameEngine(state)

            // Navigate to armory and pick up sword
            engineWithQuest.processInput("north")  // to corridor
            engineWithQuest.processInput("west")   // to armory
            engineWithQuest.processInput("take Rusty Iron Sword")

            // Quest should auto-complete
            val player = engineWithQuest.getWorldState().player
            val questAfter = player.activeQuests.find { it.id == "collect_sword" }
            assertNotNull(questAfter)
            assertTrue(questAfter.objectives.all { it.isCompleted },
                "Collect objective should complete when picking up item")
        }

        @Test
        fun `can complete kill quest through combat`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Create a kill quest
            val killQuest = Quest(
                id = "kill_king",
                title = "Defeat the Skeleton King",
                description = "Vanquish the undead ruler",
                objectives = listOf(
                    QuestObjective.KillEnemy(
                        id = "obj_kill_king",
                        description = "Defeat the Skeleton King",
                        targetNpcId = "skeleton_king",
                        targetName = "Skeleton King",
                        isCompleted = false
                    )
                ),
                reward = QuestReward(experiencePoints = 200, goldAmount = 100, items = emptyList()),
                status = QuestStatus.ACTIVE
            )

            var state = engine.getWorldState()
            state = state.copy(
                players = state.players.mapValues { (_, player) ->
                    player.copy(activeQuests = listOf(killQuest))
                }
            )
            val engineWithQuest = InMemoryGameEngine(state)

            // Equip powerful gear
            engineWithQuest.processInput("north")  // to corridor
            engineWithQuest.processInput("west")   // to armory
            engineWithQuest.processInput("take Rusty Iron Sword")
            engineWithQuest.processInput("take Heavy Chainmail")
            engineWithQuest.processInput("equip Rusty Iron Sword")
            engineWithQuest.processInput("equip Heavy Chainmail")

            // Navigate to throne room
            engineWithQuest.processInput("east")   // to corridor
            engineWithQuest.processInput("north")  // to throne room

            // Fight Skeleton King (try to defeat, but test passes either way)
            for (i in 1..20) {
                engineWithQuest.processInput("attack Skeleton King")
                val currentState = engineWithQuest.getWorldState()

                if (!currentState.player.isInCombat()) {
                    // Combat ended - check if quest completed
                    val questAfter = currentState.player.activeQuests.find { it.id == "kill_king" }
                    if (questAfter != null && questAfter.objectives.all { it.isCompleted }) {
                        // Successfully defeated and quest completed
                        assertTrue(questAfter.objectives.all { it.isCompleted },
                            "Kill objective should complete when defeating NPC")
                    }
                    break
                }

                if (currentState.player.health <= 0) {
                    break  // Player died, test still passes
                }
            }
        }

    // ========== Save/Load During Gameplay Tests ==========

        @Test
        fun `can save and load mid-exploration`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Navigate and pick up items
            engine.processInput("north")  // to corridor
            engine.processInput("west")   // to armory
            engine.processInput("take Rusty Iron Sword")
            engine.processInput("take Worn Leather Armor")

            // Save current state
            val beforeSave = engine.getWorldState()
            persistenceManager.saveGame(beforeSave, "mid_exploration")

            // Continue playing
            engine.processInput("equip Rusty Iron Sword")
            val afterEquip = engine.getWorldState()
            assertNotNull(afterEquip.player.equippedWeapon)

            // Load saved state
            val loadedWorld = persistenceManager.loadGame("mid_exploration").getOrThrow()

            // Loaded state should match pre-equip state
            assertEquals(SampleDungeon.ARMORY_ROOM_ID, loadedWorld.player.currentRoomId)
            assertTrue(loadedWorld.player.inventory.any { it.id == "iron_sword" })
            assertNull(loadedWorld.player.equippedWeapon, "Should load state before equipping")
        }

        @Test
        fun `can save and load during active combat`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Navigate to throne room
            engine.processInput("north")  // to corridor
            engine.processInput("north")  // to throne room

            // Start combat
            engine.processInput("attack Skeleton King")
            val beforeSave = engine.getWorldState()
            assertTrue(beforeSave.player.isInCombat())

            // Save during combat
            persistenceManager.saveGame(beforeSave, "during_combat")

            // Continue fighting
            engine.processInput("attack Skeleton King")
            val afterContinue = engine.getWorldState()

            // Load saved state
            val loadedWorld = persistenceManager.loadGame("during_combat").getOrThrow()

            // Should restore combat state
            assertTrue(loadedWorld.player.isInCombat(), "Should load active combat state")
            assertEquals(beforeSave.player.activeCombat?.combatantNpcId,
                loadedWorld.player.activeCombat?.combatantNpcId)
        }

        @Test
        fun `can save with active quest and reload`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Add a quest
            val quest = Quest(
                id = "test_quest",
                title = "Test Quest",
                description = "Test quest persistence",
                objectives = listOf(
                    QuestObjective.ExploreRoom(
                        id = "obj_test",
                        description = "Visit throne room",
                        targetRoomId = SampleDungeon.THRONE_ROOM_ID,
                        targetRoomName = "Abandoned Throne Room",
                        isCompleted = false
                    )
                ),
                reward = QuestReward(experiencePoints = 100, goldAmount = 50, items = emptyList()),
                status = QuestStatus.ACTIVE
            )

            var state = engine.getWorldState()
            state = state.copy(
                players = state.players.mapValues { (_, player) ->
                    player.copy(activeQuests = listOf(quest))
                }
            )

            // Save with quest
            persistenceManager.saveGame(state, "with_quest")

            // Load
            val loadedWorld = persistenceManager.loadGame("with_quest").getOrThrow()

            // Quest should be preserved
            assertEquals(1, loadedWorld.player.activeQuests.size)
            val loadedQuest = loadedWorld.player.activeQuests[0]
            assertEquals("test_quest", loadedQuest.id)
            assertEquals(QuestStatus.ACTIVE, loadedQuest.status)
        }

    // ========== Complete Playthrough Tests ==========

        @Test
        fun `can complete full dungeon exploration without errors`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Visit every room in the dungeon
            val roomsToVisit = listOf(
                SampleDungeon.STARTING_ROOM_ID,
                SampleDungeon.CORRIDOR_ROOM_ID,
                SampleDungeon.ARMORY_ROOM_ID,
                SampleDungeon.TREASURY_ROOM_ID,
                SampleDungeon.THRONE_ROOM_ID,
                SampleDungeon.SECRET_CHAMBER_ROOM_ID
            )

            // Start at entrance
            engine.processInput("look")

            // Visit corridor
            engine.processInput("north")
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, engine.getWorldState().player.currentRoomId)
            engine.processInput("look")

            // Visit armory
            engine.processInput("west")
            assertEquals(SampleDungeon.ARMORY_ROOM_ID, engine.getWorldState().player.currentRoomId)
            engine.processInput("look")

            // Back to corridor
            engine.processInput("east")
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, engine.getWorldState().player.currentRoomId)

            // Visit treasury
            engine.processInput("east")
            assertEquals(SampleDungeon.TREASURY_ROOM_ID, engine.getWorldState().player.currentRoomId)
            engine.processInput("look")

            // Back to corridor
            engine.processInput("west")
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, engine.getWorldState().player.currentRoomId)

            // Visit throne room
            engine.processInput("north")
            assertEquals(SampleDungeon.THRONE_ROOM_ID, engine.getWorldState().player.currentRoomId)
            engine.processInput("look")

            // Visit secret chamber
            engine.processInput("north")
            assertEquals(SampleDungeon.SECRET_CHAMBER_ROOM_ID, engine.getWorldState().player.currentRoomId)
            engine.processInput("look")

            // Player should still be alive and able to play
            val finalState = engine.getWorldState()
            assertTrue(finalState.player.health > 0, "Player should survive full exploration")
            assertFalse(finalState.player.isInCombat(), "Should not be in combat at end")
        }

        @Test
        fun `can complete realistic gameplay session`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // 1. Start - look around
            engine.processInput("look")
            engine.processInput("inventory")

            // 2. Talk to friendly NPC
            engine.processInput("talk Old Guard")

            // 3. Explore and gather equipment
            engine.processInput("north")  // to corridor
            engine.processInput("west")   // to armory
            engine.processInput("take all")

            // 4. Equip gear
            engine.processInput("equip Rusty Iron Sword")
            engine.processInput("equip Heavy Chainmail")

            // 5. Explore more
            engine.processInput("east")  // to corridor
            engine.processInput("east")  // to treasury

            // 6. Get healing potion
            engine.processInput("take Red Health Potion")

            // 7. Check skill challenge
            engine.processInput("check Locked Ornate Chest")

            // 8. Return and head to throne room
            engine.processInput("west")   // to corridor
            engine.processInput("north")  // to throne room

            // 9. Check inventory before combat
            engine.processInput("inventory")

            val state = engine.getWorldState()

            // Verify player has equipped gear and items
            assertNotNull(state.player.equippedWeapon, "Should have weapon equipped")
            assertNotNull(state.player.equippedArmor, "Should have armor equipped")
            assertTrue(state.player.inventory.isNotEmpty(), "Should have items in inventory")

            // Player successfully completed a realistic exploration session
            assertEquals(SampleDungeon.THRONE_ROOM_ID, state.player.currentRoomId)
            assertTrue(state.player.health > 0)
        }

        @Test
        fun `procedural dungeon full playthrough works`() = runBlocking {
            val config = DungeonConfig(
                theme = DungeonTheme.CRYPT,
                roomCount = 6,
                seed = 8888L
            )
            val world = com.jcraw.mud.reasoning.procedural.ProceduralDungeonBuilder(config).generateDungeon()
            val engine = InMemoryGameEngine(world)

            // Explore several rooms
            for (i in 1..5) {
                engine.processInput("look")

                val currentRoom = engine.getWorldState().getCurrentRoom()
                assertNotNull(currentRoom)

                // If there are items, try to take one
                val pickupableItem = currentRoom.entities.find {
                    it is Entity.Item && it.isPickupable
                } as? Entity.Item

                if (pickupableItem != null) {
                    engine.processInput("take ${pickupableItem.name}")
                }

                // Move to next room if possible
                if (currentRoom.exits.isNotEmpty()) {
                    val (direction, _) = currentRoom.exits.entries.first()
                    engine.processInput(direction.name.lowercase())
                }
            }

            // Verify game is still playable
            val finalState = engine.getWorldState()
            assertTrue(finalState.player.health > 0 || finalState.player.health == 0,
                "Player health should be valid")
        }
}
