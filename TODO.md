# TODO List

*Last Updated: 2025-10-15*

## Current Status

**Test Results:**
- ✅ brute_force_playthrough: **100% pass rate (17/17)**
- ✅ bad_playthrough: **100% pass rate (8/8)**
- ✅ smart_playthrough: **100% pass rate (7/7)**
- ✅ **Total Tests: ~298 (across all modules)**
- ✅ **Phase 2 Progress: 5/5 integration tests COMPLETE**
- ✅ **Phase 3 Progress: 4/4 integration tests COMPLETE (SaveLoadIntegrationTest - 13 tests, ProceduralDungeonIntegrationTest - 21 tests, NavigationIntegrationTest - 21 tests, FullGameplayIntegrationTest - 15 tests)**
- ✅ **Phase 4 Progress: 4/4 E2E scenario tests COMPLETE (BruteForcePlaythroughTest - 3 tests, SmartPlaythroughTest - 3 tests, BadPlaythroughTest - 3 tests, AllPlaythroughsTest - 3 tests)**

---

## Completed - Testing Migration & Improvement

### ✅ TESTING-001: Comprehensive Test Suite Implementation - COMPLETE
**Priority:** HIGH (COMPLETED)
**Description:** Migrate shell script tests to proper unit/integration tests following Kotlin best practices
**Documentation:** [TESTING.md](docs/TESTING.md)
**Status:** All 4 phases complete - 82 unit tests + 128 integration tests + 12 E2E scenario tests = **222 new tests**

#### Phase 1: Core Unit Tests - ✅ COMPLETE
Created 82 new behavioral tests covering core systems:

- ✅ `core/src/test/kotlin/com/jcraw/mud/core/EquipmentSystemTest.kt` **(16 tests)**
  - Equipment swapping, bonus calculations, immutability
  - Weapon and armor equip/unequip mechanics

- ✅ `core/src/test/kotlin/com/jcraw/mud/core/InventorySystemTest.kt` **(19 tests)**
  - Add/remove/find items, lifecycle tests
  - Immutability and state consistency

- ✅ `core/src/test/kotlin/com/jcraw/mud/core/WorldStateTest.kt` **(33 tests)**
  - Room navigation, player management
  - Entity operations, immutability tests

- ✅ `reasoning/src/test/kotlin/com/jcraw/mud/reasoning/CombatResolverTest.kt` **(14 tests)**
  - Damage calculations, equipment modifiers
  - Combat flow, stat bonuses, flee mechanics

#### Phase 2: Integration Tests - ✅ COMPLETE
Replaced shell scripts with integration tests in `app/src/test/kotlin/com/jcraw/app/integration/`:

**Pre-requisite:**
- ✅ Fix `app/src/test/kotlin/com/jcraw/app/GameServerTest.kt` compilation errors (COMPLETE - removed outdated test, added test dependencies)

**Integration Tests:**
- ✅ `CombatIntegrationTest.kt` (replaces `test_combat.sh`) - **7 tests, all passing**
  - Basic combat flow
  - Equipment modifiers (weapons/armor)
  - Combat end (player death, NPC death, flee)
  - NPC removal after defeat

- ✅ `ItemInteractionIntegrationTest.kt` (replaces `test_items.sh`) - **13 tests, all passing**
  - Take single/all items
  - Drop items (including equipped)
  - Equip weapons and armor
  - Weapon swapping
  - Use consumables (in and out of combat)
  - Healing potion mechanics
  - Non-pickupable items
  - Inventory display

- ✅ `QuestIntegrationTest.kt` (replaces `test_quests.sh`) - **11 tests, all passing**
  - Quest acceptance
  - Kill objective auto-tracking
  - Collect objective auto-tracking
  - Explore objective auto-tracking
  - Talk objective auto-tracking
  - Skill check objective auto-tracking
  - Deliver objective (documented, pending implementation)
  - Multi-objective quest completion
  - Reward claiming
  - Quest log display
  - Quest abandonment

- ✅ `SkillCheckIntegrationTest.kt` (replaces `test_skill_checks.sh`) - **14 tests, all passing**
  - All 6 stat checks (STR, DEX, CON, INT, WIS, CHA)
  - Difficulty levels (Easy, Medium, Hard)
  - Success/failure outcomes
  - Stat modifier effects
  - Multiple sequential checks

- ✅ `SocialInteractionIntegrationTest.kt` (replaces `test_social.sh`) - **13 tests, all passing**
  - Talk to NPCs (friendly and hostile)
  - Persuasion checks (success/failure)
  - Intimidation checks (success/failure)
  - Difficulty levels (easy vs hard)
  - Cannot persuade/intimidate same NPC twice
  - CHA modifier effects on success rates
  - Multiple NPCs independently

#### Phase 3: Complex Integration - ✅ COMPLETE
Advanced workflow tests in `app/src/test/kotlin/com/jcraw/app/integration/`:

- ✅ `SaveLoadIntegrationTest.kt` (replaces `test_save_load.sh`) - **13 tests, all passing**
  - Save game state to disk
  - Load game state from disk
  - Persistence roundtrip (save → modify → load → verify)
  - Save file format validation (JSON structure)
  - Load after game modifications
  - Non-existent save file handling
  - Custom save names
  - List all saves
  - Delete save files
  - Combat state preservation
  - Room connections preservation

- ✅ `ProceduralDungeonIntegrationTest.kt` (replaces `test_procedural.sh`) - **21 tests, all passing**
  - All 4 dungeon themes (Crypt, Castle, Cave, Temple)
  - Room connectivity (reachability, bidirectional connections, navigation)
  - NPC generation (boss NPCs, hostile NPCs, friendly NPCs, combat and dialogue)
  - Item distribution (weapons, armor, consumables, pickup/use mechanics)
  - Quest generation (all quest types based on dungeon state)
  - Deterministic generation with seeds

- ✅ `NavigationIntegrationTest.kt` (replaces `test_exits_debug.sh`) - **21 tests, all passing**
  - Directional navigation (n/s/e/w, cardinal names, "go" syntax)
  - Natural language navigation ("go to throne room", partial names)
  - Invalid direction handling (invalid directions, non-existent rooms, gibberish)
  - Exit validation (bidirectionality, reachability, hub rooms, dead ends)
  - Procedural dungeon navigation (connectivity, valid exits)

- ✅ `FullGameplayIntegrationTest.kt` (replaces `test_game.sh`) - **15 tests, all passing**
  - Basic game loop (exploration, NPC interaction, inventory)
  - Multi-system integration (navigation + equipment + combat, consumables in combat, loot collection, skill checks)
  - Quest integration (explore, collect, kill quests with auto-tracking)
  - Save/load during gameplay (mid-exploration, active combat, with quests)
  - Complete playthroughs (full dungeon exploration, realistic gameplay session, procedural dungeon)

#### Phase 4: Bot Test Migration - ✅ COMPLETE
Migrate bot tests to `testbot/src/test/kotlin/com/jcraw/mud/testbot/scenarios/`:

- ✅ `BruteForcePlaythroughTest.kt` (replaces `test_brute_force_playthrough.sh`) - **3 tests**
  - Bot completes brute force playthrough by collecting gear and defeating boss
  - Bot explores multiple rooms looking for gear
  - Bot takes damage but survives with equipment
- ✅ `SmartPlaythroughTest.kt` (replaces `test_smart_playthrough.sh`) - **3 tests**
  - Bot completes smart playthrough using social skills and intelligence
  - Bot attempts social interactions before resorting to combat
  - Bot explores secret areas and completes skill checks
- ✅ `BadPlaythroughTest.kt` (replaces `test_bad_playthrough.sh`) - **3 tests**
  - Bot rushes to boss and dies without gear
  - Bot reaches boss room quickly without collecting gear
  - Bot takes fatal damage from boss encounter
- ✅ `AllPlaythroughsTest.kt` (replaces `test_all_playthroughs.sh`) - **3 tests**
  - All three playthrough scenarios complete successfully
  - Game balance validation (bad player dies, good players win)
  - Multiple solution paths exist and are viable

**Files to Create/Modify:**
- See [TESTING.md](docs/TESTING.md) for complete file list and examples
- All new test files use JUnit 5, @Nested classes, mocked LLM clients
- Follow patterns in existing tests (SocialInteractionTest.kt, QuestSystemTest.kt)

**Success Criteria:**
- All shell script functionality migrated to unit/integration tests
- `gradle test` runs entire suite successfully
- Test suite completes in <2 minutes (excluding E2E bot tests)
- Shell scripts can be safely deleted

---

## Feature Backlog

### Deliver Quest Objectives
**Status:** Not implemented
**Description:** Implement DeliverItem quest objective tracking
**Files:**
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/QuestTracker.kt`
- `core/src/main/kotlin/com/jcraw/mud/core/Quest.kt`

---

### Network Layer (Optional)
**Status:** Future enhancement
**Description:** TCP/WebSocket support for remote multi-player
**Files:**
- New `network/` module
- `app/src/main/kotlin/com/jcraw/app/GameServer.kt`

---

### Persistent Vector Storage (Optional)
**Status:** Future enhancement
**Description:** Save/load vector embeddings to disk
**Files:**
- `memory/` module

---

## Testing Protocol

After each bug fix, run relevant test scenarios:

```bash
# Full test suite:
gradle test && ./test_bad_playthrough.sh && ./test_brute_force_playthrough.sh && ./test_smart_playthrough.sh
```

**Success Criteria (All Achieved):** ✅
- bad_playthrough: 100% pass rate ✅
- brute_force_playthrough: 100% pass rate ✅
- smart_playthrough: 100% pass rate ✅

---
