# Multi-User Architecture

Complete documentation for the multi-user/multi-player system.

## Overview

The foundation for multi-user support is fully implemented. The system supports multiple concurrent players in a shared world with thread-safe state management.

## Architecture Changes

### PlayerId System
```kotlin
typealias PlayerId = String
```
Unique identifier for each player in the shared world.

### WorldState Refactoring
**Before (Single-player)**:
```kotlin
data class WorldState(
    val player: PlayerState,
    // ...
)
```

**After (Multi-player)**:
```kotlin
data class WorldState(
    val players: Map<PlayerId, PlayerState>,
    // ...
)
```

**Backward Compatibility**: The `worldState.player` property still works (returns first player) for single-player code.

### Per-Player Combat
Combat state moved from `WorldState` to `PlayerState`. Each player has independent combat:
```kotlin
data class PlayerState(
    // ...
    val activeCombat: CombatState? = null
)
```

### Multi-Player API

#### Player Management
```kotlin
// Add a new player to the world
fun addPlayer(playerState: PlayerState): WorldState

// Remove a player from the world
fun removePlayer(playerId: PlayerId): WorldState

// Get a specific player's state
fun getPlayer(playerId: PlayerId): PlayerState?
```

#### Room & Movement
```kotlin
// Get the room a specific player is in
fun getCurrentRoom(playerId: PlayerId): Room?

// Move a specific player
fun movePlayer(playerId: PlayerId, direction: Direction): WorldState?

// Get all players in a room
fun getPlayersInRoom(roomId: RoomId): List<PlayerState>
```

## Components

### GameServer
**Location**: `app/src/main/kotlin/com/jcraw/app/GameServer.kt`

Manages the shared `WorldState` and coordinates multiple player sessions.

**Features**:
- Thread-safe mutations with Kotlin Mutex
- Processes intents from multiple players
- Broadcasts events to relevant players
- Maintains player session registry

**Key Methods**:
```kotlin
suspend fun addPlayerSession(session: PlayerSession, startingRoomId: RoomId)
suspend fun removePlayerSession(playerId: PlayerId)
suspend fun processIntent(playerId: PlayerId, intent: Intent): String
```

### PlayerSession
**Location**: `app/src/main/kotlin/com/jcraw/app/PlayerSession.kt`

Handles individual player I/O and maintains player context.

**Features**:
- Per-player input/output streams
- Event channel for asynchronous notifications
- Player-specific state tracking

**Key Methods**:
```kotlin
fun sendMessage(message: String)
fun readLine(): String?
suspend fun notifyEvent(event: GameEvent)
fun processEvents(): List<String>
```

### GameEvent System
**Location**: `app/src/main/kotlin/com/jcraw/app/GameEvent.kt`

Sealed class hierarchy for player actions and events.

**Event Types**:
- `PlayerJoined` - Player entered the game/room
- `PlayerLeft` - Player exited the game/room
- `PlayerMoved` - Player moved to another room
- `PlayerSaid` - Player spoke (chat)
- `CombatAction` - Combat event occurred
- `GenericAction` - Generic player action

### Entity.Player
**Location**: `core/src/main/kotlin/com/jcraw/mud/core/Room.kt`

New entity type representing other players visible in rooms:
```kotlin
data class Player(
    override val id: String,
    override val name: String,
    override val description: String,
    val playerId: PlayerId,
    val health: Int,
    val maxHealth: Int,
    val equippedWeapon: String? = null,
    val equippedArmor: String? = null
) : Entity()
```

## Threading & Concurrency

### Mutex Protection
All world state mutations are protected by a `Mutex`:
```kotlin
private val stateMutex = Mutex()

suspend fun processIntent(playerId: PlayerId, intent: Intent): String = stateMutex.withLock {
    // Thread-safe state access and mutation
    // ...
}
```

### Event Broadcasting
Events are broadcast asynchronously via channels:
1. Player performs action
2. GameServer creates GameEvent
3. Event broadcast to all players in the room
4. Each PlayerSession receives event in its channel
5. Events processed on next input prompt

## Integration

### MultiUserGame Class
**Location**: `app/src/main/kotlin/com/jcraw/app/App.kt`

Manages GameServer lifecycle and player sessions.

**Features**:
- Initializes GameServer with world state
- Creates and manages player sessions
- Fallback LLM clients if no API key
- Mode selection at startup

### Mode Selection
At game start, users choose between:
1. **Single-player mode** - Traditional MudGame class
2. **Multi-user mode (local)** - GameServer architecture

## Supported Intents

All intents work in multi-user context:
- Movement: `Move`
- Interaction: `Look`, `Take`, `Drop`, `Talk`, `Interact`
- Combat: `Attack`
- Equipment: `Equip`, `Use`
- Skills: `Check`, `Persuade`, `Intimidate`
- Meta: `Inventory`, `Help`, `Quit`
- Quests: `Quests` (other quest intents not yet supported in multi-user)

## Current Limitations

### Not Yet Implemented in Multi-User Mode
- `Save`/`Load` - Persistence not supported (world state is shared)
- Quest management - `AcceptQuest`, `AbandonQuest`, `ClaimReward`
  - Quest viewing works
  - Quest acceptance/claiming coming soon

### Network Layer (Future)
Currently, multi-user mode only supports local players (via stdio). Future work:
1. TCP/WebSocket server for remote connections
2. Client protocol for network communication
3. Authentication and session management
4. Connection pooling and reconnection logic

## Example Usage

### Starting Multi-User Mode
```bash
gradle installDist && app/build/install/app/bin/app
# Select option 2 for Multi-user mode
```

### Adding a Player (Programmatically)
```kotlin
val gameServer = GameServer(
    worldState = initialWorldState,
    memoryManager = memoryManager,
    roomDescriptionGenerator = descriptionGenerator,
    npcInteractionGenerator = npcInteractionGenerator,
    combatResolver = combatResolver,
    combatNarrator = combatNarrator,
    skillCheckResolver = skillCheckResolver
)

val session = PlayerSession(
    playerId = "player_1",
    playerName = "Alice",
    input = stdin.bufferedReader(),
    output = stdout.bufferedWriter()
)

gameServer.addPlayerSession(session, startingRoomId = "entrance")
```

### Processing Player Actions
```kotlin
val response = gameServer.processIntent("player_1", Intent.Move(Direction.NORTH))
session.sendMessage(response)
```

### Broadcasting Events
When a player moves, other players in the room see:
```
[Alice] leaves to the north.
```

When a player enters:
```
[Alice] arrives from the south.
```

## Testing

### GameServerTest
**Location**: `app/src/test/kotlin/com/jcraw/app/GameServerTest.kt`

Tests for multi-user functionality:
- Player session management
- Intent processing
- Event broadcasting
- Thread safety

Note: Some tests may need updating after recent changes.

## Future Enhancements

### Planned Features
1. **Network layer** - TCP/WebSocket support
2. **Player visibility** - See other players as entities in rooms
3. **Player interactions** - Trade, party system, PvP
4. **Shared quests** - Party-based quest objectives
5. **Chat system** - Player-to-player messaging
6. **Persistence** - Save shared world state
7. **Admin commands** - World management, player moderation

### Design Considerations
- **Scalability**: Current Mutex design works for ~10-100 players
- **Latency**: Local mode has no latency; network will add RTT
- **State size**: WorldState grows with players/rooms/entities
- **Memory**: In-memory RAG system may need optimization for large worlds

## Comparison: Single vs Multi-User

| Feature | Single-Player | Multi-User |
|---------|--------------|------------|
| Player count | 1 | N (thread-safe) |
| Save/Load | ✅ Full support | ❌ Not supported |
| Quests | ✅ Full support | ⚠️ Viewing only |
| Combat | ✅ Per-player | ✅ Per-player |
| LLM Features | ✅ Full support | ✅ Full support |
| Network | N/A | 🔄 Coming soon |
| State isolation | Complete | Shared world |
| Thread safety | Not needed | ✅ Mutex-protected |

## Troubleshooting

### Player Not Found Error
Ensure player was added via `addPlayerSession()` before processing intents.

### Race Conditions
All state mutations should go through GameServer's `stateMutex.withLock {}`.

### Event Not Received
Check that the event's `roomId` matches the player's current room.

### Memory Leaks
Remove player sessions properly via `removePlayerSession()` when players disconnect.

## References

- **WorldState**: [core/src/main/kotlin/com/jcraw/mud/core/WorldState.kt](../core/src/main/kotlin/com/jcraw/mud/core/WorldState.kt)
- **PlayerState**: [core/src/main/kotlin/com/jcraw/mud/core/PlayerState.kt](../core/src/main/kotlin/com/jcraw/mud/core/PlayerState.kt)
- **GameServer**: [app/src/main/kotlin/com/jcraw/app/GameServer.kt](../app/src/main/kotlin/com/jcraw/app/GameServer.kt)
- **PlayerSession**: [app/src/main/kotlin/com/jcraw/app/PlayerSession.kt](../app/src/main/kotlin/com/jcraw/app/PlayerSession.kt)
- **GameEvent**: [app/src/main/kotlin/com/jcraw/app/GameEvent.kt](../app/src/main/kotlin/com/jcraw/app/GameEvent.kt)
