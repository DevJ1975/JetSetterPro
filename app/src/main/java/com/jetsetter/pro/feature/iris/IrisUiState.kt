package com.jetsetter.pro.feature.iris

/**
 * Single immutable UI-state object for the IRIS chat screen — one object rather than scattered
 * flags, so impossible states can't arise and previews are trivial (guide §6).
 */
data class IrisUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isThinking: Boolean = false,
    val suggestions: List<String> = DEFAULT_SUGGESTIONS,
    /** Opt-in: speak IRIS replies aloud (TTS), persisted in UserPreferences. */
    val ttsEnabled: Boolean = false,
)

/** Quick-tap prompts surfaced above the input bar; tapping one sends it straight to IRIS. */
val DEFAULT_SUGGESTIONS: List<String> = listOf(
    "What's my gate?",
    "Help me pack",
    "Any delays?",
    "Expense summary",
)
