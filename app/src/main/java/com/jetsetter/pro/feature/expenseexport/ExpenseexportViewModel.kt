package com.jetsetter.pro.feature.expenseexport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExpenseexportViewModel @Inject constructor(
    private val repository: ExpenseexportRepository,
) : ViewModel() {

    // Seed synchronously with the mock data so the populated UI shows immediately (no empty
    // flash), then overlay any persisted tester choices once DataStore resolves.
    private val _ui = MutableStateFlow(
        ExpenseexportUiState(
            items = repository.expenses(),
            connections = repository.connections(),
            isLoading = true,
        ),
    )
    val ui: StateFlow<ExpenseexportUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = repository.loadPersisted()
            _ui.update { state ->
                if (saved == null) {
                    state.copy(isLoading = false)
                } else {
                    val excluded = saved.excludedItemIds.toSet()
                    val connected = saved.connectedIds.toSet()
                    state.copy(
                        isLoading = false,
                        selectedFormat = runCatching { ExpenseExportFormat.valueOf(saved.selectedFormat) }
                            .getOrDefault(state.selectedFormat),
                        items = state.items.map { it.copy(included = it.id !in excluded) },
                        connections = state.connections.map { it.copy(isConnected = it.id in connected) },
                    )
                }
            }
        }
    }

    fun selectFormat(format: ExpenseExportFormat) {
        if (_ui.value.selectedFormat == format) return
        // Changing the output format invalidates a prior result snapshot.
        _ui.update { it.copy(selectedFormat = format, result = null) }
        persist()
    }

    /** Includes/excludes a single expense row, keeping the total + count honest. */
    fun toggleItem(id: String) {
        _ui.update { state ->
            state.copy(
                items = state.items.map {
                    if (it.id == id) it.copy(included = !it.included) else it
                },
                result = null,
            )
        }
        persist()
    }

    /** Select-all when something is excluded; otherwise clear the whole selection. */
    fun toggleSelectAll() {
        _ui.update { state ->
            val target = !state.allSelected
            state.copy(items = state.items.map { it.copy(included = target) }, result = null)
        }
        persist()
    }

    fun toggleConnection(id: String) {
        _ui.update { state ->
            state.copy(
                connections = state.connections.map { connection ->
                    if (connection.id == id) connection.copy(isConnected = !connection.isConnected) else connection
                },
                result = null,
            )
        }
        persist()
    }

    fun export() {
        val current = _ui.value
        if (!current.canExport) return
        viewModelScope.launch {
            _ui.update { it.copy(isExporting = true, result = null) }
            // Simulate the work a real export would do (render file, push to platform).
            delay(900)
            _ui.update { state ->
                state.copy(isExporting = false, result = buildResult(state))
            }
        }
    }

    fun dismissExportResult() {
        _ui.update { it.copy(result = null) }
    }

    /** Captures a self-consistent snapshot of the export at completion time. */
    private fun buildResult(state: ExpenseexportUiState): ExpenseExportResult {
        val bytes = estimatedExportBytes(state.itemCount, state.selectedFormat)
        return ExpenseExportResult(
            fileName = "expenses${state.selectedFormat.extension}",
            itemCount = state.itemCount,
            total = state.total,
            sizeLabel = formatBytes(bytes),
            syncedTo = state.connectedNames,
        )
    }

    private fun persist() {
        val state = _ui.value
        viewModelScope.launch {
            repository.persist(
                ExpenseexportPersistedState(
                    selectedFormat = state.selectedFormat.name,
                    connectedIds = state.connections.filter { it.isConnected }.map { it.id },
                    excludedItemIds = state.items.filterNot { it.included }.map { it.id },
                ),
            )
        }
    }
}
