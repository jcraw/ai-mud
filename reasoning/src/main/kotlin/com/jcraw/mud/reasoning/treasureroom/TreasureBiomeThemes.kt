@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter"
)

package com.jcraw.mud.reasoning.treasureroom

/**
 * Biome theme map for [TreasureRoomDescriptionGenerator] companion (MUD-034n).
 * Companion delegates so FQCN `TreasureRoomDescriptionGenerator.getBiomeTheme` / `DEFAULT_BIOME_THEMES` stay.
 */
internal object TreasureBiomeThemes {

    val DEFAULT: Map<String, TreasureRoomDescriptionGenerator.BiomeTheme> = mapOf(
        "ancient_abyss" to TreasureRoomDescriptionGenerator.BiomeTheme(
            material = "weathered stone",
            aesthetic = "ancient, crumbling, moss-covered",
            barrierType = "shimmering arcane barrier",
            atmosphereHints = listOf("ancient", "crumbling", "moss-covered", "weathered")
        ),
        "magma_cave" to TreasureRoomDescriptionGenerator.BiomeTheme(
            material = "obsidian",
            aesthetic = "glowing, volcanic, heat-warped",
            barrierType = "wall of molten energy",
            atmosphereHints = listOf("glowing", "volcanic", "heat-warped", "smoldering")
        ),
        "frozen_depths" to TreasureRoomDescriptionGenerator.BiomeTheme(
            material = "ice crystal",
            aesthetic = "frosted, glacial, pristine",
            barrierType = "frozen barrier of solid ice",
            atmosphereHints = listOf("frosted", "glacial", "pristine", "crystalline")
        ),
        "bone_crypt" to TreasureRoomDescriptionGenerator.BiomeTheme(
            material = "bone",
            aesthetic = "skeletal, macabre, dusty",
            barrierType = "cage of blackened bone",
            atmosphereHints = listOf("skeletal", "macabre", "dusty", "grim")
        ),
        "elven_ruins" to TreasureRoomDescriptionGenerator.BiomeTheme(
            material = "silver-veined marble",
            aesthetic = "elegant, ancient, luminous",
            barrierType = "translucent barrier of woven moonlight",
            atmosphereHints = listOf("elegant", "ancient", "luminous", "graceful")
        ),
        "dwarven_halls" to TreasureRoomDescriptionGenerator.BiomeTheme(
            material = "granite",
            aesthetic = "sturdy, geometric, metallic",
            barrierType = "mechanical barrier of interlocking gears",
            atmosphereHints = listOf("sturdy", "geometric", "metallic", "fortified")
        )
    )

    fun get(biomeName: String): TreasureRoomDescriptionGenerator.BiomeTheme {
        return DEFAULT[biomeName] ?: DEFAULT["ancient_abyss"]!!
    }
}
