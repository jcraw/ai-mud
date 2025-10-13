package com.jcraw.mud.perception

import com.jcraw.mud.core.Direction
import com.jcraw.sophia.llm.LLMClient

/**
 * LLM-powered intent recognizer that converts natural language input into structured Intent objects.
 *
 * This component uses an LLM to handle flexible, natural language parsing rather than rigid regex patterns.
 * Players can type commands in many different ways and the LLM will understand their intent.
 */
class IntentRecognizer(
    private val llmClient: LLMClient?
) {

    /**
     * Parse natural language input into a structured Intent.
     *
     * If LLM is not available, falls back to simple pattern matching for basic commands.
     */
    suspend fun parseIntent(input: String, roomContext: String? = null): Intent {
        // Split compound commands (e.g., "take sword and equip it" → "take sword")
        val firstCommand = splitCompoundCommand(input)

        if (llmClient == null) {
            return parseFallback(firstCommand)
        }

        return try {
            parseLLM(firstCommand, roomContext)
        } catch (e: Exception) {
            // Fall back to simple parsing if LLM fails
            parseFallback(firstCommand)
        }
    }

    /**
     * Split compound commands by taking only the first action.
     * Handles "and", "then", commas, etc.
     */
    private fun splitCompoundCommand(input: String): String {
        // Split on common conjunctions and punctuation
        val separators = listOf(" and ", " then ", ", and ", ", then ", ",")

        for (separator in separators) {
            val parts = input.split(separator, ignoreCase = true, limit = 2)
            if (parts.size > 1) {
                // Return only the first part
                return parts[0].trim()
            }
        }

        // No compound detected, return as-is
        return input
    }

    /**
     * Use LLM to parse the player's input into a structured Intent.
     */
    private suspend fun parseLLM(input: String, roomContext: String?): Intent {
        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(input, roomContext)

        val response = llmClient!!.chatCompletion(
            modelId = "gpt-4o-mini",  // Cost-effective model for parsing
            systemPrompt = systemPrompt,
            userContext = userPrompt,
            maxTokens = 500,
            temperature = 0.0  // Low temperature for consistent parsing
        )

        val responseText = response.choices.firstOrNull()?.message?.content ?: ""
        return parseIntentFromResponse(responseText, input)
    }

    /**
     * Build the system prompt that explains intent recognition to the LLM.
     */
    private fun buildSystemPrompt(): String {
        return """
You are a command parser for a text-based MUD (Multi-User Dungeon) game.
Your job is to parse player input and determine their intent.

IMPORTANT: If the player gives a compound command (multiple actions), split it into multiple intents.
Return ONLY the FIRST intent. The system will process it, then parse the next part.

Examples of compound commands to split:
- "look around and take the sword" → First: look (no target)
- "take sword and equip it" → First: take with target "sword"
- "go north and attack the guard" → First: move with target "north"

Parse the player's input and respond with a JSON object containing:
- "intent": The type of intent (see list below)
- "target": The target entity/direction/item (if applicable)
- "reason": Brief explanation of your parsing (1 sentence)

Valid intent types:
- "move" - Move in a direction (requires "target" as direction: north/south/east/west/northeast/northwest/southeast/southwest/up/down or abbreviations: n/s/e/w/ne/nw/se/sw/u/d)
- "look" - Look at room or specific entity (target is optional, null = look at room)
- "examine" - Same as "look" - examine something in detail
- "search" - Search the current room/area for hidden items using skill check (target optional: e.g., "moss", "walls")
- "interact" - Interact with an object (requires target)
- "inventory" - View player inventory (no target)
- "take" - Pick up an item (requires target)
- "take_all" - Pick up all items in the room (no target, triggered by "take all", "get all", "take everything", etc.)
- "drop" - Drop an item (requires target)
- "talk" - Talk to an NPC (requires target)
- "attack" - Attack an NPC or continue combat (target optional if in combat)
- "equip" - Equip a weapon or armor (requires target)
- "use" - Use/consume an item (requires target)
- "check" - Perform a skill check on a feature (requires target)
- "persuade" - Persuade an NPC (requires target)
- "intimidate" - Intimidate an NPC (requires target)
- "save" - Save game (target is save name, defaults to "quicksave")
- "load" - Load game (target is save name, defaults to "quicksave")
- "quests" - View quest journal (no target)
- "accept_quest" - Accept a quest (target is quest ID, optional)
- "abandon_quest" - Abandon a quest (requires target quest ID)
- "claim_reward" - Claim quest reward (requires target quest ID)
- "help" - Show help (no target)
- "quit" - Quit game (no target)
- "invalid" - Unable to parse or unknown command (use "reason" to explain)

Important parsing rules:
1. Be flexible - understand natural language variations
2. Extract the core action and target from rambling text
3. "look around", "look around room", "look at the room" all map to look with no target
4. "look at X", "examine X", "inspect X" map to look with target X
5. "look around for items", "look for items to take" map to look with no target (lists ground items)
6. "search", "search room", "search for hidden items", "look for hidden items" all map to search (triggers skill check)
7. "search X" or "look for hidden items in X" map to search with target X
8. "move north", "go north", "n", "north", "head north" all map to move with target "north"
9. "move northwest", "go northwest", "nw", "northwest" all map to move with target "northwest"
10. "move north from throne room" → extract "north" as target
11. "look north", "look to the north" → map to look with target "north" (directional look)
12. Scenery items (walls, floor, ground, ceiling, throne, formations, etc.) are valid look targets

Response format (JSON only, no markdown):
{
  "intent": "move",
  "target": "north",
  "reason": "Player wants to move north"
}
""".trimIndent()
    }

    /**
     * Build the user prompt with the player's input and optional context.
     */
    private fun buildUserPrompt(input: String, roomContext: String?): String {
        return if (roomContext != null) {
            """
Current room context: $roomContext

Player input: "$input"

Parse the player's intent.
            """.trimIndent()
        } else {
            """
Player input: "$input"

Parse the player's intent.
            """.trimIndent()
        }
    }

    /**
     * Parse the LLM's JSON response into an Intent object.
     */
    private fun parseIntentFromResponse(responseText: String, originalInput: String): Intent {
        try {
            // Try to extract JSON from the response (in case LLM adds extra text)
            val jsonStart = responseText.indexOf('{')
            val jsonEnd = responseText.lastIndexOf('}')

            if (jsonStart == -1 || jsonEnd == -1) {
                return Intent.Invalid("Could not parse response")
            }

            val jsonText = responseText.substring(jsonStart, jsonEnd + 1)

            // Manual JSON parsing to avoid needing serialization on Intent classes
            val intentMatch = Regex(""""intent"\s*:\s*"([^"]+)"""").find(jsonText)
            val targetMatch = Regex(""""target"\s*:\s*(?:"([^"]*)"|null)""").find(jsonText)

            val intentType = intentMatch?.groupValues?.get(1) ?: return Intent.Invalid("Unknown command")
            val target = targetMatch?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }

            return when (intentType.lowercase()) {
                "move" -> {
                    val direction = Direction.fromString(target ?: "")
                    if (direction != null) {
                        Intent.Move(direction)
                    } else {
                        Intent.Invalid("Go where?")
                    }
                }
                "look", "examine" -> Intent.Look(target)
                "search" -> Intent.Search(target)
                "interact" -> if (target != null) Intent.Interact(target) else Intent.Invalid("Interact with what?")
                "inventory" -> Intent.Inventory
                "take" -> if (target != null) Intent.Take(target) else Intent.Invalid("Take what?")
                "take_all" -> Intent.TakeAll
                "drop" -> if (target != null) Intent.Drop(target) else Intent.Invalid("Drop what?")
                "talk" -> if (target != null) Intent.Talk(target) else Intent.Invalid("Talk to whom?")
                "attack" -> Intent.Attack(target)
                "equip" -> if (target != null) Intent.Equip(target) else Intent.Invalid("Equip what?")
                "use" -> if (target != null) Intent.Use(target) else Intent.Invalid("Use what?")
                "check" -> if (target != null) Intent.Check(target) else Intent.Invalid("Check what?")
                "persuade" -> if (target != null) Intent.Persuade(target) else Intent.Invalid("Persuade whom?")
                "intimidate" -> if (target != null) Intent.Intimidate(target) else Intent.Invalid("Intimidate whom?")
                "save" -> Intent.Save(target ?: "quicksave")
                "load" -> Intent.Load(target ?: "quicksave")
                "quests" -> Intent.Quests
                "accept_quest" -> Intent.AcceptQuest(target)
                "abandon_quest" -> if (target != null) Intent.AbandonQuest(target) else Intent.Invalid("Abandon which quest?")
                "claim_reward" -> if (target != null) Intent.ClaimReward(target) else Intent.Invalid("Claim reward for which quest?")
                "help" -> Intent.Help
                "quit" -> Intent.Quit
                "invalid" -> Intent.Invalid("Unknown command: ${originalInput.take(50)}. Type 'help' for available commands.")
                else -> Intent.Invalid("Unknown command: ${intentType}. Type 'help' for available commands.")
            }
        } catch (e: Exception) {
            return Intent.Invalid("Failed to parse command: ${e.message}")
        }
    }

    /**
     * Fallback parser using simple pattern matching when LLM is not available.
     * This is a simplified version that handles basic commands.
     */
    private fun parseFallback(input: String): Intent {
        val parts = input.lowercase().trim().split(Regex("\\s+"), limit = 2)
        val command = parts[0]
        val args = parts.getOrNull(1)

        return when (command) {
            "go", "move", "n", "s", "e", "w", "north", "south", "east", "west",
            "ne", "nw", "se", "sw", "northeast", "northwest", "southeast", "southwest",
            "u", "d", "up", "down" -> {
                val direction = when (command) {
                    "n", "north" -> Direction.NORTH
                    "s", "south" -> Direction.SOUTH
                    "e", "east" -> Direction.EAST
                    "w", "west" -> Direction.WEST
                    "ne", "northeast" -> Direction.NORTHEAST
                    "nw", "northwest" -> Direction.NORTHWEST
                    "se", "southeast" -> Direction.SOUTHEAST
                    "sw", "southwest" -> Direction.SOUTHWEST
                    "u", "up" -> Direction.UP
                    "d", "down" -> Direction.DOWN
                    "go", "move" -> Direction.fromString(args ?: "") ?: return Intent.Invalid("Go where?")
                    else -> return Intent.Invalid("Unknown direction")
                }
                Intent.Move(direction)
            }
            "look", "l", "examine", "inspect" -> Intent.Look(args)
            "search" -> Intent.Search(args)
            "interact" -> if (args.isNullOrBlank()) Intent.Invalid("Interact with what?") else Intent.Interact(args)
            "take", "get", "pickup", "pick" -> {
                if (args.isNullOrBlank()) {
                    Intent.Invalid("Take what?")
                } else if (args.lowercase() == "all" || args.lowercase() == "everything") {
                    Intent.TakeAll
                } else {
                    Intent.Take(args)
                }
            }
            "drop", "put" -> if (args.isNullOrBlank()) Intent.Invalid("Drop what?") else Intent.Drop(args)
            "talk", "speak", "chat" -> if (args.isNullOrBlank()) Intent.Invalid("Talk to whom?") else Intent.Talk(args)
            "attack", "kill", "fight", "hit" -> Intent.Attack(args)
            "equip", "wield", "wear" -> if (args.isNullOrBlank()) Intent.Invalid("Equip what?") else Intent.Equip(args)
            "use", "consume", "drink", "eat" -> if (args.isNullOrBlank()) Intent.Invalid("Use what?") else Intent.Use(args)
            "check", "test", "attempt", "try" -> if (args.isNullOrBlank()) Intent.Invalid("Check what?") else Intent.Check(args)
            "persuade", "convince" -> if (args.isNullOrBlank()) Intent.Invalid("Persuade whom?") else Intent.Persuade(args)
            "intimidate", "threaten" -> if (args.isNullOrBlank()) Intent.Invalid("Intimidate whom?") else Intent.Intimidate(args)
            "save" -> Intent.Save(args ?: "quicksave")
            "load" -> Intent.Load(args ?: "quicksave")
            "quests", "quest", "journal", "j" -> Intent.Quests
            "accept" -> Intent.AcceptQuest(args)
            "abandon" -> if (args.isNullOrBlank()) Intent.Invalid("Abandon which quest?") else Intent.AbandonQuest(args)
            "claim" -> if (args.isNullOrBlank()) Intent.Invalid("Claim reward for which quest?") else Intent.ClaimReward(args)
            "inventory", "i" -> Intent.Inventory
            "help", "h", "?" -> Intent.Help
            "quit", "exit", "q" -> Intent.Quit
            else -> Intent.Invalid("Unknown command: $command. Type 'help' for available commands.")
        }
    }
}
