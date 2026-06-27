package com.jetsetter.pro.feature.translator

/**
 * A supported language for the (mock) translator. [flag] is an emoji shown in the picker chips.
 * Module-prefixed name to avoid collisions with other features.
 */
data class TranslatorLanguage(
    val code: String,
    val name: String,
    val flag: String,
) {
    companion object {
        val English = TranslatorLanguage(code = "EN", name = "English", flag = "🇺🇸")
        val Japanese = TranslatorLanguage(code = "JA", name = "Japanese", flag = "🇯🇵")

        /** Resolves a persisted [code] back to a known language, defaulting to English. */
        fun fromCode(code: String): TranslatorLanguage = when (code) {
            Japanese.code -> Japanese
            else -> English
        }
    }
}

/** A canned, ready-to-use travel phrase with its translation and romaji pronunciation. */
data class TranslatorPhrase(
    val id: String,
    val category: String,
    val source: String,
    val target: String,
    val pronunciation: String,
)

/**
 * The outcome of a (mock) translation request, rendered in the result card.
 * [matched] is real, not faked: true when the text resolved against the offline phrasebook,
 * false when it fell back to the demo engine — the result card's status chip is derived from it.
 */
data class TranslatorResult(
    val sourceText: String,
    val translatedText: String,
    val pronunciation: String,
    val note: String,
    val matched: Boolean,
)

/**
 * A finished translation kept in the "Recent" list. Stores the language direction by code so the
 * exact pairing can be restored when a recent is tapped. Persisted via `ModuleStateStore` (Moshi).
 */
data class TranslatorRecentEntry(
    val sourceText: String,
    val translatedText: String,
    val pronunciation: String,
    val fromCode: String,
    val toCode: String,
    val matched: Boolean,
)
