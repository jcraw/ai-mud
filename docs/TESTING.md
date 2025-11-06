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
│   ├── GameServerTest.kt                           # Server tests
│   └── integration/                                # Integration tests
│       ├── CombatIntegrationTest.kt
│       ├── ItemInteractionIntegrationTest.kt
│       ├── QuestIntegrationTest.kt
│       └── ...
├── client/src/test/kotlin/.../client/              # GUI client tests
└── testbot/src/test/kotlin/.../testbot/
    ├── InMemoryGameEngineTest.kt                   # Engine tests
    └── scenarios/                                   # E2E scenario tests
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
QuestSystemTest.kt           // Quest lifecycle, objectives
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

IntentTest.kt                    // Intent data structures
DirectionParsingTest.kt          // Direction parsing
CommandParsingTest.kt            // Natural language commands
NaturalLanguageParsingTest.kt    // Room name navigation
```

#### Reasoning Module Tests

```kotlin
// reasoning/src/test/kotlin/com/jcraw/mud/reasoning/

SocialInteractionTest.kt         // NPC dialogue generation
SkillCheckResolverTest.kt        // D20 checks
RoomDescriptionGeneratorTest.kt  // Room descriptions
CombatResolverTest.kt            // Combat resolution logic
NPCInteractionTest.kt            // NPC behavior
QuestTrackerTest.kt              // Quest progress tracking
procedural/
  ProceduralGenerationTest.kt    // Procedural systems
  DungeonGeneratorTest.kt        // Dungeon theme generation
  NPCGeneratorTest.kt            // NPC procedural generation
```

#### Memory Module Tests

```kotlin
// memory/src/test/kotlin/com/jcraw/mud/memory/

VectorStoreTest.kt               // Vector search
MemoryManagerTest.kt             // Memory storage
PersistenceManagerTest.kt        // Save/load
PersistentVectorStoreTest.kt     // Persistent vectors
SaveLoadSystemTest.kt            // Full persistence roundtrip
```

### Layer 2: Integration Tests (App Module)

**Purpose:** Test system interactions and workflows
**Speed:** Seconds
**Dependencies:** `InMemoryGameEngine`, mocked LLM

Integration tests verify that multiple modules work together correctly. They use `InMemoryGameEngine` to simulate complete game sessions without UI or network I/O.

```kotlin
// app/src/test/kotlin/com/jcraw/app/integration/

CombatIntegrationTest.kt              // Full combat with equipment
ItemInteractionIntegrationTest.kt     // Complete item workflow
SkillCheckIntegrationTest.kt          // All 6 stat checks
SocialInteractionIntegrationTest.kt   // Persuade/intimidate flows
QuestIntegrationTest.kt               // Quest lifecycle + auto-tracking
NavigationIntegrationTest.kt          // Natural language navigation
SaveLoadIntegrationTest.kt            // Persistence roundtrip
ProceduralDungeonIntegrationTest.kt   // All 4 dungeon themes
FullGameplayIntegrationTest.kt        // Complete game loop
WorldSystemV3IntegrationTest.kt       // V3 graph-based navigation
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

#### World System V3 Integration Tests

The `WorldSystemV3IntegrationTest` suite verifies the complete V3 graph-based navigation system:

```kotlin
class WorldSystemV3IntegrationTest {

    // Graph Navigation - Tests movement using graph edges
    @Test
    fun `can navigate using graph edges in V3`() { }

    @Test
    fun `V3 navigation only allows valid graph edges`() { }

    @Test
    fun `V3 graph is fully connected via BFS`() { }

    @Test
    fun `V3 graph has loops for non-linear navigation`() { }

    // Node Types - Verifies proper node type distribution
    @Test
    fun `V3 world has hub frontier and dead-end nodes`() { }

    // Space Management - Tests lazy-fill and space properties
    @Test
    fun `V3 spaces start with stub descriptions`() { }

    @Test
    fun `V3 space properties include terrain and brightness`() { }

    @Test
    fun `V3 getCurrentSpace returns space for current player location`() { }

    // Chunk Storage - Verifies hierarchical chunk system
    @Test
    fun `V3 world has chunk storage`() { }

    @Test
    fun `V3 can add new chunk to world`() { }

    // Entity Storage - Tests V3 entity CRUD operations
    @Test
    fun `V3 can add entities to spaces`() { }

    @Test
    fun `V3 can remove entities from spaces`() { }

    @Test
    fun `V3 can replace entities in spaces`() { }

    // V2 Fallback - Ensures backward compatibility
    @Test
    fun `V3 methods gracefully handle V2-only worlds`() { }

    @Test
    fun `V2 methods still work on V3 worlds with rooms`() { }
}
```

**Coverage:**
- ✅ Graph-based navigation using EdgeData
- ✅ Node type assignment (Hub, Linear, Branching, DeadEnd, Boss, Frontier)
- ✅ Graph connectivity validation (BFS reachability, loop detection)
- ✅ Space component management and lazy-fill stubs
- ✅ Chunk storage and hierarchical world structure
- ✅ Entity CRUD operations in V3 spaces
- ✅ V2/V3 compatibility and graceful fallback

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

**Total Tests:** ~793 across all modules
**Pass Rate:** 100%

**Module Breakdown:**
- ✅ **core**: 112 tests
- ✅ **perception**: 8 tests
- ✅ **reasoning**: 40 tests
- ✅ **memory**: 15 tests
- ✅ **app**: 148 integration tests (including 20 V3 graph navigation tests)
- ✅ **client**: 7 tests
- ✅ **testbot**: 12 E2E scenario tests

**Test Migration Complete:** All legacy shell scripts have been migrated to proper Kotlin tests and deleted.

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
