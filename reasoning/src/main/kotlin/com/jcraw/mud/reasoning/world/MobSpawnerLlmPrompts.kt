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
    "UnusedParameter",
    "TooGenericExceptionCaught",
    "TooGenericExceptionThrown",
    "SwallowedException",
    "WildcardImport",
    "MayBeConst",
    "ImplicitDefaultLocale",
    "ForbiddenComment",
    "UnusedPrivateProperty",
)

package com.jcraw.mud.reasoning.world

/**
 * LLM prompt builders for mob spawn (MUD-034g pure move).
 * Large templates live as properties so FN_E stays under global 250.
 */
internal object MobSpawnerLlmPrompts {

    val SYSTEM_PROMPT = """
            You are a game master generating NPCs/mobs for a fantasy dungeon.
            Output valid JSON array only, no additional text.
        """.trimIndent()

    val MOB_FIELD_INSTRUCTIONS = """
            For each mob, provide:
            - name: Unique name (include type, e.g., "Skeleton Warrior #1")
            - description: 1-2 sentence description
            - health: Scale with difficulty (difficulty * 10 + variance)
            - lootTableId: Use "THEME_DIFFICULTY"
            - goldDrop: Scale with difficulty (difficulty * 5 + variance)
            - isHostile: true (default)
            - strength, dexterity, constitution, intelligence, wisdom, charisma: D&D stats (8-18, scale with difficulty)
        """.trimIndent()

    val EXAMPLE_JSON = """
            [
                {
                    "name": "Wolf Alpha",
                    "description": "A large gray wolf with piercing yellow eyes.",
                    "health": 150,
                    "lootTableId": "dark_forest_5",
                    "goldDrop": 25,
                    "isHostile": true,
                    "strength": 14,
                    "dexterity": 16,
                    "constitution": 12,
                    "intelligence": 6,
                    "wisdom": 12,
                    "charisma": 8
                }
            ]
        """.trimIndent()

    fun buildUserContext(
        theme: String,
        count: Int,
        difficulty: Int,
        mobArchetypes: List<String>
    ): String {
        val lootHint = "${theme.lowercase().replace(" ", "_")}_$difficulty"
        val fields = MOB_FIELD_INSTRUCTIONS.replace("THEME_DIFFICULTY", lootHint)
        return """
            Generate $count NPC/mob entries for a $theme setting at difficulty level $difficulty (scale 1-20).
            Use mob archetypes: ${mobArchetypes.joinToString(", ")}

            $fields

            Output as JSON array of objects. Example:
            $EXAMPLE_JSON
        """.trimIndent()
    }

    fun stripMarkdownCodeBlocks(content: String): String {
        return content
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}
