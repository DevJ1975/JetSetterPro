package com.jetsetter.pro

import com.jetsetter.pro.core.backend.CloudSession
import com.jetsetter.pro.core.backend.FakeCloudBackend
import com.jetsetter.pro.core.backend.PackingListDoc
import com.jetsetter.pro.core.backend.TravelSignalDoc
import com.jetsetter.pro.core.model.Expense
import com.jetsetter.pro.core.model.ExpenseCategory
import com.jetsetter.pro.core.model.PackingItem
import com.jetsetter.pro.core.model.Trip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the [com.jetsetter.pro.core.backend.CloudBackend] seam contract (plan B4) against
 * [FakeCloudBackend]: upsert→getOnce roundtrip, stable-id replacement, delete, observe emission,
 * per-uid isolation, packing lists keyed by tripId, and the anonymous-first auth surface
 * (ensureSignedIn null-when-unconfigured, email link preserving the uid, account deletion).
 * Any future backend implementation must satisfy the same behaviors.
 */
class CloudBackendContractTest {

    private val uid = "user-1"

    private fun trip(id: String = "t1", name: String = "Tokyo") = Trip(
        id = id,
        name = name,
        destination = "Tokyo, Japan",
        startDate = "2026-08-01",
        endDate = "2026-08-07",
    )

    private fun expense(id: String = "e1", merchant: String = "Nobu") = Expense(
        id = id,
        amount = 40.0,
        category = ExpenseCategory.FOOD,
        merchant = merchant,
        date = "2026-08-02",
    )

    // ── CloudCollection contract ─────────────────────────────────────────────

    @Test
    fun upsertThenGetOnce_roundTrips() = runBlocking {
        val backend = FakeCloudBackend()
        val t = trip()
        val e = expense()
        backend.trips.upsert(uid, t)
        backend.expenses.upsert(uid, e)

        assertEquals(listOf(t), backend.trips.getOnce(uid))
        assertEquals(listOf(e), backend.expenses.getOnce(uid))
    }

    @Test
    fun upsert_withSameIdReplacesInsteadOfDuplicating() = runBlocking {
        val backend = FakeCloudBackend()
        backend.trips.upsert(uid, trip(name = "Tokyo"))
        backend.trips.upsert(uid, trip(name = "Osaka"))

        val remote = backend.trips.getOnce(uid)
        assertEquals(1, remote.size)
        assertEquals("Osaka", remote.single().name)
    }

    @Test
    fun delete_removesOnlyThatDocument() = runBlocking {
        val backend = FakeCloudBackend()
        backend.trips.upsert(uid, trip(id = "t1"))
        backend.trips.upsert(uid, trip(id = "t2", name = "Lisbon"))

        backend.trips.delete(uid, "t1")

        assertEquals(listOf("t2"), backend.trips.getOnce(uid).map { it.id })
    }

    @Test
    fun delete_ofUnknownIdIsSilent() = runBlocking {
        val backend = FakeCloudBackend()
        backend.trips.delete(uid, "missing")   // must not throw (best-effort contract)
        assertTrue(backend.trips.getOnce(uid).isEmpty())
    }

    @Test
    fun observe_emitsTheFullSetAfterUpsert() = runBlocking {
        val backend = FakeCloudBackend()
        val t = trip()
        backend.trips.upsert(uid, t)

        assertEquals(listOf(t), backend.trips.observe(uid).first())
    }

    @Test
    fun collections_areIsolatedPerUid() = runBlocking {
        val backend = FakeCloudBackend()
        backend.expenses.upsert("user-a", expense(id = "ea"))
        backend.expenses.upsert("user-b", expense(id = "eb"))

        assertEquals(listOf("ea"), backend.expenses.getOnce("user-a").map { it.id })
        assertEquals(listOf("eb"), backend.expenses.getOnce("user-b").map { it.id })
        assertTrue(backend.expenses.getOnce(uid).isEmpty())
    }

    @Test
    fun travelSignals_roundTripStablySortedById() = runBlocking {
        val backend = FakeCloudBackend()
        val doc = TravelSignalDoc(
            id = "s1",
            kind = "seatChosen",
            payloadJson = """{"value":"12A"}""",
            updatedAt = "2026-07-17T12:00:00Z",
        )
        backend.travelSignals.upsert(uid, doc)
        assertEquals(listOf(doc), backend.travelSignals.getOnce(uid))
    }

    // ── packingLists: doc id = tripId ────────────────────────────────────────

    @Test
    fun packingLists_areKeyedByTripId() = runBlocking {
        val backend = FakeCloudBackend()
        val first = PackingListDoc("trip-1", listOf(PackingItem(name = "Passport")))
        val replacement = PackingListDoc("trip-1", listOf(PackingItem(name = "Adapter"), PackingItem(name = "Visa")))

        backend.packingLists.upsert(uid, first)
        backend.packingLists.upsert(uid, replacement)

        // Same tripId → one document, latest list wins.
        val remote = backend.packingLists.getOnce(uid)
        assertEquals(1, remote.size)
        assertEquals("trip-1", remote.single().tripId)
        assertEquals(listOf("Adapter", "Visa"), remote.single().items.map { it.name })
    }

    @Test
    fun packingLists_deleteByTripId() = runBlocking {
        val backend = FakeCloudBackend()
        backend.packingLists.upsert(uid, PackingListDoc("trip-1", listOf(PackingItem(name = "Passport"))))
        backend.packingLists.upsert(uid, PackingListDoc("trip-2", listOf(PackingItem(name = "Sunscreen"))))

        backend.packingLists.delete(uid, "trip-1")

        assertEquals(listOf("trip-2"), backend.packingLists.getOnce(uid).map { it.tripId })
    }

    // ── Auth surface ─────────────────────────────────────────────────────────

    @Test
    fun ensureSignedIn_createsAnonymousSessionAndReturnsStableUid() = runBlocking {
        val backend = FakeCloudBackend(fakeUid = "abc")
        val first = backend.ensureSignedIn()
        val second = backend.ensureSignedIn()

        assertEquals("abc", first)
        assertEquals(first, second)   // one account, not one per caller
        val session = backend.session.first()
        assertEquals(CloudSession(uid = "abc", email = null, isAnonymous = true), session)
        assertEquals(session, backend.currentSession())
    }

    @Test
    fun ensureSignedIn_returnsNullWhenUnconfigured() = runBlocking {
        val backend = FakeCloudBackend(configured = false)
        assertNull(backend.ensureSignedIn())   // never throws — callers fall back to local-only
        assertNull(backend.currentSession())
        assertFalse(backend.isConfigured)
    }

    @Test
    fun linkEmailToAnonymous_upgradesInPlacePreservingUid() = runBlocking {
        val backend = FakeCloudBackend(fakeUid = "abc")
        val anonUid = backend.ensureSignedIn()

        backend.linkEmailToAnonymous("jamil@example.com", "hunter22")

        val session = requireNotNull(backend.currentSession())
        assertEquals(anonUid, session.uid)     // uid preserved → synced rows stay attached
        assertEquals("jamil@example.com", session.email)
        assertFalse(session.isAnonymous)
    }

    @Test
    fun signOut_clearsSessionButKeepsCloudData() = runBlocking {
        val backend = FakeCloudBackend()
        val signedInUid = requireNotNull(backend.ensureSignedIn())
        backend.trips.upsert(signedInUid, trip())

        backend.signOut()

        assertNull(backend.currentSession())
        assertEquals(1, backend.trips.getOnce(signedInUid).size)
    }

    @Test
    fun deleteAccount_wipesTheUsersDataAndSignsOut() = runBlocking {
        val backend = FakeCloudBackend()
        val signedInUid = requireNotNull(backend.ensureSignedIn())
        backend.trips.upsert(signedInUid, trip())
        backend.expenses.upsert(signedInUid, expense())
        backend.packingLists.upsert(signedInUid, PackingListDoc("t1", listOf(PackingItem(name = "Passport"))))

        assertTrue(backend.deleteAccount().isSuccess)

        assertNull(backend.currentSession())
        assertTrue(backend.trips.getOnce(signedInUid).isEmpty())
        assertTrue(backend.expenses.getOnce(signedInUid).isEmpty())
        assertTrue(backend.packingLists.getOnce(signedInUid).isEmpty())
    }

    @Test
    fun deleteAccount_failsWithoutSessionAndWipesNothing() = runBlocking {
        // B5b ordering contract: if the server-side deletion can't run (here: nobody is signed
        // in — the live analogue of the edge function being unreachable), the operation returns
        // a failure Result (never throws) and local/cloud data is left untouched for a retry.
        val backend = FakeCloudBackend()
        val signedInUid = requireNotNull(backend.ensureSignedIn())
        backend.trips.upsert(signedInUid, trip())
        backend.signOut()

        val result = backend.deleteAccount()

        assertTrue(result.isFailure)
        assertEquals(1, backend.trips.getOnce(signedInUid).size)
    }

    @Test
    fun deleteAccount_failsWhenUnconfigured() = runBlocking {
        val result = FakeCloudBackend(configured = false).deleteAccount()
        assertTrue(result.isFailure)   // failure Result, not an exception — best-effort doctrine
    }
}
