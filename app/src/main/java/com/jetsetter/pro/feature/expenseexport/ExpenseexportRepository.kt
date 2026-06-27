package com.jetsetter.pro.feature.expenseexport

import com.jetsetter.pro.core.data.prefs.ModuleStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock-first backing store for the Expense Export feature. The line items and the list of
 * available accounting platforms are canned in-memory data (no network), matching the rest of
 * the app's demo approach. The user's *choices* — selected format, which platforms are
 * connected, which rows are excluded — are persisted as JSON via [ModuleStateStore] so they
 * survive an app restart.
 */
@Singleton
class ExpenseexportRepository @Inject constructor(
    private val store: ModuleStateStore,
) {
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(ExpenseexportPersistedState::class.java)

    /** Current trip's expenses, the set that would be exported. */
    fun expenses(): List<ExpenseExportItem> = listOf(
        ExpenseExportItem(
            id = "exp-1",
            merchant = "Delta Air Lines",
            category = "Airfare",
            date = "2026-07-14",
            amount = 1290.00,
        ),
        ExpenseExportItem(
            id = "exp-2",
            merchant = "Four Seasons Atlanta",
            category = "Lodging",
            date = "2026-07-14",
            amount = 412.55,
        ),
        ExpenseExportItem(
            id = "exp-3",
            merchant = "Uber",
            category = "Ground Transport",
            date = "2026-07-15",
            amount = 38.20,
        ),
        ExpenseExportItem(
            id = "exp-4",
            merchant = "Bones Steakhouse",
            category = "Meals",
            date = "2026-07-15",
            amount = 72.00,
        ),
    )

    /** Accounting / corporate-card platforms available to sync exports into. */
    fun connections(): List<ExpenseExportConnection> = listOf(
        ExpenseExportConnection(
            id = "brex",
            name = "Brex",
            description = "Corporate cards & spend management",
            isConnected = true,
        ),
        ExpenseExportConnection(
            id = "ramp",
            name = "Ramp",
            description = "Finance automation platform",
        ),
        ExpenseExportConnection(
            id = "divvy",
            name = "Divvy",
            description = "BILL spend & expense",
        ),
        ExpenseExportConnection(
            id = "expensify",
            name = "Expensify",
            description = "Receipt & expense reports",
        ),
    )

    /** Reads the saved choices, or null on first run / parse failure. */
    suspend fun loadPersisted(): ExpenseexportPersistedState? =
        store.read(KEY)?.let { json -> runCatching { adapter.fromJson(json) }.getOrNull() }

    /** Persists the user's current choices as JSON. */
    suspend fun persist(state: ExpenseexportPersistedState) {
        store.save(KEY, adapter.toJson(state))
    }

    private companion object {
        const val KEY = "expenseexport_state"
    }
}
