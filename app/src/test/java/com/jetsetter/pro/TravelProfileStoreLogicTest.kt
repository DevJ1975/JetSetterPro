package com.jetsetter.pro

import com.jetsetter.pro.core.intelligence.TravelProfileStoreLogic
import com.jetsetter.pro.core.intelligence.TravelSignal
import com.jetsetter.pro.core.model.Trip
import com.jetsetter.pro.core.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pins the pure rules of the travel-signal store (spec §1.6, plan A4) via
 * [TravelProfileStoreLogic]: the consent mapping table (master + per-surface switches, silent
 * no-op semantics), the 2000-entry FIFO cap, dismissed-suggestion counting, and idempotent
 * completed-trip selection (seed/demo trips never learn). Pure JVM — no DataStore.
 */
class TravelProfileStoreLogicTest {

    private fun signal(
        kind: TravelSignal.Kind = TravelSignal.Kind.EXPENSE_LOGGED,
        value: String = "v",
        attributes: Map<String, String> = emptyMap(),
    ) = TravelSignal(
        kind = kind,
        value = value,
        attributes = attributes,
        timestamp = "2026-07-17T12:00:00Z",
        source = "test",
    )

    private val allOn = UserPreferences() // learning defaults are all true

    // ── consent mapping (spec §1.6) ──────────────────────────────────────────────────────────────

    @Test
    fun consent_defaultsAllowEveryKind() {
        for (kind in TravelSignal.Kind.entries) {
            assertTrue("$kind should be allowed by default", TravelProfileStoreLogic.allows(kind, allOn))
        }
    }

    @Test
    fun consent_masterOffSilencesEveryKind() {
        val masterOff = allOn.copy(learningEnabled = false)
        for (kind in TravelSignal.Kind.entries) {
            assertFalse("$kind must be gated by the master switch", TravelProfileStoreLogic.allows(kind, masterOff))
        }
    }

    @Test
    fun consent_learnFromReceiptsGatesReceiptAndExpenseKinds() {
        val receiptsOff = allOn.copy(learnFromReceipts = false)
        assertFalse(TravelProfileStoreLogic.allows(TravelSignal.Kind.RECEIPT_SCANNED, receiptsOff))
        assertFalse(TravelProfileStoreLogic.allows(TravelSignal.Kind.EXPENSE_LOGGED, receiptsOff))
        // Every other kind is unaffected.
        assertTrue(TravelProfileStoreLogic.allows(TravelSignal.Kind.SEAT_CHOSEN, receiptsOff))
        assertTrue(TravelProfileStoreLogic.allows(TravelSignal.Kind.FLIGHT_FLOWN, receiptsOff))
        assertTrue(TravelProfileStoreLogic.allows(TravelSignal.Kind.LOYALTY_ADDED, receiptsOff))
    }

    @Test
    fun consent_learnFromCheckInsGatesSeatChosenOnly() {
        val checkInsOff = allOn.copy(learnFromCheckIns = false)
        assertFalse(TravelProfileStoreLogic.allows(TravelSignal.Kind.SEAT_CHOSEN, checkInsOff))
        assertTrue(TravelProfileStoreLogic.allows(TravelSignal.Kind.FLIGHT_FLOWN, checkInsOff))
        assertTrue(TravelProfileStoreLogic.allows(TravelSignal.Kind.EXPENSE_LOGGED, checkInsOff))
    }

    @Test
    fun consent_learnFromTripsGatesFlightTripAndPlaceKinds() {
        val tripsOff = allOn.copy(learnFromTrips = false)
        assertFalse(TravelProfileStoreLogic.allows(TravelSignal.Kind.FLIGHT_FLOWN, tripsOff))
        assertFalse(TravelProfileStoreLogic.allows(TravelSignal.Kind.TRIP_COMPLETED, tripsOff))
        assertFalse(TravelProfileStoreLogic.allows(TravelSignal.Kind.PLACE_VISITED, tripsOff))
        assertTrue(TravelProfileStoreLogic.allows(TravelSignal.Kind.SEAT_CHOSEN, tripsOff))
    }

    @Test
    fun consent_loyaltyAndFeedbackAreMasterOnly() {
        val allSurfacesOff = allOn.copy(
            learnFromReceipts = false,
            learnFromCheckIns = false,
            learnFromTrips = false,
        )
        assertTrue(TravelProfileStoreLogic.allows(TravelSignal.Kind.LOYALTY_ADDED, allSurfacesOff))
        assertTrue(TravelProfileStoreLogic.allows(TravelSignal.Kind.SUGGESTION_FEEDBACK, allSurfacesOff))
    }

    // ── FIFO cap ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun appendCapped_dropsOldestPastTwoThousand() {
        val full = (1..TravelProfileStoreLogic.MAX_SIGNALS).map { signal(value = "s$it") }
        val appended = TravelProfileStoreLogic.appendCapped(full, signal(value = "newest"))
        assertEquals(TravelProfileStoreLogic.MAX_SIGNALS, appended.size)
        assertEquals("s2", appended.first().value)      // s1 (oldest) dropped
        assertEquals("newest", appended.last().value)   // newest appended at the tail
    }

    @Test
    fun appendCapped_underCapJustAppends() {
        val some = listOf(signal(value = "a"), signal(value = "b"))
        val appended = TravelProfileStoreLogic.appendCapped(some, signal(value = "c"))
        assertEquals(listOf("a", "b", "c"), appended.map { it.value })
    }

    // ── dismissed counting ───────────────────────────────────────────────────────────────────────

    @Test
    fun dismissedCount_countsOnlyMatchingRejectedFeedback() {
        val signals = listOf(
            signal(
                kind = TravelSignal.Kind.SUGGESTION_FEEDBACK,
                value = "SEAT_PREFERENCE_NUDGE",
                attributes = mapOf(TravelSignal.Attr.ACCEPTED to "false"),
            ),
            signal(
                kind = TravelSignal.Kind.SUGGESTION_FEEDBACK,
                value = "SEAT_PREFERENCE_NUDGE",
                attributes = mapOf(TravelSignal.Attr.ACCEPTED to "false"),
            ),
            // Accepted → not a dismissal.
            signal(
                kind = TravelSignal.Kind.SUGGESTION_FEEDBACK,
                value = "SEAT_PREFERENCE_NUDGE",
                attributes = mapOf(TravelSignal.Attr.ACCEPTED to "true"),
            ),
            // Different kind string → not counted.
            signal(
                kind = TravelSignal.Kind.SUGGESTION_FEEDBACK,
                value = "PACKING_NUDGE",
                attributes = mapOf(TravelSignal.Attr.ACCEPTED to "false"),
            ),
            // Same value but not a feedback signal → not counted.
            signal(kind = TravelSignal.Kind.PLACE_VISITED, value = "SEAT_PREFERENCE_NUDGE"),
        )
        assertEquals(2, TravelProfileStoreLogic.dismissedCount(signals, "SEAT_PREFERENCE_NUDGE"))
        assertEquals(1, TravelProfileStoreLogic.dismissedCount(signals, "PACKING_NUDGE"))
        assertEquals(0, TravelProfileStoreLogic.dismissedCount(signals, "WELCOME_HOME"))
    }

    // ── completed-trip selection ─────────────────────────────────────────────────────────────────

    private val today = LocalDate.of(2026, 7, 17)

    private fun trip(id: String, endDate: String) = Trip(
        id = id,
        name = "Trip $id",
        destination = "Somewhere",
        startDate = "2026-07-01",
        endDate = endDate,
    )

    @Test
    fun completedTripsToRecord_selectsEndedUnrecordedNonSeedTrips() {
        val trips = listOf(
            trip("ended", "2026-07-16"),          // ended → record
            trip("ends-today", "2026-07-17"),     // not before today → skip
            trip("future", "2026-08-01"),         // upcoming → skip
            trip("recorded", "2026-07-10"),       // already recorded → skip
            trip("seed", "2026-07-10"),           // demo seed → never learns
            trip("bad-date", "not-a-date"),       // unparsable → skip
        )
        val selected = TravelProfileStoreLogic.completedTripsToRecord(
            trips = trips,
            today = today,
            recordedIds = setOf("recorded"),
            seedIds = setOf("seed"),
        )
        assertEquals(listOf("ended"), selected.map { it.id })
    }

    @Test
    fun durationDays_endMinusStart_nullWhenUnparsableOrNegative() {
        assertEquals(3L, TravelProfileStoreLogic.durationDays(
            Trip(id = "t", name = "T", destination = "D", startDate = "2026-07-14", endDate = "2026-07-17"),
        ))
        assertNull(
            TravelProfileStoreLogic.durationDays(
                Trip(id = "t", name = "T", destination = "D", startDate = "garbage", endDate = "2026-07-17"),
            ),
        )
        assertNull(
            TravelProfileStoreLogic.durationDays(
                Trip(id = "t", name = "T", destination = "D", startDate = "2026-07-17", endDate = "2026-07-14"),
            ),
        )
    }
}
