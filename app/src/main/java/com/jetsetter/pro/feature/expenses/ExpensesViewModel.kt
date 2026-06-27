package com.jetsetter.pro.feature.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.ExpenseCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val repository: ExpensesRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ExpensesUiState(isLoading = true))
    val ui: StateFlow<ExpensesUiState> = _ui.asStateFlow()

    init {
        // Seed before collecting so the empty state doesn't flash on first run, then fold the
        // repository Flow into UI state — the canonical guide §6 collect pattern.
        viewModelScope.launch {
            repository.seedIfEmpty()
            repository.observeExpenses()
                .catch { e -> _ui.update { it.copy(isLoading = false, errorMessage = e.message) } }
                .collect { list -> _ui.update { it.copy(expenses = list, isLoading = false) } }
        }
    }

    /** Toggle the active category filter; pass `null` to clear it (show all). */
    fun selectCategory(category: ExpenseCategory?) {
        _ui.update { it.copy(selectedCategory = if (it.selectedCategory == category) null else category) }
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
            repository.add(
                Expense(
                    amount = amount,
                    category = category,
                    merchant = merchant.trim(),
                    date = date,
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
