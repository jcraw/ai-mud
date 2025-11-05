# Feature Plan: World System V3 (Graph-Based Navigation)

## Overview
Upgrade the World Generation system from V2 to V3 by adding pre-generated graph topology for structured navigation, dynamic edge modification, and infinite exploration with frontier nodes. This builds on V2's hierarchical chunk system (World > Region > Zone > Subzone > Space) by adding a graph layer that defines connectivity before content generation.

## Key V3 Additions
- **GraphNodeComponent**: Pre-generated topology with typed nodes (HUB, LINEAR, BRANCHING, DEAD_END, BOSS, FRONTIER, QUESTABLE)
- **Structured layouts**: Grid/BSP/flood-fill algorithms + MST for connectivity + 20% extra edges for loops
- **Hidden exits**: 15-25% edges hidden, revealed via Perception (diff 10-30)
- **Infinite extension**: Frontier nodes cascade to new chunks; breakout edges to new biomes every 3-5 chunks
- **Player agency**: Skills/items dynamically add/prune edges (e.g., Tunnel skill, explosives)
- **Validation**: Post-gen checks for min 1 loop, avg 3.5 exits/node, reachability, 2+ frontiers

## Dependencies
- V2 World System must be fully implemented (WorldChunkComponent, SpacePropertiesComponent, hierarchical generation)
- Skills system (for Perception checks on hidden exits, Tunnel skill for edge modification)
- Persistence system (SQLite repositories)

---

## TODO: Implementation Chunks

### Chunk 1: GraphNodeComponent and Data Structures (3-4h)
**Description**: Add core ECS component and data classes for graph topology.

**Reasoning**: Start with data model to establish contracts. GraphNodeComponent needs to be minimal yet extensible. NodeType covers all structural patterns (HUB for safe zones, FRONTIER for expansion). EdgeData separates graph structure from world content (SpaceProperties), allowing pre-generation of topology before LLM content.

**Affected Modules/Files**:
- `:core/src/main/kotlin/com/jcraw/mud/core/ComponentType.kt` - Add `GRAPH_NODE` enum value
- `:core/src/main/kotlin/com/jcraw/mud/core/components/` - Create `GraphNodeComponent.kt`
- `:core/src/main/kotlin/com/jcraw/mud/core/world/` - Create `GraphTypes.kt` for NodeType, EdgeData

**Technical Decisions**:
- `NodeType` as sealed class (not enum) - allows future extension with type-specific data
- `EdgeData` with `hidden: Boolean` flag - simpler than separate hidden edge list
- `position: Pair<Int, Int>?` nullable - not all layouts need coordinates (e.g., organic caves)
- `neighbors: List<EdgeData>` not `Set` - order matters for cardinal directions

**Key Classes**:
```kotlin
// GraphTypes.kt
sealed class NodeType {
    object Hub : NodeType()           // Safe zones, towns
    object Linear : NodeType()        // Corridor chains
    object Branching : NodeType()     // Choice points, loops
    object DeadEnd : NodeType()       // Treasure, traps
    object Boss : NodeType()          // Boss rooms
    object Frontier : NodeType()      // Expandable to new chunks
    object Questable : NodeType()     // For future quest system
}

data class EdgeData(
    val targetId: String,
    val direction: String,
    val hidden: Boolean = false,
    val conditions: List<Condition> = emptyList()
)

// GraphNodeComponent.kt
data class GraphNodeComponent(
    val id: String,
    val position: Pair<Int, Int>? = null,
    val type: NodeType,
    val neighbors: List<EdgeData> = emptyList(),
    override val componentType: ComponentType = ComponentType.GRAPH_NODE
) : Component
```

**Testing**:
- Unit tests for component immutability (copy operations)
- Test EdgeData with/without conditions, hidden flags
- Test NodeType sealed class exhaustiveness in when expressions
- Rationale: Components are core contracts; tests ensure they don't break when extended

**Documentation**:
- Add GraphNodeComponent to Architecture.md component list
- Inline KDoc for NodeType variants explaining usage

---

### Chunk 2: Database Schema and GraphNodeRepository (3-4h)
**Description**: Add `graph_nodes` table and repository interface/implementation.

**Reasoning**: Persistence before generation logic ensures we can save/load graphs. Schema stores topology (nodes, edges) separately from content (SpaceProperties). JSON for neighbors allows flexible edge data without JOIN complexity. Repositories abstract DB access, keeping :reasoning logic testable.

**Affected Modules/Files**:
- `:persistence/src/main/kotlin/com/jcraw/mud/persistence/migrations/` - Add migration script `V4__graph_nodes.sql`
- `:core/src/main/kotlin/com/jcraw/mud/core/repository/` - Create `GraphNodeRepository.kt` interface
- `:persistence/src/main/kotlin/com/jcraw/mud/persistence/` - Create `SQLiteGraphNodeRepository.kt`

**Technical Decisions**:
- Store `neighbors` as JSON array - simpler than separate edges table, matches hierarchical nature
- `chunk_id` links graph to world chunks - one graph per chunk (SUBZONE level typically)
- `position_x/position_y` nullable columns - support both grid and organic layouts
- Index on `chunk_id` - most queries are "get all nodes in chunk X"
- Use kotlinx.serialization for JSON (already in project) - consistent with other persistence

**Schema**:
```sql
-- V4__graph_nodes.sql
CREATE TABLE graph_nodes (
    id TEXT PRIMARY KEY,
    chunk_id TEXT NOT NULL,
    position_x INTEGER,
    position_y INTEGER,
    type TEXT NOT NULL,
    neighbors TEXT NOT NULL,  -- JSON array of EdgeData
    FOREIGN KEY (chunk_id) REFERENCES world_chunks(id)
);

CREATE INDEX idx_graph_nodes_chunk ON graph_nodes(chunk_id);
```

**Repository Interface**:
```kotlin
interface GraphNodeRepository {
    suspend fun save(node: GraphNodeComponent, chunkId: String)
    suspend fun findById(id: String): GraphNodeComponent?
    suspend fun findByChunk(chunkId: String): List<GraphNodeComponent>
    suspend fun update(node: GraphNodeComponent)
    suspend fun delete(id: String)
    suspend fun addEdge(fromId: String, edge: EdgeData)
    suspend fun removeEdge(fromId: String, targetId: String)
}
```

**Testing**:
- Unit tests for SQLiteGraphNodeRepository CRUD operations
- Test JSON serialization/deserialization of EdgeData list
- Test edge add/remove updates neighbors correctly
- Integration test: Save graph, load graph, verify structure
- Rationale: Persistence bugs manifest late; tests catch JSON issues early

**Documentation**:
- Update ARCHITECTURE.md database schema section
- Add migration notes to persistence README if exists

---

### Chunk 3: Graph Generation Algorithms (5-6h)
**Description**: Implement `GraphGenerator` with layout algorithms (grid, BSP, flood-fill) and MST-based connectivity.

**Reasoning**: This is the core of V3. Three algorithms give variety: grid for dungeons, BSP for buildings, flood-fill for organic caves. Kruskal MST ensures connectivity, extra edges add loops (exploration choice). Node type assignment creates structure (mandatory hub/boss, 20% dead-ends). Deterministic RNG seeding makes testing/debugging feasible.

**Affected Modules/Files**:
- `:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/worldgen/` - Create `GraphGenerator.kt`
- `:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/worldgen/` - Create `GraphLayout.kt` (sealed class for algorithms)
- `:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/worldgen/` - Create `GraphValidator.kt`

**Technical Decisions**:
- Sealed class `GraphLayout` for algorithm selection - extensible, type-safe
- Use `kotlin.random.Random` with seed from WorldChunkComponent - reproducible per chunk
- Kruskal MST via union-find - O(E log E), efficient for small graphs (5-100 nodes)
- Node type assignment rules:
  - 1-2 Hubs (first nodes or central by position)
  - 1 Boss (farthest from entry or center)
  - 20% Dead-Ends (degree-1 nodes after MST)
  - 2+ Frontiers (boundary nodes)
  - Rest: Linear/Branching by degree
- Hidden edges: 15-25% random selection, higher difficulty for deeper chunks

**Key Classes**:
```kotlin
sealed class GraphLayout {
    data class Grid(val width: Int, val height: Int) : GraphLayout()
    data class BSP(val minRoomSize: Int, val maxDepth: Int) : GraphLayout()
    data class FloodFill(val nodeCount: Int, val density: Double) : GraphLayout()
}

class GraphGenerator(
    private val rng: Random,
    private val difficultyLevel: Int
) {
    fun generate(
        chunkId: String,
        sizeEstimate: Int,
        layout: GraphLayout
    ): List<GraphNodeComponent> {
        // 1. Generate positions based on layout
        // 2. Connect via MST (Kruskal)
        // 3. Add 20% extra edges for loops
        // 4. Assign node types
        // 5. Mark 15-25% edges as hidden with Perception difficulty
        // 6. Return nodes with EdgeData
    }

    private fun kruskalMST(nodes: List<GraphNodeComponent>): List<Pair<String, String>>
    private fun assignNodeTypes(nodes: List<GraphNodeComponent>, edges: List<Pair<String, String>>): List<GraphNodeComponent>
    private fun markHiddenEdges(nodes: List<GraphNodeComponent>): List<GraphNodeComponent>
}
```

**Testing**:
- Unit test each layout algorithm produces expected node count
- Test MST creates connected graph (no isolated nodes)
- Test extra edges create at least 1 loop
- Test node type assignment (1-2 hubs, 1 boss, 2+ frontiers)
- Property-based test: Generated graphs always connected, avg degree ~3.5
- Rationale: Graph topology bugs break navigation; property tests catch edge cases

**Documentation**:
- Add GraphGenerator to ARCHITECTURE.md reasoning module section
- Inline comments explaining MST/loop addition logic

---

### Chunk 4: Graph Validation System (3h)
**Description**: Implement post-generation validation checks for structure quality.

**Reasoning**: Validation catches generation bugs before content is created. Required checks: reachability (BFS from entry), loop existence (cycle detection), frontier count (infinite extension), avg degree (balanced exploration). Failures throw exceptions in dev, log warnings in prod.

**Affected Modules/Files**:
- `:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/worldgen/GraphValidator.kt` - Add validation methods

**Technical Decisions**:
- BFS for reachability - O(V+E), simple and fast
- Cycle detection via DFS - mark visited, detect back edges
- Avg degree calculation - sum edges / node count, check >= 3.0 threshold
- Frontier count - nodes with type Frontier, require >= 2
- Return `ValidationResult` sealed class (Success, Failure(reasons)) - allows retry logic

**Key Methods**:
```kotlin
class GraphValidator {
    fun validate(nodes: List<GraphNodeComponent>): ValidationResult {
        val issues = mutableListOf<String>()

        if (!isFullyConnected(nodes)) issues.add("Graph not fully connected")
        if (!hasLoop(nodes)) issues.add("No loops found")
        if (avgDegree(nodes) < 3.0) issues.add("Avg degree < 3.0")
        if (frontierCount(nodes) < 2) issues.add("Less than 2 frontier nodes")

        return if (issues.isEmpty()) ValidationResult.Success
               else ValidationResult.Failure(issues)
    }

    private fun isFullyConnected(nodes: List<GraphNodeComponent>): Boolean
    private fun hasLoop(nodes: List<GraphNodeComponent>): Boolean
    private fun avgDegree(nodes: List<GraphNodeComponent>): Double
    private fun frontierCount(nodes: List<GraphNodeComponent>): Int
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Failure(val reasons: List<String>) : ValidationResult()
}
```

**Testing**:
- Test valid graph passes all checks
- Test disconnected graph fails reachability
- Test tree (no loops) fails loop check
- Test low-degree graph fails avg degree check
- Test graph with 0-1 frontiers fails frontier check
- Rationale: Validation logic must be reliable; tests ensure no false positives/negatives

**Documentation**:
- Update WORLD_GENERATION.md with validation criteria
- Add troubleshooting section for failed validations

---

### Chunk 5: Integrate Graph Generation with World System (4-5h)
**Description**: Update WorldGenerator to pre-generate graph topology before content, lazy-fill SpaceProperties on entry.

**Reasoning**: This is the V3 generation flow change. Create graph on chunk generation (SUBZONE level), store in DB, then fill SpaceProperties only when player enters a node. Reduces LLM calls, speeds initial generation. Frontier nodes trigger cascade to new chunks.

**Affected Modules/Files**:
- `:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/WorldGenerator.kt` - Update generation flow
- `:app/src/main/kotlin/com/jcraw/app/handlers/MovementHandlers.kt` - Update travel to lazy-fill content

**Technical Decisions**:
- Graph generation at SUBZONE level (5-100 spaces) - matches chunk granularity
- Store graph immediately after validation - no orphan graphs
- SpaceProperties.description initially empty - fill on first visit via LLM
- Frontier traversal triggers cascade: Create child chunk, generate graph, link frontier->new hub
- Cache generated content in memory for session - avoid re-LLM same node

**Updated Flow**:
```kotlin
// WorldGenerator.kt
suspend fun generateChunk(parentChunk: WorldChunkComponent, level: ChunkLevel): WorldChunkComponent {
    val newChunk = createChunk(parentChunk, level)
    worldChunkRepo.save(newChunk)

    if (level == ChunkLevel.SUBZONE) {
        // NEW: Generate graph topology
        val graph = graphGenerator.generate(
            chunkId = newChunk.id,
            sizeEstimate = newChunk.sizeEstimate,
            layout = selectLayout(newChunk.biomeTheme)
        )

        val validation = graphValidator.validate(graph)
        if (validation is ValidationResult.Failure) {
            throw GenerationException("Graph validation failed: ${validation.reasons}")
        }

        graph.forEach { node -> graphNodeRepo.save(node, newChunk.id) }

        // Create SpaceProperties stubs (no descriptions yet)
        graph.forEach { node ->
            val spaceProps = SpacePropertiesComponent(
                description = "",  // Lazy-fill
                exits = node.neighbors.associate { it.direction to ExitData(it.targetId, it.conditions) },
                terrainType = TerrainType.NORMAL,
                // ... other defaults
            )
            spacePropertiesRepo.save(spaceProps, node.id)
        }
    }

    return newChunk
}

suspend fun fillSpaceContent(nodeId: String, chunkLore: String) {
    val node = graphNodeRepo.findById(nodeId) ?: return
    val spaceProps = spacePropertiesRepo.findById(nodeId) ?: return

    if (spaceProps.description.isEmpty()) {
        // LLM generates description using node type, neighbors, chunk lore
        val description = llm.generateSpaceDescription(node, chunkLore)
        val updated = spaceProps.copy(
            description = description,
            entities = placeEntities(node.type, chunkLore),
            resources = placeResources(node.type),
            traps = placeTraps(node.type)
        )
        spacePropertiesRepo.update(updated)
    }
}
```

**Testing**:
- Integration test: Generate SUBZONE chunk, verify graph exists in DB
- Test lazy-fill: Enter node, verify description generated once
- Test frontier cascade: Travel to frontier, verify new chunk created
- Test breakout edges: Every 3-5 chunks, verify new REGION created
- Rationale: Generation flow is complex; integration tests catch sequencing bugs

**Documentation**:
- Update WORLD_GENERATION.md generation flow section
- Add flowchart for graph-first generation

---

### Chunk 6: Hidden Exit Revelation via Perception (3h)
**Description**: Implement Perception checks to reveal hidden edges, update exit display to hide unrevealed exits.

**Reasoning**: V3 spec requires 15-25% hidden exits with Perception difficulty 10-30. Player must actively use Perception skill or Scout intent to reveal. Hidden state persists per-player in PlayerState. Difficulty scales with chunk depth.

**Affected Modules/Files**:
- `:core/src/main/kotlin/com/jcraw/mud/core/PlayerState.kt` - Add `revealedExits: Set<String>` field
- `:app/src/main/kotlin/com/jcraw/app/handlers/MovementHandlers.kt` - Update Scout handler
- `:app/src/main/kotlin/com/jcraw/app/handlers/SkillQuestHandlers.kt` - Update Perception usage

**Technical Decisions**:
- `revealedExits: Set<String>` stores edge IDs (fromId+targetId) - compact, fast lookup
- Perception check uses EdgeData.conditions hidden difficulty - auto-calculated during graph gen
- Scout intent (passive) vs explicit Perception skill use (active):
  - Scout: Auto-check vs avg of current room's hidden exits
  - Perception: Player-targeted, can specify direction, easier DC
- Display: Filter exits by `edge.hidden && !player.revealedExits.contains(edgeId)`

**Key Changes**:
```kotlin
// PlayerState.kt
data class PlayerState(
    // ... existing fields
    val revealedExits: Set<String> = emptySet()
)

// MovementHandlers.kt (Scout)
fun handleScout(direction: String, state: WorldState, player: PlayerState): Pair<WorldState, String> {
    val currentSpace = getCurrentSpace(state, player)
    val hiddenEdges = currentSpace.exits.filter { it.hidden && !player.revealedExits.contains(it.id) }

    if (hiddenEdges.isEmpty()) {
        return state to "You don't notice anything unusual."
    }

    val perceptionDC = hiddenEdges.map { it.conditions.difficulty }.average().toInt()
    val skillRoll = rollSkillCheck(player, "Perception", perceptionDC)

    if (skillRoll.success) {
        val revealed = hiddenEdges.first()
        val updatedPlayer = player.copy(revealedExits = player.revealedExits + revealed.id)
        val updatedState = state.updatePlayer(updatedPlayer)

        return updatedState to "You notice a hidden exit: ${revealed.direction}"
    } else {
        return state to "You search carefully but find nothing."
    }
}
```

**Testing**:
- Test Scout reveals hidden exit on success, updates revealedExits
- Test Scout fails on low Perception skill
- Test hidden exit stays hidden until revealed
- Test revealed exit persists across saves/loads
- Rationale: Hidden exits are a core V3 feature; tests ensure skill integration works

**Documentation**:
- Update GETTING_STARTED.md Scout command section
- Update SOCIAL_SYSTEM.md Perception skill with hidden exit mechanic

---

### Chunk 7: Dynamic Edge Modification (Player Agency) (4h)
**Description**: Allow skills/items to add/prune graph edges (Tunnel skill, explosives).

**Reasoning**: V3 spec: "Skills/items dynamically add/prune edges (e.g., 'Tunnel' skill carves new; explosives collapse)." This adds player creativity to exploration. New edges create shortcuts, removed edges add risk/puzzle. Persist changes to DB.

**Affected Modules/Files**:
- `:core/src/main/kotlin/com/jcraw/mud/core/repository/GraphNodeRepository.kt` - Already has addEdge/removeEdge
- `:app/src/main/kotlin/com/jcraw/app/handlers/SkillQuestHandlers.kt` - Add Tunnel skill handler
- `:app/src/main/kotlin/com/jcraw/app/handlers/ItemHandlers.kt` - Add explosive item handler

**Technical Decisions**:
- Tunnel skill: Creates new edge from current node to adjacent grid position (if layout supports)
  - Check: Skill level >= 20, costs stamina/focus
  - Creates bidirectional edge (tunnel is two-way)
  - Description: "newly carved tunnel"
- Explosives: Removes edge (collapses passage)
  - Validation: Don't remove if it disconnects graph (run BFS after removal)
  - Creates state flag: "collapsed_exit_{direction}"
- Edge modifications update GraphNodeComponent, persist via repo.update()

**Key Handlers**:
```kotlin
// SkillQuestHandlers.kt
fun handleTunnelSkill(direction: String, state: WorldState, player: PlayerState): Pair<WorldState, String> {
    if (player.getSkillLevel("Tunnel") < 20) {
        return state to "You lack the skill to carve a tunnel."
    }

    if (player.stamina < 30 || player.focus < 20) {
        return state to "You're too exhausted to tunnel."
    }

    val currentNode = graphNodeRepo.findById(player.currentLocation)
    val targetPosition = calculateAdjacentPosition(currentNode.position, direction)
    val targetNode = findOrCreateNodeAtPosition(targetPosition, state)

    val newEdge = EdgeData(targetId = targetNode.id, direction = "tunnel $direction")
    val updatedCurrentNode = currentNode.copy(neighbors = currentNode.neighbors + newEdge)
    val reverseEdge = EdgeData(targetId = currentNode.id, direction = "tunnel back")
    val updatedTargetNode = targetNode.copy(neighbors = targetNode.neighbors + reverseEdge)

    graphNodeRepo.update(updatedCurrentNode)
    graphNodeRepo.update(updatedTargetNode)

    val updatedPlayer = player.copy(stamina = player.stamina - 30, focus = player.focus - 20)
    val updatedState = state.updatePlayer(updatedPlayer)

    return updatedState to "You carve a tunnel ${direction}, revealing a new passage."
}

// ItemHandlers.kt
fun handleExplosive(direction: String, state: WorldState, player: PlayerState): Pair<WorldState, String> {
    val explosive = player.inventory.find { it.itemId == "explosive" } ?: return state to "You don't have explosives."

    val currentNode = graphNodeRepo.findById(player.currentLocation)
    val edge = currentNode.neighbors.find { it.direction == direction } ?: return state to "No exit in that direction."

    val updatedNeighbors = currentNode.neighbors - edge
    val testNode = currentNode.copy(neighbors = updatedNeighbors)

    // Validate: Don't disconnect graph
    val allNodes = graphNodeRepo.findByChunk(currentNode.chunkId)
    val validation = graphValidator.validate(allNodes.map { if (it.id == currentNode.id) testNode else it })
    if (validation is ValidationResult.Failure) {
        return state to "Collapsing this passage would trap you. The explosion fizzles."
    }

    graphNodeRepo.update(testNode)
    val updatedPlayer = player.copy(inventory = player.inventory - explosive)
    val updatedState = state.updatePlayer(updatedPlayer)

    return updatedState to "The explosion collapses the ${direction} passage in a shower of rubble."
}
```

**Testing**:
- Test Tunnel creates bidirectional edge
- Test Tunnel requires skill level, stamina, focus
- Test Explosive removes edge, updates DB
- Test Explosive prevented if it disconnects graph
- Integration test: Tunnel to new area, explosive to seal behind, verify escape via other route
- Rationale: Dynamic modification is complex; tests ensure edge cases (no pun intended) handled

**Documentation**:
- Update GETTING_STARTED.md with Tunnel skill and explosive usage
- Update SKILLS.md (if exists) with Tunnel skill requirements

---

### Chunk 8: Breakout Edges to New Biomes (3-4h)
**Description**: Every 3-5 chunks, add 1-2 breakout edges to new REGION biomes for infinite exploration.

**Reasoning**: V3 spec: "Every 3-5 chunks, add 1-2 'breakout' edges to new REGION biomes (e.g., 'unstable wall' to 'Endless Caverns')." Prevents monotony, enables emergent exploration. Breakout edges placed on frontier nodes, target new REGION with different theme.

**Affected Modules/Files**:
- `:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/WorldGenerator.kt` - Add breakout logic
- `:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/worldgen/BiomeSelector.kt` - Create for theme selection

**Technical Decisions**:
- Track chunk count from starting dungeon in WorldState metadata
- Every 3-5 chunks (random, seeded by chunk ID): Select 1-2 frontier nodes, add breakout edge
- Breakout edge:
  - Direction: "unstable wall", "hidden passage", "glowing portal" (thematic)
  - Condition: Optional skill check (e.g., Agility to squeeze through)
  - Target: New REGION with contrasting theme (e.g., dungeon -> endless caverns, forest -> desert)
- BiomeSelector uses LLM to generate contrasting theme based on current chunk lore
- New REGION cascades down to ZONE/SUBZONE/SPACE as normal

**Key Logic**:
```kotlin
// WorldGenerator.kt
suspend fun generateChunk(parentChunk: WorldChunkComponent, level: ChunkLevel): WorldChunkComponent {
    // ... existing generation

    if (level == ChunkLevel.SUBZONE && shouldAddBreakout(parentChunk)) {
        val frontierNodes = graph.filter { it.type == NodeType.Frontier }
        val breakoutCount = rng.nextInt(1, 3)
        val selectedFrontiers = frontierNodes.shuffled(rng).take(breakoutCount)

        selectedFrontiers.forEach { frontierNode ->
            val newBiome = biomeSelector.selectContrasting(parentChunk.biomeTheme, parentChunk.lore)
            val newRegion = createRegion(newBiome)
            worldChunkRepo.save(newRegion)

            val breakoutEdge = EdgeData(
                targetId = newRegion.startNodeId,
                direction = generateBreakoutDirection(newBiome),
                conditions = listOf(Condition.SkillCheck("Agility", 12))
            )

            val updatedFrontier = frontierNode.copy(neighbors = frontierNode.neighbors + breakoutEdge)
            graphNodeRepo.update(updatedFrontier)
        }
    }

    return newChunk
}

private fun shouldAddBreakout(chunk: WorldChunkComponent): Boolean {
    val chunkDepth = calculateDepthFromStart(chunk)
    return chunkDepth % rng.nextInt(3, 6) == 0
}

// BiomeSelector.kt
class BiomeSelector(private val llm: LLMClient) {
    suspend fun selectContrasting(currentTheme: String, currentLore: String): String {
        val prompt = """
            Current biome: $currentTheme
            Current lore: $currentLore

            Generate a contrasting biome theme for exploration.
            Examples: dungeon->caverns, forest->desert, ice->volcano
            Return a brief theme name and description.
        """.trimIndent()

        return llm.generate(prompt)
    }
}
```

**Testing**:
- Test breakout edges added every 3-5 chunks
- Test breakout targets new REGION with different theme
- Test breakout direction is thematic
- Integration test: Traverse 10 chunks, verify 2-3 breakouts occurred
- Rationale: Breakout frequency is critical; too many dilutes theme, too few feels linear

**Documentation**:
- Update WORLD_GENERATION.md with breakout mechanic
- Add examples of biome transitions

---

### Chunk 9: Update Exit Resolution for Graph Structure (3h)
**Description**: Update ExitResolver to use GraphNodeComponent neighbors instead of SpaceProperties exits.

**Reasoning**: V3 changes source of truth from SpaceProperties.exits to GraphNodeComponent.neighbors. ExitResolver must query graph first, filter by hidden/revealed, then apply hybrid matching (cardinal/fuzzy/LLM).

**Affected Modules/Files**:
- `:reasoning/src/main/kotlin/com/jcraw/mud/reasoning/ExitResolver.kt` - Update to use graph
- `:app/src/main/kotlin/com/jcraw/app/handlers/MovementHandlers.kt` - Update Travel handler

**Technical Decisions**:
- Query GraphNodeRepository for current node
- Filter neighbors by: !edge.hidden || player.revealedExits.contains(edgeId)
- Hybrid matching order: Exact cardinal → Fuzzy cardinal → LLM parse → "unclear direction"
- Condition checks (skill/item) occur after exit resolution, before movement
- Cache graph nodes in memory for session (reduce DB hits)

**Updated ExitResolver**:
```kotlin
class ExitResolver(
    private val graphNodeRepo: GraphNodeRepository,
    private val llm: LLMClient
) {
    suspend fun resolve(
        direction: String,
        currentNodeId: String,
        player: PlayerState
    ): ExitResolution {
        val node = graphNodeRepo.findById(currentNodeId) ?: return ExitResolution.NotFound
        val visibleEdges = node.neighbors.filter { edge ->
            !edge.hidden || player.revealedExits.contains(edgeId(node.id, edge.targetId))
        }

        // Exact match
        val exact = visibleEdges.find { it.direction.equals(direction, ignoreCase = true) }
        if (exact != null) return ExitResolution.Found(exact)

        // Fuzzy cardinal
        val fuzzy = visibleEdges.find { fuzzyMatchCardinal(it.direction, direction) }
        if (fuzzy != null) return ExitResolution.Found(fuzzy)

        // LLM parse
        val llmMatch = llm.parseDirection(direction, visibleEdges.map { it.direction })
        if (llmMatch != null) {
            val edge = visibleEdges.find { it.direction == llmMatch }
            if (edge != null) return ExitResolution.Found(edge)
        }

        return ExitResolution.Ambiguous(visibleEdges.map { it.direction })
    }
}

sealed class ExitResolution {
    data class Found(val edge: EdgeData) : ExitResolution()
    object NotFound : ExitResolution()
    data class Ambiguous(val suggestions: List<String>) : ExitResolution()
}
```

**Testing**:
- Test exact match resolves correctly
- Test fuzzy cardinal match (e.g., "n" → "north")
- Test LLM match for natural language ("climb ladder")
- Test hidden exit not resolved until revealed
- Test ambiguous direction returns suggestions
- Rationale: Exit resolution is player-facing; bugs frustrate navigation

**Documentation**:
- Update ARCHITECTURE.md ExitResolver section
- Update GETTING_STARTED.md movement commands with examples

---

### Chunk 10: Comprehensive Testing (4-5h)
**Description**: Add unit, integration, and bot tests for V3 graph features.

**Reasoning**: V3 adds substantial complexity (graph gen, validation, modification). Tests must cover: graph algorithms (MST, layout), validation (connectivity, loops), hidden exits (reveal mechanics), dynamic edges (tunnel/explosive), breakout edges (biome transitions). Bot test for full dungeon exploration validates end-to-end.

**Affected Modules/Files**:
- `:reasoning/src/test/kotlin/com/jcraw/mud/reasoning/worldgen/` - GraphGeneratorTest, GraphValidatorTest
- `:persistence/src/test/kotlin/com/jcraw/mud/persistence/` - SQLiteGraphNodeRepositoryTest
- `:app/src/test/kotlin/com/jcraw/app/` - WorldGenV3IntegrationTest
- `:testbot/src/main/kotlin/com/jcraw/mud/testbot/scenarios/` - WorldGenV3BotTest

**Test Coverage**:

**Unit Tests**:
- `GraphGeneratorTest`:
  - Grid layout produces WxH nodes
  - BSP layout produces expected room count
  - FloodFill produces nodeCount nodes
  - MST connects all nodes (BFS reachability)
  - Extra edges create loops (cycle detection)
  - Node type assignment: 1-2 hubs, 1 boss, 2+ frontiers, 20% dead-ends
  - Hidden edges: 15-25% of total edges marked hidden
- `GraphValidatorTest`:
  - Valid graph passes all checks
  - Disconnected graph fails
  - Tree (no loops) fails
  - Low avg degree fails
  - Insufficient frontiers fails
- `SQLiteGraphNodeRepositoryTest`:
  - CRUD operations work
  - findByChunk returns all nodes
  - addEdge/removeEdge updates neighbors
  - JSON serialization round-trips correctly

**Integration Tests**:
- `WorldGenV3IntegrationTest`:
  - Generate SUBZONE creates graph in DB
  - Frontier traversal cascades to new chunk
  - Lazy-fill generates description on first visit
  - Breakout edge added every 3-5 chunks
  - Hidden exit revealed by Perception check
  - Tunnel skill creates new edge, persists
  - Explosive removes edge, prevents if disconnects
  - Save/load preserves graph structure, revealed exits

**Bot Tests**:
- `WorldGenV3BotTest`:
  - Scenario: "Deep Dungeon Exploration"
    1. Start at top of dungeon
    2. Explore 10+ spaces, verify graph structure
    3. Use Perception to find hidden exit
    4. Use Tunnel skill to create shortcut
    5. Encounter breakout edge, explore new biome
    6. Use explosive to seal dangerous path
    7. Continue to boss room (via frontier cascade)
    8. Die to boss
    9. Respawn at start, verify graph/corpse persist
    10. Navigate back to corpse using created tunnel

**Testing**:
- Run full test suite: `gradle test`
- Run bot test: `gradle :testbot:run --args="WorldGenV3BotTest"`
- Verify 0 failures, all V3 scenarios pass
- Rationale: V3 is feature-complete; comprehensive tests ensure stability

**Documentation**:
- Update TESTING.md with V3 test descriptions
- Add bot test scenario to testbot README

---

### Chunk 11: Documentation Updates (3h)
**Description**: Update all relevant documentation to reflect V3 features.

**Reasoning**: Final step ensures users/developers understand V3. CLAUDE.md status, ARCHITECTURE.md components/flow, WORLD_GENERATION.md new mechanics, GETTING_STARTED.md commands, TODO.md future enhancements.

**Affected Files**:
- `CLAUDE.md` - Update project status to V3
- `docs/ARCHITECTURE.md` - Add GraphNodeComponent, GraphGenerator, repositories, updated data flow
- `docs/WORLD_GENERATION.md` - Rewrite generation flow for graph-first approach, add validation, breakout edges
- `docs/GETTING_STARTED.md` - Add Scout command details, Tunnel skill, explosive item usage
- `docs/TODO.md` - Move V3 to completed, add V4 enhancements (weather/events, mob wandering)

**Key Documentation Changes**:

**CLAUDE.md**:
```markdown
**Current State: ✅ PRODUCTION READY - V3** - World System upgraded to graph-based navigation with pre-generated topology, hidden exits, dynamic edge modification, and infinite exploration via frontier nodes and breakout edges.

## What's Implemented
### World Generation V3 ✅
- **Graph-based navigation**: Pre-generated topology with typed nodes (HUB, BOSS, FRONTIER, etc.)
- **Structured layouts**: Grid/BSP/flood-fill algorithms with MST connectivity and loops
- **Hidden exits**: 15-25% edges hidden, revealed via Perception checks
- **Infinite exploration**: Frontier nodes cascade to new chunks, breakout edges to new biomes every 3-5 chunks
- **Player agency**: Tunnel skill creates edges, explosives remove edges
- **Graph validation**: Post-gen checks for connectivity, loops, avg degree, frontiers
```

**ARCHITECTURE.md**:
```markdown
### GraphNodeComponent
- **Purpose**: Pre-generated graph topology for structured navigation
- **Location**: `:core/components/GraphNodeComponent.kt`
- **Key Fields**: `id`, `position`, `type` (NodeType), `neighbors` (List<EdgeData>)
- **Node Types**: HUB (safe), LINEAR (corridor), BRANCHING (choice), DEAD_END (treasure), BOSS, FRONTIER (expandable), QUESTABLE

### GraphGenerator
- **Purpose**: Generate graph layouts using algorithms (grid, BSP, flood-fill)
- **Location**: `:reasoning/worldgen/GraphGenerator.kt`
- **Algorithms**: Grid (WxH), BSP (recursive split), FloodFill (organic)
- **Connectivity**: Kruskal MST + 20% extra edges for loops
- **Node Assignment**: 1-2 hubs, 1 boss, 2+ frontiers, 20% dead-ends

### Data Flow (V3)
```
User Input (Travel)
    ↓
ExitResolver (query GraphNodeRepository, filter hidden, hybrid match)
    ↓
Condition Check (skills/items)
    ↓
Lazy-fill SpaceProperties (LLM generate if first visit)
    ↓
Update PlayerState (currentLocation, revealedExits)
    ↓
Action (narration)
```
```

**WORLD_GENERATION.md**:
```markdown
## V3 Generation Flow

### 1. Graph Topology Generation
When a SUBZONE chunk is created:
1. Select layout algorithm (grid/BSP/flood-fill) based on biome theme
2. Generate node positions using algorithm
3. Connect nodes via Kruskal MST
4. Add 20% extra edges for loops
5. Assign node types (hubs, boss, frontiers, dead-ends)
6. Mark 15-25% edges as hidden with Perception difficulty
7. Validate graph (connectivity, loops, avg degree, frontiers)
8. Save GraphNodeComponents to DB

### 2. Lazy Content Generation
When a player enters a node for the first time:
1. Query GraphNodeComponent for structure
2. Generate SpaceProperties description via LLM (using node type, neighbors, chunk lore)
3. Place entities, resources, traps based on node type
4. Cache content in DB

### 3. Frontier Cascade
When a player traverses a frontier node:
1. Create new child chunk (SUBZONE)
2. Generate graph for new chunk
3. Link frontier node to hub node in new chunk
4. Continue exploration

### 4. Breakout Edges
Every 3-5 chunks:
1. Select 1-2 frontier nodes
2. Generate contrasting biome theme via LLM
3. Create new REGION chunk with new theme
4. Add breakout edge from frontier to new REGION hub
5. Cascade new REGION down to SUBZONE

### 5. Hidden Exit Revelation
- Player uses Scout intent or Perception skill
- Roll against hidden edge difficulty (10-30)
- On success, add edge ID to player.revealedExits
- Hidden exit becomes visible in exit list

### 6. Dynamic Edge Modification
- **Tunnel Skill**: Creates bidirectional edge to adjacent position (costs stamina/focus)
- **Explosive Item**: Removes edge (validates no graph disconnect)
```

**GETTING_STARTED.md**:
```markdown
### Scout Command
Search for hidden exits in your current location.

**Usage**: `scout [direction]`

**Mechanics**:
- Rolls Perception vs hidden exit difficulty (10-30)
- On success, reveals a hidden exit
- Revealed exits persist across sessions

**Examples**:
- `scout` - Search all directions
- `scout north` - Focus on north direction

### Tunnel Skill
Carve a new passage through solid rock.

**Requirements**:
- Tunnel skill level >= 20
- Stamina >= 30
- Focus >= 20

**Usage**: `use Tunnel [direction]`

**Mechanics**:
- Creates bidirectional edge to adjacent position
- Costs 30 stamina + 20 focus
- New tunnel persists in world

**Examples**:
- `use Tunnel north` - Carve tunnel northward
- `use Tunnel up` - Carve shaft upward

### Explosive Item
Collapse a passage with explosives.

**Requirements**:
- Explosive item in inventory

**Usage**: `use explosive [direction]`

**Mechanics**:
- Removes edge in specified direction
- Validates removal won't disconnect graph
- Explosive consumed on use

**Examples**:
- `use explosive east` - Seal eastern passage
```

**TODO.md**:
```markdown
## Completed
- ✅ World System V3 - Graph-based navigation, hidden exits, dynamic edges, infinite exploration

## Future Enhancements (V4)
- **Weather/Events** - Day-night cycle, weather affecting brightness/terrain/mobs
- **Mob Wandering** - NPCs/mobs move between graph nodes
- **Full Open World** - Surface biomes, cities, wilderness
- **Multiplayer Shared Gen** - Shared world seeds, distributed locks
- **Procedural Evolution** - Player actions alter world lore
- **Dynamic Seeds** - World evolves based on player choices
- **Quest System Integration** - Questable nodes for gated content
```

**Testing**:
- Verify all documentation renders correctly
- Check internal links work
- Proofread for consistency
- Rationale: Documentation is user-facing; errors erode trust

**Documentation**:
- Self-documenting step; no additional docs needed

---

## Summary

**Total Chunks**: 11
**Estimated Time**: 40-45 hours

**Chunk Sequence**:
1. GraphNodeComponent and Data Structures (3-4h)
2. Database Schema and GraphNodeRepository (3-4h)
3. Graph Generation Algorithms (5-6h)
4. Graph Validation System (3h)
5. Integrate Graph Generation with World System (4-5h)
6. Hidden Exit Revelation via Perception (3h)
7. Dynamic Edge Modification (4h)
8. Breakout Edges to New Biomes (3-4h)
9. Update Exit Resolution for Graph Structure (3h)
10. Comprehensive Testing (4-5h)
11. Documentation Updates (3h)

**Critical Path**:
- Chunks 1-2 must complete before 3 (need data model and persistence)
- Chunk 3 must complete before 4-5 (need graph generator before integration)
- Chunks 6-9 can run in parallel after chunk 5
- Chunk 10 requires all feature chunks (1-9) complete
- Chunk 11 is final

**Key Technical Decisions**:
- Sealed classes for NodeType (extensibility)
- Graph at SUBZONE level (5-100 spaces)
- Lazy-fill content (reduce LLM calls)
- Validation prevents bad graphs (quality assurance)
- Player agency via skills/items (emergent gameplay)
- Breakout edges every 3-5 chunks (infinite exploration)

**Success Criteria**:
- ✅ All tests pass (unit, integration, bot)
- ✅ Graph generation produces valid, connected structures
- ✅ Hidden exits revealed by Perception
- ✅ Tunnel/explosive modify edges dynamically
- ✅ Breakout edges create new biomes
- ✅ Documentation updated and accurate
