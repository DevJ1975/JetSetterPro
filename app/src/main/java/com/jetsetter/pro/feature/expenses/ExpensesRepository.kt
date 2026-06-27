package com.jetsetter.pro.feature.expenses

import com.jetsetter.pro.core.data.mock.MockData
import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.jetsetter.pro.core.model.Expense
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for expenses. Mock-first: seeded from [MockData.expenses], then any
 * tester-added expenses are persisted as a Moshi-serialized JSON list via [ModuleStateStore]
 * (key [KEY]) so they survive app restarts — the same Moshi idiom Room uses in
 * `core/data/local/Converters.kt`. Moshi serializes the [com.jetsetter.pro.core.model.ExpenseCategory]
 * enum to its name automatically.
 */
@Singleton
class ExpensesRepository @Inject constructor(
    private val store: ModuleStateStore,
) {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<Expense>>(Types.newParameterizedType(List::class.java, Expense::class.java))

    /** Observe the persisted list, falling back to the seed if nothing is stored yet or parsing fails. */
    fun observeExpenses(): Flow<List<Expense>> =
        store.observe(KEY).map { json -> json?.let(::parse) ?: MockData.expenses }

    /** Write the seed to the store once, so the first launch has something concrete to persist against. */
    suspend fun seedIfEmpty() {
        if (store.read(KEY) == null) persist(MockData.expenses)
    }

    /** Prepend a new expense (newest first) and persist. */
    suspend fun add(expense: Expense) {
        persist(listOf(expense) + current())
    }

    private suspend fun current(): List<Expense> {
        val json = store.read(KEY) ?: return MockData.expenses
        return parse(json) ?: MockData.expenses
    }

    private suspend fun persist(expenses: List<Expense>) {
        store.save(KEY, adapter.toJson(expenses))
    }

    private fun parse(json: String): List<Expense>? =
        runCatching { adapter.fromJson(json) }.getOrNull()

    private companion object {
        const val KEY = "expenses_list"
    }
}
