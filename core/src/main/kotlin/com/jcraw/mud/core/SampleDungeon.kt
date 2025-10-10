package com.jcraw.mud.core

/**
 * Sample dungeon for MVP testing - simple interconnected room layout with traits for LLM generation
 */
object SampleDungeon {

    const val STARTING_ROOM_ID = "entrance"

    private val entrance = Room(
        id = "entrance",
        name = "Dungeon Entrance",
        traits = listOf(
            "crumbling stone archway",
            "moss-covered walls",
            "flickering torchlight",
            "musty air",
            "ancient runes carved into doorframe"
        ),
        exits = mapOf(Direction.NORTH to "corridor"),
        entities = listOf(
            Entity.NPC(
                id = "old_guard",
                name = "Old Guard",
                description = "A weathered guard in tarnished armor, watching over the entrance",
                isHostile = false,
                health = 30,
                maxHealth = 50
            )
        )
    )

    private val corridor = Room(
        id = "corridor",
        name = "Dark Corridor",
        traits = listOf(
            "narrow stone passage",
            "echoing footsteps",
            "dripping water sounds",
            "spider webs in corners",
            "worn flagstone floor"
        ),
        exits = mapOf(
            Direction.SOUTH to "entrance",
            Direction.EAST to "treasury",
            Direction.WEST to "armory",
            Direction.NORTH to "throne_room"
        )
    )

    private val treasury = Room(
        id = "treasury",
        name = "Ancient Treasury",
        traits = listOf(
            "gleaming gold coins scattered",
            "ornate treasure chests",
            "jeweled artifacts on pedestals",
            "magical aura in the air",
            "dust motes dancing in shaft of light"
        ),
        exits = mapOf(Direction.WEST to "corridor"),
        entities = listOf(
            Entity.Item(
                id = "gold_pouch",
                name = "Heavy Gold Pouch",
                description = "A leather pouch heavy with gold coins"
            )
        )
    )

    private val armory = Room(
        id = "armory",
        name = "Forgotten Armory",
        traits = listOf(
            "rusty weapon racks",
            "tarnished armor stands",
            "smell of old leather and metal",
            "cobwebs covering shields",
            "ancient battle standards hanging"
        ),
        exits = mapOf(Direction.EAST to "corridor"),
        entities = listOf(
            Entity.Item(
                id = "iron_sword",
                name = "Rusty Iron Sword",
                description = "An old iron sword, still sharp despite the rust"
            )
        )
    )

    private val throneRoom = Room(
        id = "throne_room",
        name = "Abandoned Throne Room",
        traits = listOf(
            "massive stone throne",
            "faded royal tapestries",
            "cracked marble floor",
            "ominous shadows",
            "cold draft from unseen passages",
            "skeleton slumped on throne"
        ),
        exits = mapOf(
            Direction.SOUTH to "corridor",
            Direction.NORTH to "secret_chamber"
        ),
        entities = listOf(
            Entity.NPC(
                id = "skeleton_king",
                name = "Skeleton King",
                description = "The animated remains of an ancient ruler",
                isHostile = true,
                health = 50,
                maxHealth = 50
            )
        )
    )

    private val secretChamber = Room(
        id = "secret_chamber",
        name = "Hidden Chamber",
        traits = listOf(
            "mysteriously lit crystal formations",
            "ancient magical circles etched in floor",
            "floating arcane symbols",
            "humming with magical energy",
            "portal-like shimmer in far wall"
        ),
        exits = mapOf(Direction.SOUTH to "throne_room")
    )

    /**
     * Complete dungeon map as immutable collection
     */
    val rooms: Map<RoomId, Room> = mapOf(
        entrance.id to entrance,
        corridor.id to corridor,
        treasury.id to treasury,
        armory.id to armory,
        throneRoom.id to throneRoom,
        secretChamber.id to secretChamber
    )

    /**
     * Creates initial world state with player at entrance
     */
    fun createInitialWorldState(): WorldState = WorldState(
        rooms = rooms,
        player = PlayerState(
            name = "Adventurer",
            currentRoomId = STARTING_ROOM_ID,
            health = 100,
            maxHealth = 100
        )
    )
}