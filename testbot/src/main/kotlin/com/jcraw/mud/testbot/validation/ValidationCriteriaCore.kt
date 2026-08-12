@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot.validation

/**
 * Core scenario validation criteria strings (MUD-034f).
 * Stored as properties so function-token ceilings do not apply to prompt bodies.
 */
internal object ValidationCriteriaCore {

    val exploration = """
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

    val combat = """
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

    val skillChecks = """
        Check that:
        - D20 rolls are reported
        - Stat modifiers are applied
        - Success/failure is determined correctly
        - Critical successes/failures are handled
    """.trimIndent()

    val itemInteraction = """
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

    val socialInteraction = """
        Check that:
        - NPC dialogue is personality-appropriate
        - Social checks (persuasion/intimidation) work
        - NPCs respond coherently
        - Conversation maintains context
    """.trimIndent()

    val questTesting = """
        Check that:
        - Quest viewing commands show quests correctly
        - Accept/abandon quest commands work properly
        - Quest progress is tracked accurately
        - Claim rewards command succeeds when quest is complete
        - Rewards are properly awarded (XP, gold, items)
    """.trimIndent()

    val exploratory = """
        Check that:
        - Invalid inputs are handled gracefully
        - Edge cases don't crash the game
        - Ambiguous commands receive helpful feedback
    """.trimIndent()

    val fullPlaythrough = """
        Check that:
        - Game progresses naturally
        - All mechanics work together
        - Story and world remain consistent
    """.trimIndent()
}
