# Behavior Testing Guide

Quick guide for writing manual BDD-style tests that capture player experience and "feel".

## What Is This?

A testing framework where YOU decide what to test, one test at a time. No auto-generated LLM tests - manual, focused tests that check game behavior.

Based on `docs/requirements/bdd.txt` philosophy:
- Tests as living specs
- Focus on feel, not bits
- Real game logic (InMemoryGameEngine)
- Punchy - one pain point per test

## Quick Example

```kotlin
@Test
fun `goblin taunts should menace not meme`() = runTest {
    given {
        playerInDungeon()
    }

    `when` {
        command("look")
        if (output.contains("goblin")) {
            command("talk goblin")
        }
    }

    then {
        output shouldHaveSentiment "menacing"
        output.shouldNotBeLazy()  // No "lol", "cool", etc.
        output shouldNotContain "uwu"
    }
}
```

## Location

Test files: `testbot/src/test/kotlin/com/jcraw/mud/testbot/behavior/`

- **BehaviorTestBase.kt** - Base class with setup and helpers
- **BehaviorAssertions.kt** - DSL assertions (shouldContain, shouldHaveSentiment, etc.)
- **ExampleBehaviorTests.kt** - Example tests showing usage
- **README.md** - Full reference

## How to Add a Test

### 1. Open ExampleBehaviorTests.kt (or create new file)

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyGameplayTests : BehaviorTestBase() {
    // tests here
}
```

### 2. Add a test method

```kotlin
@Test
fun `your test description here`() = runTest {
    given { /* setup */ }
    `when` { /* action */ }
    then { /* assertions */ }
}
```

### 3. Use assertions

```kotlin
// Content checks
output shouldContain "shadow"
output shouldNotContain "error"

// Quality checks
output.shouldHaveSensoryDetails(minCount = 2)
output shouldHaveWordCount 50..100
output.shouldNotBeLazy()

// Sentiment
output shouldHaveSentiment "grim"  // or "cheerful", "hostile", etc.

// Game state
output.shouldIndicateDamage()
output.shouldIndicateVictory()
```

See `BehaviorAssertions.kt` for full list.

### 4. Run the test

```bash
gradle :testbot:test --tests "MyGameplayTests"
```

Or run all behavior tests:

```bash
gradle :testbot:test --tests "*BehaviorTests"
```

## Key Features

### Real Game Logic
Uses `InMemoryGameEngine` - actual game engine with full ECS, combat, items, NPCs. No mocks.

### Optional LLM
- Tests run WITHOUT API key (fallback mode)
- Or WITH API key for full LLM features
- Set `requiresLLM = true` in your test class if you need LLM

### Commands Available

```kotlin
command("go north")
command("attack goblin")
command("take sword")
command("inventory")
// ... any game command
```

### World State Access

```kotlin
worldState().player.health
worldState().getCurrentSpace()
worldState().getEntitiesInSpace(spaceId)
```

## Common Assertions

### Narrative Quality

```kotlin
// Has descriptive/sensory words
output.shouldHaveSensoryDetails(minCount = 2)

// Not too verbose
output.shouldBePunchy(maxWords = 100)

// No lazy/informal language
output.shouldNotBeLazy()  // blocks "lol", "cool", "nice", etc.
```

### Content Checks

```kotlin
output shouldContain "keyword"
output shouldContainAny listOf("shadow", "dark", "grim")
output shouldContainAll listOf("cold", "wind", "howling")
output shouldMatchPattern "You.*attack.*for \\d+ damage"
```

### Length/Structure

```kotlin
output shouldHaveWordCount 50..100
output shouldHaveWordCount 42  // exact
output shouldHaveLineCount 3..10
output.shouldNotBeEmpty()
```

### Sentiment/Tone

```kotlin
output shouldHaveSentiment "grim"       // dark, menacing
output shouldHaveSentiment "cheerful"   // bright, happy
output shouldHaveSentiment "hostile"    // aggressive, threatening
output shouldHaveSentiment "friendly"   // welcoming, warm
output shouldHaveSentiment "mysterious" // enigmatic, strange
```

## Examples

See `testbot/src/test/kotlin/com/jcraw/mud/testbot/behavior/ExampleBehaviorTests.kt` for:

- Room description quality tests
- Combat narrative tests
- Item interaction tests
- Edge case handling
- Error message quality

## Adding Custom Assertions

Add to `BehaviorAssertions.kt`:

```kotlin
fun String.shouldIndicateCustomThing() {
    this shouldContainAny listOf("keyword1", "keyword2")
}
```

## Workflow

1. Play the game manually
2. Notice something that feels off
3. Write a test that captures what's wrong
4. Run test (it should fail - "red")
5. Fix the issue
6. Test passes ("green")
7. Repeat

From `bdd.txt`: "80% playtime, 20% test-writing"

## Notes

- **No backward compatibility needed** - Can change test data freely
- **Deterministic** - Fixed seeds, no flaky tests
- **Fast** - No network calls (unless LLM enabled)
- **Shares code** - Same InMemoryGameEngine as automated testbot
- **Easy to extend** - Add assertions, helpers as needed

## Related Docs

- `docs/requirements/bdd.txt` - BDD philosophy
- `docs/requirements/testing_bot_requirements.txt` - Automated testbot (different system)
- `testbot/src/test/kotlin/com/jcraw/mud/testbot/behavior/README.md` - Full API reference
