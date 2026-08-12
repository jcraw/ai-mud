@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

/**
 * Bad + brute-force playthrough guidance (MUD-034f).
 */
internal object InputGuidanceBadBrute {

    private val badPlan = """
                BAD PLAYTHROUGH STRATEGY (play poorly on purpose):
                1. initial_look - Quick look at entrance
                2. go_north_to_corridor - Go north to Dark Corridor
                3. skip_armory - DO NOT go west to armory (no weapons/armor!)
                4. skip_treasury - DO NOT go east to treasury (no health potions!)
                5. rush_to_throne - Go north again to throne room
                6. attack_skeleton_king_unarmed - Attack Skeleton King WITHOUT gear
                7. continue_combat_until_death - Keep attacking until you die

                CRITICAL RULES:
                - DO NOT collect any items
                - DO NOT equip weapons or armor
                - DO NOT use health potions (you don't have any!)
                - Rush directly to throne room (entrance → corridor → throne)
                - Fight Skeleton King with base stats only (STR 10, no bonuses)
                - Expected: Player dies in ~3-5 combat rounds

                This validates the game is NOT too easy - you should die without preparation.

                Target: ~10-15 actions (quick death)
    """.trimIndent()

    private val brutePlan = """
                BRUTE FORCE STRATEGY (collect everything, fight with advantage):
                1. initial_look - Look at entrance
                2. go_to_armory - Go north to corridor, then west to armory
                3. take_best_weapon - "take iron sword" (Rusty Iron Sword, +5 damage)
                4. take_best_armor - "take chainmail" (Heavy Chainmail, +4 defense)
                5. equip_weapon - "equip iron sword"
                6. equip_armor - "equip chainmail"
                7. go_to_treasury - Go east back to corridor, then east AGAIN to treasury (MANDATORY!)
                8. take_health_potion - "take health potion" for backup (MUST be in treasury room!)
                9. go_to_throne_room - Go west to corridor, then north to throne room
                10. attack_skeleton_king - "attack Skeleton King" with full gear
                11. defeat_boss - Continue attacking until victory (weapon bonus makes it winnable)

                CRITICAL RULES:
                - ALWAYS collect and equip the BEST gear (Iron Sword +5, Chainmail +4)
                - MUST visit treasury room - health potion is ONLY in treasury, NOT in corridor!
                - Do NOT try to take health potion unless you see "Ancient Treasury" in room description
                - Navigation: armory → east → corridor → east → treasury
                - Fight methodically - equipment bonuses should ensure victory
                - Expected: Win in ~5-8 combat rounds with minimal risk

                This validates the game is BEATABLE with proper preparation.

                EXACT Path: entrance → north → corridor → west → armory → east → corridor → east → treasury → west → corridor → north → throne room

                Target: ~25-35 actions (thorough preparation + combat)
    """.trimIndent()

    fun badPlaythrough(actionsTaken: List<String>): String {
        val objectives = badObjectives(actionsTaken)
        val completed = objectives.filter { it.value }.keys
        val remaining = objectives.filter { !it.value }.keys
        return """
                GOAL: Demonstrate poor gameplay and validate difficulty
                EXPECTED OUTCOME: Player should DIE to Skeleton King (50 HP, hostile, strong)

                MANDATORY BAD DECISIONS:
                ✓ Completed (${completed.size}/7): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/7): ${remaining.joinToString(", ")}

                $badPlan
                """.trimIndent()
    }

    private fun badObjectives(actionsTaken: List<String>): Map<String, Boolean> = mapOf(
        "initial_look" to actionsTaken.any { it == "look" },
        "go_north_to_corridor" to actionsTaken.any { it in listOf("n", "north", "go north") },
        "skip_armory" to !actionsTaken.any { it in listOf("w", "west", "go west") },
        "skip_treasury" to !actionsTaken.any { it in listOf("e", "east", "go east") },
        "rush_to_throne" to actionsTaken.any { it.matches(Regex(".*north.*")) && actionsTaken.size >= 2 },
        "attack_skeleton_king_unarmed" to actionsTaken.any {
            it.contains("attack") && it.contains("skeleton")
        },
        "continue_combat_until_death" to (actionsTaken.count { it == "attack" } >= 3)
    )

    fun bruteForcePlaythrough(actionsTaken: List<String>, recentHistory: List<TestStep>): String {
        val objectives = bruteForceObjectives(actionsTaken, recentHistory)
        val completed = objectives.filter { it.value }.keys
        val remaining = objectives.filter { !it.value }.keys
        return """
                GOAL: Complete dungeon through superior equipment and preparation
                EXPECTED OUTCOME: Player DEFEATS Skeleton King with proper gear

                MANDATORY PREPARATION:
                ✓ Completed (${completed.size}/11): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/11): ${remaining.joinToString(", ")}

                $brutePlan
                """.trimIndent()
    }

    private fun bruteForceObjectives(
        actionsTaken: List<String>,
        recentHistory: List<TestStep>
    ): Map<String, Boolean> = mapOf(
        "initial_look" to actionsTaken.any { it == "look" },
        "go_to_armory" to historyHas(recentHistory, "Forgotten Armory", "Armory"),
        "take_best_weapon" to successfulTake(recentHistory, "iron"),
        "take_best_armor" to successfulTake(recentHistory, "chainmail"),
        "equip_weapon" to successfulEquip(recentHistory, "sword"),
        "equip_armor" to successfulEquip(recentHistory, "chainmail"),
        "go_to_treasury" to historyHas(recentHistory, "Ancient Treasury", "Treasury"),
        "take_health_potion" to successfulTake(recentHistory, "potion"),
        "go_to_throne_room" to historyHas(recentHistory, "Throne Room"),
        "attack_skeleton_king" to recentHistory.any {
            it.playerInput.contains("attack") && it.playerInput.contains("skeleton") &&
                it.gmResponse.contains("combat", ignoreCase = true)
        },
        "defeat_boss" to recentHistory.any {
            it.gmResponse.contains("has been defeated", ignoreCase = true)
        }
    )

    private fun historyHas(history: List<TestStep>, vararg needles: String): Boolean =
        history.any { step -> needles.any { step.gmResponse.contains(it, ignoreCase = true) } }

    private fun successfulTake(history: List<TestStep>, needle: String): Boolean =
        history.any {
            it.playerInput.contains("take") && it.playerInput.contains(needle) &&
                it.gmResponse.contains("You take", ignoreCase = true)
        }

    private fun successfulEquip(history: List<TestStep>, needle: String): Boolean =
        history.any {
            it.playerInput.contains("equip") && it.playerInput.contains(needle) &&
                it.gmResponse.contains("You equip", ignoreCase = true)
        }
}
