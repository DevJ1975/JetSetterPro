package com.jetsetter.pro.core.sync

import com.jetsetter.pro.core.backend.TravelWalletItemDoc
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors Travel Wallet passes to the shared Supabase Postgres backend (`public.wallet_items`,
 * one row per pass, owner-scoped by RLS on `user_id = auth.uid()`). Cloned from the
 * [SupabaseExpenseSync] template — the local wallet store stays the offline-first source of
 * truth; this is the cross-device sync layer (the R8 walletItems producer wires it up).
 *
 * The table comes from `supabase/migrations/0002` which is NOT yet applied to the live project,
 * so this class is hardened one notch past the trips/expenses syncs: [getOnce] degrades to an
 * empty list (permitted for optional collections by the
 * [com.jetsetter.pro.core.backend.CloudCollection] contract) and [observe] terminates silently
 * when the table is missing or absent from the `supabase_realtime` publication.
 *
 * [client] is nullable: when Supabase is not configured every method is a no-op / empty so the
 * app runs purely on local data.
 */
@Singleton
class SupabaseWalletItemSync @Inject constructor(
    private val client: SupabaseClient?,
) {
    /** Best-effort upsert. Swallows failures (offline / missing table) — local already holds the write. */
    suspend fun push(uid: String, doc: TravelWalletItemDoc) {
        val c = client ?: return
        runCatching { c.from(TABLE).upsert(doc.toRow(uid)) }
    }

    /** Best-effort delete. */
    suspend fun delete(uid: String, itemId: String) {
        val c = client ?: return
        runCatching {
            c.from(TABLE).delete {
                filter {
                    eq("id", itemId)
                    eq("user_id", uid)
                }
            }
        }
    }

    /** One-shot read of the user's remote passes (for the sign-in merge). Empty on any failure. */
    suspend fun getOnce(uid: String): List<TravelWalletItemDoc> {
        val c = client ?: return emptyList()
        return runCatching {
            c.from(TABLE)
                .select(COLUMNS) { filter { eq("user_id", uid) } }
                .decodeList<WalletItemRow>()
                .map { it.toDoc() }
        }.getOrDefault(emptyList())
    }

    /**
     * Live remote passes for [uid] via Realtime postgres-changes. On every change the full
     * owner-scoped set is re-fetched and emitted; transient fetch failures are dropped (never
     * emitted as empty) so a blip can't wipe local data. The terminal `catch` keeps the flow
     * silent when the table is missing or unpublished. No-op flow when Supabase is not configured.
     */
    fun observe(uid: String): Flow<List<TravelWalletItemDoc>> {
        val c = client ?: return emptyFlow()
        val channel = c.channel("wallet_items:$uid")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = TABLE
            filter("user_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, uid)
        }
        return changes
            .onStart { channel.subscribe() }
            .mapNotNull { runCatching { getOnce(uid) }.getOrNull() }
            .onCompletion { runCatching { c.realtime.removeChannel(channel) } }
            .catch { /* channel/join failure (missing table or publication): stay silent */ }
    }

    private companion object {
        const val TABLE = "wallet_items"

        // Select only the columns [WalletItemRow] decodes; the table also has a server-managed
        // `updated_at` that the DTO omits (so upserts never touch it), and decoding it would choke.
        val COLUMNS = Columns.list(
            "id", "user_id", "type", "title", "subtitle",
            "key_detail_label", "key_detail_value", "date", "is_favorite",
        )
    }
}

// ── Wire DTO ─────────────────────────────────────────────────────────────────
// Postgrest (de)serializes rows with kotlinx.serialization. Columns are snake_case (@SerialName);
// `type` is the TravelWalletPassType enum constant name as a string.

@Serializable
private data class WalletItemRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String,
    val title: String,
    val subtitle: String,
    @SerialName("key_detail_label") val keyDetailLabel: String,
    @SerialName("key_detail_value") val keyDetailValue: String,
    val date: String,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
)

private fun TravelWalletItemDoc.toRow(uid: String) = WalletItemRow(
    id = id,
    userId = uid,
    type = type,
    title = title,
    subtitle = subtitle,
    keyDetailLabel = keyDetailLabel,
    keyDetailValue = keyDetailValue,
    date = date,
    isFavorite = isFavorite,
)

private fun WalletItemRow.toDoc() = TravelWalletItemDoc(
    id = id,
    type = type,
    title = title,
    subtitle = subtitle,
    keyDetailLabel = keyDetailLabel,
    keyDetailValue = keyDetailValue,
    date = date,
    isFavorite = isFavorite,
)
