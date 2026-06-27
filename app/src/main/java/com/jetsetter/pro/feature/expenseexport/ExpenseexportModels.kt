package com.jetsetter.pro.feature.expenseexport

import java.util.Locale

/**
 * Domain models for the Expense Export feature. Names are module-prefixed
 * (ExpenseExport*) so they never collide with models in other feature modules.
 */

/**
 * A single billable line item eligible for export. [included] controls whether the row
 * counts toward the export total/count — toggled per row in the UI, so every number on
 * screen is derived from this set rather than a static figure.
 */
data class ExpenseExportItem(
    val id: String,
    val merchant: String,
    val category: String,
    val date: String,
    val amount: Double,
    val included: Boolean = true,
)

/** Output file formats the current expense set can be exported to. */
enum class ExpenseExportFormat(val label: String, val extension: String) {
    PDF("PDF", ".pdf"),
    CSV("CSV", ".csv"),
}

/** An accounting / card platform the expenses can be pushed into. */
data class ExpenseExportConnection(
    val id: String,
    val name: String,
    val description: String,
    val isConnected: Boolean = false,
)

/**
 * Snapshot of a completed export, captured at the moment "Export" finished so the success
 * card stays consistent even if the user later re-edits the selection. Every value is
 * derived from the inputs (selected rows + format + connected platforms).
 */
data class ExpenseExportResult(
    val fileName: String,
    val itemCount: Int,
    val total: Double,
    val sizeLabel: String,
    val syncedTo: List<String>,
)

/**
 * Persisted, user-mutable state for the feature. Serialized to JSON via Moshi and stored in
 * [com.jetsetter.pro.core.data.prefs.ModuleStateStore] so tester choices survive app restarts.
 * Plain reflection-friendly types only (matches the KotlinJsonAdapterFactory idiom in Converters).
 */
data class ExpenseexportPersistedState(
    val selectedFormat: String = ExpenseExportFormat.PDF.name,
    val connectedIds: List<String> = emptyList(),
    val excludedItemIds: List<String> = emptyList(),
)

/**
 * Deterministic mock file-size estimate, derived purely from the row count and format so the
 * figure shown on the format chips matches the figure in the success card. PDF carries fixed
 * report chrome plus a per-line block; CSV is a small header plus one compact line per row.
 */
fun estimatedExportBytes(itemCount: Int, format: ExpenseExportFormat): Long = when (format) {
    ExpenseExportFormat.CSV -> 96L + itemCount * 64L
    ExpenseExportFormat.PDF -> 14_500L + itemCount * 1_850L
}

/** Human-readable byte size (B / KB / MB) for the size estimate. */
fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
}
