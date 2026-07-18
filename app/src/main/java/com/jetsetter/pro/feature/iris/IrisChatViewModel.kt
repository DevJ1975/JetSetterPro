package com.jetsetter.pro.feature.iris

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.ai.ActionRouter
import com.jetsetter.pro.core.ai.AiMessage
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.prefs.UserPreferencesRepository
import com.jetsetter.pro.core.data.repository.ExpenseRepository
import com.jetsetter.pro.core.data.repository.IrisRepository
import com.jetsetter.pro.core.data.repository.TripRepository
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.core.voice.VoiceLoopController
import com.jetsetter.pro.core.voice.VoiceLoopState
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(val text: String, val fromUser: Boolean)

@HiltViewModel
class IrisChatViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val irisRepository: IrisRepository,
    private val moduleStateStore: ModuleStateStore,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val actionRouter: ActionRouter,
) : ViewModel() {

    /**
     * Single owner of ALL IRIS speech (R9) — created lazily so the TTS/recognizer engines only
     * spin up once the user opts into voice. `ttsEnabled` speaks replies without the loop
     * ([VoiceLoopController.speakOnly]); `handsFree` runs the full listen→think→speak loop.
     */
    private var voiceLoopOrNull: VoiceLoopController? = null

    private fun voiceLoop(): VoiceLoopController =
        voiceLoopOrNull ?: VoiceLoopController(appContext) { transcript ->
            // A dropped turn (blank / already thinking) must not leave the loop stuck THINKING.
            if (!sendInternal(transcript)) voiceLoopOrNull?.onReplyReady("")
        }.also { controller ->
            voiceLoopOrNull = controller
            observeVoiceLoop(controller)
        }

    private fun observeVoiceLoop(controller: VoiceLoopController) {
        viewModelScope.launch {
            controller.state.collect { voiceState ->
                _ui.update {
                    it.copy(
                        voiceState = voiceState,
                        // The loop ended on its own (errors exhausted) — reflect it in the toggle.
                        handsFree = if (voiceState == VoiceLoopState.IDLE) false else it.handsFree,
                    )
                }
            }
        }
        viewModelScope.launch {
            controller.partial.collect { partial ->
                _ui.update { it.copy(partialTranscript = partial) }
            }
        }
    }

    // Persist the transcript as JSON so the conversation survives app restarts (guide: ModuleStateStore).
    private val messagesAdapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter<List<ChatMessage>>(Types.newParameterizedType(List::class.java, ChatMessage::class.java))

    private val _ui = MutableStateFlow(IrisUiState(messages = listOf(GREETING)))
    val ui: StateFlow<IrisUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = moduleStateStore.read(KEY)
                ?.let { runCatching { messagesAdapter.fromJson(it) }.getOrNull() }
            if (!saved.isNullOrEmpty()) {
                _ui.update { it.copy(messages = saved) }
            }
            // Tailor the quick-prompt chips to the live itinerary + ledger.
            val trips = runCatching { tripRepository.observeTrips().first() }.getOrDefault(emptyList())
            val expenses = runCatching { expenseRepository.observeExpenses().first() }.getOrDefault(emptyList())
            _ui.update { it.copy(suggestions = buildSuggestions(trips, expenses)) }
            // A proactive alert (or another screen) may have parked a prompt on the router while
            // deep-linking here — consume it AFTER the transcript restore so it isn't clobbered.
            actionRouter.consumePendingIrisPrompt()?.let { send(it) }
        }
        // Mirror the persisted TTS opt-in into UI state (and pre-warm the async TTS engine so
        // the first spoken reply isn't dropped while it initializes).
        viewModelScope.launch {
            userPreferencesRepository.preferences.collect { prefs ->
                if (prefs.ttsEnabled) voiceLoop()
                _ui.update { it.copy(ttsEnabled = prefs.ttsEnabled) }
            }
        }
        // Mirror the router's single staged action (spec §1.4) into UI state for the card.
        viewModelScope.launch {
            actionRouter.pending.collect { pending ->
                _ui.update { state ->
                    val previous = state.pendingAction
                    state.copy(
                        pendingAction = pending?.let {
                            PendingActionUi(
                                id = it.id.toString(),
                                kind = it.kind,
                                summary = it.summary,
                                // Preserve the in-flight spinner if this is the same action.
                                isCommitting = previous != null &&
                                    previous.id == it.id.toString() &&
                                    previous.isCommitting,
                            )
                        },
                    )
                }
            }
        }
    }

    /** User tapped Confirm: run the staged commit and append its result line to the transcript. */
    fun confirmPendingAction() {
        if (_ui.value.pendingAction?.isCommitting == true) return
        viewModelScope.launch {
            _ui.update { it.copy(pendingAction = it.pendingAction?.copy(isCommitting = true)) }
            val result = actionRouter.confirm() ?: return@launch
            // Persisted exactly like streamed replies: appended, then the transcript saved.
            _ui.update { it.copy(messages = it.messages + ChatMessage(result, fromUser = false)) }
            runCatching { moduleStateStore.save(KEY, messagesAdapter.toJson(_ui.value.messages)) }
        }
    }

    /** User tapped Cancel: discard the staged action (the pending collector clears the card). */
    fun cancelPendingAction() {
        actionRouter.clear()
    }

    /** Toggle speak-aloud (TTS); persisted in UserPreferences. */
    fun setTtsEnabled(enabled: Boolean) {
        if (!enabled) voiceLoopOrNull?.stopSpeaking()
        viewModelScope.launch { userPreferencesRepository.setTtsEnabled(enabled) }
    }

    /**
     * Toggle the hands-free voice loop (spec §1.8). The UI is responsible for RECORD_AUDIO
     * (required) and BLUETOOTH_CONNECT (optional, API 31+) before enabling; the controller
     * degrades to speaker routing when Bluetooth isn't permitted.
     */
    fun setHandsFree(enabled: Boolean) {
        if (enabled) {
            val controller = voiceLoop()
            controller.start()
            // start() flips the loop to LISTENING; only then mark the toggle on (an immediate
            // IDLE would clear it right back — see observeVoiceLoop).
            _ui.update { it.copy(handsFree = controller.state.value != VoiceLoopState.IDLE) }
        } else {
            voiceLoopOrNull?.stop()
            _ui.update { it.copy(handsFree = false) }
        }
    }

    override fun onCleared() {
        voiceLoopOrNull?.release()
        voiceLoopOrNull = null
        super.onCleared()
    }

    /** Context-aware quick prompts: reference the nearest trip and the ledger, else the defaults. */
    private fun buildSuggestions(trips: List<Trip>, expenses: List<Expense>): List<String> {
        val chips = mutableListOf<String>()
        trips.minByOrNull { it.startDate }?.let { trip ->
            chips += "Help me pack for ${trip.destination}"
            if (trip.packingList.any { !it.isPacked }) chips += "What's left to pack?"
        }
        if (expenses.isNotEmpty()) chips += "Summarize my spend"
        chips += "What's my gate?"
        return chips.distinct().take(4).ifEmpty { DEFAULT_SUGGESTIONS }
    }

    fun send(text: String) {
        sendInternal(text)
    }

    /** Shared send path; returns false when the turn was dropped (blank / already thinking). */
    private fun sendInternal(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _ui.value.isThinking) return false

        val history = _ui.value.messages + ChatMessage(trimmed, fromUser = true)
        _ui.update { it.copy(messages = history, isThinking = true) }

        // A new turn interrupts any reply still being spoken aloud.
        voiceLoopOrNull?.takeIf { !_ui.value.handsFree }?.stopSpeaking()

        viewModelScope.launch {
            val aiHistory = history.map { AiMessage(if (it.fromUser) "user" else "model", it.text) }

            // Append an empty assistant bubble, then stream tokens into it as they arrive.
            _ui.update { it.copy(messages = it.messages + ChatMessage("", fromUser = false)) }
            val reply = StringBuilder()
            irisRepository.stream(aiHistory)
                .catch { /* keep whatever streamed; the bubble simply stops growing */ }
                .collect { delta ->
                    reply.append(delta)
                    _ui.update { state ->
                        val messages = state.messages.toMutableList()
                        messages[messages.lastIndex] = ChatMessage(reply.toString(), fromUser = false)
                        state.copy(messages = messages)
                    }
                }

            _ui.update { it.copy(isThinking = false) }
            runCatching { moduleStateStore.save(KEY, messagesAdapter.toJson(_ui.value.messages)) }

            // All speech flows through the one controller (R9): hands-free advances the loop
            // (speak, then auto-resume the mic); plain TTS just reads the reply aloud.
            val replyText = reply.toString()
            val state = _ui.value
            when {
                state.handsFree -> voiceLoop().onReplyReady(replyText)
                state.ttsEnabled && replyText.isNotBlank() -> voiceLoop().speakOnly(replyText)
            }
        }
        return true
    }

    private companion object {
        const val KEY = "iris_chat_history"
        val GREETING = ChatMessage(
            "Hi, I'm IRIS — your travel concierge. Ask me about your flights, itinerary, packing, or expenses.",
            fromUser = false,
        )
    }
}
