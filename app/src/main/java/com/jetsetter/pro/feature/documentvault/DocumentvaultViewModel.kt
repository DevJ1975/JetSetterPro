package com.jetsetter.pro.feature.documentvault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

@HiltViewModel
class DocumentvaultViewModel @Inject constructor(
    private val repository: DocumentvaultRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(DocumentvaultUiState(isLoading = true))
    val ui: StateFlow<DocumentvaultUiState> = _ui.asStateFlow()

    // Full, expiry-sorted set; the rendered list is derived from this plus the user's filters.
    private var allEntries: List<DocumentVaultEntry> = emptyList()
    private var revealedIds: Set<String> = emptySet()
    private var selectedFilter: DocumentVaultType? = null
    private var selectedStatus: DocumentVaultStatus? = null
    private var showAddSheet: Boolean = false
    private var selectedId: String? = null

    init {
        viewModelScope.launch {
            // Hydrate persisted user documents before collecting so they don't pop in late.
            repository.load()
            repository.observeDocuments()
                .catch { e -> _ui.update { it.copy(isLoading = false, errorMessage = e.message) } }
                .collect { items ->
                    val today = LocalDate.now()
                    allEntries = items
                        .map { DocumentVaultEntry(it, it.status(today), it.daysUntilExpiry(today)) }
                        // Soonest expiry first; overdue documents (earliest dates) bubble to the top.
                        .sortedBy { it.item.expiryDate }
                    render()
                }
        }
    }

    /** Reveal or re-mask a single document's number in the list. */
    fun toggleReveal(id: String) {
        revealedIds = if (id in revealedIds) revealedIds - id else revealedIds + id
        render()
    }

    /** Filter by document type; pass null to clear. */
    fun selectFilter(type: DocumentVaultType?) {
        selectedFilter = type
        render()
    }

    /** Toggle a status filter from the summary stats; tapping the active one clears it. */
    fun selectStatus(status: DocumentVaultStatus?) {
        selectedStatus = if (status == selectedStatus) null else status
        render()
    }

    fun showAddSheet() {
        showAddSheet = true
        render()
    }

    fun dismissAddSheet() {
        showAddSheet = false
        render()
    }

    fun selectDocument(id: String) {
        selectedId = id
        render()
    }

    fun dismissDetail() {
        selectedId = null
        render()
    }

    /**
     * Persists a tester-entered document. Inputs are defensively trimmed and the date re-parsed
     * (the sheet already gates on validity); anything unparseable is ignored rather than crashing.
     * Optional fields fall back to sensible labels. Closes the sheet on success.
     */
    fun addDocument(
        title: String,
        type: DocumentVaultType,
        documentNumber: String,
        holder: String,
        issuingAuthority: String,
        expiryDate: String,
    ) {
        val trimmedTitle = title.trim()
        val trimmedNumber = documentNumber.trim()
        if (trimmedTitle.isEmpty() || trimmedNumber.isEmpty()) return
        val parsedDate = try {
            LocalDate.parse(expiryDate.trim())
        } catch (_: DateTimeParseException) {
            return
        }
        viewModelScope.launch {
            repository.addDocument(
                title = trimmedTitle,
                type = type,
                documentNumber = trimmedNumber,
                holder = holder.trim().ifEmpty { "Primary traveler" },
                issuingAuthority = issuingAuthority.trim().ifEmpty { "Self-recorded" },
                expiryDate = parsedDate,
            )
            // Close immediately; the repository Flow re-emits with the new document right after.
            showAddSheet = false
            render()
        }
    }

    /** Delete a user-added document (also closes the detail sheet if it was open on that doc). */
    fun deleteDocument(id: String) {
        if (selectedId == id) selectedId = null
        viewModelScope.launch { repository.removeDocument(id) }
    }

    private fun render() {
        val typeFilter = selectedFilter
        val statusFilter = selectedStatus
        val filtered = allEntries.filter { entry ->
            (typeFilter == null || entry.item.type == typeFilter) &&
                (statusFilter == null || entry.status == statusFilter)
        }
        _ui.update {
            it.copy(
                documents = filtered,
                isLoading = false,
                errorMessage = null,
                revealedIds = revealedIds,
                selectedFilter = typeFilter,
                selectedStatus = statusFilter,
                totalCount = allEntries.size,
                validCount = allEntries.count { e -> e.status == DocumentVaultStatus.VALID },
                expiringCount = allEntries.count { e -> e.status == DocumentVaultStatus.EXPIRING_SOON },
                expiredCount = allEntries.count { e -> e.status == DocumentVaultStatus.EXPIRED },
                // First non-valid in expiry order = single most urgent item.
                attention = allEntries.firstOrNull { e -> e.status != DocumentVaultStatus.VALID },
                showAddSheet = showAddSheet,
                selectedDocument = allEntries.firstOrNull { e -> e.item.id == selectedId },
            )
        }
    }
}
