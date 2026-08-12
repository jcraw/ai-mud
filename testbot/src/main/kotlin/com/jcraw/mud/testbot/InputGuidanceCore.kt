@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

/**
 * Core scenario input guidance packs (MUD-034f).
 * Plan text lives in properties so FN token ceilings stay clear.
 */
internal object InputGuidanceCore {

    private val explorationPlan = """
                Test plan (do ONLY what's remaining):
                1. initial_look - Use 'look' to see starting room
                2. move_north - Go north using 'n' or 'north'
                3. move_south - Go south using 's' or 'south'
                4. move_east - Go east using 'e' or 'east'
                5. move_west - Go west using 'w' or 'west'
                6. examine_object - Use 'look <object>' to examine items/NPCs
                7. revisit_room - Return to a previous room to test description variability
                8. test_full_name - Use full direction names (e.g., 'north' instead of 'n')
                9. visit_5_rooms - Explore until you've visited 5 different rooms

                CRITICAL RULES:
                - DO NOT repeat 'look' in the same room multiple times
                - DO NOT examine the same object twice
                - Move to NEW rooms that haven't been visited yet
                - Only revisit rooms ONCE to test variability
    """.trimIndent()

    private val combatPlan = """
                Test plan (do ONLY what's remaining):
                1. look_around - Use 'look' to confirm NPC is present
                2. initiate_combat - 'attack Skeleton King' to start combat
                3. attack_in_combat - Keep using 'attack' until combat ends
                4. observe_victory - Continue until NPC dies or player dies

                CRITICAL RULES:
                - DO NOT repeat 'look' multiple times
                - DO NOT try to equip/unequip items
                - DO NOT move to other rooms
                - After initiating combat, just keep attacking until it ends

                Target: ~10-15 actions total
    """.trimIndent()

    private val skillChecksPlan = """
                Test plan:
                1. look_for_features - Use 'look' to find interactive features
                2. move_to_features - Explore rooms to find features
                3. attempt_str_check - Try 'check <feature>' on something
                4. attempt_dex_check - Try another skill check
                5. attempt_different_checks - Test 4+ different features/checks

                CRITICAL: DO NOT repeat the same 'check' command on the same feature

                Target: ~25 actions (explore + 4+ checks)
    """.trimIndent()

    private val exploratoryText = """
                Try anything:
                - Random combinations
                - Edge cases
                - Invalid inputs
                - Ambiguous commands
            """.trimIndent()

    private val fullPlaythroughText = """
                Play naturally to complete the dungeon:
                - Start by looking around and exploring (look, n/s/e/w)
                - Find and collect items, equip weapons/armor
                - Fight NPCs when encountered
                - Talk to friendly NPCs for information
                - Work toward reaching the end of the dungeon
            """.trimIndent()

    fun exploration(actionsTaken: List<String>, roomsVisited: Set<String>): String {
        val roomsVisitedText = if (roomsVisited.isNotEmpty()) {
            "Rooms visited so far (${roomsVisited.size}): ${roomsVisited.joinToString(", ")}"
        } else {
            "No rooms visited yet."
        }
        val objectives = explorationObjectives(actionsTaken, roomsVisited)
        val (completed, remaining) = splitObjectives(objectives)
        return """
                Test ALL exploration mechanics efficiently:

                MANDATORY TEST OBJECTIVES:
                ✓ Completed (${completed.size}/9): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/9): ${remaining.joinToString(", ")}

                $explorationPlan

                $roomsVisitedText
                Target: ~15 actions to visit 5 rooms and test all mechanics
                """.trimIndent()
    }

    private fun explorationObjectives(
        actionsTaken: List<String>,
        roomsVisited: Set<String>
    ): Map<String, Boolean> = mapOf(
        "initial_look" to actionsTaken.any { it == "look" },
        "move_north" to actionsTaken.any { it in listOf("n", "north", "go north") },
        "move_south" to actionsTaken.any { it in listOf("s", "south", "go south") },
        "move_east" to actionsTaken.any { it in listOf("e", "east", "go east") },
        "move_west" to actionsTaken.any { it in listOf("w", "west", "go west") },
        "examine_object" to actionsTaken.any { it.matches(Regex("look .+")) },
        "revisit_room" to (roomsVisited.size >= 3 &&
            actionsTaken.count { it.matches(Regex("[nsew]|north|south|east|west")) } > roomsVisited.size),
        "test_full_name" to actionsTaken.any { it in listOf("north", "south", "east", "west") },
        "visit_5_rooms" to (roomsVisited.size >= 5)
    )

    fun combat(actionsTaken: List<String>): String {
        val objectives = mapOf(
            "look_around" to actionsTaken.any { it == "look" },
            "initiate_combat" to actionsTaken.any { it.contains("attack") && it.contains("skeleton") },
            "attack_in_combat" to (actionsTaken.count { it == "attack" || it.contains("attack") } >= 3),
            "observe_victory" to (actionsTaken.size >= 10)
        )
        val (completed, remaining) = splitObjectives(objectives)
        return """
                FOCUS: Test combat mechanics ONLY. You start in throne room with Skeleton King (60 HP, hostile).

                MANDATORY TEST OBJECTIVES:
                ✓ Completed (${completed.size}/4): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/4): ${remaining.joinToString(", ")}

                $combatPlan
                """.trimIndent()
    }

    fun skillChecks(actionsTaken: List<String>): String {
        val objectives = mapOf(
            "look_for_features" to actionsTaken.any { it == "look" },
            "move_to_features" to actionsTaken.any { it.matches(Regex("[nsew]|north|south|east|west")) },
            "attempt_str_check" to actionsTaken.any { it.startsWith("check ") },
            "attempt_dex_check" to (actionsTaken.count { it.startsWith("check ") } >= 2),
            "attempt_different_checks" to (actionsTaken.count { it.startsWith("check ") } >= 4)
        )
        val (completed, remaining) = splitObjectives(objectives)
        return """
                MANDATORY TEST OBJECTIVES:
                ✓ Completed (${completed.size}/5): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/5): ${remaining.joinToString(", ")}

                $skillChecksPlan
                """.trimIndent()
    }

    fun exploratory(): String = exploratoryText

    fun fullPlaythrough(): String = fullPlaythroughText

    private fun splitObjectives(objectives: Map<String, Boolean>): Pair<Set<String>, Set<String>> {
        val completed = objectives.filter { it.value }.keys
        val remaining = objectives.filter { !it.value }.keys
        return completed to remaining
    }
}
