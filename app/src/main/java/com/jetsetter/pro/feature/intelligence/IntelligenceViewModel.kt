package com.jetsetter.pro.feature.intelligence

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
class IntelligenceViewModel @Inject constructor(
    private val repository: IntelligenceRepository,
    private val stateStore: ModuleStateStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(IntelligenceUiState(isLoading = true))
    val ui: StateFlow<IntelligenceUiState> = _ui.asStateFlow()

    // Tester actions are persisted as JSON List<String> id sets so they survive app restart.
    private val idsAdapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))

    init {
        viewModelScope.launch {
            // Restore persisted choices before the first emission. First run → null → keep the
            // repository's seed data (incl. its pre-dismissed history) as the default.
            readIds(KEY_DISMISSED)?.let { repository.applyDismissed(it.toSet()) }
            readIds(KEY_ACTED)?.let { acted -> _ui.update { it.copy(acted = acted.toSet()) } }

            repository.observeInsights().collect { items ->
                _ui.update {
                    it.copy(
                        // Highest-severity-first so the most urgent insights sit at the top.
                        active = items.filterNot { item -> item.dismissed }
                            .sortedByDescending { item -> item.severity.weight },
                        dismissed = items.filter { item -> item.dismissed },
                        isLoading = false,
                    )
                }
            }
        }
    }

    /** Action chip tapped — mark the insight as acted (confirmed) without hiding it. */
    fun act(item: TravelIntelligenceItem) {
        _ui.update { it.copy(acted = it.acted + item.id) }
        viewModelScope.launch { saveIds(KEY_ACTED, _ui.value.acted) }
    }

    /** Select a severity to filter the active list by, or null to show all. Transient view state. */
    fun setSeverityFilter(severity: IntelligenceSeverity?) {
        _ui.update { it.copy(severityFilter = if (it.severityFilter == severity) null else severity) }
    }

    /** Clear an insight into the dismissed-history bucket. */
    fun dismiss(item: TravelIntelligenceItem) {
        repository.setDismissed(item.id, dismissed = true)
        persistDismissed()
    }

    /** Bring a dismissed insight back into the active list. */
    fun restore(item: TravelIntelligenceItem) {
        repository.setDismissed(item.id, dismissed = false)
        persistDismissed()
    }

    private fun persistDismissed() {
        viewModelScope.launch { saveIds(KEY_DISMISSED, repository.dismissedIds()) }
    }

    private suspend fun readIds(key: String): List<String>? =
        stateStore.read(key)?.let { runCatching { idsAdapter.fromJson(it) }.getOrNull() }

    private suspend fun saveIds(key: String, ids: Collection<String>) {
        stateStore.save(key, idsAdapter.toJson(ids.toList()))
    }

    private companion object {
        const val KEY_DISMISSED = "intelligence_dismissed"
        const val KEY_ACTED = "intelligence_acted"
    }
}
