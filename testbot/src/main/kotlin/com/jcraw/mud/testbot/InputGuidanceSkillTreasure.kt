@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

/**
 * Skill-progression and treasure-room input guidance (MUD-034f).
 */
internal object InputGuidanceSkillTreasure {

    private val skillProgressionPlan = """
                HOW DODGE SKILL WORKS:
                - Dodge is a defensive skill that activates when enemies attack you in combat
                - Each time you're attacked, you gain Dodge XP (amount varies by enemy difficulty)
                - Dual progression: 15% lucky chance for instant level-up OR gradual XP accumulation
                - Higher levels require more XP (quadratic scaling)
                - To level Dodge, you need to GET INTO COMBAT and let enemies attack you

                WHAT YOU NEED TO DO:
                - Find hostile NPCs/enemies in the dungeon
                - Engage them in combat (attack them)
                - Survive their counter-attacks (this trains Dodge)
                - Repeat until Dodge reaches the target level

                This is a grinding RPG - expect many combat sessions. If low on health, find a way to heal.

                NAVIGATION TIPS:
                - You have detailed history of all your previous room visits below
                - When you visited a room before, you saw which exits it has - use that history!
                - Only try exits that are shown for your CURRENT room (not exits from other rooms)
                - If you tried "go down" and got "You can't go that way", don't try it again from that same room
                - Safe zones (like towns) won't have hostile creatures
                - Combat areas will have enemies to fight

                THINK LIKE A PLAYER:
                - Where would enemies be located?
                - Which areas have I not explored yet?
                - Am I making progress toward the goal?
                - Should I check my skills to see XP progress?

                Play naturally and reason through the problem. Use the 'skills' command to track progress.
    """.trimIndent()

    private val treasurePlan = """
                Test plan:
                1. find_treasure_room - Navigate to find the treasure room
                2. examine_pedestals - Use 'examine pedestals' or 'examine altars' to see available items
                3. take_treasure - Use 'take treasure <item>' to claim an item
                4. return_treasure - Use 'return treasure <item>' to put it back
                5. swap_items - Take a different item to test swap mechanic
                6. finalize_choice - Leave the room to finalize your choice

                CRITICAL RULES:
                - You can swap items freely while in the room
                - Once you leave with an item, the choice is final
                - Test both take and return mechanics

                Target: ~40 actions
    """.trimIndent()

    fun skillProgression(
        scenario: TestScenario.SkillProgression,
        actionsTaken: List<String>,
        currentContext: String
    ): String {
        val targetLevel = scenario.targetLevel
        val dodgeLevel = parseDodgeLevel(currentContext)
        val status = if (dodgeLevel >= targetLevel) "COMPLETE!" else ""
        return """
                YOUR GOAL: Level your Dodge skill from 0 to $targetLevel

                CURRENT STATUS:
                - Dodge Level: $dodgeLevel / $targetLevel $status
                - Actions taken: ${actionsTaken.size}

                $skillProgressionPlan

                Target: Complete within ${scenario.maxSteps} actions
                """.trimIndent()
    }

    private fun parseDodgeLevel(currentContext: String): Int {
        return currentContext.lines()
            .find { it.contains("Dodge", ignoreCase = true) && it.contains("level", ignoreCase = true) }
            ?.let { line -> Regex("level\\s+(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() }
            ?: 0
    }

    fun treasureRoomPlaythrough(actionsTaken: List<String>): String {
        val objectives = treasureObjectives(actionsTaken)
        val completed = objectives.filter { it.value }.keys
        val remaining = objectives.filter { !it.value }.keys
        return """
                GOAL: Test treasure room system mechanics

                MANDATORY TEST OBJECTIVES:
                ✓ Completed (${completed.size}/6): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/6): ${remaining.joinToString(", ")}

                $treasurePlan
                """.trimIndent()
    }

    private fun treasureObjectives(actionsTaken: List<String>): Map<String, Boolean> = mapOf(
        "find_treasure_room" to actionsTaken.any { it == "look" },
        "examine_pedestals" to actionsTaken.any {
            it.contains("examine") && (it.contains("pedestal") || it.contains("altar"))
        },
        "take_treasure" to actionsTaken.any { it.contains("take treasure") },
        "return_treasure" to actionsTaken.any { it.contains("return treasure") },
        "swap_items" to (actionsTaken.count { it.contains("take treasure") } >= 2),
        "finalize_choice" to actionsTaken.any {
            it.matches(Regex("[nsew]|north|south|east|west"))
        }
    )

}
