# World System V3 Migration Status

**Last Updated**: 2025-11-05
**Status**: WorldState foundation complete, handler integration pending

## Overview

World System V3 replaces V2's Room-based model with full ECS (Entity Component System) architecture using GraphNodeComponent and SpacePropertiesComponent. This is a **full replacement**, not parallel systems - V2 will be deprecated and removed.

## What's Complete ✅

### 1. WorldState V3 Refactoring (`core/WorldState.kt` - 248 lines)

**New ECS Storage**:
```kotlin
val graphNodes: Map<SpaceId, GraphNodeComponent>  // Graph topology
val spaces: Map<SpaceId, SpacePropertiesComponent>  // Space content
```

**12 New V3 Methods**:
- `getCurrentSpace()` / `getCurrentGraphNode()` - Component-based location queries
- `getSpace()` / `getGraphNode()` - Direct component access by ID
- `updateSpace()` / `updateGraphNode()` - Immutable component updates
- `addSpace()` - Combined node + space addition
- `movePlayerV3()` - Graph-based navigation using GraphNodeComponent edges
- `getAvailableExitsV3()` - Perception-aware exit listing (filters hidden exits)
- `addEntityToSpaceV3()` / `removeEntityFromSpaceV3()` - Entity management

**Migration Strategy**:
- V2 `rooms` field marked as `@Deprecated`
- V2 methods (getCurrentRoom, movePlayer, etc.) remain temporarily for gradual migration
- Core module compiles successfully with deprecation warnings

### 2. Graph Generation Pipeline (Complete)

- ✅ GraphNodeComponent (155 lines, 29 tests)
- ✅ GraphGenerator with Grid/BSP/FloodFill layouts (31 tests)
- ✅ GraphValidator with connectivity/loop/degree checks (20 tests)
- ✅ WorldGenerator with lazy-fill content system (567 lines)
- ✅ Database schema with graph_nodes table

### 3. Documentation

- ✅ TODO.md updated with V3 status and remaining work breakdown
- ✅ CLAUDE.md updated with WorldState V3 refactoring status
- ✅ Migration strategy documented

## What's Pending ❌

### Phase 1: Movement Handlers (~3-4h)
**Files**: `app/handlers/MovementHandlers.kt`, `client/handlers/ClientMovementHandlers.kt`

**Tasks**:
1. Replace `getCurrentRoom()` calls with `getCurrentSpace()`
2. Replace `movePlayer()` calls with `movePlayerV3()`
3. Replace `getAvailableExits()` with `getAvailableExitsV3()`
4. Implement lazy-fill: Call `WorldGenerator.fillSpaceContent()` when space.description is empty
5. Implement frontier traversal: Generate new chunk when frontier node entered
6. Update exit resolution to use GraphNodeComponent edges

**Example**:
```kotlin
// V2 (deprecated)
val room = state.getCurrentRoom() ?: return
val newState = state.movePlayer(direction) ?: return null

// V3 (new)
val space = state.getCurrentSpace() ?: return
val node = state.getCurrentGraphNode() ?: return
val newState = state.movePlayerV3(direction) ?: return null

// Lazy-fill check
if (space.description.isEmpty()) {
    val filled = worldGenerator.fillSpaceContent(space, node, chunk)
    // Update state with filled space
}
```

### Phase 2: Item/Combat/Social Handlers (~4-5h)
**Files**: `app/handlers/{ItemHandlers.kt, CombatHandlers.kt, SocialHandlers.kt, SkillQuestHandlers.kt}`

**Tasks**:
1. Replace `getCurrentRoom()` with `getCurrentSpace()`
2. Replace `updateRoom()` with `updateSpace()`
3. Replace `addEntityToRoom()` with `addEntityToSpaceV3()`
4. Update entity queries to work with SpacePropertiesComponent.entities (IDs) instead of Room.entities (objects)
5. Update all handler logic to use V3 methods

**Challenge**: Entities in V3 are stored as IDs in SpacePropertiesComponent, need entity repository lookups

### Phase 3: Game Loop (~2-3h)
**Files**: `app/MudGameEngine.kt`, `app/MultiUserGame.kt`, `app/App.kt`

**Tasks**:
1. Update world initialization to create V3 WorldState with empty rooms map
2. Use V3 generation pipeline (generateChunk with graphGenerator/graphValidator)
3. Spawn player in first generated space (not room)
4. Update game loop to handle lazy-fill on movement
5. Update save/load to persist graphNodes + spaces

### Phase 4: GUI Client (~2-3h)
**Files**: `client/EngineGameClient.kt`, `client/handlers/*.kt`

**Tasks**:
1. Update all client handlers to use V3 methods
2. Update character creation to spawn in V3-generated world
3. Test full gameplay through GUI

### Phase 5: Cleanup (~1h)
**Tasks**:
1. Remove `@Deprecated rooms` field from WorldState
2. Remove V2 methods (getCurrentRoom, movePlayer, getAvailableExits, etc.)
3. Remove GraphToRoomAdapter.kt (no longer needed)
4. Update all tests to use V3 methods
5. Remove V2 sample dungeon (SampleDungeon.kt) if not migrated

### Phase 6: Integration Tests (~2h)
**Tests**:
1. End-to-end: Generate chunk with graph → navigate spaces → verify lazy-fill
2. Frontier cascade: Enter frontier node → verify new chunk generated
3. Save/load: Verify graphNodes + spaces persist correctly
4. Multi-user: Verify V3 works with concurrent players
5. Combat/items: Verify all systems work with V3 components

## Key Design Decisions

### Why Full ECS Replacement?
- **No backward compatibility needed** - Database can be wiped
- **No data migrations** - Clean slate for V3
- **Cleaner architecture** - Room was V2-specific abstraction
- **Direct component access** - Handlers work with ECS components directly

### Why Not Keep Room?
- Room is tightly coupled to V2 generation (LLM exits, full content upfront)
- V3 uses graph-first topology with lazy-fill content
- Maintaining parallel abstractions violates KISS principle
- Component-based model is more flexible and extensible

### Why Incremental Migration?
- Reduces risk - compile errors caught early
- Allows testing at each phase
- Handlers can be migrated one at a time
- Game remains playable (with V2) during transition

## Testing Strategy

1. **Unit tests**: Update WorldState tests to use V3 methods
2. **Handler tests**: Mock V3 components, verify behavior
3. **Integration tests**: Full game loop with V3 generation
4. **Regression tests**: Verify all existing features work (combat, items, skills, quests)

## Estimated Timeline

- **Phase 1** (Movement): 3-4 hours
- **Phase 2** (Other handlers): 4-5 hours
- **Phase 3** (Game loop): 2-3 hours
- **Phase 4** (GUI client): 2-3 hours
- **Phase 5** (Cleanup): 1 hour
- **Phase 6** (Testing): 2 hours

**Total**: ~12-15 hours

## Next Steps

1. Start with Movement handlers (Phase 1)
2. Test movement and lazy-fill thoroughly before proceeding
3. Migrate remaining handlers (Phase 2)
4. Update game loop and clients (Phase 3-4)
5. Clean up V2 code and add integration tests (Phase 5-6)

## Notes

- **WorldState compiles** with V3 changes (deprecation warnings expected)
- **V2 methods remain** until all handlers migrated
- **GraphToRoomAdapter** created but not needed for full ECS (can be deleted in Phase 5)
- **Database migrations**: Not needed, wipe and regenerate with V3
- **Save compatibility**: V3 saves incompatible with V2 (expected, documented)
