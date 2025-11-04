# TODO

*Last Updated: 2025-11-04*

## Current Status

**All core systems complete!** 🎉

- ✅ 773 tests passing across all modules (0 failures)
- ✅ Console app fully functional
- ✅ GUI client with real engine integration
- ✅ Multi-user game server
- ✅ All refactoring complete
- ✅ Combat System V2 complete (7 phases)
- ✅ Item System V2 complete (10 chunks)

---

## Next Major Feature

### Immediate Follow-up
- ✅ **COMPLETED** - Player inventory integrated with `InventoryComponent`:
  - Added `inventoryComponent` field to PlayerState with V2 helpers (addItemInstance, addGoldV2, etc.)
  - Updated loot handlers to use V2 system (handleLoot, handleLootAll)
  - Updated inventory display to show weight capacity, equipped items, and V2 data
  - Legacy fields remain for backward compatibility during migration

### World Generation System V2
**Status:** ✅ Complete - all tests passing (773 tests, 0 failures)
**Description:** Hierarchical, on-demand procedural world generation for infinite, lore-consistent open worlds
**Implementation Plan:** `docs/requirements/V2/FEATURE_PLAN_world_generation_system.md`

**Progress:**
- ✅ Chunks 1-6 complete (foundation, database, generation, exits, content, persistence)
- ✅ Main code compiles successfully
- ✅ WorldAction serialization fixed (added @SerialName annotations, updated tests)
- ✅ All unit tests passing (773 tests across all modules)
- ✅ Integration tests validated (AllPlaythroughsTest scenarios execute successfully)

**Completed Steps:**
1. ✅ Fix test compilation errors in ItemUseHandlerTest and PickpocketHandlerTest (ItemRepository API mismatch) — introduced shared TestItemRepository stub for Result-based API
2. ✅ Fix WorldAction serialization issues in WorldActionTest (added @SerialName to all subclasses, updated test to use sealed type)
3. ✅ Investigate and fix testbot test failures — Fixed GameplayLogger JSONL serialization, ValidationParsers fallback logic, and InMemoryGameEngine test expectations (all testbot unit tests passing)
4. ✅ Run integration tests to validate complete system — 773 tests passing
5. ✅ Update documentation with test results

### Spatial Coherence & Town Entrance Fix
1. Implement directional adjacency tracking in `WorldChunkRepository` (persist adjacency metadata, finish `findAdjacent`, add unit coverage)
2. Feed `GenerationContext.direction` into `WorldGenerator` prompts so newly linked spaces respect described orientation/verticality
3. Rework `ExitLinker` to consult adjacency: reuse known neighbor IDs, spawn new subzones/zones when taking vertical exits, and collapse duplicate directional exits from LLM output before saving
4. Update `TownGenerator`/`DungeonInitializer` to wire a guaranteed "descend into the dungeon" exit that targets the first combat subzone, plus a reciprocal "return to town" path
5. Switch client movement over to `ExitResolver` for both typed directions and natural language, emitting navigation breadcrumbs when loops close
6. Add integration tests that verify four-step loops return to origin, town→dungeon hand-off works, and hidden exits become traversable once discovered

---

## Code Quality & Refactoring

### File Size Violations (CLAUDE_GUIDELINES.md: 300-500 lines)
**Priority:** High - maintainability and readability at risk

1. **Refactor `GameServer.kt` (822 lines)**
   - Extract session management to `PlayerSessionManager.kt`
   - Extract event broadcasting to `EventBroadcaster.kt`
   - Extract state synchronization to `WorldStateSynchronizer.kt`
   - Keep core server initialization and command dispatch in `GameServer.kt`

2. **Refactor `SkillQuestHandlers.kt` (798 lines)**
   - Split into `SkillHandlers.kt` (skill checks, training, perks)
   - Split into `QuestHandlers.kt` (quest accept, progress, claim)
   - Keep common utilities in `HandlerUtils.kt` if needed

3. **Refactor `MudGameEngine.kt` (632 lines)**
   - Extract combat loop to `CombatEngine.kt`
   - Extract inventory operations to `InventoryManager.kt`
   - Extract social interactions to `SocialInteractionManager.kt`
   - Keep core game loop and intent routing in `MudGameEngine.kt`

4. **Refactor `EngineGameClient.kt` (608 lines)**
   - Extract initialization logic to `EngineInitializer.kt`
   - Extract state management to `ClientStateManager.kt`
   - Extract event handling to `ClientEventDispatcher.kt`
   - Keep GameClient interface implementation in `EngineGameClient.kt`

### Code Duplication Removal
**Priority:** Medium - DRY principle violation

1. **Extract `buildQuestionContext()` to shared utility**
   - Duplicated in `app/src/.../handlers/SocialHandlers.kt` and `client/src/.../handlers/ClientSocialHandlers.kt`
   - Move to `utils/src/main/kotlin/com/jcraw/mud/utils/SocialContextBuilder.kt`
   - Support both Room-based and SpacePropertiesComponent-based contexts
   - Update both console and client handlers to use shared utility

**Key Features:**
- Hierarchical chunks (WORLD > REGION > ZONE > SUBZONE > SPACE)
- LLM-generated lore, themes, descriptions
- Hybrid exits (cardinal + natural language) with skill/item conditions
- Theme-based traps, resources, mobs scaled by difficulty
- State persistence with mob respawns
- V2 Deep Dungeon MVP (100+ floors possible)

---

## Future Enhancements (Optional)

### Network Layer
**Description:** TCP/WebSocket support for remote multi-player
**Status:** Future enhancement
**Files:** New `network/` module, `app/src/main/kotlin/com/jcraw/app/GameServer.kt`

### Persistent Vector Storage
**Description:** Save/load vector embeddings to disk
**Status:** Future enhancement
**Files:** `memory/` module

### Additional Features
- More quest objective types (Escort, Defend, Craft)
- Character progression system (leveling, skill trees)
- More dungeon themes and procedural variations
- Multiplayer lobby system

---

## Testing Protocol

Run the full test suite:
```bash
gradle test
```

Run comprehensive bot tests:
```bash
gradle :testbot:test --tests "com.jcraw.mud.testbot.scenarios.AllPlaythroughsTest"
```

**Success Criteria:** All tests passing ✅
