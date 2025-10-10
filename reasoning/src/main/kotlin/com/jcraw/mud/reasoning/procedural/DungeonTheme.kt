package com.jcraw.mud.reasoning.procedural

/**
 * Dungeon theme that influences procedural generation aesthetics
 */
enum class DungeonTheme(
    val displayName: String,
    val roomTraitPool: List<String>,
    val itemNamePrefixes: List<String>,
    val npcNamePrefixes: List<String>
) {
    CRYPT(
        displayName = "Ancient Crypt",
        roomTraitPool = listOf(
            "crumbling stone walls",
            "dusty sarcophagi",
            "cobwebs in corners",
            "musty air",
            "flickering torchlight",
            "ancient bones scattered",
            "faded frescoes",
            "cold stone floor",
            "echoing darkness",
            "smell of decay",
            "hollow emptiness",
            "shadowy alcoves",
            "carved burial niches",
            "rusted iron sconces",
            "damp stone ceiling"
        ),
        itemNamePrefixes = listOf("Ancient", "Dusty", "Bone", "Cursed", "Forgotten"),
        npcNamePrefixes = listOf("Skeleton", "Zombie", "Ghost", "Wraith", "Ghoul")
    ),

    CASTLE(
        displayName = "Ruined Castle",
        roomTraitPool = listOf(
            "cracked marble floors",
            "torn tapestries hanging",
            "arrow slits in walls",
            "ornate stone columns",
            "faded royal banners",
            "broken furniture",
            "vaulted ceiling",
            "stained glass fragments",
            "iron chandeliers",
            "suit of armor standing",
            "weathered wooden beams",
            "carved stone reliefs",
            "dusty library shelves",
            "grand fireplace",
            "military standards"
        ),
        itemNamePrefixes = listOf("Royal", "Knight's", "Noble", "Ceremonial", "Guard's"),
        npcNamePrefixes = listOf("Knight", "Guard", "Lord", "Squire", "Sentinel")
    ),

    CAVE(
        displayName = "Dark Caverns",
        roomTraitPool = listOf(
            "rough stone walls",
            "dripping stalactites",
            "damp cave floor",
            "echoing water drops",
            "phosphorescent moss",
            "jagged rock formations",
            "underground stream",
            "mineral deposits glinting",
            "bat nests overhead",
            "narrow passages",
            "slippery rocks",
            "darkness pressing in",
            "cool damp air",
            "subterranean chill",
            "natural stone pillars"
        ),
        itemNamePrefixes = listOf("Stone", "Crystal", "Cave", "Primal", "Earth"),
        npcNamePrefixes = listOf("Goblin", "Troll", "Bat", "Spider", "Worm")
    ),

    TEMPLE(
        displayName = "Forgotten Temple",
        roomTraitPool = listOf(
            "sacred altar standing",
            "religious symbols carved",
            "incense smell lingering",
            "prayer cushions rotting",
            "holy water font dry",
            "divine frescoes faded",
            "meditation chamber",
            "ornate pillars",
            "ceremonial braziers",
            "scripture carved in walls",
            "vaulted sanctuary",
            "sacred geometry patterns",
            "ritual circles etched",
            "mystical energy humming",
            "divine statues weathered"
        ),
        itemNamePrefixes = listOf("Holy", "Blessed", "Sacred", "Divine", "Ritual"),
        npcNamePrefixes = listOf("Priest", "Monk", "Cultist", "Guardian", "Acolyte")
    )
}
