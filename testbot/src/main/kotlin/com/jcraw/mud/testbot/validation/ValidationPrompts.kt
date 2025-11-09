package com.jcraw.mud.testbot.validation

import com.jcraw.mud.core.WorldState
import com.jcraw.mud.testbot.TestScenario
import com.jcraw.mud.testbot.TestStep

/**
 * LLM prompt construction for different validation scenarios.
 */
object ValidationPrompts {
    fun buildSystemPrompt(scenario: TestScenario): String {
        return """
            You are a QA validator for a text-based MUD (Multi-User Dungeon) game engine.
            Your job is to verify that the game responds correctly and coherently to player inputs.

            Scenario: ${scenario.name}
            Description: ${scenario.description}

            CRITICAL: Be LENIENT with validation. Only fail if there's a clear error or crash.
            Normal MUD responses like room descriptions or "You can't go that way" are VALID.

            Validation criteria:
            1. Response is coherent and makes sense given the input
            2. Response follows MUD conventions (room descriptions, combat mechanics, etc.)
            3. Response maintains consistency with previous history
            4. No obvious errors, crashes, or nonsensical text
            5. Response advances the game state appropriately

            DEFAULT TO PASS unless you see a clear problem like:
            - Error messages when action should succeed
            - Crash or exception text
            - Completely nonsensical response
            - Violates game mechanics

            Respond with JSON in this format:
            {
                "pass": true/false,
                "reason": "brief explanation",
                "details": {
                    "coherence": "pass/fail",
                    "consistency": "pass/fail",
                    "mechanics": "pass/fail"
                }
            }
        """.trimIndent()
    }

    fun buildUserContext(
        scenario: TestScenario,
        playerInput: String,
        gmResponse: String,
        recentHistory: List<TestStep>,
        expectedOutcome: String?,
        worldState: WorldState?
    ): String {
        // Extract room name from CURRENT response for tracking
        val currentRoomName = gmResponse.lines().firstOrNull()?.trim()?.takeIf {
            it.isNotBlank() && !it.startsWith("You ")
        }

        // Extract room name from PREVIOUS step for movement validation
        val previousRoomName = if (recentHistory.isNotEmpty()) {
            val lastResponse = recentHistory.last().gmResponse
            // Try to extract room name from first line
            lastResponse.lines().firstOrNull()?.trim()?.takeIf {
                it.isNotBlank() && !it.startsWith("You ")
            }
        } else {
            null
        }

        val historyText = if (recentHistory.isEmpty()) {
            "No previous history."
        } else {
            recentHistory.takeLast(2).joinToString("\n") { step ->
                "Player: ${step.playerInput}\nGM: ${step.gmResponse.take(150)}"
            }
        }

        // Track inventory from history for item validation
        val trackedInventory = CodeValidationRules.trackInventoryFromHistory(recentHistory)

        // Add game state context for better validation
        val gameStateContext = if (worldState != null) {
            val currentRoom = worldState.getCurrentRoomView()
            val player = worldState.player
            val roomTransitionInfo = buildString {
                if (previousRoomName != null && currentRoomName != null) {
                    if (previousRoomName != currentRoomName) {
                        append("\n            - ROOM CHANGED: \"$previousRoomName\" → \"$currentRoomName\" (successful movement)")
                    } else {
                        append("\n            - Same room name: \"$previousRoomName\" (could be: stayed in place, OR moved to different room with same name)")
                    }
                } else if (previousRoomName != null) {
                    append("\n            - Previous room: $previousRoomName")
                } else if (currentRoomName != null) {
                    append("\n            - Current room from response: $currentRoomName")
                }
            }
            val inventoryInfo = if (trackedInventory.isNotEmpty()) {
                "\n            - Items in inventory (tracked): ${trackedInventory.joinToString(", ")}"
            } else {
                "\n            - Inventory is empty (tracked)"
            }
            """

            Current game state:
            - Player location: ${currentRoom?.name ?: "Unknown"}$roomTransitionInfo
            - Available exits: ${currentRoom?.exits?.keys?.joinToString(", ") { it.displayName } ?: "none"}
            - Player health: ${player.health}/${player.maxHealth}
            - Room entities: ${currentRoom?.entities?.joinToString(", ") { it.name } ?: "none"}$inventoryInfo
            """.trimIndent()
        } else {
            ""
        }

        val scenarioCriteria = buildScenarioCriteria(scenario)
        val expectedText = expectedOutcome?.let { "\nExpected outcome: $it" } ?: ""

        return """
            Recent history:
            $historyText
            $gameStateContext

            Current turn:
            Player input: $playerInput
            GM response: $gmResponse
            $expectedText

            Scenario-specific criteria:
            $scenarioCriteria

            Validate this response.
        """.trimIndent()
    }

    private fun buildScenarioCriteria(scenario: TestScenario): String {
        return when (scenario) {
            is TestScenario.Exploration -> buildExplorationCriteria()
            is TestScenario.Combat -> buildCombatCriteria()
            is TestScenario.SkillChecks -> buildSkillChecksCriteria()
            is TestScenario.ItemInteraction -> buildItemInteractionCriteria()
            is TestScenario.SocialInteraction -> buildSocialInteractionCriteria()
            is TestScenario.QuestTesting -> buildQuestTestingCriteria()
            is TestScenario.Exploratory -> buildExploratoryCriteria()
            is TestScenario.FullPlaythrough -> buildFullPlaythroughCriteria()
            is TestScenario.BadPlaythrough -> buildBadPlaythroughCriteria()
            is TestScenario.BruteForcePlaythrough -> buildBruteForcePlaythroughCriteria()
            is TestScenario.SmartPlaythrough -> buildSmartPlaythroughCriteria()
        }
    }

    private fun buildExplorationCriteria() = """
        Check that:
        - Room descriptions are vivid and detailed
        - Look commands provide appropriate information
        - Descriptions vary but remain consistent with previous descriptions

        MOVEMENT VALIDATION RULES (CRITICAL - READ EVERY WORD):

        **RULE 1: ANY room description starting with a room name = SUCCESSFUL MOVEMENT → PASS**
        - If response has format "Room Name\n[description]\nExits: ..." → ALWAYS PASS
        - Does NOT matter if you've seen this room name before
        - Does NOT matter if description is different from last time
        - This is how the game engine shows you entered a room

        **RULE 2: "You can't go that way" = CORRECT REJECTION → PASS**
        - This means the player tried an invalid direction
        - Check game state exits FIRST before failing
        - ONLY fail if game state shows the exit DOES exist but got rejection

        **RULE 3: Game shows rooms DIRECTLY, not "You move..."**
        - NO "You move north" or "You walk east" messages exist
        - Seeing a room description IS the movement confirmation

        **RULE 4: Trust "ROOM CHANGED" markers**
        - If game state shows "ROOM CHANGED: X → Y" → ALWAYS PASS

        **EXAMPLES FROM ACTUAL FAILED VALIDATIONS (ALL SHOULD PASS):**

        ✓ Player: "go east" from "Dark Corridor"
          Response: "Ancient Treasury\nYou enter the Ancient Treasury, where the glimmer..."
          → PASS (room description = successful movement, even if first time seeing this room)

        ✓ Player: "go south" from "Ancient Treasury" (only exit: west)
          Response: "You can't go that way."
          → PASS (correct rejection of invalid direction, south doesn't exist)

        ✓ Player: "go east" from "Dark Corridor" (exits: south, east, west, north)
          Response: "Ancient Treasury\nYou enter..."
          → PASS (east IS a valid exit, got room description = success)

        ✗ Player: "go north" + Response: "Error: NullPointerException"
          → FAIL (crash)

        ✗ Player: "go east" (east IS in exits) + Response: "You can't go that way."
          → FAIL (game state shows exit exists but rejected)

        **DO NOT FAIL** for seeing the same room name twice. Players can visit rooms multiple times!
        **DO NOT FAIL** for "You can't go that way" unless the game state proves the exit exists!
        **DO NOT FAIL** for getting a room description after a movement command - this is SUCCESS!
    """.trimIndent()

    private fun buildCombatCriteria() = """
        COMBAT VALIDATION RULES:

        **Combat Initiation:**
        - "attack <npc_name>" → should start combat with narrative
        - "You engage..." or "combat!" or similar = SUCCESS
        - "No one by that name" when NPC IS in room = FAIL (bug)
        - "No one by that name" when NPC NOT in room = PASS (correct)
        - "Attack whom?" when no target specified = PASS (correct)

        **Combat Rounds:**
        - "strike for X damage" + "retaliates for Y damage" = PASS (ongoing combat)
        - "strike for X damage" WITHOUT retaliation = PASS (killing blow, enemy died)
        - Damage numbers should be present (e.g., "5 damage", "12 damage")
        - Health tracking should be consistent
        - Both player and NPC take damage each round EXCEPT final killing blow

        **CRITICAL - Killing Blow Pattern:**
        - When NPC HP reaches 0, response shows: "You strike for X damage!" (NO retaliation)
        - This is CORRECT behavior = PASS
        - Next attack should say "No one by that name here" (NPC removed) = PASS
        - Do NOT fail when you see damage without retaliation - it means victory!

        **Equipment Bonuses:**
        - Weapons should increase damage (higher numbers with weapon equipped)
        - Armor should reduce incoming damage (lower NPC damage with armor)
        - "equip weapon" → "+X damage" message = PASS

        **Victory Condition:**
        - NPC reaches 0 HP → "defeated", "slain", "falls" = PASS
        - OR: "strike for X damage" without retaliation = PASS (killing blow)
        - NPC should be removed from room after defeat
        - Combat should end, allowing other actions

        **Defeat Condition:**
        - Player reaches 0 HP → "died", "fallen", "death" = PASS
        - Game should end or restrict actions after defeat

        **Consumables:**
        - "use potion" during combat → "restore X HP" = PASS
        - Health should increase after using healing items
        - "already at full health" = PASS (correct behavior)

        **Combat State:**
        - Can't move during combat = PASS
        - Can't take items during combat = PASS
        - Must use "attack" to continue combat = PASS

        PASS for:
        - Combat starts correctly with named NPC
        - Damage is dealt each round (with numbers)
        - Killing blow (damage without retaliation)
        - Health tracking is consistent
        - Victory/defeat conditions work
        - Equipment bonuses are reflected in damage
        - Consumables heal during combat

        FAIL only for:
        - Combat doesn't start when NPC is present
        - No damage numbers in combat rounds
        - Health doesn't decrease
        - Victory/defeat doesn't end combat properly
        - Equipment bonuses don't affect combat
        - Crashes or errors
    """.trimIndent()

    private fun buildSkillChecksCriteria() = """
        Check that:
        - D20 rolls are reported
        - Stat modifiers are applied
        - Success/failure is determined correctly
        - Critical successes/failures are handled
    """.trimIndent()

    private fun buildItemInteractionCriteria() = """
        CONTEXT: Player starts in the Armory with 4 items available:
        - Rusty Iron Sword (weapon)
        - Sharp Steel Dagger (weapon)
        - Worn Leather Armor (armor)
        - Heavy Chainmail (armor)

        IMPORTANT: Check "Items in inventory (tracked)" in game state to know what's been picked up!

        VALIDATION RULES (STATE-AWARE):

        1. **TAKE/GET commands:**
           - "You take X" = PASS (item successfully taken)
           - "You can't take that" when item IS in inventory (tracked) = PASS (correct rejection)
           - "You can't take that" when item NOT in inventory and NOT in room = PASS (item doesn't exist)
           - "You can't take that" when item NOT in inventory BUT IS in room entities = FAIL (bug)

        2. **LOOK/EXAMINE commands:**
           - ANY descriptive text (even short like "A sharp dagger") = PASS
           - Only FAIL for crashes/errors/exceptions

        3. **EQUIP/WIELD commands:**
           - "You equip X" or "You wield X" = PASS

        4. **DROP commands:**
           - "You drop X" = PASS

        5. **INVENTORY tracking:**
           - Use "Items in inventory (tracked)" to know what player has
           - Items can't be taken twice - rejection is CORRECT if already in inventory

        PASS for:
        - Successful take/drop/equip/look actions
        - "You can't take that" when item already in inventory
        - Any item description (short or long, doesn't matter)
        - Inventory listings

        FAIL only for:
        - Crashes or error messages
        - Taking an item that's already in inventory (should be rejected)
        - Rejecting an item that exists in room and isn't in inventory yet
    """.trimIndent()

    private fun buildSocialInteractionCriteria() = """
        Check that:
        - NPC dialogue is personality-appropriate
        - Social checks (persuasion/intimidation) work
        - NPCs respond coherently
        - Conversation maintains context
    """.trimIndent()

    private fun buildQuestTestingCriteria() = """
        Check that:
        - Quest viewing commands show quests correctly
        - Accept/abandon quest commands work properly
        - Quest progress is tracked accurately
        - Claim rewards command succeeds when quest is complete
        - Rewards are properly awarded (XP, gold, items)
    """.trimIndent()

    private fun buildExploratoryCriteria() = """
        Check that:
        - Invalid inputs are handled gracefully
        - Edge cases don't crash the game
        - Ambiguous commands receive helpful feedback
    """.trimIndent()

    private fun buildFullPlaythroughCriteria() = """
        Check that:
        - Game progresses naturally
        - All mechanics work together
        - Story and world remain consistent
    """.trimIndent()

    private fun buildBadPlaythroughCriteria() = """
        BAD PLAYTHROUGH VALIDATION (Expect player DEATH):

        This scenario intentionally plays poorly to validate difficulty.

        **Expected Behavior:**
        - Player rushes to throne room WITHOUT gear
        - Attacks Skeleton King (50 HP, STR 16) with base stats only (STR 14, no bonuses)
        - Player should DIE in ~3-5 combat rounds
        - Death message should appear ("You have died", "You fall", etc.)

        **PASS Criteria:**
        - Combat initiates correctly with Skeleton King
        - Damage numbers are reasonable (player deals ~5-8 dmg, King deals ~8-12 dmg per round)
        - Player health decreases each round
        - Player reaches 0 HP and dies
        - Death is narrated appropriately

        **FAIL Criteria:**
        - Player somehow wins without gear (game too easy - BUG!)
        - Combat doesn't start
        - No damage is dealt
        - Player doesn't die after many rounds (difficulty too low)
        - Crashes or errors

        This validates the game is CHALLENGING and NOT trivially easy.
    """.trimIndent()

    private fun buildBruteForcePlaythroughCriteria() = """
        BRUTE FORCE PLAYTHROUGH VALIDATION (Expect player VICTORY):

        This scenario collects best gear and fights strategically.

        **Expected Behavior:**
        - Player visits armory, takes Rusty Iron Sword (+5 dmg) and Heavy Chainmail (+4 def)
        - Player equips both items
        - Player visits treasury, takes health potion
        - Player fights Skeleton King WITH equipment bonuses
        - Player should WIN in ~8-12 combat rounds
        - Victory message should appear ("defeated", "slain", "falls")

        **PASS Criteria:**
        - All items collected successfully
        - Equipment bonuses apply (higher damage output visible)
        - Combat is longer but winnable with gear
        - Player defeats Skeleton King
        - Victory is narrated appropriately
        - Player can explore after victory (throne room, secret chamber)

        **FAIL Criteria:**
        - Can't collect or equip items
        - Equipment bonuses don't work (same damage as unarmed)
        - Player dies even with full gear (game too hard - BUG!)
        - Victory doesn't end combat properly
        - Crashes or errors

        This validates the game is BEATABLE with proper preparation.
    """.trimIndent()

    private fun buildSmartPlaythroughCriteria() = """
        SMART PLAYTHROUGH VALIDATION (Expect SOCIAL VICTORY):

        This scenario uses social skills and intelligence to avoid combat.

        **Expected Behavior:**
        - Player persuades Old Guard (Easy CHA check) for intel
        - Player attempts to intimidate Skeleton King (Hard CHA check)
          - SUCCESS: King backs down, becomes non-hostile, NO combat!
          - FAILURE: Player falls back to minimal combat
        - Player explores secret chamber
        - Player passes skill checks (STR check on door, INT check on runes)
        - Expected: 0-2 combat rounds total (social victory preferred)

        **IMPORTANT: Skill checks can FAIL due to dice rolls - this is NORMAL!**
        - "Failed! The Old Guard shakes his head..." = PASS (dice roll failed, mechanics work)
        - "Success! The Old Guard reveals..." = PASS (dice roll succeeded)
        - ONLY FAIL if: "Cannot persuade that" when NPC IS in room = BUG

        **IMPORTANT: Social checks require target to be in same room!**
        - "Cannot intimidate that" when NPC NOT in room = PASS (correct behavior)
        - "Cannot intimidate that" when NPC IS in room = FAIL (BUG!)
        - Check game state "Room entities" to verify if NPC is present

        **PASS Criteria:**
        - Social check MECHANICS work (d20 roll + stat modifier vs DC)
        - Success/failure is determined by dice roll (BOTH outcomes are valid!)
        - "Failed!" message shows skill check ran but player lost dice roll = PASS
        - "Success!" message shows skill check ran and player won dice roll = PASS
        - "Cannot X that" only when target not in room/invalid = PASS
        - Success on intimidation prevents/ends combat when it occurs
        - Skill checks in secret chamber work correctly
        - Multiple solution paths exist (not just combat)

        **FAIL Criteria:**
        - Social checks don't trigger at all (no dice roll, no "Success"/"Failed" message)
        - "Cannot persuade/intimidate that" when NPC IS in current room (check game state!)
        - Intimidation succeeds but combat continues anyway (BUG!)
        - Can't access secret chamber
        - Crashes or errors

        This validates MULTIPLE SOLUTION PATHS and non-combat gameplay.
    """.trimIndent()

}
