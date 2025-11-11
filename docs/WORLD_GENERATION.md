# World Generation System V2

## Overview

The World Generation System V2 provides hierarchical, on-demand procedural world generation for infinite, lore-consistent open worlds. The V2 MVP implementation focuses on a deep dungeon with top-to-bottom progression, mob respawns, and full integration with existing skills, combat, items, and social systems.

**Status**: Chunks 1-6 complete (foundation through persistence). Chunk 7 (handlers, UI integration, final testing) in progress.

## Architecture

### Hierarchical Chunks

The world uses a 5-level hierarchy for organization and generation:

1. **WORLD** (depth 0) - Top-level container, global lore and seed
2. **REGION** (depth 1) - Large areas (e.g., "Upper Depths", "Mid Depths")
3. **ZONE** (depth 2) - Medium areas (e.g., "Flooded Caverns", "Ancient Library")
4. **SUBZONE** (depth 3) - Small clusters (5-100 spaces)
5. **SPACE** (depth 4) - Atomic rooms/locations

Each level inherits lore and theme from its parent, creating coherent variations through LLM-driven generation.

### Component System

Two new ECS components extend the existing architecture:

#### WorldChunkComponent

Represents any level in the hierarchy (WORLD through SPACE).

```kotlin
data class WorldChunkComponent(
    val level: ChunkLevel,
    val parentId: String?,
    val children: List<String>,
    val lore: String,
    val biomeTheme: String,
    val sizeEstimate: Int,
    val mobDensity: Double,  // 0.0 to 1.0
    val difficultyLevel: Int  // 1 to 20
) : Component
```

**Key Methods**:
- `addChild(childId)` - Immutable child addition
- `removeChild(childId)` - Immutable child removal
- `withInheritedLore(parentLore)` - LLM-based lore variation
- `withTheme(theme)` - Update biome theme
- `validate()` - Hierarchy rules validation

#### SpacePropertiesComponent

Detailed properties for SPACE-level chunks only.

```kotlin
data class SpacePropertiesComponent(
    val description: String,
    val exits: Map<String, ExitData>,
    val brightness: Int,  // 0-100
    val terrainType: TerrainType,
    val traps: List<TrapData>,
    val resources: List<ResourceNode>,
    val entities: List<String>,  // Entity IDs
    val itemsDropped: List<ItemInstance>,
    val stateFlags: Map<String, Boolean>
) : Component
```

**Key Methods**:
- `updateDescription(newDesc)` - Replace description
- `resolveExit(intent)` - Find matching exit (exact/fuzzy)
- `applyChange(flag, value)` - Update state flags
- `addExit/removeExit/updateTrap/addResource` - CRUD operations

### Supporting Data Classes

#### ChunkLevel
```kotlin
enum class ChunkLevel(val depth: Int) {
    WORLD(0), REGION(1), ZONE(2), SUBZONE(3), SPACE(4)
}
```

#### TerrainType
```kotlin
enum class TerrainType(val timeCost: Int, val damageRisk: Int) {
    NORMAL(1, 0),
    DIFFICULT(2, 5),  // Agility check or take 1d6 damage
    IMPASSABLE(0, 0)  // Cannot traverse
}
```

#### ExitData
```kotlin
data class ExitData(
    val targetId: String,
    val description: String,
    val hidden: Boolean = false,
    val hiddenDifficulty: Int? = null,  // Perception DC
    val conditions: List<Condition> = emptyList()
)

sealed class Condition {
    data class SkillCheck(val skill: String, val difficulty: Int) : Condition()
    data class ItemRequired(val itemTag: String) : Condition()
}
```

#### TrapData
```kotlin
data class TrapData(
    val id: String,
    val type: String,
    val difficulty: Int,
    val damage: Int,
    val triggered: Boolean = false
)
```

#### ResourceNode
```kotlin
data class ResourceNode(
    val id: String,
    val resourceType: String,
    val templateId: String,
    val quantity: Int,
    val respawnTime: Int?  // null = finite resource
)
```

## Generation Pipeline

### LLM-Driven Generation

The system uses GPT-4o-mini (temperature 0.7) for cost-effective, creative generation.

#### Chunk Generation (WORLD/REGION/ZONE/SUBZONE)

**LLM Prompt Structure**:
```
Based on seed '{seed}' and parent lore '{parentLore}', generate {level}
details for a {direction} direction chunk:

- Rich lore (politics, inhabitants, weather stubs)
- Biome theme (2-4 words)
- Size estimate (5-100 for SUBZONE, 1-500 for ZONE)
- Mob density (0-1)
- Difficulty level (1-20)

Parent theme: {parentBiomeTheme}

Output JSON:
{
  "lore": "...",
  "biomeTheme": "...",
  "sizeEstimate": ...,
  "mobDensity": ...,
  "difficultyLevel": ...
}
```

**Validation**:
- mobDensity clamped to 0.0-1.0
- difficultyLevel scales with depth (deeper = harder)
- sizeEstimate coerced to reasonable ranges

#### Space Generation (SPACE)

**LLM Prompt Structure**:
```
Generate room/space description for {biomeTheme} subzone.
Lore: {lore}

Include:
- Vivid description (2-4 sentences)
- Exits (3-6, mix cardinal and descriptive like 'climb ladder')
- Brightness (0-100)
- Terrain type (NORMAL/DIFFICULT/IMPASSABLE)

Output JSON:
{
  "description": "...",
  "exits": [
    {"direction": "north", "description": "stone archway"},
    {"direction": "climb ladder", "description": "rusty ladder leading up"}
  ],
  "brightness": 50,
  "terrainType": "NORMAL"
}
```

**Post-Processing**:
- 20% of exits marked as hidden (requires Perception check)
- Traps generated (15% probability)
- Resources generated (5% probability)
- Mobs spawned based on mobDensity

### Lore Inheritance

`LoreInheritanceEngine` creates local variations while maintaining global coherence:

**Lore Variation**:
```
Given parent lore: '{parentLore}', create a {level}-level variation
for {direction} direction.

Maintain consistency but introduce local details (e.g., faction branch,
weather variation, specific inhabitants).

Output 2-4 sentences.
```

**Theme Blending**:
```
Blend parent theme '{parentTheme}' with local variation '{variation}'.
Output concise theme name (2-4 words).
```

**Direction Hints**: Spatial coherence (e.g., "north" = colder, "down" = darker)
WorldGenerator feeds these hints directly into both chunk and space prompts so newly generated areas mention how they sit relative to their parent (e.g., "downward stairwell", "northern glacier passage").

### ID Generation

Hierarchical ID format: `{level}_{parentId}_{uuid}`

Examples:
- WORLD: `WORLD_root`
- REGION: `REGION_WORLD_root_a1b2c3d4`
- SPACE: `SPACE_subzone123_e5f6g7h8`

**Benefits**:
- Human-readable for debugging
- DB queries can filter by level prefix
- Parent-child relationship visible

### Generation Cache

In-memory LRU cache (1000 chunk limit) prevents duplicate generation and race conditions:

- `cachePending(id, context)` - Mark generation in progress
- `cacheComplete(id, chunk)` - Store completed chunk
- `isPending(id)` - Check if generation underway
- `getCached(id)` - Retrieve cached chunk

Thread-safe with Kotlin coroutines and Mutex.

## Exit System

### Three-Phase Exit Resolution

`ExitResolver` uses a hybrid approach for flexibility and performance:

#### Phase 1: Exact Match
Fast, case-insensitive matching for cardinal directions:
- n, s, e, w, ne, nw, se, sw, up, down

#### Phase 2: Fuzzy Match
Levenshtein distance ≤ 2 for typos:
- "nroth" → "north"
- "clmb ladder" → "climb ladder"

#### Phase 3: LLM Parsing
Natural language understanding for complex input:

**Prompt**:
```
Player said '{exitIntent}'.
Available exits: {exitList}

Which exit matches? Output: 'EXIT:<direction>' or 'UNCLEAR'
```

Examples:
- "climb the rusty ladder" → "up"
- "go through the wooden door" → "east"

### Exit Linking

Initial generation uses `"PLACEHOLDER"` for targetIds. `ExitLinker` replaces these in a second pass:

1. Generate adjacent chunk/space in direction
2. Update exit targetId to actual generated ID
3. Create reciprocal exit in target space (n→s, up→down, "ladder"→"descend")

### Conditional Exits

Exits can require skill checks or items:

**Skill Check**:
```kotlin
ExitData(
    targetId = "space789",
    description = "narrow ledge",
    conditions = listOf(
        Condition.SkillCheck("Agility", difficulty = 15)
    )
)
```

**Item Required**:
```kotlin
ExitData(
    targetId = "space456",
    description = "locked door",
    conditions = listOf(
        Condition.ItemRequired("key_item")
    )
)
```

### Hidden Exits

20% of exits are hidden, requiring Perception checks:

```kotlin
ExitData(
    targetId = "secret_room",
    description = "concealed passage",
    hidden = true,
    hiddenDifficulty = 15  // Perception DC
)
```

`getVisibleExits()` filters based on player's Perception skill.

### Movement Costs

`MovementCostCalculator` integrates terrain with combat turn queue:

- **NORMAL**: 1 tick
- **DIFFICULT**: 2 ticks + Agility check (DC 10, fail = 1d6 damage)
- **IMPASSABLE**: Movement fails

**Skill Modifiers**: Athletics skill level 10+ reduces difficult terrain cost by 1 tick.

## Content Placement

### Theme Registry

`ThemeRegistry` maps biome themes to content rules:

```kotlin
data class ThemeProfile(
    val traps: List<String>,
    val resources: List<String>,
    val mobArchetypes: List<String>,
    val ambiance: String
)
```

**Predefined Themes**:
- **Dark Forest**: traps=[bear trap, pit], resources=[wood, herbs], mobs=[wolf, bandit]
- **Magma Cave**: traps=[lava pool, collapsing floor], resources=[obsidian, sulfur], mobs=[fire elemental, magma worm]
- **Ancient Crypt**: traps=[poison dart, cursed rune], resources=[bone, arcane dust], mobs=[skeleton, wraith]
- **Frozen Wasteland**: traps=[ice trap, avalanche], resources=[ice crystal, frozen herbs], mobs=[ice elemental, yeti]
- **Abandoned Castle**: traps=[spike trap, portcullis], resources=[iron, tapestry], mobs=[ghost, knight]
- **Swamp**: traps=[quicksand, poison gas], resources=[swamp moss, fish], mobs=[troll, giant frog]
- **Desert Ruins**: traps=[sandstorm, scorpion nest], resources=[sand glass, cactus], mobs=[mummy, sand worm]
- **Underground Lake**: traps=[drowning pool, slippery rocks], resources=[fish, algae], mobs=[sea serpent, water elemental]

### Trap Generation

`TrapGenerator` creates theme-appropriate traps:

- **Probability**: ~15% base chance per space (higher in difficult areas)
- **Difficulty Scaling**: `difficulty + variance(-2 to +2)`
- **High Difficulty Bonus**: Second trap if difficulty > 10
- **LLM Descriptions**: Optional vivid trap descriptions

### Resource Generation

`ResourceGenerator` ties to ItemRepository for loot integration:

- **Probability**: ~5% base chance per space
- **Quantity Scaling**: `baseQuantity + difficulty/5`
- **Respawn Logic**: `respawnTime = 100 + difficulty * 10` (deep dungeons renewable)
- **Template Mapping**: 30+ resource name → templateId mappings

### Mob Spawning

`MobSpawner` creates NPCs with theme-based generation:

- **Count Formula**: `mobDensity * spaceSize`
- **LLM Generation**: Diverse mob variety with JSON parsing
- **Fallback**: Deterministic generation when LLM unavailable
- **Stat Scaling**: `8 + difficulty/2`, clamped to 3-20
- **Health Formula**: `difficulty * 10 + variance`
- **Gold Formula**: `difficulty * 5 + variance`
- **Loot Table**: Format `{theme}_{difficulty}`

### Loot Table Generation

`LootTableGenerator` creates procedural loot tables:

- **Theme Filtering**: Items matching theme keywords (e.g., "obsidian" for magma cave)
- **Rarity Distribution**: COMMON 50%, UNCOMMON 30%, RARE 15%, EPIC 4%, LEGENDARY 1%
- **Quality Scaling**: Higher difficulty = better quality (+0 to +3 modifier)
- **Guaranteed Drops**: 0/1/2 for common/elite/boss mobs
- **Max Drops**: 2/3/4 scaling with difficulty
- **Gold Range**: `10*difficulty..50*difficulty`

### Space Population

`SpacePopulator` orchestrates all content:

```kotlin
fun populate(
    space: SpacePropertiesComponent,
    theme: String,
    difficulty: Int,
    mobDensity: Double
): SpacePropertiesComponent
```

**Populates**:
- Traps (probabilistic, theme-based)
- Resources (probabilistic, theme-based)
- Mobs (density-based count)

Mob density is now mapped via `MobSpawnTuning` so standard rooms average 1-3 hostiles:
- densities `< 0.1` spawn none
- `~0.3` spawns 1, `~0.5` spawns 2, `>=0.8` spawns 3
- Very large spaces can stretch to 4-5 mobs, but caps keep encounters readable

```kotlin
fun repopulate(
    space: SpacePropertiesComponent,
    theme: String,
    difficulty: Int,
    mobDensity: Double
): SpacePropertiesComponent
```

**Repopulates**:
- Mobs only (preserves traps, resources, state flags)
- Used on game restart for murder-hobo gameplay

## State Management

### State Flags

Player-initiated world changes persist via boolean flags:

```kotlin
stateFlags = mapOf(
    "boulder_moved" to true,
    "bridge_repaired" to true,
    "chest_opened" to true
)
```

`StateChangeHandler` applies changes immutably:

```kotlin
sealed class WorldAction {
    data class DestroyObstacle(val flag: String) : WorldAction()
    data class TriggerTrap(val trapId: String) : WorldAction()
    data class HarvestResource(val nodeId: String) : WorldAction()
    data class PlaceItem(val item: ItemInstance) : WorldAction()
    data class RemoveItem(val itemId: String) : WorldAction()
    data class UnlockExit(val exitDirection: String) : WorldAction()
    data class SetFlag(val flag: String, val value: Boolean) : WorldAction()
}
```

### Description Regeneration

Descriptions cached until state flags change:

```kotlin
fun shouldRegenDescription(
    oldFlags: Map<String, Boolean>,
    newFlags: Map<String, Boolean>
): Boolean
```

**LLM Regeneration Prompt**:
```
Regenerate room description.
Original: '{description}'
State changes: {changedFlags}
Lore context: {lore}

Output: updated description (2-4 sentences).
```

Temperature: 0.7 (balance consistency with variety)

### Persistence

`WorldPersistence` handles save/load:

#### Full Save
```kotlin
fun saveWorldState(
    worldId: String,
    playerState: PlayerState
): Result<Unit>
```

Saves:
- World seed and global lore
- All loaded chunks (from GenerationCache)
- All space properties
- Player state and navigation

#### Incremental Save
```kotlin
fun saveSpace(
    space: SpacePropertiesComponent,
    chunkId: String
): Result<Unit>
```

Autosave after each move (performance balance).

#### Load with Prefetch
```kotlin
fun loadWorldState(
    saveName: String
): Result<Pair<String, PlayerState>>
```

Loads:
- World seed and global lore
- Player state
- Current space + adjacent spaces (reduces first-move latency)

### Respawn System

`RespawnManager` handles mob regeneration on game restart:

```kotlin
fun respawnWorld(worldId: String): Result<Unit>
```

**Preserves**:
- State flags (player changes)
- Items dropped (corpses, loot)
- Traps (triggered state)
- Resources (harvested nodes)

**Regenerates**:
- Mobs (fresh entity lists from theme/density)

**Enables**:
- Murder-hobo gameplay (dungeons repopulate)
- Replayability (same world, new challenges)

### Autosave

`AutosaveManager` prevents data loss:

- **Move-based**: Every 5 moves
- **Time-based**: Every 2 minutes
- **Async**: Kotlin coroutines (non-blocking)
- **Cancelable**: Stops on game exit

## Database Schema

### world_seed
```sql
CREATE TABLE world_seed (
    id INTEGER PRIMARY KEY,
    seed_string TEXT NOT NULL,
    global_lore TEXT NOT NULL
);
```

Singleton pattern (id=1).

### world_chunks
```sql
CREATE TABLE world_chunks (
    id TEXT PRIMARY KEY,
    level TEXT NOT NULL,
    parent_id TEXT,
    children TEXT NOT NULL,  -- JSON array
    lore TEXT NOT NULL,
    biome_theme TEXT NOT NULL,
    size_estimate INTEGER NOT NULL,
    mob_density REAL NOT NULL,
    difficulty_level INTEGER NOT NULL,
    adjacency TEXT NOT NULL DEFAULT '{}',  -- JSON map of direction -> chunk_id
    FOREIGN KEY (parent_id) REFERENCES world_chunks(id)
);
CREATE INDEX idx_chunks_parent ON world_chunks(parent_id);
CREATE INDEX idx_chunks_level ON world_chunks(level);
```

`adjacency` caches normalized direction → chunk ID pairs so `WorldChunkRepository.findAdjacent()` can resolve known neighbors without re-querying the generator.

### space_properties
```sql
CREATE TABLE space_properties (
    chunk_id TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    exits TEXT NOT NULL,  -- JSON map
    brightness INTEGER NOT NULL,
    terrain_type TEXT NOT NULL,
    traps TEXT NOT NULL,  -- JSON array
    resources TEXT NOT NULL,  -- JSON array
    entities TEXT NOT NULL,  -- JSON array
    items_dropped TEXT NOT NULL,  -- JSON array
    state_flags TEXT NOT NULL,  -- JSON map
    FOREIGN KEY (chunk_id) REFERENCES world_chunks(id)
);
CREATE INDEX idx_space_chunk ON space_properties(chunk_id);
```

**JSON Serialization**: kotlinx.serialization for type-safe nested data.

## V2 MVP: Deep Dungeon

### Initialization

`DungeonInitializer` creates the starting hierarchy:

```kotlin
fun initializeDeepDungeon(seed: String): Result<String>
```

**Structure**:
- **WORLD**: "Ancient Abyss Dungeon" with global lore
- **3 REGIONS**:
  - Upper Depths (floors 1-10, difficulty 5)
  - Mid Depths (floors 11-50, difficulty 12)
  - Lower Depths (floors 51-100+, difficulty 18)
- **Pre-generated**: First ZONE → SUBZONE → SPACE (player start at top)

Returns starting space ID for player spawn.

### Progression

- **Top-to-bottom**: Player descends deeper
- **Difficulty scaling**: Deeper levels have harder mobs, better loot
- **Mob respawn**: Dungeons repopulate on restart
- **100+ floors possible**: Infinite generation on-demand

## Integration with Existing Systems

### Skills System

- **Perception**: Detect hidden exits, spot traps
- **Agility**: Navigate difficult terrain, avoid trap damage
- **Athletics**: Reduce movement costs on difficult terrain
- **Stealth**: Avoid triggering traps (future)

### Combat System

- **Turn Queue**: Movement costs integrate (difficult terrain = fewer player turns)
- **Mob Stats**: Scale with difficulty level
- **Loot Tables**: Theme-based drops from MobSpawner

### Items System

- **Resource Nodes**: Generate ItemInstances from ItemRepository
- **Loot Generation**: LootTableGenerator queries ItemRepository
- **Item Conditions**: Exits can require specific items

### Social System

- **NPC Generation**: MobSpawner creates entities with personalities
- **Disposition**: Future integration (faction-based reactions)

## Testing Strategy

### Unit Tests (Chunks 1-6)

- **Chunk 1**: 106 tests (components, data classes)
- **Chunk 2**: 78 tests (database, repositories)
- **Chunk 3**: 65+ tests (generation pipeline, LLM integration)
- **Chunk 4**: 92 tests (exit resolution, navigation)
- **Chunk 5**: 112 tests (content placement, theme matching)
- **Chunk 6**: 115 tests (state changes, persistence)

**Total**: ~576 unit/integration tests

### Integration Tests (Chunk 7)

- **WorldGenerationIntegrationTest**: Hierarchy generation, exit linking, content population, persistence
- **WorldExplorationTest** (bot): Full playthrough with 20+ moves, combat, death, respawn

### Testing Focus

- **Behavior and contracts** (not line coverage)
- **Probabilistic mechanics** (trap/resource generation over 100 trials)
- **LLM integration** (mocked for determinism)
- **Serialization roundtrips** (save/load data integrity)
- **Edge cases** (missing parents, orphaned chunks, corrupted DB)

## V3: Graph-Based Navigation

**Status**: Chunks 1-4 complete. Graph topology generation with validation.

V3 upgrades the World Generation system from V2's exit-based navigation to pre-generated graph topology. This enables:
- Structured layouts (Grid/BSP/FloodFill algorithms)
- Hidden exits (15-25% edges, revealed via Perception)
- Infinite exploration (Frontier nodes cascade to new chunks)
- Player agency (Tunnel skill creates edges, explosives remove edges)
- Quality assurance (Post-generation validation)

### Graph Components

#### GraphNodeComponent

Pre-generated topology nodes defining connectivity before content generation.

```kotlin
data class GraphNodeComponent(
    val id: String,
    val position: Pair<Int, Int>?,  // For Grid/BSP layouts
    val type: NodeType,              // HUB, LINEAR, BRANCHING, DEAD_END, BOSS, FRONTIER, QUESTABLE
    val neighbors: List<EdgeData>,
    val chunkId: String
) : Component
```

**Node Types**:
- **Hub**: Safe zones, towns, high connectivity (4+ edges)
- **Linear**: Corridors, 2 edges (in/out)
- **Branching**: Choice points, 3+ edges
- **DeadEnd**: Treasure/traps, 1 edge
- **Boss**: Major encounters, farthest from entry
- **Frontier**: Boundary nodes, trigger cascade to new chunks
- **Questable**: Future quest system integration

#### EdgeData

```kotlin
data class EdgeData(
    val targetId: String,
    val direction: String,
    val hidden: Boolean = false,
    val conditions: List<Condition> = emptyList()
)
```

### Graph Generation

`GraphGenerator` creates topology using one of three algorithms:

1. **Grid Layout**: Regular NxM arrangement for dungeons
   - Parameters: `width`, `height`
   - Use case: Structured dungeons, maze-like areas

2. **BSP Layout**: Binary space partitioning for buildings
   - Parameters: `minRoomSize`, `maxDepth`
   - Use case: Castles, temples, indoor structures

3. **FloodFill Layout**: Organic growth for caves
   - Parameters: `nodeCount`, `density` (0.0-1.0)
   - Use case: Natural caves, organic environments

**Generation Steps**:
1. Generate node positions based on layout algorithm
2. Connect nodes via Kruskal MST (ensures connectivity)
3. Add 40-50% extra edges for loops (promotes avg degree 3.0-3.5 for engaging navigation)
4. Assign node types (1-2 hubs, 1 boss, 2+ frontiers, 20% dead-ends)
5. Mark 15-25% edges as hidden with Perception difficulty (10-30)
6. Validate graph structure

### Graph Validation

`GraphValidator` enforces quality requirements post-generation. All generated graphs must pass validation before being saved to the database.

#### Validation Criteria

**1. Full Connectivity**
- All nodes must be reachable from the first node (entry point)
- Uses BFS traversal to verify reachability
- **Why**: Prevents isolated nodes that break navigation
- **Failure**: "Graph not fully connected - some nodes are unreachable"

**2. Loop Existence**
- Graph must contain at least one cycle
- Uses DFS with recursion stack to detect back edges
- **Why**: Loops provide exploration choice and prevent linear progression
- **Failure**: "No loops found - graph is a tree"

**3. Minimum Average Degree**
- Average edges per node must be >= 3.0
- Calculated as: `total_edges / node_count`
- **Why**: Ensures sufficient connectivity for interesting exploration
- **Failure**: "Average degree X.XX < 3.0 - insufficient connectivity"

**4. Minimum Frontier Count**
- At least 2 frontier nodes required
- Counts nodes with `type == NodeType.Frontier`
- **Why**: Enables robust infinite expansion capability
- **Failure**: "Frontier count X < 2 - insufficient expansion points"

#### ValidationResult

```kotlin
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Failure(val reasons: List<String>) : ValidationResult()
}
```

**Usage**:
```kotlin
val validator = GraphValidator()
val validation = validator.validate(nodes)

when (validation) {
    is ValidationResult.Success -> {
        // Save graph to database
    }
    is ValidationResult.Failure -> {
        // Log failures, retry generation, or throw exception
        throw GenerationException("Graph validation failed: ${validation.reasons}")
    }
}
```

#### Validation Flow

```
GraphGenerator.generate()
    ↓
GraphValidator.validate(nodes)
    ↓
    ├─ Check 1: isFullyConnected() [BFS]
    ├─ Check 2: hasLoop() [DFS cycle detection]
    ├─ Check 3: avgDegree() [>= 3.0]
    └─ Check 4: frontierCount() [>= 2]
    ↓
ValidationResult.Success or ValidationResult.Failure
    ↓
Save to database or retry generation
```

### Graph Integration with V2

Graph generation occurs at the **SUBZONE level** (V2 hierarchy):
- V2 creates hierarchical chunks (WORLD → REGION → ZONE → SUBZONE)
- V3 generates graph topology when SUBZONE is created
- Graph nodes become SPACE entities (V2 atomic rooms)
- SpaceProperties filled lazily on first player visit

**Benefits**:
- Reduced LLM calls (topology pre-computed, content generated on-demand)
- Consistent structure (validated graphs ensure quality)
- Dynamic modification (edges can be added/removed at runtime)
- Infinite expansion (frontier nodes trigger new chunk generation)

### V3 Generation Flow with Lazy-Fill (Chunk 5: Generation Layer Complete, Integration Pending)

WorldGenerator now supports two generation modes:

#### V2 Mode (Immediate Content Generation)
```kotlin
val generator = WorldGenerator(llmClient, loreEngine) // No graph components
val result = generator.generateChunk(context)
// Returns ChunkGenerationResult with empty graphNodes list
// Spaces generated via generateSpace() with full LLM content
```

#### V3 Mode (Graph-First with Lazy-Fill)
```kotlin
val graphGenerator = GraphGenerator(rng, difficultyLevel)
val graphValidator = GraphValidator()
val generator = WorldGenerator(llmClient, loreEngine, graphGenerator, graphValidator)

// 1. Generate SUBZONE chunk
val result = generator.generateChunk(context)
// result.graphNodes contains validated graph topology

// 2. Generate SpaceProperties stubs for each graph node
for (node in result.graphNodes) {
    val stub = generator.generateSpaceStub(node, result.chunk)
    // stub.description is empty - lazy-fill
    // stub.exits come from node.neighbors (not LLM)
}

// 3. Fill content on-demand when player enters
val filled = generator.fillSpaceContent(currentSpace, graphNode, chunk)
// LLM generates description based on node type and neighbors
// Brightness and terrain set based on node type
```

#### Lazy-Fill Benefits

**Performance**:
- Initial generation: ~$0.00008 per chunk (no space LLM calls)
- On-demand fill: ~$0.00012 per space (only when visited)
- 100-space SUBZONE: $0.00008 (V3) vs $0.01200 (V2) if visiting all spaces
- 10% exploration: $0.00128 (V3) vs $0.01200 (V2) = 90% cost savings

**Gameplay**:
- Instant chunk generation (no waiting for 100 LLM calls)
- Descriptions incorporate node type context (Hub = "gathering point", DeadEnd = "dead-end chamber")
- Consistent structure (validated graph prevents navigation bugs)
- Player-driven content (only generate what's explored)

#### Node Type-Based Generation

`fillSpaceContent()` uses node type to guide LLM generation:

| Node Type | Description Context | Brightness | Terrain |
|-----------|---------------------|------------|---------|
| Hub | "safe zone or gathering point" | 70 | NORMAL |
| Linear | "corridor or passage" | 40 | NORMAL |
| Branching | "junction or crossroads" | 50 | NORMAL |
| DeadEnd | "dead-end chamber" | 30 | DIFFICULT |
| Boss | "ominous boss chamber" | 60 | NORMAL |
| Frontier | "unexplored frontier" | 20 | DIFFICULT |
| Questable | "significant quest location" | 55 | NORMAL |

**LLM Prompt Template**:
```
Theme: {chunk.biomeTheme}
Lore: {chunk.lore}
Node Type: {nodeTypeDescription}
Exits: {exitDirections}

Generate a vivid 2-3 sentence description for this space.
Return ONLY the description text, no JSON, no formatting.
```

#### Migration Path

WorldGenerator supports both modes simultaneously:
- V2 callers: Continue using `generateChunk()` → empty graph nodes
- V3 callers: Pass graph components, receive topology, use stubs + lazy-fill
- Existing V2 worlds: Compatible (graph nodes optional)
- New V3 worlds: Graph-first generation at SUBZONE level

#### Implementation Status (Chunk 5)

**✅ Generation Layer Complete** (WorldGenerator.kt lines 48-360):
- `generateChunk()` generates graph topology at SUBZONE level with validation
- `generateGraphTopology()` creates validated graphs using GraphGenerator
- `generateSpaceStub()` creates SpaceProperties with empty descriptions
- `fillSpaceContent()` performs on-demand LLM generation on first visit
- `generateNodeDescription()` uses node type and neighbors for contextual descriptions
- `determineNodeProperties()` sets brightness/terrain based on node type

**✅ Adapter Layer Complete** (Option B: Parallel Systems):
- `GraphToRoomAdapter.kt` (193 lines, 16 tests) converts V3 components to V2 Room format
- `toRoom()` - Single space conversion with name extraction, trait building, exit mapping
- `toRooms()` - Batch conversion for chunk-level operations
- Cardinal direction mapping, non-cardinal exits stored in properties map
- Trait generation from brightness, terrain, node type, features
- **Design Philosophy**: KISS principle - additive, non-disruptive, gradual ECS migration path
- **Status**: Ready for movement handler integration

**❌ Integration Pending**:
- Movement handlers still use V2 Room-based system (adapter not yet integrated)
- `fillSpaceContent()` not yet called from game loop
- Frontier traversal not implemented (cascade to new chunks)
- See TODO.md for integration plan

### File Locations

- **Core**: `GraphNodeComponent.kt`, `world/GraphTypes.kt`
- **Reasoning**: `worldgen/GraphGenerator.kt`, `worldgen/GraphValidator.kt`, `worldgen/GraphLayout.kt`, `world/GraphToRoomAdapter.kt`
- **Memory**: `GraphNodeRepository.kt`, `SQLiteGraphNodeRepository.kt`

### Testing

- **GraphGeneratorTest**: 31 tests (layouts, MST, loops, node types, hidden edges)
- **GraphLayoutTest**: 25 tests (algorithm-specific validation)
- **GraphValidatorTest**: 20 tests (connectivity, loops, degree, frontiers)
- **GraphToRoomAdapterTest**: 16 tests (name extraction, direction mapping, trait generation, batch conversion)

**Total V3 Tests**: 92 tests passing (100% pass rate)

## V3 Roadmap

Future V3 enhancements (Chunks 5-11):

- **Integration with World System**: Lazy-fill content on node entry
- **Hidden Exit Revelation**: Perception checks reveal hidden edges
- **Dynamic Edge Modification**: Tunnel skill adds edges, explosives remove edges
- **Breakout Edges**: Every 3-5 chunks, add edges to new biomes
- **Exit Resolution Update**: Use GraphNodeComponent as source of truth
- **Comprehensive Testing**: Bot tests for full exploration flow

Future V4 enhancements (post-V3):

- **Open World**: Horizontal regions, multiple dungeons
- **Weather System**: Dynamic weather affecting visibility/terrain
- **Minimap**: Visual representation of explored spaces
- **Multiplayer**: Shared world state, player encounters
- **Faction System**: Region-based politics, NPC allegiances
- **Dynamic Events**: World-changing events (earthquakes, invasions)
- **Persistent Modifications**: Player-built structures, landmarks
- **Advanced AI**: NPC pathfinding, faction wars

## Performance Considerations

### LLM Costs

- **Model**: GPT-4o-mini (~$0.15 per 1M input tokens)
- **Chunk Generation**: ~500 tokens per chunk (~$0.00008 per chunk)
- **Space Generation**: ~800 tokens per space (~$0.00012 per space)
- **Description Regen**: ~400 tokens (~$0.00006 per regen)

**Mitigation**:
- Description caching (regen only on flag changes)
- Generation caching (avoid duplicate generations)
- Fallback generation (deterministic when LLM unavailable)

### Database Performance

- **Indexes**: parent_id, chunk_id, level for fast queries
- **JSON Fields**: Flexible but slightly slower (acceptable trade-off)
- **Incremental Saves**: Autosave only changed spaces (not full world)
- **Prefetch**: Load adjacent spaces on navigation (smooth movement)

### Memory Usage

- **Generation Cache**: LRU eviction at 1000 chunks (~10MB)
- **Loaded Chunks**: Only active region in memory
- **Lazy Generation**: Chunks generated on-demand (no pre-gen overhead)

## Troubleshooting

### Common Issues

**Exit resolution fails**:
- Check visible exits with `getVisibleExits()` (hidden exits need Perception)
- Verify conditions are met (skill checks, item requirements)
- Try exact cardinal directions (n/s/e/w) before natural language

**Mobs not respawning**:
- Ensure `respawnWorld()` called on game restart
- Check mobDensity > 0 in parent chunk
- Verify theme profile exists in ThemeRegistry

**State changes not persisting**:
- Check autosave triggers (5 moves or 2 minutes)
- Verify WorldAction applied via StateChangeHandler
- Inspect space_properties.state_flags in DB

**LLM generation errors**:
- Check API key and rate limits
- Use fallback generation (deterministic, no LLM)
- Validate JSON parsing (malformed LLM output)

## Development Guidelines

### Adding New Themes

1. Add profile to `ThemeRegistry`:
```kotlin
val myTheme = ThemeProfile(
    traps = listOf("trap1", "trap2"),
    resources = listOf("resource1", "resource2"),
    mobArchetypes = listOf("mob1", "mob2"),
    ambiance = "descriptive atmosphere"
)
```

2. Tag items in ItemRepository with theme keywords

3. Generate loot table: `LootTableGenerator.generateForTheme("my_theme", difficulty)`

### Adding New WorldActions

1. Add sealed class case to `WorldAction`:
```kotlin
data class MyAction(val param: String) : WorldAction()
```

2. Handle in `StateChangeHandler.applyChange()`:
```kotlin
when (action) {
    is WorldAction.MyAction -> {
        // Apply change logic
        space.copy(stateFlags = space.stateFlags + (action.param to true))
    }
}
```

3. Update `shouldRegenDescription()` if needed

### Extending Exit Conditions

Add new `Condition` sealed class cases:
```kotlin
sealed class Condition {
    data class SkillCheck(val skill: String, val difficulty: Int) : Condition()
    data class ItemRequired(val itemTag: String) : Condition()
    data class QuestRequired(val questId: String) : Condition()  // New
}
```

Update `meetsConditions()` in `ExitData`.

## File Locations

### Core Module
- `Component.kt` - ComponentType enum
- `world/ChunkLevel.kt`, `TerrainType.kt`, `ExitData.kt`, `TrapData.kt`, `ResourceNode.kt`
- `world/NavigationState.kt`
- `repository/WorldChunkRepository.kt`, `SpacePropertiesRepository.kt`, `WorldSeedRepository.kt`

### Reasoning Module
- `world/ChunkIdGenerator.kt`, `WorldGenerator.kt`, `LoreInheritanceEngine.kt`
- `world/ExitResolver.kt`, `ExitLinker.kt`, `MovementCostCalculator.kt`
- `world/ThemeRegistry.kt`, `TrapGenerator.kt`, `ResourceGenerator.kt`, `MobSpawner.kt`, `LootTableGenerator.kt`, `SpacePopulator.kt`
- `world/StateChangeHandler.kt`, `RespawnManager.kt`

### Memory Module
- `world/WorldDatabase.kt`
- `world/SQLiteWorldChunkRepository.kt`, `SQLiteSpacePropertiesRepository.kt`, `SQLiteWorldSeedRepository.kt`
- `world/GenerationCache.kt`, `WorldPersistence.kt`, `AutosaveManager.kt`

### Perception Module
- `Intent.Scout`, `Intent.Travel` (to be added in Chunk 7)

### App Module
- `handlers/WorldHandlers.kt` (to be added in Chunk 7)

### Client Module
- `handlers/ClientWorldHandlers.kt` (to be added in Chunk 7)

### Testbot Module
- `scenarios/WorldExplorationTest.kt` (to be added in Chunk 7)

---

**Last Updated**: 2025-01-29
**Status**: Chunks 1-6 complete, Chunk 7 in progress
**Total Lines**: ~4500 implementation + ~576 tests
