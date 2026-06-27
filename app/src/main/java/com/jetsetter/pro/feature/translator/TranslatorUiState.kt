package com.jetsetter.pro.feature.translator

/**
 * Single immutable UI-state object for the Translator screen — one object rather than scattered
 * flags, so impossible states can't arise and previews are trivial.
 *
 * Every value the UI shows is either held here or *derived* from these fields (no faked numbers):
 * the character counter, over-limit validation and the Translate-button enablement are all
 * computed from [inputText], so they always agree with what's typed.
 */
data class TranslatorUiState(
    val fromLanguage: TranslatorLanguage = TranslatorLanguage.English,
    val toLanguage: TranslatorLanguage = TranslatorLanguage.Japanese,
    val inputText: String = "",
    val isTranslating: Boolean = false,
    val result: TranslatorResult? = null,
    val phrases: List<TranslatorPhrase> = emptyList(),
    val recents: List<TranslatorRecentEntry> = emptyList(),
    /** Transient flag: the active translation was just copied to the clipboard. Auto-clears. */
    val recentlyCopied: Boolean = false,
) {
    /** Input with surrounding whitespace removed — the value that is actually translated. */
    val trimmedInput: String get() = inputText.trim()

    /** Live character count of the raw input (drives the counter under the field). */
    val characterCount: Int get() = inputText.length

    /** True once the input exceeds [MAX_INPUT_LENGTH]; blocks translation and flags the counter. */
    val isOverLimit: Boolean get() = characterCount > MAX_INPUT_LENGTH

    /** Translate is allowed only with real, in-limit content that isn't already translating. */
    val canTranslate: Boolean get() = trimmedInput.isNotEmpty() && !isOverLimit && !isTranslating

    companion object {
        /** Mock cap on a single translation request (a real engine would batch longer text). */
        const val MAX_INPUT_LENGTH = 200
    }
}
