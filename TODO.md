# TODO

*Last Updated: 2025-10-28*

## Current Status

**All core systems complete!** 🎉

- ✅ ~751 tests passing across all modules
- ✅ Console app fully functional
- ✅ GUI client with real engine integration
- ✅ Multi-user game server
- ✅ All refactoring complete
- ✅ Combat System V2 complete (7 phases)
- ✅ Item System V2 complete (10 chunks)

---

## Next Major Feature

### Immediate Follow-up
- Integrate the player-facing inventory with `InventoryComponent` and update trading/loot handlers to use V2 items before expanding merchant features.

### World Generation System V2
**Status:** Partially complete - test fixes needed
**Description:** Hierarchical, on-demand procedural world generation for infinite, lore-consistent open worlds
**Implementation Plan:** `docs/requirements/V2/FEATURE_PLAN_world_generation_system.md`

**Progress:**
- ✅ Chunks 1-6 complete (foundation, database, generation, exits, content, persistence)
- ✅ Main code compiles successfully
- ❌ Test compilation errors (ItemRepository API changes, WorldGenerationIntegrationTest needs fixes)

**Next Steps:**
1. ✅ Fix test compilation errors in ItemUseHandlerTest and PickpocketHandlerTest (ItemRepository API mismatch) — introduced shared TestItemRepository stub for Result-based API
2. Fix WorldGenerationIntegrationTest nullable type issues
3. Run integration tests to validate complete system
4. Update CLAUDE.md status when tests pass

### Spatial Coherence & Town Entrance Fix
1. Implement directional adjacency tracking in `WorldChunkRepository` (persist adjacency metadata, finish `findAdjacent`, add unit coverage)
2. Feed `GenerationContext.direction` into `WorldGenerator` prompts so newly linked spaces respect described orientation/verticality
3. Rework `ExitLinker` to consult adjacency: reuse known neighbor IDs, spawn new subzones/zones when taking vertical exits, and collapse duplicate directional exits from LLM output before saving
4. Update `TownGenerator`/`DungeonInitializer` to wire a guaranteed “descend into the dungeon” exit that targets the first combat subzone, plus a reciprocal “return to town” path
5. Switch client movement over to `ExitResolver` for both typed directions and natural language, emitting navigation breadcrumbs when loops close
6. Add integration tests that verify four-step loops return to origin, town→dungeon hand-off works, and hidden exits become traversable once discovered

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
