package com.jetsetter.pro.feature.iris

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.ai.AiMessage
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.prefs.UserPreferencesRepository
import com.jetsetter.pro.core.data.repository.ExpenseRepository
import com.jetsetter.pro.core.data.repository.IrisRepository
import com.jetsetter.pro.core.data.repository.TripRepository
import com.jetsetter.pro.core.intelligence.UserMemory
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.Trip
import java.time.LocalTime
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val irisRepository: IrisRepository,
    private val moduleStateStore: ModuleStateStore,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
    private val userMemory: UserMemory,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

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
        }
        // Mirror the persisted TTS opt-in into UI state.
        viewModelScope.launch {
            userPreferencesRepository.preferences.collect { prefs ->
                _ui.update { it.copy(ttsEnabled = prefs.ttsEnabled) }
            }
        }
    }

    /** Toggle speak-aloud (TTS); persisted in UserPreferences. */
    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setTtsEnabled(enabled) }
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
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _ui.value.isThinking) return

        val history = _ui.value.messages + ChatMessage(trimmed, fromUser = true)
        _ui.update { it.copy(messages = history, isThinking = true) }

        // Learn the user, on-device only (PERSONAL — never sent to Claude).
        viewModelScope.launch {
            userMemory.recordTopic(classifyTopic(trimmed))
            userMemory.recordUsage(LocalTime.now().hour)
        }

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
        }
    }

    /** Deterministic on-device topic bucket for a user message (no model, no network). */
    private fun classifyTopic(text: String): String {
        val t = text.lowercase()
        return when {
            "pack" in t -> "packing"
            "delay" in t || "gate" in t || "boarding" in t -> "flight-status"
            "expense" in t || "spend" in t || "budget" in t || "receipt" in t -> "expenses"
            "visa" in t || "passport" in t || "esta" in t || "etias" in t || "entry" in t -> "visa-entry"
            "hotel" in t || "lodging" in t || "stay" in t -> "lodging"
            "restaurant" in t || "dinner" in t || "eat" in t || "food" in t -> "dining"
            "lounge" in t || "loyalty" in t || "miles" in t || "status" in t -> "loyalty"
            "weather" in t || "forecast" in t -> "weather"
            else -> "general"
        }
    }

    private companion object {
        const val KEY = "iris_chat_history"
        val GREETING = ChatMessage(
            "Hi, I'm IRIS — your travel concierge. Ask me about your flights, itinerary, packing, or expenses.",
            fromUser = false,
        )
    }
}
