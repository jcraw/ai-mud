package com.jcraw.mud.reasoning.world

/**
 * Defines content rules for biome themes.
 * Maps theme names to trap types, resource types, mob archetypes, and ambiance.
 */
data class ThemeProfile(
    val traps: List<String>,
    val resources: List<String>,
    val mobArchetypes: List<String>,
    val ambiance: String
)

/**
 * Registry of predefined theme profiles for content generation.
 * Provides exact and semantic matching for theme lookups.
 */
object ThemeRegistry {
    private val profiles = mapOf(
        "dark forest" to ThemeProfile(
            traps = listOf("bear trap", "pit trap", "poisoned spike"),
            resources = listOf("wood", "herbs", "mushroom", "berries"),
            mobArchetypes = listOf("wolf", "bandit", "goblin", "giant spider"),
            ambiance = "damp, shadowy, overgrown"
        ),
        "magma cave" to ThemeProfile(
            traps = listOf("lava pool", "collapsing floor", "steam vent", "heat wave"),
            resources = listOf("obsidian", "sulfur", "magma crystal", "fire ore"),
            mobArchetypes = listOf("fire elemental", "magma worm", "salamander", "lava drake"),
            ambiance = "scorching, smoky, unstable"
        ),
        "ancient crypt" to ThemeProfile(
            traps = listOf("poison dart", "cursed rune", "collapsing ceiling", "spirit ward"),
            resources = listOf("bone", "arcane dust", "ancient coin", "grave flower"),
            mobArchetypes = listOf("skeleton", "wraith", "zombie", "lich"),
            ambiance = "cold, silent, oppressive"
        ),
        "frozen wasteland" to ThemeProfile(
            traps = listOf("thin ice", "avalanche trigger", "frost rune", "blizzard zone"),
            resources = listOf("ice shard", "frost herb", "frozen timber", "glacier stone"),
            mobArchetypes = listOf("ice elemental", "frost giant", "yeti", "winter wolf"),
            ambiance = "freezing, desolate, windswept"
        ),
        "abandoned castle" to ThemeProfile(
            traps = listOf("portcullis", "spike pit", "arrow trap", "swinging blade"),
            resources = listOf("iron scrap", "old tapestry", "rusted armor", "ancient tome"),
            mobArchetypes = listOf("knight ghost", "gargoyle", "possessed armor", "court phantom"),
            ambiance = "echoing, crumbling, haunted"
        ),
        "swamp" to ThemeProfile(
            traps = listOf("quicksand", "poisoned water", "disease cloud", "bog trap"),
            resources = listOf("swamp weed", "leech", "murky water", "peat"),
            mobArchetypes = listOf("swamp troll", "giant frog", "will-o-wisp", "bog creature"),
            ambiance = "murky, fetid, humid"
        ),
        "desert ruins" to ThemeProfile(
            traps = listOf("sand trap", "scorpion nest", "sun glare trap", "collapsing wall"),
            resources = listOf("sand glass", "desert herb", "ancient pottery", "dried wood"),
            mobArchetypes = listOf("sand worm", "mummy", "scorpion", "desert bandit"),
            ambiance = "hot, arid, windblown"
        ),
        "underground lake" to ThemeProfile(
            traps = listOf("slippery ledge", "deep water", "drowning trap", "water pressure"),
            resources = listOf("cave fish", "luminous algae", "water crystal", "wet stone"),
            mobArchetypes = listOf("aquatic horror", "cave fisher", "water elemental", "giant eel"),
            ambiance = "damp, echoing, bioluminescent"
        )
    )

    /**
     * Gets theme profile by exact match (case-insensitive).
     * Returns null if no match found.
     */
    fun getProfile(theme: String): ThemeProfile? {
        val normalized = theme.lowercase().trim()
        return profiles.entries.find { (key, _) ->
            key.lowercase() == normalized
        }?.value
    }

    /**
     * Gets theme profile by semantic match.
     * Uses keyword matching as a simple fallback (LLM integration is optional enhancement).
     * Returns most relevant profile or null.
     */
    fun getProfileSemantic(theme: String): ThemeProfile? {
        val normalized = theme.lowercase().trim()

        // Try exact match first
        getProfile(theme)?.let { return it }

        // Keyword matching for common variations
        return when {
            "forest" in normalized || "wood" in normalized || "tree" in normalized ->
                profiles["dark forest"]
            "lava" in normalized || "magma" in normalized || "volcano" in normalized || "fire" in normalized ->
                profiles["magma cave"]
            "crypt" in normalized || "tomb" in normalized || "undead" in normalized || "grave" in normalized ->
                profiles["ancient crypt"]
            "ice" in normalized || "snow" in normalized || "frost" in normalized || "frozen" in normalized ->
                profiles["frozen wasteland"]
            "castle" in normalized || "fortress" in normalized || "keep" in normalized ->
                profiles["abandoned castle"]
            "swamp" in normalized || "bog" in normalized || "marsh" in normalized ->
                profiles["swamp"]
            "desert" in normalized || "sand" in normalized || "dune" in normalized ->
                profiles["desert ruins"]
            "lake" in normalized || "water" in normalized || "underground" in normalized && "river" !in normalized ->
                profiles["underground lake"]
            else -> null
        }
    }

    /**
     * Returns all registered theme names.
     */
    fun getAllThemeNames(): List<String> = profiles.keys.toList()

    /**
     * Returns default profile for unknown themes.
     */
    fun getDefaultProfile(): ThemeProfile = profiles["dark forest"]!!
}
