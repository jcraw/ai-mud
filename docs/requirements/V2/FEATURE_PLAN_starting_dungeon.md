# Starting Dungeon Configuration - Implementation Plan

## Overview

This plan implements the "Ancient Abyss Dungeon" as the default starting area - a pre-generated deep dungeon (100+ floors) with fixed structure, murderhobo gameplay loop (fight → loot → sell/craft → gear → descend), and full integration with combat, skills, items, and social systems. Builds on World Generation V2 MVP (chunks 1-6 complete).

**Key Features**:
- Pre-generated fixed hierarchy (Ancient Abyss with 4 regions)
- Town safe zone with merchants
- Mob respawn system with turn-based timers
- Player death → corpse creation → respawn at town
- Corpse retrieval for gear recovery
- Boss fight (Abyssal Lord) + treasure (Abyss Heart)
- Victory condition (return treasure to town)
- Hidden exit to open world
- World persistence across player deaths

**Total Estimated Time:** 32 hours (8 chunks × 3-5 hours each)

---

## Chain-of-Thought Analysis

### Current State Assessment

**What Exists (World Gen V2 MVP, Chunks 1-6)**:
- ✅ Hierarchical world structure (WORLD→REGION→ZONE→SUBZONE→SPACE)
- ✅ WorldChunkComponent (lore, theme, difficulty, mobDensity)
- ✅ SpacePropertiesComponent (exits, traps, resources, entities, stateFlags)
- ✅ gameTime tracking in WorldState (line 10)
- ✅ advanceTime(ticks) method (line 46)
- ✅ DungeonInitializer for deep dungeon setup
- ✅ MobSpawner for entity generation
- ✅ RespawnManager for world regeneration
- ✅ TradingComponent, InventoryComponent, SocialComponent
- ✅ Persistence layer (repositories, save/load)
- ✅ Exit system with skill/item conditions

**What's Missing**:
- ❌ RespawnComponent (per-entity respawn tracking with timers)
- ❌ ComponentType.CORPSE, ComponentType.RESPAWN enum values
- ❌ Corpse entity handling (player death → corpse creation)
- ❌ Corpse database table
- ❌ Intent.Rest (rest at town to regen HP/Mana)
- ❌ Safe zone designation (spaces with no mobs/traps)
- ❌ Victory condition system
- ❌ Boss entity designation
- ❌ Fixed dungeon structure (4 specific regions)
- ❌ Town subzone with merchants

### Architecture Alignment

- **ECS Extension**: RespawnComponent extends existing Component system
- **Immutable State**: All changes return new copies (existing pattern)
- **Modular**: Respawn logic in :reasoning, persistence in :memory
- **Backward Compat**: World system is opt-in (legacy mode preserved)

### Key Dependencies

1. **World Generation V2**: Foundation (chunks 1-6 complete)
2. **Combat System**: Player death detection (HP <= 0)
3. **Items System**: Corpse inventory, loot transfer
4. **Social System**: Merchant disposition for pricing
5. **Skills System**: Perception for hidden exit
6. **Quest System**: Victory condition integration (return treasure)

### Critical Design Decisions

1. **RespawnComponent vs Simple Regeneration**
   - *Decision*: Add explicit RespawnComponent with lastKilled timestamp
   - *Reasoning*: Current system regenerates all mobs on restart; need granular per-entity respawn for farming spots (some mobs respawn fast, bosses don't)
   - *Impact*: More complex but enables strategic farming

2. **Player Death Handling**
   - *Decision*: Create corpse entity with full inventory, spawn fresh player at town
   - *Reasoning*: Dark Souls-style corpse retrieval; risk/reward for deep exploration
   - *Alternative Considered*: Just respawn with partial inventory loss
   - *Why This Approach*: More engaging; enables "corpse run" gameplay

3. **World Persistence on Death**
   - *Decision*: World state fully persists (no regeneration of chunks/spaces)
   - *Reasoning*: Player changes (killed mobs, looted chests, state flags) should persist; only individual mob respawns trigger after timers
   - *Impact*: Requires corpse DB table, respawn component tracking

4. **Fixed vs Procedural Dungeon**
   - *Decision*: Fixed hierarchy (4 predefined regions) with procedural room details
   - *Reasoning*: MVP needs consistent difficulty curve; fully procedural risks imbalance
   - *Hybrid Approach*: Structure fixed (regions/zones), content LLM-generated (rooms/mobs)

5. **Victory Condition**
   - *Decision*: Quest-based (return Abyss Heart to town)
   - *Reasoning*: Integrates with existing quest system; clear win condition
   - *Alternative*: Just boss kill achievement
   - *Why This Approach*: Encourages full loop (descend → kill boss → return)

### Challenges & Mitigations

- **Challenge**: Respawn timer complexity (turn-based vs real-time)
  - *Mitigation*: Use gameTime (turn proxy); simple threshold check on space entry

- **Challenge**: Corpse decay preventing retrieval
  - *Mitigation*: Long decay timer (5000 turns = ~83 hours of gameplay at 1 turn/minute)

- **Challenge**: Player confusion about death mechanics
  - *Mitigation*: Clear narration ("Your corpse lies in [space name]. A new soul awakens...")

- **Challenge**: Fixed dungeon limits replayability
  - *Mitigation*: LLM-generated room details ensure variety; mob respawns change encounters

---

## TODO: Implementation Chunks

### Chunk 1: Foundation - New Components & Data Model (4 hours)

**Description**: Implement core data structures for respawn system, corpse handling, and safe zones.

**Affected Modules**:
- `core/src/main/kotlin/com/jcraw/mud/core/`
- `perception/src/main/kotlin/com/jcraw/mud/perception/`

**Files to Create/Modify**:

1. **Component.kt** (core:30 - modify)
   - Add `CORPSE` and `RESPAWN` to ComponentType enum
   - *Reasoning*: Extends existing ECS pattern; minimal change to core enum

2. **RespawnComponent.kt** (core:60 - new)
   ```kotlin
   data class RespawnComponent(
       val respawnTurns: Long = 500L,  // Turns until respawn
       val lastKilled: Long = 0L,      // gameTime when killed
       val originalEntityId: String,   // Template for regeneration
       override val componentType: ComponentType = ComponentType.RESPAWN
   ) : Component
   ```
   - Methods:
     - `shouldRespawn(currentTime: Long): Boolean` - Check if enough time elapsed
     - `markKilled(gameTime: Long): RespawnComponent` - Set lastKilled
   - *Reasoning*: Encapsulates respawn logic; reusable across entity types

3. **CorpseData.kt** (core:80 - new)
   ```kotlin
   data class CorpseData(
       val id: String,
       val playerId: PlayerId,
       val spaceId: String,  // Where corpse is located
       val inventory: InventoryComponent,  // Full player inventory
       val equipment: List<ItemInstance>,  // Equipped items
       val gold: Int,
       val decayTimer: Long,  // gameTime when corpse despawns
       val looted: Boolean = false
   )
   ```
   - *Reasoning*: Separate from Entity for persistence; simpler DB serialization
   - *Note*: Corpses stored in DB, not as world entities (cleaner separation)

4. **SpacePropertiesComponent.kt** (core:40 - modify)
   - Add `isSafeZone: Boolean = false` field
   - Safe zones: No mob spawns, no traps, no combat
   - *Reasoning*: Simple flag vs separate SafeZoneComponent (KISS principle)

5. **Intent.kt** (perception:35 - modify)
   - Add `data class Rest(val location: String = "current") : Intent()`
   - Triggers HP/Mana regen, advances gameTime by 100 ticks
   - *Reasoning*: Distinct intent for resting mechanic

6. **BossFlags.kt** (core:40 - new)
   ```kotlin
   data class BossDesignation(
       val isBoss: Boolean = false,
       val bossTitle: String = "",
       val victoryFlag: String = ""  // Quest flag on death
   )
   ```
   - Attach to Entity.NPC as metadata (not full component)
   - *Reasoning*: Lightweight flag vs complex boss component

**Key Technical Decisions**:
- **Corpse as Data Class**: Not a full Entity; simpler persistence
  - *Reasoning*: Corpses are static (no AI, no combat); don't need ECS complexity
- **Safe Zone Flag**: Boolean field vs component
  - *Reasoning*: Single flag simpler; no need for complex safe zone behavior
- **Boss as Metadata**: Not a component type
  - *Reasoning*: Boss is just a strong NPC with a flag; reuses NPC combat logic

**Testing Approach** (`core:test`):

1. **RespawnComponentTest.kt** (12 tests)
   - `shouldRespawn()` threshold logic (various times)
   - `markKilled()` updates lastKilled
   - Edge cases (respawnTurns = 0, negative times)

2. **CorpseDataTest.kt** (10 tests)
   - Serialization roundtrip
   - Inventory/equipment preservation
   - Decay timer calculation

3. **SpacePropertiesComponentTest.kt** (modify existing, +8 tests)
   - Safe zone flag serialization
   - Safe zones block mob spawns (integration with SpacePopulator)

4. **IntentTest.kt** (perception:test, +6 tests)
   - Intent.Rest serialization
   - Rest at various locations

5. **BossFlagsTest.kt** (8 tests)
   - Boss designation serialization
   - Victory flag integration

**Total Tests**: ~44 tests

**Documentation Updates**:
- None yet (comprehensive update in Chunk 8)

---

### Chunk 2: Database Extensions (3 hours)

**Description**: Implement persistence layer for respawn components and corpses.

**Affected Modules**:
- `memory/src/main/kotlin/com/jcraw/mud/memory/`
- `core/src/main/kotlin/com/jcraw/mud/core/repository/`

**Files to Create/Modify**:

1. **WorldDatabase.kt** (memory:80 - modify)
   - Add tables:
   ```sql
   CREATE TABLE respawn_components (
       entity_id TEXT PRIMARY KEY,
       respawn_turns INTEGER NOT NULL,
       last_killed INTEGER NOT NULL,
       original_entity_id TEXT NOT NULL,
       space_id TEXT NOT NULL,
       FOREIGN KEY (space_id) REFERENCES space_properties(chunk_id)
   );
   CREATE INDEX idx_respawn_space ON respawn_components(space_id);

   CREATE TABLE corpses (
       id TEXT PRIMARY KEY,
       player_id TEXT NOT NULL,
       space_id TEXT NOT NULL,
       inventory_json TEXT NOT NULL,
       equipment_json TEXT NOT NULL,
       gold INTEGER NOT NULL,
       decay_timer INTEGER NOT NULL,
       looted INTEGER NOT NULL DEFAULT 0,  -- Boolean as INTEGER
       FOREIGN KEY (space_id) REFERENCES space_properties(chunk_id)
   );
   CREATE INDEX idx_corpse_space ON corpses(space_id);
   CREATE INDEX idx_corpse_player ON corpses(player_id);
   ```
   - *Reasoning*: Respawn tied to spaces (check on entry); corpses tied to spaces (loot location)

2. **RespawnRepository.kt** (core/repository:70 - new)
   - Interface:
     - `save(entityId: String, component: RespawnComponent, spaceId: String): Result<Unit>`
     - `findBySpace(spaceId: String): Result<Map<String, RespawnComponent>>`  // entityId → component
     - `markKilled(entityId: String, gameTime: Long): Result<Unit>`
     - `deleteBySpace(spaceId: String): Result<Unit>`
     - `delete(entityId: String): Result<Unit>`
   - *Reasoning*: Bulk query by space (check all respawns on entry)

3. **SQLiteRespawnRepository.kt** (memory:180 - new)
   - Implements RespawnRepository
   - JSON serialization for component if needed (currently flat structure)
   - Batch update optimization for markKilled (multiple entities)
   - *Reasoning*: Performance critical (frequent checks); optimize for bulk ops

4. **CorpseRepository.kt** (core/repository:80 - new)
   - Interface:
     - `save(corpse: CorpseData): Result<Unit>`
     - `findBySpace(spaceId: String): Result<List<CorpseData>>`
     - `findByPlayer(playerId: PlayerId): Result<List<CorpseData>>`  // All corpses for player
     - `markLooted(corpseId: String): Result<Unit>`
     - `deleteDecayed(currentTime: Long): Result<Int>`  // Delete all with decayTimer < currentTime
     - `delete(corpseId: String): Result<Unit>`
   - *Reasoning*: `findByPlayer()` for UI ("Your corpses: ..."); `deleteDecayed()` for cleanup

5. **SQLiteCorpseRepository.kt** (memory:220 - new)
   - Implements CorpseRepository
   - JSON serialization for inventory/equipment (nested structures)
   - Batch decay deletion (periodic cleanup)
   - *Reasoning*: JSON for complex inventory; cleanup crucial (prevent DB bloat)

**Key Technical Decisions**:
- **Respawn Indexed by Space**: Enables efficient "check all respawns in current space"
  - *Reasoning*: On player entry, check all entities in space for respawn
- **Corpse Indexed by Space AND Player**: UI queries ("where are my corpses?")
  - *Reasoning*: Players need to find their corpses; space index for looting
- **Decay Timer Cleanup**: Periodic batch deletion vs on-query filtering
  - *Decision*: Batch deletion (cron-like task every 100 turns)
  - *Reasoning*: Prevents DB bloat; cleanup cost amortized

**Testing Approach** (`memory:test`):

1. **WorldDatabaseTest.kt** (modify existing, +10 tests)
   - Table creation (respawn_components, corpses)
   - Foreign key constraints
   - Index existence

2. **SQLiteRespawnRepositoryTest.kt** (25 tests)
   - Save/load roundtrip
   - `findBySpace()` returns all entities
   - `markKilled()` updates lastKilled
   - Batch operations (mark multiple entities killed)
   - Delete operations

3. **SQLiteCorpseRepositoryTest.kt** (30 tests)
   - Save/load with complex inventory
   - JSON serialization (inventory, equipment)
   - `findBySpace()` and `findByPlayer()` queries
   - `markLooted()` flag update
   - `deleteDecayed()` bulk deletion (test with 100 corpses, various timers)
   - Error cases (nonexistent corpse, invalid space)

**Total Tests**: ~65 tests

**Documentation Updates**:
- None yet (comprehensive update in Chunk 8)

---

### Chunk 3: Fixed Dungeon Pre-Generation (4 hours)

**Description**: Extend DungeonInitializer to create Ancient Abyss Dungeon with fixed 4-region structure.

**Affected Modules**:
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/`

**Files to Create/Modify**:

1. **DungeonInitializer.kt** (reasoning:modify existing +200 lines)
   - Add `initializeAncientAbyss(seed: String = "dark fantasy DnD"): Result<AncientAbyssData>`
   - Returns `data class AncientAbyssData(worldId: String, townSpaceId: String, regions: Map<String, String>)`
   - **Structure**:
     - **WORLD**: "Ancient Abyss Dungeon"
       - Lore: "Forgotten prison of eldritch horrors, sealed eons ago. Descend at your peril for abyssal treasures."
     - **REGION 1**: "Upper Depths" (difficulty 1-10)
       - 3 zones, 10-20 subzones
       - Theme: Damp caves, slimes, goblins
       - Includes town subzone (zone 1, subzone 1) - safe zone
     - **REGION 2**: "Mid Depths" (difficulty 10-30)
       - 4 zones, 20-40 subzones
       - Theme: Fungal warrens, traps, locks
       - Scattered merchants (2-3 NPCs)
       - Hidden exit in zone 2 (Perception 40 + Lockpicking 30 check)
     - **REGION 3**: "Lower Depths" (difficulty 30-60)
       - 5 zones, 40-70 subzones
       - Theme: Undead crypts, demons
       - Rare LEGENDARY loot
     - **REGION 4**: "Abyssal Core" (difficulty 60+)
       - 2 zones, 20+ subzones (lazy gen)
       - Theme: Lava forges, boss lair
       - Final space: Abyssal Lord boss
   - **Pre-Generation**: Generate regions 1-3 fully (~70 subzones); region 4 lazy
   - *Reasoning*: Pre-gen shallow for fast start; lazy deep for infinite descent

2. **TownGenerator.kt** (reasoning:150 - new)
   - Methods:
     - `generateTownSubzone(parentZone: WorldChunkComponent): Result<Pair<String, String>>`  // Returns (subzoneId, firstSpaceId)
     - `populateTownSpace(spaceId: String, spaceProps: SpacePropertiesComponent): Result<SpacePropertiesComponent>`
   - **Town Features**:
     - isSafeZone = true
     - 3-5 NPCs: Merchant (potions, armor), Blacksmith (weapons), Innkeeper (lore/hints)
     - All with TradingComponent (finite gold: 500-1000, stock: 10-20 items)
     - Description: "A safe haven carved from the dungeon depths. Torches flicker, merchants hawk wares, and weary adventurers rest."
   - *Reasoning*: Centralized town generation; reusable for other safe zones

3. **BossGenerator.kt** (reasoning:120 - new)
   - Methods:
     - `generateAbyssalLordSpace(parentSubzone: WorldChunkComponent): Result<String>`  // Returns spaceId
     - `createAbyssalLord(): Entity.NPC`
   - **Abyssal Lord**:
     - Stats: Strength 80, Agility 60, Intelligence 70, Wisdom 60, Vitality 100, Charisma 50
     - Skills: Sword 100, Fire Magic 80, Dark Magic 70
     - HP: 1000
     - LLM AI: "Strategize as ancient demon lord; use fire magic, summon minions"
     - BossDesignation: isBoss=true, bossTitle="Abyssal Lord", victoryFlag="abyssal_lord_defeated"
     - Loot: Abyss Heart (on death)
   - **Abyss Heart**:
     - LEGENDARY item, equip slot: ACCESSORY
     - Effects: +50 all stats, +100% XP gain
     - Quest flag: "abyss_heart_retrieved"
   - *Reasoning*: Boss as super-powered NPC; reuses combat system

4. **HiddenExitPlacer.kt** (reasoning:100 - new)
   - Methods:
     - `placeHiddenExit(midDepthsRegionId: String): Result<Unit>`
   - **Exit Details**:
     - Location: Mid Depths, Zone 2, random space
     - Description: "Cracked wall revealing starlight beyond"
     - Conditions:
       - `SkillCheck("Perception", 40)` OR
       - `SkillCheck("Lockpicking", 30)` OR
       - `SkillCheck("Strength", 50)`  // Brute force
     - Target: New REGION "Surface Wilderness" (ChunkLevel.REGION; gen on first use)
   - *Reasoning*: Easter egg for explorers; seeds open world expansion

**Key Technical Decisions**:
- **Fixed Regions, Procedural Rooms**: Structure predictable, content varied
  - *Reasoning*: Balance consistency (difficulty curve) with variety (LLM rooms)
- **Pre-Gen 70 Subzones**: ~10 seconds on launch
  - *Reasoning*: Fast start (no first-move latency); shallow areas most replayed
- **Town as Zone 1 Subzone 1**: Always accessible (predictable location)
  - *Reasoning*: Players need reliable safe zone; easy to navigate to

**Testing Approach** (`reasoning:test`):

1. **DungeonInitializerTest.kt** (modify existing, +20 tests)
   - `initializeAncientAbyss()` creates 4 regions
   - Region hierarchy (WORLD→REGION→ZONE→SUBZONE→SPACE)
   - Difficulty scaling (Upper 1-10, Mid 10-30, Lower 30-60, Core 60+)
   - Pre-gen count (~70 subzones for regions 1-3)
   - Town space exists and is safe zone

2. **TownGeneratorTest.kt** (18 tests)
   - Town subzone has isSafeZone=true
   - 3-5 NPCs with TradingComponent
   - Finite gold and stock
   - No mobs/traps in town

3. **BossGeneratorTest.kt** (15 tests)
   - Abyssal Lord stats (Strength 80, etc.)
   - Boss designation flag
   - Abyss Heart creation on death
   - LLM AI prompt structure

4. **HiddenExitPlacerTest.kt** (12 tests)
   - Exit placed in Mid Depths Zone 2
   - Conditions (Perception 40 OR Lockpicking 30 OR Strength 50)
   - Target region lazy-generated on first use

**Total Tests**: ~65 tests

**Documentation Updates**:
- None yet (comprehensive update in Chunk 8)

---

### Chunk 4: Town & Merchants (4 hours)

**Description**: Implement town mechanics, rest system, and merchant interactions with disposition-based pricing.

**Affected Modules**:
- `app/src/main/kotlin/com/jcraw/app/handlers/`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/`

**Files to Create/Modify**:

1. **RestHandler.kt** (app/handlers:120 - new)
   - Handles `Intent.Rest`
   - **Logic**:
     - Check if in safe zone (SpacePropertiesComponent.isSafeZone)
     - If not: "You cannot rest here. Danger lurks."
     - If yes:
       - Regen HP to max (Vitality-based)
       - Regen Mana to max (Intelligence-based)
       - Advance gameTime by 100 ticks (simulate rest duration)
       - Narrate: "You rest at the [location]. HP and Mana fully restored. Time passes..."
   - *Reasoning*: Safe zone mechanic encourages town use; time advancement affects respawns

2. **MerchantPricingCalculator.kt** (reasoning:150 - new)
   - Methods:
     - `calculateBuyPrice(basePrice: Int, disposition: Disposition): Int`
       - FRIENDLY: -20% (basePrice * 0.8)
       - NEUTRAL: 0% (basePrice)
       - UNFRIENDLY: +25% (basePrice * 1.25)
       - HOSTILE: +50% (basePrice * 1.5) or refuse to trade
     - `calculateSellPrice(basePrice: Int, disposition: Disposition): Int`
       - FRIENDLY: +10% (basePrice * 1.1)
       - NEUTRAL: 0% (basePrice * 0.5)  // Standard 50% sell price
       - UNFRIENDLY: -20% (basePrice * 0.4)
       - HOSTILE: Refuse to buy
     - `canTrade(merchant: Entity.NPC, player: PlayerState): Boolean`
       - False if disposition HOSTILE or merchant.gold <= 0
   - *Reasoning*: Disposition integration; rewards social gameplay

3. **TownMerchantTemplates.kt** (reasoning:100 - new)
   - Predefined merchant templates:
     - **Potions Merchant**: Stock potions (health, mana, stamina); gold=500
     - **Armor Merchant**: Stock armor (leather, chainmail, plate); gold=1000
     - **Blacksmith**: Stock weapons (swords, axes, bows); gold=800
     - **Innkeeper**: Stock food, torches, rope; gold=300; gives lore hints
   - Each has SocialComponent (disposition=NEUTRAL initially)
   - *Reasoning*: Consistent town merchants across saves; easy to spawn

4. **SafeZoneValidator.kt** (reasoning:80 - new)
   - Methods:
     - `isSafeZone(space: SpacePropertiesComponent): Boolean`
       - Check `space.isSafeZone` flag
     - `validateSafeZone(space: SpacePropertiesComponent): Result<Unit>`
       - Ensure no mobs (entities list empty or all NPCs have isMerchant flag)
       - Ensure no traps
       - Ensure no combat state
   - *Reasoning*: Validation prevents accidental combat in town

5. **TownHandlers.kt** (app/handlers:modify existing MovementHandlers, +100 lines)
   - On movement into town:
     - Clear any combat state
     - Narrate: "You enter the safety of the Town. Your weapons lower."
   - On attempt to attack in safe zone:
     - Block attack: "This is a safe zone. Combat is forbidden."
   - *Reasoning*: Enforces safe zone rules

**Key Technical Decisions**:
- **Rest Advances Time**: 100 ticks per rest
  - *Reasoning*: Affects respawn timers (strategic choice: rest now vs push deeper)
- **Disposition Pricing**: Integrated with social system
  - *Reasoning*: Rewards befriending merchants; consequences for hostility
- **Safe Zone Blocks Combat**: Hard rule, no exceptions
  - *Reasoning*: Town is sanctuary; prevents griefing in multiplayer (future)

**Testing Approach** (`app:test`, `reasoning:test`):

1. **RestHandlerTest.kt** (app:test, 20 tests)
   - Rest in safe zone (HP/Mana regen)
   - Rest in danger zone (fails)
   - gameTime advances by 100 ticks
   - Narration text validation

2. **MerchantPricingCalculatorTest.kt** (reasoning:test, 25 tests)
   - Buy price scaling (all dispositions)
   - Sell price scaling (all dispositions)
   - HOSTILE refuses trade
   - canTrade() validation (gold check)

3. **TownMerchantTemplatesTest.kt** (reasoning:test, 12 tests)
   - All merchants have TradingComponent
   - Stock counts correct
   - Gold amounts correct
   - Disposition starts NEUTRAL

4. **SafeZoneValidatorTest.kt** (reasoning:test, 15 tests)
   - isSafeZone() flag check
   - validateSafeZone() blocks mobs
   - validateSafeZone() blocks traps

5. **TownHandlersTest.kt** (app:test, 18 tests)
   - Combat state cleared on town entry
   - Attack blocked in safe zone
   - Movement narration

**Total Tests**: ~90 tests

**Documentation Updates**:
- None yet (comprehensive update in Chunk 8)

---

### Chunk 5: Respawn System (4 hours)

**Description**: Implement timer-based mob respawn with RespawnComponent integration.

**Affected Modules**:
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/world/`
- `app/src/main/kotlin/com/jcraw/app/`

**Files to Create/Modify**:

1. **RespawnChecker.kt** (reasoning:200 - new)
   - Methods:
     - `checkRespawns(spaceId: String, currentTime: Long): Result<List<Entity.NPC>>`
       - Query RespawnRepository.findBySpace(spaceId)
       - For each component: if shouldRespawn(currentTime), regenerate entity
       - Create new Entity.NPC from originalEntityId (query template or recreate via MobSpawner)
       - Update component.lastKilled = 0 (reset timer)
       - Return list of respawned entities
     - `registerRespawn(entity: Entity.NPC, spaceId: String, respawnTurns: Long): Result<Unit>`
       - Create RespawnComponent(respawnTurns, lastKilled=0, originalEntityId=entity.id)
       - Save to RespawnRepository
     - `markDeath(entityId: String, spaceId: String, gameTime: Long): Result<Unit>`
       - Update RespawnRepository.markKilled(entityId, gameTime)
   - *Reasoning*: Centralized respawn logic; integrates with space entry

2. **MobSpawner.kt** (reasoning:modify existing, +80 lines)
   - Add `spawnWithRespawn(theme: String, mobDensity: Double, difficulty: Int, spaceId: String, respawnTurns: Long): Result<List<Entity.NPC>>`
     - Call existing `spawnEntities()` method
     - For each entity, register respawn via RespawnChecker
     - Respawn time scaling:
       - difficulty 1-10: 300 turns (5 min gameplay)
       - difficulty 10-30: 500 turns (8 min)
       - difficulty 30-60: 1000 turns (16 min)
       - difficulty 60+: null (no respawn, bosses)
   - *Reasoning*: Shallow areas respawn fast (farming); deep areas slow/never

3. **SpacePopulator.kt** (reasoning:modify existing, +60 lines)
   - Update `populate()` method to use `spawnWithRespawn()`
   - Safe zones: Skip mob spawning entirely
   - *Reasoning*: Integration point; respawn tracking automatic

4. **MudGameEngine.kt** (app:modify existing, +100 lines)
   - On space entry (Intent.Travel):
     - Call RespawnChecker.checkRespawns(newSpaceId, worldState.gameTime)
     - Add respawned entities to space
     - Narrate if entities respawned: "The area has been reclaimed by [mob types]."
   - On entity death in combat:
     - Call RespawnChecker.markDeath(entityId, currentSpaceId, worldState.gameTime)
   - *Reasoning*: Real-time respawn checks on movement; death tracking automatic

5. **RespawnConfig.kt** (core:40 - new)
   - Data class for respawn configuration:
   ```kotlin
   data class RespawnConfig(
       val enabled: Boolean = true,
       val difficultyScaling: Map<IntRange, Long> = mapOf(
           1..10 to 300L,
           11..30 to 500L,
           31..60 to 1000L,
           61..100 to Long.MAX_VALUE  // No respawn
       )
   )
   ```
   - *Reasoning*: Configurable respawn rates; easy to tweak balance

**Key Technical Decisions**:
- **Respawn on Entry**: Check when player enters space (not periodic)
  - *Reasoning*: Simpler than background timer; player sees immediate effect
- **Scaling Respawn Times**: Shallow fast, deep slow
  - *Reasoning*: Encourages shallow farming; makes deep areas feel dangerous
- **Boss No Respawn**: respawnTurns = Long.MAX_VALUE
  - *Reasoning*: Bosses are one-time encounters; prevents trivial farming

**Testing Approach** (`reasoning:test`, `app:test`):

1. **RespawnCheckerTest.kt** (reasoning:test, 30 tests)
   - `checkRespawns()` with various timers
   - Entities respawn after threshold
   - Entities don't respawn before threshold
   - `registerRespawn()` creates component
   - `markDeath()` updates lastKilled

2. **MobSpawnerTest.kt** (reasoning:test, modify existing, +15 tests)
   - `spawnWithRespawn()` creates entities + components
   - Respawn time scaling by difficulty
   - Safe zones skip spawning

3. **SpacePopulatorTest.kt** (reasoning:test, modify existing, +12 tests)
   - `populate()` registers respawns
   - Safe zones have no mobs

4. **MudGameEngineTest.kt** (app:test, modify existing, +20 tests)
   - Space entry triggers respawn check
   - Entities added to space after respawn
   - Entity death marks lastKilled
   - Narration on respawn

5. **RespawnConfigTest.kt** (core:test, 8 tests)
   - Config serialization
   - Difficulty scaling lookup

**Total Tests**: ~85 tests

**Documentation Updates**:
- None yet (comprehensive update in Chunk 8)

---

### Chunk 6: Death & Corpse System (5 hours)

**Description**: Implement player death handling, corpse creation, respawn at town, and corpse retrieval mechanics.

**Affected Modules**:
- `app/src/main/kotlin/com/jcraw/app/`
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/`

**Files to Create/Modify**:

1. **DeathHandler.kt** (reasoning:250 - new)
   - Methods:
     - `handlePlayerDeath(player: PlayerState, currentSpace: SpacePropertiesComponent, gameTime: Long): Result<DeathResult>`
       - Create CorpseData:
         - id: UUID
         - playerId: player.id
         - spaceId: current space
         - inventory: player.inventory
         - equipment: player equipped items
         - gold: player.gold
         - decayTimer: gameTime + 5000L (turns)
         - looted: false
       - Save to CorpseRepository
       - Create new PlayerState:
         - Fresh skills (level 1, unlocked basics)
         - Empty inventory (starter dagger, clothes)
         - Reset gold = 0
         - Reset relations to NEUTRAL
         - Spawn at town (townSpaceId from AncientAbyssData)
       - Return DeathResult(newPlayer, corpseId, townSpaceId)
     - `createCorpseNarration(oldPlayer: PlayerState, corpseSpaceName: String): String`
       - "You have perished! Your corpse lies in [corpseSpaceName], awaiting retrieval. A new soul awakens in the Town, driven by fate to reclaim what was lost..."
   - *Reasoning*: Full death loop; corpse retrieval creates risk/reward

2. **CorpseManager.kt** (reasoning:180 - new)
   - Methods:
     - `findPlayerCorpses(playerId: PlayerId): Result<List<CorpseData>>`
       - Query CorpseRepository.findByPlayer(playerId)
     - `lootCorpse(corpseId: String, player: PlayerState): Result<LootResult>`
       - Query CorpseRepository
       - Transfer inventory/equipment/gold to player (weight check)
       - Mark corpse.looted = true
       - Delete corpse from DB
       - Return LootResult(itemsTransferred, overWeightItems)
     - `cleanupDecayedCorpses(currentTime: Long): Result<Int>`
       - Call CorpseRepository.deleteDecayed(currentTime)
       - Return count of deleted corpses
     - `describeCorpse(corpse: CorpseData): String`
       - "The corpse of [oldPlayerId] lies here, clutching [inventory summary]. It will decay in [turns remaining] turns."
   - *Reasoning*: UI helpers for corpse interaction

3. **Intent.kt** (perception:modify existing, +40 lines)
   - Add `data class LootCorpse(val corpseId: String = "nearest") : Intent()`
   - Handles looting player corpses (distinct from NPC corpses via Intent.Loot)
   - *Reasoning*: Separate intent for clarity (player corpse vs NPC corpse)

4. **CorpseHandlers.kt** (app/handlers:200 - new)
   - Handles `Intent.LootCorpse`
   - **Logic**:
     - If corpseId == "nearest": Find corpses in current space
     - Call CorpseManager.lootCorpse()
     - Narrate items transferred
     - If over-weight: "Some items too heavy: [list]"
     - Mark corpse despawned in UI
   - *Reasoning*: User-friendly "loot corpse" command

5. **MudGameEngine.kt** (app:modify existing, +150 lines)
   - On combat death (HP <= 0):
     - Call DeathHandler.handlePlayerDeath()
     - Update WorldState with new player
     - Archive old player ID (DB flag: archived=true)
     - Narrate death + corpse location
     - Transition to town (force movement)
   - Periodic cleanup (every 100 turns):
     - Call CorpseManager.cleanupDecayedCorpses(gameTime)
   - *Reasoning*: Integrated death flow; cleanup prevents DB bloat

6. **CorpseDecayScheduler.kt** (app:100 - new)
   - Kotlin coroutine for periodic cleanup
   - Methods:
     - `scheduleCleanup(delay: Duration = 100.turns)` - Every 100 turns
     - `cancelCleanup()` - Stop on game exit
   - *Reasoning*: Automated cleanup; unobtrusive

**Key Technical Decisions**:
- **Corpse Decay**: 5000 turns (~83 hours at 1 turn/min)
  - *Reasoning*: Long enough for retrieval; prevents permanent clutter
- **New Player at Town**: Not random spawn
  - *Reasoning*: Predictable; town is safe starting point
- **Weight Check on Loot**: Can't carry everything if overweight
  - *Reasoning*: Strategic choice (what to retrieve first)
- **Archived Player IDs**: Keep history for stats
  - *Reasoning*: Track total deaths, old characters

**Testing Approach** (`reasoning:test`, `app:test`):

1. **DeathHandlerTest.kt** (reasoning:test, 30 tests)
   - `handlePlayerDeath()` creates corpse
   - New player has fresh skills/inventory
   - Corpse has full old inventory
   - Decay timer calculated
   - Narration text validation

2. **CorpseManagerTest.kt** (reasoning:test, 35 tests)
   - `findPlayerCorpses()` queries by player
   - `lootCorpse()` transfers items
   - Weight check (over-weight items rejected)
   - `cleanupDecayedCorpses()` deletes old corpses (test with 50 corpses, various timers)
   - `describeCorpse()` text validation

3. **CorpseHandlersTest.kt** (app:test, 25 tests)
   - Intent.LootCorpse handling
   - "nearest" corpse finding
   - Narration on loot
   - Over-weight handling

4. **MudGameEngineTest.kt** (app:test, modify existing, +25 tests)
   - Combat death triggers handlePlayerDeath()
   - Player spawns at town
   - Old player ID archived
   - Periodic cleanup runs every 100 turns

5. **CorpseDecaySchedulerTest.kt** (app:test, 12 tests)
   - Cleanup scheduled correctly
   - Coroutine cancellation on exit
   - No memory leaks

**Total Tests**: ~127 tests

**Documentation Updates**:
- None yet (comprehensive update in Chunk 8)

---

### Chunk 7: Boss, Treasure & Victory (4 hours)

**Description**: Implement Abyssal Lord boss fight, Abyss Heart treasure, hidden exit, and victory condition.

**Affected Modules**:
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/`
- `app/src/main/kotlin/com/jcraw/app/handlers/`

**Files to Create/Modify**:

1. **BossLootHandler.kt** (reasoning:120 - new)
   - Methods:
     - `generateBossLoot(boss: Entity.NPC): List<ItemInstance>`
       - If boss.bossDesignation.victoryFlag == "abyssal_lord_defeated":
         - Return Abyss Heart item
       - Else: Standard LEGENDARY loot
     - `createAbyssHeart(): ItemInstance`
       - Template: LEGENDARY accessory
       - Effects: +50 all stats, +100% XP gain
       - Quest flag: "abyss_heart_retrieved" (set on pickup)
   - *Reasoning*: Boss loot special-cased; Abyss Heart unique

2. **VictoryChecker.kt** (reasoning:150 - new)
   - Methods:
     - `checkVictoryCondition(player: PlayerState, currentSpace: SpacePropertiesComponent): VictoryResult`
       - Check if in town (space.isSafeZone)
       - Check if player has Abyss Heart in inventory
       - If both: Return VictoryResult.Won(narration)
       - Else: VictoryResult.NotYet
     - `generateVictoryNarration(player: PlayerState): String`
       - LLM prompt: "Generate epic victory narration. Player [name] has retrieved the Abyss Heart and returned to Town. The dungeon is conquered. 3-4 sentences."
       - Fallback: "You have triumphed! The Abyss Heart pulses in your grasp. The dungeon trembles, defeated. You are legend."
   - *Reasoning*: Victory check on town entry; LLM for epic narration

3. **HiddenExitHandler.kt** (app/handlers:180 - new)
   - Handles hidden exit discovery
   - **Logic**:
     - On Intent.Travel to hidden exit:
       - Check conditions (Perception 40 OR Lockpicking 30 OR Strength 50)
       - If pass: Reveal exit: "You discover a cracked wall. Starlight streams through. Beyond lies the Surface Wilderness..."
       - Create "Surface Wilderness" REGION (lazy gen on first use)
       - Allow travel
     - If fail: "The wall looks solid. Perhaps higher Perception or tools could reveal secrets."
   - *Reasoning*: Easter egg; seeds open world expansion

4. **BossCombatEnhancements.kt** (reasoning:100 - new)
   - Enhance AI for boss fights:
     - Methods:
       - `getBossAIPrompt(boss: Entity.NPC, combatState: CombatState): String`
         - "You are the Abyssal Lord, an ancient demon of immense power. You wield fire magic and dark sorcery. Current HP: [hp]. Enemy HP: [player hp]. Strategy: Use fire magic at range, summon minions if below 50% HP, melee if cornered. Be ruthless but strategic."
       - `handleBossSummon(boss: Entity.NPC, gameTime: Long): Result<List<Entity.NPC>>`
         - If boss.hp < boss.maxHp * 0.5 and not summoned yet:
           - Summon 2-3 minor demons (difficulty scaled)
           - Set summonedFlag = true
   - *Reasoning*: Boss fights more dynamic; LLM-driven tactics

5. **VictoryHandlers.kt** (app/handlers:120 - new)
   - On town entry:
     - Call VictoryChecker.checkVictoryCondition()
     - If victory:
       - Narrate victory
       - Save final state (for stats)
       - Prompt: "Play again? (y/n)" or "Restart with same character? (y/n)"
       - Optional: Clear dungeon, respawn all (NG+)
   - *Reasoning*: Victory loop; optional NG+ for replayability

**Key Technical Decisions**:
- **Victory = Return to Town**: Not just boss kill
  - *Reasoning*: Encourages full loop (return home); prevents accidental loss of treasure
- **Boss Summons**: Dynamic encounter
  - *Reasoning*: Boss fight more engaging; tests player tactics
- **Hidden Exit Optional**: Not required for victory
  - *Reasoning*: Easter egg for explorers; doesn't block main quest

**Testing Approach** (`reasoning:test`, `app:test`):

1. **BossLootHandlerTest.kt** (reasoning:test, 15 tests)
   - Abyssal Lord drops Abyss Heart
   - Other bosses drop LEGENDARY loot
   - Abyss Heart stats validation (+50 all stats)

2. **VictoryCheckerTest.kt** (reasoning:test, 20 tests)
   - Victory check (in town + has Abyss Heart)
   - Not victory (missing one condition)
   - LLM narration prompt structure

3. **HiddenExitHandlerTest.kt** (app:test, 18 tests)
   - Skill check pass (Perception 40)
   - Skill check fail (Perception 30)
   - Exit revealed on pass
   - Surface Wilderness region created

4. **BossCombatEnhancementsTest.kt** (reasoning:test, 15 tests)
   - Boss AI prompt structure
   - Summon triggers at 50% HP
   - Summons only once (flag check)

5. **VictoryHandlersTest.kt** (app:test, 18 tests)
   - Victory narration on town entry with Abyss Heart
   - No victory without Abyss Heart
   - Restart prompt

**Total Tests**: ~86 tests

**Documentation Updates**:
- None yet (comprehensive update in Chunk 8)

---

### Chunk 8: Integration, Testing & Documentation (5 hours)

**Description**: Full system integration, comprehensive testing, bot scenario, and documentation updates.

**Affected Modules**:
- All modules
- `testbot/src/test/kotlin/com/jcraw/mud/testbot/scenarios/`
- `docs/`

**Files to Create/Modify**:

1. **StartingDungeonIntegrationTest.kt** (reasoning:test:250 - new)
   - Full integration test (no bot):
     - Initialize Ancient Abyss
     - Verify 4 regions created
     - Pre-gen count (~70 subzones)
     - Town exists with merchants
     - Player spawns at town
     - Navigate to Mid Depths
     - Find hidden exit (Perception check)
     - Navigate to Lower Depths
     - Boss space exists
     - Kill boss (simulated combat)
     - Loot Abyss Heart
     - Return to town
     - Victory condition triggered
   - *Reasoning*: Fast integration test; data integrity focus

2. **MurderhoboBotTest.kt** (testbot:400 - new)
   - Bot scenario: Full murderhobo playthrough
   - **Steps**:
     1. Initialize Ancient Abyss
     2. Start at town, buy potions
     3. Farm Upper Depths (kill goblins, loot, return to town, sell)
     4. Check respawn (wait 300 turns, re-enter Upper Depths, goblins respawned)
     5. Descend to Mid Depths (harder combat)
     6. Die in combat (HP <= 0)
     7. Verify corpse created in Mid Depths
     8. New player spawns at town
     9. Farm Upper Depths again (rebuild gear)
     10. Descend to Mid Depths, find corpse, loot inventory
     11. Continue to Lower Depths
     12. Descend to Abyssal Core
     13. Fight Abyssal Lord (summons minions at 50% HP)
     14. Kill boss, loot Abyss Heart
     15. Return to town (navigate upward)
     16. Victory narration
   - **Assertions**:
     - Respawn works (goblin count same after 300 turns)
     - Corpse persists (same inventory)
     - World unchanged (same rooms/exits/flags)
     - Boss summons at 50% HP
     - Victory triggered with Abyss Heart in town
   - *Reasoning*: End-to-end validation; tests full murderhobo loop + death/corpse

3. **CLAUDE.md** (docs:modify existing)
   - Update "What's Implemented":
     - ✅ Starting Dungeon (Ancient Abyss)
     - ✅ RespawnComponent with timer-based mob respawning
     - ✅ Death & Corpse System (corpse creation, retrieval, decay)
     - ✅ Town safe zone with merchants
     - ✅ Boss fight (Abyssal Lord) + victory condition
     - ✅ Hidden exit to open world
   - Update commands:
     - `rest` - Rest at safe zone to regen HP/Mana
     - `loot corpse` - Retrieve items from your corpse
     - `corpses` - List your corpses and locations
   - *Reasoning*: Primary project doc

4. **STARTING_DUNGEON.md** (docs:new file:350)
   - Comprehensive starting dungeon documentation:
     - Overview (Ancient Abyss concept)
     - Architecture (4 regions, town, respawn system)
     - Gameplay loop (murderhobo: farm → gear → descend)
     - Death mechanics (corpse creation, retrieval, decay)
     - Boss fight (Abyssal Lord tactics, summons)
     - Victory condition (return Abyss Heart to town)
     - Hidden exit (Surface Wilderness)
     - Merchant guide (town NPCs, disposition pricing)
     - Respawn mechanics (timer scaling by difficulty)
     - Technical details (components, DB schema)
   - *Reasoning*: Dedicated guide for complex feature

5. **ARCHITECTURE.md** (docs:modify existing)
   - Add starting dungeon module descriptions:
     - RespawnComponent, CorpseData
     - DeathHandler, CorpseManager
     - RespawnChecker
     - VictoryChecker
     - Town/Boss generators
   - Update data flow diagram (death → corpse → respawn)
   - *Reasoning*: Technical reference

6. **GETTING_STARTED.md** (docs:modify existing)
   - Add starting dungeon section:
     - How to start (game initializes to town)
     - Town safe zone explanation
     - Resting mechanics
     - Death explanation (corpse retrieval encouraged)
     - Merchant trading
     - Victory goal (kill boss, return treasure)
   - *Reasoning*: User-facing guide

7. **WorldHandlers.kt** (app/handlers:modify existing, +50 lines)
   - Integrate respawn checks on Intent.Travel
   - Integrate corpse display in space descriptions
   - *Reasoning*: Final handler integration

8. **ClientWorldHandlers.kt** (client/handlers:modify existing, +40 lines)
   - GUI corpse display
   - Victory screen
   - *Reasoning*: GUI client parity

**Key Technical Decisions**:
- **Bot Test Priority**: Murderhobo + corpse retrieval scenario most critical
  - *Reasoning*: Tests full loop; most complex user journey
- **Documentation Before Complete**: Write docs early
  - *Reasoning*: Forces clarity; reveals design gaps

**Testing Approach** (`reasoning:test`, `testbot`, `app:test`):

1. **StartingDungeonIntegrationTest.kt** (reasoning:test, 1 test, ~50 assertions)
   - Full dungeon initialization
   - Navigation end-to-end
   - Boss fight
   - Victory condition

2. **MurderhoboBotTest.kt** (testbot, 1 scenario, ~70 assertions)
   - Farm → death → corpse retrieval → boss → victory
   - Respawn validation
   - World persistence validation

3. **WorldHandlersTest.kt** (app:test, modify existing, +20 tests)
   - Respawn check on movement
   - Corpse display in descriptions

4. **ClientWorldHandlersTest.kt** (client:test, modify existing, +15 tests)
   - GUI corpse rendering
   - Victory screen

**Total Tests**: ~105 tests + 2 comprehensive scenarios

**Documentation Updates**:
- ✅ CLAUDE.md (starting dungeon features)
- ✅ STARTING_DUNGEON.md (new comprehensive guide)
- ✅ ARCHITECTURE.md (module structure, components)
- ✅ GETTING_STARTED.md (user commands, gameplay loop)

---

## Summary

**Total Implementation**:
- **8 chunks** × 3-5 hours each = **32 hours**
- **~662 unit/integration tests** across all chunks
- **2 comprehensive scenarios** (integration test + bot test)
- **4 documentation files** updated/created

**Chunk Sequence**:
1. Foundation (RespawnComponent, CorpseData, safe zones, boss flags) - 4h
2. Database Extensions (respawn/corpse tables, repositories) - 3h
3. Fixed Dungeon Pre-Generation (Ancient Abyss structure) - 4h
4. Town & Merchants (safe zone, rest, disposition pricing) - 4h
5. Respawn System (timer-based mob respawning) - 4h
6. Death & Corpse System (player death, corpse retrieval, decay) - 5h
7. Boss, Treasure & Victory (Abyssal Lord, Abyss Heart, hidden exit) - 4h
8. Integration (handlers, bot test, docs) - 5h

**Key Principles Applied**:
- **KISS**: Simple boolean flags (safe zone, boss) vs complex components
- **Modularity**: Clear separation (:core data, :reasoning logic, :memory persistence)
- **Immutability**: All state transitions return new copies
- **Testing Focus**: Behavior and contracts (murderhobo bot scenario validates full loop)
- **LLM Leverage**: Boss AI prompts, victory narration
- **Backward Compat**: Starting dungeon is opt-in (can still use procedural dungeons)

**World System Requirements Updates**:

Based on analysis, the World Generation V2 system needs these extensions (all addressed in plan):

1. ✅ **RespawnComponent** - Added in Chunk 1
2. ✅ **ComponentType.CORPSE, RESPAWN** - Added in Chunk 1
3. ✅ **Corpse persistence** - Added in Chunk 2
4. ✅ **Safe zone flag** - Added in Chunk 1 (SpacePropertiesComponent.isSafeZone)
5. ✅ **gameTime tracking** - Already exists in WorldState (no changes needed)

**No breaking changes to World Gen V2 required**. All extensions are additive.

**Success Criteria**:
- ✅ Pre-gen Ancient Abyss with 4 fixed regions
- ✅ Town safe zone with merchants and disposition-based pricing
- ✅ Mob respawn with difficulty-scaled timers
- ✅ Player death → corpse creation → new player spawn at town
- ✅ Corpse retrieval with inventory transfer
- ✅ Boss fight with AI-driven tactics and summons
- ✅ Victory condition (return Abyss Heart to town)
- ✅ Hidden exit to open world
- ✅ World persistence across player deaths (corpses, flags, changes persist)
- ✅ Bot scenario validates full murderhobo loop + corpse retrieval
- ✅ Comprehensive documentation
