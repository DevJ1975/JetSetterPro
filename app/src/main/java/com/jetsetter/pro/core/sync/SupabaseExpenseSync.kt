package com.jetsetter.pro.core.sync

import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.ExpenseCategory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors expenses to the shared Supabase Postgres backend (`public.expenses`, one row per
 * expense, owner-scoped by RLS on `user_id = auth.uid()`). The exact analogue of
 * [SupabaseTripSync] — Room stays the offline-first source of truth (see
 * [com.jetsetter.pro.core.data.repository.ExpenseRepository]); this is the cross-device sync layer.
 *
 * [client] is nullable: when Supabase is not configured every method is a no-op / empty so the app
 * runs purely on local Room data.
 */
@Singleton
class SupabaseExpenseSync @Inject constructor(
    private val client: SupabaseClient?,
) {
    /** Best-effort upsert. Swallows failures (offline / transient) — Room already holds the write. */
    suspend fun push(uid: String, expense: Expense) {
        val c = client ?: return
        runCatching { c.from(TABLE).upsert(expense.toRow(uid)) }
    }

    /** Best-effort delete. */
    suspend fun delete(uid: String, expenseId: String) {
        val c = client ?: return
        runCatching {
            c.from(TABLE).delete {
                filter {
                    eq("id", expenseId)
                    eq("user_id", uid)
                }
            }
        }
    }

    /** One-shot read of the user's remote expenses (for the sign-in reconcile). */
    suspend fun getOnce(uid: String): List<Expense> {
        val c = client ?: return emptyList()
        return c.from(TABLE)
            .select(COLUMNS) { filter { eq("user_id", uid) } }
            .decodeList<ExpenseRow>()
            .map { it.toExpense() }
    }

    /**
     * Live remote expenses for [uid] via Realtime postgres-changes. On every change the full
     * owner-scoped set is re-fetched and emitted; transient fetch failures are dropped (never
     * emitted as empty) so a blip can't wipe local data. Requires the `expenses` table to be in
     * the `supabase_realtime` publication. No-op flow when Supabase is not configured.
     */
    fun observe(uid: String): Flow<List<Expense>> {
        val c = client ?: return emptyFlow()
        val channel = c.channel("expenses:$uid")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = TABLE
            filter("user_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, uid)
        }
        return changes
            .onStart { channel.subscribe() }
            .mapNotNull { runCatching { getOnce(uid) }.getOrNull() }
            .onCompletion { runCatching { c.realtime.removeChannel(channel) } }
    }

    private companion object {
        const val TABLE = "expenses"

        // Select only the columns [ExpenseRow] decodes; the table also has a server-managed
        // `updated_at` that the DTO omits (so upserts never null it), and decoding it would choke.
        val COLUMNS = Columns.list(
            "id", "user_id", "amount", "currency", "category", "merchant", "date", "notes",
        )
    }
}

// ── Wire DTO ─────────────────────────────────────────────────────────────────
// Postgrest (de)serializes rows with kotlinx.serialization. Columns are snake_case (@SerialName);
// `category` is the ExpenseCategory enum name as a string.

@Serializable
private data class ExpenseRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val amount: Double,
    val currency: String = "USD",
    val category: String,
    val merchant: String,
    val date: String,
    val notes: String? = null,
)

private fun Expense.toRow(uid: String) = ExpenseRow(
    id = id,
    userId = uid,
    amount = amount,
    currency = currency,
    category = category.name,
    merchant = merchant,
    date = date,
    notes = notes,
)

private fun ExpenseRow.toExpense() = Expense(
    id = id,
    amount = amount,
    currency = currency,
    category = category.toExpenseCategory(),
    merchant = merchant,
    date = date,
    notes = notes,
)

/** Tolerates an unknown category name from a newer client. */
private fun String.toExpenseCategory(): ExpenseCategory =
    runCatching { ExpenseCategory.valueOf(this) }.getOrDefault(ExpenseCategory.OTHER)
