package com.jetsetter.pro.feature.translator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TranslatorViewModel @Inject constructor(
    private val repository: TranslatorRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(TranslatorUiState())
    val ui: StateFlow<TranslatorUiState> = _ui.asStateFlow()

    init {
        // Load the phrasebook and the persisted recent translations on first frame so the
        // "Recent" section renders in its saved state without flashing empty.
        viewModelScope.launch {
            val phrases = repository.phrases()
            val recents = repository.loadRecents()
            _ui.update { it.copy(phrases = phrases, recents = recents) }
        }
    }

    fun onInputChange(text: String) {
        _ui.update { it.copy(inputText = text) }
    }

    fun clearInput() {
        _ui.update { it.copy(inputText = "", result = null, recentlyCopied = false) }
    }

    fun swapLanguages() {
        _ui.update {
            it.copy(
                fromLanguage = it.toLanguage,
                toLanguage = it.fromLanguage,
                result = null,
                recentlyCopied = false,
            )
        }
    }

    fun translate() {
        val current = _ui.value
        if (!current.canTranslate) return
        val text = current.trimmedInput
        _ui.update { it.copy(isTranslating = true, recentlyCopied = false) }
        viewModelScope.launch {
            val result = repository.translate(text, current.fromLanguage, current.toLanguage)
            val recents = prependRecent(
                TranslatorRecentEntry(
                    sourceText = result.sourceText,
                    translatedText = result.translatedText,
                    pronunciation = result.pronunciation,
                    fromCode = current.fromLanguage.code,
                    toCode = current.toLanguage.code,
                    matched = result.matched,
                ),
            )
            _ui.update { it.copy(isTranslating = false, result = result, recents = recents) }
            persistRecents(recents)
        }
    }

    /** Tapping a saved phrase fills the box, shows it as the active translation, and logs it. */
    fun usePhrase(phrase: TranslatorPhrase) {
        val result = TranslatorResult(
            sourceText = phrase.source,
            translatedText = phrase.target,
            pronunciation = phrase.pronunciation,
            note = "From your ${phrase.category} phrasebook.",
            matched = true,
        )
        val recents = prependRecent(
            TranslatorRecentEntry(
                sourceText = phrase.source,
                translatedText = phrase.target,
                pronunciation = phrase.pronunciation,
                fromCode = TranslatorLanguage.English.code,
                toCode = TranslatorLanguage.Japanese.code,
                matched = true,
            ),
        )
        _ui.update {
            it.copy(
                inputText = phrase.source,
                fromLanguage = TranslatorLanguage.English,
                toLanguage = TranslatorLanguage.Japanese,
                result = result,
                recents = recents,
                recentlyCopied = false,
            )
        }
        persistRecents(recents)
    }

    /** Re-opens a previous translation: restores its direction, input and result instantly. */
    fun useRecent(entry: TranslatorRecentEntry) {
        _ui.update {
            it.copy(
                inputText = entry.sourceText,
                fromLanguage = TranslatorLanguage.fromCode(entry.fromCode),
                toLanguage = TranslatorLanguage.fromCode(entry.toCode),
                result = TranslatorResult(
                    sourceText = entry.sourceText,
                    translatedText = entry.translatedText,
                    pronunciation = entry.pronunciation,
                    note = if (entry.matched) {
                        "From your recent translations."
                    } else {
                        "Recent offline demo result."
                    },
                    matched = entry.matched,
                ),
                recentlyCopied = false,
            )
        }
    }

    /** Empties the recent list and clears it from disk. */
    fun clearRecents() {
        _ui.update { it.copy(recents = emptyList()) }
        persistRecents(emptyList())
    }

    /**
     * Marks the active result as just-copied so the UI can flash a confirmation; the actual
     * clipboard write happens in the composable (Android UI concern). Auto-clears after a beat.
     */
    fun markResultCopied() {
        if (_ui.value.result == null) return
        _ui.update { it.copy(recentlyCopied = true) }
        viewModelScope.launch {
            delay(COPIED_FLASH_MS)
            _ui.update { it.copy(recentlyCopied = false) }
        }
    }

    /** Prepends [entry], de-duping by identical text + direction, capped at [MAX_RECENTS]. */
    private fun prependRecent(entry: TranslatorRecentEntry): List<TranslatorRecentEntry> {
        val deduped = _ui.value.recents.filterNot {
            it.sourceText.equals(entry.sourceText, ignoreCase = true) &&
                it.fromCode == entry.fromCode &&
                it.toCode == entry.toCode
        }
        return (listOf(entry) + deduped).take(MAX_RECENTS)
    }

    private fun persistRecents(recents: List<TranslatorRecentEntry>) {
        viewModelScope.launch { repository.saveRecents(recents) }
    }

    private companion object {
        const val MAX_RECENTS = 8
        const val COPIED_FLASH_MS = 1600L
    }
}
