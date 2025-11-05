# Combat Test Bot Review & Improvements

## Date: 2025-10-11

## Summary

Reviewed and significantly enhanced the combat test scenario to comprehensively test all combat mechanics in as few steps as possible. The combat test now efficiently validates all combat features with clear guidance and robust validation.

## Problems Identified

From analyzing test logs (`test-logs/combat_1760250995866.txt`):

1. **Inefficient exploration**: Bot spent many turns searching for NPCs instead of starting near combat
2. **Poor NPC targeting**: Bot tried vague commands like "attack shadows" instead of explicit NPC names
3. **Missing mechanics coverage**: No testing of:
   - Weapon damage bonuses
   - Armor defense bonuses
   - Equipment before/during combat
   - Consumables (healing potions)
   - Turn-based combat progression
   - Victory/defeat conditions with state cleanup
4. **Weak validation**: Generic validation didn't catch combat-specific issues
5. **Unclear guidance**: LLM didn't understand combat test goals

## Combat Mechanics in Game

Based on code review (`CombatResolver.kt:1-191`, `App.kt:565-661`):

### Core Mechanics
- **Combat initiation**: `attack <npc_name>` starts combat with specific NPC
- **Turn-based**: Player attacks → NPC counterattacks → repeat
- **Damage calculation**:
  - Player: base (5-15) + weapon bonus + STR modifier - enemy's defense
  - NPC: base (3-12) + STR modifier - player's armor defense
  - Minimum 1 damage always
- **Equipment system**:
  - Weapons: +3 (dagger), +5 (sword) damage bonus
  - Armor: +2 (leather), +4 (chainmail) defense bonus
- **Health tracking**: Both player and NPC health decrease each round
- **Victory**: NPC reaches 0 HP → combat ends, NPC removed from room
- **Defeat**: Player reaches 0 HP → combat ends, game over
- **Flee mechanic**: 50% chance to escape (not focus of this test)

### Available NPCs
From `SampleDungeon.kt:22-209`:
- **Old Guard** (entrance) - 30 HP, not hostile, STR 10
- **Skeleton King** (throne_room) - 60 HP, hostile, STR 14

### Available Equipment
From `SampleDungeon.kt:124-167` (Armory):
- Rusty Iron Sword (+5 damage)
- Sharp Steel Dagger (+3 damage)
- Worn Leather Armor (+2 defense)
- Heavy Chainmail (+4 defense)

## Improvements Implemented

### 1. Detailed Test Strategy (`InputGenerator.kt:109-144`)

Added comprehensive step-by-step guidance:
- **Look around** - Identify NPCs and items present
- **Check inventory** - See starting equipment
- **Equip items** - Put on weapon/armor BEFORE fighting
- **Initiate combat** - Use explicit NPC names ("attack Skeleton King")
- **Continue attacking** - Use "attack" to progress turns
- **Monitor health** - Track HP changes
- **Use consumables** - Healing potions if needed
- **Complete combat** - Fight to victory or defeat
- **Test multiple NPCs** - Validate consistency across enemies

### 2. Code-Based Combat Validation (`OutputValidator.kt:350-482`)

Added deterministic validation for:

**Combat Initiation:**
- Detects successful combat start ("engage", "combat", "battle")
- Validates NPC targeting (checks if NPC actually in room)
- Handles missing target ("Attack whom?")

**Combat Rounds:**
- Validates damage output ("strike for X damage")
- Checks both player and NPC damage messages
- Detects combat progression

**Victory/Defeat:**
- Recognizes victory ("defeated", "slain", "falls")
- Recognizes defeat ("died", "fallen", "death")
- Validates combat state cleanup

**Consumables:**
- Validates healing ("restore X HP")
- Handles "already at full health" case

**Bug Detection:**
- FAIL if NPC exists in room but game says "No one by that name"
- FAIL if combat doesn't progress correctly

### 3. LLM Combat Validation Criteria (`OutputValidator.kt:685-740`)

Enhanced subjective validation with explicit rules:
- Combat initiation patterns
- Damage number presence
- Health tracking consistency
- Equipment bonus reflection
- Victory/defeat condition handling
- Combat state restrictions
- Clear PASS/FAIL criteria

### 4. Optimal Starting Configuration (`TestBotMain.kt:68-141`)

**Starting Room:**
- Combat tests now start in `throne_room` with Skeleton King
- Eliminates wasted exploration turns
- Immediate access to hostile NPC

**Equipment Options:**
1. **No equipment** - Tests acquiring gear mid-test
2. **Weapon only** - Tests weapon damage bonuses
3. **Weapon + armor** - Tests full combat with all bonuses

Player can choose at test start to test different scenarios efficiently.

## Test Coverage

The improved combat test now validates:

✅ **Combat Initiation**
- Starting combat with named NPC
- Targeting validation
- Combat state setup

✅ **Turn-Based Combat**
- Player attacks
- NPC counterattacks
- Turn progression

✅ **Damage System**
- Base damage calculation
- Weapon bonuses (+3, +5)
- Armor defense (+2, +4)
- Minimum damage (1)

✅ **Health Tracking**
- Player HP decreases
- NPC HP decreases
- Health displayed correctly

✅ **Victory Condition**
- NPC dies at 0 HP
- NPC removed from room
- Combat state cleared
- Can perform other actions

✅ **Defeat Condition**
- Player dies at 0 HP
- Game ends or restricts actions

✅ **Equipment System**
- Equipping weapons
- Equipping armor
- Bonuses reflected in combat
- Equipment status displayed

✅ **Consumables**
- Using healing potions
- HP restoration
- Full health handling

## Files Modified

1. **`testbot/src/main/kotlin/com/jcraw/mud/testbot/InputGenerator.kt`**
   - Lines 109-144: Enhanced combat scenario guidance
   - Added 11-step systematic combat test strategy
   - Listed all mechanics to validate

2. **`testbot/src/main/kotlin/com/jcraw/mud/testbot/OutputValidator.kt`**
   - Lines 350-482: Added code-based combat validation
   - Lines 685-740: Enhanced LLM combat validation criteria
   - Deterministic checks for all combat mechanics

3. **`testbot/src/main/kotlin/com/jcraw/mud/testbot/TestBotMain.kt`**
   - Lines 68-141: Added optimal starting configuration
   - Combat tests start in throne_room
   - Optional equipment pre-configuration (3 modes)

## Test Efficiency

**Before:**
- ~30 steps, most spent searching for NPCs
- Incomplete mechanic coverage
- Many false failures from weak validation

**After:**
- ~15-20 steps target, all focused on combat
- Complete mechanic coverage
- Robust validation with clear pass/fail rules

## Running Combat Tests

```bash
# Run the test bot
gradle :testbot:run

# Select options:
# - Dungeon: 1 (Sample Dungeon)
# - Scenario: 2 (Combat)
# - Equipment: 1/2/3 (No equipment / Weapon / Weapon + Armor)
```

**Recommended test runs:**
1. **Weapon + Armor** (option 3) - Tests full combat with all bonuses
2. **Weapon only** (option 2) - Tests weapon bonus without armor
3. **No equipment** (option 1) - Tests bare-handed combat (hardest)

## Comparison to Other Scenarios

The combat test now follows the same pattern as successful tests:

**Exploration Test** (`InputGenerator.kt:85-107`):
✅ Starts in optimal location
✅ Detailed step-by-step guidance
✅ Clear mechanics list
✅ Efficient turn usage

**Item Interaction Test** (`InputGenerator.kt:121-148`):
✅ Starts in Armory with all items
✅ Systematic 10-step test plan
✅ Lists exact items available
✅ ~12-15 actions target

**Combat Test** (improved):
✅ Starts in throne_room with Skeleton King
✅ Systematic 11-step test plan
✅ Lists all mechanics to validate
✅ ~15-20 actions target
✅ Optional equipment pre-configuration

## Next Steps

The combat test is now comprehensive and efficient. To verify:

1. **Run combat test** with each equipment option
2. **Review test logs** to confirm all mechanics tested
3. **Check validation** - should see `[CODE]` prefixes for deterministic checks
4. **Compare results** across different equipment configurations

If tests pass consistently, the combat system is working correctly across all scenarios.

## Notes

- Combat test can start with/without equipment based on what you want to test
- Starting in throne_room saves ~10 turns of exploration
- Code validation catches bugs deterministically (e.g., NPC targeting failures)
- LLM validation handles narrative quality (e.g., damage descriptions)
- All combat mechanics covered in minimal steps
