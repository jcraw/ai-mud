# V2 Removal Plan

**Status**: Not Started
**Estimated Effort**: 8-12 hours
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

### Phase 1: Core WorldState (Est. 1-2h)

**Objective**: Make WorldState V3-only

**Steps**:
1. Remove deprecated V2 methods from WorldState.kt:
   - Remove `getCurrentRoom()` (lines 35-40)
   - Remove `getRoom()` (line 44)
   - Remove `updateRoom()` (line 46)
   - Remove `movePlayer()` V2 variants (lines 65-86)
   - Remove `addEntityToRoom()` (lines 88-92)
   - Remove `removeEntityFromRoom()` (lines 94-98)
   - Remove `replaceEntity()` (lines 100-104)
   - Remove `getAvailableExits()` V2 variants (lines 106-109)
   - Remove `getPlayersInRoom()` (lines 111-112)

2. Remove the `rooms` field (line 23)

3. Update V3 methods to be the primary API (remove "V3" suffix if desired)

**Expected Breakage**: This will break compilation in ~40 files. This is intentional - it forces migration.

### Phase 2: Console Handlers (Est. 3-4h)

**Objective**: Remove V2 fallback code from console handlers

For each handler file, remove V2 fallback patterns:

**Pattern to Remove**:
```kotlin
// BEFORE (V3 with V2 fallback)
val currentGraphNode = game.worldState.getCurrentGraphNode()
val newState = if (currentGraphNode != null) {
    game.worldState.movePlayerV3(direction)
} else {
    game.worldState.movePlayer(direction)  // REMOVE THIS
}

// AFTER (V3-only)
val newState = game.worldState.movePlayerV3(direction)
```

**Files to Update**:
1. MovementHandlers.kt
   - Line 20-27: Remove V2 fallback in `handleMove()`
   - Line 143-146: Update quest tracking to use V3 spaces
   - Line 159-165: Update `handleLook()` to use V3 entity system
   - Line 233-253: Update `handleSearch()` to use V3 only

2. ItemHandlers.kt
   - Remove all `getCurrentRoom()` calls
   - Replace with `getCurrentSpace()` + `getEntitiesInSpace()`

3. CombatHandlers.kt
   - Remove V2 room-based entity access
   - Use V3 space + entity methods

4. SocialHandlers.kt
   - Remove V2 room references
   - Use V3 space for NPC lookups

5. SkillQuestHandlers.kt
   - Update quest tracking to use space IDs instead of room IDs
   - Use V3 entity methods

6. TradeHandlers.kt, PickpocketHandlers.kt
   - Remove V2 fallbacks
   - Use V3-only APIs

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
