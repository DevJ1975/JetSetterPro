package com.jetsetter.pro.feature.documentvault

/**
 * Single immutable UI-state object for the Document Vault screen. [documents] is already sorted by
 * soonest expiry and filtered by [selectedFilter] + [selectedStatus]; the count fields summarize
 * the full (unfiltered) set. [attention] is the single most urgent document, derived from the same
 * sorted set, so the banner can never contradict the list.
 */
data class DocumentvaultUiState(
    val documents: List<DocumentVaultEntry> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val revealedIds: Set<String> = emptySet(),
    val selectedFilter: DocumentVaultType? = null,
    val selectedStatus: DocumentVaultStatus? = null,
    val totalCount: Int = 0,
    val validCount: Int = 0,
    val expiringCount: Int = 0,
    val expiredCount: Int = 0,
    /** Soonest-expiring document that is expired or expiring soon, or null if everything is valid. */
    val attention: DocumentVaultEntry? = null,
    /** Whether the "Add document" modal bottom sheet is presented. */
    val showAddSheet: Boolean = false,
    /** The document opened in the detail sheet, or null when none is selected. */
    val selectedDocument: DocumentVaultEntry? = null,
) {
    /** True when a type or status filter is narrowing the list (drives the empty-state copy). */
    val hasActiveFilter: Boolean get() = selectedFilter != null || selectedStatus != null
}
