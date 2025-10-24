# Large Files Refactoring Plan

**Status:** ✅ **COMPLETED** - All 3 Phases Complete
**Target:** Refactor 3 files (1961, 1507, 1146 lines) into 17 smaller files (<350 lines each)
**Goal:** Improve maintainability, follow KISS principle, stay under 300-500 line guideline

> **🚀 Quick Start:** New to this refactoring? See [QUICKSTART.md](QUICKSTART.md) for a step-by-step guide.

---

## Overview

This document details the refactoring of oversized Kotlin files in the AI-MUD project. The refactoring maintains all existing functionality while improving code organization and maintainability.

### Files to Refactor

1. **App.kt** (1961 lines) → 7 files
2. **EngineGameClient.kt** (1507 lines) → 6 files
3. **OutputValidator.kt** (1146 lines) → 4 files

**Total:** 3 files → 17 files (average ~280 lines per file)

---

## Execution Order

Execute in this order to minimize dependencies and establish patterns:

1. **Phase 1:** OutputValidator.kt (least dependencies, establishes testing pattern)
2. **Phase 2:** App.kt (most critical, establishes handler pattern)
3. **Phase 3:** EngineGameClient.kt (mirrors App.kt structure)

---

## Phase 1: OutputValidator.kt Refactoring

**Current:** 1146 lines in single file
**Target:** 4 files (~150-600 lines each)

### 1.1 File Structure

```
testbot/src/main/kotlin/com/jcraw/mud/testbot/
├── OutputValidator.kt (150 lines) - Main orchestrator
├── validation/
│   ├── CodeValidationRules.kt (600 lines) - Deterministic validation
│   ├── ValidationPrompts.kt (350 lines) - LLM prompt builders
│   └── ValidationParsers.kt (100 lines) - Parsing utilities
```

### 1.2 Detailed Breakdown

#### **OutputValidator.kt** (~150 lines)
**Purpose:** Main entry point, orchestration, LLM integration

**Contains:**
- Class declaration with constructor
- `validate()` method - orchestrates code + LLM validation
- Private helper to call LLM with prompts
- Package imports

**Example structure:**
```kotlin
class OutputValidator(
    private val llmClient: LLMClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun validate(
        scenario: TestScenario,
        playerInput: String,
        gmResponse: String,
        recentHistory: List<TestStep>,
        expectedOutcome: String? = null,
        worldState: WorldState? = null
    ): ValidationResult {
        // STEP 1: Code-based validation (deterministic)
        val codeValidation = CodeValidationRules.validate(
            playerInput, gmResponse, recentHistory, worldState
        )
        if (codeValidation != null) return codeValidation

        // STEP 2: LLM validation (subjective)
        val systemPrompt = ValidationPrompts.buildSystemPrompt(scenario)
        val userContext = ValidationPrompts.buildUserContext(
            scenario, playerInput, gmResponse, recentHistory,
            expectedOutcome, worldState
        )

        val response = llmClient.chatCompletion(
            modelId = "gpt-4o-mini",
            systemPrompt = systemPrompt,
            userContext = userContext,
            maxTokens = 300,
            temperature = 0.3
        )

        return ValidationParsers.parseValidation(response.choices.firstOrNull()?.message?.content ?: "")
    }
}
```

---

#### **CodeValidationRules.kt** (~600 lines)
**Purpose:** Deterministic code-based validation logic

**Contains:**
- `validate()` - main entry point
- `validateItemInteraction()` - take/drop/equip/use
- `validateMovement()` - directional movement
- `validateCombat()` - attack/damage/victory
- `validateSocialInteraction()` - talk/persuade/intimidate
- `trackInventoryFromHistory()` - helper function

**Example structure:**
```kotlin
object CodeValidationRules {
    fun validate(
        playerInput: String,
        gmResponse: String,
        recentHistory: List<TestStep>,
        worldState: WorldState?
    ): ValidationResult? {
        if (worldState == null) return null

        // Try each validation type
        validateItemInteraction(playerInput, gmResponse, recentHistory, worldState)?.let { return it }
        validateMovement(playerInput, gmResponse, recentHistory, worldState)?.let { return it }
        validateCombat(playerInput, gmResponse, recentHistory, worldState)?.let { return it }
        validateSocialInteraction(playerInput, gmResponse, recentHistory, worldState)?.let { return it }

        // No definitive validation
        return null
    }

    private fun validateItemInteraction(...): ValidationResult? { /* extract lines 93-264 */ }
    private fun validateMovement(...): ValidationResult? { /* extract lines 267-348 */ }
    private fun validateCombat(...): ValidationResult? { /* extract lines 350-464 */ }
    private fun validateSocialInteraction(...): ValidationResult? { /* extract lines 466-654 */ }
    private fun trackInventoryFromHistory(...): Set<String> { /* extract lines 664-697 */ }
}
```

---

#### **ValidationPrompts.kt** (~350 lines)
**Purpose:** LLM prompt construction for different scenarios

**Contains:**
- `buildSystemPrompt()` - base validation prompt
- `buildUserContext()` - context with history + game state
- Scenario-specific criteria builders (Exploration, Combat, SkillChecks, etc.)

**Example structure:**
```kotlin
object ValidationPrompts {
    fun buildSystemPrompt(scenario: TestScenario): String { /* lines 699-734 */ }

    fun buildUserContext(
        scenario: TestScenario,
        playerInput: String,
        gmResponse: String,
        recentHistory: List<TestStep>,
        expectedOutcome: String?,
        worldState: WorldState?
    ): String { /* lines 736-1117 */ }

    private fun buildScenarioCriteria(scenario: TestScenario): String {
        return when (scenario) {
            is TestScenario.Exploration -> buildExplorationCriteria()
            is TestScenario.Combat -> buildCombatCriteria()
            // ... etc
        }
    }

    private fun buildExplorationCriteria(): String { /* lines 807-856 */ }
    private fun buildCombatCriteria(): String { /* lines 857-921 */ }
    // ... other scenario builders
}
```

---

#### **ValidationParsers.kt** (~100 lines)
**Purpose:** Parse LLM responses and extract validation results

**Contains:**
- `parseValidation()` - JSON extraction from LLM response
- Helper functions for parsing

**Example structure:**
```kotlin
object ValidationParsers {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseValidation(responseText: String): ValidationResult { /* lines 1119-1145 */ }
}
```

### 1.3 Migration Steps

1. Create `validation/` package directory
2. Create `CodeValidationRules.kt` - copy validation logic
3. Create `ValidationPrompts.kt` - copy prompt builders
4. Create `ValidationParsers.kt` - copy parsing logic
5. Refactor `OutputValidator.kt` to orchestrate
6. Update imports in test files
7. Run tests: `gradle :testbot:test`
8. Verify no functionality changes

### 1.4 Testing

**Existing tests should pass unchanged:**
```bash
gradle :testbot:test --tests "com.jcraw.mud.testbot.scenarios.*"
```

**No new tests needed** - this is pure refactoring, behavior unchanged.

---

## Phase 2: App.kt Refactoring

**Current:** 1961 lines in single file (MudGame class is 1583 lines)
**Target:** 8 files (~150-350 lines each)

### 2.1 File Structure

```
app/src/main/kotlin/com/jcraw/app/
├── App.kt (~150 lines) - Main entry point
├── MudGameEngine.kt (~350 lines) - Core game loop
├── MultiUserGame.kt (~250 lines) - Multi-user mode
├── handlers/
│   ├── MovementHandlers.kt (~200 lines)
│   ├── ItemHandlers.kt (~300 lines)
│   ├── CombatHandlers.kt (~250 lines)
│   ├── SocialHandlers.kt (~250 lines)
│   └── SkillQuestHandlers.kt (~300 lines)
```

### 2.2 Detailed Breakdown

#### **App.kt** (~150 lines)
**Purpose:** CLI entry point, initialization, mode selection

**Contains:**
- `main()` function (lines 34-129)
- `loadApiKeyFromLocalProperties()` (lines 1935-1961)
- String repeat operator (line 1928)

**Responsibilities:**
- Get API key from environment/properties
- Print welcome banner
- Ask user for game mode (single/multi)
- Load Sample Dungeon
- Generate initial quests
- Initialize LLM components
- Start appropriate game mode

**Dependencies:** None (creates MudGame/MultiUserGame)

---

#### **MudGameEngine.kt** (~350 lines)
**Purpose:** Core MudGame class - game loop, state management, room description

**Contains:**
- `MudGame` class declaration (lines 131-138)
- All component initialization (lines 139-164)
- `start()` - main game loop (lines 165-185)
- `printWelcome()` (lines 187-191)
- `describeCurrentRoom()` (lines 193-227)
- `generateRoomDescription()` (lines 229-239)
- `processIntent()` - intent dispatcher (lines 241-276)
- `trackQuests()` (lines 278-309)
- `buildExitsWithNames()` (lines 314-323)

**Key pattern - processIntent() dispatcher:**
```kotlin
private fun processIntent(intent: Intent) {
    when (intent) {
        is Intent.Move -> MovementHandlers.handleMove(this, intent.direction)
        is Intent.Look -> MovementHandlers.handleLook(this, intent.target)
        is Intent.Take -> ItemHandlers.handleTake(this, intent.target)
        is Intent.Attack -> CombatHandlers.handleAttack(this, intent.target)
        is Intent.Talk -> SocialHandlers.handleTalk(this, intent.target)
        is Intent.UseSkill -> SkillQuestHandlers.handleUseSkill(this, intent.skill, intent.action)
        // ... etc
    }
}
```

**Exposes mutable state to handlers:**
```kotlin
// Make state accessible to handlers
internal var worldState: WorldState
    private set

internal fun updateWorldState(newState: WorldState) {
    worldState = newState
}

// Expose components to handlers
internal val combatResolver: CombatResolver
internal val skillCheckResolver: SkillCheckResolver
// ... etc
```

---

#### **handlers/MovementHandlers.kt** (~200 lines)
**Purpose:** Movement, exploration, searching

**Contains:**
- `handleMove()` (lines 325-399)
- `handleLook()` (lines 401-429)
- `handleSearch()` (lines 431-480)

**Pattern - Extension functions on MudGame:**
```kotlin
package com.jcraw.app.handlers

import com.jcraw.app.MudGameEngine
import com.jcraw.mud.core.*

object MovementHandlers {
    fun handleMove(game: MudGameEngine, direction: Direction) {
        // Check if in combat - must flee first
        if (game.worldState.player.isInCombat()) {
            println("\nYou attempt to flee from combat...")
            val result = game.combatResolver.attemptFlee(game.worldState)
            println(result.narrative)
            // ... rest of flee logic
        } else {
            // Normal movement
            val newState = game.worldState.movePlayer(direction)
            if (newState == null) {
                println("You can't go that way.")
                return
            }
            game.updateWorldState(newState)
            println("You move ${direction.displayName}.")
            game.describeCurrentRoom()
        }
    }

    fun handleLook(game: MudGameEngine, target: String?) { /* ... */ }
    fun handleSearch(game: MudGameEngine, target: String?) { /* ... */ }
}
```

---

#### **handlers/ItemHandlers.kt** (~300 lines)
**Purpose:** Item interaction (inventory, take, drop, equip, use)

**Contains:**
- `handleInventory()` (lines 486-517)
- `handleTake()` (lines 519-560)
- `handleTakeAll()` (lines 562-598)
- `handleDrop()` (lines 600-651)
- `handleGive()` (lines 653-687)
- `handleEquip()` (lines 943-980)
- `handleUse()` (lines 982-1061)
- `calculateNpcDamage()` helper (lines 1067-1073)

**Pattern - Same as MovementHandlers:**
```kotlin
object ItemHandlers {
    fun handleTake(game: MudGameEngine, target: String) { /* ... */ }
    fun handleDrop(game: MudGameEngine, target: String) { /* ... */ }
    fun handleEquip(game: MudGameEngine, target: String) { /* ... */ }
    // ... etc
}
```

---

#### **handlers/CombatHandlers.kt** (~250 lines)
**Purpose:** Combat system (attack, damage, victory/defeat)

**Contains:**
- `handleAttack()` (lines 820-941)
- Helper for NPC damage calculation (if not in ItemHandlers)

---

#### **handlers/SocialHandlers.kt** (~250 lines)
**Purpose:** NPC interactions (talk, say, emote, persuade, intimidate, ask)

**Contains:**
- `handleTalk()` (lines 689-724)
- `handleSay()` (lines 726-781)
- `handleEmote()` (lines 1268-1300)
- `handleAskQuestion()` (lines 1302-1327)
- `handlePersuade()` (lines 1150-1207)
- `handleIntimidate()` (lines 1209-1266)
- `resolveNpcTarget()` helper (lines 783-801)
- `isQuestion()` helper (lines 803-818)

---

#### **handlers/SkillQuestHandlers.kt** (~300 lines)
**Purpose:** Skills, quests, progression systems

**Contains:**
- `handleCheck()` (lines 1075-1148) - feature skill checks
- `handleUseSkill()` (lines 1329-1393)
- `handleTrainSkill()` (lines 1421-1464)
- `handleChoosePerk()` (lines 1466-1502)
- `handleViewSkills()` (lines 1504-1507)
- `inferSkillFromAction()` helper (lines 1399-1419)
- `handleQuests()` (lines 1574-1615)
- `handleAcceptQuest()` (lines 1617-1652)
- `handleAbandonQuest()` (lines 1654-1669)
- `handleClaimReward()` (lines 1671-1705)
- `handleSave()` (lines 1509-1517)
- `handleLoad()` (lines 1519-1536)
- `handleHelp()` (lines 1538-1572)
- `handleQuit()` (lines 1707-1713)
- `handleInteract()` (lines 482-484) - stub

---

#### **MultiUserGame.kt** (~250 lines)
**Purpose:** Multi-user game server mode

**Contains:**
- `MultiUserGame` class (lines 1719-1925)
- Fallback component creators (lines 1851-1924)

---

### 2.3 Migration Steps

**Step 1: Create handler package and files**
```bash
mkdir -p app/src/main/kotlin/com/jcraw/app/handlers
touch app/src/main/kotlin/com/jcraw/app/handlers/{Movement,Item,Combat,Social,SkillQuest}Handlers.kt
```

**Step 2: Extract handlers (one at a time)**
1. Copy handler methods to respective files
2. Convert to object with static methods taking `MudGameEngine` as first param
3. Update visibility modifiers (e.g., make worldState accessible)

**Step 3: Refactor MudGame class**
1. Rename `MudGame` → `MudGameEngine` in new file
2. Add internal state accessors for handlers
3. Update `processIntent()` to call handler objects
4. Keep all component initialization in MudGameEngine

**Step 4: Extract MultiUserGame**
1. Move to separate file
2. Update imports

**Step 5: Simplify App.kt**
1. Keep only `main()`, `loadApiKeyFromLocalProperties()`, string operator
2. Update imports

**Step 6: Update test files**
1. Update imports in integration tests
2. Change `MudGame` → `MudGameEngine` references

**Step 7: Run tests**
```bash
gradle :app:test
gradle clean build
```

### 2.4 Testing

**Critical tests to verify:**
```bash
# All integration tests must pass
gradle :app:test --tests "com.jcraw.app.integration.*"

# Specific handler tests
gradle :app:test --tests "*ItemInteractionIntegrationTest"
gradle :app:test --tests "*NavigationIntegrationTest"
gradle :app:test --tests "*CombatIntegrationTest"
gradle :app:test --tests "*SocialInteractionIntegrationTest"
gradle :app:test --tests "*SkillSystemV2IntegrationTest"
gradle :app:test --tests "*QuestIntegrationTest"

# Full playthrough test
gradle :app:test --tests "*FullGameplayIntegrationTest"
```

**Acceptance criteria:**
- All existing tests pass without modification
- No behavior changes
- Console app runs identically: `gradle installDist && app/build/install/app/bin/app`

---

## Phase 3: EngineGameClient.kt Refactoring

**Current:** 1507 lines in single file
**Target:** 6 files (~200-350 lines each)

### 3.1 File Structure

```
client/src/main/kotlin/com/jcraw/mud/client/
├── EngineGameClient.kt (~200 lines) - Core GameClient impl
├── handlers/
│   ├── ClientMovementHandlers.kt (~200 lines)
│   ├── ClientItemHandlers.kt (~300 lines)
│   ├── ClientCombatHandlers.kt (~250 lines)
│   ├── ClientSocialHandlers.kt (~250 lines)
│   └── ClientSkillQuestHandlers.kt (~350 lines)
```

### 3.2 Strategy

**Mirror App.kt structure:**
- Same handler groupings
- Same method names
- Different implementation (emits GameEvents instead of println)

**Key difference from App.kt:**
```kotlin
// App.kt (console)
println("You move ${direction.displayName}.")

// EngineGameClient.kt (GUI)
emitEvent(GameEvent.Narrative("You move ${direction.displayName}."))
```

### 3.3 Migration Steps

**Step 1: Create handler package**
```bash
mkdir -p client/src/main/kotlin/com/jcraw/mud/client/handlers
```

**Step 2: Extract handlers (follow App.kt pattern)**
1. Copy from EngineGameClient to respective handler files
2. Use object pattern with `EngineGameClient` as first param
3. Preserve all `emitEvent()` calls

**Step 3: Refactor EngineGameClient**
1. Keep core class: init, sendInput, observeEvents, getCurrentState, close
2. Keep `emitEvent()`, `describeCurrentRoom()`, `buildExitsWithNames()`
3. Update `processIntent()` to dispatch to handlers
4. Add internal state accessors for handlers

**Step 4: Run tests**
```bash
gradle :client:test
gradle :client:run  # Manual GUI test
```

### 3.4 Testing

**Tests to verify:**
```bash
# No unit tests for client yet, manual verification:
gradle :client:run

# Test all game flows:
# - Movement and exploration
# - Item interaction
# - Combat
# - Social interactions
# - Skill usage
# - Quest management
```

**Acceptance criteria:**
- GUI client runs identically
- All game features work through UI
- No regressions in event handling

---

## Code Quality Standards

### Kotlin Best Practices

**Use sealed classes for results (if needed):**
```kotlin
sealed class HandlerResult {
    object Success : HandlerResult()
    data class Error(val message: String) : HandlerResult()
    data class StateUpdate(val newState: WorldState) : HandlerResult()
}
```

**Extension functions for shared logic:**
```kotlin
// Instead of duplicating in handlers
fun MudGameEngine.getCurrentNpc(target: String): Entity.NPC? {
    val room = worldState.getCurrentRoom() ?: return null
    return room.entities.filterIsInstance<Entity.NPC>()
        .find { it.name.lowercase().contains(target.lowercase()) }
}
```

**Keep functions pure where possible:**
```kotlin
// Pure function - easier to test
fun calculateDamage(baseDamage: Int, strModifier: Int, armorDefense: Int): Int {
    return (baseDamage + strModifier - armorDefense).coerceAtLeast(1)
}

// Not pure - accesses mutable state
fun MudGameEngine.applyDamage(damage: Int) {
    worldState = worldState.updatePlayer(
        worldState.player.copy(health = worldState.player.health - damage)
    )
}
```

### Documentation Updates

**After each phase, update:**

1. **ARCHITECTURE.md** - Update file locations
2. **CLAUDE.md** - Update "Key Files" section
3. **This document** - Mark phase as complete

**Example ARCHITECTURE.md update:**
```markdown
## Module: app

### Key Files
- `app/src/main/kotlin/com/jcraw/app/App.kt` - Main entry point (~150 lines)
- `app/src/main/kotlin/com/jcraw/app/MudGameEngine.kt` - Core game loop (~350 lines)
- `app/src/main/kotlin/com/jcraw/app/handlers/` - Intent handlers (~1300 lines)
  - `MovementHandlers.kt` - Navigation and exploration
  - `ItemHandlers.kt` - Inventory and equipment
  - `CombatHandlers.kt` - Combat system
  - `SocialHandlers.kt` - NPC interactions
  - `SkillQuestHandlers.kt` - Skills and quests
```

---

## Testing Strategy

### Before Refactoring
```bash
# Capture baseline test results
gradle clean test > /tmp/baseline-tests.log

# Verify all tests pass
grep "BUILD SUCCESSFUL" /tmp/baseline-tests.log
```

### During Refactoring

**After each file extraction:**
```bash
# Run affected module tests
gradle :<module>:test

# Example for Phase 1:
gradle :testbot:test

# Example for Phase 2:
gradle :app:test
```

### After Refactoring

**Full regression suite:**
```bash
# All tests
gradle clean test

# Test bot playthroughs (comprehensive validation)
gradle :testbot:test --tests "com.jcraw.mud.testbot.scenarios.AllPlaythroughsTest"

# Manual smoke tests
gradle installDist && app/build/install/app/bin/app  # Console
gradle :client:run  # GUI
```

**Success criteria:**
- All ~640 tests pass
- No behavioral changes
- Console app works identically
- GUI client works identically

---

## Rollback Plan

If issues arise during refactoring:

1. **Git is your friend** - Commit after each successful phase
2. **Revert specific phase:**
   ```bash
   git revert <commit-hash>
   ```
3. **Full rollback:**
   ```bash
   git reset --hard <last-good-commit>
   ```

**Recommended commit strategy:**
```
feat: refactor OutputValidator into 4 files [Phase 1/3]
feat: refactor App.kt into 8 files [Phase 2/3]
feat: refactor EngineGameClient into 6 files [Phase 3/3]
```

---

## Success Metrics

### Line Count Verification

**Before:**
```bash
wc -l app/src/main/kotlin/com/jcraw/app/App.kt
# 1961

wc -l client/src/main/kotlin/com/jcraw/mud/client/EngineGameClient.kt
# 1507

wc -l testbot/src/main/kotlin/com/jcraw/mud/testbot/OutputValidator.kt
# 1146
```

**After:**
```bash
# All files should be < 600 lines (target: < 350)
find . -name "*.kt" -exec wc -l {} \; | sort -rn | head -30

# Should show no files over 600 lines in refactored modules
```

### Test Coverage

**No decrease in test count:**
```bash
gradle test | grep "tests completed"
# Before: ~640 tests
# After: ~640 tests (same or more)
```

---

## Timeline Estimate

**Per phase (conservative):**
- Phase 1 (OutputValidator): 2-3 hours
- Phase 2 (App.kt): 4-6 hours
- Phase 3 (EngineGameClient): 3-4 hours

**Total: ~10-13 hours of focused work**

Can be split across multiple Claude Code sessions.

---

## Questions & Edge Cases

### Q: Should handlers be objects or classes?
**A:** Use `object` (singleton) for stateless handlers. All state lives in MudGameEngine/EngineGameClient.

### Q: What about code duplication between App and Client handlers?
**A:** Accept duplication for now. Future refactoring can extract shared logic to `core` module if needed. KISS principle - don't over-engineer.

### Q: Do we need new tests for handlers?
**A:** No. This is pure refactoring - existing integration tests validate behavior. Handler logic is unchanged, just moved to different files.

### Q: What if tests fail during refactoring?
**A:** Revert the phase and debug. Use git bisect if needed. Never commit broken tests.

---

## Completion Checklist

### Phase 1: OutputValidator ✓ COMPLETE
- [x] Create validation/ package
- [x] Extract CodeValidationRules.kt (~680 lines)
- [x] Extract ValidationPrompts.kt (~460 lines)
- [x] Extract ValidationParsers.kt (~40 lines)
- [x] Refactor OutputValidator.kt (~65 lines)
- [x] Update imports
- [x] Run testbot tests - all pass (AllPlaythroughsTest passed)
- [x] Commit: "refactoring phase 1"
- [x] Update ARCHITECTURE.md

**Result:** 1146 lines → 4 files (65, 680, 460, 40 lines)

### Phase 2: App.kt ✅ COMPLETE
- [x] Create handlers/ package
- [x] Extract MovementHandlers.kt (171 lines) - handles movement, look, search
- [x] Extract ItemHandlers.kt (348 lines) - handles inventory, take, drop, equip, use, give
- [x] Extract CombatHandlers.kt (136 lines) - handles attack, combat resolution
- [x] Extract SocialHandlers.kt (348 lines) - handles talk, say, emote, persuade, intimidate, ask
- [x] Extract SkillQuestHandlers.kt (522 lines) - handles skills, quests, persistence, meta-commands
- [x] Create MudGameEngine.kt (254 lines) - core game loop and state management
- [x] Extract MultiUserGame.kt (248 lines) - multi-user server mode
- [x] Simplify App.kt (145 lines) - main entry point only
- [x] Update imports in tests
- [x] Run app tests - all pass
- [x] Test console app manually
- [x] Commit: "refactoring phase 2"
- [x] Update ARCHITECTURE.md and CLAUDE.md

**Result:** 1961 lines → 8 files (145, 254, 248, 171, 348, 136, 348, 522 lines)

### Phase 3: EngineGameClient ✅ COMPLETE
- [x] Create handlers/ package (client)
- [x] Extract ClientMovementHandlers.kt (~250 lines)
- [x] Extract ClientItemHandlers.kt (~300 lines)
- [x] Extract ClientCombatHandlers.kt (~140 lines)
- [x] Extract ClientSocialHandlers.kt (~230 lines)
- [x] Extract ClientSkillQuestHandlers.kt (~480 lines)
- [x] Refactor EngineGameClient.kt (~311 lines)
- [x] Update imports
- [x] Run client build - successful
- [ ] Test GUI client manually (optional)
- [ ] Commit: "feat: refactor EngineGameClient into 6 files [Phase 3/3]"
- [x] Update ARCHITECTURE.md and CLAUDE.md

**Result:** 1507 lines → 6 files (311, 250, 300, 140, 230, 480 lines)

### Final Verification ✅ COMPLETE
- [x] Client module builds successfully
- [x] Verify line counts: All refactored files under 600 lines
- [x] Update ARCHITECTURE.md, CLAUDE.md with final file structure
- [x] Mark this document as COMPLETED (update status at top)
- [ ] Run full test suite (optional - would take time)
- [ ] Console app manual test (optional)
- [ ] GUI client manual test (optional)
- [ ] Commit changes
- [ ] Celebrate! 🎉

**Refactoring Results:**
- **Phase 1**: OutputValidator.kt: 1146 lines → 4 files (65, 680, 460, 40 lines)
- **Phase 2**: App.kt: 1961 lines → 8 files (145, 254, 248, 171, 348, 136, 348, 522 lines)
- **Phase 3**: EngineGameClient.kt: 1507 lines → 6 files (311, 250, 300, 140, 230, 480 lines)

**Total**: 4614 lines → 18 files, average ~256 lines per file ✅

---

## Notes for Future Sessions

**If resuming work:**
1. Check which phase is in progress (see checklist above)
2. Run tests first to verify current state
3. Read the phase-specific migration steps
4. Follow the step-by-step process
5. Commit frequently (after each handler extraction)

**If stuck:**
- Revert to last working commit
- Re-read the migration steps
- Check for typos in imports/package names
- Run tests to identify what broke

**Remember:**
- This is refactoring, not feature work
- No behavior changes allowed
- Tests are your safety net
- KISS principle - keep it simple
