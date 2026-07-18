package com.jetsetter.pro

import com.jetsetter.pro.core.backend.TravelSignalDoc
import com.jetsetter.pro.core.intelligence.TravelProfileStoreLogic
import com.jetsetter.pro.core.intelligence.TravelSignal
import com.jetsetter.pro.core.intelligence.TravelSignalCloudCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the travel-signal cloud mapping (plan B5a): the pure [TravelSignalCloudCodec]
 * signal→doc→signal roundtrip (kind as camelCase wire name in its own column, value/attributes/
 * timestamp/source folded into `payloadJson`, timestamp doubling as `updatedAt`), its tolerant
 * decoding (unknown kind / malformed payload → null, skipped by the merge), and the sign-in
 * [TravelProfileStoreLogic.mergeById] reconcile (union by id, local wins, timestamp order, FIFO
 * cap). Pure JVM — no network, no Supabase.
 */
class TravelSignalCloudCodecTest {

    private fun signal(
        id: String = "sig-1",
        kind: TravelSignal.Kind = TravelSignal.Kind.SEAT_CHOSEN,
        value: String = "12A",
        attributes: Map<String, String> = mapOf(
            TravelSignal.Attr.AIRLINE to "Delta",
            TravelSignal.Attr.CABIN_HINT to "economy",
        ),
        timestamp: String = "2026-07-17T12:30:00Z",
        source: String = "checkin",
    ) = TravelSignal(id = id, kind = kind, value = value, attributes = attributes, timestamp = timestamp, source = source)

    // ── signal → doc → signal roundtrip ──────────────────────────────────────

    @Test
    fun roundTrip_preservesEveryField() {
        val original = signal()
        val doc = TravelSignalCloudCodec.toDoc(original)
        assertEquals(original, TravelSignalCloudCodec.fromDoc(doc))
    }

    @Test
    fun roundTrip_survivesEveryKindAndEmptyAttributes() {
        for (kind in TravelSignal.Kind.entries) {
            val original = signal(id = "sig-${kind.name}", kind = kind, attributes = emptyMap())
            val doc = TravelSignalCloudCodec.toDoc(original)
            assertEquals(kind.wireName, doc.kind)
            assertEquals(original, TravelSignalCloudCodec.fromDoc(doc))
        }
    }

    @Test
    fun toDoc_usesWireNameKindAndTimestampAsUpdatedAt() {
        val doc = TravelSignalCloudCodec.toDoc(signal())
        assertEquals("sig-1", doc.id)
        assertEquals("seatChosen", doc.kind)                 // camelCase wire name, not SEAT_CHOSEN
        assertEquals("2026-07-17T12:30:00Z", doc.updatedAt)  // signal timestamp doubles as updatedAt
    }

    @Test
    fun fromDoc_readsTheCrossPlatformPayloadShape() {
        // A doc as another client would write it: payload carries value/attributes/timestamp/source.
        val doc = TravelSignalDoc(
            id = "sig-ios",
            kind = "expenseLogged",
            payloadJson = """{"value":"FOOD","attributes":{"currency":"USD"},""" +
                """"timestamp":"2026-07-01T08:00:00Z","source":"expenses"}""",
            updatedAt = "2026-07-01T08:00:00Z",
        )
        val decoded = requireNotNull(TravelSignalCloudCodec.fromDoc(doc))
        assertEquals(TravelSignal.Kind.EXPENSE_LOGGED, decoded.kind)
        assertEquals("FOOD", decoded.value)
        assertEquals(mapOf("currency" to "USD"), decoded.attributes)
        assertEquals("2026-07-01T08:00:00Z", decoded.timestamp)
        assertEquals("expenses", decoded.source)
    }

    // ── tolerant decoding: bad docs are skipped, never crash ─────────────────

    @Test
    fun fromDoc_unknownKindReturnsNull() {
        val doc = TravelSignalCloudCodec.toDoc(signal()).copy(kind = "teleported")
        assertNull(TravelSignalCloudCodec.fromDoc(doc))
    }

    @Test
    fun fromDoc_malformedPayloadReturnsNull() {
        val doc = TravelSignalCloudCodec.toDoc(signal()).copy(payloadJson = "not json {{{")
        assertNull(TravelSignalCloudCodec.fromDoc(doc))
    }

    // ── mergeById: the sign-in reconcile ─────────────────────────────────────

    @Test
    fun mergeById_adoptsRemoteOnlySignalsInTimestampOrder() {
        val localNew = signal(id = "l1", timestamp = "2026-07-17T12:00:00Z")
        val remoteOld = signal(id = "r1", timestamp = "2026-07-01T12:00:00Z")

        val merged = TravelProfileStoreLogic.mergeById(listOf(localNew), listOf(remoteOld))

        assertEquals(listOf("r1", "l1"), merged.map { it.id })   // older remote sorts first
    }

    @Test
    fun mergeById_localWinsOnIdConflictAndNothingDuplicates() {
        val local = signal(id = "s1", value = "12A")
        val remoteSameId = signal(id = "s1", value = "31C")

        val merged = TravelProfileStoreLogic.mergeById(listOf(local), listOf(remoteSameId))

        assertEquals(1, merged.size)
        assertEquals("12A", merged.single().value)
    }

    @Test
    fun mergeById_capKeepsOnlyTheNewestEntries() {
        val local = (1..3).map { signal(id = "l$it", timestamp = "2026-07-1${it}T00:00:00Z") }
        val remote = listOf(signal(id = "r1", timestamp = "2026-07-01T00:00:00Z"))

        val merged = TravelProfileStoreLogic.mergeById(local, remote, cap = 2)

        assertEquals(listOf("l2", "l3"), merged.map { it.id })   // oldest (r1, l1) dropped
    }

    @Test
    fun mergeById_unparsableTimestampsSortOldest() {
        val garbage = signal(id = "g1", timestamp = "not-a-date")
        val dated = signal(id = "d1", timestamp = "2026-07-17T12:00:00Z")

        val merged = TravelProfileStoreLogic.mergeById(listOf(dated), listOf(garbage))

        assertEquals(listOf("g1", "d1"), merged.map { it.id })
    }
}
