@file:Suppress(
    "ReturnCount",
    "CyclomaticComplexMethod",
    "LongMethod",
)

package com.jcraw.mud.perception

/**
 * Intent.Say-command pure helpers for [IntentRecognizer].
 * Pure extract (MUD-034c) — no parsing semantics change.
 */
internal object IntentSayParse {

    val SAY_QUESTION_KEYWORDS = listOf(
        "who", "what", "where", "when", "why", "how",
        "can", "will", "is", "are", "am",
        "do", "does", "did",
        "should", "could", "would",
        "have", "has", "had",
        "tell me", "explain", "describe"
    )

    val SAY_ARTICLES = setOf("the", "a", "an")

    fun parseSay(args: String?): Intent {
        if (args.isNullOrBlank()) {
            return Intent.Invalid("Intent.Say what?")
        }

        val (npcTarget, message) = extractSayComponents(args)
        val trimmedMessage = message.trim()

        return if (trimmedMessage.isEmpty()) {
            Intent.Invalid("Intent.Say what?")
        } else {
            Intent.Say(trimmedMessage, npcTarget)
        }
    }

    fun extractSayComponents(raw: String): Pair<String?, String> {
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

    fun splitSayRemainder(remainder: String): Pair<String?, String> {
        val normalized = remainder.trim()
        if (normalized.isEmpty()) {
            return null to ""
        }
        keywordSplit(normalized)?.let { return it }
        delimiterSplit(normalized)?.let { return it }
        return wordSplit(normalized)
    }

    private fun keywordSplit(normalized: String): Pair<String?, String>? {
        val messageStart = findKeywordMessageStart(normalized.lowercase())
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
        return null
    }

    private fun findKeywordMessageStart(lower: String): Int {
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
        return messageStart
    }

    private fun delimiterSplit(normalized: String): Pair<String?, String>? {
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
        return null
    }

    private fun wordSplit(normalized: String): Pair<String?, String> {
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

    fun sanitizeNpcTarget(raw: String?): String? {
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
}
