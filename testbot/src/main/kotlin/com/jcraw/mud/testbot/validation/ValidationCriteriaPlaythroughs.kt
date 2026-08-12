@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot.validation

/**
 * Playthrough scenario validation criteria strings (MUD-034f).
 */
internal object ValidationCriteriaPlaythroughs {

    val badPlaythrough = """
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

    val bruteForcePlaythrough = """
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

    val smartPlaythrough = """
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

    val skillProgression = """
        Check that:
        - Response doesn't contain errors or crashes
        - Combat/skill actions are processed correctly
        - Skill XP or level-up messages appear when appropriate
        - Commands are recognized and produce valid output

        **PASS Criteria:**
        - Valid response to command (combat, movement, skills check, etc.)
        - No error messages or crashes
        - Progression feedback is clear (XP gains, level-ups, etc.)

        **FAIL Criteria:**
        - Crashes or errors
        - Invalid command responses
        - Complete silence/no feedback for valid actions

        This is a long-running test focused on skill leveling, so minor issues are acceptable.
    """.trimIndent()

    val treasureRoomPlaythrough = """
        Check that:
        - Treasure room mechanics work correctly (examine, take, return, swap)
        - Pedestal/altar descriptions are clear and accurate
        - Item swapping works as expected
        - Finalization when leaving the room functions properly
        - No errors or crashes during treasure room interactions

        **PASS Criteria:**
        - Commands are recognized and produce appropriate responses
        - Treasure room state changes correctly (items taken/returned)
        - Clear feedback on actions
        - No error messages

        **FAIL Criteria:**
        - Crashes or unhandled errors
        - Commands not recognized
        - Treasure room mechanics don't work as designed
        - Invalid state transitions

        This validates the treasure room system mechanics.
    """.trimIndent()
}
