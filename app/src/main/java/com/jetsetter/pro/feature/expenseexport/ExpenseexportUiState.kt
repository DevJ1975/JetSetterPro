package com.jetsetter.pro.feature.expenseexport

/**
 * Single immutable UI-state object for the Expense Export screen — one object rather than
 * scattered flags, so impossible states can't arise and previews are trivial.
 *
 * Every headline number ([itemCount], [total], file-size estimates) is computed from
 * [includedItems] so the summary, the format chips, and the export button can never disagree
 * with what the user has actually selected.
 */
data class ExpenseexportUiState(
    val items: List<ExpenseExportItem> = emptyList(),
    val connections: List<ExpenseExportConnection> = emptyList(),
    val selectedFormat: ExpenseExportFormat = ExpenseExportFormat.PDF,
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val result: ExpenseExportResult? = null,
) {
    /** The rows that will actually be written. */
    val includedItems: List<ExpenseExportItem> get() = items.filter { it.included }

    val itemCount: Int get() = includedItems.size
    val totalCount: Int get() = items.size
    val total: Double get() = includedItems.sumOf { it.amount }

    val connectedCount: Int get() = connections.count { it.isConnected }
    val connectedNames: List<String> get() = connections.filter { it.isConnected }.map { it.name }

    val allSelected: Boolean get() = items.isNotEmpty() && items.all { it.included }
    val noneSelected: Boolean get() = items.isEmpty() || includedItems.isEmpty()

    /** Export is allowed only when at least one row is selected and we're not mid-export. */
    val canExport: Boolean get() = !isExporting && itemCount > 0
}
