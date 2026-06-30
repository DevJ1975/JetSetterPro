package com.jetsetter.pro.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.ExpenseCategory

/**
 * Room row for an expense. [category] is stored as the enum *name* (a plain String) rather than
 * via a TypeConverter — it keeps the table introspectable and matches how the Supabase `category`
 * column and the iOS contract store it.
 */
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val amount: Double,
    val currency: String,
    val category: String,
    val merchant: String,
    val date: String,
    val notes: String?,
)

fun ExpenseEntity.toDomain(): Expense =
    Expense(id, amount, currency, category.toExpenseCategory(), merchant, date, notes)

fun Expense.toEntity(): ExpenseEntity =
    ExpenseEntity(id, amount, currency, category.name, merchant, date, notes)

/** Tolerates an unknown category name from a newer client. */
private fun String.toExpenseCategory(): ExpenseCategory =
    runCatching { ExpenseCategory.valueOf(this) }.getOrDefault(ExpenseCategory.OTHER)
