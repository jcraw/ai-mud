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
    companion object {
        private val SAY_QUESTION_KEYWORDS = listOf(
            "who", "what", "where", "when", "why", "how",
            "can", "will", "is", "are", "am",
            "do", "does", "did",
            "should", "could", "would",
            "have", "has", "had",
            "tell me", "explain", "describe"
        )

        private val SAY_ARTICLES = setOf("the", "a", "an")
    }

    /**
     * Parse natural language input into a structured Intent.
     *
     * If LLM is not available, falls back to simple pattern matching for basic commands.
     *
     * @param input The player's text input
     * @param roomContext Optional room context description
     * @param exitsWithNames Map of available exits to their destination room names (for navigation)
     */
    suspend fun parseIntent(
        input: String,
        roomContext: String? = null,
        exitsWithNames: Map<Direction, String>? = null
    ): Intent {
        // Split compound commands (e.g., "take sword and equip it" → "take sword")
        val firstCommand = splitCompoundCommand(input)

        if (llmClient == null) {
            return parseFallback(firstCommand)
        }

        return try {
            parseLLM(firstCommand, roomContext, exitsWithNames)
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
    private suspend fun parseLLM(
        input: String,
        roomContext: String?,
        exitsWithNames: Map<Direction, String>?
    ): Intent {
        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(input, roomContext, exitsWithNames)

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
- "give" - Give an item to an NPC (requires "target" for item and "npc_target" for NPC, e.g., "give sword to guard")
- "talk" - Talk to an NPC (requires target)
- "say" - Speak a line of dialogue (use "target" for the message text, optional "npc_target" for whom it's addressed)
- "attack" - Attack an NPC or continue combat (target optional if in combat, extract ANY identifying word from NPC name)
- "equip" - Equip a weapon or armor (requires target, extract ANY identifying word from item name)
- "use" - Use/consume an item (requires target, extract ANY identifying word from item name)
- "check" - Perform a skill check on a feature (requires target, extract ANY identifying word)
- "persuade" - Persuade an NPC (requires target, extract ANY identifying word from NPC name)
- "intimidate" - Intimidate an NPC (requires target, extract ANY identifying word from NPC name)
- "save" - Save game (target is save name, defaults to "quicksave")
- "load" - Load game (target is save name, defaults to "quicksave")
- "quests" - View quest journal (no target)
- "accept_quest" - Accept a quest (target is quest ID, optional)
- "abandon_quest" - Abandon a quest (requires target quest ID)
- "claim_reward" - Claim quest reward (requires target quest ID)
- "emote" - Express emotion/action (requires "target" for emote type like smile/wave/nod/shrug/laugh/cry/bow, optional "npc_target" for directed emotes like "smile at guard")
- "ask_question" - Ask NPC about topic (requires "npc_target" for NPC and "target" for topic, e.g., "ask guard about castle")
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
11. ROOM-NAME NAVIGATION: If player says "go to [room name]" and room name matches an exit destination, determine the direction
    - Example: Player says "go to throne room" and exits show "north -> Throne Room" → return move with target "north"
    - Example: Player says "go to the crypt" and exits show "east -> Crypt Entrance" → return move with target "east"
    - Match room names flexibly: "throne", "throne room", "the throne room" should all match "Throne Room"
12. "look north", "look to the north" → map to look with target "north" (directional look)
13. Scenery items (walls, floor, ground, ceiling, throne, formations, etc.) are valid look targets
14. PARTIAL NAMES: "attack king" when NPC is "skeleton king" → extract "king", "take sword" when item is "rusty sword" → extract "sword"
15. For attack/talk/persuade/intimidate with NPCs, extract ANY word from the player's input that could identify the NPC
16. For equip/use/take/drop with items, extract ANY word from the player's input that could identify the item
17. "say" or dialogue lines should map to the "say" intent with the spoken words in "target" and optional NPC in "npc_target"

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
    private fun buildUserPrompt(
        input: String,
        roomContext: String?,
        exitsWithNames: Map<Direction, String>?
    ): String {
        val parts = mutableListOf<String>()

        if (roomContext != null) {
            parts.add("Current room context: $roomContext")
        }

        if (!exitsWithNames.isNullOrEmpty()) {
            val exitsText = exitsWithNames.entries.joinToString("\n") { (dir, name) ->
                "  ${dir.displayName} -> $name"
            }
            parts.add("Available exits:\n$exitsText")
        }

        parts.add("Player input: \"$input\"")
        parts.add("Parse the player's intent.")

        return parts.joinToString("\n\n")
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
            val npcTargetMatch = Regex(""""npc_target"\s*:\s*(?:"([^"]*)"|null)""").find(jsonText)

            val intentType = intentMatch?.groupValues?.get(1) ?: return Intent.Invalid("Unknown command")
            val target = targetMatch?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }
            val npcTarget = npcTargetMatch?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }

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
                "give" -> {
                    if (target != null && npcTarget != null) {
                        Intent.Give(target, npcTarget)
                    } else if (target == null) {
                        Intent.Invalid("Give what?")
                    } else {
                        Intent.Invalid("Give to whom?")
                    }
                }
                "talk" -> if (target != null) Intent.Talk(target) else Intent.Invalid("Talk to whom?")
                "say" -> {
                    val message = target?.takeIf { it.isNotBlank() }
                    if (message != null) {
                        Intent.Say(message, npcTarget)
                    } else {
                        Intent.Invalid("Say what?")
                    }
                }
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
                "emote" -> if (target != null) Intent.Emote(target, npcTarget) else Intent.Invalid("What emotion do you want to express?")
                "ask_question" -> {
                    if (npcTarget != null && target != null) {
                        Intent.AskQuestion(npcTarget, target)
                    } else if (npcTarget == null) {
                        Intent.Invalid("Ask whom?")
                    } else {
                        Intent.Invalid("Ask about what?")
                    }
                }
                "help" -> Intent.Help
                "quit" -> Intent.Quit
                "invalid" -> Intent.Invalid("Unknown command: ${originalInput.take(50)}. Type 'help' for available commands.")
                else -> Intent.Invalid("Unknown command: ${intentType}. Type 'help' for available commands.")
            }
        } catch (e: Exception) {
            return Intent.Invalid("Failed to parse command: ${e.message}")
        }
    }

    private fun parseSay(args: String?): Intent {
        if (args.isNullOrBlank()) {
            return Intent.Invalid("Say what?")
        }

        val (npcTarget, message) = extractSayComponents(args)
        val trimmedMessage = message.trim()

        return if (trimmedMessage.isEmpty()) {
            Intent.Invalid("Say what?")
        } else {
            Intent.Say(trimmedMessage, npcTarget)
        }
    }

    private fun extractSayComponents(raw: String): Pair<String?, String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null to ""
        }

        val lower = trimmed.lowercase()
        val prefix = when {
            lower.startsWith("to ") -> "to "
            lower.startsWith("at ") -> "at "
            else -> null
        }

        if (prefix != null) {
            val remainder = trimmed.substring(prefix.length).trim()
            if (remainder.isEmpty()) {
                return null to ""
            }

            val (targetPart, messagePart) = splitSayRemainder(remainder)
            val cleanedTarget = sanitizeNpcTarget(targetPart)
            val fallbackMessage = if (messagePart.isNotBlank()) messagePart else remainder
            return cleanedTarget to fallbackMessage
        }

        return null to trimmed
    }

    private fun splitSayRemainder(remainder: String): Pair<String?, String> {
        val normalized = remainder.trim()
        if (normalized.isEmpty()) {
            return null to ""
        }

        val lower = normalized.lowercase()
        var messageStart = -1

        for (keyword in SAY_QUESTION_KEYWORDS) {
            val idx = lower.indexOf(keyword)
            if (idx != -1) {
                val validBoundary = idx == 0 || !lower[idx - 1].isLetter()
                if (validBoundary && (messageStart == -1 || idx < messageStart)) {
                    messageStart = idx
                }
            }
        }

        if (messageStart > 0 && messageStart < normalized.length) {
            val potentialTarget = normalized.substring(0, messageStart).trim()
            val message = normalized.substring(messageStart).trim()
            if (message.isNotEmpty()) {
                return potentialTarget to message
            }
        } else if (messageStart == 0) {
            // Message starts immediately, no explicit target
            return null to normalized
        }

        val delimiterIndices = listOf(
            normalized.indexOf(':'),
            normalized.indexOf(',')
        ).filter { it > 0 && it < normalized.length - 1 }
            .sorted()

        if (delimiterIndices.isNotEmpty()) {
            val delimiterIndex = delimiterIndices.first()
            val target = normalized.substring(0, delimiterIndex).trim()
            val message = normalized.substring(delimiterIndex + 1).trim()
            if (message.isNotEmpty()) {
                return target to message
            }
        }

        val words = normalized.split(Regex("\\s+"))
        if (words.size > 1) {
            val targetWords = if (words[0].lowercase() in SAY_ARTICLES && words.size > 2) {
                listOf(words[0], words[1])
            } else {
                listOf(words[0])
            }

            val target = targetWords.joinToString(" ")
            val message = normalized.substring(target.length).trim()
            if (message.isNotEmpty()) {
                return target to message
            }
        }

        return null to normalized
    }

    private fun sanitizeNpcTarget(raw: String?): String? {
        if (raw == null) {
            return null
        }

        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        val lower = trimmed.lowercase()
        val article = SAY_ARTICLES.firstOrNull { lower.startsWith("$it ") }
        val withoutArticle = if (article != null) {
            trimmed.substring(article.length + 1).trim()
        } else {
            trimmed
        }

        return withoutArticle.ifBlank { null }
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
            "give", "deliver" -> {
                if (args.isNullOrBlank()) {
                    Intent.Invalid("Give what?")
                } else {
                    // Parse "give [item] to [npc]" or "give [item] [npc]"
                    val parts = args.split(Regex("\\s+to\\s+|\\s+"), limit = 2)
                    if (parts.size < 2) {
                        Intent.Invalid("Give to whom?")
                    } else {
                        Intent.Give(parts[0].trim(), parts[1].trim())
                    }
                }
            }
            "say", "tell" -> parseSay(args)
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
            "smile", "wave", "nod", "shrug", "laugh", "cry", "bow" -> {
                // Parse "smile at guard" or just "smile"
                val emoteType = command
                if (args.isNullOrBlank()) {
                    Intent.Emote(emoteType, null)
                } else {
                    // Remove "at" if present
                    val target = args.removePrefix("at ").trim()
                    Intent.Emote(emoteType, target)
                }
            }
            "emote" -> {
                if (args.isNullOrBlank()) {
                    Intent.Invalid("What emotion do you want to express?")
                } else {
                    // Parse "emote smile at guard" or "emote smile"
                    val parts = args.split(Regex("\\s+at\\s+|\\s+"), limit = 2)
                    val emoteType = parts[0].trim()
                    val target = parts.getOrNull(1)?.trim()
                    Intent.Emote(emoteType, target)
                }
            }
            "ask" -> {
                if (args.isNullOrBlank()) {
                    Intent.Invalid("Ask whom?")
                } else {
                    // Parse "ask guard about castle" or "ask guard castle"
                    val parts = args.split(Regex("\\s+about\\s+|\\s+"), limit = 2)
                    if (parts.size < 2) {
                        Intent.Invalid("Ask about what?")
                    } else {
                        Intent.AskQuestion(parts[0].trim(), parts[1].trim())
                    }
                }
            }
            "inventory", "i" -> Intent.Inventory
            "help", "h", "?" -> Intent.Help
            "quit", "exit", "q" -> Intent.Quit
            else -> Intent.Invalid("Unknown command: $command. Type 'help' for available commands.")
        }
    }
}
