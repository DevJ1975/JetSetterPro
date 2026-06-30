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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntelligenceViewModel @Inject constructor(
    private val repository: IntelligenceRepository,
    private val stateStore: ModuleStateStore,
) : ViewModel() {

    // Tester actions persist as JSON List<String> id sets so they survive app restart. The derived
    // insight ids are stable, so a dismissed/acted id keeps matching across re-derivations.
    private val idsAdapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))

    // Transient view preference — not persisted; kept out of the reactive pipeline so it survives
    // each data-driven re-emission.
    private val severityFilter = MutableStateFlow<IntelligenceSeverity?>(null)

    private val _ui = MutableStateFlow(IntelligenceUiState(isLoading = true))
    val ui: StateFlow<IntelligenceUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeInsights(),
                stateStore.observe(KEY_DISMISSED),
                stateStore.observe(KEY_ACTED),
                severityFilter,
            ) { derived, dismissedJson, actedJson, filter ->
                val dismissedIds = parseIds(dismissedJson)
                val actedIds = parseIds(actedJson).toSet()
                IntelligenceUiState(
                    // Highest-severity-first so the most urgent insights sit at the top.
                    active = derived.filterNot { it.id in dismissedIds }
                        .sortedByDescending { it.severity.weight },
                    dismissed = derived.filter { it.id in dismissedIds },
                    acted = actedIds,
                    severityFilter = filter,
                    isLoading = false,
                )
            }.collect { state -> _ui.value = state }
        }
    }

    /** Action chip tapped — mark the insight as acted (confirmed) without hiding it. */
    fun act(item: TravelIntelligenceItem) {
        viewModelScope.launch { addId(KEY_ACTED, item.id) }
    }

    /** Select a severity to filter the active list by, or null to show all. Transient view state. */
    fun setSeverityFilter(severity: IntelligenceSeverity?) {
        severityFilter.update { if (it == severity) null else severity }
    }

    /** Clear an insight into the dismissed-history bucket. */
    fun dismiss(item: TravelIntelligenceItem) {
        viewModelScope.launch { addId(KEY_DISMISSED, item.id) }
    }

    /** Bring a dismissed insight back into the active list. */
    fun restore(item: TravelIntelligenceItem) {
        viewModelScope.launch { removeId(KEY_DISMISSED, item.id) }
    }

    private suspend fun addId(key: String, id: String) {
        val updated = readIds(key).toMutableSet().apply { add(id) }
        stateStore.save(key, idsAdapter.toJson(updated.toList()))
    }

    private suspend fun removeId(key: String, id: String) {
        val updated = readIds(key).toMutableSet().apply { remove(id) }
        stateStore.save(key, idsAdapter.toJson(updated.toList()))
    }

    private suspend fun readIds(key: String): List<String> = parseIds(stateStore.read(key))

    private fun parseIds(json: String?): List<String> =
        json?.let { runCatching { idsAdapter.fromJson(it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY_DISMISSED = "intelligence_dismissed"
        const val KEY_ACTED = "intelligence_acted"
    }
}
