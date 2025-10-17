# TODO List

*Last Updated: 2025-10-15*

## Current Status

**Test Results:**
- ✅ brute_force_playthrough: **100% pass rate (17/17)**
- ✅ bad_playthrough: **100% pass rate (8/8)**
- ✅ smart_playthrough: **100% pass rate (7/7)**
- ✅ **Total Tests: ~298 (across all modules)**
- ✅ **Phase 1 Progress: COMPLETE (82 unit tests)**
- ✅ **Phase 2 Progress: COMPLETE (58 integration tests)**
- ✅ **Phase 3 Progress: COMPLETE (70 integration tests)**
- ✅ **Phase 4 Progress: COMPLETE (12 E2E scenario tests)**
- ✅ **All shell script tests successfully migrated to Kotlin unit/integration tests!**

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

#### Phase 3: Complex Integration - ✅ COMPLETE (2025-10-15)
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

**Progress:** 4/4 tests complete (70 total tests)

#### Phase 4: Bot Test Migration - ✅ COMPLETE (2025-10-15)
Migrate bot tests to `testbot/src/test/kotlin/com/jcraw/mud/testbot/scenarios/`:

- ✅ `BruteForcePlaythroughTest.kt` (replaces `test_brute_force_playthrough.sh`) - **3 tests, all passing**
  - Bot completes brute force playthrough by collecting gear and defeating boss
  - Bot explores multiple rooms looking for gear
  - Bot takes damage but survives with equipment
- ✅ `SmartPlaythroughTest.kt` (replaces `test_smart_playthrough.sh`) - **3 tests, all passing**
  - Bot completes smart playthrough using social skills and intelligence
  - Bot attempts social interactions before resorting to combat
  - Bot explores secret areas and completes skill checks
- ✅ `BadPlaythroughTest.kt` (replaces `test_bad_playthrough.sh`) - **3 tests, all passing**
  - Bot rushes to boss and dies without gear
  - Bot reaches boss room quickly without collecting gear
  - Bot takes fatal damage from boss encounter
- ✅ `AllPlaythroughsTest.kt` (replaces `test_all_playthroughs.sh`) - **3 tests, all passing**
  - All three playthrough scenarios complete successfully
  - Game balance validation (bad player dies, good players win)
  - Multiple solution paths exist and are viable

**Progress:** 4/4 tests complete (12 total tests)

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

## Next Implementation

### 🎯 SOCIAL-001: Advanced Social System (V2)
**Priority:** HIGH (NEXT TO IMPLEMENT)
**Status:** Planning Complete - Ready for Implementation
**Description:** Comprehensive social interaction system with disposition tracking, knowledge management, and dynamic NPC relationships
**Documentation:** [Implementation Plan](docs/requirements/V2/SOCIAL_SYSTEM_IMPLEMENTATION_PLAN.md)
**Estimated Time:** 28-35 hours

**Key Features:**
- **Disposition System** - Numeric NPC attitude tracking (-100 to +100)
- **Knowledge Management** - NPCs with individual knowledge bases and canon generation
- **Emote System** - Non-verbal interactions (bow, wave, laugh, etc.)
- **Event-Driven Mechanics** - Actions affect NPC relationships dynamically
- **Enhanced Dialogue** - LLM responses influenced by disposition and history
- **Quest Integration** - Disposition affects quest hints and rewards

**Implementation Phases:**
1. ✅ Architecture & Planning - COMPLETE
2. ✅ Phase 1: Core Data Models (2-3 hours) - COMPLETE (2025-10-16)
3. ⏳ Phase 2: Database Layer (4-5 hours)
4. ⏳ Phase 3: Core Logic Components (4-5 hours)
5. ⏳ Phase 4: Intent Recognition (2-3 hours)
6. ⏳ Phase 5: NPC Dialogue Enhancement (3-4 hours)
7. ⏳ Phase 6: Game Loop Integration (4-5 hours)
8. ⏳ Phase 7: Procedural Generation Update (2-3 hours)
9. ⏳ Phase 8: Quest System Integration (2 hours)
10. ⏳ Phase 9: Memory System Extension (2-3 hours)
11. ⏳ Phase 10: Documentation (2-3 hours)
12. ⏳ Phase 11: Integration Testing & Polish (3-4 hours)

**Files Created (Phase 1):**
- ✅ `core/src/main/kotlin/com/jcraw/mud/core/Component.kt`
- ✅ `core/src/main/kotlin/com/jcraw/mud/core/SocialComponent.kt`
- ✅ `core/src/main/kotlin/com/jcraw/mud/core/SocialEvent.kt`
- ✅ `core/src/main/kotlin/com/jcraw/mud/core/KnowledgeEntry.kt`
- ✅ `core/src/test/kotlin/com/jcraw/mud/core/ComponentSystemTest.kt` (30 tests)

**Files to Create (Phase 2+):**
- ⏳ `core/src/main/kotlin/com/jcraw/mud/core/repository/Repository.kt` (Phase 2)
- ⏳ `persistence/` module with SQLite repositories (Phase 2)
- ⏳ `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/DispositionManager.kt` (Phase 3)
- ⏳ `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/EmoteHandler.kt` (Phase 3)
- ⏳ `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/NPCKnowledgeManager.kt` (Phase 3)
- ⏳ Integration tests in `app/src/test/kotlin/com/jcraw/app/integration/` (Phase 11)

**Files Modified (Phase 1):**
- ✅ `core/src/main/kotlin/com/jcraw/mud/core/Entity.kt` (added ComponentHost implementation)

**Files to Modify (Phase 2+):**
- ⏳ `perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt` (add Emote, AskQuestion) (Phase 4)
- ⏳ `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/NPCInteractionGenerator.kt` (enhance with disposition) (Phase 5)
- ⏳ `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/procedural/NPCGenerator.kt` (add SocialComponent generation) (Phase 7)
- ⏳ Game loop implementations (App.kt, EngineGameClient.kt, InMemoryGameEngine.kt) (Phase 6)

**Success Criteria:**
- All 21 TODO items complete
- ~50+ new unit tests passing
- ~20+ new integration tests passing
- Full playthrough with social interactions works
- Documentation updated
- Backward compatible with existing save files

---

## Feature Backlog

### Deliver Quest Objectives
**Status:** Not implemented (superseded by SOCIAL-001)
**Description:** Implement DeliverItem quest objective tracking
**Note:** Will be implemented as part of overall system improvements
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

Run the full test suite:

```bash
gradle test
```

**Success Criteria (All Achieved):** ✅
- All 298 tests passing ✅
- Phase 1-4 testing migration complete ✅
- Legacy shell scripts removed ✅

---

## Cleanup Status

### ✅ CLEANUP-001: Remove Legacy Shell Scripts - COMPLETE (2025-10-16)
**Priority:** HIGH (COMPLETED)
**Description:** Delete obsolete shell script tests after successful migration to Kotlin
**Status:** All 13 shell scripts deleted

**Deleted Files:**
- ~~test_combat.sh~~ → CombatIntegrationTest.kt
- ~~test_items.sh~~ → ItemInteractionIntegrationTest.kt
- ~~test_skill_checks.sh~~ → SkillCheckIntegrationTest.kt
- ~~test_social.sh~~ → SocialInteractionIntegrationTest.kt
- ~~test_quests.sh~~ → QuestIntegrationTest.kt
- ~~test_save_load.sh~~ → SaveLoadIntegrationTest.kt
- ~~test_procedural.sh~~ → ProceduralDungeonIntegrationTest.kt
- ~~test_game.sh~~ → FullGameplayIntegrationTest.kt
- ~~test_exits_debug.sh~~ → NavigationIntegrationTest.kt
- ~~test_brute_force_playthrough.sh~~ → BruteForcePlaythroughTest.kt
- ~~test_smart_playthrough.sh~~ → SmartPlaythroughTest.kt
- ~~test_bad_playthrough.sh~~ → BadPlaythroughTest.kt
- ~~test_all_playthroughs.sh~~ → AllPlaythroughsTest.kt

**Outcome:** Codebase now uses only Kotlin tests via `gradle test`

---
