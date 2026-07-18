package com.jetsetter.pro

import com.jetsetter.pro.core.data.remote.FlightAwareAirport
import com.jetsetter.pro.core.data.remote.FlightAwareFlight
import com.jetsetter.pro.core.data.remote.FlightAwareResponse
import com.jetsetter.pro.feature.disruption.DisruptionFlightStatus
import com.jetsetter.pro.feature.disruption.toMonitoredFlight
import com.jetsetter.pro.feature.flighttracker.toTrackerFlight
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Pins the FlightAware wiring's pure mappings (plan B6): the AeroAPI wire DTO's snake_case
 * decode, DTO → [com.jetsetter.pro.feature.flighttracker.FlightTrackerFlight] (offset-from-now
 * schedule model), and DTO → [com.jetsetter.pro.feature.disruption.DisruptionMonitoredFlight]
 * (minutes-since-midnight model). Pure JVM — no network, no Android.
 */
class FlightAwareMappingTest {

    private val now: Instant = Instant.parse("2026-07-17T12:00:00Z")
    private val utc: ZoneId = ZoneOffset.UTC

    private fun flight(
        ident: String? = "DL1423",
        operator: String? = "DAL",
        status: String? = "Scheduled",
        cancelled: Boolean? = false,
        origin: FlightAwareAirport? = FlightAwareAirport(codeIata = "LAS", city = "Las Vegas"),
        destination: FlightAwareAirport? = FlightAwareAirport(codeIata = "ATL", city = "Atlanta"),
        scheduledOut: String? = "2026-07-17T13:35:00Z",
        estimatedOut: String? = null,
        actualOut: String? = null,
        scheduledIn: String? = "2026-07-17T17:53:00Z",
        estimatedIn: String? = null,
        actualIn: String? = null,
        gateOrigin: String? = "C22",
        gateDestination: String? = "B7",
        terminalOrigin: String? = "3",
        terminalDestination: String? = "S",
        departureDelaySeconds: Long? = 0L,
        arrivalDelaySeconds: Long? = 0L,
    ) = FlightAwareFlight(
        ident = ident,
        operator = operator,
        status = status,
        cancelled = cancelled,
        origin = origin,
        destination = destination,
        scheduledOut = scheduledOut,
        estimatedOut = estimatedOut,
        actualOut = actualOut,
        scheduledIn = scheduledIn,
        estimatedIn = estimatedIn,
        actualIn = actualIn,
        gateOrigin = gateOrigin,
        gateDestination = gateDestination,
        terminalOrigin = terminalOrigin,
        terminalDestination = terminalDestination,
        departureDelaySeconds = departureDelaySeconds,
        arrivalDelaySeconds = arrivalDelaySeconds,
    )

    // ── Wire decode (snake_case @Json names) ─────────────────────────────────

    @Test
    fun dtoDecode_readsSnakeCaseFieldsAndNestedAirports() {
        val json = """
            {"flights":[{
              "ident":"DL1423","operator":"DAL","status":"Delayed","cancelled":false,
              "origin":{"code_iata":"LAS","city":"Las Vegas"},
              "destination":{"code_iata":"ATL","city":"Atlanta"},
              "scheduled_out":"2026-07-17T13:35:00Z","estimated_out":"2026-07-17T14:20:00Z",
              "actual_out":null,
              "scheduled_in":"2026-07-17T17:53:00Z","estimated_in":"2026-07-17T18:38:00Z",
              "actual_in":null,
              "gate_origin":"C22","gate_destination":"B7",
              "terminal_origin":"3","terminal_destination":"S",
              "departure_delay":2700,"arrival_delay":2700
            }]}
        """.trimIndent()
        val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            .adapter(FlightAwareResponse::class.java)

        val decoded = adapter.fromJson(json)

        assertNotNull(decoded)
        val f = decoded!!.flights.single()
        assertEquals("DL1423", f.ident)
        assertEquals("Delayed", f.status)
        assertEquals(false, f.cancelled)
        assertEquals("LAS", f.origin?.codeIata)
        assertEquals("Atlanta", f.destination?.city)
        assertEquals("2026-07-17T13:35:00Z", f.scheduledOut)
        assertEquals("2026-07-17T14:20:00Z", f.estimatedOut)
        assertNull(f.actualOut)
        assertEquals("2026-07-17T17:53:00Z", f.scheduledIn)
        assertEquals("C22", f.gateOrigin)
        assertEquals("B7", f.gateDestination)
        assertEquals("3", f.terminalOrigin)
        assertEquals("S", f.terminalDestination)
        assertEquals(2700L, f.departureDelaySeconds)
        assertEquals(2700L, f.arrivalDelaySeconds)
    }

    @Test
    fun dtoDecode_toleratesSparsePayload() {
        val json = """{"flights":[{"ident":"AA88"}]}"""
        val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            .adapter(FlightAwareResponse::class.java)

        val f = adapter.fromJson(json)!!.flights.single()

        assertEquals("AA88", f.ident)
        assertNull(f.origin)
        assertNull(f.scheduledOut)
        assertNull(f.cancelled)
        assertNull(f.departureDelaySeconds)
    }

    // ── DTO → FlightTrackerFlight (offset-from-now model) ────────────────────

    @Test
    fun toTrackerFlight_mapsScheduleToOffsetsFromNow() {
        val tracked = flight(departureDelaySeconds = 2700L).toTrackerFlight(now)!!

        assertEquals("DL1423", tracked.ident)
        assertEquals("DAL", tracked.airline)
        assertEquals("LAS", tracked.originCode)
        assertEquals("Las Vegas", tracked.originCity)
        assertEquals("ATL", tracked.destinationCode)
        assertEquals("Atlanta", tracked.destinationCity)
        assertEquals("C22", tracked.gate)
        assertEquals("3", tracked.terminal)
        assertEquals(95L, tracked.departureOffsetMin)   // 13:35Z is 95 min after 12:00Z "now"
        assertEquals(258L, tracked.durationMin)         // 13:35 → 17:53 gate-to-gate
        assertEquals(45L, tracked.delayMin)             // 2700 s → 45 min
    }

    @Test
    fun toTrackerFlight_negativeOffsetOnceDeparted() {
        val tracked = flight(
            scheduledOut = "2026-07-17T10:25:00Z",
            scheduledIn = "2026-07-17T14:43:00Z",
        ).toTrackerFlight(now)!!

        assertEquals(-95L, tracked.departureOffsetMin)
    }

    @Test
    fun toTrackerFlight_delayFloorsToWholeMinutesAndClampsEarly() {
        assertEquals(44L, flight(departureDelaySeconds = 2699L).toTrackerFlight(now)!!.delayMin)
        assertEquals(0L, flight(departureDelaySeconds = -600L).toTrackerFlight(now)!!.delayMin)
        assertEquals(0L, flight(departureDelaySeconds = null).toTrackerFlight(now)!!.delayMin)
    }

    @Test
    fun toTrackerFlight_fallsBackWhenOptionalFieldsAbsent() {
        val tracked = flight(
            operator = null,
            origin = null,
            destination = FlightAwareAirport(codeIata = "ATL", city = null),
            gateOrigin = null,
            terminalOrigin = " ",
        ).toTrackerFlight(now)!!

        assertEquals("DL1423", tracked.airline)     // operator falls back to the ident
        assertEquals("—", tracked.originCode)
        assertEquals("—", tracked.originCity)
        assertEquals("ATL", tracked.destinationCity) // city falls back to the code
        assertEquals("—", tracked.gate)
        assertEquals("—", tracked.terminal)
    }

    @Test
    fun toTrackerFlight_nullWithoutIdentOrScheduleOrPositiveDuration() {
        assertNull(flight(ident = null).toTrackerFlight(now))
        assertNull(flight(ident = "  ").toTrackerFlight(now))
        assertNull(flight(scheduledOut = null).toTrackerFlight(now))
        assertNull(flight(scheduledIn = "not-a-date").toTrackerFlight(now))
        assertNull(flight(scheduledIn = "2026-07-17T13:35:00Z").toTrackerFlight(now)) // zero duration
    }

    // ── DTO → DisruptionMonitoredFlight (minutes-since-midnight model) ───────

    @Test
    fun toMonitoredFlight_mapsClockMinutesAndDelayFromEstimates() {
        val monitored = flight(
            status = "Delayed",
            scheduledOut = "2026-07-17T07:00:00Z",
            estimatedOut = "2026-07-17T08:35:00Z",
            scheduledIn = "2026-07-17T14:15:00Z",
            estimatedIn = "2026-07-17T17:15:00Z",
        ).toMonitoredFlight(zone = utc, fare = 342.0)!!

        assertEquals("DL1423", monitored.flightNumber)
        assertEquals("LAS → ATL", monitored.route)
        assertEquals("Las Vegas (LAS)", monitored.origin)
        assertEquals("Atlanta (ATL)", monitored.destination)
        assertEquals(7 * 60, monitored.scheduledDepartureMin)
        assertEquals(8 * 60 + 35, monitored.revisedDepartureMin)
        assertEquals(14 * 60 + 15, monitored.scheduledArrivalMin)
        assertEquals(17 * 60 + 15, monitored.revisedArrivalMin)
        assertEquals(95, monitored.delayMinutes)               // derived: revised − scheduled
        assertEquals("C22", monitored.gate)
        assertEquals(342.0, monitored.fare, 0.0)
        assertEquals(DisruptionFlightStatus.DELAYED, monitored.status)
        assertEquals("Delayed", monitored.reason)              // provider status wins as reason
    }

    @Test
    fun toMonitoredFlight_actualBeatsEstimatedBeatsScheduledPlusDelay() {
        val base = flight(
            scheduledOut = "2026-07-17T07:00:00Z",
            estimatedOut = "2026-07-17T07:30:00Z",
            actualOut = "2026-07-17T07:45:00Z",
        )
        assertEquals(7 * 60 + 45, base.toMonitoredFlight(utc, 0.0)!!.revisedDepartureMin)

        val estimated = flight(scheduledOut = "2026-07-17T07:00:00Z", estimatedOut = "2026-07-17T07:30:00Z")
        assertEquals(7 * 60 + 30, estimated.toMonitoredFlight(utc, 0.0)!!.revisedDepartureMin)

        val delayOnly = flight(scheduledOut = "2026-07-17T07:00:00Z", departureDelaySeconds = 1200L)
        assertEquals(7 * 60 + 20, delayOnly.toMonitoredFlight(utc, 0.0)!!.revisedDepartureMin)
    }

    @Test
    fun toMonitoredFlight_statusCancelledDelayedOnTime() {
        assertEquals(
            DisruptionFlightStatus.CANCELLED,
            flight(cancelled = true, status = null).toMonitoredFlight(utc, 0.0)!!.status,
        )
        assertEquals(
            "Cancelled by the airline",
            flight(cancelled = true, status = null).toMonitoredFlight(utc, 0.0)!!.reason,
        )
        val onTime = flight(status = null).toMonitoredFlight(utc, 0.0)!!
        assertEquals(DisruptionFlightStatus.ON_TIME, onTime.status)
        assertEquals("On schedule", onTime.reason)
        val delayed = flight(status = null, estimatedOut = "2026-07-17T14:25:00Z").toMonitoredFlight(utc, 0.0)!!
        assertEquals(DisruptionFlightStatus.DELAYED, delayed.status)
        assertEquals("Running 50m late", delayed.reason)
    }

    @Test
    fun toMonitoredFlight_fallsBackWhenAirportsAbsent() {
        val monitored = flight(origin = null, destination = null, gateOrigin = null)
            .toMonitoredFlight(utc, 0.0)!!

        assertEquals("DL1423", monitored.route)  // route falls back to the ident
        assertEquals("—", monitored.origin)
        assertEquals("—", monitored.destination)
        assertEquals("—", monitored.gate)
    }

    @Test
    fun toMonitoredFlight_nullWithoutIdentOrScheduledTimes() {
        assertNull(flight(ident = null).toMonitoredFlight(utc, 0.0))
        assertNull(flight(scheduledOut = null).toMonitoredFlight(utc, 0.0))
        assertNull(flight(scheduledIn = "garbage").toMonitoredFlight(utc, 0.0))
    }
}
