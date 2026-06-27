package com.jetsetter.pro.feature.identityvault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class IdentityvaultViewModel @Inject constructor(
    private val repository: IdentityvaultRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(IdentityvaultUiState(isLoading = true))
    val ui: StateFlow<IdentityvaultUiState> = _ui.asStateFlow()

    init {
        // Seed before collecting so the empty state doesn't flash on first run, then fold the
        // repository Flow into UI state, computing each credential's expiry status against today.
        viewModelScope.launch {
            repository.seedIfEmpty()
            repository.observeItems()
                .catch { e -> _ui.update { it.copy(isLoading = false, errorMessage = e.message) } }
                .collect { items ->
                    val today = LocalDate.now()
                    val entries = items
                        .map { item ->
                            IdentityVaultEntry(
                                item = item,
                                status = item.status(today),
                                daysUntilExpiry = item.daysUntilExpiry(today),
                                passportCaution = item.passportRenewalCaution(today),
                            )
                        }
                        // Soonest expiry first surfaces expired / expiring items at the top.
                        .sortedWith(compareBy(nullsLast<LocalDate>()) { it.item.expiryDate })
                    _ui.update { state ->
                        // Drop reveal/visibility for any credential that no longer exists.
                        val liveIds = entries.map { it.item.id }.toSet()
                        state.copy(
                            entries = entries,
                            isLoading = false,
                            revealedIds = state.revealedIds intersect liveIds,
                        )
                    }
                }
        }
    }

    /** Mock biometric unlock — shows a brief "scanning" state, then opens the vault. */
    fun unlock() {
        val state = _ui.value
        if (state.isUnlocked || state.isAuthenticating) return
        _ui.update { it.copy(isAuthenticating = true) }
        viewModelScope.launch {
            delay(BIOMETRIC_SCAN_MS)
            _ui.update { it.copy(isUnlocked = true, isAuthenticating = false) }
        }
    }

    /** Re-locks the vault and re-masks every credential. */
    fun lock() {
        _ui.update {
            it.copy(
                isUnlocked = false,
                isAuthenticating = false,
                revealedIds = emptySet(),
                showAddSheet = false,
            )
        }
    }

    /** Reveals or hides a single credential. No-op while the vault is locked. */
    fun toggleReveal(id: String) {
        _ui.update { state ->
            if (!state.isUnlocked) return@update state
            val revealed = if (id in state.revealedIds) state.revealedIds - id else state.revealedIds + id
            state.copy(revealedIds = revealed)
        }
    }

    /** Reveals every credential at once, or hides them all if they're already shown. */
    fun toggleRevealAll() {
        _ui.update { state ->
            if (!state.isUnlocked) return@update state
            val revealed = if (state.allRevealed) emptySet() else state.entries.map { it.item.id }.toSet()
            state.copy(revealedIds = revealed)
        }
    }

    /** Presents the "Add credential" sheet (only while unlocked). */
    fun showAddSheet() {
        _ui.update { if (it.isUnlocked) it.copy(showAddSheet = true) else it }
    }

    /** Dismisses the "Add credential" sheet without persisting anything. */
    fun dismissAddSheet() {
        _ui.update { it.copy(showAddSheet = false) }
    }

    /**
     * Builds an [IdentityVaultItem] from the sheet inputs and persists it. Required fields (label,
     * value, a valid ISO expiry) are validated here too so the data layer never stores junk; the
     * sheet already gates the confirm button on the same rules.
     */
    fun addItem(
        type: IdentityVaultItemType,
        label: String,
        value: String,
        holderName: String,
        issuer: String,
        expiry: String,
    ) {
        val trimmedLabel = label.trim()
        val trimmedValue = value.trim()
        val trimmedExpiry = expiry.trim()
        if (trimmedLabel.isEmpty() || trimmedValue.isEmpty()) return
        if (runCatching { LocalDate.parse(trimmedExpiry) }.isFailure) return
        viewModelScope.launch {
            repository.addItem(
                IdentityVaultItem(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    label = trimmedLabel,
                    value = trimmedValue,
                    holderName = holderName.trim().ifEmpty { "Cardholder" },
                    issuer = issuer.trim().ifEmpty { "—" },
                    expiry = trimmedExpiry,
                ),
            )
            _ui.update { it.copy(showAddSheet = false) }
        }
    }

    /** Deletes a credential by id. */
    fun deleteItem(id: String) {
        viewModelScope.launch { repository.removeItem(id) }
    }

    private companion object {
        const val BIOMETRIC_SCAN_MS = 850L
    }
}
