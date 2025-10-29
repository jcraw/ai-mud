# World Generation System - Implementation Plan

## Overview

This plan implements a hierarchical, on-demand procedural world generation system for infinite, lore-consistent open worlds. The V2 MVP focuses on a deep dungeon (top-to-bottom progression with mob respawns), integrating with existing skills, combat, items, and social systems.

**Total Estimated Time:** 29 hours (7 chunks × 3-5 hours each)

---

## Chain-of-Thought Analysis

### Architecture Alignment
- **ECS Extension**: New components (WORLD_CHUNK, SPACE_PROPERTIES) extend existing Component system
- **Modular**: Follows existing module separation (core→data, perception→intents, reasoning→logic, memory→lore, persistence→DB)
- **Immutable**: Data classes with copy methods for state transitions
- **Async**: Coroutine-based generation for non-blocking world creation
- **RAG Integration**: Vector DB for lore coherence and semantic queries

### Key Dependencies
1. **Component System** (core): Extends ComponentType enum, Component interface
2. **Skills System**: Exit conditions (Perception for hidden, Agility for climbing)
3. **Combat System**: Mob spawning via difficulty levels, terrain modifiers
4. **Items System**: Resource nodes (gathering), corpse persistence
5. **Social System**: NPC placement with lore-based politics/disposition

### Critical Design Decisions
1. **Hierarchy Depth**: 5 levels (WORLD→REGION→ZONE→SUBZONE→SPACE) balances granularity vs complexity
   - *Reasoning*: SPACE is atomic (room-like), SUBZONE clusters 5-100 spaces, higher levels provide lore/theme inheritance
2. **Lazy Generation**: Generate chunks on-demand when player approaches boundary
   - *Reasoning*: Infinite world impossible to pre-generate; lazy approach scales indefinitely
3. **Hybrid Exit Resolution**: Combine exact matching + LLM parsing
   - *Reasoning*: Cardinal directions (n/s/e/w) need fast exact match; natural language ("climb ladder") needs LLM flexibility
4. **Description Caching**: LLM-generated descriptions cached until state flags change
   - *Reasoning*: Balance rich narrative (LLM quality) with performance (API cost/latency)
5. **Mob Respawn on Restart**: Regenerate entity lists from theme/density, not from DB state
   - *Reasoning*: Murder-hobo gameplay viable; dungeon repopulates for replayability

### Challenges & Mitigations
- **Challenge**: LLM generation latency during exploration
  - *Mitigation*: Async generation with "generating..." message; pre-gen adjacent chunks in background
- **Challenge**: Exit ambiguity ("go up" could mean stairs/ladder/climb)
  - *Mitigation*: LLM fallback with context; suggest visible exits on failure
- **Challenge**: State flag explosion (too many flags → complex state)
  - *Mitigation*: Keep flags minimal (boolean map); only critical changes (e.g., "boulder_moved")
- **Challenge**: Respawn logic must preserve player changes (flags/corpses) but reset mobs
  - *Mitigation*: Separate entity regeneration from state persistence; DB stores flags/items, not mob IDs

---

## TODO: Implementation Chunks

### Chunk 1: Foundation - Components & Data Model (4 hours)

**Description**: Implement core data structures for hierarchical world representation.

**Affected Modules**:
- `core/src/main/kotlin/com/jcraw/mud/core/component/`
- `core/src/main/kotlin/com/jcraw/mud/core/world/`

**Files to Create/Modify**:
1. **ComponentType.kt** (core:30)
   - Add `WORLD_CHUNK` and `SPACE_PROPERTIES` to enum
   - *Reasoning*: Extends existing ECS pattern; minimal change to core enum

2. **ChunkLevel.kt** (core:25)
   - Enum: `WORLD, REGION, ZONE, SUBZONE, SPACE`
   - `depth: Int` property (WORLD=0, SPACE=4)
   - *Reasoning*: Type-safe hierarchy levels; depth property enables validation (e.g., SPACE can't have children)

3. **TerrainType.kt** (core:30)
   - Enum: `NORMAL, DIFFICULT, IMPASSABLE`
   - Properties: `timeCostMultiplier: Double`, `damageRisk: Int`
   - *Reasoning*: Encapsulates terrain mechanics; easier to add new types (e.g., WATER, LAVA) in V3

4. **ExitData.kt** (core:45)
   - Data class: `targetId: String, conditions: List<Condition>, description: String`
   - `Condition` sealed class: `SkillCheck(skill, difficulty)`, `ItemRequired(itemTag)`
   - `meetsConditions(player: PlayerState): Boolean` method
   - *Reasoning*: Flexible exit requirements; sealed class allows type-safe pattern matching

5. **TrapData.kt** (core:35)
   - Data class: `id: String, type: String, difficulty: Int, triggered: Boolean`
   - `roll(): TrapResult` method (skill check)
   - *Reasoning*: Encapsulates trap logic; triggered flag persists state

6. **ResourceNode.kt** (core:40)
   - Data class: `id: String, templateId: String, quantity: Int, respawnTime: Int?`
   - Ties to ItemTemplate (existing items system)
   - *Reasoning*: Reuses item system; respawnTime enables renewable resources

7. **WorldChunkComponent.kt** (core:120)
   - Implements `Component` interface
   - Fields: `level, parentId, children, lore, biomeTheme, sizeEstimate, mobDensity, difficultyLevel`
   - Methods:
     - `inheritFromParent(parentLore: String, llmService: LLMService): WorldChunkComponent` - LLM-modify lore/theme
     - `addChild(childId: String): WorldChunkComponent` - Immutable update
     - `validate(): Boolean` - Check hierarchy rules (e.g., SPACE has no children)
   - *Reasoning*: LLM integration at component level; validation prevents invalid hierarchies

8. **SpacePropertiesComponent.kt** (core:180)
   - Implements `Component` interface
   - Fields: `description, exits, brightness, terrainType, traps, resources, entities, itemsDropped, stateFlags`
   - Methods:
     - `updateDescription(llmService: LLMService, lore: String): SpacePropertiesComponent` - Regen if flags changed
     - `resolveExit(intent: String): String?` - Hybrid exact/fuzzy match
     - `applyChange(flag: String, value: Boolean): SpacePropertiesComponent` - Update state
     - `addExit/removeExit/updateTrap/addResource` - Immutable updates
   - *Reasoning*: Self-contained space logic; description regen tied to state changes

**Key Technical Decisions**:
- **Immutability**: All methods return new copies (align with existing codebase style)
- **LLM Integration**: Methods accept `LLMService` parameter (inject at call site; no tight coupling)
- **Validation**: Component-level validation catches errors early (e.g., SPACE with children)
- **Sealed Classes**: `Condition` as sealed class enables exhaustive when statements

**Testing Approach** (`core:test`):
1. **ComponentTypeTest.kt** (5 tests)
   - Enum includes new types
   - No duplicate values

2. **ChunkLevelTest.kt** (8 tests)
   - Depth ordering (WORLD=0, SPACE=4)
   - Hierarchy validation (parent depth < child depth)

3. **TerrainTypeTest.kt** (6 tests)
   - Time cost multipliers (NORMAL=1.0, DIFFICULT=2.0, IMPASSABLE=0)
   - Damage risk calculations

4. **ExitDataTest.kt** (12 tests)
   - `meetsConditions()` with various player states
   - SkillCheck success/failure
   - ItemRequired validation
   - Multiple conditions (all must pass)

5. **TrapDataTest.kt** (10 tests)
   - `roll()` with various difficulties
   - Triggered flag persistence
   - Trap types (pit, poison, arrow)

6. **ResourceNodeTest.kt** (8 tests)
   - Quantity depletion
   - Respawn logic
   - Integration with ItemTemplate

7. **WorldChunkComponentTest.kt** (20 tests)
   - `inheritFromParent()` modifies lore (mock LLM)
   - `addChild()` immutability
   - `validate()` hierarchy rules (SPACE no children, parentId consistency)
   - Difficulty scaling (deeper = higher)

8. **SpacePropertiesComponentTest.kt** (25 tests)
   - `updateDescription()` triggers on flag change (mock LLM)
   - `resolveExit()` exact match (cardinal) vs fuzzy (natural language)
   - `applyChange()` updates flags and invalidates cache
   - Exit/trap/resource CRUD operations
   - Entity list management

**Total Tests**: ~94 tests (focus on contracts: validation, immutability, integration points)

**Documentation Updates**:
- None yet (comprehensive update in Chunk 7)

---

### Chunk 2: Database Schema & Repositories (4 hours)

**Description**: Implement persistence layer for world chunks and space properties.

**Affected Modules**:
- `memory/src/main/kotlin/com/jcraw/mud/memory/world/`
- `core/src/main/kotlin/com/jcraw/mud/core/repository/`

**Files to Create/Modify**:
1. **WorldDatabase.kt** (memory:150)
   - Extends existing database setup (similar to ItemDatabase, CombatDatabase)
   - Tables:
     ```sql
     CREATE TABLE world_seed (
       id INTEGER PRIMARY KEY,
       seed_string TEXT NOT NULL,
       global_lore TEXT NOT NULL
     );

     CREATE TABLE world_chunks (
       id TEXT PRIMARY KEY,
       level TEXT NOT NULL,
       parent_id TEXT,
       children TEXT NOT NULL, -- JSON array
       lore TEXT NOT NULL,
       biome_theme TEXT NOT NULL,
       size_estimate INTEGER NOT NULL,
       mob_density REAL NOT NULL,
       difficulty_level INTEGER NOT NULL,
       FOREIGN KEY (parent_id) REFERENCES world_chunks(id)
     );
     CREATE INDEX idx_chunks_parent ON world_chunks(parent_id);

     CREATE TABLE space_properties (
       chunk_id TEXT PRIMARY KEY,
       description TEXT NOT NULL,
       exits TEXT NOT NULL, -- JSON map
       brightness INTEGER NOT NULL,
       terrain_type TEXT NOT NULL,
       traps TEXT NOT NULL, -- JSON array
       resources TEXT NOT NULL, -- JSON array
       entities TEXT NOT NULL, -- JSON array
       items_dropped TEXT NOT NULL, -- JSON array
       state_flags TEXT NOT NULL, -- JSON map
       FOREIGN KEY (chunk_id) REFERENCES world_chunks(id)
     );
     CREATE INDEX idx_space_chunk ON space_properties(chunk_id);
     ```
   - `clearAll()` method for wiping world (no backward compatibility needed)
   - *Reasoning*: Follows existing DB pattern; JSON for flexible nested data; foreign keys enforce integrity

2. **WorldChunkRepository.kt** (core/repository:80)
   - Interface with CRUD operations:
     - `save(chunk: WorldChunkComponent, id: String): Result<Unit>`
     - `findById(id: String): Result<WorldChunkComponent?>`
     - `findByParent(parentId: String): Result<List<WorldChunkComponent>>`
     - `findAdjacent(currentId: String, direction: String): Result<WorldChunkComponent?>`
     - `delete(id: String): Result<Unit>`
     - `getAll(): Result<List<WorldChunkComponent>>`
   - *Reasoning*: `findAdjacent()` enables boundary detection; Result type for error handling

3. **SQLiteWorldChunkRepository.kt** (memory:280)
   - Implements `WorldChunkRepository`
   - JSON serialization: `Json.encodeToString(children)`, `Json.decodeFromString<List<String>>()`
   - `findAdjacent()` logic:
     - Query parent's children
     - LLM-based spatial reasoning: "Given chunks [list], which is [direction] of [currentId]?"
     - Cache results in adjacency map (avoid repeated LLM calls)
   - *Reasoning*: LLM fallback for spatial queries balances flexibility with performance

4. **SpacePropertiesRepository.kt** (core/repository:60)
   - Interface with operations:
     - `save(properties: SpacePropertiesComponent, chunkId: String): Result<Unit>`
     - `findByChunkId(chunkId: String): Result<SpacePropertiesComponent?>`
     - `updateDescription(chunkId: String, desc: String): Result<Unit>` - Optimized single field
     - `updateFlags(chunkId: String, flags: Map<String, Boolean>): Result<Unit>`
     - `addItems(chunkId: String, items: List<ItemInstance>): Result<Unit>`
     - `delete(chunkId: String): Result<Unit>`
   - *Reasoning*: Granular updates reduce write overhead (don't rewrite entire component for flag change)

5. **SQLiteSpacePropertiesRepository.kt** (memory:320)
   - Implements `SpacePropertiesRepository`
   - JSON serialization for complex fields (exits, traps, resources, entities, itemsDropped, stateFlags)
   - Type conversions: `TerrainType.valueOf(rs.getString("terrain_type"))`
   - Optimistic locking: Add `version` column for concurrent updates (V3 multiplayer prep)
   - *Reasoning*: JSON flexibility for nested data; optimistic locking prevents lost updates

6. **WorldSeedRepository.kt** (core/repository:30)
   - Interface:
     - `save(seed: String, globalLore: String): Result<Unit>`
     - `get(): Result<Pair<String, String>?>` - Returns (seed, globalLore) or null
   - *Reasoning*: Single global seed; simple interface

7. **SQLiteWorldSeedRepository.kt** (memory:80)
   - Implements `WorldSeedRepository`
   - Ensures single row (id=1)
   - *Reasoning*: Singleton pattern at DB level

**Key Technical Decisions**:
- **JSON Serialization**: Use kotlinx.serialization (already in project) for nested data
  - *Reasoning*: Type-safe, compile-time checked; easier than manual parsing
- **Foreign Keys**: Enforce referential integrity (prevent orphaned chunks)
  - *Reasoning*: Catches bugs early; DB handles cascade deletes
- **Indexes**: On parent_id and chunk_id for fast queries
  - *Reasoning*: Traversal queries (findByParent) are frequent
- **Adjacency Caching**: Store LLM-computed spatial relationships in-memory map
  - *Reasoning*: LLM calls expensive; spatial relationships stable after initial resolution

**Testing Approach** (`memory:test`):
1. **WorldDatabaseTest.kt** (15 tests)
   - Table creation/schema validation
   - Foreign key constraints (can't delete parent with children)
   - Index existence
   - clearAll() wipes all tables

2. **SQLiteWorldChunkRepositoryTest.kt** (25 tests)
   - Save/load roundtrip with all fields
   - JSON serialization (children list)
   - findByParent() returns children
   - findAdjacent() with mock LLM (test spatial reasoning prompt)
   - Null parent handling (WORLD level)
   - Error cases (nonexistent IDs, duplicate saves)

3. **SQLiteSpacePropertiesRepositoryTest.kt** (30 tests)
   - Save/load roundtrip with complex nested data
   - JSON serialization (exits map, traps/resources/entities arrays)
   - updateDescription() optimized query (only desc changes)
   - updateFlags() optimized query
   - addItems() appends to itemsDropped list
   - TerrainType enum conversion
   - Error cases (nonexistent chunk_id)

4. **SQLiteWorldSeedRepositoryTest.kt** (8 tests)
   - Save/get roundtrip
   - Single row constraint (second save updates, not inserts)
   - Null handling (no seed yet)

**Total Tests**: ~78 tests (focus on serialization, optimized queries, error handling)

**Documentation Updates**:
- None yet (comprehensive update in Chunk 7)

---

### Chunk 3: Generation Pipeline Core (5 hours)

**Description**: Implement LLM-driven world generation with lore inheritance and cascade logic.

**Affected Modules**:
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/`
- `memory/src/main/kotlin/com/jcraw/mud/memory/world/`

**Files to Create/Modify**:
1. **GenerationContext.kt** (reasoning:50)
   - Data class: `seed: String, globalLore: String, parentChunk: WorldChunkComponent?, level: ChunkLevel, direction: String?`
   - Provides context for LLM prompts
   - *Reasoning*: Encapsulates generation parameters; direction hint enables spatial coherence

2. **WorldGenerator.kt** (reasoning:350)
   - Primary generation engine
   - Methods:
     - `generateChunk(context: GenerationContext): Result<Pair<WorldChunkComponent, String>>` - Returns (chunk, chunkId)
       - LLM prompt: "Based on seed '{seed}' and parent lore '{parentLore}', generate {level} details for a {direction} direction chunk: rich lore (politics, inhabitants, weather stubs), biome theme, size estimate (5-100 for SUBZONE, 1-500 for ZONE), mob density (0-1), difficulty level (1-20). Parent theme: {parentBiomeTheme}. Output JSON: {lore, biomeTheme, sizeEstimate, mobDensity, difficultyLevel}"
       - Parse LLM JSON response
       - Validate ranges (mobDensity 0-1, difficultyLevel scales with depth)
       - Generate unique ID: `UUID.randomUUID().toString()`
       - Apply inheritance: lore variation based on parent, theme coherence
     - `generateSpace(parentSubzone: WorldChunkComponent): Result<Pair<SpacePropertiesComponent, String>>`
       - LLM prompt: "Generate room/space description for {biomeTheme} subzone. Lore: {lore}. Include exits (3-6, mix cardinal and descriptive like 'climb ladder'), brightness (0-100), terrain type (NORMAL/DIFFICULT/IMPASSABLE), and initial entities (NPCs/mobs based on mobDensity {density}, difficultyLevel {diff}, theme {theme}). Output JSON: {description, exits: [{direction, description, targetId: 'PLACEHOLDER'}], brightness, terrainType, entities: [{name, type, health, lootTable}]}"
       - Parse LLM response
       - Generate traps (10-20% chance per space): `if (Random.nextDouble() < 0.15) generateTrap(theme)`
       - Generate resources (5% chance for nodes): `if (Random.nextDouble() < 0.05) generateResource(theme)`
       - Hidden exits (20% of exits): Add `SkillCheck(Perception, difficulty=10+difficultyLevel)` to conditions
     - `generateTrap(theme: String): TrapData` - Theme-based trap selection (e.g., "dark forest" → bear trap)
     - `generateResource(theme: String): ResourceNode` - Theme-based resource (e.g., "magma cave" → obsidian ore)
     - `generateEntities(theme: String, mobDensity: Double, difficultyLevel: Int): List<Entity.NPC>` - Create mobs/NPCs
   - *Reasoning*: Single responsibility per method; LLM prompts are detailed for consistency; validation catches invalid LLM output

3. **LoreInheritanceEngine.kt** (reasoning:180)
   - Methods:
     - `varyLore(parentLore: String, level: ChunkLevel, direction: String?): String`
       - LLM prompt: "Given parent lore: '{parentLore}', create a {level}-level variation for {direction} direction. Maintain consistency but introduce local details (e.g., faction branch, weather variation, specific inhabitants). Output 2-4 sentences."
       - Temperature: 0.7 (balance consistency with creativity)
     - `blendThemes(parentTheme: String, variation: String): String`
       - Example: "snowy mountain" + "hot caves" → "volcanic tunnels beneath glacier"
       - LLM prompt: "Blend parent theme '{parentTheme}' with local variation '{variation}'. Output concise theme name (2-4 words)."
   - *Reasoning*: Separate lore logic from generation; reusable across chunk types

4. **ChunkIdGenerator.kt** (reasoning:60)
   - Methods:
     - `generate(level: ChunkLevel, parentId: String?): String`
       - Format: `{level}_{parentId}_{uuid}` (e.g., "SPACE_subzone123_a1b2c3d4")
       - WORLD level: `WORLD_root`
     - `parse(id: String): ChunkLevel?` - Extract level from ID
   - *Reasoning*: Readable IDs for debugging; hierarchical structure visible in ID

5. **GenerationCache.kt** (memory:120)
   - In-memory cache for pending/recent generations
   - Methods:
     - `cachePending(id: String, context: GenerationContext)` - Mark generation in progress
     - `cacheComplete(id: String, chunk: WorldChunkComponent)`
     - `getCached(id: String): WorldChunkComponent?` - Avoid duplicate generations
     - `isPending(id: String): Boolean` - Prevent concurrent gen of same chunk
   - Eviction: LRU with 1000 chunk limit
   - *Reasoning*: Prevents race conditions (multiple players approach same boundary); improves perf

6. **DungeonInitializer.kt** (reasoning:150)
   - V2 MVP-specific: Generates starter dungeon hierarchy
   - Methods:
     - `initializeDeepDungeon(seed: String): Result<String>` - Returns root WORLD chunk ID
       - Creates WORLD: "Ancient Abyss Dungeon" with global lore
       - Creates 3 REGIONS: "Upper Depths" (floors 1-10), "Mid Depths" (11-50), "Lower Depths" (51-100+)
       - Pre-generates first ZONE/SUBZONE/SPACE (player start location at top)
       - Saves to DB via repositories
       - Returns starting space ID
   - *Reasoning*: MVP needs predictable start; pre-gen avoids first-move latency

**Key Technical Decisions**:
- **LLM Temperature**: 0.7 for generation (balance creativity with consistency)
  - *Reasoning*: Too low (0.3) → repetitive; too high (1.0) → incoherent
- **JSON Output**: Force LLM to output structured JSON (easier parsing than free text)
  - *Reasoning*: Reduces parsing errors; validation simpler
- **Placeholder Exit Targets**: Initial generation uses "PLACEHOLDER"; linked in Chunk 4
  - *Reasoning*: Can't know target IDs until chunks exist; two-pass approach separates concerns
- **ID Format**: Human-readable with hierarchy (level_parent_uuid)
  - *Reasoning*: Debugging easier; DB queries can filter by level prefix

**Testing Approach** (`reasoning:test`):
1. **WorldGeneratorTest.kt** (30 tests)
   - `generateChunk()` with mock LLM (various levels)
   - JSON parsing success/failure cases
   - Validation: mobDensity range, difficultyLevel scaling
   - Lore inheritance (parent lore appears in child)
   - Theme coherence (child theme relates to parent)
   - Trap generation probability (~15% over 100 trials)
   - Resource generation probability (~5% over 100 trials)
   - Hidden exit probability (~20% over 100 exits)
   - Entity generation (count matches mobDensity * sizeEstimate)

2. **LoreInheritanceEngineTest.kt** (15 tests)
   - `varyLore()` with mock LLM (check prompt structure)
   - Output length validation (2-4 sentences)
   - Consistency check (keywords from parent appear in variation)
   - `blendThemes()` output format (2-4 words)

3. **ChunkIdGeneratorTest.kt** (10 tests)
   - `generate()` format validation (regex match)
   - Uniqueness (multiple calls return different UUIDs)
   - `parse()` extracts correct level
   - WORLD level special case (no parent)

4. **GenerationCacheTest.kt** (12 tests)
   - `cachePending/Complete()` flow
   - `isPending()` prevents duplicate gen
   - `getCached()` returns recent chunks
   - LRU eviction (1001st chunk evicts oldest)
   - Concurrent access safety

5. **DungeonInitializerTest.kt** (18 tests)
   - `initializeDeepDungeon()` creates hierarchy
   - WORLD→REGION→ZONE→SUBZONE→SPACE chain
   - Starting space has valid exits
   - DB persistence (chunks saved correctly)
   - Lore consistency across levels
   - Difficulty scaling (Upper < Mid < Lower)

**Total Tests**: ~85 tests (focus on LLM prompt structure, validation, probabilistic mechanics)

**Documentation Updates**:
- None yet (comprehensive update in Chunk 7)

---

### Chunk 4: Exit System & Navigation (4 hours)

**Description**: Implement hybrid exit resolution, conditional checks, and movement intent handling.

**Affected Modules**:
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/`
- `perception/src/main/kotlin/com/jcraw/mud/perception/`

**Files to Create/Modify**:
1. **Intent.Scout** (perception:30)
   - Data class: `direction: String`
   - Reveals description/hints for a direction without moving
   - *Reasoning*: Supports cautious exploration; skill-filtered (Perception)

2. **Intent.Travel** (perception:35)
   - Data class: `direction: String`
   - Replaces existing `Intent.Move` for world system (keep Move for backward compat in non-world mode)
   - *Reasoning*: Distinct from simple Move; indicates world system pathfinding

3. **ExitResolver.kt** (reasoning:280)
   - Core exit resolution engine
   - Methods:
     - `resolve(exitIntent: String, currentSpace: SpacePropertiesComponent, playerState: PlayerState): ResolveResult`
       - Phase 1: Exact match (case-insensitive cardinal directions: n/s/e/w/up/down/ne/nw/se/sw)
       - Phase 2: Fuzzy match (Levenshtein distance < 3 for exit descriptions)
       - Phase 3: LLM parse (prompt: "Player said '{exitIntent}'. Available exits: {exitList}. Which exit matches? Output: 'EXIT:<direction>' or 'UNCLEAR'")
       - Check conditions: `exit.conditions.all { it.meetsConditions(playerState) }`
       - Return ResolveResult (Success with targetId, Failure with reason, Ambiguous with suggestions)
     - `getVisibleExits(space: SpacePropertiesComponent, playerState: PlayerState): List<ExitData>`
       - Filter hidden exits: Check Perception skill vs difficulty
       - Return only exits player can see
     - `describeExit(exit: ExitData, player: PlayerState): String`
       - Show conditions if not met: "You see stairs leading up, but they look treacherous (requires Agility 15 or climbing gear)"
   - *Reasoning*: Three-phase approach balances speed (exact) with flexibility (LLM); condition checks prevent invalid moves

4. **ExitLinker.kt** (reasoning:150)
   - Links placeholder exit targets after generation
   - Methods:
     - `linkExits(spaceId: String, space: SpacePropertiesComponent, parentSubzone: WorldChunkComponent, worldGen: WorldGenerator): Result<SpacePropertiesComponent>`
       - For each exit with targetId "PLACEHOLDER":
         - Generate adjacent chunk/space in direction
         - Update exit targetId to actual generated ID
         - Create reciprocal exit in target space (e.g., if source has "north", target gets "south")
       - Save updated space to DB
     - `createReciprocalExit(direction: String): String` - Maps direction to opposite (n→s, up→down, "climb ladder"→"descend ladder")
   - *Reasoning*: Two-pass generation (gen chunk, then link) avoids circular dependencies

5. **MovementCostCalculator.kt** (reasoning:100)
   - Calculates time cost and risks for movement
   - Methods:
     - `calculateCost(terrain: TerrainType, playerState: PlayerState): MovementCost`
       - NORMAL: 1 tick
       - DIFFICULT: 2 ticks + Agility check (DC 10, fail = 1d6 damage)
       - IMPASSABLE: 0 ticks (movement fails)
     - `applySkillModifiers(baseCost: Int, player: PlayerState): Int`
       - Athletics skill reduces difficult terrain cost (level 10+ = -1 tick)
   - Data class `MovementCost(ticks: Int, damageRisk: Int, success: Boolean)`
   - *Reasoning*: Integrates with combat turn queue (movement consumes ticks); skill integration

6. **NavigationState.kt** (core:60)
   - Tracks player's current location in world hierarchy
   - Data class: `currentSpaceId: String, currentSubzoneId: String, currentZoneId: String, currentRegionId: String, worldId: String`
   - Methods:
     - `updateLocation(newSpaceId: String, repos: WorldChunkRepository): NavigationState` - Query DB to update hierarchy IDs
   - Add to `PlayerState` as `navigationState: NavigationState?` (null for legacy non-world mode)
   - *Reasoning*: Fast hierarchy queries (avoid repeated DB lookups); enables breadcrumb trails

**Key Technical Decisions**:
- **Three-Phase Resolution**: Exact → Fuzzy → LLM
  - *Reasoning*: Most inputs are simple (n/s/e/w); fallback to LLM only when needed (reduce API cost)
- **Reciprocal Exits**: Auto-generate return paths
  - *Reasoning*: Prevents one-way exits (player frustration); maintains spatial coherence
- **Skill-Filtered Visibility**: Hidden exits require Perception check
  - *Reasoning*: Rewards exploration builds; creates "secret" paths
- **Movement Costs**: Integrate with combat turn queue
  - *Reasoning*: Terrain affects combat pacing (difficult terrain = fewer player turns)

**Testing Approach** (`perception:test`, `reasoning:test`):
1. **IntentTest.kt** (perception:test, 10 tests)
   - Intent.Scout serialization
   - Intent.Travel serialization
   - Backward compat (Intent.Move still works)

2. **ExitResolverTest.kt** (reasoning:test, 35 tests)
   - `resolve()` phase 1 (exact match: n/s/e/w/up/down)
   - Phase 2 (fuzzy: "nroth" matches "north")
   - Phase 3 (LLM: "climb the rusty ladder" → "up")
   - Condition checks (SkillCheck fails if player skill too low)
   - ItemRequired condition (no item → failure)
   - Hidden exits not resolved if Perception too low
   - Ambiguous input → suggestions
   - `getVisibleExits()` filters by Perception
   - `describeExit()` shows condition hints

3. **ExitLinkerTest.kt** (reasoning:test, 20 tests)
   - `linkExits()` replaces PLACEHOLDER with real IDs
   - Reciprocal exit creation (n→s, "ladder"→"descend")
   - Multiple exits per space
   - DB persistence after linking
   - Error cases (orphaned placeholders)

4. **MovementCostCalculatorTest.kt** (reasoning:test, 15 tests)
   - `calculateCost()` for each terrain type
   - Agility check on DIFFICULT (fail = damage)
   - Athletics skill reduction
   - Tick integration with combat turn queue

5. **NavigationStateTest.kt** (core:test, 12 tests)
   - `updateLocation()` queries hierarchy
   - PlayerState integration (nullable field)
   - Breadcrumb trail (list of visited spaces)

**Total Tests**: ~92 tests (focus on resolution phases, condition validation, spatial coherence)

**Documentation Updates**:
- None yet (comprehensive update in Chunk 7)

---

### Chunk 5: Content Placement & Spawning (4 hours)

**Description**: Implement trap/resource/entity placement with theme-based generation and mob respawn logic.

**Affected Modules**:
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/`
- `core/src/main/kotlin/com/jcraw/mud/core/`

**Files to Create/Modify**:
1. **ThemeRegistry.kt** (reasoning:120)
   - Registry mapping biome themes to content rules
   - Data class `ThemeProfile(traps: List<String>, resources: List<String>, mobArchetypes: List<String>, ambiance: String)`
   - Predefined profiles:
     - "dark forest": traps=[bear trap, pit], resources=[wood, herbs], mobs=[wolf, bandit], ambiance="damp, shadowy"
     - "magma cave": traps=[lava pool, collapsing floor], resources=[obsidian, sulfur], mobs=[fire elemental, magma worm], ambiance="scorching, smoky"
     - "ancient crypt": traps=[poison dart, cursed rune], resources=[bone, arcane dust], mobs=[skeleton, wraith], ambiance="cold, silent"
   - Method: `getProfile(theme: String): ThemeProfile?` - Exact match or LLM-based semantic match
   - *Reasoning*: Centralized content rules; easy to extend with new themes

2. **TrapGenerator.kt** (reasoning:130)
   - Methods:
     - `generate(theme: String, difficulty: Int): TrapData`
       - Select trap type from ThemeRegistry
       - Scale difficulty (dungeon level + random variance)
       - Generate unique ID: `trap_${UUID.randomUUID()}`
     - `generateTrapDescription(trap: TrapData, theme: String): String`
       - LLM prompt: "Describe a {trap.type} trap in a {theme} setting. 1-2 sentences, vivid but concise."
   - *Reasoning*: Traps add danger; LLM descriptions create immersion

3. **ResourceGenerator.kt** (reasoning:150)
   - Methods:
     - `generate(theme: String, difficulty: Int): ResourceNode`
       - Select resource from ThemeRegistry
       - Match resource to ItemTemplate (query ItemRepository)
       - Quantity: `Random.nextInt(1, 5) * difficulty` (harder areas = more yield)
       - Respawn time: `if (difficulty < 5) null else 100 + difficulty * 10` (deep dungeons have renewable resources)
     - `generateNodeDescription(resource: ResourceNode, theme: String): String`
       - LLM prompt: "Describe a {resource.templateId} resource node in {theme}. 1 sentence."
   - *Reasoning*: Ties to items system; renewable resources for sustained exploration

4. **MobSpawner.kt** (reasoning:220)
   - Methods:
     - `spawnEntities(theme: String, mobDensity: Double, difficulty: Int, spaceSize: Int): List<Entity.NPC>`
       - Mob count: `(spaceSize * mobDensity).toInt().coerceAtLeast(0)`
       - Select archetypes from ThemeRegistry
       - LLM prompt: "Generate {count} NPC/mob entries for {theme} at difficulty {difficulty}. Output JSON array: [{name, description, health, lootTableId, goldDrop, baseStats}]"
       - Parse JSON, create Entity.NPC instances
       - Assign loot tables from LootTableRegistry (create new if theme not registered)
     - `respawn(space: SpacePropertiesComponent, theme: String, density: Double, difficulty: Int): List<Entity.NPC>`
       - Clear existing entities, regenerate fresh list
       - Used on game restart
   - *Reasoning*: Dynamic mob variety; respawn enables replayability

5. **LootTableGenerator.kt** (reasoning:180)
   - Extends existing LootTableRegistry with procedural generation
   - Methods:
     - `generateForTheme(theme: String, difficulty: Int): LootTable`
       - Query ItemRepository for items matching theme (e.g., "obsidian" for magma cave)
       - Create weighted entries (higher difficulty = better items, lower weight)
       - Rarity bias: COMMON 50%, UNCOMMON 30%, RARE 15%, EPIC 4%, LEGENDARY 1%
       - Register in LootTableRegistry with ID: `{theme}_{difficulty}`
     - `generateGoldRange(difficulty: Int): IntRange`
       - Formula: `(10 * difficulty)..(50 * difficulty)`
   - *Reasoning*: Theme-specific loot creates immersion; difficulty scaling rewards risk

6. **SpacePopulator.kt** (reasoning:200)
   - Orchestrates all content placement
   - Methods:
     - `populate(space: SpacePropertiesComponent, theme: String, difficulty: Int, mobDensity: Double): SpacePropertiesComponent`
       - Generate traps (10-20% chance)
       - Generate resources (5% chance)
       - Spawn mobs (MobSpawner)
       - Return updated space with all content
     - `repopulate(space: SpacePropertiesComponent, theme: String, difficulty: Int, mobDensity: Double): SpacePropertiesComponent`
       - Respawn mobs only (preserve traps/resources/flags)
   - *Reasoning*: Single entry point for population; repopulate separates mob reset from world changes

**Key Technical Decisions**:
- **Theme Registry**: Hardcoded profiles for V2, extensible for V3
  - *Reasoning*: LLM generation for every trap/resource too expensive; registry provides fast lookup
- **Procedural Loot Tables**: Generate on-demand, cache in registry
  - *Reasoning*: Infinite themes impossible to pre-define; procedural approach scales
- **Respawn Logic**: Regenerate mobs, preserve world changes (traps/resources/flags)
  - *Reasoning*: Murder-hobo viable; dungeons repopulate for replay value
- **Quantity Scaling**: Resources/mobs scale with difficulty
  - *Reasoning*: Rewards risk (deeper dungeons = better loot)

**Testing Approach** (`reasoning:test`):
1. **ThemeRegistryTest.kt** (12 tests)
   - Predefined profiles exist (dark forest, magma cave, crypt)
   - `getProfile()` exact match
   - LLM semantic match (e.g., "lava dungeon" → "magma cave")

2. **TrapGeneratorTest.kt** (15 tests)
   - `generate()` returns theme-appropriate traps
   - Difficulty scaling
   - Unique IDs
   - `generateTrapDescription()` with mock LLM

3. **ResourceGeneratorTest.kt** (18 tests)
   - `generate()` matches ItemTemplate
   - Quantity scales with difficulty
   - Respawn time logic (low difficulty = no respawn)
   - `generateNodeDescription()` with mock LLM

4. **MobSpawnerTest.kt** (25 tests)
   - `spawnEntities()` count matches density * size
   - Theme-appropriate mobs (wolves in forest, fire elementals in magma)
   - JSON parsing from LLM
   - Loot table assignment
   - `respawn()` clears old entities, generates new
   - Error cases (invalid theme, LLM parse failure)

5. **LootTableGeneratorTest.kt** (20 tests)
   - `generateForTheme()` creates weighted table
   - Rarity distribution (~50% common, ~1% legendary over 1000 trials)
   - Difficulty scaling (higher diff = better items)
   - Registry registration (theme_difficulty ID)
   - `generateGoldRange()` formula validation

6. **SpacePopulatorTest.kt** (22 tests)
   - `populate()` adds traps/resources/mobs
   - Trap probability (~15% over 100 spaces)
   - Resource probability (~5% over 100 spaces)
   - Mob count matches density
   - `repopulate()` preserves flags/traps/resources
   - Integration test (full populate → save → load → repopulate)

**Total Tests**: ~112 tests (focus on probabilistic mechanics, theme matching, integration)

**Documentation Updates**:
- None yet (comprehensive update in Chunk 7)

---

### Chunk 6: State Changes & Persistence (4 hours)

**Description**: Implement state flag system, description regeneration, and full save/load integration.

**Affected Modules**:
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/`
- `memory/src/main/kotlin/com/jcraw/mud/memory/`
- `app/src/main/kotlin/com/jcraw/app/`

**Files to Create/Modify**:
1. **StateChangeHandler.kt** (reasoning:180)
   - Handles player-initiated world modifications
   - Methods:
     - `applyChange(space: SpacePropertiesComponent, action: WorldAction, player: PlayerState): Result<SpacePropertiesComponent>`
       - WorldAction sealed class: DestroyObstacle(flag), TriggerTrap(trapId), HarvestResource(nodeId), PlaceItem(item), etc.
       - Update stateFlags: `space.stateFlags + (action.flag to true)`
       - Remove resources on harvest
       - Trigger trap (set triggered=true)
       - Return updated space
     - `shouldRegenDescription(oldFlags: Map<String, Boolean>, newFlags: Map<String, Boolean>): Boolean`
       - True if any flag changed
     - `regenDescription(space: SpacePropertiesComponent, lore: String, llmService: LLMService): String`
       - LLM prompt: "Regenerate room description. Original: '{space.description}'. State changes: {changedFlags}. Lore context: {lore}. Output: updated description (2-4 sentences)."
   - *Reasoning*: Centralized change logic; description regen only when needed (performance)

2. **WorldAction.kt** (core:80)
   - Sealed class for type-safe actions:
     - `DestroyObstacle(flag: String, skillRequired: String?, difficulty: Int?)`
     - `TriggerTrap(trapId: String)`
     - `HarvestResource(nodeId: String)`
     - `PlaceItem(item: ItemInstance)`
     - `RemoveItem(itemId: String)`
     - `UnlockExit(exitDirection: String, keyItem: String?)`
   - *Reasoning*: Exhaustive when statements; easy to add new actions

3. **WorldPersistence.kt** (memory:250)
   - Extends existing save/load system
   - Methods:
     - `saveWorldState(worldId: String, playerState: PlayerState): Result<Unit>`
       - Save player's NavigationState
       - Query all loaded chunks from GenerationCache
       - Batch save to DB (chunks, spaces, seed)
       - Save player state (existing system)
     - `loadWorldState(saveName: String): Result<Pair<String, PlayerState>>`
       - Load seed and global lore
       - Load player state (existing system)
       - Load player's current space + adjacent spaces (prefetch)
       - Return (startingSpaceId, playerState)
     - `saveSpace(space: SpacePropertiesComponent, chunkId: String): Result<Unit>`
       - Incremental save (autosave after each move)
   - *Reasoning*: Incremental saves prevent loss; prefetch reduces first-move latency

4. **RespawnManager.kt** (reasoning:150)
   - Handles game restart with mob respawn
   - Methods:
     - `respawnWorld(worldId: String): Result<Unit>`
       - Load all chunks from DB
       - For each space:
         - Preserve stateFlags, itemsDropped, resources
         - Clear entities list
         - Respawn mobs via MobSpawner (theme, density, difficulty)
         - Save updated space to DB
     - `createFreshStart(dungeonInitializer: DungeonInitializer): Result<String>`
       - Initialize new dungeon (calls DungeonInitializer)
       - Return starting space ID
   - *Reasoning*: Murder-hobo viable; dungeon repopulates for replay

5. **MudGameEngine.kt** (app:modify existing)
   - Add world system fields:
     - `worldGenerator: WorldGenerator`
     - `exitResolver: ExitResolver`
     - `stateChangeHandler: StateChangeHandler`
     - `worldPersistence: WorldPersistence`
     - `spacePopulator: SpacePopulator`
     - `currentWorldId: String?` (null for legacy mode)
   - Modify game loop:
     - On Intent.Travel: Resolve exit, move player, autosave space
     - On Intent.Scout: Describe exit (no movement)
     - On WorldAction intents: Apply change, regen description if needed
   - Add world mode toggle in startup menu
   - *Reasoning*: Backward compat with existing (non-world) mode; world is opt-in

6. **AutosaveManager.kt** (memory:100)
   - Periodic autosave (every 5 moves or 2 minutes)
   - Methods:
     - `scheduleAutosave(worldId: String, playerState: PlayerState, delay: Duration)`
       - Kotlin coroutine with delay
       - Calls WorldPersistence.saveWorldState()
     - `cancelAutosave()` - Stop coroutine on game exit
   - *Reasoning*: Prevents data loss; unobtrusive (async)

**Key Technical Decisions**:
- **Incremental Saves**: Save space after each move, full save on exit
  - *Reasoning*: Balance data safety with performance (full DB write on every action too expensive)
- **Description Regen**: Only when flags change
  - *Reasoning*: LLM calls expensive; cache descriptions until state changes
- **Respawn Scope**: Mobs only, not traps/resources
  - *Reasoning*: Player changes to world persist; mobs repopulate for gameplay
- **Autosave Frequency**: Every 5 moves or 2 minutes
  - *Reasoning*: Not too frequent (annoying), not too rare (data loss risk)

**Testing Approach** (`reasoning:test`, `memory:test`):
1. **StateChangeHandlerTest.kt** (reasoning:test, 25 tests)
   - `applyChange()` updates stateFlags
   - Trap triggering (triggered=true)
   - Resource harvest (removed from list)
   - `shouldRegenDescription()` detects flag changes
   - `regenDescription()` with mock LLM (prompt validation)

2. **WorldActionTest.kt** (core:test, 12 tests)
   - Sealed class serialization
   - Exhaustive when statement (compile error if case missing)

3. **WorldPersistenceTest.kt** (memory:test, 30 tests)
   - `saveWorldState()` full save (chunks + player)
   - `loadWorldState()` restores state
   - Incremental `saveSpace()` after moves
   - Prefetch adjacent spaces on load
   - Error cases (corrupted DB, missing seed)
   - Integration test (save → restart → load → state matches)

4. **RespawnManagerTest.kt** (reasoning:test, 18 tests)
   - `respawnWorld()` clears entities, preserves flags
   - Mob count matches original mobDensity
   - Traps/resources unchanged
   - `createFreshStart()` initializes dungeon

5. **MudGameEngineTest.kt** (app:test, 20 tests)
   - World mode toggle (world vs legacy)
   - Intent.Travel resolution (move player, autosave)
   - Intent.Scout (no movement)
   - WorldAction handling (state change + desc regen)
   - Integration test (explore → modify world → save → load → changes persist)

6. **AutosaveManagerTest.kt** (memory:test, 10 tests)
   - Autosave triggers on move count (5 moves)
   - Autosave triggers on time (2 minutes)
   - Coroutine cancellation on exit
   - No data loss on crash (last autosave recoverable)

**Total Tests**: ~115 tests (focus on persistence, state consistency, integration)

**Documentation Updates**:
- None yet (comprehensive update in Chunk 7)

---

### Chunk 7: Integration, Testing & Documentation (5 hours)

**Description**: Implement handlers, UI updates, comprehensive testing, and full documentation.

**Affected Modules**:
- `app/src/main/kotlin/com/jcraw/app/handlers/`
- `client/src/main/kotlin/com/jcraw/mud/client/handlers/`
- `testbot/src/test/kotlin/com/jcraw/mud/testbot/scenarios/`
- All documentation files

**Files to Create/Modify**:
1. **WorldHandlers.kt** (app/handlers:250)
   - Intent handlers for world system:
     - `handleTravel(intent: Intent.Travel, state: GameState): GameState`
       - Resolve exit via ExitResolver
       - Check conditions (skill/item requirements)
       - Calculate movement cost (MovementCostCalculator)
       - Generate adjacent chunk if boundary reached (WorldGenerator)
       - Update player NavigationState
       - Autosave space
       - Narrate movement and new room description
     - `handleScout(intent: Intent.Scout, state: GameState): GameState`
       - Resolve exit (no movement)
       - Describe what player sees in that direction
       - Show conditions if exit locked/hidden
     - `handleWorldAction(action: WorldAction, state: GameState): GameState`
       - Apply state change (StateChangeHandler)
       - Regen description if flags changed
       - Narrate action result
   - *Reasoning*: Separate handler file keeps concerns isolated

2. **ClientWorldHandlers.kt** (client/handlers:220)
   - GUI client equivalents of WorldHandlers
   - Updates UiState with room descriptions, exit lists, minimap data
   - *Reasoning*: Unidirectional data flow (handlers → UiState → UI renders)

3. **MovementHandlers.kt** (app/handlers:modify existing)
   - Add world mode check:
     - If `currentWorldId != null`: Use Intent.Travel (world system)
     - Else: Use Intent.Move (legacy room system)
   - *Reasoning*: Backward compat; seamless mode switching

4. **HelpText.kt** (app:modify existing)
   - Add world system commands:
     - "scout <direction> - Look in a direction without moving"
     - "travel <direction> - Move to adjacent space (supports natural language)"
     - "interact <object> - Interact with world objects (destroy, unlock, etc.)"
   - *Reasoning*: Discoverability for new players

5. **UiState.kt** (client:modify existing)
   - Add world system fields:
     - `currentSpaceDescription: String`
     - `visibleExits: List<ExitData>`
     - `minimapData: MinimapData?` (stub for V3)
     - `navigationBreadcrumbs: List<String>` (visited spaces)
   - *Reasoning*: UI observes state; all world data exposed

6. **WorldExplorationTest.kt** (testbot:350)
   - Bot scenario: Deep dungeon playthrough
   - Steps:
     1. Initialize world (DungeonInitializer)
     2. Explore 20+ spaces (mix cardinal and natural language)
     3. Encounter traps (some spotted, some triggered)
     4. Harvest resources
     5. Combat mobs (existing combat system)
     6. Loot corpses
     7. Navigate to deeper levels (difficulty increases)
     8. Die in combat
     9. Restart game (respawn at top)
     10. Retrieve corpse from deep level (test persistence)
   - Assertions:
     - World generates on-demand (chunk count increases)
     - Descriptions are unique (LLM variety)
     - Exit resolution works (cardinal + NLP)
     - Traps trigger (damage taken)
     - Resources harvested (items added)
     - Difficulty scales (deeper mobs harder)
     - State persists (corpse location saved)
     - Mobs respawned (entities regenerated)
   - *Reasoning*: End-to-end test validates full system integration

7. **WorldGenerationIntegrationTest.kt** (reasoning:test:200)
   - Integration test without bot:
     - Generate hierarchy (WORLD→REGION→ZONE→SUBZONE→SPACE)
     - Link exits (placeholders replaced)
     - Populate spaces (traps/resources/mobs)
     - Modify state (flags)
     - Save to DB
     - Load from DB
     - Verify all data persisted correctly
   - *Reasoning*: Faster than bot test; focused on data integrity

8. **CLAUDE.md** (docs:modify existing)
   - Add "World Generation System ✅" to implemented features
   - Update commands section with scout/travel
   - Add world mode toggle to startup flow
   - *Reasoning*: Primary project doc

9. **ARCHITECTURE.md** (docs:modify existing)
   - Add world generation module descriptions
   - Update data flow diagram (Intent→Generator→DB→Narration)
   - Document new component types (WORLD_CHUNK, SPACE_PROPERTIES)
   - *Reasoning*: Technical reference for developers

10. **GETTING_STARTED.md** (docs:modify existing)
    - Add world mode section
    - Document scout/travel commands with examples
    - Explain hidden exits and skill checks
    - *Reasoning*: User-facing guide

11. **WORLD_GENERATION.md** (docs:new file:400)
    - Comprehensive world system documentation:
      - Architecture overview (hierarchy, lazy generation)
      - Component details (WorldChunkComponent, SpacePropertiesComponent)
      - Generation pipeline (LLM prompts, lore inheritance)
      - Exit system (resolution phases, conditions)
      - Content placement (traps, resources, mobs)
      - State persistence (saves, respawn)
      - V2 MVP scope (deep dungeon)
      - V3 roadmap (open world, weather, multiplayer)
    - *Reasoning*: Dedicated doc for complex system

**Key Technical Decisions**:
- **Backward Compatibility**: World system is opt-in; legacy mode still works
  - *Reasoning*: Gradual migration; existing saves not broken
- **Bot Test**: Single comprehensive scenario validates full stack
  - *Reasoning*: Executable documentation; catches integration bugs
- **Documentation First**: Write docs before considering implementation complete
  - *Reasoning*: Forces clarity; docs often reveal design gaps

**Testing Approach** (`app:test`, `client:test`, `testbot`):
1. **WorldHandlersTest.kt** (app:test, 30 tests)
   - `handleTravel()` with various exit types
   - Condition failures (skill too low, missing item)
   - Boundary detection (generate new chunk)
   - Autosave triggers
   - Movement cost calculations

2. **ClientWorldHandlersTest.kt** (client:test, 25 tests)
   - UiState updates on travel
   - Exit list rendering
   - Breadcrumb trail

3. **MovementHandlersTest.kt** (app:test, 15 tests)
   - Mode switching (world vs legacy)
   - Backward compat (existing saves work)

4. **WorldExplorationTest.kt** (testbot, 1 comprehensive scenario)
   - Full playthrough (20+ moves, combat, death, respawn)
   - ~50 assertions covering all systems

5. **WorldGenerationIntegrationTest.kt** (reasoning:test, 30 tests)
   - Hierarchy generation
   - Exit linking
   - Content population
   - State persistence
   - Full roundtrip (gen → save → load)

**Total Tests**: ~100 tests + 1 bot scenario (focus on integration, edge cases, user workflows)

**Documentation Updates**:
- ✅ CLAUDE.md (updated with world system features)
- ✅ ARCHITECTURE.md (module structure, data flow)
- ✅ GETTING_STARTED.md (user commands, examples)
- ✅ WORLD_GENERATION.md (new comprehensive guide)

---

## Summary

**Total Implementation**:
- **7 chunks** × 3-5 hours each = **29 hours**
- **~576 unit/integration tests** across all chunks
- **1 comprehensive bot scenario** (end-to-end validation)
- **4 documentation files** updated/created

**Chunk Sequence**:
1. Foundation (components, data classes)
2. Database (schema, repositories)
3. Generation Pipeline (LLM, lore inheritance)
4. Exit System (resolution, conditions)
5. Content Placement (traps, resources, mobs)
6. State Changes (flags, persistence, respawn)
7. Integration (handlers, UI, tests, docs)

**Key Principles Applied**:
- **KISS**: Avoid overengineering (hierarchical chunks, simple ID scheme)
- **Modularity**: Clear separation (:core data, :reasoning logic, :memory persistence)
- **Immutability**: All state transitions return new copies
- **Testing Focus**: Behavior and contracts, not line coverage
- **LLM Leverage**: Semantic parsing, rich narratives, theme coherence
- **Backward Compat**: World system is opt-in (legacy mode preserved)

**Success Criteria**:
- ✅ Hierarchical on-demand generation with lore inheritance
- ✅ Flexible exits (cardinal + natural language) with skill/item conditions
- ✅ Theme-based content (traps, resources, mobs)
- ✅ Persistent state changes (flags, corpses, items)
- ✅ Mob respawn on restart (murder-hobo viable)
- ✅ V2 deep dungeon MVP (top-to-bottom progression)
- ✅ Full integration with skills, combat, items, social
- ✅ Comprehensive tests and documentation
