package com.jetsetter.pro.core.sync

import com.jetsetter.pro.core.backend.DisruptionEventDoc
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors detected flight disruptions to the shared Supabase Postgres backend
 * (`public.disruption_events`, one row per event, owner-scoped by RLS on `user_id = auth.uid()`).
 * Cloned from the [SupabaseExpenseSync] template — detection happens locally
 * (DisruptionMonitorWorker, plan B6); this is the cross-device sync layer.
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
class SupabaseDisruptionEventSync @Inject constructor(
    private val client: SupabaseClient?,
) {
    /** Best-effort upsert. Swallows failures (offline / missing table) — local already holds the write. */
    suspend fun push(uid: String, doc: DisruptionEventDoc) {
        val c = client ?: return
        runCatching { c.from(TABLE).upsert(doc.toRow(uid)) }
    }

    /** Best-effort delete. */
    suspend fun delete(uid: String, eventId: String) {
        val c = client ?: return
        runCatching {
            c.from(TABLE).delete {
                filter {
                    eq("id", eventId)
                    eq("user_id", uid)
                }
            }
        }
    }

    /** One-shot read of the user's remote events (for the sign-in merge). Empty on any failure. */
    suspend fun getOnce(uid: String): List<DisruptionEventDoc> {
        val c = client ?: return emptyList()
        return runCatching {
            c.from(TABLE)
                .select(COLUMNS) { filter { eq("user_id", uid) } }
                .decodeList<DisruptionEventRow>()
                .map { it.toDoc() }
        }.getOrDefault(emptyList())
    }

    /**
     * Live remote events for [uid] via Realtime postgres-changes. On every change the full
     * owner-scoped set is re-fetched and emitted; transient fetch failures are dropped (never
     * emitted as empty) so a blip can't wipe local data. The terminal `catch` keeps the flow
     * silent when the table is missing or unpublished. No-op flow when Supabase is not configured.
     */
    fun observe(uid: String): Flow<List<DisruptionEventDoc>> {
        val c = client ?: return emptyFlow()
        val channel = c.channel("disruption_events:$uid")
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
        const val TABLE = "disruption_events"

        // Select only the columns [DisruptionEventRow] decodes; the table also has a server-managed
        // `updated_at` that the DTO omits (so upserts never touch it), and decoding it would choke.
        val COLUMNS = Columns.list(
            "id", "user_id", "flight_number", "route", "status", "reason", "detected_at", "payload",
        )
    }
}

// ── Wire DTO ─────────────────────────────────────────────────────────────────
// Postgrest (de)serializes rows with kotlinx.serialization. Columns are snake_case (@SerialName);
// `payload` is native jsonb (JsonElement) holding the full provider payload, not JSON-in-a-string.

@Serializable
private data class DisruptionEventRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("flight_number") val flightNumber: String,
    val route: String,
    val status: String,
    val reason: String,
    @SerialName("detected_at") val detectedAt: String,
    val payload: JsonElement = JsonObject(emptyMap()),
)

private fun DisruptionEventDoc.toRow(uid: String) = DisruptionEventRow(
    id = id,
    userId = uid,
    flightNumber = flightNumber,
    route = route,
    status = status,
    reason = reason,
    detectedAt = detectedAt,
    payload = parsePayloadJson(payloadJson),
)

private fun DisruptionEventRow.toDoc() = DisruptionEventDoc(
    id = id,
    flightNumber = flightNumber,
    route = route,
    status = status,
    reason = reason,
    detectedAt = detectedAt,
    payloadJson = payload.toString(),
)

/** Parses payload JSON leniently: malformed text is preserved as a JSON string, never dropped. */
private fun parsePayloadJson(json: String): JsonElement =
    runCatching { Json.parseToJsonElement(json) }.getOrElse { JsonPrimitive(json) }
