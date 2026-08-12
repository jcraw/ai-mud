@file:Suppress(
    "ReturnCount",
    "MaxLineLength",
)

package com.jcraw.mud.perception

import com.jcraw.mud.core.Direction

/**
 * LLM JSON map: navigation / look / search / interact.
 * Pure extract (MUD-034c) — keys and outcomes unchanged.
 */
internal object IntentLlmJsonMapNav {

    fun mapNav(
        intentType: String,
        target: String?
    ): Intent? {
        return when (intentType) {
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
            "interact" -> if (target != null) Intent.Interact(target) else Intent.Invalid("Intent.Interact with what?")
            else -> null
        }
    }
}
