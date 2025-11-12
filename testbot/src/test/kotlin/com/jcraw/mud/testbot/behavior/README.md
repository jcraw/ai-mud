# Behavior Testing Framework

Manual BDD-style testing framework for AI-MUD. Write tests that capture player "feel" and experience.

## Quick Start

### 1. Create a Test File

```kotlin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyBehaviorTests : BehaviorTestBase() {

    @Test
    fun `my test description`() = runTest {
        given { /* setup */ }
        `when` { /* action */ }
        then { /* assertions */ }
    }
}
```

### 2. Write Tests Using BDD Style

```kotlin
@Test
fun `entering new biome has sensory details`() = runTest {
    given {
        playerInDungeon()
    }

    `when` {
        command("go north")
    }

    then {
        output.shouldHaveSensoryDetails(minCount = 2)
        output shouldHaveWordCount 50..100
        output.shouldNotBeLazy()
    }
}
```

### 3. Use Assertions

Available assertions (see `BehaviorAssertions.kt`):

**Content:**
- `output shouldContain "shadow"`
- `output shouldNotContain "error"`
- `output shouldContainAny listOf("dark", "grim")`
- `output shouldContainAll listOf("shadow", "cold")`
- `output shouldMatchPattern "You.*attack.*goblin"`

**Length/Structure:**
- `output shouldHaveWordCount 50..100`
- `output shouldHaveLineCount 3..10`
- `output.shouldNotBeEmpty()`

**Sentiment/Tone:**
- `output shouldHaveSentiment "grim"`  // grim, cheerful, mysterious, hostile, friendly
- `output.shouldNotBeLazy()`  // No "cool", "lol", "meme"

**Narrative Quality:**
- `output.shouldHaveSensoryDetails(minCount = 2)`  // Checks for descriptive words
- `output.shouldBePunchy(maxWords = 100)`  // Not too verbose

**Game State:**
- `output.shouldIndicateDamage()`
- `output.shouldIndicateVictory()`
- `output.shouldIndicateFailure()`
- `output.shouldIndicateItemAcquired()`

## Features

### Real Game Logic
- Uses `InMemoryGameEngine` - actual game engine, no mocks
- Full ECS, WorldState, intent processing
- Real combat, items, NPCs, everything

### Optional LLM
- Set `requiresLLM = true` to require API key
- Set `requiresLLM = false` (default) to run without LLM
- LLM components used if API key available, fallback otherwise

### Flexible Setup

Override `createInitialWorldState()` for custom worlds:

```kotlin
override fun createInitialWorldState(): WorldState {
    // Return custom world state
    return MyCustomWorld.create()
}
```

### Helper Methods

```kotlin
// Run commands
command("go north")
command("attack goblin")

// Access world state
worldState().player.health
worldState().getCurrentSpace()

// Reset between tests
reset()
```

## Examples

See `ExampleBehaviorTests.kt` for working examples:
- Room description quality
- Combat narrative
- Item interaction
- Error handling
- Edge cases

## Adding Tests

1. Create new test file or add to existing
2. Extend `BehaviorTestBase`
3. Write tests using `@Test` and `runTest { }`
4. Use assertions from `BehaviorAssertions.kt`
5. Run with `gradle :testbot:test`

## Philosophy

Based on `docs/requirements/bdd.txt`:
- **Tests as living specs** - Executable documentation of player experience
- **Focus on feel** - Not implementation details
- **Black-box** - Test inputs and outputs, not internals
- **Punchy** - One behavior per test

## Notes

- Tests share code with existing testbot infrastructure
- Uses same InMemoryGameEngine as automated LLM tests
- No mocks - real game logic
- Fast execution (no network calls if no LLM)
- Easy to add new assertions as needed
