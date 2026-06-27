package com.jetsetter.pro.feature.luggagetracker

import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory mock backing for the Luggage Tracker feature. Mirrors the shape of a real
 * bag-tracking API (registered bags + per-bag scan history) without any network — the
 * live integration (e.g. an airline baggage / RFID feed) is documented in the feature
 * parity guide.
 *
 * Scans are seeded as "minutes ago" offsets and anchored to a real clock at read time, so
 * the mock always looks like a live journey ("12 min ago") no matter when it's opened, and
 * every displayed time stays self-consistent with the scan it came from.
 */
@Singleton
class LuggagetrackerRepository @Inject constructor() {

    private data class ScanSeed(val location: String, val detail: String, val minutesAgo: Long)

    private data class BagSeed(
        val tagId: String,
        val description: String,
        val status: LuggageTrackerStatus,
        val scans: List<ScanSeed>,
    )

    private val seeds: List<BagSeed> = listOf(
        BagSeed(
            tagId = "DL 0042 1788",
            description = "Black Tumi roller · 26\"",
            status = LuggageTrackerStatus.IN_TRANSIT,
            scans = listOf(
                ScanSeed("ATL · Concourse B ramp", "Loaded onto DL 1423 · ULD AKE2291", 18),
                ScanSeed("ATL · Sortation T3", "Routed to gate C22", 52),
                ScanSeed("ATL · Transfer belt 7", "Connection scan from LAS", 66),
                ScanSeed("LAS · Outbound ramp", "Loaded — outbound to ATL", 315),
                ScanSeed("LAS · Check-in desk 14", "Tag printed and accepted", 455),
            ),
        ),
        BagSeed(
            tagId = "DL 0042 1789",
            description = "Silver hardshell · 30\"",
            status = LuggageTrackerStatus.CHECKED_IN,
            scans = listOf(
                ScanSeed("LAS · Check-in desk 14", "Tag printed and accepted", 9),
                ScanSeed("LAS · Self-tag kiosk 3", "Bag tag generated", 22),
            ),
        ),
        BagSeed(
            tagId = "DL 0042 1791",
            description = "Olive duffel · carry-on",
            status = LuggageTrackerStatus.ARRIVED,
            scans = listOf(
                ScanSeed("ATL · Carousel 11", "Delivered to claim — ready for pickup", 3),
                ScanSeed("ATL · Inbound belt 5", "Unloaded from DL 1423", 14),
                ScanSeed("LAS · Outbound ramp", "Loaded — outbound to ATL", 320),
                ScanSeed("LAS · Check-in desk 14", "Tag printed and accepted", 460),
            ),
        ),
        BagSeed(
            tagId = "DL 0042 1790",
            description = "Garment bag · navy",
            status = LuggageTrackerStatus.DELAYED,
            scans = listOf(
                ScanSeed("SLC · Mishandled office", "Missed connection — rebooking on DL 2207", 78),
                ScanSeed("SLC · Transfer belt 4", "Arrived offline from LAS", 95),
                ScanSeed("LAS · Outbound ramp", "Loaded — outbound to SLC", 290),
                ScanSeed("LAS · Check-in desk 14", "Tag printed and accepted", 452),
            ),
        ),
    )

    /**
     * All registered bags for the current traveler, with scan timestamps anchored to
     * [nowMillis] so relative labels ("12 min ago") read realistically on open. Scan history
     * is returned most-recent first.
     */
    suspend fun registeredBags(nowMillis: Long): List<LuggageTrackerBag> = seeds.map { seed ->
        LuggageTrackerBag(
            tagId = seed.tagId,
            description = seed.description,
            status = seed.status,
            scanHistory = seed.scans
                .sortedBy { it.minutesAgo }
                .map { scan ->
                    LuggageTrackerScan(
                        location = scan.location,
                        detail = scan.detail,
                        timestampMillis = nowMillis - scan.minutesAgo * 60_000L,
                    )
                },
        )
    }
}
