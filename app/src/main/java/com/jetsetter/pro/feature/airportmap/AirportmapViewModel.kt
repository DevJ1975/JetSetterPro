package com.jetsetter.pro.feature.airportmap

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

@HiltViewModel
class AirportmapViewModel @Inject constructor(
    private val repository: AirportmapRepository,
    private val stateStore: ModuleStateStore,
) : ViewModel() {

    // The view controls (origin / category filter / sort) are persisted as a small JSON object so
    // a tester's view survives app restarts; the search query and expand state stay transient.
    // Moshi serialises the enums by name — the same idiom Converters.kt uses for nested lists.
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val prefsAdapter = moshi.adapter(AirportGuidePrefs::class.java)

    private val _ui = MutableStateFlow(
        AirportmapUiState(
            airportName = repository.airportName,
            airportCode = repository.airportCode,
            // Lounges open by default so the user lands on an expanded section to interact with.
            expandedCategories = setOf(AirportAmenityCategory.LOUNGES),
            isLoading = true,
        ),
    )
    val ui: StateFlow<AirportmapUiState> = _ui.asStateFlow()

    init {
        // Load the mock amenities once, overlay the saved view controls, then drop the loading flag.
        viewModelScope.launch {
            val prefs = readPrefs()
            val items = repository.getAmenities()
            _ui.update {
                it.copy(
                    allItems = items,
                    origin = prefs.origin,
                    categoryFilter = prefs.categoryFilter,
                    sort = prefs.sort,
                    // Keep a persisted single-category filter's section expanded on restart.
                    expandedCategories = prefs.categoryFilter
                        ?.let { c -> it.expandedCategories + c } ?: it.expandedCategories,
                    isLoading = false,
                )
            }
        }
    }

    /** Set the traveller's current concourse; every walk time recomputes from here. */
    fun selectOrigin(concourse: Concourse) {
        if (_ui.value.origin == concourse) return
        _ui.update { it.copy(origin = concourse) }
        persist()
    }

    /** Live search over amenity name / location / note / concourse. Not persisted. */
    fun updateQuery(query: String) {
        _ui.update { it.copy(query = query) }
    }

    /**
     * Apply a category filter. Passing the already-selected category (or `null`) clears it back to
     * "All"; selecting a category also expands its section so its rows are immediately visible.
     */
    fun selectCategoryFilter(category: AirportAmenityCategory?) {
        _ui.update { state ->
            val next = if (category != null && state.categoryFilter == category) null else category
            state.copy(
                categoryFilter = next,
                expandedCategories = next?.let { state.expandedCategories + it } ?: state.expandedCategories,
            )
        }
        persist()
    }

    /** Change the row ordering (nearest first, or grouped by concourse). */
    fun selectSort(sort: AmenitySort) {
        if (_ui.value.sort == sort) return
        _ui.update { it.copy(sort = sort) }
        persist()
    }

    /** Expand or collapse a category section. */
    fun toggleCategory(category: AirportAmenityCategory) {
        _ui.update { state ->
            val expanded = if (category in state.expandedCategories) {
                state.expandedCategories - category
            } else {
                state.expandedCategories + category
            }
            state.copy(expandedCategories = expanded)
        }
    }

    /** Expand every section that currently has amenities. */
    fun expandAll() {
        _ui.update { state ->
            state.copy(expandedCategories = state.allItems.map { it.category }.toSet())
        }
    }

    /** Collapse every section at once. */
    fun collapseAll() {
        _ui.update { it.copy(expandedCategories = emptySet()) }
    }

    /** Clear the category filter and search query (origin and sort are left as-is). */
    fun clearFilters() {
        _ui.update { it.copy(categoryFilter = null, query = "") }
        persist()
    }

    private fun persist() {
        val s = _ui.value
        val prefs = AirportGuidePrefs(origin = s.origin, categoryFilter = s.categoryFilter, sort = s.sort)
        viewModelScope.launch { stateStore.save(PREFS_KEY, prefsAdapter.toJson(prefs)) }
    }

    /** Read the persisted view controls; falls back to defaults when absent or unreadable. */
    private suspend fun readPrefs(): AirportGuidePrefs {
        val json = stateStore.read(PREFS_KEY) ?: return AirportGuidePrefs()
        return runCatching { prefsAdapter.fromJson(json) }.getOrNull() ?: AirportGuidePrefs()
    }

    private companion object {
        const val PREFS_KEY = "airportmap_prefs"
    }
}

/** Persisted slice of [AirportmapUiState] — the traveller's view controls, serialised via Moshi. */
internal data class AirportGuidePrefs(
    val origin: Concourse = Concourse.T,
    val categoryFilter: AirportAmenityCategory? = null,
    val sort: AmenitySort = AmenitySort.NEAREST,
)
