# AI-MUD

An AI-powered MUD (Multi-User Dungeon) engine that will use LLMs to generate dynamic, variable descriptions and handle natural language player interactions.

## Current Status

**⚠️ Early Development Phase** - This project is in the very beginning stages. Currently contains:

- Basic Gradle multi-module project structure
- OpenAI LLM client implementation (copied from another project)
- Requirements documentation outlining the vision
- No game logic or MUD mechanics implemented yet

## Vision

The goal is to create a text-based roleplaying game where:
- Players navigate rooms via natural language commands
- LLM generates unique descriptions each time (based on fixed room traits)
- Combat and interactions get richer narratives through AI
- Everything feels fresh and adaptive while maintaining consistency

See `docs/requirements.txt` for detailed specifications.

## Development

This project uses [Gradle](https://gradle.org/) with Kotlin.

### Commands

* `./gradlew build` - Build the project
* `./gradlew check` - Run all checks including tests
* `./gradlew clean` - Clean all build outputs

Note: `./gradlew run` is configured but there's no main application logic yet.

### Project Structure

- **app** - Will contain the main game application (currently empty)
- **utils** - Shared utilities and common code (currently empty)
- **llm** - OpenAI client for LLM integration (implemented but not integrated into build)
- **buildSrc** - Shared build logic and convention plugins

### Next Steps

Implementation will follow the requirements in `docs/requirements.txt` to build:
1. Room graph and world state management
2. Natural language input parsing
3. Dynamic content generation
4. Game mechanics (exploration, skills, combat)
5. Console-based user interface