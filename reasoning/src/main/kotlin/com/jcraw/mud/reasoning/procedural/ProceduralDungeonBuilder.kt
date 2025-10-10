package com.jcraw.mud.reasoning.procedural

import com.jcraw.mud.core.PlayerId
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.Room
import com.jcraw.mud.core.Stats
import com.jcraw.mud.core.WorldState
import kotlin.random.Random

/**
 * Configuration for procedural dungeon generation
 */
data class DungeonConfig(
    val theme: DungeonTheme = DungeonTheme.CRYPT,
    val roomCount: Int = 10,
    val branchingFactor: Double = 0.3,
    val seed: Long? = null  // Optional seed for reproducible dungeons
)

/**
 * Main orchestrator for procedural dungeon generation
 * Coordinates all generators to create a complete dungeon
 */
class ProceduralDungeonBuilder(
    private val config: DungeonConfig = DungeonConfig()
) {

    private val random = config.seed?.let { Random(it) } ?: Random.Default
    private val layoutGenerator = DungeonLayoutGenerator(random)
    private val roomGenerator = RoomGenerator(config.theme, random)
    private val itemGenerator = ItemGenerator(config.theme, random)
    private val npcGenerator = NPCGenerator(config.theme, random)

    /**
     * Generate a complete dungeon and return initial WorldState
     */
    fun generateDungeon(): WorldState {
        println("🎲 Generating ${config.theme.displayName} with ${config.roomCount} rooms...")

        // Generate layout (graph of connected rooms)
        val layout = layoutGenerator.generateLayout(
            roomCount = config.roomCount,
            branchingFactor = config.branchingFactor
        )

        // Generate rooms with entities
        val rooms = layout.associate { node ->
            val room = when {
                node.isEntrance -> generateEntranceRoom(node)
                node.isBoss -> generateBossRoom(node)
                else -> generateStandardRoom(node)
            }
            node.id to room
        }

        // Get entrance ID
        val entranceId = layout.first { it.isEntrance }.id

        // Create default player
        val playerId: PlayerId = "player1"
        val player = PlayerState(
            id = playerId,
            name = "Adventurer",
            currentRoomId = entranceId,
            health = 100,
            maxHealth = 100,
            stats = Stats(
                strength = 14,
                dexterity = 12,
                constitution = 14,
                intelligence = 13,
                wisdom = 11,
                charisma = 10
            )
        )

        println("✅ Dungeon generated! Starting room: $entranceId")

        return WorldState(
            rooms = rooms,
            players = mapOf(playerId to player)
        )
    }

    /**
     * Generate entrance room with minimal danger
     */
    private fun generateEntranceRoom(node: DungeonNode): Room {
        val room = roomGenerator.generateEntranceRoom(
            id = node.id,
            exits = node.connections
        )

        // Entrance has no enemies, maybe a friendly NPC and some basic loot
        val entities = mutableListOf<com.jcraw.mud.core.Entity>()

        // 50% chance of friendly NPC
        if (random.nextBoolean()) {
            npcGenerator.generateFriendlyNPC("${node.id}_npc")?.let { entities.add(it) }
        }

        // Add 1-2 basic items
        val basicItems = listOf(
            itemGenerator.generateConsumable("${node.id}_potion"),
            itemGenerator.generateTreasure("${node.id}_treasure")
        )
        entities.addAll(basicItems.take(random.nextInt(1, 3)))

        return room.copy(entities = entities)
    }

    /**
     * Generate boss room with powerful enemy
     */
    private fun generateBossRoom(node: DungeonNode): Room {
        val room = roomGenerator.generateBossRoom(
            id = node.id,
            exits = node.connections
        )

        val entities = mutableListOf<com.jcraw.mud.core.Entity>()

        // Always has a boss NPC
        entities.add(npcGenerator.generateBoss("${node.id}_boss"))

        // Boss room has valuable loot (2-4 items, weighted toward good stuff)
        repeat(random.nextInt(2, 5)) { index ->
            val itemId = "${node.id}_treasure_$index"
            val roll = random.nextInt(100)

            val item = when {
                roll < 40 -> itemGenerator.generateWeapon(itemId)      // 40% weapon
                roll < 70 -> itemGenerator.generateArmor(itemId)       // 30% armor
                else -> itemGenerator.generateTreasure(itemId)         // 30% treasure
            }

            entities.add(item)
        }

        return room.copy(entities = entities)
    }

    /**
     * Generate standard room with random entities
     */
    private fun generateStandardRoom(node: DungeonNode): Room {
        val room = roomGenerator.generateRoom(
            id = node.id,
            exits = node.connections
        )

        val entities = mutableListOf<com.jcraw.mud.core.Entity>()

        // Calculate power level based on distance from entrance (rough approximation)
        val powerLevel = minOf(3, 1 + (node.x * node.x + node.y * node.y) / 10)

        // Add NPC (60% none, 20% hostile, 20% friendly)
        npcGenerator.generateRoomNPC(node.id, powerLevel)?.let { entities.add(it) }

        // Add loot (0-3 random items)
        entities.addAll(itemGenerator.generateRoomLoot(node.id))

        return room.copy(entities = entities)
    }

    /**
     * Generate dungeon with custom theme
     */
    companion object {
        fun generateCrypt(roomCount: Int = 10, seed: Long? = null): WorldState {
            return ProceduralDungeonBuilder(
                DungeonConfig(theme = DungeonTheme.CRYPT, roomCount = roomCount, seed = seed)
            ).generateDungeon()
        }

        fun generateCastle(roomCount: Int = 10, seed: Long? = null): WorldState {
            return ProceduralDungeonBuilder(
                DungeonConfig(theme = DungeonTheme.CASTLE, roomCount = roomCount, seed = seed)
            ).generateDungeon()
        }

        fun generateCave(roomCount: Int = 10, seed: Long? = null): WorldState {
            return ProceduralDungeonBuilder(
                DungeonConfig(theme = DungeonTheme.CAVE, roomCount = roomCount, seed = seed)
            ).generateDungeon()
        }

        fun generateTemple(roomCount: Int = 10, seed: Long? = null): WorldState {
            return ProceduralDungeonBuilder(
                DungeonConfig(theme = DungeonTheme.TEMPLE, roomCount = roomCount, seed = seed)
            ).generateDungeon()
        }
    }
}
