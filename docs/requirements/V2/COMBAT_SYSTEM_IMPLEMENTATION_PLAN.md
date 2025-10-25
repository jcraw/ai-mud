# Combat System V2 Implementation Plan

## Overview

This plan implements an **integrated asynchronous turn-based combat system** that emerges naturally from player actions without dedicated combat modes. Combat leverages the social disposition system for initiation, uses skill-driven mechanics with time-based turn ordering, employs LLM-driven monster AI modulated by entity skills, and optimizes performance through cached narrations.

**Key Principles:**
- **Emergent integration** - Combat flows from freeform actions without mode switches
- **Asynchronous pacing** - Game clock with action costs, no real-time pressure
- **Skill-driven depth** - Multi-skill checks determine outcomes
- **AI behaviors** - LLM decisions scaled by intelligence/wisdom
- **Fair permadeath** - Corpses with item recovery, telegraphed threats
- **Code-calculated mechanics** - Reliable damage/resolution with LLM flavor
- **Modular ECS architecture** - Component-based design

## Current State Analysis

**V1 Combat System (Existing):**
- Modal combat mode via `PlayerState.activeCombat`
- Simple `CombatState` (playerHealth, npcHealth, isPlayerTurn, turnCount)
- Simultaneous turns (player attacks → NPC counter-attacks immediately)
- Basic damage: `random + weapon + STR - armor`
- No persistence beyond PlayerState
- No AI (random damage, 50% flee chance)
- No status effects or timing mechanics
- Component system exists but underutilized

**What Needs Changing:**
1. **Remove combat mode** - Transition to disposition-triggered combat
2. **Add time/speed system** - Action costs, priority queue, game clock
3. **Extend components** - CombatComponent with HP, timers, status effects
4. **Add persistence** - Database tables for combat state, events, corpses
5. **Implement AI** - LLM-driven monster decisions
6. **Add status effects** - DOT, buffs, debuffs with duration
7. **Optimize narration** - Vector DB caching for flavor text
8. **Multi-skill resolution** - LLM determines relevant skills for actions

## Dependencies

**Already Implemented:**
- ✅ Skill System V2 (use-based progression, perks, resources)
- ✅ Social System (disposition, knowledge, interactions)
- ✅ Component/ECS architecture
- ✅ Repository pattern
- ✅ Memory/RAG system
- ✅ LLM infrastructure

**Required:**
- Extend component system with CombatComponent
- Create combat-specific repositories
- Integrate disposition triggers
- Implement game clock/timing
- Add vector DB caching for combat narrations

## Architecture Decisions

**Chain-of-Thought Reasoning:**

1. **Why emergent vs. modal combat?**
   - Modal combat breaks immersion and limits creative actions
   - Disposition-based initiation feels natural (hostile entities attack)
   - Allows mid-combat social actions (persuade to de-escalate)
   - More flexible for future multiplayer scenarios
   - Decision: Use disposition thresholds (<-75 = hostile) to trigger combat behaviors

2. **Why time-based turns vs. strict alternating?**
   - Faster entities (high Speed skill) should act more frequently
   - Asynchronous play better for intermittent sessions
   - More strategic depth (timing abilities, controlling tempo)
   - Realistic (a nimble rogue acts faster than a lumbering ogre)
   - Decision: Action cost formula: `baseCost / (1 + speedLevel / 10)`

3. **Why LLM AI instead of scripted?**
   - Emergent behaviors more interesting than predictable patterns
   - Personality-driven decisions align with social system
   - Intelligence/wisdom skills create natural difficulty scaling
   - Low-int monsters make poor tactical choices (realistic)
   - Decision: Modulate LLM prompt complexity and temperature by skills

4. **Why cache narrations?**
   - LLM calls are slow and costly
   - Many combat scenarios are similar ("sword hit", "dodge")
   - Vector DB enables semantic matching to pre-generated variants
   - Live LLM only for unique/complex situations
   - Decision: Preload 50+ variants per common action tier, semantic search

5. **Why component-based vs. extending entities?**
   - Not all entities need combat capability
   - Easy to attach/detach (e.g., peaceful NPCs become hostile)
   - Consistent with skill/social component pattern
   - Clean separation of concerns
   - Decision: CombatComponent implements Component interface

## Implementation Plan

### PHASE 1: Foundation - Component & Schema (3-4 hours)

**Objective:** Establish core data structures, database schema, and repository interfaces.

**Reasoning:** Foundation must be solid before building resolution logic. Components define the contract, DB ensures persistence, repositories abstract storage. This phase has no dependencies on later phases.

**Tasks:**

1.1. **CombatComponent Data Class** (`:core/Component.kt`)
- Extend `Component` sealed interface
- Fields: `currentHp`, `maxHp`, `actionTimerEnd`, `statusEffects`, `position`
- Methods:
  - `calculateMaxHp(skills, items)`: Formula = `10 + (Vitality*5) + (Endurance*3) + (Constitution*2) + itemBonuses`
  - `applyDamage(amount, type)`: Reduce HP, apply type-specific effects
  - `advanceTimer(cost)`: Set `actionTimerEnd = currentTime + cost`
  - `applyStatus(effect)`: Add to statusEffects list, handle stacking
  - `tickEffects(gameTime)`: Process duration decrements, DOT applications

**Why these methods?** They encapsulate combat state mutations while maintaining immutability (return new CombatComponent). `calculateMaxHp` centralizes formula for consistency. `tickEffects` enables passive effect processing between actions.

1.2. **StatusEffect Data Class** (`:core/StatusEffect.kt`)
- Fields: `type` (enum), `magnitude`, `duration`, `source`
- StatusEffectType enum: `POISON_DOT`, `STRENGTH_BOOST`, `SLOW`, `REGENERATION`, `SHIELD`
- Methods:
  - `tick()`: Decrement duration, return null if expired
  - V1 limit: 5 basic types (expand later)

**Why limit types?** Keeps V1 scope manageable. Can expand enum later without schema changes.

1.3. **DamageType Enum** (`:core/DamageType.kt`)
- Types: `PHYSICAL`, `FIRE`, `COLD`, `POISON`, `LIGHTNING`, `MAGIC`
- Used for resist calculations: `reduction = resistSkillLevel / 2%`

1.4. **CombatEvent Sealed Class** (`:core/CombatEvent.kt`)
- Events: `CombatStarted`, `AttackResolved`, `StatusApplied`, `HealthChanged`, `CombatEnded`, `EntityDied`
- Used for event log persistence and broadcasting to multiplayer

**Why sealed class?** Type-safe event handling, exhaustive when expressions, serializable for DB storage.

1.5. **Database Schema** (`:memory/combat/CombatDatabase.kt`)
- New file following `:memory/skill/SkillDatabase.kt` pattern
- Tables:
  - `combat_components` (entity_id PK, current_hp, max_hp, action_timer_end, status_effects JSON)
  - `status_effects` (denormalized: entity_id, type, magnitude, duration, source)
  - `combat_events_log` (id, entity_id, target_id, action_type, damage, outcome, timestamp)
  - `corpses` (id, location_room_id, contents JSON, decay_timer)
- Indices: `entity_id`, `action_timer_end` (for queue queries), `timestamp`

**Why denormalize status_effects?** Fast queries for "all entities with poison" or "effects expiring this tick". JSON in combat_components is backup.

1.6. **CombatRepository Interface** (`:core/repository/CombatRepository.kt`)
- Methods:
  - `findByEntityId(entityId): CombatComponent?`
  - `save(entityId, component): Result<Unit>`
  - `updateHp(entityId, newHp): Result<Unit>`
  - `applyEffect(entityId, effect): Result<Unit>`
  - `findActiveThreats(roomId): Result<List<String>>` (entities with hostile disposition + combat component)
  - `logEvent(event): Result<Unit>`
  - `getEventHistory(entityId, limit): Result<List<CombatEvent>>`

**Why separate from entity repository?** Combat is optional capability, not core entity data. Separation allows independent evolution.

1.7. **SQLiteCombatRepository Implementation** (`:memory/combat/SQLiteCombatRepository.kt`)
- Follow `:memory/skill/SQLiteSkillComponentRepository.kt` pattern
- JSON serialization for CombatComponent using kotlinx.serialization
- Transaction support for atomic HP/effect updates

**Testing:**
- Unit tests for CombatComponent calculations (HP formula, damage application, timer advance)
- Unit tests for StatusEffect.tick()
- Integration test: Save/load CombatComponent via repository
- Integration test: Query active threats in room
- Verify schema initialization

**Documentation:**
- KDoc all classes/methods
- No user-facing docs yet (internal data structures)

---

### PHASE 2: Turn Queue & Timing System (4-5 hours)

**Objective:** Implement game clock, action cost calculations, and asynchronous turn ordering.

**Reasoning:** Timing is core to V2's asynchronous design. Must work before implementing resolution logic that depends on turn order. This phase depends on Phase 1 (CombatComponent.actionTimerEnd) but not later phases.

**Tasks:**

2.1. **GameClock in WorldState** (`:core/WorldState.kt`)
- Add field: `gameTime: Long = 0L` (tick counter)
- Method: `advanceTime(ticks: Long): WorldState` - Returns new WorldState with incremented time
- Persisted in `world_state` table (extend schema)

**Why ticks not real time?** Async play means real-time meaningless. Ticks provide deterministic, saveable progression.

2.2. **Action Cost Constants** (`:reasoning/combat/ActionCosts.kt`)
- Object with base costs:
  - `MELEE_ATTACK = 6`
  - `RANGED_ATTACK = 5`
  - `SPELL_CAST = 8`
  - `ITEM_USE = 4`
  - `MOVE = 10`
  - `SOCIAL = 3`
- Formula: `actualCost = baseCost / (1 + speedSkillLevel / 10).coerceAtLeast(2)`

**Why configurable?** Allows post-launch tuning without code changes. Minimum 2 ticks prevents degenerate cases.

2.3. **TurnQueue Manager** (`:reasoning/combat/TurnQueueManager.kt`)
- In-memory priority queue (min-heap) keyed by `actionTimerEnd`
- Methods:
  - `enqueue(entityId, timerEnd)`
  - `dequeue(): String?` (returns next entity whose timer <= currentTime)
  - `peek(): Pair<String, Long>?` (check next without removing)
  - `remove(entityId)` (when entity dies/flees)
  - `rebuild(worldState)` (reconstruct from DB after load)

**Why in-memory?** Performance. DB persists timers, but active queue is ephemeral. Rebuild on load.

2.4. **Turn Resolution Loop** (`:app/MudGameEngine.kt` extension)
- Before processing player input:
  - Check `turnQueue.peek()`
  - While next timer <= currentTime AND entity is NPC: execute NPC action
  - Dequeue and advance entity's timer
- After player action:
  - Advance player's timer
  - Re-enqueue player
  - Advance gameTime by player's action cost

**Why process NPC actions before player input?** Ensures NPCs don't "wait" for player. Player sees results of pending NPC actions, then decides.

2.5. **Speed Skill Integration** (`:reasoning/combat/SpeedCalculator.kt`)
- Method: `calculateActionCost(baseAction: String, speedLevel: Int): Long`
- Fetches base cost from ActionCosts
- Applies formula
- Returns final tick cost

**Testing:**
- Unit test: Speed formula (verify level 0, 10, 50, 100 produce expected costs)
- Unit test: TurnQueue ordering (enqueue multiple, verify dequeue order)
- Integration test: Advance gameTime, verify NPCs process in correct order
- Edge case: Ties (player vs. NPC at same timer) - player goes first per config

**Documentation:**
- Explain timing system in ARCHITECTURE.md
- Add "game clock" section to GETTING_STARTED.md (not user-controllable, background mechanic)

---

### PHASE 3: Damage Resolution & Multi-Skill Checks (4-5 hours)

**Objective:** Implement attack resolution with multi-skill checks, damage calculation, and status effect application.

**Reasoning:** Core combat mechanic. Depends on Phase 1 (components) and Phase 2 (timing) to function. Independent of AI (can test with scripted NPC actions).

**Tasks:**

3.1. **SkillClassifier** (`:reasoning/combat/SkillClassifier.kt`)
- LLM-based: Given action description + entity skills, return relevant skills with weights
- Prompt: "Which skills apply to: 'Swing sword at goblin'? Return JSON: [{skill: 'Sword Fighting', weight: 0.6}, {skill: 'Accuracy', weight: 0.3}, {skill: 'Strength', weight: 0.1}]"
- Fallback: If LLM fails, use weapon type → skill mapping (sword → "Sword Fighting")
- Cache common mappings in vector DB (semantic search for similar actions)

**Why LLM not hardcoded?** Handles creative actions ("throw sand in eyes" → Dexterity + Trickery). Flexible for modding. Fallback ensures robustness.

3.2. **AttackResolver** (`:reasoning/combat/AttackResolver.kt`)
- Method: `resolveAttack(attacker, defender, action, worldState): AttackResult`
- Steps:
  1. Get skill weights from SkillClassifier
  2. Calculate total modifier: `sum(skillLevel * weight)`
  3. Roll: `d20 + modifier`
  4. Defender defense: `d20 + (Dodge*0.6 + Parry*0.4)` (if has weapon/shield)
  5. Hit if attack > defense
  6. Damage: DamageCalculator.calculate(...)
  7. Apply damage to defender's CombatComponent
  8. Check for status effects (weapon/ability procs)
  9. Return AttackResult(hit, damage, effects, narrative)

**Why weighted skills?** Single-skill checks are boring. Multi-skill creates depth (generalist vs. specialist tradeoffs).

3.3. **DamageCalculator** (`:reasoning/combat/DamageCalculator.kt`)
- Configurable formula via DB params table
- V1 formula: `(baseDamage + skillMod + itemBonus) * typeMult - resistReduction ± variance`
  - Base damage: Weapon stat (e.g., sword = 10, dagger = 6)
  - Skill mod: Relevant skill level (e.g., Sword Fighting)
  - Item bonus: Enchantments (future)
  - Type mult: 1.0 default, 1.5 for weakness, 0.5 for resistance
  - Resist reduction: `damageType resist skill level / 2%` (e.g., Fire Resistance 20 → -10% fire damage)
  - Variance: ±20% random (config: `damage_variance = 0.2`)
- Minimum 1 damage (can't reduce to 0)

**Why configurable?** Balance tuning post-launch. Variance adds excitement without RNG dominance.

3.4. **StatusEffectApplicator** (`:reasoning/combat/StatusEffectApplicator.kt`)
- Method: `applyEffectToEntity(entityId, effect, worldState): WorldState`
- Handles stacking rules:
  - DOT: Replace if same type (keep highest magnitude)
  - Buffs/debuffs: Stack additively up to cap (e.g., max 3 STRENGTH_BOOST)
- Persists via CombatRepository.applyEffect()
- Logs event

3.5. **Stealth Bonus Integration**
- If attacker has active Stealth status (hidden):
  - +50% damage on first hit
  - Remove Stealth after attack
- Stealth applied via "hide" action (uses Stealth skill check)

**Testing:**
- Unit test: SkillClassifier with mocked LLM (verify JSON parsing)
- Unit test: DamageCalculator formula (verify each component)
- Unit test: ResistanceCalculator (verify reduction %)
- Unit test: StatusEffectApplicator stacking rules
- Integration test: Full attack resolution (attacker hits, damage applied, HP reduced)
- Integration test: Stealth bonus application
- Mock: LLM returns deterministic skill weights

**Documentation:**
- Combat mechanics in ARCHITECTURE.md (multi-skill checks, damage formula)
- No user docs (internal mechanics)

---

### PHASE 4: Combat Initiation & Disposition Integration (3-4 hours)

**Objective:** Remove modal combat, integrate with social disposition to trigger emergent combat behaviors.

**Reasoning:** Transforms combat from mode to behavior. Depends on Phase 1-3 (components, timing, resolution) to handle actual combat. Must work with existing social system.

**Tasks:**

4.1. **Disposition Threshold Logic** (`:reasoning/combat/CombatInitiator.kt`)
- Method: `checkForHostileEntities(roomId, worldState): List<String>`
- Finds NPCs with disposition < -75 toward player
- Returns IDs of hostile entities
- Called every turn before player input

**Why -75?** Aligns with existing social system thresholds. Leaves room for "unfriendly but not attacking" (-50 to -75).

4.2. **Automatic Counter-Attack** (`:reasoning/combat/CombatBehavior.kt`)
- If player attacks neutral/friendly NPC: set disposition to -100 (hostile)
- Hostile NPCs automatically added to turn queue with next available action timer
- Method: `triggerCounterAttack(npcId, worldState): WorldState`

4.3. **Remove activeCombat from PlayerState** (`:core/PlayerState.kt`)
- Delete `activeCombat: CombatState?` field
- Remove `isInCombat()` method
- Migrate: Check for nearby hostile entities instead

**Why remove?** Combat is emergent property of dispositions, not discrete state. Simplifies state model.

4.4. **Intent Handler Updates** (`:app/handlers/CombatHandlers.kt`)
- Refactor `handleAttack()`:
  - No mode check, just resolve attack against target
  - Set disposition to hostile if not already
  - Add NPC to turn queue
  - Return attack result
- Remove "already in combat" branching logic

4.5. **Room Description Updates** (`:action/RoomDescriber.kt`)
- Add combat status to room descriptions:
  - "A hostile goblin glares at you!" (if disposition < -75)
  - "A wary guard watches you closely." (if -50 to -75)
- No explicit "You are in combat with..." message

**Testing:**
- Integration test: Attack neutral NPC → disposition drops → NPC counter-attacks next turn
- Integration test: Enter room with hostile NPC → NPC added to turn queue
- Integration test: Persuade hostile NPC → disposition improves → stops attacking
- Edge case: Multiple hostile NPCs → all added to queue
- Regression: Ensure existing social disposition mechanics unaffected

**Documentation:**
- Update GETTING_STARTED.md: Remove "combat mode" references, explain emergent combat
- Update SOCIAL_SYSTEM.md: Add combat initiation thresholds

---

### PHASE 5: Monster AI & Quality Modulation (4-5 hours)

**Objective:** Implement LLM-driven monster AI that scales decision quality by intelligence/wisdom skills.

**Reasoning:** Differentiates monsters beyond stats. Depends on Phase 1-4 (need working combat to make decisions about). Independent of narration (can use simple text initially).

**Tasks:**

5.1. **MonsterAIHandler** (`:reasoning/combat/MonsterAIHandler.kt`)
- Method: `decideAction(npcId, worldState): Intent`
- Inputs: NPC skills, personality, player state, room context, combat history
- LLM prompt structure:
  - Low intelligence (0-20): "You are a dumb goblin. Make an impulsive decision. Options: attack, flee, use item."
  - Medium intelligence (21-50): "You are a competent guard. Choose tactically. Consider HP, environment."
  - High intelligence (51+): "You are a cunning wizard. Strategize optimally. Analyze weaknesses, predict player actions."
- Temperature modulation by wisdom:
  - Low wisdom (0-20): temp=1.2 (erratic)
  - Medium wisdom (21-50): temp=0.7 (balanced)
  - High wisdom (51+): temp=0.3 (consistent)

**Why int/wis separation?** Intelligence = strategic depth of prompt, wisdom = decision consistency. Low-int/high-wis = simple but reliable. High-int/low-wis = clever but unpredictable.

5.2. **AI Decision Options**
- Actions LLM can choose:
  - `Attack(target, method)` - Method can be creative (e.g., "aim for legs")
  - `Flee(direction)` - Toward allies or away from danger
  - `UseItem(itemId)` - Healing, buffs
  - `UseAbility(abilityName)` - Special skills (e.g., "breathe fire")
  - `Defend()` - Raise shield, take defensive stance
  - `Wait()` - Do nothing (tactical delay)
- Fallback rules if LLM fails:
  - If HP < 30%: Flee or UseItem(healing potion)
  - If HP > 70%: Attack strongest player
  - Else: Attack random target

5.3. **Personality Integration** (`:reasoning/combat/PersonalityAI.kt`)
- Cowardly NPCs: Flee at 50% HP (not 30%)
- Aggressive NPCs: Always attack, never flee
- Defensive NPCs: Prefer Defend action, counter-attack
- Retrieved from SocialComponent.personality field

**Why personality?** Adds flavor, avoids homogeneous behavior. Synergizes with social system characterization.

5.4. **Vector DB Caching for AI Decisions** (`:memory/combat/AICacheRepository.kt`)
- Cache common scenarios: "low HP goblin with no items" → likely flee
- Semantic search: Find similar past decisions
- Hit rate target: 60% (reduces LLM calls)
- TTL: 100 game sessions (refresh as balance changes)

**Why cache AI?** LLM calls slow down turns. Common situations repeat (goblin at 10% HP). Balance changes invalidate old decisions (TTL).

5.5. **NPC Action Execution** (`:app/MudGameEngine.kt`)
- When NPC's turn comes up:
  1. MonsterAIHandler.decideAction()
  2. Execute intent (attack, flee, use item)
  3. Advance NPC's action timer
  4. Broadcast event to players in room
  5. Dequeue NPC, re-enqueue with new timer

**Testing:**
- Unit test: AI prompt generation (verify int/wis affect prompt)
- Unit test: Temperature calculation
- Integration test: Low-int monster makes poor choice (attacks stronger enemy despite low HP)
- Integration test: High-int monster uses optimal tactic (flees when outmatched)
- Integration test: Cowardly NPC flees earlier than brave NPC
- Mock: LLM returns known intents
- Performance test: Vector cache hit rate (target >50%)

**Documentation:**
- AI system in ARCHITECTURE.md (intelligence/wisdom modulation)
- Monster AI guidelines for future content creators (how to set skills for desired behavior)

---

### PHASE 6: Optimized Flavor Narration (3-4 hours)

**Objective:** Generate contextual combat narratives via LLM with vector DB caching to minimize latency and cost.

**Reasoning:** Flavor makes combat engaging. Depends on Phase 1-5 (need combat events to narrate). Optimization critical for fast turns.

**Tasks:**

6.1. **NarrationVariantGenerator** (`:memory/combat/NarrationVariantGenerator.kt`)
- Offline script (not runtime): Pre-generates 50+ variants per scenario
- Scenarios:
  - Melee hit (sword, axe, club) x 10 variants each
  - Melee miss x 10 variants
  - Ranged hit/miss
  - Spell cast (fire, ice, lightning)
  - Critical hit (double damage)
  - Status effect applied (poison, slow, etc.)
  - Death blow
- Stores in vector DB with embeddings
- Tags: weapon type, damage tier (low/med/high), outcome (hit/miss/crit)

**Why pre-generate?** Allows human review for quality. Faster than live generation. Can theme by dungeon atmosphere.

6.2. **NarrationMatcher** (`:memory/combat/NarrationMatcher.kt`)
- Method: `findNarration(context: CombatContext): String?`
- Context: weapon, skills used, damage amount, outcome, target type
- Semantic search vector DB for nearest match (cosine similarity > 0.85)
- Returns cached variant or null (triggers live LLM)

6.3. **CombatNarrator Refactor** (`:reasoning/CombatNarrator.kt`)
- Updated method: `narrateCombatAction(context: CombatContext): String`
- Flow:
  1. Try NarrationMatcher.findNarration()
  2. If hit and suitable: return cached variant
  3. Else: Call LLM with full context (weapon, skills, env, history)
  4. Store LLM response in vector DB for future (with tags)
  5. Return narrative
- Batch multiple actions: Single LLM call for "You hit for 10, goblin misses, guard hits you for 5"

**Why batch?** Reduces LLM calls from 3 to 1. Coherent narrative across multiple simultaneous events.

6.4. **Concise Narration Mode** (config toggle)
- Mode 1: Verbose (default) - Full LLM flavor
- Mode 2: Concise - Use cached only, fallback to template strings ("You hit for 10 damage.")
- Config: `combat_narration_mode = "verbose"` in settings table

**Why configurable?** Some players prefer fast gameplay over narrative. Accessibility (low-bandwidth).

6.5. **Equipment-Aware Descriptions**
- Include weapon name in narration: "You slash with your Flaming Sword!"
- Include armor in defense: "Your Plate Armor deflects the blow!"
- Retrieved from InventoryComponent

**Testing:**
- Unit test: NarrationMatcher (verify semantic search finds appropriate variant)
- Integration test: Common scenario (sword hit) → returns cached variant
- Integration test: Unique scenario ("throw goblin at wall") → live LLM call
- Performance test: 100 combat turns, measure cache hit rate (target >60%)
- Performance test: Batch narration vs. individual (verify faster)
- Manual QA: Read generated narratives for quality/variety

**Documentation:**
- Explain narration system in ARCHITECTURE.md
- Add "Combat Narration" section to CLAUDE.md (how to customize/extend variants)

---

### PHASE 7: Death, Corpses & Item Recovery (2-3 hours)

**Objective:** Implement permadeath with corpse system, item dropping, and retrieval mechanics.

**Reasoning:** Consequence for failure. Depends on Phase 1-6 (need combat to cause death). Relatively independent (add-on feature).

**Tasks:**

7.1. **Corpse Entity Type** (`:core/Entity.kt`)
- New sealed class member: `Entity.Corpse(id, name, location, contents, decayTimer)`
- Contents: List<Item> (inventory at death)
- DecayTimer: Ticks until despawn (default: 100)

**Why entity?** Corpses can be interacted with (loot, examine). Fits existing entity model.

7.2. **Death Handler** (`:reasoning/combat/DeathHandler.kt`)
- Method: `handleDeath(entityId, worldState): WorldState`
- Steps:
  1. Remove entity's CombatComponent
  2. Create Corpse entity with entity's inventory
  3. Place corpse in current room
  4. Remove entity from room
  5. Log death event
  6. If player: Trigger permadeath sequence
  7. If NPC: Drop loot, update quest progress (existing logic)

7.3. **Player Permadeath Sequence** (`:app/MudGameEngine.kt`)
- On player death:
  1. Display death message: "You have fallen! Your belongings scatter..."
  2. Create player corpse at death location
  3. Prompt: "Continue as new character (Y/N)?"
  4. If Y: Respawn at starting location, reset skills/stats, new character name
  5. If N: Quit game
- Old corpse persists (can retrieve items)

**Why new character?** Permadeath tension. But allows item recovery (not total loss). Balances risk vs. frustration.

7.4. **Corpse Interaction** (`:app/handlers/ItemHandlers.kt`)
- Extend `handleTake()` to support taking from corpses
- Syntax: "loot corpse", "take sword from corpse"
- Lists corpse contents if examined
- Items transfer to player inventory

7.5. **Corpse Decay** (`:reasoning/combat/CorpseDecayManager.kt`)
- Method: `tickDecay(worldState): WorldState`
- Called every turn (tied to gameTime)
- Decrements decayTimer for all corpses
- Removes corpses at 0 timer
- Deleted items drop to room (chance-based: 30% per item)

**Why decay?** Prevents corpse spam. Encourages timely retrieval. Realism.

7.6. **Corpse Persistence** (`:memory/combat/CombatDatabase.kt`)
- `corpses` table already in schema (Phase 1)
- Save/load corpses with world state
- Decay timer persists across saves

**Testing:**
- Integration test: Player dies → corpse created with inventory
- Integration test: Loot corpse → items transfer
- Integration test: Corpse decay → removed after 100 ticks
- Integration test: Respawn → player at starting location, corpse at death location
- Edge case: Multiple corpses in same room (player died twice)
- Regression: NPC death still triggers quest updates

**Documentation:**
- Update GETTING_STARTED.md: Explain permadeath, corpse retrieval
- Add "Death & Respawn" section

---

### PHASE 8: Escape, Stealth & Advanced Mechanics (3-4 hours)

**Objective:** Implement flee mechanics, stealth system, AoE attacks, magic integration, and environmental interactions.

**Reasoning:** Adds tactical depth. Depends on Phase 1-7 (full combat working). These are enhancements, not core features.

**Tasks:**

8.1. **Flee Mechanics Redesign** (`:reasoning/combat/FleeResolver.kt`)
- Replace 50% random flee with skill check
- Formula: `d20 + (Agility*0.6 + Speed*0.4)` vs. enemy `d20 + Pursuit`
- On success:
  - Player moves to random adjacent room
  - Hostile NPCs don't follow (for now)
- On failure:
  - Enemy gets free attack (no defense roll)
  - Player remains in room, can act next turn

**Why skill-based?** Rewards investment in mobility skills. More predictable than random.

8.2. **Stealth System** (`:reasoning/combat/StealthHandler.kt`)
- Action: "hide" → Stealth skill check vs. enemy Perception
- On success: Apply HIDDEN status effect (duration = Stealth level / 10 ticks)
- While hidden:
  - Enemies can't target you (attack other targets or search)
  - First attack gets +50% damage
  - Attacking removes HIDDEN
- Re-hide requires line-of-sight break (leave room or use smoke bomb)

**Why duration-based?** Prevents permanent invulnerability. High Stealth = longer duration = more tactical options.

8.3. **AoE Attacks** (`:reasoning/combat/AoEResolver.kt`)
- Certain abilities/items hit all entities in room
- Example: "Whirlwind Strike" perk (unlocked via Sword Fighting)
- Method: `resolveAoE(sourceId, ability, worldState): List<AttackResult>`
- Loops over all valid targets in room
- Individual damage rolls per target (not same damage)

**Why individual rolls?** Avoids "all or nothing" outcomes. More realistic (different angles, defenses).

8.4. **Magic Integration** (`:reasoning/combat/MagicCombatHandler.kt`)
- Magic skills (Fire Magic, Ice Magic, etc.) already exist
- Combat integration:
  - Spell casting costs action time (8 ticks base)
  - Requires resource check (Mana Reserve)
  - Damage formula: `spellBaseDamage + magicSkillLevel + INT modifier`
  - Status effects: Fire → burning DOT, Ice → slow, Lightning → stun chance
- Spell failure on low skill: Wasted action, mana consumed

**Why mana cost?** Prevents spam. Encourages resource management. Differentiates from melee.

8.5. **Environmental Interactions** (`:reasoning/combat/EnvironmentalActions.kt`)
- Context-aware actions: "push boulder", "kick over brazier"
- Uses Strength skill vs. difficulty rating (from room features)
- Effects:
  - Boulder rolls: AoE damage in direction
  - Brazier: Creates fire hazard, applies burning to entities
  - Collapse pillar: Blocks exit, damages nearby
- Room features marked in RoomDefinition (existing field)

**Why environmental?** Adds creativity, rewards exploration. Ties combat to room design.

**Testing:**
- Integration test: Flee success → player moves room
- Integration test: Flee failure → enemy free attack
- Integration test: Hide → enemy can't target → attack removes hidden
- Integration test: AoE hits all targets in room
- Integration test: Magic spell consumes mana, applies status
- Integration test: Environmental action (push boulder) → damage multiple enemies
- Edge case: Hidden player + AoE attack → still hit (AoE ignores stealth)

**Documentation:**
- Update ARCHITECTURE.md: Stealth, AoE, magic, environmental sections
- Update GETTING_STARTED.md: Commands (hide, cast spell, push boulder)

---

### PHASE 9: Handler & Client Integration (3-4 hours)

**Objective:** Update intent handlers, GUI client, and multiplayer event broadcasting to support new combat system.

**Reasoning:** Connects backend to user interfaces. Depends on Phase 1-8 (all combat features complete). Required for playability.

**Tasks:**

9.1. **Intent Handler Refactoring** (`:app/handlers/CombatHandlers.kt`)
- Complete refactor to use new system:
  - `handleAttack()`: Use AttackResolver, check multi-targets
  - `handleFlee()`: Use FleeResolver
  - Remove all `activeCombat` references
  - Integrate TurnQueueManager
- Add new handlers:
  - `handleDefend()`: Apply defensive stance (next attack gets -50% damage)
  - `handleHide()`: Stealth check via StealthHandler

9.2. **Movement Handler Updates** (`:app/handlers/MovementHandlers.kt`)
- Before move: Check if hostile entities present
- If yes: Flee attempt (not free movement)
- If flee fails: Block movement, enemy attacks
- Prevents escaping combat by walking away

**Why restrict movement?** Maintains combat stakes. Can still flee with dedicated action.

9.3. **Skill Handler Integration** (`:app/handlers/SkillQuestHandlers.kt`)
- `handleUseSkill()`: If skill is combat-relevant, resolve via CombatSystem
- Example: "use Fire Magic to cast fireball" → MagicCombatHandler
- XP awarded per existing skill system

9.4. **GUI Client Updates** (`:client/handlers/ClientCombatHandlers.kt`)
- New file (doesn't exist yet)
- Methods:
  - `handleAttack()`: Send attack intent to EngineGameClient
  - `handleFlee()`: Send flee intent
  - `updateCombatUI()`: Refresh HP bars, status effects, turn order
- UI components (`:client/ui/CombatPanel.kt`):
  - HP bar (player and visible enemies)
  - Status effect icons
  - Turn order indicator ("Next: Goblin in 3 ticks")
  - Action buttons (Attack, Defend, Flee, Use Item)

**Why dedicated panel?** Combat has rich state (HP, effects, timers). Needs clear visualization.

9.5. **Multiplayer Event Broadcasting** (`:app/GameServer.kt`)
- Broadcast combat events to all players in room:
  - "Goblin attacks Player1 for 10 damage!"
  - "Player2 casts fireball!"
- Use existing event system (GameEvent sealed class)
- Add CombatEvent types to GameEvent

**Why room-scoped?** Players in other rooms shouldn't see. Privacy and performance.

9.6. **Turn Indicator for Async Play**
- Display whose turn is next: "Next action: [Entity] in [X] ticks"
- Shows timer countdown (optional: real-time estimate if player takes 5sec/action)
- Helps players understand pacing

**Testing:**
- Integration test: Console attack → AttackResolver called → HP updated
- Integration test: GUI attack button → intent sent → UI refreshes
- Integration test: Multiplayer → Player1 attacks → Player2 sees event
- Integration test: Move while in combat → flee check triggered
- Regression: All existing handlers still work (items, social, quests)

**Documentation:**
- Update CLIENT_UI.md: New combat panel, controls
- Update MULTI_USER.md: Combat event broadcasting

---

### PHASE 10: Testing & Documentation (4-5 hours)

**Objective:** Comprehensive testing suite, bot playthroughs, balance tuning, and complete documentation.

**Reasoning:** Ensures quality and maintainability. Depends on Phase 1-9 (all features complete). Last phase before release.

**Tasks:**

10.1. **Unit Test Suite** (all `:reasoning` combat classes)
- CombatComponent calculations (HP, damage, timers)
- StatusEffect.tick() edge cases
- DamageCalculator formula verification
- AttackResolver multi-skill checks
- MonsterAIHandler prompt generation
- FleeResolver skill check formula
- StealthHandler detection logic
- Mocks: LLM responses, repositories, RNG (seeded Random)

**Coverage target:** 80%+ for combat logic (not pure data classes)

10.2. **Integration Test Suite**
- Full combat flow: Initiate → attack → damage → death
- Multi-NPC combat: 3 entities, correct turn order
- Disposition triggers: Neutral → attack → hostile → counter
- Corpse creation and looting
- Flee success/failure paths
- Stealth → attack → reveal
- Magic spell with mana cost
- Environmental action
- Save/load with active combat
- Multiplayer: 2 players in room, events broadcast

10.3. **Bot Playthrough Tests** (`:testbot/scenarios/`)
- New scenarios:
  - `CombatWinScenario.kt`: Attack goblin, win, loot corpse
  - `CombatLossScenario.kt`: Attack strong enemy, die, respawn, retrieve corpse
  - `FleeScenario.kt`: Attack, flee, return later
  - `StealthAttackScenario.kt`: Hide, surprise attack, win
  - `MultiEnemyScenario.kt`: Fight 2 goblins simultaneously
  - `MagicCombatScenario.kt`: Cast spells, manage mana
- Validation: Verify end state (HP, inventory, quest progress)

10.4. **Balance Tuning** (via bot data)
- Run 100 bot playthroughs, collect metrics:
  - Average combat duration (target: 5-10 turns)
  - Player win rate vs. equal-level NPC (target: 60-70%)
  - Flee success rate (target: 50-60%)
  - Damage variance (verify ±20% not too swingy)
- Adjust formulas if needed:
  - Damage too low/high: Tweak base values
  - Combat too long: Reduce HP or increase damage
  - AI too predictable: Adjust temperature

10.5. **Performance Testing**
- 100 combat turns, measure:
  - LLM call count (target: <30% of turns with caching)
  - Average turn latency (target: <1sec for cached, <3sec for LLM)
  - Database query time (target: <50ms)
- Optimize slow paths:
  - Index missing columns
  - Batch DB writes
  - Increase cache hit rate

10.6. **Documentation Updates**
- **CLAUDE.md**: Update "What's Implemented" - Combat System V2 ✅
- **ARCHITECTURE.md**: New "Combat System" section (components, turn queue, AI, narration)
- **GETTING_STARTED.md**:
  - Combat commands (attack, flee, hide, defend)
  - Explain emergent combat (no mode)
  - Death and respawn
  - Status effects
- **CLIENT_UI.md**: Combat panel documentation
- **MULTI_USER.md**: Combat in multiplayer
- **New doc**: `COMBAT_SYSTEM.md` (detailed reference)
  - Damage formulas
  - Status effect list
  - AI behavior guidelines
  - Balance parameters

**Testing:**
- All unit tests passing (target: 200+ new tests)
- All integration tests passing
- All bot scenarios passing
- Performance benchmarks met
- Manual playthrough: No crashes, feels fun

**Documentation:**
- All docs updated
- Code KDoc coverage >90%
- README reflects new features

---

## Implementation Sequence Summary

1. **Phase 1:** Foundation - Components, DB schema, repositories → Enables data persistence
2. **Phase 2:** Timing - Game clock, turn queue, action costs → Enables asynchronous play
3. **Phase 3:** Resolution - Multi-skill checks, damage, status effects → Core combat mechanic
4. **Phase 4:** Initiation - Disposition integration, remove modal combat → Emergent behavior
5. **Phase 5:** AI - LLM-driven monsters, quality modulation → Engaging enemies
6. **Phase 6:** Narration - Cached flavor text, optimized LLM → Fast, flavorful output
7. **Phase 7:** Death - Corpses, permadeath, item recovery → Consequence and recovery
8. **Phase 8:** Advanced - Flee, stealth, AoE, magic, environmental → Tactical depth
9. **Phase 9:** Integration - Handlers, GUI, multiplayer → User-facing features
10. **Phase 10:** Testing & Docs - Comprehensive validation and documentation → Quality assurance

**Total Estimated Time:** 30-40 hours

**Dependencies:**
- Each phase builds on previous phases
- Phases can't be parallelized (tight coupling)
- But within phases, tasks can be done concurrently (e.g., unit tests while implementing)

**Milestones:**
- After Phase 4: Playable emergent combat (basic)
- After Phase 6: Polished experience (with AI and narration)
- After Phase 8: Feature-complete
- After Phase 10: Release-ready

---

## Success Criteria

✅ **Combat emerges from actions** - No mode switch, disposition-triggered
✅ **Time-based turns** - Game clock, action costs, speed modulates frequency
✅ **Multi-skill checks** - LLM determines relevant skills, weighted modifiers
✅ **LLM AI scaled by skills** - Low-int monsters make poor decisions, high-int strategize
✅ **Code damage, optimized flavor** - Formulas in code, LLM for narration with caching
✅ **Permadeath with corpses** - Death creates corpse, new character can retrieve items
✅ **Integrates with skills/social** - Uses existing systems, no duplication
✅ **Tests passing** - 200+ new tests, bot playthroughs successful
✅ **Fair playthroughs** - Win rate 60-70%, no frustrating deaths without warning
✅ **Performance targets** - <3sec turn latency, >60% cache hit rate

---

## Risk Mitigation

**Risk 1: LLM latency makes combat slow**
- Mitigation: Vector DB caching (target 60% hit rate), batch narrations, concise mode toggle

**Risk 2: Multi-skill checks feel arbitrary**
- Mitigation: Transparent display ("Using: Sword Fighting 60%, Accuracy 30%"), allow player feedback

**Risk 3: AI makes nonsensical decisions**
- Mitigation: Fallback rules, extensive testing, cache good decisions

**Risk 4: Balance issues (too easy/hard)**
- Mitigation: Configurable formulas, bot-driven metrics, post-launch tuning

**Risk 5: Database schema conflicts**
- Mitigation: Independent tables (not extending entities table), migration script tested

**Risk 6: Multiplayer sync issues**
- Mitigation: V1 is local multiplayer (no network), turn queue is authoritative source

---

## Future Enhancements (Post-V2)

- **Multiplayer over network** - TCP/WebSocket, shared turn queue
- **Advanced positioning** - Front/back rows, tanking, AoE shape-based
- **Allies/Pets** - AI-controlled companions, commands
- **Reaction skills** - Parry, counter-attack, dodge as triggered actions
- **Advanced AI** - NPC-NPC combat, faction warfare, sieges
- **Combo system** - Skill chains for bonus effects
- **Equipment durability** - Weapons degrade, require repair
- **PostgreSQL migration** - For multi-user scaling

---

## Next Steps

1. Create feature branch: `git checkout -b feature/combat-system-v2`
2. Start Phase 1: Components & Schema
3. Commit after each phase for easy rollback
4. Open PR after Phase 10 complete
5. Deploy to test environment for playtesting
6. Gather feedback, iterate on balance
7. Merge to main when stable

**First Implementation Task:** Create `CombatComponent.kt` in `:core` module with HP calculation logic.
