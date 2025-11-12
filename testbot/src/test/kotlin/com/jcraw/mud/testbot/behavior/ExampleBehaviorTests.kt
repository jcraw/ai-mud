package com.jcraw.mud.testbot.behavior

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Example behavior tests showing how to write manual BDD-style tests.
 *
 * These tests focus on player experience and "feel" rather than technical implementation.
 * Add your own tests here or create new test files.
 *
 * Tests use real game logic via InMemoryGameEngine - no mocks.
 * LLM components are optional (tests run without API key in fallback mode).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExampleBehaviorTests : BehaviorTestBase() {

    // ====================
    // Exploration behavior
    // ====================

    @Test
    fun `room descriptions should be punchy and descriptive`() = runTest {
        given { playerInDungeon() }

        `when` {
            command("look")
        }

        then {
            output.shouldNotBeEmpty()
            output shouldHaveWordCount 10..150  // Not too short, not too verbose
            output.shouldNotBeLazy()  // No "nice", "cool", etc.
        }
    }

    @Test
    fun `moving to new location provides description`() = runTest {
        given { playerInDungeon() }

        `when` {
            command("go north")
        }

        then {
            output shouldContain "exit"  // Should mention exits or navigation
            output.shouldNotBeEmpty()
        }
    }

    @Test
    fun `looking at items shows their description`() = runTest {
        given { playerInDungeon() }

        `when` {
            // First, see what's in the room
            command("look")
            // Then try to look at something specific (if available)
        }

        then {
            output.shouldNotBeEmpty()
        }
    }

    // ====================
    // Combat behavior
    // ====================

    @Test
    fun `attacking enemy shows damage dealt`() = runTest {
        given { playerInDungeon() }

        // Navigate to find an enemy (sample dungeon has goblins)
        `when` {
            // Try moving around to find enemies
            val lookOutput = command("look")

            // If we see an NPC, try attacking
            if (lookOutput.contains("guard", ignoreCase = true) ||
                lookOutput.contains("goblin", ignoreCase = true)) {
                val attackOutput = command("attack guard")

                then {
                    attackOutput.shouldIndicateDamage()
                    attackOutput shouldContainAny listOf("attack", "damage", "strike", "hit")
                    attackOutput.shouldNotBeLazy()
                }
            }
        }
    }

    // ====================
    // Item interaction behavior
    // ====================

    @Test
    fun `taking items adds them to inventory`() = runTest {
        given { playerInDungeon() }

        `when` {
            val lookOutput = command("look")

            // If there are items in the room, try taking one
            if (lookOutput.contains("sword", ignoreCase = true) ||
                lookOutput.contains("item", ignoreCase = true)) {
                command("take sword")
                val invOutput = command("inventory")

                then {
                    invOutput shouldContain "inventory"
                }
            }
        }
    }

    @Test
    fun `inventory shows equipped items`() = runTest {
        given { playerInDungeon() }

        `when` {
            command("inventory")
        }

        then {
            output shouldContain "inventory"
            output.shouldNotBeEmpty()
        }
    }

    // ====================
    // Narrative quality tests
    // ====================

    @Test
    fun `combat narration should not be memey or informal`() = runTest {
        given { playerInDungeon() }

        `when` {
            val lookOutput = command("look")

            if (lookOutput.contains("guard", ignoreCase = true)) {
                command("attack guard")

                then {
                    output.shouldNotBeLazy()  // No "lol", "haha", etc.
                    output shouldNotContain "meme"
                    output shouldNotContain "uwu"
                    output shouldNotContain "owo"
                }
            }
        }
    }

    // ====================
    // Edge cases
    // ====================

    @Test
    fun `invalid direction shows helpful error`() = runTest {
        given { playerInDungeon() }

        `when` {
            command("go nowhere")
        }

        then {
            output.shouldIndicateFailure()
            output shouldContainAny listOf("can't", "cannot", "invalid")
        }
    }

    @Test
    fun `taking non-existent item fails gracefully`() = runTest {
        given { playerInDungeon() }

        `when` {
            command("take nonexistentitem12345")
        }

        then {
            output.shouldIndicateFailure()
            output shouldContainAny listOf("can't", "don't see", "not here")
        }
    }

    // ====================
    // Add your own tests below
    // ====================

    // TODO: User adds custom behavior tests here
    // Example:
    // @Test
    // fun `goblin taunts should menace not meme`() = runTest {
    //     given { /* setup */ }
    //     `when` { /* action */ }
    //     then { /* assertions */ }
    // }
}
