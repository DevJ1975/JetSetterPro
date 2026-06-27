package com.jetsetter.pro.feature.traveljournal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TraveljournalViewModel @Inject constructor(
    private val repository: TraveljournalRepository,
    private val stateStore: ModuleStateStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(TraveljournalUiState(isLoading = true))
    val ui: StateFlow<TraveljournalUiState> = _ui.asStateFlow()

    // Moshi adapter for the full List<TripJournalEntry>, matching the Converters.kt reference.
    private val entriesAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<TripJournalEntry>>(
            Types.newParameterizedType(List::class.java, TripJournalEntry::class.java),
        )

    init {
        // Prefer the persisted journal so tester-added entries survive a restart; otherwise
        // fall back to the mock repository seed. Then drop the loading flag.
        viewModelScope.launch {
            val saved = stateStore.read(KEY_ENTRIES)?.let { json ->
                runCatching { entriesAdapter.fromJson(json) }.getOrNull()
            }
            val entries = saved ?: repository.loadEntries()
            _ui.update { it.copy(entries = entries, isLoading = false) }
        }
    }

    fun onDraftTitleChange(value: String) {
        _ui.update { it.copy(draftTitle = value) }
    }

    fun onDraftBodyChange(value: String) {
        _ui.update { it.copy(draftBody = value) }
    }

    fun onDraftLocationChange(value: String) {
        _ui.update { it.copy(draftLocation = value) }
    }

    fun onDraftMoodChange(mood: TripJournalMood) {
        _ui.update { it.copy(draftMood = mood) }
    }

    /** Switches the entries-list mood filter; null restores the "All" view. */
    fun onMoodFilterChange(mood: TripJournalMood?) {
        _ui.update { it.copy(moodFilter = mood) }
    }

    /**
     * Prepends a new entry built from the composer draft, then clears the draft fields. Guarded by
     * [TraveljournalUiState.canAddEntry] (title required), so empty memories never get created.
     */
    fun addEntry() {
        val state = _ui.value
        if (!state.canAddEntry) return
        val entry = repository.createEntry(
            title = state.draftTitle.trim(),
            body = state.draftBody.trim(),
            location = state.draftLocation.trim(),
            mood = state.draftMood,
        )
        _ui.update {
            it.copy(
                entries = listOf(entry) + it.entries,
                draftTitle = "",
                draftBody = "",
                draftLocation = "",
                draftMood = TripJournalMood.JOYFUL,
            )
        }
        // Persist the full current entries list so it survives app restart.
        persistEntries(_ui.value.entries)
    }

    /** Removes an entry and persists the result; clears the mood filter if it's now empty. */
    fun deleteEntry(id: String) {
        _ui.update { state ->
            val remaining = state.entries.filterNot { it.id == id }
            val filter = state.moodFilter?.takeIf { mood -> remaining.any { it.mood == mood } }
            state.copy(entries = remaining, moodFilter = filter)
        }
        persistEntries(_ui.value.entries)
    }

    private fun persistEntries(entries: List<TripJournalEntry>) {
        viewModelScope.launch {
            stateStore.save(KEY_ENTRIES, entriesAdapter.toJson(entries))
        }
    }

    private companion object {
        const val KEY_ENTRIES = "traveljournal_entries"
    }
}
