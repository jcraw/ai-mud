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
                maxHealth = 50,
                stats = Stats(
                    strength = 12,      // Average strength for an aging warrior
                    dexterity = 8,      // Slowed by age
                    constitution = 14,  // Tough and resilient
                    intelligence = 10,  // Average
                    wisdom = 16,        // Experienced and perceptive
                    charisma = 13       // Friendly and talkative
                ),
                persuasionChallenge = SkillChallenge(
                    statType = StatType.CHARISMA,
                    difficulty = Difficulty.EASY,
                    description = "The Old Guard seems willing to share information if asked nicely",
                    successDescription = "The Old Guard smiles warmly. 'Ah, a polite adventurer! Let me tell you - there's a hidden passage in the secret chamber beyond the throne room. Look for the stone door and you'll find ancient treasures beyond!'",
                    failureDescription = "The Old Guard shakes his head. 'Sorry, friend. Can't help you with that. Orders are orders.'"
                )
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
        ),
        entities = listOf(
            Entity.Feature(
                id = "loose_stone",
                name = "Suspicious Loose Stone",
                description = "A stone in the wall that seems slightly out of place",
                isInteractable = true,
                skillChallenge = SkillChallenge(
                    statType = StatType.WISDOM,
                    difficulty = Difficulty.EASY,
                    description = "Your perception tells you something is unusual about this stone",
                    successDescription = "You carefully examine the stone and notice faint scratch marks around it. Pressing it reveals a small hidden alcove containing a silver key!",
                    failureDescription = "You inspect the stone but can't figure out what makes it special. Perhaps you're overthinking it."
                )
            )
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
                description = "A leather pouch heavy with gold coins",
                itemType = ItemType.MISC
            ),
            Entity.Item(
                id = "health_potion",
                name = "Red Health Potion",
                description = "A glowing red potion that restores vitality",
                isUsable = true,
                itemType = ItemType.CONSUMABLE,
                healAmount = 30,
                isConsumable = true
            ),
            Entity.Feature(
                id = "locked_chest",
                name = "Locked Ornate Chest",
                description = "A beautifully carved chest with an intricate lock mechanism",
                isInteractable = true,
                skillChallenge = SkillChallenge(
                    statType = StatType.DEXTERITY,
                    difficulty = Difficulty.MEDIUM,
                    description = "The lock is complex but could be picked with nimble fingers",
                    successDescription = "With deft movements, you manipulate the lock tumblers until you hear a satisfying *click*. The chest opens, revealing a glittering ruby inside!",
                    failureDescription = "Your fingers fumble with the lock picks. The mechanism is too complex for you right now."
                )
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
                description = "An old iron sword, still sharp despite the rust",
                isUsable = true,
                itemType = ItemType.WEAPON,
                damageBonus = 5
            ),
            Entity.Item(
                id = "steel_dagger",
                name = "Sharp Steel Dagger",
                description = "A well-balanced dagger with a keen edge",
                isUsable = true,
                itemType = ItemType.WEAPON,
                damageBonus = 3
            ),
            Entity.Item(
                id = "leather_armor",
                name = "Worn Leather Armor",
                description = "Supple leather armor, worn but still protective",
                isUsable = true,
                itemType = ItemType.ARMOR,
                defenseBonus = 2
            ),
            Entity.Item(
                id = "chainmail",
                name = "Heavy Chainmail",
                description = "A suit of interlocking metal rings, heavy but protective",
                isUsable = true,
                itemType = ItemType.ARMOR,
                defenseBonus = 4
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
                maxHealth = 50,
                stats = Stats(
                    strength = 16,      // Powerful undead warrior
                    dexterity = 14,     // Quick and precise
                    constitution = 10,  // Undead don't tire
                    intelligence = 12,  // Retains tactical knowledge
                    wisdom = 14,        // Ancient experience
                    charisma = 6        // Terrifying, not charming
                ),
                intimidationChallenge = SkillChallenge(
                    statType = StatType.CHARISMA,
                    difficulty = Difficulty.HARD,
                    description = "The Skeleton King's hollow eyes glare at you. Perhaps a show of dominance could make him back down",
                    successDescription = "Your fierce display of power makes the Skeleton King hesitate. His bones rattle as he backs away from the throne. 'Very well, mortal. You have proven your strength. I shall let you pass... this time.' The Skeleton King slumps back onto the throne, no longer hostile.",
                    failureDescription = "The Skeleton King laughs, a hollow rattling sound. 'You dare threaten ME? Foolish mortal!' His hostility intensifies."
                )
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
        exits = mapOf(Direction.SOUTH to "throne_room"),
        entities = listOf(
            Entity.Feature(
                id = "stuck_door",
                name = "Heavy Stone Door",
                description = "A massive stone door partially ajar, but jammed in place",
                isInteractable = true,
                skillChallenge = SkillChallenge(
                    statType = StatType.STRENGTH,
                    difficulty = Difficulty.HARD,
                    description = "The door appears stuck. It would take considerable strength to force it open",
                    successDescription = "With a mighty heave, you force the stone door open wide enough to squeeze through. Beyond lies a hidden passage!",
                    failureDescription = "You strain against the heavy door, but it barely budges. You're not strong enough to force it open."
                )
            ),
            Entity.Feature(
                id = "rune_inscription",
                name = "Ancient Rune Inscription",
                description = "Glowing runes etched into the wall in an archaic script",
                isInteractable = true,
                skillChallenge = SkillChallenge(
                    statType = StatType.INTELLIGENCE,
                    difficulty = Difficulty.MEDIUM,
                    description = "The runes are in an ancient language. Deciphering them would require knowledge of arcane lore",
                    successDescription = "Your knowledge of ancient scripts pays off. The runes read: 'Here lies the gateway to the Ethereal Plane. Speak the word of power: Azathoth.'",
                    failureDescription = "The runes are incomprehensible to you. Their meaning remains a mystery."
                )
            )
        )
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
            maxHealth = 100,
            stats = Stats(
                strength = 14,      // Above-average warrior
                dexterity = 12,     // Moderately agile
                constitution = 14,  // Hardy adventurer
                intelligence = 13,  // Quick-thinking
                wisdom = 11,        // Some experience
                charisma = 10       // Average social skills
            )
        )
    )
}