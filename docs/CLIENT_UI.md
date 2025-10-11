# Client UI Documentation

## Overview

The AI-MUD Client UI is a Compose Multiplatform desktop application that provides a graphical interface for the game engine. It replaces the console-based interface with a polished, fantasy-themed UI featuring character selection, scrollable log, and command input.

## Architecture

### Unidirectional Data Flow

The UI follows a strict unidirectional data flow pattern:

```
User Input → ViewModel (updates State) → UI (observes State and re-renders)
```

- **UiState**: Immutable data class holding all UI state
- **GameViewModel**: Manages state and coordinates with game client
- **UI Components**: Pure composables that observe state and emit events

### Modules

```
client/
├── ui/                          # Compose UI screens
│   ├── CharacterSelectionScreen.kt
│   ├── MainGameScreen.kt
│   └── Theme.kt
├── GameClient.kt                # Interface for game engine integration
├── EngineGameClient.kt          # Real game engine implementation ✅ NEW
├── MockGameClient.kt            # Mock implementation for testing
├── GameEvent.kt                 # Sealed class for structured events
├── GameViewModel.kt             # State management
├── UiState.kt                   # UI state data classes
└── Main.kt                      # Application entry point
```

## Running the Client

```bash
# Build and run
gradle :client:run

# Or build a distributable package
gradle :client:packageDistribution
```

## Features

### Character Selection Screen

- **Grid layout** of pre-made character templates:
  - Warrior (STR-focused)
  - Rogue (DEX-focused)
  - Mage (INT-focused)
  - Cleric (WIS-focused)
  - Bard (CHA-focused)

- Each card shows:
  - Character name and description
  - All 6 D&D-style stats (STR, DEX, CON, INT, WIS, CHA)
  - Visual selection state

### Main Gameplay Screen

#### Status Bar
- Character name
- HP (current/max)
- Gold amount
- Experience points
- Theme toggle button
- Copy log button

#### Game Log
- **Auto-scrolling** text pane
- **Color-coded entries**:
  - Narrative: Standard text color
  - Player actions: Gold/primary color, italicized, prefixed with `>`
  - Combat: Red, bold
  - Quest: Gold, bold
  - System: Secondary color, italicized
  - Status: Tertiary color

- **Monospace font** for consistent formatting
- Unlimited history (scrollable)

#### Input Field
- Command text field with auto-focus
- Send button
- **Enter key** to submit
- **Up/Down arrows** for command history navigation
- Input validation (non-empty)

### Theming

Two fantasy-themed color schemes:

**Dark Theme** (default):
- Background: Very dark brown (#0F0E0B)
- Primary: Gold (#D4AF37)
- Surface: Dark stone (#1C1A15)
- Text: Cream/parchment (#E8E1D3)

**Light Theme**:
- Background: Light parchment (#F5EFE0)
- Primary: Dark bronze (#8B6F47)
- Surface: Lighter parchment (#FFFBF5)
- Text: Dark brown (#2C2416)

### UX Features

- **Auto-focus**: Input field gains focus on launch
- **Auto-scroll**: Log scrolls to new messages automatically
- **Theme toggle**: Switch between dark/light modes
- **Copy log**: Copy entire game log to clipboard
- **History navigation**: Arrow keys to recall previous commands
- **Keyboard shortcuts**:
  - Enter: Send command
  - Up: Navigate to older command
  - Down: Navigate to newer command

## Integration with Game Engine

### GameClient Interface

The client communicates with the game engine through a clean interface:

```kotlin
interface GameClient {
    suspend fun sendInput(text: String)
    fun observeEvents(): Flow<GameEvent>
    fun getCurrentState(): PlayerState?
    suspend fun close()
}
```

### Real Engine Integration (EngineGameClient)

**Status**: ✅ **COMPLETE** - The client is now fully integrated with the real game engine.

The `EngineGameClient` wraps the complete MudGame engine, providing:

**Integrated Systems**:
- ✅ LLM integration (OpenAI GPT-4)
- ✅ Procedural dungeon generation (4 themes: Crypt, Castle, Cave, Temple)
- ✅ Turn-based combat with weapons and armor
- ✅ Equipment system (equip, use consumables)
- ✅ Skill checks (D&D-style with all 6 stats)
- ✅ Quest system (6 objective types, rewards)
- ✅ NPC dialogue with personality
- ✅ RAG-enhanced memory for contextual narratives
- ✅ Save/load persistence
- ✅ Intent recognition and parsing

**API Key Configuration**:
The client looks for an OpenAI API key in this order:
1. `OPENAI_API_KEY` environment variable
2. `openai.api.key` system property
3. `local.properties` file (in project root or parent directories)

Without an API key, the engine works in fallback mode with simpler descriptions.

**Character Selection Flow**:
1. User selects a character template (Warrior, Rogue, Mage, Cleric, Bard)
2. `EngineGameClient` initializes with selected stats and procedural dungeon
3. Quest pool is generated based on dungeon theme
4. Initial room description is sent to UI
5. User can play through the full game via the GUI

### GameEvent Types

The client receives structured events from the engine:

- `GameEvent.Narrative`: Story/world descriptions
- `GameEvent.PlayerAction`: Echo of player commands
- `GameEvent.Combat`: Combat-specific messages
- `GameEvent.System`: Errors, help, meta info
- `GameEvent.Quest`: Quest updates
- `GameEvent.StatusUpdate`: HP, location changes

### Mock Client

For testing without a real engine, `MockGameClient` provides:
- Welcome messages
- Simple command recognition (look, move, attack, inventory, help, quests)
- Combat simulation with damage
- Mock status updates

## Testing

### Running Tests

```bash
gradle :client:test
```

### Test Coverage

Current tests (7 passing):
- Initial state validation
- Character selection flow
- Input handling and history
- Theme toggling
- GameEvent to LogEntry conversion
- History navigation (up/down arrows)
- Blank input rejection

### Test Strategy

Following project guidelines:
- **Behavior-focused**: Test contracts, not implementation
- **Mocked dependencies**: Use mock GameClient
- **Property-based**: Varied inputs for robust validation
- **Integration**: Full flows from selection to gameplay

## Future Enhancements

### Near-term
1. ~~**Real engine integration**~~: ✅ **COMPLETE** - Connected to actual game engine
2. **Character creation**: Allow custom character stats instead of pre-made templates
3. **Save/Load UI**: Visual save/load dialogs instead of text commands
4. **Settings panel**: Adjustable font sizes, colors, dungeon themes
5. **Quest objective tracking**: Visual indicators for quest progress
6. **Combat animations**: Visual feedback for damage, attacks

### Long-term
1. **Multiplatform**: Expand to Android, iOS, Web via Compose Multiplatform
2. **Rich formatting**: Markdown support, embedded images in narrative
3. **Sound effects**: Audio cues for combat, quests, ambient atmosphere
4. **Map visualization**: 2D/3D dungeon map overlay
5. **Multiplayer UI**: Support for multi-user game mode with chat
6. **Accessibility**: Screen reader support, high contrast modes, keybindings

## Development Guidelines

### Adding New UI Features

1. **Define state** in `UiState.kt`
2. **Add ViewModel methods** for user actions
3. **Create/update Composables** to render state
4. **Write tests** for new behaviors
5. **Update documentation**

### Styling Consistency

- Use `MaterialTheme.typography` for text styles
- Apply theme colors via `colors` parameter
- Use `FontFamily.Serif` for titles/headers
- Use `FontFamily.Monospace` for game log
- Maintain fantasy aesthetic (gold, brown, parchment tones)

### Performance Tips

- Keep composables pure (no side effects in body)
- Use `remember` for computed values
- Use `LaunchedEffect` for side effects
- Avoid unnecessary recompositions with `derivedStateOf`

## Troubleshooting

### Build Issues

**Compose plugin conflicts**:
- Ensure `org.jetbrains.compose` plugin version is compatible
- Don't mix `application` plugin with Compose's built-in run task

**Dependency issues**:
- Verify `kotlinx-coroutines-test` version matches coroutines version
- Ensure all repositories (mavenCentral, google, compose) are configured

### Runtime Issues

**UI not updating**:
- Check StateFlow collection in composables
- Verify `_uiState.update {}` calls in ViewModel
- Use Compose debugging tools

**History navigation broken**:
- Ensure index bounds checking
- Test with different history sizes
- Check direction logic (up vs down)

## API Reference

### GameViewModel

```kotlin
class GameViewModel(
    gameClient: GameClient? = null,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
)

// Public methods
fun selectCharacter(template: CharacterTemplate)
fun sendInput(input: String)
fun navigateHistory(direction: Int): String?
fun toggleTheme()
fun getLogAsText(): String
fun clearError()
fun close()

// Observable state
val uiState: StateFlow<UiState>
```

### UiState

```kotlin
data class UiState(
    val screen: Screen,                  // CharacterSelection or MainGame
    val selectedCharacter: CharacterTemplate?,
    val playerState: PlayerState?,
    val logEntries: List<LogEntry>,
    val inputHistory: List<String>,
    val historyIndex: Int,
    val theme: Theme,                    // DARK or LIGHT
    val isLoading: Boolean,
    val errorMessage: String?
)
```

### LogEntry

```kotlin
data class LogEntry(
    val text: String,
    val type: EntryType,                 // NARRATIVE, PLAYER_ACTION, etc.
    val timestamp: Long
)
```

## Contributing

When adding features:

1. Follow **KISS principle** - avoid overengineering
2. Maintain **unidirectional flow** - no callbacks to ViewModel
3. Write **behavior tests** - focus on contracts
4. Keep files **under 300-500 lines**
5. Use **sealed classes** over enums where appropriate
6. Document **why**, not just what

See `CLAUDE_GUIDELINES.md` for full development principles.
