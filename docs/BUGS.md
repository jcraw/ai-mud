# Known Bugs

*Last Updated: 2025-10-13*
*Source: Automated test bot results from test-logs/*

This document tracks known bugs discovered through automated testing. Bugs are prioritized by severity and impact on gameplay.

## Critical Priority

### BUG-001: Combat State Desync - Skeleton King Disappears During Combat
**Severity:** CRITICAL
**Status:** Open
**Discovered:** 2025-10-13 (brute_force_playthrough test)
**Pass Rate Impact:** 36% pass rate (32/50 failures)

**Symptoms:**
- After successfully attacking Skeleton King 5 times, the game loses track of the NPC
- Command `continue attacking skeleton king` fails with "No one by that name here"
- Command `attack skeleton king` (without "continue") intermittently works
- Player appears stuck in combat with non-existent entity

**Reproduction:**
1. Equip gear (iron sword, chainmail)
2. Enter boss room
3. Attack Skeleton King 5 times successfully
4. Continue attacking - entity disappears

**Test Evidence:**
- Steps 11-15: Attacks work ✓
- Steps 18-20, 23-45, 47-50: "No one by that name here" ✗
- Steps 21, 22, 46: Attack works sporadically ✓

**Suspected Locations:**
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/QuestTracker.kt` - Combat state tracking
- `core/src/main/kotlin/com/jcraw/mud/core/WorldState.kt` - NPC entity management
- Combat intent handling in perception/reasoning modules

**Impact:** Combat becomes unwinnable after initial attacks. Breaks brute force strategy completely.
**FIX** If the skeleton king is killed, the test needs to recognize that he is gone and it is correct to not be able to attack him any longer
---

### BUG-002: Social Checks Non-Functional on Boss NPCs
**Severity:** CRITICAL
**Status:** Open
**Discovered:** 2025-10-13 (smart_playthrough test)
**Pass Rate Impact:** 14% pass rate (43/50 failures)

**Symptoms:**
- All intimidate attempts fail or produce nonsensical responses
- All persuade attempts return "Cannot persuade that."
- Repeated social interaction attempts have no effect
- Skeleton King appears immune to all CHA-based interactions
- No skill check mechanics trigger for social interactions

**Reproduction:**
1. Talk to Skeleton King (works)
2. Try to intimidate Skeleton King (fails)
3. Try to persuade Skeleton King (returns "Cannot persuade that.")
4. Repeat with different approaches (all fail)

**Test Evidence:**
- Step 3: Persuade Old Guard works ✓
- Step 5: Intimidate Skeleton King fails ✗
- Steps 9-50: All social interactions on boss fail ✗

**Suspected Locations:**
- `perception/` module - Social intent parsing
- `reasoning/` module - Social check mechanics and CHA calculations
- Boss NPC configuration may have missing social interaction flags

**Impact:** Social victory path is impossible. Completely breaks smart playthrough strategy and invalidates CHA-based character builds.
**FIX** need to verify that social interactions are implemented and if not, implement them in the codebase so that this test will work, which should extend to other social situations that will work in the game
---

### BUG-003: Death/Respawn Bug - Cannot Attack After Player Death
**Severity:** HIGH
**Status:** Open
**Discovered:** 2025-10-13 (bad_playthrough test)
**Pass Rate Impact:** 87% pass rate (1/8 failures)

**Symptoms:**
- After player dies and respawns, NPC 'skeleton king' is still in room but combat won't start
- Command `attack skeleton king` doesn't initiate combat
- NPC visible in room description but unattackable

**Reproduction:**
1. Fight Skeleton King without gear
2. Die to Skeleton King
3. Press any key to respawn
4. Try to attack Skeleton King again (fails)

**Test Evidence:**
- Steps 4-7: Combat works normally ✓
- Step 8: After respawn, "NPC 'skeleton king' in room but combat didn't start" ✗

**Suspected Locations:**
- `app/src/main/kotlin/com/jcraw/app/App.kt:302-314, 671-683` - Death/respawn handling
- World state reset logic may not properly reset combat state
- NPC entity state may persist incorrectly after death

**Impact:** Game becomes unplayable after death/respawn. Forces player to quit and restart application.
**FIX** this is correct that the game does not work after death.  it should end the test when the player dies
---

## High Priority

### BUG-004: Cannot Use Potions During/After Combat
**Severity:** HIGH
**Status:** Open
**Discovered:** 2025-10-13 (brute_force_playthrough test)

**Symptoms:**
- Player cannot use health potion when needed
- Command `use health potion` fails

**Reproduction:**
1. Pick up health potion
2. Enter combat
3. Try to use health potion (fails)

**Test Evidence:**
- Step 9: Take health potion ✓
- Step 17: Use health potion ✗

**Suspected Locations:**
- `perception/` module - Consumable intent parsing during combat
- `reasoning/` module - Combat state may block consumable use

**Impact:** Combat healing is broken. Makes difficult encounters impossible without excessive grinding.
**FIX** drinking a potion during combat should work and should take the players turn for that round
---

## Medium Priority

### BUG-005: Skill Checks on Features Not Triggering
**Severity:** MEDIUM
**Status:** Open
**Discovered:** 2025-10-13 (smart_playthrough test)

**Symptoms:**
- `check stuck door` doesn't trigger STR check
- `check rune inscription` doesn't trigger INT check
- Expected D&D-style skill checks don't fire

**Reproduction:**
1. Find room with stuck door feature
2. Command `check stuck door` (no STR check)
3. Find room with rune inscription
4. Command `check rune inscription` (no INT check)

**Test Evidence:**
- Step 7: Check stuck door - no strength check triggered ✗
- Step 8: Check rune inscription - no intelligence check triggered ✗

**Suspected Locations:**
- `perception/` module - Feature interaction intent parsing
- `reasoning/` module - Skill check trigger logic
- Feature entity definitions may lack check metadata

**Impact:** Skill check exploration gameplay is broken. Reduces gameplay variety and stat build diversity.
**FIX** determine if the skill system needs to be fixed in the engine to make it work, and fix it.  it shouldnt be fixed for just these cases, but in a very general way so that it will work for all interactions.  the player should be able to ask the GM to do ANYTHING and the GM should be able to figure out what skill that will require and do a skill check using a programmatic dice roll (not LLM dice roll)
---

### BUG-006: Navigation Command Parser Issue
**Severity:** MEDIUM
**Status:** Open
**Discovered:** 2025-10-13 (brute_force_playthrough test)

**Symptoms:**
- Command `go to throne room` returns "unknown command"
- Natural language navigation fails
- Simple `go north` works as workaround

**Reproduction:**
1. Be in room with throne room to the north
2. Command `go to throne room` (fails)
3. Command `go north` (works)

**Test Evidence:**
- Step 16: "go to throne room" returns unknown command ✗

**Suspected Locations:**
- `perception/` module - Natural language intent parsing for navigation
- Intent recognition may be too strict

**Impact:** Minor UX issue. Simple directional commands work, but reduces natural language immersion.
**FIX** this should work to keep natural language immersion, the LLM should be able to figure out what exit they are referring to 
---

## Test Statistics Summary

| Test Scenario | Pass Rate | Critical Issues |
|---------------|-----------|-----------------|
| bad_playthrough | 87% (7/8) | BUG-003 (respawn) |
| brute_force_playthrough | 36% (18/50) | BUG-001 (combat desync), BUG-004 (potions) |
| smart_playthrough | 14% (7/50) | BUG-002 (social checks), BUG-005 (skill checks) |

## Next Steps

Priority order for fixes:
1. **BUG-001** - Fix combat state desync (blocks combat gameplay)
2. **BUG-002** - Implement social check mechanics (blocks social gameplay)
3. **BUG-003** - Fix respawn combat initiation (blocks death recovery)
4. **BUG-004** - Enable potion use in combat (blocks healing)
5. **BUG-005** - Implement feature skill checks (reduces variety)
6. **BUG-006** - Improve NLU for navigation (polish)

## Testing Protocol

After fixes, re-run all three test scenarios:
```bash
./test_bad_playthrough.sh
./test_brute_force_playthrough.sh
./test_smart_playthrough.sh
```

Target metrics:
- bad_playthrough: 100% pass rate
- brute_force_playthrough: 90%+ pass rate
- smart_playthrough: 90%+ pass rate
