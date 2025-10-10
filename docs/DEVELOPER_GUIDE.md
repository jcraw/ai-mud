# Developer Guide

Quick reference for working with the AI MUD codebase.

## Project Structure

```
ai-mud/
├── core/           - Data models (Room, Entity, WorldState, PlayerState)
├── perception/     - Input parsing (Intent sealed classes)
├── reasoning/      - LLM generation (RoomDescriptionGenerator)
├── action/         - Output formatting (placeholder)
├── memory/         - State persistence (placeholder)
├── llm/            - OpenAI client
├── app/            - Main game application
├── utils/          - Shared utilities (currently empty)
├── buildSrc/       - Gradle convention plugins
└── docs/           - Documentation
```

## Key Files

| File | Purpose |
|------|---------|
| `core/src/main/kotlin/com/jcraw/mud/core/Room.kt` | Room data model |
| `core/src/main/kotlin/com/jcraw/mud/core/SampleDungeon.kt` | Sample dungeon definition |
| `core/src/main/kotlin/com/jcraw/mud/core/WorldState.kt` | Immutable world state |
| `perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt` | Player intent sealed class |
| `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/RoomDescriptionGenerator.kt` | LLM description generator |
| `llm/src/main/kotlin/com/jcraw/sophia/llm/OpenAIClient.kt` | OpenAI API client |
| `app/src/main/kotlin/com/jcraw/app/App.kt` | Main game loop |

## Common Tasks

### Adding a New Room

Edit `core/src/main/kotlin/com/jcraw/mud/core/SampleDungeon.kt`:

```kotlin
private val myRoom = Room(
    id = "my_room",
    name = "My Amazing Room",
    traits = listOf(
        "trait one",
        "trait two",
        "trait three"
    ),
    exits = mapOf(Direction.NORTH to "other_room_id"),
    entities = listOf(
        Entity.Item(
            id = "item_id",
            name = "Item Name",
            description = "Item description"
        )
    )
)

// Add to rooms map
val rooms: Map<RoomId, Room> = mapOf(
    // ... existing rooms
    myRoom.id to myRoom
)
```

### Adding a New Intent

Edit `perception/src/main/kotlin/com/jcraw/mud/perception/Intent.kt`:

```kotlin
sealed class Intent {
    // ... existing intents
    data class MyIntent(val param: String) : Intent()
}
```

Add parsing in `app/src/main/kotlin/com/jcraw/app/App.kt`:

```kotlin
private fun parseInput(input: String): Intent {
    // ... existing parsing
    return when (command) {
        "mycommand" -> Intent.MyIntent(args ?: "default")
        else -> Intent.Invalid("Unknown command")
    }
}

private fun processIntent(intent: Intent) {
    when (intent) {
        // ... existing handlers
        is Intent.MyIntent -> handleMyIntent(intent)
    }
}

private fun handleMyIntent(intent: Intent.MyIntent) {
    println("Handling: ${intent.param}")
}
```

### Adding a New Entity Type

Edit `core/src/main/kotlin/com/jcraw/mud/core/Entity.kt`:

```kotlin
sealed class Entity {
    // ... existing types

    @Serializable
    data class MyEntity(
        override val id: String,
        override val name: String,
        override val description: String,
        val myProperty: Int
    ) : Entity()
}
```

### Customizing LLM Prompts

Edit `reasoning/src/main/kotlin/com/jcraw/mud/reasoning/RoomDescriptionGenerator.kt`:

```kotlin
private fun buildSystemPrompt(): String = """
    Your custom system prompt here.

    Guidelines:
    - Your guideline 1
    - Your guideline 2
""".trimIndent()
```

## Running the Game

### With LLM (requires API key)

```bash
# Set API key in local.properties
echo "openai.api.key=sk-your-key" >> local.properties

# Build and run
gradle installDist && app/build/install/app/bin/app
```

### Without LLM (fallback mode)

```bash
# Just run without API key
gradle installDist && app/build/install/app/bin/app
```

## Testing

### Run All Tests

```bash
gradle test
```

### Run Module-Specific Tests

```bash
gradle :core:test
gradle :perception:test
gradle :reasoning:test
```

### Run with Verbose Output

```bash
gradle test --info
```

### Writing Tests

Follow behavior-driven approach (see `CLAUDE_GUIDELINES.md`):

```kotlin
class MyTest {
    @Test
    fun `should do expected behavior when condition`() {
        // Arrange
        val input = setupTestData()

        // Act
        val result = systemUnderTest.perform(input)

        // Assert
        assertEquals(expected, result)
    }
}
```

## Code Style

### Naming Conventions

- **Data classes**: PascalCase (e.g., `Room`, `PlayerState`)
- **Functions**: camelCase (e.g., `generateDescription`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `STARTING_ROOM_ID`)
- **Sealed classes**: PascalCase for class, variants too (e.g., `Intent.Move`)

### File Organization

- **One public class per file** (unless sealed class hierarchy)
- **Companion objects** for constants and factory methods
- **Extension functions** in separate files or at bottom of class file
- **Keep files under 300-500 lines**

### State Management

- **Immutable by default**: Use `data class` with `val`
- **State updates**: Use `copy()` to create new instances
- **Helper methods**: Add to data class for common transformations

Example:
```kotlin
data class PlayerState(...) {
    fun addItem(item: Entity.Item): PlayerState {
        return copy(inventory = inventory + item)
    }
}
```

## LLM Integration

### Model Selection

- **Development**: `gpt-4o-mini` (cost-effective)
- **Production**: TBD (consider gpt-4o or gpt-4-turbo)

### Cost Estimation

With gpt-4o-mini:
- ~300-350 tokens per room description
- ~$0.0002 per description
- 1000 descriptions = $0.20

### Rate Limiting

Currently no rate limiting. For production, consider:
- Caching repeated descriptions
- Debouncing rapid `look` commands
- Request queuing

## Debugging

### Enable LLM Debug Output

Already enabled! Look for:
```
🌐 OpenAI API call starting...
📡 HTTP Status: 200 OK
🔥 LLM API: gpt-4o-mini | tokens=339 (225+114)
✅ OpenAI API call successful
```

### Common Issues

**API key not found**:
```
⚠️  OpenAI API key not found - using simple trait-based descriptions
```
→ Set `openai.api.key` in `local.properties`

**Build failures**:
```
gradle clean build
```

**Tests failing**:
- Check if you're using mock LLM client in tests
- Ensure test data is deterministic

## Architecture Patterns

### Data Flow

```
User Input
    ↓
parseInput() → Intent
    ↓
processIntent(intent) → Update WorldState
    ↓
describeCurrentRoom() → LLM Generation
    ↓
Output to Console
```

### Module Dependencies

```
app → perception, reasoning, llm, core
reasoning → core, llm, memory
perception → core, llm
memory → core
llm → (external: ktor, kotlinx.serialization)
core → (external: kotlinx.serialization)
```

### Async/Await

LLM calls use `suspend` functions:

```kotlin
suspend fun generateDescription(room: Room): String {
    val response = llmClient.chatCompletion(...)
    return response.choices.first().message.content
}
```

Call from synchronous context:
```kotlin
runBlocking {
    val description = generator.generateDescription(room)
}
```

## Contributing

1. **Read guidelines**: `CLAUDE_GUIDELINES.md`
2. **Check TODO**: `TODO.md` for current priorities
3. **Write tests**: Behavior-driven, not coverage-driven
4. **Keep it simple**: KISS principle
5. **Update docs**: Keep this guide and others current

## Resources

- [Kotlin Docs](https://kotlinlang.org/docs/home.html)
- [Ktor Client](https://ktor.io/docs/getting-started-ktor-client.html)
- [OpenAI API](https://platform.openai.com/docs/api-reference)
- [Gradle](https://docs.gradle.org/)

## Contact

This is a personal learning project. See commit history for development decisions.
