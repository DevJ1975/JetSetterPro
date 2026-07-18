package com.jetsetter.pro.feature.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.ai.ExpenseCategorizer
import com.jetsetter.pro.core.data.repository.ExpenseRepository
import com.jetsetter.pro.core.intelligence.TravelProfileStore
import com.jetsetter.pro.core.intelligence.TravelSignal
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.ExpenseCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val travelProfileStore: TravelProfileStore,
    private val categorizer: ExpenseCategorizer,
) : ViewModel() {

    private val _ui = MutableStateFlow(ExpensesUiState(isLoading = true))
    val ui: StateFlow<ExpensesUiState> = _ui.asStateFlow()

    /**
     * On-device category suggestion for the add sheet (spec §3.1) — prefill only. `null` while
     * nothing has been suggested (categorizer unavailable, unsure, or no merchant yet), so the
     * manual picker stays the source of truth.
     */
    private val _suggestedCategory = MutableStateFlow<ExpenseCategory?>(null)
    val suggestedCategory: StateFlow<ExpenseCategory?> = _suggestedCategory.asStateFlow()

    private var suggestionJob: Job? = null

    init {
        // Sign in + reconcile with Supabase (seeds locally first so the empty state doesn't flash),
        // then fold the repository Flow into UI state — the canonical guide §6 collect pattern.
        viewModelScope.launch {
            repository.initSync()
            repository.observeExpenses()
                .catch { e -> _ui.update { it.copy(isLoading = false, errorMessage = e.message) } }
                .collect { list -> _ui.update { it.copy(expenses = list, isLoading = false) } }
        }
    }

    /** Toggle the active category filter; pass `null` to clear it (show all). */
    fun selectCategory(category: ExpenseCategory?) {
        _ui.update { it.copy(selectedCategory = if (it.selectedCategory == category) null else category) }
    }

    /**
     * Ask the on-device categorizer to propose a category for [merchant] (debounced by the caller);
     * a blank merchant clears any previous suggestion. Cancels the in-flight lookup so a stale
     * merchant can never win the race.
     *
     * Note: the receipt-scan path (OCR text → categorize → `RECEIPT_SCANNED` signal) is
     * intentionally absent — no receipt-scan UI flow exists yet; `core/ocr` gets wired to expense
     * entry in a later increment, and the signal is recorded there.
     */
    fun suggestCategory(merchant: String) {
        suggestionJob?.cancel()
        val trimmed = merchant.trim()
        if (trimmed.isEmpty()) {
            _suggestedCategory.value = null
            return
        }
        suggestionJob = viewModelScope.launch {
            _suggestedCategory.value = categorizer.categorize(merchant = trimmed)
        }
    }

    /** Add a user-entered expense. No-ops on invalid input so the UI can stay simple. */
    fun addExpense(
        amount: Double,
        merchant: String,
        category: ExpenseCategory,
        date: String,
    ) {
        if (amount <= 0.0 || merchant.isBlank() || date.isBlank()) return
        viewModelScope.launch {
            val expense = Expense(
                amount = amount,
                category = category,
                merchant = merchant.trim(),
                date = date,
            )
            repository.add(expense)
            // Learning seam (plan A4): a user-entered expense feeds the travel profile's spend
            // stats. Consent gating (master + learnFromReceipts) happens inside the store.
            travelProfileStore.record(
                TravelSignal(
                    kind = TravelSignal.Kind.EXPENSE_LOGGED,
                    value = expense.merchant,
                    attributes = mapOf(
                        TravelSignal.Attr.AMOUNT to expense.amount.toString(),
                        TravelSignal.Attr.CURRENCY to expense.currency,
                        TravelSignal.Attr.CATEGORY to expense.category.name.lowercase(Locale.US),
                    ),
                    timestamp = Instant.now().toString(),
                    source = "expenses",
                ),
            )
        }
    }

    /** Mileage quick-add: miles × the IRS standard rate → a MILEAGE-category expense. */
    fun addMileage(miles: Double, date: String) {
        if (miles <= 0.0 || date.isBlank()) return
        viewModelScope.launch {
            repository.add(
                Expense(
                    amount = miles * IRS_MILEAGE_RATE,
                    category = ExpenseCategory.MILEAGE,
                    merchant = "Mileage reimbursement",
                    date = date,
                    notes = "${trimMiles(miles)} mi @ \$${formatRate(IRS_MILEAGE_RATE)}/mi",
                ),
            )
        }
    }

    companion object {
        /** 2025 IRS standard business mileage rate (USD per mile). */
        const val IRS_MILEAGE_RATE = 0.70

        private fun trimMiles(miles: Double): String =
            if (miles % 1.0 == 0.0) miles.toInt().toString() else miles.toString()

        /** The rate formatted for display, e.g. "0.70" (the Double 0.70 renders as "0.7" unformatted). */
        private fun formatRate(rate: Double): String = String.format(Locale.US, "%.2f", rate)
    }
}
