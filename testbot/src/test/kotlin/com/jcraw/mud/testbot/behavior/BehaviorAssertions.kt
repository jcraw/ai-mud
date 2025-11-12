package com.jcraw.mud.testbot.behavior

import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.fail

/**
 * DSL-style assertions for behavior testing.
 * Focus on "feel" and player experience, not just technical correctness.
 *
 * Examples:
 * - output shouldContain "shadow"
 * - output shouldNotContain "error"
 * - output shouldHaveWordCount 50..100
 * - output shouldHaveSentiment "grim"
 * - output shouldMatchPattern "You.*attack.*goblin"
 */

// ====================
// Content assertions
// ====================

infix fun String.shouldContain(substring: String) {
    assertTrue(
        this.contains(substring, ignoreCase = true),
        "Expected output to contain '$substring' but got:\n$this"
    )
}

infix fun String.shouldNotContain(substring: String) {
    assertFalse(
        this.contains(substring, ignoreCase = true),
        "Expected output NOT to contain '$substring' but got:\n$this"
    )
}

infix fun String.shouldContainAny(keywords: List<String>) {
    val found = keywords.any { this.contains(it, ignoreCase = true) }
    assertTrue(
        found,
        "Expected output to contain any of ${keywords.joinToString()} but got:\n$this"
    )
}

infix fun String.shouldContainAll(keywords: List<String>) {
    val missing = keywords.filter { !this.contains(it, ignoreCase = true) }
    assertTrue(
        missing.isEmpty(),
        "Expected output to contain all of ${keywords.joinToString()} but missing: ${missing.joinToString()}\nGot:\n$this"
    )
}

infix fun String.shouldMatchPattern(regex: String) {
    assertTrue(
        Regex(regex, RegexOption.IGNORE_CASE).containsMatchIn(this),
        "Expected output to match pattern '$regex' but got:\n$this"
    )
}

// ====================
// Length/structure assertions
// ====================

infix fun String.shouldHaveWordCount(range: IntRange) {
    val wordCount = this.split(Regex("\\s+")).size
    assertTrue(
        wordCount in range,
        "Expected word count in range $range but got $wordCount words:\n$this"
    )
}

infix fun String.shouldHaveWordCount(count: Int) {
    val wordCount = this.split(Regex("\\s+")).size
    assertTrue(
        wordCount == count,
        "Expected exactly $count words but got $wordCount:\n$this"
    )
}

infix fun String.shouldHaveLineCount(range: IntRange) {
    val lineCount = this.lines().filter { it.isNotBlank() }.size
    assertTrue(
        lineCount in range,
        "Expected line count in range $range but got $lineCount lines:\n$this"
    )
}

fun String.shouldNotBeEmpty() {
    assertTrue(
        this.isNotBlank(),
        "Expected output to not be empty"
    )
}

// ====================
// Sentiment/tone assertions
// ====================

/**
 * Basic keyword-based sentiment check.
 * For more sophisticated checks, pass LLM client to validate.
 */
infix fun String.shouldHaveSentiment(sentiment: String) {
    val keywords = when (sentiment.lowercase()) {
        "grim", "dark", "menacing" -> listOf("shadow", "dark", "grim", "ominous", "menace", "threat", "danger")
        "cheerful", "bright", "happy" -> listOf("bright", "cheerful", "happy", "warm", "pleasant", "joy")
        "mysterious", "enigmatic" -> listOf("mysterious", "strange", "curious", "enigmatic", "unknown")
        "hostile", "aggressive" -> listOf("hostile", "aggressive", "attack", "threaten", "snarl", "growl")
        "friendly", "welcoming" -> listOf("friendly", "welcome", "smile", "warm", "kind")
        else -> fail("Unknown sentiment: $sentiment")
    }

    val matches = keywords.count { this.contains(it, ignoreCase = true) }
    assertTrue(
        matches >= 1,
        "Expected sentiment '$sentiment' (keywords: ${keywords.joinToString()}) but got:\n$this"
    )
}

// ====================
// Narrative quality assertions
// ====================

/**
 * Check that output has descriptive details (adjectives, sensory words).
 * Looks for at least `minCount` sensory/descriptive words.
 */
fun String.shouldHaveSensoryDetails(minCount: Int = 2) {
    val sensoryWords = listOf(
        // Visual
        "dark", "bright", "shadowy", "gleaming", "dim", "glowing", "crimson", "azure",
        // Auditory
        "echoing", "silent", "whispering", "thunderous", "crackling", "howling",
        // Tactile
        "cold", "warm", "rough", "smooth", "sharp", "soft",
        // Olfactory
        "acrid", "musty", "fragrant", "putrid", "sweet",
        // General descriptive
        "ancient", "massive", "tiny", "ornate", "crude", "elegant", "twisted"
    )

    val count = sensoryWords.count { this.contains(it, ignoreCase = true) }
    assertTrue(
        count >= minCount,
        "Expected at least $minCount sensory details but found $count:\n$this"
    )
}

/**
 * Check that output avoids generic/lazy descriptions.
 */
fun String.shouldNotBeLazy() {
    val lazyPhrases = listOf(
        "nice", "interesting", "cool", "awesome", "lol", "haha", "meme"
    )

    val found = lazyPhrases.filter { this.contains(it, ignoreCase = true) }
    assertTrue(
        found.isEmpty(),
        "Output contains lazy/informal language: ${found.joinToString()}\n$this"
    )
}

/**
 * Check that description is within "punchy" length (not too verbose).
 */
fun String.shouldBePunchy(maxWords: Int = 100) {
    val wordCount = this.split(Regex("\\s+")).size
    assertTrue(
        wordCount <= maxWords,
        "Expected description to be punchy (<= $maxWords words) but got $wordCount:\n$this"
    )
}

// ====================
// Game state assertions
// ====================

/**
 * Assert that player took damage.
 */
fun String.shouldIndicateDamage() {
    this shouldContainAny listOf("damage", "hit", "hurt", "wounded", "strike")
}

/**
 * Assert that combat succeeded.
 */
fun String.shouldIndicateVictory() {
    this shouldContainAny listOf("victory", "defeated", "slain", "killed", "triumph")
}

/**
 * Assert that action failed.
 */
fun String.shouldIndicateFailure() {
    this shouldContainAny listOf("failed", "can't", "cannot", "unable", "miss")
}

/**
 * Assert that item was acquired.
 */
fun String.shouldIndicateItemAcquired() {
    this shouldContainAny listOf("take", "pick", "acquire", "obtain", "get")
}
