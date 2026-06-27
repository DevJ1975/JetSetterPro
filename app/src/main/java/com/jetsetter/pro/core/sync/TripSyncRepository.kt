package com.jetsetter.pro.core.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jetsetter.pro.core.model.ItineraryItem
import com.jetsetter.pro.core.model.PackingItem
import com.jetsetter.pro.core.model.Trip
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors trips to Cloud Firestore under `users/{uid}/trips/{tripId}`.
 *
 * Room stays the offline-first source of truth (see [com.jetsetter.pro.core.data.repository.TripRepository]);
 * this is the cross-device sync layer. Writes are fire-and-forget: Firestore's local cache
 * applies them immediately and flushes to the backend when online, so we never block (or hang
 * offline) awaiting a server ack. Nested item/packing lists are stored as JSON strings,
 * mirroring the Room [com.jetsetter.pro.core.data.local.Converters].
 */
@Singleton
class TripSyncRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val itemsType = Types.newParameterizedType(List::class.java, ItineraryItem::class.java)
    private val packingType = Types.newParameterizedType(List::class.java, PackingItem::class.java)
    private val itemsAdapter = moshi.adapter<List<ItineraryItem>>(itemsType)
    private val packingAdapter = moshi.adapter<List<PackingItem>>(packingType)

    private fun trips(uid: String) =
        firestore.collection("users").document(uid).collection("trips")

    /** Best-effort upsert; Firestore queues offline and syncs on reconnect. */
    fun push(uid: String, trip: Trip) {
        trips(uid).document(trip.id).set(trip.toMap(), SetOptions.merge())
    }

    /** Best-effort delete; Firestore queues offline and syncs on reconnect. */
    fun delete(uid: String, tripId: String) {
        trips(uid).document(tripId).delete()
    }

    /** One-shot read of the user's remote trips (for the initial sign-in reconcile). */
    suspend fun getOnce(uid: String): List<Trip> =
        trips(uid).get().await().documents.mapNotNull { it.toTrip() }

    /**
     * Live remote trips for [uid]. An empty result that is **only from the local cache** is
     * skipped, so we don't momentarily wipe local data before the server responds; an empty
     * result confirmed by the server is emitted (so "all trips deleted elsewhere" propagates).
     */
    fun observe(uid: String): Flow<List<Trip>> = callbackFlow {
        val registration = trips(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            if (snapshot.isEmpty && snapshot.metadata.isFromCache) return@addSnapshotListener
            trySend(snapshot.documents.mapNotNull { it.toTrip() })
        }
        awaitClose { registration.remove() }
    }

    private fun Trip.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "destination" to destination,
        "startDate" to startDate,
        "endDate" to endDate,
        "items" to itemsAdapter.toJson(items),
        "packingList" to packingAdapter.toJson(packingList),
    )

    private fun DocumentSnapshot.toTrip(): Trip? {
        val docId = getString("id") ?: id
        val name = getString("name") ?: return null
        val destination = getString("destination") ?: return null
        val startDate = getString("startDate") ?: return null
        val endDate = getString("endDate") ?: return null
        val items = getString("items")?.let { itemsAdapter.fromJson(it) } ?: emptyList()
        val packingList = getString("packingList")?.let { packingAdapter.fromJson(it) } ?: emptyList()
        return Trip(docId, name, destination, startDate, endDate, items, packingList)
    }
}
