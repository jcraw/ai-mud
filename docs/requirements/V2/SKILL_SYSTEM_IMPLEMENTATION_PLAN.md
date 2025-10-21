# Skill System Implementation Plan

## Overview

This document provides the architectural design and implementation strategy for the AI-MUD Skill System. The system adds dynamic, use-based skill progression with infinite growth potential, multi-skill combinations, resource management, and integration with existing social, combat, and memory systems.

**Estimated Time**: 25-35 hours across 12 phases
**Complexity**: Similar to Social System (component-based, database-backed, multi-module)

---

## Architecture Overview

### Design Principles

1. **Component-Based**: Extends existing ECS (Entity Component System) from social system
2. **Use-Based Progression**: Skills improve through practice, not abstract XP pools
3. **Infinite Scaling**: No hard caps; exponential XP curve after level 100 for godlike progression
4. **Multi-Skill Combinations**: Actions can leverage multiple skills with weighted resolution
5. **Resource Management**: Skills like "Mana Reserve" provide consumable resources
6. **Modular Integration**: Plugs into perception, reasoning, memory, and persistence modules

### Key Features

- **30+ Predefined Skills**: Core stats, combat, magic, rogue, resources, resistances
- **Perk System**: Every 10 levels, choose 1 of 2 perks (abilities or passives)
- **Unlock Methods**: Attempt (low chance), Observation (1.5x XP buff), Training (2x XP + level 1), Prerequisites
- **XP Formula**: Success grants full XP, failure grants 20%; levels require `100 * level^2` XP (exponential after 100)
- **Social Integration**: Replaces StubSkillSystem; disposition affects training/buffs
- **RAG Integration**: Stores skill history for narrative coherence

---

## Module Distribution

### :core
- `ComponentType.SKILL` enum value
- `SkillComponent` data class (holds Map<String, SkillState>)
- `SkillState` data class (level, xp, unlocked, perks, etc.)
- `Perk` data class (name, description, type, effect)
- `PerkType` sealed class (ABILITY, PASSIVE)
- `SkillEvent` sealed class (SkillUnlocked, XpGained, LevelUp, PerkUnlocked, SkillCheckAttempt)
- `SkillRepository`, `SkillComponentRepository` interfaces (in core/repository/)
- `CombatSystem`, `MagicSystem` stub interfaces (future modules)

### :perception
- `Intent.UseSkill(skill: String?, action: String)` - Perform skill-based action
- `Intent.TrainSkill(skill: String, method: String)` - Train with NPC/mentor
- `Intent.ChoosePerk(skillName: String, choice: Int)` - Select perk at milestone
- `Intent.ViewSkills` - Display skill sheet
- Updated `IntentRecognizer` to parse skill-related actions via LLM

### :reasoning
- `SkillManager` - Core skill logic (grantXp, unlockSkill, checkSkill, updateComponent)
- `PerkSelector` - Manages perk choices and unlocks
- `SkillComboResolver` - Identifies and resolves multi-skill actions
- `ResourceManager` - Tracks mana/chi pools, consumption, regeneration
- `ResistanceCalculator` - Calculates damage reduction from resistance skills
- `SkillDefinitions` - Predefined skill catalog (30+ skills)
- `PerkDefinitions` - Predefined perk trees for each skill

### :memory
- Updated `MemoryManager` - Stores skill usage history for RAG
- `recallSkillHistory(entityId, skillName)` - Retrieves past skill events
- `SkillDatabase` - SQLite persistence (skill_components, skills, skill_events_log)
- `SQLiteSkillRepository`, `SQLiteSkillComponentRepository` - Database implementations

### :action
- `SkillFormatter` - Formats skill sheets, level-up messages, perk choices

### :app, :client, :testbot
- Wire `SkillManager` into game loop
- Handle skill intents (UseSkill, TrainSkill, ChoosePerk, ViewSkills)
- UI elements for skill display and perk selection

---

## Database Schema

### skill_components
```sql
CREATE TABLE skill_components (
    entity_id TEXT PRIMARY KEY,
    component_data TEXT NOT NULL  -- JSON-serialized SkillComponent
);
```

### skills (Denormalized for fast queries)
```sql
CREATE TABLE skills (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entity_id TEXT NOT NULL,
    skill_name TEXT NOT NULL,
    level INTEGER NOT NULL DEFAULT 0,
    xp INTEGER NOT NULL DEFAULT 0,
    unlocked BOOLEAN NOT NULL DEFAULT 0,
    tags TEXT,  -- JSON array
    perks TEXT, -- JSON array
    resource_type TEXT,
    UNIQUE(entity_id, skill_name)
);
CREATE INDEX idx_skills_entity ON skills(entity_id);
CREATE INDEX idx_skills_name ON skills(skill_name);
```

### skill_events_log
```sql
CREATE TABLE skill_events_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entity_id TEXT NOT NULL,
    skill_name TEXT NOT NULL,
    event_type TEXT NOT NULL,  -- 'xp_gained', 'level_up', 'unlocked', 'perk_chosen'
    xp_gained INTEGER,
    new_level INTEGER,
    timestamp INTEGER NOT NULL
);
CREATE INDEX idx_events_entity ON skill_events_log(entity_id);
```

---

## Phase-by-Phase Implementation Guide

### Phase 1: Foundation - Core Data Models (3-4h)

**Goal**: Create immutable data structures for skills, perks, and events.

**Tasks**:
1. Add `SKILL` to `ComponentType` enum in `core/src/main/kotlin/com/jcraw/mud/core/Component.kt`
2. Create `SkillComponent` data class implementing `Component`
3. Create `SkillState` data class with fields: `level`, `xp`, `xpToNext`, `unlocked`, `tags`, `perks`, `resourceType`
4. Implement `SkillState` methods:
   - `addXp(amount: Long): SkillState` - Adds XP, levels up if threshold crossed
   - `unlock(): SkillState` - Sets `unlocked = true`
   - `getEffectiveLevel(): Int` - Level + temp buffs (stub for now)
   - `calculateXpToNext(level: Int): Long` - Formula: `if (level <= 100) 100 * level^2 else 100 * level^2 * (level / 100)^1.5`
5. Create `Perk` data class
6. Create `PerkType` sealed class with `ABILITY` and `PASSIVE` objects
7. Create `SkillEvent` sealed class hierarchy (5 event types)

**Tests**: 15-20 unit tests
- XP calculation edge cases (level 1, 50, 100, 101, 200)
- Level-up threshold detection
- Unlock logic
- Immutability (verify copy-on-update)

**Files**:
- `core/src/main/kotlin/com/jcraw/mud/core/Component.kt` (extend existing)
- `core/src/main/kotlin/com/jcraw/mud/core/SkillEvent.kt` (new)
- `core/src/test/kotlin/com/jcraw/mud/core/SkillStateTest.kt` (new)

---

### Phase 2: Database Layer (4-5h)

**Goal**: Persistent storage for skill data with repository pattern.

**Tasks**:
1. Design schema (3 tables: skill_components, skills, skill_events_log)
2. Create `SkillRepository` interface in `core/src/main/kotlin/com/jcraw/mud/core/repository/SkillRepository.kt`
   - Methods: `findByEntityId`, `findByTag`, `updateXp`, `unlockSkill`, `addPerk`, `logEvent`
3. Create `SkillComponentRepository` interface
   - Methods: `save`, `load`, `delete`
4. Implement `SQLiteSkillRepository` in `memory/src/main/kotlin/com/jcraw/mud/memory/skill/SQLiteSkillRepository.kt`
5. Implement `SQLiteSkillComponentRepository`
6. Create `SkillDatabase` class to initialize tables and provide repository instances

**Tests**: 15-20 database tests
- CRUD operations for skills
- Transaction rollback on errors
- Index performance (query by entity_id, skill_name, tags)
- Event logging

**Files**:
- `core/src/main/kotlin/com/jcraw/mud/core/repository/SkillRepository.kt` (new)
- `core/src/main/kotlin/com/jcraw/mud/core/repository/SkillComponentRepository.kt` (new)
- `memory/src/main/kotlin/com/jcraw/mud/memory/skill/SkillDatabase.kt` (new)
- `memory/src/main/kotlin/com/jcraw/mud/memory/skill/SQLiteSkillRepository.kt` (new)
- `memory/src/main/kotlin/com/jcraw/mud/memory/skill/SQLiteSkillComponentRepository.kt` (new)
- `memory/src/test/kotlin/com/jcraw/mud/memory/skill/SkillDatabaseTest.kt` (new)

**Notes**: Similar to `SocialDatabase` pattern; use JSON serialization for complex fields.

---

### Phase 3: Skill Manager (Core Logic) (4-5h)

**Goal**: Implement core skill progression logic (XP, leveling, checks).

**Tasks**:
1. Create `SkillManager` class in `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/SkillManager.kt`
   - Constructor: `SkillManager(skillRepo: SkillRepository, componentRepo: SkillComponentRepository, rng: Random)`
2. Implement `grantXp(entityId: String, skillName: String, baseXp: Long, success: Boolean): SkillEvent`
   - Calculate XP (full if success, 20% if failure)
   - Add XP to SkillState, level up if threshold crossed
   - Return SkillEvent (XpGained or LevelUp)
3. Implement `unlockSkill(entityId: String, skillName: String, method: UnlockMethod): SkillEvent?`
   - Handle Attempt (d100 < 5%), Observation (1.5x buff), Training (level 1 + 2x buff), Prerequisite checks
4. Implement `checkSkill(entityId: String, skillName: String, difficulty: Int, opposedSkill: String? = null): SkillCheckResult`
   - Roll: d20 + skillLevel vs difficulty (or opposed roll)
   - Return result with success, margin, narrative
5. Helper methods: `getSkillComponent(entityId)`, `updateSkillComponent(entityId, component)`
6. Create `UnlockMethod` sealed class (4 types)
7. Create `SkillCheckResult` data class

**Tests**: 20-25 unit tests
- XP granting (success vs failure)
- Leveling thresholds
- Unlock methods (all 4 types)
- Skill checks (easy/medium/hard difficulties)
- Opposed checks
- Edge cases (negative XP, non-existent skills)

**Files**:
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/SkillManager.kt` (new)
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/UnlockMethod.kt` (new)
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/SkillCheckResult.kt` (new)
- `reasoning/src/test/kotlin/com/jcraw/mud/reasoning/skill/SkillManagerTest.kt` (new)

---

### Phase 4: Intent Recognition (3-4h)

**Goal**: Parse player input to detect skill-related actions.

**Tasks**:
1. Add 4 new intents to `perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt`
   - `UseSkill(skill: String?, action: String)` - "cast fireball", "pick lock"
   - `TrainSkill(skill: String, method: String)` - "train sword fighting with knight"
   - `ChoosePerk(skillName: String, choice: Int)` - "choose perk 1 for sword fighting"
   - `ViewSkills` - "show skills", "skill sheet", "abilities"
2. Update `IntentRecognizer` LLM system prompt to detect skill actions
   - Add rule for identifying skill names in player input
   - Map actions to skills (e.g., "cast fireball" → UseSkill("Fire Magic", "cast fireball"))
3. Add fallback parsing for skill intents (regex patterns for common commands)

**Tests**: 8-10 intent parsing tests
- UseSkill detection (magic, lockpicking, stealth)
- TrainSkill with NPC names
- ChoosePerk parsing
- ViewSkills variations

**Files**:
- `perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt` (extend)
- `perception/src/main/kotlin/com/jcraw/mud/perception/IntentRecognizer.kt` (update)
- `perception/src/test/kotlin/com/jcraw/mud/perception/IntentTest.kt` (extend)

---

### Phase 5: Multi-Skill Combinations & Resources (4-5h)

**Goal**: Enable actions that use multiple skills; manage consumable resources (mana, chi).

**Tasks**:
1. Create `SkillComboResolver` in `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/SkillComboResolver.kt`
   - `identifySkills(action: String): Map<String, Float>` - LLM/rules to map action → skills with weights
   - `resolveCombo(entityId: String, skillWeights: Map<String, Float>, difficulty: Int): SkillCheckResult`
     - Weighted average: `effectiveLevel = sum(skillLevel[i] * weight[i])`
     - Roll d20 + effectiveLevel vs difficulty
2. Create `ResourceManager` in `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/ResourceManager.kt`
   - `getResourcePool(entityId: String, resourceType: String): ResourcePool` - max = skill level * 10, current tracked
   - `consumeResource(entityId: String, resourceType: String, amount: Int): Boolean`
   - `regenerateResource(entityId: String, resourceType: String)` - regen based on "Flow" skill
3. Create `ResistanceCalculator` in `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/ResistanceCalculator.kt`
   - `calculateReduction(entityId: String, damageType: String, damage: Int): Int`
   - Reduction = `damage * (resistanceLevel / 2) / 100` (e.g., level 20 Fire Resistance = 10% reduction)

**Tests**: 15-20 tests
- Combo resolution (2-skill, 3-skill, weighted)
- Resource consumption (success, failure, insufficient)
- Resource regeneration
- Resistance calculations (various levels, damage types)

**Files**:
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/SkillComboResolver.kt` (new)
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/ResourceManager.kt` (new)
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/ResistanceCalculator.kt` (new)
- `reasoning/src/test/kotlin/com/jcraw/mud/reasoning/skill/SkillComboResolverTest.kt` (new)
- `reasoning/src/test/kotlin/com/jcraw/mud/reasoning/skill/ResourceManagerTest.kt` (new)

---

### Phase 6: Perk System (3-4h)

**Goal**: Milestone perks with player choice at levels 10, 20, 30, etc.

**Tasks**:
1. Create `PerkSelector` in `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/PerkSelector.kt`
   - `getPerkChoices(skillName: String, level: Int): List<Perk>` - Returns 2 choices for milestone
   - `selectPerk(entityId: String, skillName: String, perkChoice: Perk): SkillEvent`
   - `hasPendingPerkChoice(entityId: String, skillName: String): Boolean`
2. Create `PerkDefinitions` in `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/PerkDefinitions.kt`
   - Define perks for each skill (2 choices per milestone 10/20/30...)
   - Example: Sword Fighting L10: "Quick Strike" (ability) vs "Feint" (ability)
   - Example: Sword Fighting L20: "+15% Damage" (passive) vs "+10% Parry" (passive)

**Tests**: 10-12 tests
- Perk choice retrieval
- Perk selection (adds to SkillState)
- Pending perk detection
- Milestone validation (no perks at L9, perks at L10)

**Files**:
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/PerkSelector.kt` (new)
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/PerkDefinitions.kt` (new)
- `reasoning/src/test/kotlin/com/jcraw/mud/reasoning/skill/PerkSelectorTest.kt` (new)

---

### Phase 7: Predefined Skills & Seed Data (2-3h)

**Goal**: Catalog of 30+ skills with metadata; seed database.

**Tasks**:
1. Create `SkillDefinitions` in `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/SkillDefinitions.kt`
2. Define `SkillDefinition` data class (name, description, tags, baseUnlockChance, prereqs, resourceType)
3. Implement skill catalog:
   - **Core Stats** (6): Strength, Agility, Vitality, Intelligence, Wisdom, Charisma (tags: ["stat"])
   - **Combat** (6): Sword Fighting, Axe Mastery, Bow Accuracy, Light Armor, Heavy Armor, Shield Use
   - **Rogue** (5): Stealth, Backstab, Lockpicking, Trap Disarm, Trap Setting
   - **Elemental Magic** (7): Fire Magic, Water Magic, Earth Magic, Air Magic, Gesture Casting, Chant Casting, Magical Projectile Accuracy
   - **Advanced Magic** (3): Summoning, Necromancy, Elemental Affinity (prereq: Fire Magic 50)
   - **Resources** (4): Mana Reserve, Mana Flow, Chi Reserve, Chi Flow
   - **Resistances** (3): Fire Resistance, Poison Resistance, Slashing Resistance
   - **Other** (2): Blacksmithing, Diplomacy
4. Implement `seedSkills(repository: SkillRepository)` - Loads predefined skills into DB
5. Create starter skill sets for archetypes (Warrior, Rogue, Mage, Cleric, Bard)

**Tests**: 8-10 tests
- Skill definition structure
- Prerequisite validation
- Tag filtering
- Starter set completeness

**Files**:
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/SkillDefinitions.kt` (new)
- `reasoning/src/test/kotlin/com/jcraw/mud/reasoning/skill/SkillDefinitionsTest.kt` (new)

---

### Phase 8: Social System Integration (3-4h)

**Goal**: Replace StubSkillSystem; wire skill checks to social interactions.

**Tasks**:
1. Update `DispositionManager` to call `SkillManager.checkSkill("Diplomacy", difficulty)` for persuasion
2. Update `DispositionManager` to call `SkillManager.checkSkill("Charisma", difficulty)` for intimidation
3. Grant Diplomacy XP on successful persuasion/intimidation
4. Implement disposition-based skill buffs:
   - Friendly NPCs (disposition > 50) grant 2x XP training buff when training via `Intent.TrainSkill`
5. Create NPC training system:
   - `Intent.TrainSkill` + target NPC → checks disposition → grants level 1 + buff if friendly
6. Remove `StubSkillSystem` from social system code

**Tests**: 12-15 integration tests
- Persuasion uses Diplomacy skill
- Intimidation uses Charisma skill
- XP granted on successful social checks
- Disposition buffs applied during training
- Training failure with hostile NPCs

**Files**:
- `memory/src/main/kotlin/com/jcraw/mud/memory/social/DispositionManager.kt` (update)
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/NPCInteractionGenerator.kt` (update)
- `app/src/main/kotlin/com/jcraw/app/App.kt` (wire SkillManager)
- `memory/src/test/kotlin/com/jcraw/mud/memory/social/SocialSystemV2IntegrationTest.kt` (extend)

---

### Phase 9: Memory/RAG Integration (3-4h)

**Goal**: Store skill usage history for narrative coherence.

**Tasks**:
1. Update `SkillManager` to log skill attempts to `MemoryManager`:
   - On `grantXp`: `memoryManager.remember("Practiced [skillName]: [outcome]", metadata = mapOf("skill" to skillName))`
   - On `checkSkill`: `memoryManager.remember("Attempted [skillName] check: [success/failure]", metadata = mapOf("skill" to skillName))`
2. Implement `recallSkillHistory(entityId: String, skillName: String): List<String>`
   - Uses `memoryManager.recallWithMetadata(filter = mapOf("skill" to skillName))`
3. Update narrative generators to recall skill history:
   - In `NPCInteractionGenerator`, mention past skill successes/failures
   - In `CombatResolver`, reference combat skill progression
4. Store perk effects in memory for callbacks:
   - `memoryManager.remember("Unlocked perk [perkName] for [skillName]")`

**Tests**: 8-10 integration tests
- Skill usage logged to memory
- Recall skill history retrieves correct events
- Narrative includes skill history
- Perk unlocks stored and recalled

**Files**:
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/skill/SkillManager.kt` (update)
- `memory/src/main/kotlin/com/jcraw/mud/memory/MemoryManager.kt` (extend if needed)
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/NPCInteractionGenerator.kt` (update)
- `memory/src/test/kotlin/com/jcraw/mud/memory/SkillMemoryIntegrationTest.kt` (new)

---

### Phase 10: Game Loop Integration (4-5h)

**Goal**: Wire skill system into all 3 game implementations (console, GUI, testbot).

**Tasks**:
1. Wire `SkillManager` into `App.kt` (console):
   - Initialize `SkillDatabase` and `SkillManager` in main game loop
   - Add handlers for `Intent.UseSkill`, `Intent.TrainSkill`, `Intent.ChoosePerk`, `Intent.ViewSkills`
2. Wire `SkillManager` into `EngineGameClient.kt` (GUI):
   - Add skill handlers
   - Create UI events for skill updates (level-up, perk choice prompts)
3. Wire `SkillManager` into `InMemoryGameEngine.kt` (testbot):
   - Add skill handlers for automated testing
4. Create `SkillFormatter` in `action/src/main/kotlin/com/jcraw/mud/action/SkillFormatter.kt`:
   - `formatSkillSheet(component: SkillComponent): String` - Display skills, levels, XP
   - `formatLevelUp(skillName: String, newLevel: Int): String` - Level-up message
   - `formatPerkChoice(skillName: String, perks: List<Perk>): String` - Perk selection prompt
5. Add GUI elements:
   - Skill sheet screen (button in status bar)
   - Perk choice dialog (modal when milestone reached)
6. Implement `Intent.Sleep` command to reset temporary skill buffs

**Tests**: 10-12 integration tests
- Skill handlers in all 3 implementations
- Skill sheet formatting
- Perk choice UI flow
- Sleep command resets buffs

**Files**:
- `app/src/main/kotlin/com/jcraw/app/App.kt` (extend)
- `client/src/main/kotlin/com/jcraw/mud/client/EngineGameClient.kt` (extend)
- `testbot/src/main/kotlin/com/jcraw/mud/testbot/InMemoryGameEngine.kt` (extend)
- `action/src/main/kotlin/com/jcraw/mud/action/SkillFormatter.kt` (new)
- `client/src/main/kotlin/com/jcraw/mud/client/ui/SkillSheetScreen.kt` (new)
- `app/src/test/kotlin/com/jcraw/app/SkillGameLoopIntegrationTest.kt` (new)

---

### Phase 11: Combat & Magic Stub Interfaces (2-3h)

**Goal**: Create stub interfaces for future combat/magic modules; integrate skills with existing combat.

**Tasks**:
1. Create `CombatSystem` interface in `core/src/main/kotlin/com/jcraw/mud/core/CombatSystem.kt`:
   - `resolveAttack(attacker: Entity, defender: Entity, skillManager: SkillManager): CombatResult`
2. Create `MagicSystem` interface in `core/src/main/kotlin/com/jcraw/mud/core/MagicSystem.kt`:
   - `castSpell(caster: Entity, spellName: String, target: Entity?, skillManager: SkillManager): SpellResult`
3. Update `CombatResolver` to optionally use `SkillManager`:
   - If SkillManager present, factor in weapon skills for damage calculation
   - Example: `damage += skillManager.checkSkill(attackerId, "Sword Fighting", 10).margin`
4. Add skill XP rewards to existing combat:
   - On attack with sword → grant "Sword Fighting" XP
   - On hit taken → grant armor skill XP (Light Armor, Heavy Armor)
   - On damage taken → grant resistance XP (Fire Resistance, etc.)

**Files**:
- `core/src/main/kotlin/com/jcraw/mud/core/CombatSystem.kt` (new)
- `core/src/main/kotlin/com/jcraw/mud/core/MagicSystem.kt` (new)
- `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/CombatResolver.kt` (update)

---

### Phase 12: E2E Testing & Documentation (4-5h)

**Goal**: Comprehensive testing and documentation updates.

**Tasks**:
1. Create `SkillSystemIntegrationTest.kt` with full workflow tests:
   - Unlock skill via attempt
   - Use skill, gain XP, level up
   - Choose perk at milestone
   - Multi-skill combo action
   - Resource consumption and regeneration
   - Resistance damage reduction
   - Social integration (training with NPC)
2. Create test bot scenario `SkillProgressionPlaythroughTest.kt`:
   - Bot levels combat skills by fighting
   - Bot chooses perks intelligently
   - Bot uses skills to defeat boss
3. Create test bot scenario `MagePlaythroughTest.kt`:
   - Bot unlocks magic skills
   - Bot manages mana resources
   - Bot uses multi-skill combos (Fire Magic + Gesture Casting)
   - Bot defeats enemies with magic
4. Write comprehensive integration tests (20-25 tests total)
5. Documentation:
   - Create `docs/SKILL_SYSTEM.md` (architecture, usage, examples)
   - Update `docs/GETTING_STARTED.md` (skill commands, gameplay)
   - Update `docs/ARCHITECTURE.md` (skill module details)
   - Update `CLAUDE.md` (skill system status, file locations)
   - Update `docs/IMPLEMENTATION_LOG.md` (skill system completion)
6. Balance testing:
   - Adjust XP curves (too fast/slow?)
   - Test difficulty scaling (are checks too hard/easy?)
   - Test perk power levels (game-breaking perks?)

**Files**:
- `testbot/src/test/kotlin/com/jcraw/mud/testbot/integration/SkillSystemIntegrationTest.kt` (new)
- `testbot/src/test/kotlin/com/jcraw/mud/testbot/scenarios/SkillProgressionPlaythroughTest.kt` (new)
- `testbot/src/test/kotlin/com/jcraw/mud/testbot/scenarios/MagePlaythroughTest.kt` (new)
- `docs/SKILL_SYSTEM.md` (new)
- `docs/GETTING_STARTED.md` (update)
- `docs/ARCHITECTURE.md` (update)
- `CLAUDE.md` (update)
- `docs/IMPLEMENTATION_LOG.md` (update)

---

## Data Flow

```
Player Input: "cast fireball"
    ↓
Perception: IntentRecognizer → Intent.UseSkill(skill = "Fire Magic", action = "cast fireball")
    ↓
Reasoning: SkillComboResolver → identifies skills ["Fire Magic": 0.6, "Gesture Casting": 0.3, "Magical Projectile Accuracy": 0.1]
    ↓
Reasoning: SkillManager → checkSkill(weighted combo) → SkillCheckResult(success, margin)
    ↓
Reasoning: ResourceManager → consumeResource("mana", cost)
    ↓
Reasoning: SkillManager → grantXp("Fire Magic", baseXp, success) → LevelUp?
    ↓
Memory: MemoryManager → remember("Cast fireball: success/failure")
    ↓
Action: SkillFormatter → format narrative with skill check outcome
    ↓
Output: "You weave arcane gestures and hurl a blazing fireball! (Fire Magic leveled to 11)"
```

---

## Key Design Decisions

### 1. XP Formula: Why Exponential After Level 100?

**Formula**: `if (level <= 100) 100 * level^2 else 100 * level^2 * (level / 100)^1.5`

- **Early game**: Linear-ish growth (L1→L10 takes ~5,000 XP)
- **Mid game**: Quadratic growth (L50→L60 takes ~185,000 XP)
- **Late game**: Super-exponential (L100→L200 takes millions of XP)
- **Rationale**: Allows godlike progression for dedicated players without trivializing mid-game

### 2. Multi-Skill Combos: Weighted Average

**Example**: Casting fireball uses:
- Fire Magic (60% weight)
- Gesture Casting (30% weight)
- Magical Projectile Accuracy (10% weight)

**Calculation**: `effectiveLevel = 0.6 * fireMagicLevel + 0.3 * gestureCastingLevel + 0.1 * accuracyLevel`

**Rationale**: More realistic than "highest skill wins"; encourages balanced builds.

### 3. Unlock Methods: Four Paths

1. **Attempt** (d100 < 5%): Low-chance unlock for all skills
2. **Observation** (1.5x XP buff): Witness skill use → temp buff
3. **Training** (level 1 + 2x XP buff): NPC mentor grants immediate unlock + buff
4. **Prerequisite**: Some skills require others (e.g., Advanced Fire Magic needs Fire Magic 50)

**Rationale**: Multiple progression paths prevent grind-lock; encourages exploration and social interaction.

### 4. Resources as Skills

**Mana Reserve** skill: `maxMana = level * 10` (L20 = 200 mana)
**Mana Flow** skill: `regenRate = level * 2 per turn` (L10 = 20 mana/turn)

**Rationale**: Resource capacity and regen become trainable; avoids hardcoded stat scaling.

### 5. Resistances Grant XP on Damage

Fire Resistance gains XP when taking fire damage → levels up → reduces future fire damage.

**Rationale**: Use-based progression; getting hit trains resistance (realistic).

---

## Integration with Existing Systems

### Social System
- **Disposition → Skill Buffs**: Friendly NPCs (disposition > 50) grant 2x XP training buff
- **Skill Checks for Social**: Persuasion uses "Diplomacy" skill, Intimidation uses "Charisma" skill
- **Replace StubSkillSystem**: Wire real SkillManager into DispositionManager

### Quest System
- **Quest Rewards**: Add skill XP to quest rewards (e.g., "Complete Crypt Quest → +500 Sword Fighting XP")
- **Skill-Based Objectives**: Future enhancement: "UseSkill" quest objectives (already in quest system spec)

### Combat System
- **Weapon Skills**: Sword Fighting, Axe Mastery, Bow Accuracy modify damage
- **Armor Skills**: Light Armor, Heavy Armor reduce incoming damage
- **XP on Combat**: Every attack/defense grants XP to relevant skills

### Memory/RAG
- **Action History**: Store skill usage for narrative coherence
- **Perk Callbacks**: Reference unlocked perks in narratives ("You invoke your Quick Strike ability")

---

## Testing Strategy

### Unit Tests (80-100 tests)
- **SkillState**: XP calculations, leveling, thresholds
- **SkillManager**: XP granting, unlock methods, skill checks
- **PerkSelector**: Perk choices, selection, milestones
- **SkillComboResolver**: Multi-skill resolution, weighting
- **ResourceManager**: Consumption, regeneration, capacity
- **Database**: CRUD operations, transactions, indexes

### Integration Tests (40-50 tests)
- **Full Workflows**: Unlock → use → level → choose perk
- **Social Integration**: Training with NPCs, disposition buffs
- **Combat Integration**: Skill XP from attacks, armor XP from hits
- **Memory Integration**: Skill history recall in narratives
- **Multi-Skill Combos**: Complex actions using 3+ skills

### E2E Tests (5-10 tests)
- **SkillProgressionPlaythrough**: Bot levels skills to beat dungeon
- **MagePlaythrough**: Bot uses magic skills with mana management
- **AllPlaythroughs**: Validate skill system doesn't break existing playthroughs

---

## Future Enhancements (Out of Scope)

1. **LLM Dynamic Skill/Perk Generation**: Generate new skills on-the-fly for novel actions
2. **Shapeshifting**: Form-specific skills (e.g., "Wolf Form" unlocks "Claw Attack")
3. **Multiplayer Teaching**: Players can train each other's skills
4. **Full Combat Module**: Replace CombatResolver stub with skill-based combat system
5. **Full Magic Module**: Spell system with skill-based success/power/cost
6. **Crafting Skills**: Blacksmithing, Alchemy, Enchanting with product quality tied to skill level

---

## Success Criteria

- ✅ **Use-based leveling**: Skills level via practice with infinite growth
- ✅ **Multi-skill combos**: Actions use 2+ skills with weighted resolution
- ✅ **Perks with choices**: Every 10 levels, choose 1 of 2 perks
- ✅ **Social integration**: Persuasion/intimidation use Diplomacy/Charisma skills; NPCs grant training buffs
- ✅ **Resource management**: Mana/Chi pools with consumption and regeneration
- ✅ **Predefined skills**: 30+ skills loaded into database
- ✅ **All tests passing**: 150+ tests across unit/integration/E2E
- ✅ **Playthrough validation**: Test bot completes dungeon using skills

---

## Risk Mitigation

### Risk: XP Curve Too Steep/Shallow
**Mitigation**: Phase 12 includes balance testing; adjust formula constants if needed.

### Risk: LLM Skill Identification Inaccurate
**Mitigation**: Use predefined skill mappings for common actions; LLM fallback for novel actions.

### Risk: Database Performance with 100+ Skills
**Mitigation**: Denormalized `skills` table with indexes; cache SkillComponent in memory.

### Risk: Complexity Creep
**Mitigation**: Keep skill definitions data-driven (JSON-like); avoid hardcoding logic per skill.

---

## Estimated Effort

| Phase | Description | Estimated Hours |
|-------|-------------|-----------------|
| 1 | Foundation - Core Data Models | 3-4h |
| 2 | Database Layer | 4-5h |
| 3 | Skill Manager (Core Logic) | 4-5h |
| 4 | Intent Recognition | 3-4h |
| 5 | Multi-Skill Combos & Resources | 4-5h |
| 6 | Perk System | 3-4h |
| 7 | Predefined Skills & Seed Data | 2-3h |
| 8 | Social System Integration | 3-4h |
| 9 | Memory/RAG Integration | 3-4h |
| 10 | Game Loop Integration | 4-5h |
| 11 | Combat & Magic Stub Interfaces | 2-3h |
| 12 | E2E Testing & Documentation | 4-5h |
| **Total** | | **25-35h** |

---

## Next Steps

1. Review this implementation plan with stakeholders
2. Set up task tracking (use TodoWrite tool)
3. Begin Phase 1: Foundation - Core Data Models
4. After each phase: Run tests, commit, update TODO list
5. Final phase: Update all documentation and mark feature complete

---

**Document Status**: DRAFT - Ready for Implementation
**Last Updated**: 2025-10-20
**Author**: Claude Code (Architecture Agent)
