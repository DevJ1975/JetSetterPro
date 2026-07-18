package com.jetsetter.pro.feature.loyaltyvault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.intelligence.TravelProfileStore
import com.jetsetter.pro.core.intelligence.TravelSignal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class LoyaltyvaultViewModel @Inject constructor(
    private val repository: LoyaltyvaultRepository,
    private val travelProfileStore: TravelProfileStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(LoyaltyvaultUiState(isLoading = true))
    val ui: StateFlow<LoyaltyvaultUiState> = _ui.asStateFlow()

    init {
        // Seed before collecting so the empty state doesn't flash on first run, then fold the
        // repository Flow into UI state — the canonical collect pattern used across the app.
        viewModelScope.launch {
            repository.seedIfEmpty()
            repository.observeAccounts().collect { accounts ->
                _ui.update { state ->
                    // Drop a filter whose type no longer has any accounts (e.g. last one deleted),
                    // and forget reveals for accounts that no longer exist.
                    val ids = accounts.mapTo(HashSet()) { it.id }
                    val filter = when (state.filter) {
                        LoyaltyVaultFilter.AIRLINE ->
                            state.filter.takeIf { accounts.any { a -> a.type == LoyaltyVaultProgramType.AIRLINE } }
                        LoyaltyVaultFilter.HOTEL ->
                            state.filter.takeIf { accounts.any { a -> a.type == LoyaltyVaultProgramType.HOTEL } }
                        LoyaltyVaultFilter.ALL -> state.filter
                    } ?: LoyaltyVaultFilter.ALL
                    state.copy(
                        accounts = accounts,
                        isLoading = false,
                        filter = filter,
                        revealedIds = state.revealedIds.intersect(ids),
                    )
                }
            }
        }
    }

    /** Switches the All / Airlines / Hotels segment (also re-scopes the summary). */
    fun selectFilter(filter: LoyaltyVaultFilter) {
        _ui.update { it.copy(filter = filter) }
    }

    /** Cycles the list ordering (Value → Balance → Name → Value). */
    fun cycleSort() {
        _ui.update {
            val next = LoyaltyVaultSort.entries[(it.sort.ordinal + 1) % LoyaltyVaultSort.entries.size]
            it.copy(sort = next)
        }
    }

    /** Reveals or re-masks the membership number for a single account (no-op in privacy mode). */
    fun toggleReveal(accountId: String) {
        _ui.update { state ->
            if (state.hideBalances) return@update state
            val revealed = state.revealedIds.toMutableSet()
            if (!revealed.add(accountId)) revealed.remove(accountId)
            state.copy(revealedIds = revealed)
        }
    }

    /** Privacy mode: masks every balance / value / number at once; also clears any reveals. */
    fun togglePrivacy() {
        _ui.update { it.copy(hideBalances = !it.hideBalances, revealedIds = emptySet()) }
    }

    /** Pin / unpin so a program floats to the top of the list (persisted). */
    fun togglePin(accountId: String) {
        viewModelScope.launch { repository.togglePin(accountId) }
    }

    fun showAddSheet() {
        _ui.update { it.copy(showAddSheet = true) }
    }

    fun dismissAddSheet() {
        _ui.update { it.copy(showAddSheet = false) }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch { repository.removeAccount(accountId) }
    }

    /**
     * Builds a [LoyaltyVaultAccount] from the sheet inputs and persists it. Blank program name or
     * membership number is rejected (the sheet also disables the button); [balanceText] is parsed
     * digits-only so "248,530" or "248530 pts" both work. Valuation / unit default off the chosen
     * [type] so the value math stays self-consistent. Flips [LoyaltyvaultUiState.isSaving] for the
     * brief persist, then closes the sheet.
     */
    fun addAccount(
        type: LoyaltyVaultProgramType,
        programName: String,
        membershipNumber: String,
        tier: String,
        balanceText: String,
    ) {
        val name = programName.trim()
        val number = membershipNumber.trim()
        if (name.isEmpty() || number.isEmpty()) return
        val balance = balanceText.filter { it.isDigit() }.toLongOrNull() ?: 0L

        _ui.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repository.addAccount(
                LoyaltyVaultAccount(
                    id = "lv-user-${System.currentTimeMillis()}",
                    programName = name,
                    membershipNumber = number,
                    tier = tier.trim().ifEmpty { "Member" },
                    balance = balance,
                    unitLabel = type.defaultUnitLabel,
                    type = type,
                    centsPerUnit = type.defaultCentsPerUnit,
                    nextTier = null,
                ),
            )
            // Learning seam (plan A4): a user-added program teaches the profile which airline/hotel
            // brands they belong to. Only user adds pass here — seeds go through seedIfEmpty.
            travelProfileStore.record(
                TravelSignal(
                    kind = TravelSignal.Kind.LOYALTY_ADDED,
                    value = name,
                    attributes = mapOf(
                        TravelSignal.Attr.BRAND_KIND to when (type) {
                            LoyaltyVaultProgramType.AIRLINE -> "airline"
                            LoyaltyVaultProgramType.HOTEL -> "hotel"
                        },
                    ),
                    timestamp = Instant.now().toString(),
                    source = "loyalty",
                ),
            )
            _ui.update { it.copy(isSaving = false, showAddSheet = false) }
        }
    }
}
