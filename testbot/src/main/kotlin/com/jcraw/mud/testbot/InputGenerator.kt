package com.jcraw.mud.testbot

import com.jcraw.sophia.llm.LLMClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Generates natural language player inputs using LLM.
 * Uses gpt-4o-mini for cost savings as per guidelines.
 */
class InputGenerator(
    private val llmClient: LLMClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    /**
     * Generate a player input for the given scenario and context.
     */
    suspend fun generateInput(
        scenario: TestScenario,
        recentHistory: List<TestStep>,
        currentContext: String
    ): GeneratedInput {
        val systemPrompt = buildSystemPrompt(scenario)
        val userContext = buildUserContext(scenario, recentHistory, currentContext)

        val response = llmClient.chatCompletion(
            modelId = "gpt-4o-mini",
            systemPrompt = systemPrompt,
            userContext = userContext,
            maxTokens = 200,
            temperature = 0.8
        )

        val responseText = response.choices.firstOrNull()?.message?.content ?: ""
        return parseResponse(responseText)
    }

    private fun buildSystemPrompt(scenario: TestScenario): String {
        return """
            You are an AI test bot playing a text-based MUD (Multi-User Dungeon) game.
            Your goal is to test the game engine by generating realistic player inputs.

            Scenario: ${scenario.name}
            Description: ${scenario.description}

            IMPORTANT: You are ALREADY in the game. Look at the "Current game state" to see where you are.
            Do NOT try to "enter" or "start" the game - you're already playing!

            CRITICAL RULES TO AVOID REDUNDANCY:
            1. Read "Actions taken so far" list carefully - do NOT repeat actions
            2. Follow the "Remaining" objectives list - test ONLY what hasn't been completed
            3. Each action should test something NEW - no duplicate tests
            4. The test objectives are MANDATORY, not optional suggestions
            5. Move systematically through the test plan - don't jump around randomly

            Generate a single player command that:
            1. Tests the NEXT uncompleted objective from the scenario guidance
            2. Uses valid game commands (look, go/move/n/s/e/w, take, attack, talk, equip, use, check, etc.)
            3. Has NOT been done before (check "Actions taken so far" list)
            4. Makes progress toward completing ALL mandatory test cases

            Respond with JSON in this format:
            {
                "reasoning": "your thought process before choosing this action (2-3 sentences explaining why this action makes sense given the current situation and goal)",
                "input": "the player command",
                "intent": "what you're trying to test",
                "expected": "what you expect to happen"
            }
        """.trimIndent()
    }

    private fun buildUserContext(
        scenario: TestScenario,
        recentHistory: List<TestStep>,
        currentContext: String
    ): String {
        // Extract all actions taken so far
        val actionsTaken = recentHistory.map { it.playerInput.lowercase() }

        val historyText = if (recentHistory.isEmpty()) {
            "No previous actions yet."
        } else {
            // Show ALL actions taken (condensed) + last 3 detailed
            val allActions = "Actions taken so far (${recentHistory.size}): ${actionsTaken.joinToString(", ")}"
            val recentDetailed = recentHistory.takeLast(3).joinToString("\n") { step ->
                "Player: ${step.playerInput}\nGM: ${step.gmResponse.take(200)}"
            }
            "$allActions\n\nRecent details:\n$recentDetailed"
        }

        // Track unique rooms visited for exploration scenario
        val roomsVisited = if (scenario is TestScenario.Exploration) {
            extractRoomsFromHistory(recentHistory)
        } else {
            emptySet()
        }

        val scenarioGuidance = when (scenario) {
            is TestScenario.Exploration -> {
                val roomsVisitedText = if (roomsVisited.isNotEmpty()) {
                    "Rooms visited so far (${roomsVisited.size}): ${roomsVisited.joinToString(", ")}"
                } else {
                    "No rooms visited yet."
                }

                val objectives = mapOf(
                    "initial_look" to actionsTaken.any { it == "look" },
                    "move_north" to actionsTaken.any { it in listOf("n", "north", "go north") },
                    "move_south" to actionsTaken.any { it in listOf("s", "south", "go south") },
                    "move_east" to actionsTaken.any { it in listOf("e", "east", "go east") },
                    "move_west" to actionsTaken.any { it in listOf("w", "west", "go west") },
                    "examine_object" to actionsTaken.any { it.matches(Regex("look .+")) },
                    "revisit_room" to (roomsVisited.size >= 3 && actionsTaken.count { it.matches(Regex("[nsew]|north|south|east|west")) } > roomsVisited.size),
                    "test_full_name" to actionsTaken.any { it in listOf("north", "south", "east", "west") },
                    "visit_5_rooms" to (roomsVisited.size >= 5)
                )

                val completed = objectives.filter { it.value }.keys
                val remaining = objectives.filter { !it.value }.keys

                """
                Test ALL exploration mechanics efficiently:

                MANDATORY TEST OBJECTIVES:
                ✓ Completed (${completed.size}/9): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/9): ${remaining.joinToString(", ")}

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

                $roomsVisitedText
                Target: ~15 actions to visit 5 rooms and test all mechanics
                """.trimIndent()
            }
            is TestScenario.Combat -> {
                val objectives = mapOf(
                    "look_around" to actionsTaken.any { it == "look" },
                    "initiate_combat" to actionsTaken.any { it.contains("attack") && it.contains("skeleton") },
                    "attack_in_combat" to (actionsTaken.count { it == "attack" || it.contains("attack") } >= 3),
                    "observe_victory" to (actionsTaken.size >= 10) // Approximate - should have multiple rounds
                )

                val completed = objectives.filter { it.value }.keys
                val remaining = objectives.filter { !it.value }.keys

                """
                FOCUS: Test combat mechanics ONLY. You start in throne room with Skeleton King (60 HP, hostile).

                MANDATORY TEST OBJECTIVES:
                ✓ Completed (${completed.size}/4): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/4): ${remaining.joinToString(", ")}

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
            }
            is TestScenario.SkillChecks -> {
                val objectives = mapOf(
                    "look_for_features" to actionsTaken.any { it == "look" },
                    "move_to_features" to actionsTaken.any { it.matches(Regex("[nsew]|north|south|east|west")) },
                    "attempt_str_check" to actionsTaken.any { it.startsWith("check ") },
                    "attempt_dex_check" to (actionsTaken.count { it.startsWith("check ") } >= 2),
                    "attempt_different_checks" to (actionsTaken.count { it.startsWith("check ") } >= 4)
                )

                val completed = objectives.filter { it.value }.keys
                val remaining = objectives.filter { !it.value }.keys

                """
                MANDATORY TEST OBJECTIVES:
                ✓ Completed (${completed.size}/5): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/5): ${remaining.joinToString(", ")}

                Test plan:
                1. look_for_features - Use 'look' to find interactive features
                2. move_to_features - Explore rooms to find features
                3. attempt_str_check - Try 'check <feature>' on something
                4. attempt_dex_check - Try another skill check
                5. attempt_different_checks - Test 4+ different features/checks

                CRITICAL: DO NOT repeat the same 'check' command on the same feature

                Target: ~25 actions (explore + 4+ checks)
                """.trimIndent()
            }
            is TestScenario.ItemInteraction -> {
                // Track what's been tested so far
                val objectives = mapOf(
                    "look_room" to (actionsTaken.any { it.contains("look") && !it.contains("look ") }),
                    "examine_item_before_taking" to actionsTaken.any { it.matches(Regex(".*look .+")) },
                    "take_items" to (actionsTaken.count { it.startsWith("take ") || it.startsWith("get ") } >= 2),
                    "check_inventory" to (actionsTaken.any { it.contains("inventory") || it == "i" }),
                    "equip_weapon" to actionsTaken.any { it.matches(Regex(".*(equip|wield).*(sword|dagger).*")) },
                    "equip_armor" to actionsTaken.any { it.matches(Regex(".*(equip|wear).*(armor|chainmail).*")) },
                    "drop_item" to actionsTaken.any { it.startsWith("drop ") },
                    "take_dropped_item_back" to (actionsTaken.count { it.startsWith("take ") || it.startsWith("get ") } >= 3),
                    "test_get_all" to (actionsTaken.any { it.contains("all") || it.contains("everything") }),
                    "test_partial_names" to (actionsTaken.any { it.matches(Regex(".*(sword|dagger|armor|mail)")) && !it.contains("Rusty") && !it.contains("Sharp") })
                )

                val completed = objectives.filter { it.value }.keys
                val remaining = objectives.filter { !it.value }.keys

                """
                IMPORTANT: You are already in the Armory which has 4 items:
                - Rusty Iron Sword (weapon, +5 damage)
                - Sharp Steel Dagger (weapon, +3 damage)
                - Worn Leather Armor (armor, +2 defense)
                - Heavy Chainmail (armor, +4 defense)

                MANDATORY TEST OBJECTIVES (must complete ALL):
                ✓ Completed (${completed.size}/10): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/10): ${remaining.joinToString(", ")}

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
            }
            is TestScenario.SocialInteraction -> {
                val objectives = mapOf(
                    "look_for_npcs" to actionsTaken.any { it == "look" },
                    "move_to_npcs" to actionsTaken.any { it.matches(Regex("[nsew]|north|south|east|west")) },
                    "talk_to_npc" to actionsTaken.any { it.startsWith("talk ") },
                    "persuade_npc" to (actionsTaken.any { it.contains("persuade") || it.contains("convince") }),
                    "intimidate_npc" to (actionsTaken.any { it.contains("intimidate") || it.contains("threaten") }),
                    "talk_to_2nd_npc" to (actionsTaken.count { it.startsWith("talk ") } >= 2)
                )

                val completed = objectives.filter { it.value }.keys
                val remaining = objectives.filter { !it.value }.keys

                """
                MANDATORY TEST OBJECTIVES:
                ✓ Completed (${completed.size}/6): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/6): ${remaining.joinToString(", ")}

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
            }
            is TestScenario.Exploratory -> """
                Try anything:
                - Random combinations
                - Edge cases
                - Invalid inputs
                - Ambiguous commands
            """.trimIndent()
            is TestScenario.FullPlaythrough -> """
                Play naturally to complete the dungeon:
                - Start by looking around and exploring (look, n/s/e/w)
                - Find and collect items, equip weapons/armor
                - Fight NPCs when encountered
                - Talk to friendly NPCs for information
                - Work toward reaching the end of the dungeon
            """.trimIndent()
            is TestScenario.QuestTesting -> {
                val objectives = mapOf(
                    "view_quests" to actionsTaken.any { it in listOf("quests", "journal", "j") },
                    "accept_quest_1" to actionsTaken.any { it.startsWith("accept ") },
                    "work_on_objectives" to (actionsTaken.any { it.contains("attack") || it.contains("take") || it.matches(Regex("[nsew]")) || it.startsWith("talk ") || it.startsWith("check ") }),
                    "check_progress" to (actionsTaken.count { it in listOf("quests", "journal", "j") } >= 2),
                    "accept_quest_2" to (actionsTaken.count { it.startsWith("accept ") } >= 2),
                    "claim_reward" to actionsTaken.any { it.startsWith("claim ") },
                    "test_abandon" to actionsTaken.any { it.startsWith("abandon ") }
                )

                val completed = objectives.filter { it.value }.keys
                val remaining = objectives.filter { !it.value }.keys

                """
                MANDATORY TEST OBJECTIVES:
                ✓ Completed (${completed.size}/7): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/7): ${remaining.joinToString(", ")}

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
            }
            is TestScenario.BadPlaythrough -> {
                val objectives = mapOf(
                    "initial_look" to actionsTaken.any { it == "look" },
                    "go_north_to_corridor" to actionsTaken.any { it in listOf("n", "north", "go north") },
                    "skip_armory" to !actionsTaken.any { it in listOf("w", "west", "go west") },
                    "skip_treasury" to !actionsTaken.any { it in listOf("e", "east", "go east") },
                    "rush_to_throne" to actionsTaken.any { it.matches(Regex(".*north.*")) && actionsTaken.size >= 2 },
                    "attack_skeleton_king_unarmed" to actionsTaken.any { it.contains("attack") && it.contains("skeleton") },
                    "continue_combat_until_death" to (actionsTaken.count { it == "attack" } >= 3)
                )

                val completed = objectives.filter { it.value }.keys
                val remaining = objectives.filter { !it.value }.keys

                """
                GOAL: Demonstrate poor gameplay and validate difficulty
                EXPECTED OUTCOME: Player should DIE to Skeleton King (50 HP, hostile, strong)

                MANDATORY BAD DECISIONS:
                ✓ Completed (${completed.size}/7): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/7): ${remaining.joinToString(", ")}

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
            }
            is TestScenario.BruteForcePlaythrough -> {
                // Track based on SUCCESSFUL completion (check responses, not just inputs)
                val objectives = mapOf(
                    "initial_look" to actionsTaken.any { it == "look" },
                    "go_to_armory" to recentHistory.any {
                        it.gmResponse.contains("Forgotten Armory", ignoreCase = true) ||
                        it.gmResponse.contains("Armory", ignoreCase = true)
                    },
                    "take_best_weapon" to recentHistory.any {
                        it.playerInput.contains("take") && it.playerInput.contains("iron") &&
                        it.gmResponse.contains("You take", ignoreCase = true)
                    },
                    "take_best_armor" to recentHistory.any {
                        it.playerInput.contains("take") && it.playerInput.contains("chainmail") &&
                        it.gmResponse.contains("You take", ignoreCase = true)
                    },
                    "equip_weapon" to recentHistory.any {
                        it.playerInput.contains("equip") && it.playerInput.contains("sword") &&
                        it.gmResponse.contains("You equip", ignoreCase = true)
                    },
                    "equip_armor" to recentHistory.any {
                        it.playerInput.contains("equip") && it.playerInput.contains("chainmail") &&
                        it.gmResponse.contains("You equip", ignoreCase = true)
                    },
                    "go_to_treasury" to recentHistory.any {
                        it.gmResponse.contains("Ancient Treasury", ignoreCase = true) ||
                        it.gmResponse.contains("Treasury", ignoreCase = true)
                    },
                    "take_health_potion" to recentHistory.any {
                        it.playerInput.contains("take") && it.playerInput.contains("potion") &&
                        it.gmResponse.contains("You take", ignoreCase = true)
                    },
                    "go_to_throne_room" to recentHistory.any {
                        it.gmResponse.contains("Throne Room", ignoreCase = true)
                    },
                    "attack_skeleton_king" to recentHistory.any {
                        it.playerInput.contains("attack") && it.playerInput.contains("skeleton") &&
                        it.gmResponse.contains("combat", ignoreCase = true)
                    },
                    "defeat_boss" to recentHistory.any {
                        it.gmResponse.contains("has been defeated", ignoreCase = true)
                    }
                )

                val completed = objectives.filter { it.value }.keys
                val remaining = objectives.filter { !it.value }.keys

                """
                GOAL: Complete dungeon through superior equipment and preparation
                EXPECTED OUTCOME: Player DEFEATS Skeleton King with proper gear

                MANDATORY PREPARATION:
                ✓ Completed (${completed.size}/11): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/11): ${remaining.joinToString(", ")}

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
            }
            is TestScenario.SmartPlaythrough -> {
                // Track room progression for navigation-aware objectives
                val inThroneRoom = recentHistory.any {
                    it.gmResponse.contains("Throne Room", ignoreCase = true)
                }

                val objectives = mapOf(
                    "initial_look" to actionsTaken.any { it == "look" },
                    "talk_to_guard" to actionsTaken.any { it.contains("talk") && it.contains("guard") },
                    "persuade_guard" to actionsTaken.any { it.contains("persuade") && it.contains("guard") },
                    "go_to_corridor" to recentHistory.any { it.gmResponse.contains("Dark Corridor", ignoreCase = true) },
                    "go_to_throne_room" to inThroneRoom,
                    "attempt_intimidate_king" to actionsTaken.any { it.contains("intimidate") && it.contains("skeleton") },
                    "avoid_or_minimize_combat" to (actionsTaken.count { it == "attack" } <= 2), // Minimal/no combat
                    "explore_secret_chamber" to actionsTaken.any { it.matches(Regex(".*(secret|chamber).*")) || actionsTaken.count { it == "n" } >= 3 },
                    "pass_strength_check" to actionsTaken.any { it.contains("check") && it.contains("door") },
                    "pass_intelligence_check" to actionsTaken.any { it.contains("check") && it.contains("rune") }
                )

                val completed = objectives.filter { it.value }.keys
                val remaining = objectives.filter { !it.value }.keys

                """
                GOAL: Complete dungeon through social skills and intelligence
                EXPECTED OUTCOME: Bypass combat via intimidation/persuasion, explore safely

                MANDATORY SMART TACTICS:
                ✓ Completed (${completed.size}/10): ${completed.joinToString(", ")}
                ✗ Remaining (${remaining.size}/10): ${remaining.joinToString(", ")}

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
            }
            is TestScenario.SkillProgression -> {
                // Extract current Dodge level from game context
                val dodgeLevel = currentContext.lines()
                    .find { it.contains("Dodge", ignoreCase = true) && it.contains("level", ignoreCase = true) }
                    ?.let { line ->
                        Regex("level\\s+(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
                    } ?: 0

                """
                YOUR GOAL: Level your Dodge skill from 0 to 10

                CURRENT STATUS:
                - Dodge Level: $dodgeLevel / 10 ${if (dodgeLevel >= 10) "✅ COMPLETE!" else ""}
                - Actions taken: ${actionsTaken.size}

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
                - Repeat until Dodge reaches level 10

                NAVIGATION TIPS:
                - Read room descriptions carefully - they show available exits
                - Try different exits to explore and find enemies
                - Safe zones (like towns) won't have hostile creatures
                - Combat areas will have enemies to fight

                THINK LIKE A PLAYER:
                - Where would enemies be located?
                - Which areas have I not explored yet?
                - Am I making progress toward the goal?
                - Should I check my skills to see XP progress?

                Play naturally and reason through the problem. Use the 'skills' command to track progress.

                Target: Complete within 120 actions
                """.trimIndent()
            }
        }

        return """
            Current game state:
            $currentContext

            Recent history:
            $historyText

            Scenario guidance:
            $scenarioGuidance

            Generate the next player input.
        """.trimIndent()
    }

    private fun parseResponse(responseText: String): GeneratedInput {
        return try {
            // Try to extract JSON from response
            val jsonStart = responseText.indexOf('{')
            val jsonEnd = responseText.lastIndexOf('}') + 1
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonText = responseText.substring(jsonStart, jsonEnd)
                json.decodeFromString<GeneratedInput>(jsonText)
            } else {
                // Fallback: treat entire response as input
                GeneratedInput(
                    input = responseText.trim(),
                    intent = "unknown",
                    expected = "unknown"
                )
            }
        } catch (e: Exception) {
            // Fallback on parse error
            GeneratedInput(
                input = responseText.trim(),
                intent = "parse_error",
                expected = "fallback"
            )
        }
    }

    /**
     * Extract unique room names from test history.
     * Looks for room name headers in GM responses.
     */
    private fun extractRoomsFromHistory(history: List<TestStep>): Set<String> {
        val roomNames = mutableSetOf<String>()
        val roomPattern = Regex("^([A-Z][a-zA-Z\\s]+)\\n", RegexOption.MULTILINE)

        for (step in history) {
            // Look for room name at start of GM response
            val match = roomPattern.find(step.gmResponse)
            if (match != null) {
                val roomName = match.groupValues[1].trim()
                // Filter out common non-room patterns
                if (roomName.length > 3 && !roomName.startsWith("You ") && !roomName.startsWith("The ")) {
                    roomNames.add(roomName)
                }
            }
        }

        return roomNames
    }
}

@Serializable
data class GeneratedInput(
    val input: String,
    val intent: String,
    val expected: String,
    val reasoning: String = ""  // Bot's thought process before action
)
