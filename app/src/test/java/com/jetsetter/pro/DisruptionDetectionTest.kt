package com.jetsetter.pro

import com.jetsetter.pro.core.data.remote.FlightAwareFlight
import com.jetsetter.pro.core.work.DetectedDisruption
import com.jetsetter.pro.core.work.DisruptionDetection
import com.jetsetter.pro.core.work.FlightStatusSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the disruption-detection predicate (plan B6): a disruption is cancellation, departure
 * delay strictly over 45 minutes, or a gate change versus the last-seen snapshot — and each
 * fires exactly once across polls (transition-based, so an already-seen state never re-raises).
 * Pure JVM — fake snapshots only, no network.
 */
class DisruptionDetectionTest {

    private fun snapshot(
        ident: String = "DL1423",
        cancelled: Boolean = false,
        delayMin: Long = 0L,
        gate: String? = "C22",
    ) = FlightStatusSnapshot(
        ident = ident,
        cancelled = cancelled,
        departureDelayMinutes = delayMin,
        departureGate = gate,
    )

    // ── snapshot(flight) ─────────────────────────────────────────────────────

    @Test
    fun snapshot_condensesFlightToComparableFields() {
        val s = DisruptionDetection.snapshot(
            FlightAwareFlight(
                ident = " DL1423 ",
                cancelled = true,
                departureDelaySeconds = 2760L,   // 46 min
                gateOrigin = "C22",
            ),
        )!!

        assertEquals("DL1423", s.ident)
        assertEquals(true, s.cancelled)
        assertEquals(46L, s.departureDelayMinutes)
        assertEquals("C22", s.departureGate)
    }

    @Test
    fun snapshot_defaultsAndClamps() {
        val s = DisruptionDetection.snapshot(
            FlightAwareFlight(ident = "AA88", departureDelaySeconds = -300L, gateOrigin = "  "),
        )!!

        assertEquals(false, s.cancelled)
        assertEquals(0L, s.departureDelayMinutes)   // early departures clamp to 0
        assertNull(s.departureGate)                 // blank gate → null
    }

    @Test
    fun snapshot_nullWithoutIdent() {
        assertNull(DisruptionDetection.snapshot(FlightAwareFlight(ident = null)))
        assertNull(DisruptionDetection.snapshot(FlightAwareFlight(ident = "  ")))
    }

    // ── Cancellation ─────────────────────────────────────────────────────────

    @Test
    fun cancellation_firesOnFirstSightingOnly() {
        val cancelled = snapshot(cancelled = true)

        val first = DisruptionDetection.detect(cancelled, previous = null)!!
        assertEquals(DetectedDisruption.STATUS_CANCELLED, first.status)
        assertEquals("Flight DL1423 was cancelled", first.reason)

        // Second poll sees the same cancellation → no duplicate event.
        assertNull(DisruptionDetection.detect(cancelled, previous = cancelled))
    }

    // ── Delay > 45 min ───────────────────────────────────────────────────────

    @Test
    fun delay_firesStrictlyOverThreshold() {
        assertNull(DisruptionDetection.detect(snapshot(delayMin = 45L), previous = null))

        val detected = DisruptionDetection.detect(snapshot(delayMin = 46L), previous = null)!!
        assertEquals(DetectedDisruption.STATUS_DELAYED, detected.status)
        assertEquals("Departure delayed 46m", detected.reason)
    }

    @Test
    fun delay_firesOnCrossingTheThresholdNotOnGrowth() {
        // 30 → 95: crosses 45 → fires (with the human-formatted span).
        val crossed = DisruptionDetection.detect(
            snapshot(delayMin = 95L),
            previous = snapshot(delayMin = 30L),
        )!!
        assertEquals("Departure delayed 1h 35m", crossed.reason)

        // 50 → 60: already past the threshold last poll → no re-fire.
        assertNull(DisruptionDetection.detect(snapshot(delayMin = 60L), previous = snapshot(delayMin = 50L)))
    }

    @Test
    fun cancellationOutranksDelayAndGateChange() {
        val worst = snapshot(cancelled = true, delayMin = 90L, gate = "D4")
        val detected = DisruptionDetection.detect(worst, previous = snapshot(gate = "C22"))!!
        assertEquals(DetectedDisruption.STATUS_CANCELLED, detected.status)
    }

    // ── Gate change ──────────────────────────────────────────────────────────

    @Test
    fun gateChange_firesOnlyWhenBothSnapshotsHaveDifferentGates() {
        val detected = DisruptionDetection.detect(
            snapshot(gate = "D4"),
            previous = snapshot(gate = "C22"),
        )!!
        assertEquals(DetectedDisruption.STATUS_GATE_CHANGED, detected.status)
        assertEquals("Departure gate changed C22 → D4", detected.reason)

        // First-seen gate, an unchanged gate, or a gate dropping to unknown never fire.
        assertNull(DisruptionDetection.detect(snapshot(gate = "D4"), previous = snapshot(gate = null)))
        assertNull(DisruptionDetection.detect(snapshot(gate = "C22"), previous = snapshot(gate = "C22")))
        assertNull(DisruptionDetection.detect(snapshot(gate = null), previous = snapshot(gate = "C22")))
    }

    // ── Ident mismatch (monitored flight changed between polls) ──────────────

    @Test
    fun previousSnapshotForDifferentIdentIsIgnored() {
        // The old flight's gate can't produce a "change" on the new flight…
        assertNull(
            DisruptionDetection.detect(
                snapshot(ident = "UA512", gate = "F8"),
                previous = snapshot(ident = "DL1423", gate = "C22"),
            ),
        )
        // …but a fresh flight already over the delay threshold fires as first-seen.
        val detected = DisruptionDetection.detect(
            snapshot(ident = "UA512", delayMin = 50L, gate = null),
            previous = snapshot(ident = "DL1423", delayMin = 50L),
        )!!
        assertEquals(DetectedDisruption.STATUS_DELAYED, detected.status)
    }

    // ── Duration formatting ──────────────────────────────────────────────────

    @Test
    fun formatMinutes_compactSpans() {
        assertEquals("40m", DisruptionDetection.formatMinutes(40L))
        assertEquals("1h", DisruptionDetection.formatMinutes(60L))
        assertEquals("1h 35m", DisruptionDetection.formatMinutes(95L))
        assertEquals("0m", DisruptionDetection.formatMinutes(-5L))
    }
}
