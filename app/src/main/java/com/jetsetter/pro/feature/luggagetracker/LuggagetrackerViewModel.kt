package com.jetsetter.pro.feature.luggagetracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Persisted slice of this module's state. The last-viewed bag and any nicknames the tester
 * types are real preferences, so we keep them across restarts via [ModuleStateStore] using
 * the same Moshi idiom Room uses in `core/data/local/Converters.kt`.
 *
 * [nicknames] maps a bag's tag id to its user-set name.
 */
data class LuggageTrackerPrefs(
    val selectedTagId: String? = null,
    val nicknames: Map<String, String> = emptyMap(),
)

@HiltViewModel
class LuggagetrackerViewModel @Inject constructor(
    private val repository: LuggagetrackerRepository,
    private val stateStore: ModuleStateStore,
) : ViewModel() {

    private val prefsAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(LuggageTrackerPrefs::class.java)

    private val _ui = MutableStateFlow(LuggagetrackerUiState(isLoading = true))
    val ui: StateFlow<LuggagetrackerUiState> = _ui.asStateFlow()

    init {
        // Seed the registered bags, anchor scan times to "now", and restore the tester's last
        // selection + nicknames before they interact — so the screen never flashes empty and
        // returns them to where they left off.
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val prefs = runCatching {
                stateStore.read(KEY)?.let { prefsAdapter.fromJson(it) }
            }.getOrNull() ?: LuggageTrackerPrefs()

            val bags = repository.registeredBags(now).map { bag ->
                val nick = prefs.nicknames[bag.tagId]?.takeIf { it.isNotBlank() }
                if (nick == null) bag else bag.copy(nickname = nick)
            }

            _ui.update {
                it.copy(
                    bags = bags,
                    nowMillis = now,
                    selectedTagId = prefs.selectedTagId?.takeIf { id -> bags.any { b -> b.tagId == id } }
                        ?: bags.firstOrNull()?.tagId,
                    isLoading = false,
                )
            }
        }
    }

    /** Switch the bag whose scan history is shown, and remember it across restarts. */
    fun onSelectBag(bag: LuggageTrackerBag) {
        _ui.update { it.copy(selectedTagId = bag.tagId, errorMessage = null) }
        persist()
    }

    /** Live-filter the bag list as the tester types. */
    fun onQueryChange(query: String) {
        _ui.update { it.copy(query = query) }
    }

    /** Clear the search field. */
    fun onClearQuery() {
        _ui.update { it.copy(query = "") }
    }

    /** Open the rename sheet for [bag]. */
    fun onStartRename(bag: LuggageTrackerBag) {
        _ui.update { it.copy(renamingTagId = bag.tagId) }
    }

    /** Dismiss the rename sheet without saving. */
    fun onDismissRename() {
        _ui.update { it.copy(renamingTagId = null) }
    }

    /**
     * Save (or, when blank, clear) a bag's nickname and persist it. Closes the sheet. The
     * trimmed value is applied so a name of only whitespace is treated as "no nickname".
     */
    fun onSaveNickname(tagId: String, nickname: String) {
        val trimmed = nickname.trim().take(MAX_NICKNAME_LENGTH)
        _ui.update { state ->
            state.copy(
                bags = state.bags.map { bag ->
                    if (bag.tagId == tagId) bag.copy(nickname = trimmed.ifEmpty { null }) else bag
                },
                renamingTagId = null,
            )
        }
        persist()
    }

    /** Serialize the current selection + nicknames to the per-module store. */
    private fun persist() {
        val state = _ui.value
        val nicknames = state.bags
            .mapNotNull { bag -> bag.nickname?.takeIf { it.isNotBlank() }?.let { bag.tagId to it } }
            .toMap()
        val prefs = LuggageTrackerPrefs(selectedTagId = state.selectedTagId, nicknames = nicknames)
        viewModelScope.launch {
            runCatching { stateStore.save(KEY, prefsAdapter.toJson(prefs)) }
        }
    }

    companion object {
        /** Keep nicknames short enough to fit a card title cleanly. */
        const val MAX_NICKNAME_LENGTH = 24
        private const val KEY = "luggage_tracker_prefs"
    }
}
