package com.jetsetter.pro.core.sync

import com.jetsetter.pro.core.model.ItineraryItem
import com.jetsetter.pro.core.model.ItineraryItemType
import com.jetsetter.pro.core.model.PackingItem
import com.jetsetter.pro.core.model.Trip
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
 * Mirrors trips to the shared Supabase Postgres backend (`public.trips`, one row per trip,
 * owner-scoped by Row Level Security on `user_id = auth.uid()`). Replaces the previous Cloud
 * Firestore mirror; this is the same database the iOS app talks to.
 *
 * Room stays the offline-first source of truth (see
 * [com.jetsetter.pro.core.data.repository.TripRepository]); this is the cross-device sync layer.
 * Nested item / packing lists are stored as native **`jsonb`** columns (not JSON-in-a-string as
 * Firestore did) — the [TripRow] DTO encodes them via kotlinx.serialization. The JSON keys inside
 * those arrays stay camelCase (`startDate`, `isPacked`, …) so they match the iOS encoding; only
 * the top-level columns are snake_case (`user_id`, `start_date`, `packing_list`).
 *
 * [client] is nullable: when Supabase is not configured every method is a no-op / empty so the
 * app runs purely on local Room data.
 */
@Singleton
class SupabaseTripSync @Inject constructor(
    private val client: SupabaseClient?,
) {
    /** Best-effort upsert. Swallows failures (offline / transient) — Room already holds the write. */
    suspend fun push(uid: String, trip: Trip) {
        val c = client ?: return
        runCatching { c.from(TABLE).upsert(trip.toRow(uid)) }
    }

    /** Best-effort delete. */
    suspend fun delete(uid: String, tripId: String) {
        val c = client ?: return
        runCatching {
            c.from(TABLE).delete {
                filter {
                    eq("id", tripId)
                    eq("user_id", uid)
                }
            }
        }
    }

    /** One-shot read of the user's remote trips (for the sign-in reconcile). */
    suspend fun getOnce(uid: String): List<Trip> {
        val c = client ?: return emptyList()
        return c.from(TABLE)
            .select(COLUMNS) { filter { eq("user_id", uid) } }
            .decodeList<TripRow>()
            .map { it.toTrip() }
    }

    /**
     * Live remote trips for [uid] via Realtime postgres-changes. On every insert/update/delete
     * the full owner-scoped set is re-fetched and emitted, so [TripRepository] can mirror remote
     * edits *and deletions* into Room. Fetch failures are dropped (not emitted as empty) so a
     * transient error never wipes local data. Requires the `trips` table to be in the
     * `supabase_realtime` publication. No-op flow when Supabase is not configured.
     */
    fun observe(uid: String): Flow<List<Trip>> {
        val c = client ?: return emptyFlow()
        val channel = c.channel("trips:$uid")
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
        const val TABLE = "trips"

        // Select only the columns [TripRow] decodes. The table also has a server-managed
        // `updated_at` column that the DTO intentionally omits (so upserts never send null into
        // its NOT NULL DEFAULT now()); selecting it would make decodeList choke on an unknown key.
        val COLUMNS = Columns.list(
            "id", "user_id", "name", "destination", "start_date", "end_date", "items", "packing_list",
        )
    }
}

// ── Wire DTOs ────────────────────────────────────────────────────────────────
// Postgrest (de)serializes rows with kotlinx.serialization. Top-level columns are snake_case
// (@SerialName); the jsonb array elements keep camelCase property names so both platforms read
// the same shape. `type` is the ItineraryItemType enum name as a string.

@Serializable
private data class TripRow(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val destination: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    val items: List<ItineraryItemRow> = emptyList(),
    @SerialName("packing_list") val packingList: List<PackingItemRow> = emptyList(),
)

@Serializable
private data class ItineraryItemRow(
    val id: String,
    val title: String,
    val type: String,
    val startDate: String,
    val endDate: String? = null,
    val location: String? = null,
    val notes: String? = null,
)

@Serializable
private data class PackingItemRow(
    val id: String,
    val name: String,
    val isPacked: Boolean = false,
)

private fun Trip.toRow(uid: String) = TripRow(
    id = id,
    userId = uid,
    name = name,
    destination = destination,
    startDate = startDate,
    endDate = endDate,
    items = items.map {
        ItineraryItemRow(it.id, it.title, it.type.name, it.startDate, it.endDate, it.location, it.notes)
    },
    packingList = packingList.map { PackingItemRow(it.id, it.name, it.isPacked) },
)

private fun TripRow.toTrip() = Trip(
    id = id,
    name = name,
    destination = destination,
    startDate = startDate,
    endDate = endDate,
    items = items.map {
        ItineraryItem(it.id, it.title, it.type.toItemType(), it.startDate, it.endDate, it.location, it.notes)
    },
    packingList = packingList.map { PackingItem(it.id, it.name, it.isPacked) },
)

/** Maps a stored type name back to the enum, tolerating unknown values from a newer client. */
private fun String.toItemType(): ItineraryItemType =
    runCatching { ItineraryItemType.valueOf(this) }.getOrDefault(ItineraryItemType.ACTIVITY)