@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

/**
 * Smart playthrough input guidance (MUD-034f).
 */
internal object InputGuidanceSmartSkill {

    private val smartPlan = """
                SMART PLAYTHROUGH STRATEGY (use brains, not brawn):
                1. initial_look - Look at entrance, identify Old Guard NPC
                2. talk_to_guard - "talk old guard" for dialogue
                3. persuade_guard - "persuade old guard" (Easy CHA check, gives intel about secret chamber)
                   - NOTE: This can FAIL due to dice roll - that's OK! Mechanics still work.
                4. go_to_corridor - Go north from entrance to Dark Corridor
                5. go_to_throne_room - Go north from corridor to throne room where Skeleton King is
                6. attempt_intimidate_king - "intimidate Skeleton King" (Hard CHA check)
                   - CRITICAL: MUST be in throne room FIRST (step 5) before intimidating!
                   - SUCCESS: King backs down, becomes non-hostile, no combat needed!
                   - FAILURE: Fall back to minimal combat (player has STR 10, weaker than king!)
                7. avoid_or_minimize_combat - Avoid fighting if possible, use intimidation first
                8. explore_secret_chamber - Go north from throne room to secret chamber
                9. pass_strength_check - "check stuck door" (Hard STR check, player has STR 10)
                10. pass_intelligence_check - "check rune inscription" (Medium INT check, player has INT 9)

                CRITICAL RULES:
                - ALWAYS try social/skill approaches BEFORE combat
                - MUST navigate to throne room BEFORE intimidating Skeleton King!
                - Intimidate Skeleton King to avoid difficult fight (Hard CHA 9, DC ~15-20)
                - If intimidation succeeds, King becomes passive - NO COMBAT!
                - Dice rolls can fail - don't give up if persuasion/intimidation fails!
                - Explore secret chamber and pass skill checks to show versatility
                - Expected: Complete with 0-2 combat rounds (social victory preferred)

                This validates MULTIPLE SOLUTION PATHS exist - not just combat.

                Path: entrance → corridor → throne room → (intimidate king) → secret chamber

                Target: ~20-30 actions (social interactions + skill checks)
    """.trimIndent()

    fun smartPlaythrough(actionsTaken: List<String>, recentHistory: List<TestStep>): String {
        val objectives = smartObjectives(actionsTaken, recentHistory)
        val completed = objectives.filter { it.value }.keys
        val remaining = objectives.filter { !it.value }.keys
        return """
                GOAL: Complete dungeon through social skills and intelligence
                EXPECTED OUTCOME: Bypass combat via intimidation/persuasion, explore safely

                MANDATORY SMART TACTICS:
                ✓ Completed (${completed.size}/10): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/10): ${remaining.joinToString(", ")}

                $smartPlan
                """.trimIndent()
    }

    private fun smartObjectives(
        actionsTaken: List<String>,
        recentHistory: List<TestStep>
    ): Map<String, Boolean> {
        val inThroneRoom = recentHistory.any {
            it.gmResponse.contains("Throne Room", ignoreCase = true)
        }
        return smartObjectivesPart1(actionsTaken, recentHistory, inThroneRoom) +
            smartObjectivesPart2(actionsTaken)
    }

    private fun smartObjectivesPart1(
        actionsTaken: List<String>,
        recentHistory: List<TestStep>,
        inThroneRoom: Boolean
    ): Map<String, Boolean> = mapOf(
        "initial_look" to actionsTaken.any { it == "look" },
        "talk_to_guard" to actionsTaken.any { it.contains("talk") && it.contains("guard") },
        "persuade_guard" to actionsTaken.any { it.contains("persuade") && it.contains("guard") },
        "go_to_corridor" to recentHistory.any {
            it.gmResponse.contains("Dark Corridor", ignoreCase = true)
        },
        "go_to_throne_room" to inThroneRoom
    )

    private fun smartObjectivesPart2(actionsTaken: List<String>): Map<String, Boolean> = mapOf(
        "attempt_intimidate_king" to actionsTaken.any {
            it.contains("intimidate") && it.contains("skeleton")
        },
        "avoid_or_minimize_combat" to (actionsTaken.count { it == "attack" } <= 2),
        "explore_secret_chamber" to actionsTaken.any {
            it.matches(Regex(".*(secret|chamber).*")) || actionsTaken.count { a -> a == "n" } >= 3
        },
        "pass_strength_check" to actionsTaken.any { it.contains("check") && it.contains("door") },
        "pass_intelligence_check" to actionsTaken.any {
            it.contains("check") && it.contains("rune")
        }
    )

}
