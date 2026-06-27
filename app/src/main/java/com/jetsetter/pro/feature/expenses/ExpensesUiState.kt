package com.jetsetter.pro.feature.expenses

import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.ExpenseCategory

/** A single category's rolled-up spend, used by the breakdown card. */
data class CategoryTotal(
    val category: ExpenseCategory,
    val total: Double,
)

/**
 * Single immutable UI-state object for the Expenses screen — one object rather than scattered
 * flags, so impossible states can't arise and previews are trivial (guide §6).
 *
 * [expenses] is the full, unfiltered list; the derived accessors below project it for the
 * summary, the breakdown card, and the (optionally) category-filtered list shown to the user.
 */
data class ExpensesUiState(
    val expenses: List<Expense> = emptyList(),
    val selectedCategory: ExpenseCategory? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Total across every expense, regardless of the active filter. */
    val grandTotal: Double get() = expenses.sumOf { it.amount }

    /** The list actually shown — narrowed to [selectedCategory] when one is active. */
    val visibleExpenses: List<Expense>
        get() = selectedCategory?.let { cat -> expenses.filter { it.category == cat } } ?: expenses

    /** Total of the currently visible (filtered) expenses. */
    val visibleTotal: Double get() = visibleExpenses.sumOf { it.amount }

    /** Per-category totals, largest first — drives the breakdown card and its proportion bars. */
    val categoryTotals: List<CategoryTotal>
        get() = expenses
            .groupBy { it.category }
            .map { (category, items) -> CategoryTotal(category, items.sumOf { it.amount }) }
            .sortedByDescending { it.total }
}
