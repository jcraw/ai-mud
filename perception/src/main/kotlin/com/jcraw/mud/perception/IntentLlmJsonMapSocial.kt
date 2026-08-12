@file:Suppress(
    "ReturnCount",
    "MaxLineLength",
)

package com.jcraw.mud.perception

/**
 * LLM JSON map: social / combat intents.
 * Pure extract (MUD-034c) — keys and outcomes unchanged.
 */
internal object IntentLlmJsonMapSocial {

    fun mapSocial(intentType: String, target: String?, npcTarget: String?): Intent? =
        mapTalkCombat(intentType, target, npcTarget)
            ?: mapEmoteAsk(intentType, target, npcTarget)

    private fun mapTalkCombat(intentType: String, target: String?, npcTarget: String?): Intent? =
        when (intentType) {
            "talk" -> if (target != null) Intent.Talk(target) else Intent.Invalid("Intent.Talk to whom?")
            "say" -> {
                val message = target?.takeIf { it.isNotBlank() }
                if (message != null) Intent.Say(message, npcTarget) else Intent.Invalid("Intent.Say what?")
            }
            "attack" -> Intent.Attack(target)
            else -> null
        }

    private fun mapEmoteAsk(intentType: String, target: String?, npcTarget: String?): Intent? =
        when (intentType) {
            "persuade" -> if (target != null) Intent.Persuade(target) else Intent.Invalid("Intent.Persuade whom?")
            "intimidate" -> if (target != null) Intent.Intimidate(target) else Intent.Invalid("Intent.Intimidate whom?")
            "emote" -> if (target != null) Intent.Emote(target, npcTarget) else Intent.Invalid("What emotion do you want to express?")
            "ask_question" -> mapAsk(target, npcTarget)
            else -> null
        }

    private fun mapAsk(target: String?, npcTarget: String?): Intent =
        if (npcTarget != null && target != null) {
            Intent.AskQuestion(npcTarget, target)
        } else if (npcTarget == null) {
            Intent.Invalid("Ask whom?")
        } else {
            Intent.Invalid("Ask about what?")
        }
}
