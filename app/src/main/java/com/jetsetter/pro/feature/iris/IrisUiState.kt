package com.jetsetter.pro.feature.iris

import com.jetsetter.pro.core.ai.IrisPendingAction
import com.jetsetter.pro.core.voice.VoiceLoopState

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
    /** The staged IRIS action awaiting Cancel/Confirm (spec §1.4), or `null` when none. */
    val pendingAction: PendingActionUi? = null,
    /** Hands-free conversation loop active (spec §1.8): listen → think → speak → listen. */
    val handsFree: Boolean = false,
    /** Current phase of the voice loop — drives the Listening/Thinking/Speaking chip. */
    val voiceState: VoiceLoopState = VoiceLoopState.IDLE,
    /** Live partial hypothesis while LISTENING, ghosted into the input field; else null. */
    val partialTranscript: String? = null,
)

/**
 * UI projection of the staged [IrisPendingAction] — just what the confirmation card renders
 * (no commit lambda; committing goes through the ActionRouter via the ViewModel).
 */
data class PendingActionUi(
    val id: String,
    val kind: IrisPendingAction.Kind,
    val summary: String,
    /** True while the confirmed commit is running; the card swaps its buttons for a spinner. */
    val isCommitting: Boolean = false,
)

/** Quick-tap prompts surfaced above the input bar; tapping one sends it straight to IRIS. */
val DEFAULT_SUGGESTIONS: List<String> = listOf(
    "When should I leave?",
    "What's my gate?",
    "Help me pack",
    "Any delays?",
    "Expense summary",
)
