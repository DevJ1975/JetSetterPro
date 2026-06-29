package com.jetsetter.pro.feature.iris

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.ai.AiMessage
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.data.repository.IrisRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(val text: String, val fromUser: Boolean)

@HiltViewModel
class IrisChatViewModel @Inject constructor(
    private val irisRepository: IrisRepository,
    private val moduleStateStore: ModuleStateStore,
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
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _ui.value.isThinking) return

        val history = _ui.value.messages + ChatMessage(trimmed, fromUser = true)
        _ui.update { it.copy(messages = history, isThinking = true) }

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

    private companion object {
        const val KEY = "iris_chat_history"
        val GREETING = ChatMessage(
            "Hi, I'm IRIS — your travel concierge. Ask me about your flights, itinerary, packing, or expenses.",
            fromUser = false,
        )
    }
}
