# V2 Removal Plan

**Status**: Phase 1-2 Complete (Core WorldState cleanup + Console Handlers)
**Estimated Effort**: 8-12 hours (4-6h spent)
**Priority**: CRITICAL - Violates project guideline "no backward compatibility needed"

## Problem Statement

Current codebase has V3 (graph-based navigation) with V2 (room-based) fallback code throughout. This violates the project guideline that states "no backward compatibility needed - can wipe and restart data between versions."

## Scope

**Total**: ~177 V2 references across 42 files

### Breakdown by Area

1. **Console Handlers** (~49 occurrences) - Have V3 with V2 fallback
   - MovementHandlers.kt (4 V2 references)
   - ItemHandlers.kt (6 V2 references)
   - CombatHandlers.kt (5 V2 references)
   - SocialHandlers.kt (11 V2 references)
   - SkillQuestHandlers.kt (6 V2 references)
   - TradeHandlers.kt, PickpocketHandlers.kt (additional references)

2. **GUI Client** (~18 occurrences) - Currently pure V2, needs V3-only migration
   - ClientMovementHandlers.kt (4 V2 references)
   - ClientItemHandlers.kt (4 V2 references)
   - ClientCombatHandlers.kt (1 V2 reference)
   - ClientSocialHandlers.kt (4 V2 references)
   - ClientSkillQuestHandlers.kt (1 V2 reference)
   - ClientTradeHandlers.kt (1 V2 reference)
   - EngineGameClient.kt (3 V2 references)

3. **Infrastructure** (~8 occurrences)
   - GameServer.kt (5 V2 references)
   - MultiUserGame.kt (3 V2 references)

4. **Core WorldState** - V2 methods marked @Deprecated
   - `rooms` field (line 23)
   - `getCurrentRoom()` methods (lines 35-40)
   - `getRoom()` (line 44)
   - `updateRoom()` (line 46)
   - `movePlayer()` V2 methods (lines 65-86)
   - `addEntityToRoom()` (line 88-92)
   - `removeEntityFromRoom()` (lines 94-98)
   - `replaceEntity()` (lines 100-104)
   - `getAvailableExits()` V2 methods (lines 106-109)
   - `getPlayersInRoom()` (lines 111-112)

5. **Tests** (~64 occurrences) - Need updating to V3 API
   - Integration tests
   - Unit tests
   - WorldStateTest.kt

6. **Dependencies**
   - SceneryDescriptionGenerator - needs V3 version (currently takes Room)
   - Various other services may need adaptation

## Migration Strategy

### Phase 1: Core WorldState (Est. 1-2h) ✅ COMPLETE

**Objective**: Make WorldState V3-only

**Completed Steps**:
1. ✅ Removed all deprecated V2 methods from WorldState.kt:
   - ✅ Removed `getCurrentRoom()` (both variants)
   - ✅ Removed `getRoom()`
   - ✅ Removed `updateRoom()`
   - ✅ Removed `movePlayer()` V2 variants (both overloads)
   - ✅ Removed `addEntityToRoom()`
   - ✅ Removed `removeEntityFromRoom()`
   - ✅ Removed `replaceEntity()`
   - ✅ Removed `getAvailableExits()` V2 variants (both overloads)
   - ✅ Removed `getPlayersInRoom()`

2. ✅ Removed the `rooms` field from data class

3. ✅ Removed deprecated V3 helper methods (`addEntityToSpaceV3`, `removeEntityFromSpaceV3`) that had better V3 replacements

**Result**: WorldState is now V3-only. File reduced from 336 lines to 272 lines.

**Expected Breakage**: Build fails at core module (SampleDungeon.kt) - intentional. Forces migration in subsequent phases.

### Phase 2: Console Handlers (Est. 3-4h) ✅ COMPLETE

**Objective**: Remove V2 fallback code from console handlers

**Completed Steps**:
1. ✅ MovementHandlers.kt - 4 V2 references removed:
   - Removed V3/V2 fallback in `handleMove()` - now uses `movePlayerV3()` only
   - Updated quest tracking to use space IDs
   - Updated `handleLook()` to use V3 entity system
   - Updated `handleSearch()` to use V3 entity storage

2. ✅ ItemHandlers.kt - 35 V2 references removed:
   - `handleTake()` - V3-only entity storage
   - `handleTakeAll()` - V3-only entity storage
   - `handleDrop()` - V3-only entity storage
   - `handleGive()` - V3-only entity storage
   - `handleLoot()` - V3-only entity storage
   - `handleLootAll()` - V3-only entity storage

3. ✅ CombatHandlers.kt - 5 V2 references removed:
   - Removed V2 room-based entity access
   - All combat now uses V3 space + entity methods

4. ✅ SocialHandlers.kt - 11 V2 references removed:
   - `handleTalk()` - V3-only NPC lookup
   - `handleEmote()` - V3-only entity storage
   - `handleAskQuestion()` - V3-only entity storage
   - `handlePersuade()` - V3-only entity storage
   - `handleIntimidate()` - V3-only entity storage
   - `resolveNpcTarget()` - V3-only helper
   - `buildQuestionContext()` - V3-only (spaces don't have traits)

5. ✅ SkillQuestHandlers.kt - 6 V2 references removed:
   - `handleInteract()` - V3-only entity storage
   - `handleCheck()` - V3-only entity storage
   - `handleTrainSkill()` - V3-only entity storage

6. ✅ TradeHandlers.kt - 2 V2 references removed:
   - `handleTrade()` - Updated to V3 comments (stub function)
   - `handleListStock()` - Updated to V3 comments (stub function)

7. ✅ PickpocketHandlers.kt - 1 V2 reference removed:
   - `handlePickpocket()` - Now uses `getEntitiesInSpace()`

**Result**: Console handlers are now V3-only. All 7 handler files successfully migrated (64 V2 references removed).

**Note**: Build broken - reasoning module has extensive V2 dependencies (see Phase 2a below)

### Phase 2a: Reasoning Module V2 Cleanup (NEW - Est. 3-4h)

**Objective**: Remove V2 dependencies from reasoning module

**Problem**: Console handlers migrated successfully, but build now broken because reasoning module has extensive V2 method usage:

**Files Needing Updates** (~20+ files):
- `CombatNarrator.kt` - Uses `getCurrentRoom()`
- `CombatResolver.kt` - Uses `getCurrentRoom()`
- `QuestTracker.kt` - Uses `world.rooms` and `updateRoom()`
- `AttackResolver.kt` - Uses `world.rooms`
- `CombatBehavior.kt` - Uses `getRoom()` and `updateRoom()`
- `CombatInitiator.kt` - Uses `getRoom()`
- `QuestGenerator.kt` - Uses `getRoom()`
- Additional files in reasoning module need analysis

**Recommended Approach**:
1. Audit all reasoning module files for V2 method usage
2. Create V3 adapter methods where needed (e.g., Room → SpacePropertiesComponent)
3. Update service constructors to accept V3 dependencies
4. Test reasoning module in isolation

**Priority**: BLOCKING - Must complete before Phase 3 (GUI Client)

### Phase 3: GUI Client (Est. 2-3h)

**Objective**: Migrate GUI client to V3-only

The GUI client is currently pure V2. Need to fully migrate to V3 graph-based navigation.

**Files**:
1. EngineGameClient.kt - Update core game client logic
2. ClientMovementHandlers.kt - Use movePlayerV3 exclusively
3. ClientItemHandlers.kt - Use V3 entity system
4. ClientCombatHandlers.kt - Use V3 space references
5. ClientSocialHandlers.kt - Use V3 for NPC interactions
6. ClientSkillQuestHandlers.kt - Use V3 quest tracking
7. ClientTradeHandlers.kt - Use V3 entity lookups

### Phase 4: Infrastructure (Est. 1h)

**Objective**: Update GameServer and MultiUserGame to V3-only

**Files**:
1. GameServer.kt - Remove 5 V2 references
2. MultiUserGame.kt - Remove 3 V2 references

### Phase 5: Tests (Est. 2-3h)

**Objective**: Fix all broken tests

**Files**:
- Update ~64 test occurrences to use V3 API
- Fix WorldStateTest.kt
- Update integration tests
- Verify all tests pass

### Phase 6: Dependencies (Est. 1h)

**Objective**: Update dependent services

**Files**:
- SceneryDescriptionGenerator - Create V3 version that takes SpacePropertiesComponent
- Review other services for V2 dependencies

### Phase 7: Documentation (Est. 30min)

**Objective**: Update all docs to reflect V3-only architecture

**Files**:
- Update CLAUDE.md
- Update TODO.md
- Update ARCHITECTURE.md
- Remove V2-specific documentation

## Testing Strategy

After each phase:
1. Run `gradle build` to check compilation
2. Run test suite: `gradle test`
3. Manual smoke test:
   - Start console app
   - Generate V3 world
   - Test movement, combat, items, social interactions
   - Test GUI client
   - Test multi-user mode

## Rollback Plan

None needed - per project guidelines, we can wipe and restart data between versions. If migration fails, simply revert commits.

## Success Criteria

- [ ] No @Deprecated annotations in codebase
- [ ] No V2 fallback code patterns
- [ ] No `rooms` field in WorldState
- [ ] All tests passing
- [ ] Console app works with V3 only
- [ ] GUI client works with V3 only
- [ ] Multi-user mode works with V3 only
- [ ] Build succeeds with no warnings about deprecated API usage

## Risks

1. **Large scope** - 177 references across 42 files is significant
2. **Testing coverage** - Reasoning module already has 4 broken test files
3. **Hidden dependencies** - May discover additional V2 dependencies during migration

## Mitigation

1. Work incrementally by phase
2. Commit after each phase
3. Test thoroughly between phases
4. Use compiler errors as guide for finding all references

## Notes

- V3 is fully functional - all game systems work with graph-based navigation
- V2 code only exists for backward compatibility (which we don't need)
- This is purely a cleanup task, not adding new functionality
- Estimated 8-12h includes all phases plus buffer for unexpected issues
