# Testing Strategy & Guidelines

*Last Updated: 2025-10-15*

## Overview

This document outlines the comprehensive testing strategy for the AI MUD engine. The testing architecture follows Kotlin/Gradle best practices and clean architecture principles with three distinct test layers:

1. **Unit Tests** - Fast, isolated tests in each module
2. **Integration Tests** - Cross-system tests using `InMemoryGameEngine`
3. **E2E Tests** - LLM-powered exploratory scenario tests

## Architecture

### Test Organization

```
ai-mud/
├── core/src/test/kotlin/com/jcraw/mud/core/        # Core domain tests
├── perception/src/test/kotlin/.../perception/      # Input parsing tests
├── reasoning/src/test/kotlin/.../reasoning/        # Game logic tests
├── memory/src/test/kotlin/.../memory/              # Persistence tests
├── action/src/test/kotlin/.../action/              # Output formatting tests
├── llm/src/test/kotlin/.../llm/                    # LLM client tests
├── app/src/test/kotlin/com/jcraw/app/
│   ├── GameServerTest.kt                           # Existing server test
│   └── integration/                                # NEW: Integration tests
│       ├── CombatIntegrationTest.kt
│       ├── ItemInteractionIntegrationTest.kt
│       ├── QuestIntegrationTest.kt
│       └── ...
├── client/src/test/kotlin/.../client/              # GUI client tests
└── testbot/src/test/kotlin/.../testbot/
    ├── InMemoryGameEngineTest.kt                   # Engine tests
    └── scenarios/                                   # NEW: E2E scenario tests
        ├── BruteForcePlaythroughTest.kt
        ├── SmartPlaythroughTest.kt
        └── ...
```

### Why Per-Module Tests?

✅ **Standard Kotlin/Gradle structure** - Familiar to all developers
✅ **Fast unit tests** - Run in milliseconds without dependencies
✅ **Clear ownership** - Tests live with the code they verify
✅ **Easy CI/CD** - `gradle test` runs everything automatically
✅ **Better isolation** - Module tests only depend on their module
✅ **Parallel execution** - Gradle runs module tests concurrently

## Test Layers

### Layer 1: Unit Tests (Module-Level)

**Purpose:** Test individual components in isolation
**Speed:** Milliseconds
**Mocking:** LLM calls, external dependencies

#### Core Module Tests

```kotlin
// core/src/test/kotlin/com/jcraw/mud/core/

CombatSystemTest.kt          // Combat mechanics, damage calculation
QuestSystemTest.kt           // Quest lifecycle, objectives ✅
EquipmentSystemTest.kt       // Equip/unequip weapons & armor
InventorySystemTest.kt       // Add/remove/find items
WorldStateTest.kt            // Room navigation, entity lookup
DirectionNavigationTest.kt   // Direction parsing and validation
EntityHierarchyTest.kt       // NPC, Item, Feature hierarchy
```

**Example:**
```kotlin
class EquipmentSystemTest {
    @Nested
    inner class WeaponEquipping {
        @Test
        fun `player can equip weapon from inventory`() {
            val player = PlayerState(/* ... */)
            val sword = Entity.Item(name = "Sword", damageBonus = 5)

            val playerWithSword = player.addItem(sword)
            val equipped = playerWithSword.equipItem(sword.id)

            assertEquals(sword.id, equipped.equippedWeapon?.id)
            assertFalse(equipped.inventory.contains(sword))
        }
    }
}
```

#### Perception Module Tests

```kotlin
// perception/src/test/kotlin/com/jcraw/mud/perception/

IntentTest.kt                    // Intent data structures ✅
DirectionParsingTest.kt          // Direction parsing ✅
CommandParsingTest.kt            // Natural language commands
NaturalLanguageParsingTest.kt    // Room name navigation
```

#### Reasoning Module Tests

```kotlin
// reasoning/src/test/kotlin/com/jcraw/mud/reasoning/

SocialInteractionTest.kt         // NPC dialogue generation ✅
SkillCheckResolverTest.kt        // D20 checks ✅
RoomDescriptionGeneratorTest.kt  // Room descriptions ✅
CombatResolverTest.kt            // Combat resolution logic
NPCInteractionTest.kt            // NPC behavior
QuestTrackerTest.kt              // Quest progress tracking
procedural/
  ProceduralGenerationTest.kt    // Procedural systems ✅
  DungeonGeneratorTest.kt        // Dungeon theme generation
  NPCGeneratorTest.kt            // NPC procedural generation
```

#### Memory Module Tests

```kotlin
// memory/src/test/kotlin/com/jcraw/mud/memory/

VectorStoreTest.kt               // Vector search ✅
MemoryManagerTest.kt             // Memory storage ✅
PersistenceManagerTest.kt        // Save/load ✅
PersistentVectorStoreTest.kt     // Persistent vectors ✅
SaveLoadSystemTest.kt            // Full persistence roundtrip
```

### Layer 2: Integration Tests (App Module)

**Purpose:** Test system interactions and workflows
**Speed:** Seconds
**Dependencies:** `InMemoryGameEngine`, mocked LLM

Integration tests verify that multiple modules work together correctly. They use `InMemoryGameEngine` to simulate complete game sessions without UI or network I/O.

```kotlin
// app/src/test/kotlin/com/jcraw/app/integration/

CombatIntegrationTest.kt              // Full combat with equipment ✅
ItemInteractionIntegrationTest.kt     // Complete item workflow ✅
SkillCheckIntegrationTest.kt          // All 6 stat checks
SocialInteractionIntegrationTest.kt   // Persuade/intimidate flows
QuestIntegrationTest.kt               // Quest lifecycle + auto-tracking ✅
NavigationIntegrationTest.kt          // Natural language navigation
SaveLoadIntegrationTest.kt            // Persistence roundtrip
ProceduralDungeonIntegrationTest.kt   // All 4 dungeon themes
FullGameplayIntegrationTest.kt        // Complete game loop
```

**Example:**
```kotlin
class CombatIntegrationTest {

    @Nested
    inner class BasicCombat {
        @Test
        fun `player defeats NPC in combat`() = runBlocking {
            val worldState = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(worldState)

            // Navigate to NPC
            engine.processInput("north")

            // Start combat
            var response = engine.processInput("attack guard")
            assertTrue(response.contains("attack") || response.contains("combat"))

            // Fight until NPC is dead
            repeat(20) {
                response = engine.processInput("attack guard")
                if (response.contains("defeated") || response.contains("dies")) {
                    return@runBlocking
                }
            }

            fail("Combat did not resolve in 20 turns")
        }
    }

    @Nested
    inner class EquipmentModifiers {
        @Test
        fun `equipped weapon increases damage`() = runBlocking {
            // Test weapon bonuses work in actual combat
        }

        @Test
        fun `equipped armor reduces incoming damage`() = runBlocking {
            // Test armor defense works in actual combat
        }
    }

    @Nested
    inner class CombatEnd {
        @Test
        fun `player death shows respawn prompt`() = runBlocking {
            // Test death/respawn flow
        }

        @Test
        fun `defeated NPC drops loot`() = runBlocking {
            // Test loot generation
        }
    }
}
```

### Layer 3: E2E Scenario Tests (Testbot Module)

**Purpose:** Validate complete user scenarios with LLM decision-making
**Speed:** Minutes
**Dependencies:** Real LLM calls (can be mocked for CI)

E2E tests use the `testbot` to play through complete scenarios, simulating real player behavior with LLM-powered decision making.

```kotlin
// testbot/src/test/kotlin/com/jcraw/mud/testbot/scenarios/

BruteForcePlaythroughTest.kt     // Combat-focused playthrough
SmartPlaythroughTest.kt          // Strategic playthrough
BadPlaythroughTest.kt            // Death/failure scenarios
AllPlaythroughsTest.kt           // Suite runner
```

**Example:**
```kotlin
class SmartPlaythroughTest {
    @Test
    fun `bot completes smart playthrough successfully`() = runBlocking {
        val result = TestRunner.runScenario(
            scenarioType = ScenarioType.SMART_PLAYTHROUGH,
            maxSteps = 15,
            theme = CryptTheme
        )

        assertEquals(TestResult.PASSED, result.outcome)
        assertTrue(result.stepResults.all { it.isValid })
        println("Completed in ${result.stepResults.size} steps")
    }
}
```

## Testing Principles (from CLAUDE_GUIDELINES.md)

### Focus on Behavior, Not Coverage

❌ **Don't test:**
- Getters/setters
- Pure data classes
- Trivial constructors
- Obvious implementation details

✅ **Do test:**
- Business logic contracts
- Edge cases and boundaries
- Failure scenarios
- System interactions
- State transitions

### Example: Good vs Bad Tests

**Bad (trivial):**
```kotlin
@Test
fun `player has a name`() {
    val player = PlayerState(id = "p1", name = "Hero", currentRoomId = "r1")
    assertEquals("Hero", player.name)
}
```

**Good (behavior):**
```kotlin
@Test
fun `player cannot equip weapon they don't own`() {
    val player = PlayerState(id = "p1", name = "Hero", currentRoomId = "r1")
    val sword = Entity.Item(id = "sword", name = "Sword", damageBonus = 5)

    val result = player.equipItem(sword.id)

    assertEquals(player, result) // No change
    assertNull(result.equippedWeapon)
}
```

### Property-Based Testing

Test edge cases and boundaries:

```kotlin
@ParameterizedTest
@ValueSource(ints = [-1, 0, 1, 50, 100, 101, 1000])
fun `health cannot exceed max health`(newHealth: Int) {
    val player = PlayerState(
        id = "p1",
        name = "Hero",
        currentRoomId = "r1",
        health = 50,
        maxHealth = 100
    )

    val updated = player.copy(health = newHealth)

    assertTrue(updated.health <= updated.maxHealth)
    assertTrue(updated.health >= 0)
}
```

### Mock LLM Calls

Always mock LLM interactions for deterministic tests:

```kotlin
private class StubLLMClient(private val response: String) : LLMClient {
    var callCount = 0
        private set

    override suspend fun chatCompletion(
        modelId: String,
        systemPrompt: String,
        userContext: String,
        maxTokens: Int,
        temperature: Double
    ): OpenAIResponse {
        callCount++
        return OpenAIResponse(
            id = "stub-${callCount}",
            `object` = "chat.completion",
            created = System.currentTimeMillis(),
            model = modelId,
            choices = listOf(
                OpenAIChoice(
                    message = OpenAIMessage(role = "assistant", content = response),
                    finishReason = "stop"
                )
            ),
            usage = OpenAIUsage(
                promptTokens = 10,
                completionTokens = response.length,
                totalTokens = 10 + response.length
            )
        )
    }

    override suspend fun createEmbedding(text: String, model: String): List<Double> {
        return List(1536) { 0.0 } // OpenAI embedding dimensions
    }

    override fun close() {}
}
```

### Use @Nested Classes for Organization

```kotlin
class QuestIntegrationTest {

    @Nested
    inner class QuestAcceptance {
        @Test
        fun `player can accept available quest`() { }

        @Test
        fun `player cannot accept quest twice`() { }
    }

    @Nested
    inner class ObjectiveTracking {
        @Test
        fun `kill objective tracks NPC defeat`() { }

        @Test
        fun `collect objective tracks item pickup`() { }

        @Test
        fun `explore objective tracks room entry`() { }
    }

    @Nested
    inner class RewardClaiming {
        @Test
        fun `player receives XP and gold on claim`() { }

        @Test
        fun `player receives quest items on claim`() { }

        @Test
        fun `cannot claim incomplete quest`() { }
    }
}
```

## Shell Script Migration Plan

### Current Shell Scripts → Unit Tests

| Shell Script | New Test File | Module | Type | Status |
|-------------|---------------|--------|------|--------|
| `test_combat.sh` | `CombatIntegrationTest.kt` | app | Integration | ✅ Complete (7 tests) |
| `test_items.sh` | `ItemInteractionIntegrationTest.kt` | app | Integration | ✅ Complete (13 tests) |
| `test_skill_checks.sh` | `SkillCheckIntegrationTest.kt` | app | Integration | ✅ Complete (14 tests) |
| `test_social.sh` | `SocialInteractionIntegrationTest.kt` | app | Integration | ✅ Complete (13 tests) |
| `test_quests.sh` | `QuestIntegrationTest.kt` | app | Integration | ✅ Complete (11 tests) |
| `test_save_load.sh` | `SaveLoadIntegrationTest.kt` | app | Integration | ✅ Complete (13 tests) |
| `test_procedural.sh` | `ProceduralDungeonIntegrationTest.kt` | app | Integration | ⏳ Pending |
| `test_game.sh` | `FullGameplayIntegrationTest.kt` | app | Integration | ⏳ Pending |
| `test_exits_debug.sh` | `NavigationIntegrationTest.kt` | app | Integration | ⏳ Pending |
| `test_brute_force_playthrough.sh` | `BruteForcePlaythroughTest.kt` | testbot | E2E | ⏳ Pending |
| `test_smart_playthrough.sh` | `SmartPlaythroughTest.kt` | testbot | E2E | ⏳ Pending |
| `test_bad_playthrough.sh` | `BadPlaythroughTest.kt` | testbot | E2E | ⏳ Pending |
| `test_all_playthroughs.sh` | `AllPlaythroughsTest.kt` | testbot | E2E | ⏳ Pending |

### Migration Process

For each shell script:

1. **Read the script** - Understand what it tests
2. **Identify test cases** - Extract distinct scenarios
3. **Write unit test** - Create test class with @Nested groups
4. **Use InMemoryGameEngine** - For integration tests
5. **Add assertions** - Verify expected behavior
6. **Run and verify** - Ensure tests pass
7. **Mark script deprecated** - Add comment pointing to new test
8. **Delete script** - After verification period

**Example migration:**

**Before (test_combat.sh):**
```bash
#!/bin/bash
echo "Testing combat system..."
echo -e "look\neast\ntake sword\nequip sword\nnorth\nattack guard\nattack guard\nattack guard" | \
  gradle installDist && app/build/install/app/bin/app
```

**After (CombatIntegrationTest.kt):**
```kotlin
class CombatIntegrationTest {
    @Test
    fun `player can defeat guard with equipped sword`() = runBlocking {
        val worldState = SampleDungeon.createInitialWorldState()
        val engine = InMemoryGameEngine(worldState)

        // Navigate and equip weapon
        engine.processInput("east")
        engine.processInput("take sword")
        engine.processInput("equip sword")

        // Go to guard
        engine.processInput("north")

        // Fight
        var defeated = false
        repeat(20) {
            val response = engine.processInput("attack guard")
            if (response.contains("defeated") || response.contains("dies")) {
                defeated = true
            }
        }

        assertTrue(defeated, "Guard should be defeated within 20 turns")
    }
}
```

## Running Tests

### Run All Tests
```bash
gradle test
```

### Run Tests for Specific Module
```bash
gradle :core:test
gradle :app:test
gradle :testbot:test
```

### Run Specific Test Class
```bash
gradle :app:test --tests "CombatIntegrationTest"
```

### Run Specific Test Method
```bash
gradle :app:test --tests "CombatIntegrationTest.player can defeat guard*"
```

### Run with Output
```bash
gradle test --info
gradle test --debug
```

### Continuous Testing
```bash
gradle test --continuous
```

## CI/CD Integration

### GitHub Actions Example
```yaml
name: Test Suite

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v2

    - name: Set up JDK 17
      uses: actions/setup-java@v2
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Run unit tests
      run: gradle test

    - name: Run integration tests
      run: gradle :app:test

    - name: Run E2E tests (mocked)
      run: gradle :testbot:test
      env:
        OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}

    - name: Upload test results
      uses: actions/upload-artifact@v2
      with:
        name: test-results
        path: '**/build/test-results/**/*.xml'
```

## Test Coverage Goals

We don't aim for line coverage metrics. Instead, we aim for:

✅ **All critical paths tested** - Combat, quests, items, navigation
✅ **All edge cases covered** - Empty inventory, zero health, invalid input
✅ **All failure modes tested** - Can't equip, can't move, invalid commands
✅ **All integrations verified** - Module boundaries work correctly
✅ **Regression prevention** - Past bugs have tests

## Current Test Status

**Total Tests:** ~220 (across all modules)
**Pass Rate:** 100% (all core, perception, reasoning, memory, app, client tests passing)

**Module Breakdown:**
- ✅ core: 8 test files, 112 tests (includes 82 new Phase 1 unit tests)
- ✅ perception: 2 test files, 8 tests
- ✅ reasoning: 5 test files, 40 tests
- ✅ memory: 4 test files, 15 tests
- ✅ app: 6 integration test files, 71 tests (Phase 2 complete, Phase 3: 1/4 complete)
- ✅ client: 1 test file, 7 tests
- ✅ testbot: InMemoryGameEngine infrastructure

## Implementation Phases

### Phase 1: Core Unit Tests - ✅ COMPLETE (2025-10-15)
Priority: **HIGH** ✅
Focus: Fill gaps in core module testing

- ✅ `EquipmentSystemTest.kt` - 16 tests for equip/unequip weapons & armor
- ✅ `InventorySystemTest.kt` - 19 tests for add/remove/find items
- ✅ `WorldStateTest.kt` - 33 tests for room navigation, entity lookup
- ✅ `CombatResolverTest.kt` - 14 tests for damage calculation, turn order

**Outcome:** 82 new behavioral tests created, all passing

### Phase 2: Integration Tests - ✅ COMPLETE (2025-10-15)
Priority: **HIGH** ✅
Focus: Replace shell script tests with proper integration tests

**Pre-requisite:**
- ✅ Fix `app/src/test/kotlin/com/jcraw/app/GameServerTest.kt` compilation errors (COMPLETE - removed outdated test)
- ✅ Add testbot dependency to app module for InMemoryGameEngine access

**Tests Created (58 total tests):**
- ✅ `CombatIntegrationTest.kt` - **7 tests, all passing** ✅
  - Player defeats weak NPC
  - Equipped weapon increases damage
  - Equipped armor reduces incoming damage
  - Player death ends combat
  - Defeated NPC removed from room
  - Player can flee from combat
  - Combat progresses through multiple rounds
- ✅ `ItemInteractionIntegrationTest.kt` - **13 tests, all passing** ✅
  - Take single/all items
  - Drop items (including equipped)
  - Equip weapons and armor
  - Weapon swapping
  - Use consumables (in and out of combat)
  - Healing potion mechanics
  - Non-pickupable items
  - Inventory display
- ✅ `QuestIntegrationTest.kt` - **11 tests, all passing** ✅
  - Quest acceptance
  - Kill objective auto-tracking
  - Collect objective auto-tracking
  - Explore objective auto-tracking
  - Talk objective auto-tracking
  - Skill check objective auto-tracking
  - Deliver objective (documented, pending implementation)
  - Multi-objective quest completion
  - Reward claiming
  - Quest log display
  - Quest abandonment
- ✅ `SkillCheckIntegrationTest.kt` - **14 tests, all passing** ✅
  - All 6 stat checks (STR, DEX, CON, INT, WIS, CHA)
  - Difficulty levels (Easy, Medium, Hard)
  - Success/failure outcomes
  - Stat modifier effects on success rates
  - Multiple sequential skill checks
  - Edge cases with very high/low stats
- ✅ `SocialInteractionIntegrationTest.kt` - **13 tests, all passing** ✅
  - Talk to NPCs (friendly and hostile)
  - Persuasion checks (success/failure)
  - Intimidation checks (success/failure)
  - Difficulty levels (easy vs hard)
  - Cannot persuade/intimidate same NPC twice
  - CHA modifier effects on success rates
  - Multiple NPCs independently

**Outcome:** Phase 2 complete! 5 integration test files with 58 total tests, 100% passing

### Phase 3: Complex Integration (2-3 days)
Priority: **MEDIUM**
Focus: Advanced workflows and persistence

- ✅ `SaveLoadIntegrationTest.kt` - **13 tests, all passing** ✅
  - Save game state to disk
  - Load game state from disk
  - Persistence roundtrip (save → modify → load → verify)
  - Save file format validation (JSON structure)
  - Load after game modifications
  - Non-existent save file handling
  - Custom save names
  - List all saves
  - Delete save files
  - Combat state preservation
  - Room connections preservation
- [ ] `ProceduralDungeonIntegrationTest.kt` - All themes generate correctly
- [ ] `NavigationIntegrationTest.kt` - Natural language navigation
- [ ] `FullGameplayIntegrationTest.kt` - Complete playthrough

**Progress:** 1/4 tests complete

### Phase 4: Bot Migration (1-2 days)
Priority: **LOW**
Focus: Migrate shell script E2E tests to testbot

- [ ] `BruteForcePlaythroughTest.kt` - Combat-focused playthrough
- [ ] `SmartPlaythroughTest.kt` - Strategic playthrough
- [ ] `BadPlaythroughTest.kt` - Death/failure scenarios
- [ ] `AllPlaythroughsTest.kt` - Suite runner
- [ ] Delete shell scripts after verification

## Best Practices Summary

1. **Test behavior, not implementation** - Verify contracts and outcomes
2. **No trivial tests** - Every test should catch real bugs
3. **Mock external dependencies** - LLM, file I/O, network
4. **Use @Nested classes** - Organize related tests
5. **Property-based testing** - Test edge cases and boundaries
6. **Descriptive names** - `player cannot equip weapon they don't own`
7. **Arrange-Act-Assert** - Clear test structure
8. **Fast unit tests** - Milliseconds, not seconds
9. **Deterministic** - Same input = same output
10. **Executable documentation** - Tests explain why features matter

## Resources

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Kotlin Test Documentation](https://kotlinlang.org/api/latest/kotlin.test/)
- [Gradle Testing Guide](https://docs.gradle.org/current/userguide/java_testing.html)
- [CLAUDE_GUIDELINES.md](../CLAUDE_GUIDELINES.md) - Project testing philosophy

## Questions?

See existing test examples:
- `reasoning/src/test/kotlin/com/jcraw/mud/reasoning/SocialInteractionTest.kt` - LLM mocking
- `core/src/test/kotlin/com/jcraw/mud/core/QuestSystemTest.kt` - @Nested organization
- `testbot/src/test/kotlin/com/jcraw/mud/testbot/InMemoryGameEngineTest.kt` - Integration pattern
