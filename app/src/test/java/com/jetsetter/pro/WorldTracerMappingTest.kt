package com.jetsetter.pro

import com.jetsetter.pro.core.data.remote.worldtracer.WORLD_TRACER_STATUS_UNKNOWN
import com.jetsetter.pro.core.data.remote.worldtracer.WorldTracerBag
import com.jetsetter.pro.core.data.remote.worldtracer.WorldTracerBagDto
import com.jetsetter.pro.core.data.remote.worldtracer.WorldTracerRoutingDto
import com.jetsetter.pro.core.data.remote.worldtracer.WorldTracerScan
import com.jetsetter.pro.core.data.remote.worldtracer.toWorldTracerBag
import com.jetsetter.pro.feature.luggagetracker.LuggageTrackerBag
import com.jetsetter.pro.feature.luggagetracker.LuggageTrackerScan
import com.jetsetter.pro.feature.luggagetracker.LuggageTrackerStatus
import com.jetsetter.pro.feature.luggagetracker.looksLikeRealBagTag
import com.jetsetter.pro.feature.luggagetracker.refreshedFrom
import com.jetsetter.pro.feature.luggagetracker.worldTracerStatusToLuggage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Pins the pure parts of the SITA WorldTracer seam (plan B7): the wire DTO→[WorldTracerBag]
 * mapping in core, and the feature-side [refreshedFrom] merge the Luggage Tracker's
 * live-over-mock refresh relies on. Pure JVM — no network, no Android.
 */
class WorldTracerMappingTest {

    private fun dto(
        tagNumber: String? = "DL00421788",
        status: String? = "IN_TRANSIT",
        lastSeenStation: String? = "ATL",
        lastSeenAt: String? = "2026-07-14T10:00:00Z",
        routing: List<WorldTracerRoutingDto> = listOf(
            WorldTracerRoutingDto(
                station = "ATL",
                description = "Loaded onto DL 1423",
                scannedAt = "2026-07-14T10:00:00Z",
            ),
        ),
    ) = WorldTracerBagDto(
        tagNumber = tagNumber,
        status = status,
        lastSeenStation = lastSeenStation,
        lastSeenAt = lastSeenAt,
        routing = routing,
    )

    // ── DTO → WorldTracerBag ─────────────────────────────────────────────────

    @Test
    fun fullPayload_maps() {
        val bag = dto().toWorldTracerBag()

        assertNotNull(bag)
        assertEquals("DL00421788", bag!!.tag)
        assertEquals("IN_TRANSIT", bag.status)
        assertEquals("ATL", bag.lastSeenStation)
        assertEquals(Instant.parse("2026-07-14T10:00:00Z"), bag.lastSeenAt)
        assertEquals(1, bag.routing.size)
        val scan = bag.routing.single()
        assertEquals("ATL", scan.station)
        assertEquals("Loaded onto DL 1423", scan.description)
        assertEquals(Instant.parse("2026-07-14T10:00:00Z"), scan.scannedAt)
    }

    @Test
    fun missingOrBlankTag_mapsToNull() {
        assertNull(dto(tagNumber = null).toWorldTracerBag())
        assertNull(dto(tagNumber = "   ").toWorldTracerBag())
    }

    @Test
    fun status_normalizesCaseAndWhitespace() {
        assertEquals("IN_TRANSIT", dto(status = "  in transit ").toWorldTracerBag()!!.status)
        assertEquals("DELIVERED", dto(status = "Delivered").toWorldTracerBag()!!.status)
    }

    @Test
    fun missingStatus_fallsBackToUnknown() {
        assertEquals(WORLD_TRACER_STATUS_UNKNOWN, dto(status = null).toWorldTracerBag()!!.status)
        assertEquals(WORLD_TRACER_STATUS_UNKNOWN, dto(status = "  ").toWorldTracerBag()!!.status)
    }

    @Test
    fun sparseFields_degradeToNulls() {
        val bag = dto(
            lastSeenStation = " ",
            lastSeenAt = "not-a-time",
            routing = listOf(
                WorldTracerRoutingDto(station = "LAS", description = null, scannedAt = "garbled"),
            ),
        ).toWorldTracerBag()

        assertNotNull(bag)
        assertNull(bag!!.lastSeenStation)
        assertNull(bag.lastSeenAt)
        val scan = bag.routing.single()
        assertNull(scan.description)
        assertNull(scan.scannedAt)
    }

    @Test
    fun routingEntriesWithoutStation_areDropped() {
        val bag = dto(
            routing = listOf(
                WorldTracerRoutingDto(station = null, description = "orphan", scannedAt = null),
                WorldTracerRoutingDto(station = "  ", description = "blank", scannedAt = null),
                WorldTracerRoutingDto(station = " LAS ", description = "kept", scannedAt = null),
            ),
        ).toWorldTracerBag()

        assertEquals(1, bag!!.routing.size)
        assertEquals("LAS", bag.routing.single().station)
    }

    @Test
    fun emptyRouting_mapsToEmptyList() {
        assertTrue(dto(routing = emptyList()).toWorldTracerBag()!!.routing.isEmpty())
    }

    // ── Feature-side gates + merge ───────────────────────────────────────────

    @Test
    fun realLookingTags_areRecognized() {
        assertTrue(looksLikeRealBagTag("DL 0042 1788")) // display form of the mock board
        assertTrue(looksLikeRealBagTag("DL00421788"))
        assertTrue(looksLikeRealBagTag("0042178812")) // 10-digit license plate
        assertFalse(looksLikeRealBagTag(""))
        assertFalse(looksLikeRealBagTag("MOCK-BAG"))
        assertFalse(looksLikeRealBagTag("DL42")) // too short to be a tag number
    }

    @Test
    fun providerStatuses_mapOntoFeatureEnum() {
        assertEquals(LuggageTrackerStatus.CHECKED_IN, worldTracerStatusToLuggage("CHECKED_IN"))
        assertEquals(LuggageTrackerStatus.IN_TRANSIT, worldTracerStatusToLuggage("IN_TRANSIT"))
        assertEquals(LuggageTrackerStatus.ARRIVED, worldTracerStatusToLuggage("DELIVERED"))
        assertEquals(LuggageTrackerStatus.DELAYED, worldTracerStatusToLuggage("MISHANDLED"))
        assertNull(worldTracerStatusToLuggage(WORLD_TRACER_STATUS_UNKNOWN))
        assertNull(worldTracerStatusToLuggage("TELEPORTED"))
    }

    private fun mockBag() = LuggageTrackerBag(
        tagId = "DL 0042 1788",
        description = "Black Tumi roller · 26\"",
        status = LuggageTrackerStatus.CHECKED_IN,
        scanHistory = listOf(
            LuggageTrackerScan(location = "LAS · Check-in", detail = "Tag printed", timestampMillis = 1_000L),
        ),
    )

    @Test
    fun refresh_replacesStatusAndScans_mostRecentFirst() {
        val live = WorldTracerBag(
            tag = "DL00421788",
            status = "DELIVERED",
            lastSeenStation = "ATL",
            lastSeenAt = Instant.parse("2026-07-14T12:00:00Z"),
            routing = listOf(
                WorldTracerScan("LAS", "Loaded", Instant.parse("2026-07-14T08:00:00Z")),
                WorldTracerScan("ATL", "At claim", Instant.parse("2026-07-14T12:00:00Z")),
            ),
        )

        val refreshed = mockBag().refreshedFrom(live)

        assertEquals(LuggageTrackerStatus.ARRIVED, refreshed.status)
        // Display identity is kept; only journey data refreshes.
        assertEquals("DL 0042 1788", refreshed.tagId)
        assertEquals("Black Tumi roller · 26\"", refreshed.description)
        assertEquals(listOf("ATL", "LAS"), refreshed.scanHistory.map { it.location })
        assertEquals(
            Instant.parse("2026-07-14T12:00:00Z").toEpochMilli(),
            refreshed.scanHistory.first().timestampMillis,
        )
    }

    @Test
    fun refresh_unmappableStatus_keepsExistingStatus() {
        val live = WorldTracerBag(
            tag = "DL00421788",
            status = WORLD_TRACER_STATUS_UNKNOWN,
            lastSeenStation = null,
            lastSeenAt = null,
            routing = listOf(WorldTracerScan("ATL", null, Instant.parse("2026-07-14T12:00:00Z"))),
        )

        assertEquals(LuggageTrackerStatus.CHECKED_IN, mockBag().refreshedFrom(live).status)
    }

    @Test
    fun refresh_undatedScansDropped_andEmptyLiveHistoryKeepsMockScans() {
        val live = WorldTracerBag(
            tag = "DL00421788",
            status = "IN_TRANSIT",
            lastSeenStation = "ATL",
            lastSeenAt = null,
            routing = listOf(WorldTracerScan("ATL", "No timestamp", null)),
        )

        val refreshed = mockBag().refreshedFrom(live)

        // Every live scan lacked a timestamp → the anchored mock history is kept.
        assertEquals(mockBag().scanHistory, refreshed.scanHistory)
        assertEquals(LuggageTrackerStatus.IN_TRANSIT, refreshed.status)
    }

    @Test
    fun refresh_scanWithoutDescription_getsPlaceholderDetail() {
        val live = WorldTracerBag(
            tag = "DL00421788",
            status = "IN_TRANSIT",
            lastSeenStation = "ATL",
            lastSeenAt = null,
            routing = listOf(WorldTracerScan("ATL", null, Instant.parse("2026-07-14T12:00:00Z"))),
        )

        assertEquals("Checkpoint scan", mockBag().refreshedFrom(live).scanHistory.single().detail)
    }
}
