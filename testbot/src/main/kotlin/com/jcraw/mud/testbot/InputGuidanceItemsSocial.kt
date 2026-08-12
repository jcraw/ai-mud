@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

/**
 * Item / social / quest input guidance packs (MUD-034f).
 * Plan text as properties; thin functions under FN_E 250.
 */
internal object InputGuidanceItemsSocial {

    private val itemPlan = """
                IMPORTANT: You are already in the Armory which has 4 items:
                - Rusty Iron Sword (weapon, +5 damage)
                - Sharp Steel Dagger (weapon, +3 damage)
                - Worn Leather Armor (armor, +2 defense)
                - Heavy Chainmail (armor, +4 defense)

                Test plan (do ONLY what's remaining):
                1. look_room - Use 'look' alone to see room contents
                2. examine_item_before_taking - 'look <item>' to inspect an item (do once)
                3. take_items - Pick up 2-3 different items with 'take <item>' or 'get <item>'
                4. check_inventory - Use 'inventory' or 'i' to verify
                5. equip_weapon - 'equip <weapon>' to equip a weapon
                6. equip_armor - 'equip <armor>' to equip armor
                7. drop_item - 'drop <item>' to drop something
                8. take_dropped_item_back - 'take <item>' to pick up the dropped item
                9. test_get_all - Try "get all", "take everything", or similar
                10. test_partial_names - Use "get sword" for "Rusty Iron Sword" or "get mail" for "Heavy Chainmail"

                CRITICAL RULES:
                - DO NOT repeat completed objectives
                - DO NOT move to other rooms
                - DO NOT retry failed commands
                - DO NOT examine the same item multiple times
                - Pick the NEXT uncompleted objective from the list

                Target: Complete all 10 objectives in ~15 actions
    """.trimIndent()

    private val socialPlan = """
                Test plan:
                1. look_for_npcs - Use 'look' to find NPCs
                2. move_to_npcs - Explore to find NPCs if needed
                3. talk_to_npc - Use 'talk <npc>' to start dialogue
                4. persuade_npc - Try 'persuade <npc>' for CHA check
                5. intimidate_npc - Try 'intimidate <npc>' for CHA check
                6. talk_to_2nd_npc - Talk to a different NPC

                CRITICAL: DO NOT talk to the same NPC repeatedly

                Target: ~15 actions
    """.trimIndent()

    private val questPlan = """
                Test plan:
                1. view_quests - Use 'quests', 'journal', or 'j' to see available quests
                2. accept_quest_1 - Use 'accept <quest_id>' to accept a quest
                3. work_on_objectives - Complete objectives (kill, collect, explore, talk, check)
                4. check_progress - Check 'quests' again to see progress/completion
                5. accept_quest_2 - Accept a second quest
                6. claim_reward - Use 'claim <quest_id>' when a quest is complete
                7. test_abandon - Use 'abandon <quest_id>' to test abandoning a quest

                CRITICAL: DO NOT view quests repeatedly without making progress

                Target: ~40 actions (includes quest work)
    """.trimIndent()

    fun itemInteraction(actionsTaken: List<String>): String {
        val objectives = itemObjectives(actionsTaken)
        val completed = objectives.filter { it.value }.keys
        val remaining = objectives.filter { !it.value }.keys
        return """
                MANDATORY TEST OBJECTIVES (must complete ALL):
                ✓ Completed (${completed.size}/10): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/10): ${remaining.joinToString(", ")}

                $itemPlan
                """.trimIndent()
    }

    private fun itemObjectives(actionsTaken: List<String>): Map<String, Boolean> = mapOf(
        "look_room" to (actionsTaken.any { it.contains("look") && !it.contains("look ") }),
        "examine_item_before_taking" to actionsTaken.any { it.matches(Regex(".*look .+")) },
        "take_items" to (actionsTaken.count { it.startsWith("take ") || it.startsWith("get ") } >= 2),
        "check_inventory" to (actionsTaken.any { it.contains("inventory") || it == "i" }),
        "equip_weapon" to actionsTaken.any { it.matches(Regex(".*(equip|wield).*(sword|dagger).*")) },
        "equip_armor" to actionsTaken.any { it.matches(Regex(".*(equip|wear).*(armor|chainmail).*")) },
        "drop_item" to actionsTaken.any { it.startsWith("drop ") },
        "take_dropped_item_back" to (actionsTaken.count { it.startsWith("take ") || it.startsWith("get ") } >= 3),
        "test_get_all" to (actionsTaken.any { it.contains("all") || it.contains("everything") }),
        "test_partial_names" to (actionsTaken.any {
            it.matches(Regex(".*(sword|dagger|armor|mail)")) && !it.contains("Rusty") && !it.contains("Sharp")
        })
    )

    fun socialInteraction(actionsTaken: List<String>): String {
        val objectives = socialObjectives(actionsTaken)
        val completed = objectives.filter { it.value }.keys
        val remaining = objectives.filter { !it.value }.keys
        return """
                MANDATORY TEST OBJECTIVES:
                ✓ Completed (${completed.size}/6): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/6): ${remaining.joinToString(", ")}

                $socialPlan
                """.trimIndent()
    }

    private fun socialObjectives(actionsTaken: List<String>): Map<String, Boolean> = mapOf(
        "look_for_npcs" to actionsTaken.any { it == "look" },
        "move_to_npcs" to actionsTaken.any { it.matches(Regex("[nsew]|north|south|east|west")) },
        "talk_to_npc" to actionsTaken.any { it.startsWith("talk ") },
        "persuade_npc" to (actionsTaken.any { it.contains("persuade") || it.contains("convince") }),
        "intimidate_npc" to (actionsTaken.any { it.contains("intimidate") || it.contains("threaten") }),
        "talk_to_2nd_npc" to (actionsTaken.count { it.startsWith("talk ") } >= 2)
    )

    fun questTesting(actionsTaken: List<String>): String {
        val objectives = questObjectives(actionsTaken)
        val completed = objectives.filter { it.value }.keys
        val remaining = objectives.filter { !it.value }.keys
        return """
                MANDATORY TEST OBJECTIVES:
                ✓ Completed (${completed.size}/7): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/7): ${remaining.joinToString(", ")}

                $questPlan
                """.trimIndent()
    }

    private fun questObjectives(actionsTaken: List<String>): Map<String, Boolean> = mapOf(
        "view_quests" to actionsTaken.any { it in listOf("quests", "journal", "j") },
        "accept_quest_1" to actionsTaken.any { it.startsWith("accept ") },
        "work_on_objectives" to (actionsTaken.any {
            it.contains("attack") || it.contains("take") || it.matches(Regex("[nsew]")) ||
                it.startsWith("talk ") || it.startsWith("check ")
        }),
        "check_progress" to (actionsTaken.count { it in listOf("quests", "journal", "j") } >= 2),
        "accept_quest_2" to (actionsTaken.count { it.startsWith("accept ") } >= 2),
        "claim_reward" to actionsTaken.any { it.startsWith("claim ") },
        "test_abandon" to actionsTaken.any { it.startsWith("abandon ") }
    )
}
